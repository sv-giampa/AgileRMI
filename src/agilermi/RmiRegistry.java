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
import javax.net.ssl.SSLSocketFactory;

/**
 * Defines a class that accepts new TCP connections over a port of the local
 * machine and automatically creates and manages the object sockets to export
 * remote objects. It integrates a registry of exported objects. The instances
 * of this class can be shared among more than one RmiHandler to obtain a
 * multi-peer interoperability.
 * 
 * @author Salvatore Giampa'
 *
 */
public class RmiRegistry {

	// lock for synchronized access to this instance
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

	private boolean remoteExceptionEnabled = true;

	// automatically referenced interfaces
	private Set<Class<?>> remotes = new HashSet<>();

	// map: object -> skeleton
	Map<Object, Skeleton> skeletonByObject = new IdentityHashMap<>();

	// map: identifier -> skeleton
	private Map<String, Skeleton> skeletonById = new HashMap<>();

	// filter factory used to customize network communication streams
	private FilterFactory filterFactory;

	/**
	 * If it is enabled, tends to create new connections for each created or
	 * received stub. By default it is disabled.
	 */
	private boolean multiConnectionMode = false;

	/**
	 * Creates a new simple RmiRegistry instance, without starting its connection
	 * listener. No other layers are inserted into the communication of
	 * {@link RmiHandler} instances, just a plain, object based communication will
	 * be established. See other constructors of this class (e.g.
	 * {@link #RmiRegistry(int, boolean, ServerSocketFactory, SocketFactory, FilterFactory, boolean)})
	 * to add some other communication layers, such as compression, authentication,
	 * cryptography and so on (For example, you can use the {@link SSLSocketFactory}
	 * to reach a good level of cryptography for RMI network communication).
	 * 
	 */
	public RmiRegistry() {
		this(null, null, null);
	}

	/**
	 * Creates a new RmiRegistry with the given FilterFactory instance, without
	 * starting its connection listener in a daemon thread.
	 * 
	 * @param filterFactory the {@link FilterFactory} instance that gives the
	 *                      streams in which the underlying communication streams
	 *                      will be wrapped in
	 */
	public RmiRegistry(FilterFactory filterFactory) {
		this(null, null, filterFactory);
	}

	/**
	 * Creates a new RmiRegistry with the given ServerSocketFactory, SocketFactory
	 * and FilterFactory instances, without starting the connection listener.
	 * 
	 * @param serverSocketFactory the {@link ServerSocketFactory} instance to use to
	 *                            build the listener server socket
	 * @param socketFactory       the {@link SocketFactory} instance to use to build
	 *                            client sockets
	 * @param filterFactory       the {@link FilterFactory} instance that gives the
	 *                            streams in which the underlying communication
	 *                            streams will be wrapped in
	 */
	public RmiRegistry(ServerSocketFactory serverSocketFactory, SocketFactory socketFactory,
			FilterFactory filterFactory) {
		if (serverSocketFactory == null)
			serverSocketFactory = ServerSocketFactory.getDefault();
		if (socketFactory == null)
			socketFactory = SocketFactory.getDefault();

		this.ssFactory = serverSocketFactory;
		this.sFactory = socketFactory;
		this.filterFactory = filterFactory;
		attachFailureObserver(failureObserver);
	}

	/**
	 * Creates a new RmiRegistry and automatically starts its connection listener on
	 * the given port in a daemon thread.
	 * 
	 * @param port the port to listen on
	 * @throws IOException if an I\O error occurs
	 */
	public RmiRegistry(int port) throws IOException {
		this(port, true, null, null, null, true);
	}

	/**
	 * Creates a new RmiRegistry and automatically starts its connection listener on
	 * the given port.
	 * 
	 * @param port   the port to listen on
	 * @param daemon set the daemon flag of the listener thread. See
	 *               {@link Thread#setDaemon(boolean)} for more details.
	 * @throws IOException if an I\O error occurs
	 */
	public RmiRegistry(int port, boolean daemon) throws IOException {
		this(port, daemon, null, null, null, true);
	}

	/**
	 * Creates a new RmiRegistry and automatically starts its connection listener on
	 * the given port.
	 * 
	 * @param port          the port to listen on
	 * @param daemon        set the daemon flag of the listener thread. See
	 *                      {@link Thread#setDaemon(boolean)} for more details.
	 * @param filterFactory the {@link FilterFactory} instance that gives the
	 *                      streams in which the underlying communication
	 * @throws IOException if an I/O error occurs
	 */
	public RmiRegistry(int port, boolean daemon, FilterFactory filterFactory) throws IOException {
		this(port, daemon, null, null, filterFactory, true);
	}

	/**
	 * Creates a new RmiRegistry with the specified {@link FilterFactory} and
	 * automatically starts its connection listener on the given port in a daemon
	 * thread.
	 * 
	 * @param port          the port to listen on
	 * @param filterFactory the {@link FilterFactory} instance that gives the
	 *                      streams in which the underlying communication
	 * @throws IOException if an I\O error occurs
	 */
	public RmiRegistry(int port, FilterFactory filterFactory) throws IOException {
		this(port, true, null, null, filterFactory, true);
	}

	/**
	 * Creates a new RmiRegistry with the specified {@link FilterFactory} and
	 * automatically starts its connection listener on the given port in a daemon
	 * thread.
	 * 
	 * @param port                the port to listen on
	 * @param daemon              set the daemon flag of the listener thread. See
	 *                            {@link Thread#setDaemon(boolean)} for more
	 *                            details.
	 * @param serverSocketFactory the {@link ServerSocketFactory} instance to use to
	 *                            build the listener server socket
	 * @param socketFactory       the {@link SocketFactory} instance to use to build
	 *                            client sockets
	 * @param filterFactory       the {@link FilterFactory} instance that gives the
	 *                            streams in which the underlying communication
	 *                            streams will be wrapped in
	 * @throws IOException if an I\O error occurs
	 */
	public RmiRegistry(int port, boolean daemon, ServerSocketFactory serverSocketFactory, SocketFactory socketFactory,
			FilterFactory filterFactory) throws IOException {
		this(port, daemon, serverSocketFactory, socketFactory, filterFactory, true);
	}

	/**
	 * 
	 * Creates a new RmiRegistry. This constructor allows to reach the maximum
	 * customization degree of network communication offered by Agile RMI.
	 * 
	 * @param port                the port to listen on
	 * @param daemon              set the daemon flag of the listener thread. See
	 *                            {@link Thread#setDaemon(boolean)} for more
	 *                            details.
	 * @param serverSocketFactory the {@link ServerSocketFactory} instance to use to
	 *                            build the listener server socket
	 * @param socketFactory       the {@link SocketFactory} instance to use to build
	 *                            client sockets
	 * @param filterFactory       the {@link FilterFactory} instance that gives the
	 *                            streams in which the underlying communication
	 *                            streams will be wrapped in
	 * @param enableListener      set this to true to start the connection listener
	 *                            after construction, set to false otherwise
	 * @throws IOException if an I\O error occurs
	 */
	public RmiRegistry(int port, boolean daemon, ServerSocketFactory serverSocketFactory, SocketFactory socketFactory,
			FilterFactory filterFactory, boolean enableListener) throws IOException {
		this(serverSocketFactory, socketFactory, filterFactory);
		if (enableListener)
			enableListener(port, daemon);
	}

	/**
	 * Finalizes this registry instance and all its current open connections. This
	 * method is also called by the Garbage Collector when the registry is no longer
	 * referenced
	 */
	@Override
	public synchronized void finalize() {
		detachFailureObserver(failureObserver);
		for (List<RmiHandler> rmiHandlers : handlers.values())
			for (RmiHandler rmiHandler : rmiHandlers)
				rmiHandler.dispose();
	}

	/**
	 * Shows the multi-connection mode enable state. See
	 * {@link #multiConnectionMode}.
	 * 
	 * @return true if multi-connection mode is enabled, false otherwise
	 */
	public boolean isMultiConnectionMode() {
		return multiConnectionMode;
	}

	/**
	 * Enable or disable multi-connection mode. See {@link #multiConnectionMode}.
	 * 
	 * @param multiConnectionMode true to enable, false to disable
	 */
	public void setMultiConnectionMode(boolean multiConnectionMode) {
		this.multiConnectionMode = multiConnectionMode;
	}

	/**
	 * Gets the stub for the specified object identifier on the specified host
	 * respect to the given interface. This method creates a new {@link RmiHandler}
	 * if necessary to communicate with the specified host. The new
	 * {@link RmiHandler} can be obtained by calling the
	 * {@link #getRmiHandler(String, int)} method.
	 * 
	 * @param address        the host address
	 * @param port           the host port
	 * @param objectId       the remote object identifier
	 * @param stubInterfaces the interfaces implemented by the stub
	 * @return the stub object
	 * @throws UnknownHostException if the host address cannot be resolved
	 * @throws IOException          if I/O errors occur
	 */
	public Object getStub(String address, int port, String objectId, Class<?>... stubInterfaces)
			throws UnknownHostException, IOException {
		synchronized (lock) {
			return getRmiHandler(address, port, multiConnectionMode).getStub(objectId, stubInterfaces);
		}
	}

	/**
	 * Gets the stub for the specified object identifier on the specified host
	 * respect to the given interface. This method creates a new {@link RmiHandler}
	 * if necessary to communicate with the specified host. The new
	 * {@link RmiHandler} can be obtained by calling the
	 * {@link #getRmiHandler(String, int)} method.
	 * 
	 * @param address          the host address
	 * @param port             the host port
	 * @param objectId         the remote object identifier
	 * @param createNewHandler always create a new handler without getting an
	 *                         already existing one. This parameter overrides the
	 *                         {@link #multiConnectionMode} attribute
	 * @param stubInterfaces   the interfaces implemented by the stub
	 * @return the stub object
	 * @throws UnknownHostException if the host address cannot be resolved
	 * @throws IOException          if I/O errors occur
	 */
	public Object getStub(String address, int port, String objectId, boolean createNewHandler,
			Class<?>... stubInterfaces) throws UnknownHostException, IOException {
		synchronized (lock) {
			return getRmiHandler(address, port, createNewHandler).getStub(objectId, stubInterfaces);
		}
	}

	/**
	 * Gets an {@link RmiHandler} instance for the specified host. If it has not
	 * been created, creates it. If some RmiHandler already exists, gets one of
	 * them.
	 * 
	 * @param host the host address
	 * @param port the host port
	 * @return the object peer related to the specified host
	 * @throws UnknownHostException if the host address cannot be resolved
	 * @throws IOException          if I/O errors occur
	 */
	public RmiHandler getRmiHandler(String host, int port) throws IOException {
		return getRmiHandler(host, port, false);
	}

	/**
	 * Gets an {@link RmiHandler} instance for the specified host. If it has not
	 * been created, creates it.
	 * 
	 * @param host      the host address
	 * @param port      the host port
	 * @param createNew always create a new handler without getting an already
	 *                  existing one
	 * @return the object peer related to the specified host
	 * @throws UnknownHostException if the host address cannot be resolved
	 * @throws IOException          if I/O errors occur
	 */
	public RmiHandler getRmiHandler(String host, int port, boolean createNew) throws IOException {
		synchronized (lock) {
			InetSocketAddress inetAddress = new InetSocketAddress(host, port);
			if (!handlers.containsKey(inetAddress))
				handlers.put(inetAddress, new ArrayList<>(1));
			List<RmiHandler> rmiHandlers = handlers.get(inetAddress);
			RmiHandler rmiHandler = null;
			if (rmiHandlers.size() == 0 || createNew)
				rmiHandlers.add(rmiHandler = new RmiHandler(sFactory.createSocket(host, port), this, filterFactory));
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
	public void enableListener(int port, boolean daemon) throws IOException {
		synchronized (lock) {
			if (listener != null)
				disableListener();
			serverSocket = ssFactory.createServerSocket(port);
			this.port = serverSocket.getLocalPort();
			listener = new Thread(listenerTask);
			listener.setDaemon(daemon);
			listener.start();
		}
	}

	/**
	 * Disable the registry listener. This method will disallow the registry to
	 * accept new incoming connections, but does not close the current open ones.
	 */
	public void disableListener() {
		synchronized (lock) {
			if (listener == null)
				return;

			listener.interrupt();
			listener = null;
			try {
				serverSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Defines the main thread that accepts new incoming connections and creates
	 * {@link RmiHandler} objects
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

	/**
	 * Enables the stubs for remote objects to throw a {@link RemoteException} when
	 * their {@link RmiHandler} will be disposed
	 * 
	 * @param enable set this to true to enable the exception, set to false
	 *               otherwise
	 */
	public void enableRemoteException(boolean enable) {
		this.remoteExceptionEnabled = enable;
	}

	/**
	 * Gets the enable status of the {@link RemoteException}
	 * 
	 * @return true if {@link RemoteException} is enabled, false otherwise
	 */
	public boolean isRemoteExceptionEnabled() {
		return remoteExceptionEnabled;
	}

	/**
	 * Publish the given object respect to the specified interface.
	 * 
	 * @param objectId the identifier to use for this service
	 * @param object   the implementation of the service to publish
	 * @throws IllegalArgumentException if the specified identifier was already
	 *                                  bound or if the objectId parameter matches
	 *                                  the automatic referencing objectId pattern
	 *                                  that is /\#[0-9]+/
	 */
	public synchronized void publish(String objectId, Object object) {
		if (objectId.startsWith(Skeleton.IDENTIFIER_PREFIX))
			throw new IllegalArgumentException("The used identifier prefix '" + Skeleton.IDENTIFIER_PREFIX
					+ "' is reserved to atomatic referencing. Please use another identifier pattern.");

		Skeleton sk = null;
		if (skeletonByObject.containsKey(object)) {
			sk = skeletonByObject.get(object);

			if (skeletonById.containsKey(objectId) && skeletonById.get(objectId) != sk)
				throw new IllegalArgumentException("the given object name '" + objectId + "' is already bound.");

			if (sk.getObject() != object)
				throw new IllegalStateException(
						"INTERNAL ERROR: the given object is associated to a skeleton that does not references it");
		} else {
			if (skeletonById.containsKey(objectId))
				throw new IllegalArgumentException("the given object name '" + objectId + "' is already bound.");
			sk = new Skeleton(object, this);
			skeletonById.put(sk.getId(), sk);
			skeletonByObject.put(object, sk);
		}
		skeletonById.put(objectId, sk);
		sk.addNames(objectId);
	}

	/**
	 * Publish the given object respect to the specified interface. The identifier
	 * is automatically generated and returnedCondition
	 * 
	 * @param object the implementation of the service to publish
	 * @return the generated identifier
	 */
	public synchronized String publish(Object object) {
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
	 * @param object the object to unpublish
	 */
	public synchronized void unpublish(Object object) {
		Skeleton skeleton = skeletonByObject.remove(object);
		if (skeleton != null) {
			skeletonById.remove(skeleton.getId());
			for (String id : skeleton.names())
				skeletonById.remove(id);
		}
	}

	/**
	 * Unpublish the remote object that is associated to the given identifier
	 * 
	 * @param objectId the object identifier of the remote object
	 */

	public synchronized void unpublish(String objectId) {
		synchronized (lock) {
			Skeleton skeleton = skeletonById.remove(objectId);
			if (skeleton != null) {
				skeletonByObject.remove(skeleton.getObject());
				for (String name : skeleton.names())
					skeletonById.remove(name);
			}
		}
	}

	/**
	 * Attach a {@link FailureObserver} object
	 * 
	 * @param o the failure observer
	 */
	public synchronized void attachFailureObserver(FailureObserver o) {
		synchronized (o) {

		}
		failureObservers.add(o);
	}

	/**
	 * Detach a {@link FailureObserver} object
	 * 
	 * @param o the failure observer
	 */
	public synchronized void detachFailureObserver(FailureObserver o) {
		failureObservers.remove(o);
	}

	/**
	 * Gets a published remote object by identifier
	 * 
	 * @param objectId object identifier
	 * @return the remotized object
	 */
	public synchronized Object getRemoteObject(String objectId) {
		Skeleton skeleton = skeletonById.get(objectId);
		if (skeleton != null)
			return skeleton.getObject();
		else
			return null;
	}

	/**
	 * Gets the identifier of a remote object published on this registry
	 * 
	 * @param object the published object reference
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
		if (!remoteIf.isInterface())
			throw new IllegalArgumentException("the specified class is not an interface");
		remotes.add(remoteIf);
	}

	/**
	 * Remove a automatic referencing mark for the interface. See the
	 * {@link RmiRegistry#exportInterface(Class)} method. All the objects
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
	 * {@link RmiRegistry#exportInterface(Class)} method.
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
	 * Package-level operation used to broadcast a {@link RmiHandler} failure to the
	 * failure observers
	 * 
	 * @param rmiHandler the object peer that caused the failure
	 * @param exception  the exception thrown by the object peer
	 */
	void sendRmiHandlerFailure(RmiHandler rmiHandler, Exception exception) {
		failureObservers.forEach(o -> {
			try {
				o.failure(rmiHandler, exception);
			} catch (Throwable e) {
			}
		});
	}

	Skeleton getSkeleton(String id) {
		return skeletonById.get(id);
	}

	Skeleton getSkeleton(Object object) {
		return skeletonByObject.get(object);
	}

}
