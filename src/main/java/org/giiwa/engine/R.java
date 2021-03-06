/*
 * Copyright 2015 JIHU, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
package org.giiwa.engine;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.DoubleStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.giiwa.bean.Temp;
import org.giiwa.conf.Config;
import org.giiwa.dao.TimeStamp;
import org.giiwa.dao.X;
import org.giiwa.json.JSON;
import org.giiwa.misc.Exporter;
import org.giiwa.misc.IOUtil;
import org.giiwa.net.mq.IStub;
import org.giiwa.net.mq.MQ;
import org.giiwa.net.mq.MQ.Request;
import org.giiwa.task.Task;
import org.giiwa.web.Controller;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.REXPDouble;
import org.rosuda.REngine.REXPGenericVector;
import org.rosuda.REngine.REXPInteger;
import org.rosuda.REngine.REXPList;
import org.rosuda.REngine.REXPLogical;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.REXPNull;
import org.rosuda.REngine.REXPRaw;
import org.rosuda.REngine.REXPString;
import org.rosuda.REngine.REXPSymbol;
import org.rosuda.REngine.RList;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

/**
 * R utility
 * 
 * @author joe
 *
 */
public class R extends IStub {

	public R(String name) {
		super(name);
	}

	static Log log = LogFactory.getLog(R.class);

	public static R inst = new R("r");
	private static String ROOT;
	private static boolean inited = false;

	public static void serve() {

		ROOT = Controller.GIIWA_HOME + "/temp/_R/";
		new File(ROOT).mkdirs();

		String host = Config.getConf().getString("r.host", X.EMPTY);

		if (X.isIn(host, "127.0.0.1", X.EMPTY) && !inited) {
			// local
			try {
				inst.bind();
				inited = true;
			} catch (Exception e) {
				log.error(e.getMessage(), e);
				Task.schedule(() -> {
					serve();
				}, 3000);
			}
		}

	}

	@SuppressWarnings("rawtypes")
	public Object run(String code) throws Exception {
		return run(code, null, (List) null, false);
	}

	@SuppressWarnings("rawtypes")
	public Object run(String code, String dataname, List data) throws Exception {
		return run(code, dataname, data, false);
	}

	/**
	 * run the R code in sanbox
	 * 
	 * @param code
	 * @param name
	 * @param data
	 * @param header
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings("rawtypes")
	public Object run(String code, String dataname, List data, boolean head) throws Exception {

		String host = Config.getConf().getString("r.host", X.EMPTY);

		if (X.isIn(host, "127.0.0.1", X.EMPTY)) {
			// local
			return _run(code, dataname, data, head);

		} else {

			JSON j1 = JSON.create();
			j1.append("c", code).append("dn", dataname).append("d", data).append("h", head ? 1 : 0);
			return MQ.call(inst.name, Request.create().put(j1), X.AMINUTE * 60);
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Object _run(String code, String dataname, List data, boolean head) throws Exception {

		_check();

		RConnection c = conn;

		if (c != null) {

			String func = "f" + c.hashCode();

			StringBuilder sb = new StringBuilder();

			// save to file
			try {

				Temp temp = null;
				if (!X.isEmpty(data)) {
					temp = Temp.create("data");

					String s1 = _export(dataname, (List<Object>) data, head, temp);
					if (!X.isEmpty(s1)) {
						sb.append(s1).append("\n");
					}
				}

				Temp t = Temp.create("a.txt");
				File f = t.getFile();
				f.getParentFile().mkdirs();
				sb.append(func + "<-function(){\n");
				sb.append("sink(file=\"" + f.getAbsolutePath() + "\");\n");
				sb.append(code).append("\nsink(file=NULL)\n};\n" + func + "();");

//				System.out.println(sb);
//
				if (log.isDebugEnabled())
					log.debug("R.run, code=\n" + sb);

				c.eval(sb.toString());

				if (temp != null) {
					// TODO
//					temp.delete();
				}

				String r = IOUtil.read(f, "UTF8");

				if (log.isDebugEnabled())
					log.debug("R.run, result=\n" + r);

				t.delete();
				return r;

//				System.out.println(r2J2(x));

//				Object s1 = r2J(x);
//				return JSON.create().append("data", s1);
			} catch (RserveException re) {

				log.error(sb.toString() + ", error=" + re.getRequestErrorDescription(re.getRequestReturnCode()), re);
				throw re;
			} finally {

				c.eval("rm(" + func + ")");
				c.eval("rm(" + dataname + ")");
//				c.eval("rm(" + func + ", " + dataname + ")");

			}
		} else {
			log.error("R.run, c=null");
		}

		return null;

	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private String _export(String dataname, List data, boolean head, Temp t) throws Exception {

		if (dataname == null || data == null || data.isEmpty()) {
			return X.EMPTY;
		}

		StringBuilder sb = new StringBuilder();

		Object o1 = data.get(0);

		Object[] hh = (o1 instanceof Map) ? (((Map) o1).keySet().toArray()) : null;

		Exporter<Object> ex = t.export(Exporter.FORMAT.plain);
		if (head && hh != null) {
			ex.print(hh);
		}

		ex.createSheet(e -> {
			if (e == null)
				return null;
			if (e.getClass().isArray())
				return (Object[]) e;

			if (e instanceof List)
				return ((List) e).toArray();

			if (e instanceof Map) {
				Map m = (Map) e;
				Object[] o = new Object[hh.length];
				for (int i = 0; i < hh.length; i++) {
					o[i] = m.get(hh[i]);
				}

				return o;
			}
			return new Object[] { e };
		});

		ex.print((List<Object>) data);
		ex.close();

		sb.append(dataname + " <- read.csv('" + t.getFile().getCanonicalPath() + "',");
		if (head) {
			sb.append("header=T,");
		} else {
			sb.append("header=F,");
		}
		sb.append("stringsAsFactors=TRUE);");

		return sb.toString();

	}

	private Object r2J(REXP x) throws REXPMismatchException {

		if (x instanceof REXPDouble) {
			double[] d1 = x.asDoubles();

			if (d1.length == 1) {
				if (Double.isNaN(d1[0]))
					return x.asString();
				return d1[0];
			}

			return X.asList(d1, e -> e);
		}

		if (x instanceof REXPInteger) {
			int[] ii = x.asIntegers();
			if (ii.length == 1)
				return ii[0];

			return X.asList(ii, e -> e);
		}

		if (x instanceof REXPLogical || x instanceof REXPRaw || x instanceof REXPString || x instanceof REXPSymbol) {
			String[] ss = x.asStrings();
			if (ss == null || ss.length == 0) {
				return null;
			} else if (ss.length == 1) {
				return ss[0];
			} else {
				return Arrays.asList(ss);
			}
		}

		if (x instanceof REXPGenericVector) {
			REXPGenericVector x1 = (REXPGenericVector) x;

			RList r1 = x1.asList();
			List<Object> l2 = new ArrayList<Object>();
			for (int i = 0; i < r1.size(); i++) {
				Object o = r1.get(i);
				if (o instanceof REXP) {
					l2.add(r2J((REXP) o));
				}
			}
			return l2;
		}

		if (x instanceof REXPList) {
			REXPList x1 = (REXPList) x;

			RList r1 = x1.asList();
			List<Object> l2 = new ArrayList<Object>();
			for (int i = 0; i < r1.size(); i++) {
				Object o = r1.get(i);
//				System.out.println("o=" + o);
				if (o instanceof REXP) {
					l2.add(r2J((REXP) o));
				}
			}
			return l2;
		}

		if (x instanceof REXPNull) {
			return null;
		}

		String[] ss = x.asStrings();
		if (ss == null || ss.length == 0) {
			return null;
		} else if (ss.length == 1) {
			return ss[0];
		} else {
			return Arrays.asList(ss);
		}

	}

	private String r2J2(REXP x) throws REXPMismatchException {

		if (x instanceof REXPDouble) {
			double[] d1 = x.asDoubles();

			if (d1.length == 1) {
				if (Double.isNaN(d1[0]))
					return x.asString();
				return Double.toString(d1[0]);
			}

			return X.asList(d1, e -> e).toString();
		}

		if (x instanceof REXPInteger) {
			int[] ii = x.asIntegers();
			if (ii.length == 1)
				return Integer.toString(ii[0]);

			return X.asList(ii, e -> e).toString();
		}

		if (x instanceof REXPLogical || x instanceof REXPRaw || x instanceof REXPString || x instanceof REXPSymbol) {
			String[] ss = x.asStrings();
			if (ss == null || ss.length == 0) {
				return null;
			} else if (ss.length == 1) {
				return ss[0];
			} else {
				return Arrays.asList(ss).toString();
			}
		}

		if (x instanceof REXPGenericVector) {
			REXPGenericVector x1 = (REXPGenericVector) x;

			RList r1 = x1.asList();
			List<Object> l2 = new ArrayList<Object>();
			for (int i = 0; i < r1.size(); i++) {
				Object o = r1.get(i);
				if (o instanceof REXP) {
					l2.add(r2J2((REXP) o));
				}
			}
			return l2.toString();
		}

		if (x instanceof REXPList) {
			REXPList x1 = (REXPList) x;

			RList r1 = x1.asList();
			List<Object> l2 = new ArrayList<Object>();
			for (int i = 0; i < r1.size(); i++) {
				Object o = r1.get(i);
//				System.out.println("o=" + o);
				if (o instanceof REXP) {
					l2.add(r2J2((REXP) o));
				}
			}
			return l2.toString();
		}

		if (x instanceof REXPNull) {
			return null;
		}

		String[] ss = x.asStrings();
		if (ss == null || ss.length == 0) {
			return null;
		} else if (ss.length == 1) {
			return ss[0];
		} else {
			return Arrays.asList(ss).toString();
		}

	}

	private static RConnection conn = null;

//	private static Pool<RConnection> pool = null;

	synchronized void _check() {

		if (conn != null)
			return;

		try {
			String host = Config.getConf().getString("r.host", X.EMPTY);
			int port = Config.getConf().getInt("r.port", 6311);

			if (X.isEmpty(host)) {
				conn = new RConnection();
			} else {
				conn = new RConnection(host, port);
			}
			return;

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static void main(String[] args) {

		Task.init(10);

		R.ROOT = "/Users/joe/d/temp/";

//		String s = "mean(b)";
		try {
			Map<String, List<Object[]>> p1 = new HashMap<String, List<Object[]>>();
			p1.put("b", Arrays.asList(X.split("10, 20, 30", "[, ]"), X.split("10, 20, 30", "[, ]"),
					X.split("10, 20, 30", "[, ]")));

			System.out.println(inst.run("summary(d);", "d", Arrays.asList(1, 2, 3, 100)));

			Object j1 = inst.run(
					"f509376766<-function(){x <- c(214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,214,90,93,90,106,214,214,214,214,214,214);fivenum(x)};f509376766();");
			System.out.println(j1);

			TimeStamp t = TimeStamp.create();

			Object[] ll = new Object[10];
			for (int i = 0; i < ll.length; i++) {
				ll[i] = (long) (ll.length * Math.random());
			}

			t.reset();
			Object r = inst.run("count(data)", ll);
			System.out.println(r + ", cost=" + t.past());

			Object r1 = inst.run("median(data)", ll);
			System.out.println(r1 + ", cost=" + t.past());

			for (int i = 0; i < ll.length; i++) {
				ll[i] = i + 1;
			}
			t.reset();
			double d1 = Arrays.asList(ll).stream().mapToInt(e -> {
				return X.toInt(e);
			}).average().getAsDouble();
			System.out.println(d1 + ", cost=" + t.past());

//			j1 = inst.run("ls()");
//			System.out.println(j1.get("data"));

			t.reset();
			List l2 = new ArrayList<Object>();
			l2.add(ll);
			r = inst.run("mean(c1)", "c1", Arrays.asList(ll), false);

			System.out.println(r + ", cost=" + t.past());

			List<JSON> l1 = JSON.createList();
			l1.add(JSON.create().append("a", 1).append("b", 2).append("c", 3).append("d", 4));
			l1.add(JSON.create().append("a", 1).append("b", 32).append("c", 3).append("d", 4));
			l1.add(JSON.create().append("a", 10).append("b", 22).append("c", 39).append("d", 4));
			l1.add(JSON.create().append("a", 1).append("b", 21).append("c", 3).append("d", 4));
			l1.add(JSON.create().append("a", 1).append("b", 42).append("c", 3).append("d", 4));

			t.reset();
			j1 = inst.run(
					"library(C50);d16<-C5.0(x=mtcars[, 1:5], y=as.factor(mtcars[,6]));save(d16, file=\"d16\");summary(d16);",
					null, null, false);
			System.out.println(j1 + ", cost=" + t.past());
//			System.out.println(j1.get("data"));

//			j1 = inst.run("ls()");
//			System.out.println("ls=" + j1.get("data"));

			t.reset();
			j1 = inst.run("load(file=\"d16\");summary(d16)");
			System.out.println("cost=" + t.past() + "//" + j1);

//			System.out.println(((List) j1.get("data")).get(0));

//			System.out.println(t1.toPrettyString());

			StringBuilder sb = new StringBuilder();
			sb.append("m1 <- read.csv('/Users/joe/d/temp/data',header=T,stringsAsFactors=TRUE);\n");
			sb.append("library(vegan)\n");
			sb.append("a <- vegdist(m1, method = 'bray')\n");
			sb.append("a <- anosim(a, m1$mpg, permutations = 999)\n");
			sb.append("print(a)");

			j1 = inst.run(sb.toString());
			System.out.println(j1);

//			List l3 = (List) j1.get("data");
//			for (int i = 0; i < l3.size(); i++) {
//				System.out.println(i + "=> " + l3.get(i));
//			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public Object run(String code, Object[] data) throws Exception {

		if (X.isIn(code, "mean(data)", "avg(data)", "mean", "avg")) {
			return mean(data);
		} else if (X.isIn(code, "sum(data)", "sum")) {
			return sum(data);
		} else if (X.isIn(code, "max(data)", "max")) {
			return max(data);
		} else if (X.isIn(code, "min(data)", "min")) {
			return min(data);
		} else if (X.isIn(code, "count(data)", "count", "length(data)", "length")) {
			return count(data);
		} else if (X.isIn(code, "value_count(data)", "value_count")) {
			return value_count(data);
		} else if (X.isIn(code, "cv", "cv(data)")) {
			Object sd = run("sd(data)", data);
			Object mean = run("mean(data)", data);
			if (X.isEmpty(sd) || X.isEmpty(mean) || X.toDouble(mean) != 0)
				return null;

			return X.toDouble(sd) / X.toDouble(mean);
		}

		_check();

		RConnection c = conn;

		if (c != null) {

			// save to file
			Temp t = Temp.create("data");
			try {
				StringBuilder sb = new StringBuilder();
				String func = "f" + c.hashCode();
				sb.append(func + "<-function(){");
				if (!X.isEmpty(data)) {
					File f = t.getFile();
					f.getParentFile().mkdirs();
					FileWriter f1 = new FileWriter(f);
					for (Object o : data) {
						f1.write(o + " ");
					}
					f1.close();
					sb.append("data <- scan('" + f.getAbsolutePath() + "');");
				}

				sb.append(code).append("};" + func + "();");

				if (log.isDebugEnabled())
					log.debug("R.run, code=" + sb);

				REXP x = c.eval(sb.toString());

				String[] ss = x.asStrings();
				if (ss == null || ss.length == 0) {
					return null;
				} else if (ss.length == 1) {
					return ss[0];
				} else {
					return ss;
				}

			} finally {

				// TODO
//				t.delete();
			}
		}

		return null;

	}

	public Object mean(Object[] data) throws Exception {
		double d = Arrays.asList(data).parallelStream().mapToDouble(e -> {
			return X.toDouble(e);
		}).average().getAsDouble();
		return d;
	}

	public Object sum(Object[] data) throws Exception {
		double d = Arrays.asList(data).parallelStream().mapToDouble(e -> {
			return X.toDouble(e);
		}).sum();
		return d;
	}

	public Object count(Object[] data) throws Exception {
		return data.length;
	}

	public Object value_count(Object[] data) throws Exception {
		long d = Arrays.asList(data).parallelStream().distinct().count();
		return d;
	}

	public Object range(Object[] data) throws Exception {
		DoubleStream d = Arrays.asList(data).parallelStream().mapToDouble(e -> {
			return X.toDouble(e);
		});
		return d.max().getAsDouble() - d.min().getAsDouble();
	}

	public Object max(Object[] data) throws Exception {
		double d = Arrays.asList(data).parallelStream().mapToDouble(e -> {
			return X.toDouble(e);
		}).max().getAsDouble();
		return d;
	}

	public Object min(Object[] data) throws Exception {
		double d = Arrays.asList(data).parallelStream().mapToDouble(e -> {
			return X.toDouble(e);
		}).min().getAsDouble();
		return d;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void onRequest(long seq, Request req) {

		try {

			JSON j1 = req.get();

			Object j2 = this._run(j1.getString("c"), j1.getString("dn"), (List) j1.get("d"), j1.getInt("h") == 1);
			req.response(j2);

		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}

	}

}
