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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;

import agilermi.filter.FilterFactory;

/**
 * Defines a simple class that accepts new TCP connections over a port of the
 * local machine and automatically creates and manages the object sockets to
 * export remote objects. It integrates a registry of exported objects. The
 * instances of this class can be shared among more than one RmiHandler to
 * obtain a multi-peer interoperability.
 * 
 * @author Salvatore Giampa'
 *
 */
public class RmiRegistry {

	private Object lock = new Object();

	// the server socket used from last call to the start() method
	private ServerSocket serverSocket;

	// the reference to the main thread
	private Thread listener;

	// the peer that are currently online
	private Map<InetSocketAddress, List<RmiHandler>> handlers = new HashMap<>();

	// port of the TCP listener
	private int port;

	private ServerSocketFactory ssFactory;
	private SocketFactory sFactory;

	// failure observers
	private Set<FailureObserver> failureObservers = new HashSet<>();

	private boolean dispositionExceptionEnabled = true;

	// automatically referenced interfaces
	private Set<Class<?>> remotes = new HashSet<>();

	private Map<Object, Skeleton> skeletonByObject = new IdentityHashMap<>();

	private Map<String, Skeleton> skeletonById = new HashMap<>();

	private FilterFactory filterFactory;

	public RmiRegistry() throws IOException {
		this(0, true, null, null, null, false);
	}

	public RmiRegistry(FilterFactory filterFactory) throws IOException {
		this(0, true, null, null, filterFactory, false);
	}

	public RmiRegistry(ServerSocketFactory ssFactory, SocketFactory socketFactory, FilterFactory filterFactory)
			throws IOException {
		this(0, true, ssFactory, socketFactory, filterFactory, false);
	}

	/**
	 * Creates a new Object server with a new empty object registry
	 * 
	 * @param port the port to listen on
	 * @throws IOException if an I\O error occurs
	 */
	public RmiRegistry(int port) throws IOException {
		this(port, true, null, null, null, true);
	}

	/**
	 * 
	 * @param port
	 * @param daemon
	 * @throws IOException
	 */
	public RmiRegistry(int port, boolean daemon) throws IOException {
		this(port, daemon, null, null, null, true);
	}

	/**
	 * 
	 * @param port
	 * @param daemon
	 * @throws IOException
	 */
	public RmiRegistry(int port, boolean daemon, FilterFactory filterFactory) throws IOException {
		this(port, daemon, null, null, filterFactory, true);
	}

	/**
	 * Creates a new Object server with a new empty object registry
	 * 
	 * @param port the port to listen on
	 * @throws IOException if an I\O error occurs
	 */
	public RmiRegistry(int port, FilterFactory filterFactory) throws IOException {
		this(port, true, null, null, filterFactory, true);
	}

	public RmiRegistry(int port, boolean daemon, ServerSocketFactory ssFactory, SocketFactory sFactory,
			FilterFactory filterFactory) throws IOException {
		this(port, daemon, ssFactory, sFactory, filterFactory, true);
	}

	/**
	 * 
	 * Creates a new Object server with a new empty object registry
	 * 
	 * @param port      the port to listen on
	 * @param daemon    set to false to create a non-daemon listener thread (shuts
	 *                  down when all the application is terminated)
	 * @param ssFactory a custom factory to create server sockets
	 * @throws IOException if an I\O error occurs
	 */
	public RmiRegistry(int port, boolean daemon, ServerSocketFactory ssFactory, SocketFactory sFactory,
			FilterFactory filterFactory, boolean enableListener) throws IOException {
		if (ssFactory == null)
			ssFactory = ServerSocketFactory.getDefault();
		if (sFactory == null)
			sFactory = SocketFactory.getDefault();

		this.ssFactory = ssFactory;
		this.sFactory = sFactory;
		this.filterFactory = filterFactory;
		attachFailureObserver(failureObserver);

		if (enableListener)
			enableListener(port, daemon);
	}

	@Override
	public synchronized void finalize() {
		detachFailureObserver(failureObserver);
		for (List<RmiHandler> rmiHandlers : handlers.values())
			for (RmiHandler rmiHandler : rmiHandlers)
				rmiHandler.dispose();
	}

	/**
	 * Gets the stub for the specified object identifier on the specified host
	 * respect to the given interface. Similar to the
	 * {@link RmiHandler#getStub(String, Class)} method, but creates a new
	 * ObjectPeer if necessary to communicate with the specified host.
	 * 
	 * @param address       the host address
	 * @param port          the host port
	 * @param objectId      the remote object identifier
	 * @param stubInterface the stub's interface
	 * @param               <T> type of the stub object
	 * @return the stub object
	 * @throws UnknownHostException if the host address cannot be resolved
	 * @throws IOException          if I/O errors occur
	 */
	public Object getStub(String address, int port, String objectId, Class<?>... stubInterfaces)
			throws UnknownHostException, IOException {
		synchronized (lock) {
			return getRmiHandler(address, port).getStub(objectId, stubInterfaces);
		}
	}

	/**
	 * Gets the {@link RmiHandler} object for the specified host. If it has not been
	 * created, creates it.
	 * 
	 * @param address the host address
	 * @param port    the host port
	 * @return the object peer related to the specified host
	 * @throws UnknownHostException if the host address cannot be resolved
	 * @throws IOException          if I/O errors occur
	 */
	public RmiHandler getRmiHandler(String host, int port) throws IOException {
		synchronized (lock) {
			InetSocketAddress inetAddress = new InetSocketAddress(host, port);
			if (!handlers.containsKey(inetAddress))
				handlers.put(inetAddress, new ArrayList<>(1));
			List<RmiHandler> rmiHandlers = handlers.get(inetAddress);
			RmiHandler rmiHandler = null;
			if (rmiHandlers.size() == 0)
				rmiHandlers.add(rmiHandler = RmiHandler.connect(host, port, this, sFactory, filterFactory));
			else
				rmiHandler = rmiHandlers.get((int) (Math.random() * rmiHandlers.size()));
			return rmiHandler;
		}
	}

	/**
	 * Enable the registry listener on the selected port. This method enables the
	 * registry to accept new external incoming connections for RMI
	 * 
	 * @param port   the port to start the listener on
	 * @param daemon if true, the listener is started as daemon, that is it will be
	 *               stopped when all other non-daemon threads in the application
	 *               will bterminated.
	 * @throws IOException if I/O errors occur
	 */
	public synchronized void enableListener(int port, boolean daemon) throws IOException {
		if (listener != null)
			stopListener();
		serverSocket = ssFactory.createServerSocket(port);
		this.port = serverSocket.getLocalPort();
		listener = new Thread(listenerTask);
		listener.setDaemon(daemon);
		listener.start();
	}

	/**
	 * Disable the registry listener. This method will disallow the registry to
	 * accept new incoming connections, but does not close the current open ones.
	 */
	private synchronized void stopListener() {
		if (listener == null)
			return;

		listener.interrupt();
		listener = null;
		try {
			serverSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		/*
		 * for (RmiHandler socket : sockets) socket.dispose();
		 * 
		 * sockets.clear();
		 */
	}

	/**
	 * Defines the main thread that accepts new incoming connections and creates
	 * {@link ObjectPeer} objects
	 */
	private Runnable listenerTask = new Runnable() {
		@Override
		public void run() {
			while (listener != null && !listener.isInterrupted())
				try {
					Socket socket = serverSocket.accept();
					RmiHandler rmiHandler = new RmiHandler(socket, RmiRegistry.this, filterFactory);
					if (!handlers.containsKey(rmiHandler.getInetSocketAddress()))
						handlers.put(rmiHandler.getInetSocketAddress(), new ArrayList<>(1));
					handlers.get(rmiHandler.getInetSocketAddress()).add(rmiHandler);
				} catch (IOException e) {
					e.printStackTrace();
				}
		};
	};

	/**
	 * Defines the {@link FailureObserver} used to manage the peer which closed the
	 * connection
	 */
	private FailureObserver failureObserver = new FailureObserver() {
		@Override
		public void failure(RmiHandler rmiHandler, Exception exception) {
			List<RmiHandler> list = handlers.get(rmiHandler.getInetSocketAddress());
			if (list != null)
				list.remove(rmiHandler);
		}
	};

	// search index over identifiers
	// private Map<String, Object> byId = new HashMap<>();

	// search index over <object,interface> couples
	// private Map<Object, String> byEntry = new HashMap<>();

	public void setDispositionExceptionEnabled(boolean dispositionExceptionEnabled) {
		this.dispositionExceptionEnabled = dispositionExceptionEnabled;
	}

	/**
	 * Gets the enable status of the disposition exception
	 * 
	 * @return
	 */
	public boolean isDispositionExceptionEnabled() {
		return dispositionExceptionEnabled;
	}

	/**
	 * Publish the given object respect to the specified interface.
	 * 
	 * @param objectId the identifier to use for this service
	 * @param object   the implementation of the service to publish
	 * @param remoteIf the interface of the service to publish
	 * @throws IllegalArgumentException if the specified identifier was already
	 *                                  bound or if the objectId parameter matches
	 *                                  the automatic referencing objectId pattern
	 *                                  that is /\#[0-9]+/
	 */
	public synchronized void publish(String id, Object object) {
		// if (byId.get(id) == object)
		// return;
		// if (byId.containsKey(id) && byId.get(id) != object)
		// throw new IllegalArgumentException("the given objectId '" + id + "' is
		// already bound.");
		if (id.startsWith("#"))
			throw new IllegalArgumentException(
					"The used identifier pattern /\\#.*/ is reserved to atomatic referencing. Please use another identifier pattern.");

		/*
		 * if(byEntry.containsKey(object)) { String otherId = byEntry.get(object);
		 * byId.remove(otherId); }
		 */
		// byEntry.put(object, id);
		// byId.put(id, object);

		Skeleton sk = null;
		if (skeletonByObject.containsKey(object)) {
			sk = skeletonByObject.get(object);

			if (skeletonById.containsKey(id) && skeletonById.get(id) != sk)
				throw new IllegalArgumentException("the given object name '" + id + "' is already bound.");

			if (sk.getObject() != object)
				throw new IllegalStateException(
						"INTERNAL ERROR: the given object is associated to a skeleton that does not references it");
		} else {
			if (skeletonById.containsKey(id))
				throw new IllegalArgumentException("the given object name '" + id + "' is already bound.");
			sk = new Skeleton(object, this);
			skeletonById.put(sk.getId(), sk);
			skeletonByObject.put(object, sk);
		}
		skeletonById.put(id, sk);
		sk.addNames(id);

		/*
		 * if (idByName.containsKey(name)) throw new
		 * IllegalArgumentException("the given object name '" + name +
		 * "' is already bound.");
		 */

	}

	/**
	 * Publish the given object respect to the specified interface. The identifier
	 * is automatically generated and returnedCondition
	 * 
	 * @param object   the implementation of the service to publish
	 * @param remoteIf the interface of the service to publish
	 * @return the generated identifier
	 */
	public synchronized String publish(Object object) {
//		if (byEntry.containsKey(object)) {
//			return byEntry.get(object);
//		}
//
//		String id = "#" + String.valueOf(nextId++); // objectId pattern: /\#[0-9]+/
//		byEntry.put(object, id);
//		byId.put(id, object);

		if (skeletonByObject.containsKey(object)) {
			Skeleton sk = skeletonByObject.get(object);
			if (sk.getObject() != object)
				throw new IllegalStateException(
						"the given object is associated to a skeleton that does not references it");
			return sk.getId();
		} else {
			Skeleton sk = new Skeleton(object, this);
			skeletonById.put(sk.getId(), sk);
			skeletonByObject.put(object, sk);
			return sk.getId();
		}
	}

	/**
	 * Unpublish an object respect to the given interface
	 * 
	 * @param object   the implementation to unpublish
	 * @param remoteIf the interface respect to unpublish
	 */
	public synchronized void unpublish(Object object) {

		Skeleton skeleton = skeletonByObject.remove(object);
		if (skeleton != null) {
			skeletonById.remove(skeleton.getId());
			for (String id : skeleton.names())
				skeletonById.remove(id);
		}

//		String id = byEntry.remove(object);
//		if (id != null)
//			byId.remove(id);
	}

	/**
	 * Unpublish the service with the specified identifier
	 * 
	 * @param objectId the objectId of the service
	 */
	public synchronized void unpublish(String id) {
//		if (id.matches("\\#[0-9]+"))
//			throw new IllegalArgumentException(
//					"The used identifier pattern /\\#[0-9]+/ is reserved to atomatic referencing. Please use another identifier pattern.");
//		Object entry = byId.remove(id);
//		if (entry != null)
//			byEntry.remove(entry);

		Skeleton skeleton = skeletonById.remove(id);
		if (skeleton != null) {
			skeletonByObject.remove(skeleton.getObject());
			for (String name : skeleton.names())
				skeletonById.remove(name);
		}
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
	 * Package-level operation used to get an object by identifier
	 * 
	 * @param objectId
	 * @return the remotized object
	 */
	public synchronized Object getRemoteObject(String id) {
		Skeleton skeleton = skeletonById.get(id);
		if (skeleton != null)
			return skeleton.getObject();
		else
			return null;
	}

	/**
	 * Package-level operation used to get the identifier of a service
	 * 
	 * @param object   the service implementation
	 * @param remoteIf the service interface
	 * @return the associated identifier or null if no entry was found
	 */
	public synchronized String getRemoteObjectId(Object object) {
		Skeleton skeleton = skeletonByObject.get(object);
		if (skeleton != null)
			return skeleton.getId();
		else
			return null;
		// return byEntry.get(object);
	}

	/**
	 * Marks an interface to automatically create remote references for objects that
	 * are sent as arguments for an invocation or as return values. The objects are
	 * automatically published on this registry when the related parameter of the
	 * stub method has a type that matches with the marked interface
	 * 
	 * @param remoteIf the interface to mark
	 */
	public synchronized void exportInterface(Class<?> remoteIf) {
		if (remoteIf == Remote.class)
			throw new IllegalArgumentException("agilermi.Remote interface cannot be exported!");
		/*
		 * if (!remoteIf.isInterface()) throw new
		 * IllegalArgumentException("the specified class is not an interface");
		 */
		remotes.add(remoteIf);
	}

	/**
	 * Remove a automatic referencing mark for the interface. See the
	 * {@link ObjectRegistry#exportInterface(Class)} method. All the objects
	 * automatically referenced until this call, remains published in the registry.
	 * 
	 * @param remoteIf the interface to unmark
	 */
	public synchronized void unexportInterface(Class<?> remoteIf) {
		if (Remote.class.isAssignableFrom(remoteIf))
			throw new IllegalArgumentException(
					"An interface that is statically defined as remote cannot be unexported.");
		remotes.remove(remoteIf);
	}

	/**
	 * Check if an interface is marked for automatic referencing. See the
	 * {@link ObjectRegistry#exportInterface(Class)} method.
	 * 
	 * @param remoteIf the interface to check
	 * @return true if the interface is marked for automatic referencing, false
	 *         otherwise
	 */
	public boolean isRemote(Class<?> remoteIf) {
		if (Remote.class.isAssignableFrom(remoteIf))
			return true;

		boolean isMapped = remotes.contains(remoteIf);

		if (!isMapped) {
			for (Class<?> superIf : remoteIf.getInterfaces()) {
				isMapped = isRemote(superIf);
				if (isMapped)
					return true;
			}
		}
		return isMapped;
	}

	/**
	 * Package-level operation used to send a failure to the failure observers
	 * 
	 * @param rmiHandler the object peer that caused the failure
	 * @param exception  the exception thrown by the object peer
	 */
	void sendFailure(RmiHandler rmiHandler, Exception exception) {
		failureObservers.forEach(o -> {
			try {
				o.failure(rmiHandler, exception);
			} catch (Throwable e) {
			}
		});
	}

	// search index over identifiers
	// private Map<String, Object> byId = new HashMap<>();

	// search index over <object,interface> couples
	// private Map<Object, String> byEntry = new HashMap<>();

	Skeleton getSkeleton(String id) {
		return skeletonById.get(id);
	}

	Skeleton getSkeleton(Object object) {
		return skeletonByObject.get(object);
	}

}
