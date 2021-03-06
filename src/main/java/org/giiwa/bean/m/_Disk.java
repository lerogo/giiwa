package org.giiwa.bean.m;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.giiwa.dao.Bean;
import org.giiwa.dao.BeanDAO;
import org.giiwa.dao.Column;
import org.giiwa.dao.Table;
import org.giiwa.dao.UID;
import org.giiwa.dao.X;
import org.giiwa.dao.Helper.V;
import org.giiwa.dao.Helper.W;
import org.giiwa.json.JSON;

@Table(name = "gi_m_disk", memo = "GI-磁盘监测")
public class _Disk extends Bean {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static Log log = LogFactory.getLog(_Disk.class);

	public static BeanDAO<String, _Disk> dao = BeanDAO.create(_Disk.class);

	@Column(memo = "唯一序号")
	String id;

	@Column(memo = "节点")
	String node;

	@Column(memo = "磁盘")
	String disk;

	@Column(memo = "路径")
	String path;

	@Column(memo = "总空间")
	long total;

	@Column(memo = "已使用")
	long used;

	@Column(memo = "空闲")
	long free;

	public long getUsed() {
		return used;
	}

	public long getFree() {
		return free;
	}

	public synchronized static void update(String node, List<JSON> l1) {

		for (JSON jo : l1) {
			// insert or update
			String path = jo.getString("path");
			String name = jo.getString("name");
			if (X.isIn(name, "tmpfs", "devtmpfs")) {
				continue;
			}

			if (X.isEmpty(path) || X.isEmpty(name)) {
				log.error(jo, new Exception("name or path missed"));
				break;
			}

			long total = jo.getLong("total");
			if (total < 10 * 1024 * 1024 * 1024L)
				continue;

			String id = UID.id(node, path);
			try {

				name = name.replace("[\\\\]", "/");
				V v = V.fromJSON(jo).append("node", node).force("name", name).remove("_id", X.ID);

				// insert
				if (dao.exists(id)) {
					dao.update(id, v.copy());
				} else {
					dao.insert(v.copy().force(X.ID, id));
				}

				if (!Record.dao.exists(W.create("node", node).and("path", path).and("created",
						System.currentTimeMillis() - X.AHOUR, W.OP.gt))) {
					// save to record per hour
					Record.dao
							.insert(v.copy().force(X.ID, UID.id(id, System.currentTimeMillis())).append("node", node));
				}
			} catch (Exception e) {
				log.error(jo, e);
			}
		}
	}

	@Table(name = "gi_m_disk_record", memo = "GI-磁盘监测历史")
	public static class Record extends _Disk {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public static BeanDAO<String, Record> dao = BeanDAO.create(Record.class);

		public void cleanup() {
			dao.delete(W.create().and("created", System.currentTimeMillis() - X.AMONTH, W.OP.lt));
		}

	}

}
