package coarsermi;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Defines a registry of exported objects on the local machine. This data
 * structure can be shared among more than one ObjectPeer to obtain a multi-peer
 * interoperability.
 * 
 * @author Salvatore Giampa'
 *
 */
public class ObjectRegistry {
	private long nextId = 0; // incremental generation of object identifiers

	/**
	 * Defines a (service <-> implementation) exporting
	 * 
	 * @author Salvatore Giampa'
	 *
	 */
	private static class Entry {
		Object object; // implementation
		Class<?> remoteIf; // interface

		public Entry(Object object, Class<?> remoteIf) {
			this.object = object;
			this.remoteIf = remoteIf;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((object == null) ? 0 : object.hashCode());
			result = prime * result + ((remoteIf == null) ? 0 : remoteIf.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Entry other = (Entry) obj;
			if (object == null) {
				if (other.object != null)
					return false;
			} else if (!object.equals(other.object))
				return false;
			if (remoteIf == null) {
				if (other.remoteIf != null)
					return false;
			} else if (!remoteIf.equals(other.remoteIf))
				return false;
			return true;
		}

	}

	// search index over identifiers
	private Map<String, Entry> byId = new HashMap<>();

	// search index over <object,interface> couples
	private Map<Entry, String> byEntry = new HashMap<>();

	// automatically referenced interfaces
	private Set<Class<?>> autoReferences = new HashSet<>();

	// failure observers
	private Set<FailureObserver> failureObservers = new HashSet<>();

	/**
	 * Publish the given object respect to the specified interface. The identifier
	 * is automatically generated and returned
	 * 
	 * @param object   the implementation of the service to publish
	 * @param remoteIf the interface of the service to publish
	 * @return the generated identifier
	 */
	public String publish(Object object, Class<?> remoteIf) {
		Entry entry = new Entry(object, remoteIf);
		if (byEntry.containsKey(entry)) {
			return byEntry.get(entry);
		} else {
			String id = String.valueOf(nextId++);
			publish(id, entry);
			return id;
		}
	}

	private void publish(String id, Entry entry) {
		if (byId.containsKey(id))
			throw new IllegalArgumentException("the given id '" + id + "' is already bound.");
		byEntry.put(entry, id);
		byId.put(id, entry);
	}

	/**
	 * Publish the given object respect to the specified interface.
	 * 
	 * @param id       the identifier to use for this service
	 * @param object   the implementation of the service to publish
	 * @param remoteIf the interface of the service to publish
	 * @return the generated identifier
	 * @throws IllegalArgumentException if the specified identifier was already
	 *                                  bound
	 */
	public void publish(String id, Object object, Class<?> remoteIf) {
		Entry entry = new Entry(object, remoteIf);
		publish(id, entry);
	}

	/**
	 * Unpublish an object respect to the given interface
	 * 
	 * @param object   the implementation to unpublish
	 * @param remoteIf the interface respect to unpublish
	 */
	public void unpublish(Object object, Class<?> remoteIf) {
		String id = byEntry.remove(new Entry(object, remoteIf));
		if (id != null)
			byId.remove(id);
	}

	/**
	 * Unpublish the service with the specified identifier
	 * 
	 * @param id the id of the service
	 */
	public void unpublish(String id) {
		Entry entry = byId.remove(id);
		if (entry != null)
			byEntry.remove(entry);
	}

	/**
	 * Attach a {@link FailureObserver} object.
	 * @param o the failure observer
	 */
	public void attachFailureObserver(FailureObserver o) {
		failureObservers.add(o);
	}

	/**
	 * Detach a {@link FailureObserver} object.
	 * @param o the failure observer
	 */
	public void detachFailureObserver(FailureObserver o) {
		failureObservers.remove(o);
	}

	// Package-level operation used to send a failure to the failure observers
	void sendFailure(ObjectPeer objectPeer, Exception exception) {
		failureObservers.forEach(o -> o.failure(objectPeer, exception));
	}

	// Package-level operation used to get an object by identifier
	Object getObject(String id) {
		return byId.get(id).object;
	}

	// Package-level operation used to get the identifier of a service
	String getId(Object object, Class<?> remoteIf) {
		return byEntry.get(new Entry(object, remoteIf));
	}

	/**
	 * Marks an interface to automatically create remote references for objects that
	 * are sent as arguments for an invocation or as return values. The objects are
	 * automatically publish on this registry when the related parameter of the stub
	 * method has a type that matches with the marked interface
	 * 
	 * @param remoteIf the interface to mark
	 */
	public void setAutoReferenced(Class<?> remoteIf) {
		if (!remoteIf.isInterface())
			throw new IllegalArgumentException("the specified class is not an interface");
		autoReferences.add(remoteIf);
	}

	/**
	 * Remove a automatic referencing mark for the interface. See the
	 * {@link ObjectRegistry#setAutoReferenced(Class)} method.
	 * 
	 * @param remoteIf the interface to unmark
	 */
	public void unsetAutoReferenced(Class<?> remoteIf) {
		autoReferences.remove(remoteIf);
	}

	/**
	 * Check if an interface is marked for automatic referencing. See the
	 * {@link ObjectRegistry#setAutoReferenced(Class)} method.
	 * 
	 * @param remoteIf the interface to check
	 * @return true if the interface is marked for automatic referencing, false
	 *         otherwise
	 */
	public boolean isAutoReferenced(Class<?> remoteIf) {
		return autoReferences.contains(remoteIf);
	}
}
