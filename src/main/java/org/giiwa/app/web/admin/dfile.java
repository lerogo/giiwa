package org.giiwa.app.web.admin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.giiwa.bean.Disk;
import org.giiwa.bean.Node;
import org.giiwa.bean.Repo;
import org.giiwa.bean.Temp;
import org.giiwa.dao.Beans;
import org.giiwa.dao.UID;
import org.giiwa.dao.X;
import org.giiwa.dao.Helper.V;
import org.giiwa.dao.Helper.W;
import org.giiwa.dfile.DFile;
import org.giiwa.dfile.FileServer;
import org.giiwa.dfile.MyDFile;
import org.giiwa.json.JSON;
import org.giiwa.misc.IOUtil;
import org.giiwa.task.Monitor;
import org.giiwa.task.Task;
import org.giiwa.web.Controller;
import org.giiwa.web.Path;

public class dfile extends Controller {

	@Path(path = "file/delete", login = true, access = "access.config.admin")
	public void file_delete() {

		try {
			String f = this.getString("f");
			if (!X.isEmpty(f)) {
				Disk.delete(f);
			}

			this.send(JSON.create().append(X.STATE, 200).append(X.MESSAGE, "删除成功！"));
		} catch (Exception e) {
			this.error(e);
		}
	}

	@Path(path = "file/download", login = true, access = "access.config.admin")
	public void file_download() {

		try {
			String f = this.getString("f");
			if (X.isEmpty(f)) {
				this.send(JSON.create().append(X.STATE, 201).append(X.MESSAGE, "参数错误，[f]"));
				return;
			}
			DFile f1 = Disk.seek(f);
			if (f1.isFile()) {
				// download directly
				this.send(JSON.create().append(X.STATE, 200).append("uri", f));
				return;
			}

			String access = UID.random(10);

			// zip the folder
			long mid = Monitor.start(new Task() {

				/**
				 * 
				 */
				private static final long serialVersionUID = 1L;

				@SuppressWarnings("unused")
				public String uri;
				@SuppressWarnings("unused")
				public int state = 201;
				@SuppressWarnings("unused")
				public String message = "正在生成临时文件 ...";

				@Override
				public void onExecute() {
					// zip f1

					Monitor.flush(this);

					Temp t = Temp.create(f1.getName() + ".zip");
					try {
						File f2 = t.getFile();
						f2.getParentFile().mkdirs();

						ZipOutputStream out = new ZipOutputStream(new FileOutputStream(f2));

						f1.scan(e -> {
							try {
								if (e.isDirectory()) {
									ZipEntry en = new ZipEntry(e.getFilename() + "/");
									out.putNextEntry(en);
									out.closeEntry();
								} else {
									ZipEntry en = new ZipEntry(e.getFilename());
									out.putNextEntry(en);
									InputStream in = e.getInputStream();
									IOUtil.copy(in, out, false);
									out.closeEntry();
									X.close(in);
								}
							} catch (Exception e1) {
								log.error(e1.getMessage(), e1);
							}
						});

						X.close(out);

						DFile f3 = Disk.seek("/temp/" + lang.format(System.currentTimeMillis(), "yyyy/MM/dd/")
								+ System.currentTimeMillis() + "/" + f1.getName() + ".zip");
						t.save(f3);

						state = 200;
						uri = "/f/d/" + f3.getId() + "/" + f3.getName();
						Monitor.flush(this);

					} catch (Exception e) {
						log.error(e.getMessage(), e);
						message = e.getMessage();
						state = 202;
						Monitor.flush(this);
					} finally {
						t.delete();
					}

				}

			}, access);

			this.send(JSON.create().append(X.STATE, 200).append("checking",
					"/admin/monitor/checking?id=" + mid + "&access=" + access));
			return;

		} catch (Exception e) {
			this.error(e);
		}
	}

	@Path(path = "disk", login = true, access = "access.config.admin")
	public void disk() {

		int s = this.getInt("s");
		int n = this.getInt("n", 10);

		W q = W.create().sort("priority", -1).sort("path", 1);
		String name = this.getString("name");
		if (!X.isEmpty(name)) {

			W q1 = W.create();
			List<String> l1 = X.asList(Node.dao.distinct("id", W.create()
					.and("updated", System.currentTimeMillis() - Node.LOST, W.OP.gte).and("ip", name, W.OP.like)),
					e -> e.toString());
			if (l1 == null || l1.isEmpty()) {
				q1.or("node", X.EMPTY);
			} else {
				q1.or("node", l1);
			}
			q1.or("path", name);
			q.and(q1);

			this.set("name", name);
		}

		Beans<Disk> bs = Disk.dao.load(q, s, n);
		bs.count();

		this.pages(bs, s, n);

		this.show("/admin/dfile.disk.html");

	}

	@Path(path = "disk/add", login = true, access = "access.config.admin")
	public void disk_add() {

		if (method.isPost()) {

			V v = V.create();
			String s = this.getString("s");
			File f = new File(s);

			try {
				v.append("path", f.getCanonicalPath());
				v.append("priority", this.getInt("priority"));
				v.append("node", this.getString("node1"));
				v.append("enabled", this.getInt("enabled"));
				v.append("type", this.get("type"));

				Disk.create(v);
				this.send(JSON.create().append(X.STATE, 200));

				FileServer.reset();
				Disk.reset();

			} catch (Exception e) {
				log.error(e.getMessage(), e);
				this.send(JSON.create().append(X.STATE, 201).append(X.MESSAGE, e.getMessage()));
			}
			return;
		}

		this.set("nodes",
				Node.dao.load(W.create().and("updated", System.currentTimeMillis() - Node.LOST, W.OP.gte), 0, 10000));

		this.show("/admin/dfile.disk.add.html");

	}

	@Path(path = "file/add", login = true, access = "access.config.admin")
	public void file_add() {

		String f = this.getString("f");
		String repo = this.getString("repo");
		Repo.Entity e = Repo.load(repo);
		if (e != null) {
			try {
				String name = f != null ? (f + "/" + e.getName()) : ("/" + e.getName());

				DFile f1 = Disk.seek(name);
				f1.delete();
				IOUtil.copy(e.getInputStream(), f1.getOutputStream());

				this.send(JSON.create().append(X.STATE, 200).append(X.MESSAGE, "ok"));
				return;
			} catch (Exception e1) {
				log.error(e1.getMessage(), e1);
			} finally {
				e.delete();
			}
		}
		this.send(JSON.create().append(X.STATE, 201).append(X.MESSAGE, "failed"));

	}

	@Path(path = "file/batch", login = true, access = "access.config.admin")
	public void file_batch() {

		String repo = this.getString("repo");
		Repo.Entity e = Repo.load(repo);
		if (e != null) {

			Task.schedule(() -> {
				ZipInputStream zip = null;
				try {

					// zip file
					zip = new ZipInputStream(e.getInputStream());

					ZipEntry en = zip.getNextEntry();
					while (en != null) {
						if (!en.isDirectory()) {
							// file, put the file to ...
							DFile f1 = Disk.seek(en.getName());
							log.debug("put, filename=" + f1.getFilename());

							OutputStream out = f1.getOutputStream();
							IOUtil.copy(zip, out, false);
							X.close(out);
						}
						en = zip.getNextEntry();
					}

				} catch (Exception e1) {
					log.error(e1.getMessage(), e1);
				} finally {
					e.delete();
					X.close(zip);
				}
			});
			this.send(JSON.create().append(X.STATE, 200).append(X.MESSAGE, "正在复制，请稍后刷新..."));
			return;
		}
		this.send(JSON.create().append(X.STATE, 201).append(X.MESSAGE, "文件错误！"));

	}

	@Path(path = "disk/edit", login = true, access = "access.config.admin")
	public void disk_edit() {

		long id = this.getLong("id");

		if (method.isPost()) {
			V v = V.create();
			v.append("priority", this.getInt("priority"));
			v.append("enabled", this.getInt("enabled"));

			Disk.dao.update(id, v);
			FileServer.reset();
			Disk.reset();
			this.send(JSON.create().append(X.STATE, 200));

			return;
		}

		Disk s = Disk.dao.load(id);
		this.set("s", s);
		this.show("/admin/dfile.disk.edit.html");

	}

	@Path(path = "disk/delete", login = true, access = "access.config.admin")
	public void disk_delete() {

		final long id = this.getLong("id");
		int f = this.getInt("f");

		final Disk d = Disk.dao.load(id);

		if (d != null) {

			new Task() {
				/**
				 * 
				 */
				private static final long serialVersionUID = 1L;

				@Override
				public boolean interruptable() {
					return Boolean.FALSE;
				}

				@Override
				public void onExecute() {

					try {
						Disk.dao.delete(id);
						FileServer.reset();
						Disk.reset();

						if (f == 0) {
							DFile f = MyDFile.create(d, "/");
							DFile[] ff = f.listFiles();
							if (ff != null) {
								for (DFile f1 : ff) {
									copy(d.getPath(), f1);
								}
							}
						}
					} catch (Exception e) {
						log.error(e.getMessage(), e);
					}
				}

				void copy(String pre, DFile f) {
					try {
						if (f.isFile()) {
							try {
								String filename = f.getCanonicalPath().replace(pre, "");
								Disk.copy(f, Disk.seek(filename));
							} catch (Exception e) {
								log.error(e.getMessage(), e);
							}
						} else if (f.isDirectory()) {
							DFile[] ff = f.listFiles();
							if (ff != null) {
								for (DFile f1 : ff) {
									copy(pre, f1);
								}
							}
						}
					} catch (Exception e) {
						log.error(e.getMessage(), e);
					}
				}

			}.schedule(0);
		}
		this.send(JSON.create().append(X.STATE, 200));
	}

	@Path(path = "folder", login = true, access = "access.config.admin")
	public void folder() {

		try {
			String p = this.getString("f");
			if (X.isEmpty(p)) {
				p = "/";
			}

			if (!X.isSame(p, "/")) {
				this.set("f", Disk.seek(p));
			}

			Collection<DFile> list = Disk.list(p);
			this.set("list", list);

			this.show("/admin/dfile.folder.html");
		} catch (Exception e) {
			this.error(e);
		}
	}

}
