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
package org.giiwa.misc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.giiwa.dao.TimeStamp;
import org.giiwa.task.Task;

/**
 * A general pool class, that can be for database , or something else
 * 
 * @author joe
 *
 */
public class Pool<E> {

	static Log log = LogFactory.getLog(Pool.class);

	private ReentrantLock lock = new ReentrantLock();
	private Condition door = lock.newCondition();

	private Map<E, _E> cached = new HashMap<E, _E>();
	private List<_E> idle = new ArrayList<_E>();
	private int initial = 10;
	private int max = 10;
	private int created = 0;
	private int age = 600; // second

	private IPoolFactory<E> factory = null;

	private class _E {
		E e;
		long last = System.currentTimeMillis();

		_E(E e) {
			this.e = e;
			cached.put(e, this);
		}

	}

	private _E _get(E e) {
		_E e1 = cached.get(e);
		if (e1 == null) {
			e1 = new _E(e);
		}
		return e1;
	}

	/**
	 * create a pool by initial, max and factory.
	 *
	 * @param <E>     the element type
	 * @param initial the initial
	 * @param max     the max
	 * @param factory the factory
	 * @return the pool
	 */
	public static <E> Pool<E> create(int initial, int max, IPoolFactory<E> factory) {
		Pool<E> p = new Pool<E>();
		p.initial = initial;
		p.max = max;
		p.factory = factory;

		Task.schedule(() -> {
			p.init();
		}, 0);
		return p;
	}

	private void init() {
		for (int i = 0; i < initial; i++) {
			E t = factory.create();
			if (t != null) {
				try {
					lock.lock();
					idle.add(_get(t));
					door.signal();
				} finally {
					lock.unlock();
				}
			}
		}
		created = idle.size();
	}

	/**
	 * release a object to the pool.
	 *
	 * @param t the t
	 */
	public void release(E t) {
		if (t == null) {
			created--;
		} else {
			try {
				lock.lock();

				_E e = _get(t);
				if (System.currentTimeMillis() - e.last < age && factory.cleanup(t)) {
					idle.add(e);
				} else {
					// the t is bad
					cached.remove(t);
					factory.destroy(t);
					created--;
				}
				door.signal();
			} finally {
				lock.unlock();
			}
		}
	}

	/**
	 * destroy the pool, and destroy all the object in the pool.
	 */
	public void destroy() {
		synchronized (idle) {
			for (_E e : idle) {
				cached.remove(e.e);
				factory.destroy(e.e);
			}
			idle.clear();
		}
	}

	/**
	 * get a object from the pool, if meet the max, then wait till timeout.
	 *
	 * @param timeout the timeout
	 * @return the e
	 */
	public E get(long timeout) {
		try {
			TimeStamp t = TimeStamp.create();

			long t1 = timeout;

			try {
				lock.lock();

				while (t1 > 0) {
					if (idle.size() > 0) {
						_E e = idle.remove(0);
						return e.e;
					} else {
						t1 = timeout - t.pastms();
						if (t1 > 0) {

							// log.debug("t1=" + t1);
							//
							if (created < max) {

								Task.schedule(() -> {
									E e = factory.create();
									if (e != null) {
										created++;
										release(e);
									}
								}, 0);
							}

							door.awaitNanos(TimeUnit.MILLISECONDS.toNanos(t1));
						}
					}
				}
			} finally {
				lock.unlock();
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}

		return null;
	}

	/**
	 * the pool factory interface using to create E object in pool
	 * 
	 * @author wujun
	 *
	 * @param <E> the Object
	 */
	public interface IPoolFactory<E> {

		/**
		 * create a object.
		 *
		 * @return the e
		 */
		public E create();

		/**
		 * clean up a object after used.
		 *
		 * @param t the t
		 */
		public boolean cleanup(E t);

		/**
		 * destroy a object.
		 *
		 * @param t the t
		 */
		public void destroy(E t);

	}
}
