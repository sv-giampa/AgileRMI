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
import java.util.AbstractMap.SimpleEntry;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import agilermi.configuration.Unreferenced;
import agilermi.utility.logging.RMILogger;

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

	private RMIRegistry rmiRegistry;
	private int refGlobalCounter = 0;
	private Map<RMIHandler, Integer> refCounters = new HashMap<>();
	private Object object;
	private String id;
	private Set<String> names = new TreeSet<>();
	private long lastUseTime;

	private RMILogger logger = RMILogger.get(Skeleton.class);

	private int cacheSize;

	private Map<SimpleEntry<String, Long>, Object> invocationsCache = Collections
			.synchronizedMap(new LinkedHashMap<SimpleEntry<String, Long>, Object>(cacheSize, 0.75f, true) {
				private static final long serialVersionUID = -6061814438769261316L;

				@Override
				protected boolean removeEldestEntry(Map.Entry<SimpleEntry<String, Long>, Object> eldest) {
					return size() > cacheSize;
				}
			});

	Skeleton(Object object, RMIRegistry rmiRegistry) {
		id = IDENTIFIER_PREFIX + (nextId++);
		this.object = object;
		this.rmiRegistry = rmiRegistry;
		this.cacheSize = rmiRegistry.getSkeletonInvocationCacheSize();
		updateLastUseTime();
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
			logger.log("removed remote reference shared with '%s'\t(Object=%s; Class=%s)",
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
				rmiRegistry.getSkeletonByObjectMap().put(object, this);
				rmiRegistry.getSkeletonByIdMap().put(id, this);
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
			logger.log("added remote reference shared with '%s'\t(Object=%s; Class=%s)",
					rMIHandler.getInetSocketAddress().toString(), object, object.getClass().getName());
	}

	synchronized void removeAllRefs(RMIHandler rMIHandler) {
		Integer count = refCounters.remove(rMIHandler);
		refGlobalCounter -= count;
		if (Debug.SKELETONS)
			logger.log("removed all remote references shared with '%s'\t(Object=%s; Class=%s)",
					rMIHandler.getInetSocketAddress().toString(), object, object.getClass().getName());
		scheduleRemoval();
	}

	public void unpublish() {
		if (object instanceof Unreferenced)
			((Unreferenced) object).unreferenced();

		if (Debug.SKELETONS)
			logger.log("removed from registry\t(Object=%s; Class=%s)", object, object.getClass().getName());
		rmiRegistry.unpublish(object);
	}

	private Future<?> scheduledRemoval;

	private synchronized void scheduleRemoval() {
		if (Debug.SKELETONS)
			logger.log("trying to schedule removal\t(Object=%s; Class=%s)", object, object.getClass().getName());
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
				logger.log("scheduling removal\t(Object=%s; Class=%s)", object, object.getClass().getName());

			// schedule the skeleton remove operation to be executed afte 10 seconds, to
			// avoid errors caused by network latecies (e.g. when a machine pass a remote
			// reference of this skeleton to another machine)
			scheduledRemoval = rmiRegistry.executorService.schedule(() -> {
				synchronized (Skeleton.this) {
					if (refGlobalCounter == 0 && names.isEmpty()) {
						unpublish();
					}
				}
			}, rmiRegistry.getLatencyTime(), TimeUnit.MILLISECONDS);

			if (Debug.SKELETONS)
				logger.log("removal scheduled at %d ms\t(Object=%s; Class=%s)", rmiRegistry.getLatencyTime(), object,
						object.getClass().getName());
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

	public long getLastUseTime() {
		return lastUseTime;
	}

	void updateLastUseTime() {
		lastUseTime = System.currentTimeMillis();
	}

	Object invoke(String method, Class<?>[] parameterTypes, Object[] parameters, String remoteRegistryKey,
			long invocationId) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException,
			NoSuchMethodException, SecurityException {

		updateLastUseTime();

		SimpleEntry<String, Long> cacheKey = new SimpleEntry<String, Long>(remoteRegistryKey, invocationId);

		synchronized (invocationsCache) {
			if (invocationsCache.containsKey(cacheKey)) {
				return invocationsCache.get(cacheKey);
			}
		}

		Method met = object.getClass().getMethod(method, parameterTypes);

		if (Modifier.isPrivate(met.getModifiers()))
			throw new IllegalAccessException("This method is private. It must not be accessed over RMI.");
		if (Modifier.isStatic(met.getModifiers()))
			throw new IllegalAccessException("This method is static. It must not be accessed over RMI.");

		// set the method accessible (it becomes accessible from the library)
		boolean accessible = met.isAccessible();
		met.setAccessible(true);
		try {
			Object result = met.invoke(object, parameters);
			invocationsCache.put(cacheKey, result);
			return result;
		} finally {
			met.setAccessible(accessible);
		}
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
