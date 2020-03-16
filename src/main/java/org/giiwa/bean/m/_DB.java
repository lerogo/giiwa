package org.giiwa.bean.m;

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

@Table(name = "gi_m_db", memo = "GI-数据库监测")
public class _DB extends Bean {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static Log log = LogFactory.getLog(_DB.class);

	public static BeanDAO<String, _DB> dao = BeanDAO.create(_DB.class);

	@Column(memo = "唯一序号")
	String id;

	@Column(memo = "节点")
	String node;

	@Column(memo = "名称")
	String name;

	@Column(memo = "最大")
	long max;

	@Column(memo = "最小")
	long min;

	@Column(memo = "平均")
	long avg;

	@Column(memo = "次数")
	long times;

	public synchronized static void update(String node, JSON jo) {
		// insert or update
		try {
			String name = jo.getString("name");

			V v = V.fromJSON(jo);
			v.append("node", node);

			String id = UID.id(node, name);
			if (dao.exists(id)) {
				dao.update(id, v.copy());
			} else {
				dao.insert(v.copy().force(X.ID, id));
			}

			Record.dao.insert(v.force(X.ID, UID.id(node, name, System.currentTimeMillis())));

		} catch (Exception e) {
			log.error(jo, e);
		}
	}

	@Table(name = "gi_m_db_record", memo = "GI-数据库监测历史")
	public static class Record extends _DB {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		public static BeanDAO<String, Record> dao = BeanDAO.create(Record.class);

		public void cleanup() {
			dao.delete(W.create().and("created", System.currentTimeMillis() - X.AWEEK, W.OP.lt));
		}

	}

}
