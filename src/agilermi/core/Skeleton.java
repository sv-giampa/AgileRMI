/**
 *  Copyright 2018-2019 Salvatore Giampà
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

package agilermi.core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import agilermi.configuration.Unreferenced;

/**
 * Represents a local remote object exposed by a local {@link RMIRegistry}
 * instance on the network. An instance of this class is a proxy for a remote
 * object. Instances of this class count the remote references to a remote
 * object and act the distributed garbage collection mechanism. This is the main
 * component used to act the distributed garbage collection.
 * 
 * @author Salvatore Giampa'
 *
 */
final class Skeleton {
	public static String IDENTIFIER_PREFIX = "###";
	private static long nextId = 0;

	private RMIRegistry rMIRegistry;
	private int refGlobalCounter = 0;
	private Map<RMIHandler, Integer> refCounters = new HashMap<>();
	private Object object;
	private String id;
	private Set<String> names = new TreeSet<>();

	Skeleton(Object object, RMIRegistry rMIRegistry) {
		id = IDENTIFIER_PREFIX + (nextId++);
		this.object = object;
		this.rMIRegistry = rMIRegistry;
		scheduleRemoval();
	}

	int getRefGlobalCounter() {
		return refGlobalCounter;
	}

	synchronized void removeRef(RMIHandler rMIHandler) {
		Integer count = refCounters.get(rMIHandler);
		if (count == null || count <= 0)
			return;
		count = count - 1;
		refCounters.put(rMIHandler, count);
		refGlobalCounter--;
		if (Debug.SKELETONS)
			System.out.printf("[Distributed GC] removed remote reference shared with '%s'\n\t(Object=%s; Class=%s)\n",
					rMIHandler.getInetSocketAddress().toString(), object, object.getClass().getName());
		scheduleRemoval();
	}

	synchronized void addRef(RMIHandler rMIHandler) {
		if (scheduledRemoval != null) {
			if (scheduledRemoval.cancel(false)) {
				scheduledRemoval = null;
			} else {
				try {
					scheduledRemoval.get();
				} catch (InterruptedException e) {
				} catch (ExecutionException e) {
				}
				rMIRegistry.skeletonByObject.put(object, this);
				rMIRegistry.skeletonById.put(id, this);
			}
		}

		Integer count = refCounters.get(rMIHandler);
		if (count == null)
			refCounters.put(rMIHandler, 1);
		else {
			count = refCounters.get(rMIHandler) + 1;
			refCounters.put(rMIHandler, count);
		}
		refGlobalCounter++;
		if (Debug.SKELETONS)
			System.out.printf("[Distributed GC] added remote reference shared with '%s'\n\t(Object=%s; Class=%s)\n",
					rMIHandler.getInetSocketAddress().toString(), object, object.getClass().getName());
	}

	synchronized void removeAllRefs(RMIHandler rMIHandler) {
		Integer count = refCounters.remove(rMIHandler);
		refGlobalCounter -= count;
		if (Debug.SKELETONS)
			System.out.printf(
					"[Distributed GC] removed all remote references shared with '%s'\n\t(Object=%s; Class=%s)\n",
					rMIHandler.getInetSocketAddress().toString(), object, object.getClass().getName());
		scheduleRemoval();
	}

	private Future<?> scheduledRemoval;

	private synchronized void scheduleRemoval() {
		if (Debug.SKELETONS)
			System.out.printf("[Distributed GC] trying to schedule removal\n\t(Object=%s; Class=%s)\n", object,
					object.getClass().getName());
		if (refGlobalCounter == 0 && names.isEmpty()) {
			if (scheduledRemoval != null) {
				if (scheduledRemoval.cancel(false)) {
					scheduledRemoval = null;
				} else {
					if (!scheduledRemoval.isCancelled())
						return;
				}
			}
			if (Debug.SKELETONS)
				System.out.printf("[Distributed GC] scheduling removal\n\t(Object=%s; Class=%s)\n", object,
						object.getClass().getName());

			// schedule the skeleton remove operation to be executed afte 10 seconds, to
			// avoid errors caused by network latecies (e.g. when a machine pass a remote
			// reference of this skeleton to another machine)
			scheduledRemoval = rMIRegistry.executorService.schedule(() -> {
				synchronized (Skeleton.this) {
					if (refGlobalCounter == 0 && names.isEmpty()) {
						if (object instanceof Unreferenced)
							((Unreferenced) object).unreferenced();

						if (Debug.SKELETONS)
							System.out.printf("[Distributed GC] removed from registry\n\t(Object=%s; Class=%s)\n",
									object, object.getClass().getName());
						rMIRegistry.unpublish(object);
					}
				}
			}, rMIRegistry.getDgcLeaseValue(), TimeUnit.MILLISECONDS);

			if (Debug.SKELETONS)
				System.out.printf("[Distributed GC] removal scheduled at %d ms\n\t(Object=%s; Class=%s)\n",
						rMIRegistry.getDgcLeaseValue(), object, object.getClass().getName());
		}
	}

	void addNames(String name) {
		names.add(name);
	}

	void removeNames(String name) {
		names.remove(name);
	}

	Set<String> names() {
		return Collections.unmodifiableSet(names);
	}

	Object getObject() {
		return object;
	}

	String getId() {
		return id;
	}

	Object invoke(String method, Class<?>[] parameterTypes, Object[] parameters) throws IllegalAccessException,
			IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {

		Method met = object.getClass().getMethod(method, parameterTypes);

		if (Modifier.isPrivate(met.getModifiers()))
			throw new IllegalAccessException("This method is private. It must not be accessed over RMI.");
		if (Modifier.isStatic(met.getModifiers()))
			throw new IllegalAccessException("This method is static. It must not be accessed over RMI.");

		// set the method accessible (it becomes accessible by the library)
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
