package coarsersi;

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
	
	private boolean dispositionExceptionEnabled = true;
	
	public void setDispositionExceptionEnabled(boolean dispositionExceptionEnabled) {
		this.dispositionExceptionEnabled = dispositionExceptionEnabled;
	}
	
	public boolean isDispositionExceptionEnabled() {
		return dispositionExceptionEnabled;
	}

	private synchronized void publish(String id, Entry entry) {
		if (byId.containsKey(id))
			throw new IllegalArgumentException("the given id '" + id + "' is already bound.");
		byEntry.put(entry, id);
		byId.put(id, entry);
	}

	/**
	 * Publish the given object respect to the specified interface. The identifier
	 * is automatically generated and returned
	 * 
	 * @param object   the implementation of the service to publish
	 * @param remoteIf the interface of the service to publish
	 * @return the generated identifier
	 */
	public synchronized String publish(Object object, Class<?> remoteIf) {
		Entry entry = new Entry(object, remoteIf);
		if (byEntry.containsKey(entry)) {
			return byEntry.get(entry);
		} else {
			String id = "#" + String.valueOf(nextId++); //id pattern: /\#[0-9]+/
			publish(id, entry);
			return id;
		}
	}

	/**
	 * Publish the given object respect to the specified interface.
	 * 
	 * @param id       the identifier to use for this service
	 * @param object   the implementation of the service to publish
	 * @param remoteIf the interface of the service to publish
	 * @throws IllegalArgumentException if the specified identifier was already
	 *                                  bound or if the id parameter matches the
	 *                                  automatic referencing id pattern that is
	 *                                  /\#[0-9]+/
	 */
	public synchronized void publish(String id, Object object, Class<?> remoteIf) {
		if (id.matches("\\#[0-9]+"))
			throw new IllegalArgumentException(
					"The used identifier pattern /\\#[0-9]+/ is reserved to atomatic referencing. Please use another identifier pattern.");
		Entry entry = new Entry(object, remoteIf);
		publish(id, entry);
	}

	/**
	 * Unpublish an object respect to the given interface
	 * 
	 * @param object   the implementation to unpublish
	 * @param remoteIf the interface respect to unpublish
	 */
	public synchronized void unpublish(Object object, Class<?> remoteIf) {
		String id = byEntry.remove(new Entry(object, remoteIf));
		if (id != null)
			byId.remove(id);
	}

	/**
	 * Unpublish the service with the specified identifier
	 * 
	 * @param id the id of the service
	 */
	public synchronized void unpublish(String id) {
		if (id.matches("\\#[0-9]+"))
			throw new IllegalArgumentException(
					"The used identifier pattern /\\#[0-9]+/ is reserved to atomatic referencing. Please use another identifier pattern.");
		Entry entry = byId.remove(id);
		if (entry != null)
			byEntry.remove(entry);
	}

	/**
	 * Attach a {@link FailureObserver} object.
	 * 
	 * @param o the failure observer
	 */
	public synchronized void attachFailureObserver(FailureObserver o) {
		failureObservers.add(o);
	}

	/**
	 * Detach a {@link FailureObserver} object.
	 * 
	 * @param o the failure observer
	 */
	public synchronized void detachFailureObserver(FailureObserver o) {
		failureObservers.remove(o);
	}

	/**
	 * Package-level operation used to send a failure to the failure observers
	 * 
	 * @param objectPeer the object peer that caused the failure
	 * @param exception  the exception thrown by the object peer
	 */
	void sendFailure(ObjectPeer objectPeer, Exception exception) {
		failureObservers.forEach(o -> {
			try {
				o.failure(objectPeer, exception);
			} catch (Throwable e) {}
		});
	}

	/**
	 * Package-level operation used to get an object by identifier
	 * 
	 * @param id
	 * @return the remotized object
	 */
	 synchronized Object getObject(String id) {
		return byId.get(id).object;
	}

	/**
	 * Package-level operation used to get the identifier of a service
	 * 
	 * @param object   the service implementation
	 * @param remoteIf the service interface
	 * @return the associated identifier or null if no entry was found
	 */
	 synchronized String getId(Object object, Class<?> remoteIf) {
		return byEntry.get(new Entry(object, remoteIf));
	}

	/**
	 * Marks an interface to automatically create remote references for objects that
	 * are sent as arguments for an invocation or as return values. The objects are
	 * automatically published on this registry when the related parameter of the
	 * stub method has a type that matches with the marked interface
	 * 
	 * @param remoteIf the interface to mark
	 */
	public synchronized void setAutoReferenced(Class<?> remoteIf) {
		if (!remoteIf.isInterface())
			throw new IllegalArgumentException("the specified class is not an interface");
		autoReferences.add(remoteIf);
	}

	/**
	 * Remove a automatic referencing mark for the interface. See the
	 * {@link ObjectRegistry#setAutoReferenced(Class)} method. All the objects
	 * automatically referenced until this call, remains published in the registry.
	 * 
	 * @param remoteIf the interface to unmark
	 */
	public synchronized void unsetAutoReferenced(Class<?> remoteIf) {
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
