/**
 *  Copyright 2017 Salvatore Giampà
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  
 **/

package agilermi;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

class Skeleton {
	private static long nextId = 0;

	private RmiRegistry rmiRegistry;
	private int refGlobalCounter = 0;
	private Map<RmiHandler, Integer> refCounters = new HashMap<>();
	private Object object;
	private String id;
	private Set<String> names = new TreeSet<>();

	public Skeleton(Object object, RmiRegistry rmiRegistry) {
		id = "#" + (nextId++);
		this.object = object;
		this.rmiRegistry = rmiRegistry;
	}

	public int getRefGlobalCounter() {
		return refGlobalCounter;
	}

	public synchronized void removeRef(RmiHandler rmiHandler) {
		Integer count = refCounters.get(rmiHandler);
		if (count == null)
			return;
		count--;
		refCounters.put(rmiHandler, count);
		refGlobalCounter--;

		scheduleRemove();
	}

	public synchronized void addRef(RmiHandler rmiHandler) {
		Integer count = refCounters.get(rmiHandler);
		if (count == null)
			refCounters.put(rmiHandler, 1);
		else {
			count = refCounters.get(rmiHandler) + 1;
			refCounters.put(rmiHandler, count);
		}
		refGlobalCounter++;
	}

	public synchronized void removeAllRefs(RmiHandler rmiHandler) {
		Integer count = refCounters.remove(rmiHandler);
		refGlobalCounter -= count;
		scheduleRemove();
	}

	private Thread scheduledRemove = null;

	private synchronized void scheduleRemove() {
		if (refGlobalCounter == 0 && names.isEmpty()) {
			if (scheduledRemove != null)
				scheduledRemove.interrupt();
			scheduledRemove = new Thread(() -> {
				try {
					Thread.sleep(10000);
				} catch (InterruptedException e) {
					return;
				}
				synchronized (Skeleton.this) {
					if (refGlobalCounter == 0 && names.isEmpty()) {
						rmiRegistry.unpublish(object);
					}
				}
			});
			scheduledRemove.start();
		}
	}

	public void addNames(String name) {
		names.add(name);
	}

	public void removeNames(String name) {
		names.remove(name);
	}

	public Set<String> names() {
		return Collections.unmodifiableSet(names);
	}

	public Object getObject() {
		return object;
	}

	public String getId() {
		return id;
	}

	public Object invoke(String method, Class<?>[] parameterTypes, Object[] parameters) throws IllegalAccessException,
			IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {

		if (method.equals("equals") && parameters.length == 1 && Proxy.isProxyClass(parameters[0].getClass())) {
			InvocationHandler ih = Proxy.getInvocationHandler(parameters[0]);
			if (ih instanceof RemoteInvocationHandler) {
				RemoteInvocationHandler rih = (RemoteInvocationHandler) ih;
				if (this.id.equals(rih.getObjectId()))
					return true;
				if (names.contains(rih.getObjectId()))
					return true;
			}
		}

		Method met = object.getClass().getMethod(method, parameterTypes);

		// set the method accessible
		met.setAccessible(true);

		return met.invoke(object, parameters);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((object == null) ? 0 : object.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;

		if (obj instanceof Skeleton) {
			Skeleton other = (Skeleton) obj;
			if (object == null) {
				if (other.object != null)
					return false;
			} else if (!object.equals(other.object))
				return false;
			return true;
		}

		return false;
	}

}
