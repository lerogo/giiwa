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
package org.giiwa.app.task;

import java.util.List;

import org.giiwa.core.bean.Helper.V;
import org.giiwa.core.bean.Helper.W;
import org.giiwa.core.bean.X;
import org.giiwa.core.task.StatTask;
import org.giiwa.framework.bean.Node;
import org.giiwa.framework.bean.Stat;
import org.giiwa.framework.bean.Stat.SIZE;

/**
 * The Class NodeLoadStatTask.
 */
public class NodeLoadStatTask extends StatTask {

	/**
	 * The Constant serialVersionUID.
	 */
	private static final long serialVersionUID = 1L;

	public static void init() {
		inst = new NodeLoadStatTask();
		inst.schedule((long) (X.AMINUTE * Math.random()));
	}

	@Override
	protected SIZE[] getSizes() {
		return new Stat.SIZE[] { Stat.SIZE.min };
	}

	@Override
	public void onFinish() {
		this.schedule(X.AMINUTE);
	}

	@Override
	public String getName() {
		return "gi.load";
	}

	@Override
	protected void onStat(SIZE size, long start, long end, Object cat) {
		if (cat == null)
			return;

		String id = cat.toString();
		Node e = Node.dao.load(id);
		long[] ff = new long[13];

		if (e.getUpdated() < System.currentTimeMillis() - Node.LOST) {
			ff[0] = -1;
			ff[1] = -1;
			ff[2] = -1;
			ff[3] = -1;
			ff[4] = -1;
			ff[5] = -1; // dfile.times
			ff[6] = -1; // dfile.max
			ff[7] = -1; // dfile.min
			ff[8] = -1; // dfile.avg
			ff[9] = -1; // dfile.times_c
			ff[10] = -1; // dfile.max_c
			ff[11] = -1; // dfile.min_c
			ff[12] = -1; // dfile.avg_c
		} else {
			ff[0] = e.getUsage();
			ff[1] = e.getLong("globaltasks");
			ff[2] = e.getLong("localthreads");
			ff[3] = e.getLong("localrunning");
			ff[4] = e.getLong("localpending");
			ff[5] = e.getLong("dfiletimes");
			ff[6] = e.getLong("dfilemaxcost");
			ff[7] = e.getLong("dfilemincost");
			ff[8] = e.getLong("dfileavgcost");
			ff[9] = e.getLong("dfiletimes_c");
			ff[10] = e.getLong("dfilemaxcost_c");
			ff[11] = e.getLong("dfilemincost_c");
			ff[12] = e.getLong("dfileavgcost_c");
		}

		Stat.snapshot(start, "node.load." + id, size, W.create(), V.create(), ff);

	}

	@Override
	protected List<?> getCategories() {
		return Node.dao.distinct("id", W.create());
	}

}
