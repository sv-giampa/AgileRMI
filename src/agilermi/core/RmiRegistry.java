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

package agilermi.core;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;

import agilermi.authentication.Authenticator;
import agilermi.communication.ProtocolEndpointFactory;
import agilermi.configuration.FailureObserver;
import agilermi.configuration.Remote;
import agilermi.exception.RemoteException;

/**
 * Defines a class that accepts new TCP connections over a port of the local
 * machine and automatically creates and manages the object sockets to export
 * remote objects. It integrates a registry of exported objects. The instances
 * of this class can be shared among more than one RmiHandler to obtain a
 * multi-peer interoperability. This class can be instantiated through its
 * {@link Builder} whose instances are constructed by the {@link #builder()}
 * factory method.<br>
 * <br>
 * Instantiation example:<br>
 * <code>RmiRegistry registry = RmiRegistry.builder().build();</code>
 * 
 * @author Salvatore Giampa'
 *
 */
public class RmiRegistry {

	// identifies the current instance of RmiRegistry. It serves to avoid loop-back
	// connections and to replace remote pointer that point to an object on this
	// same registry with their local instance
	final String registryKey;

	// lock for synchronized access to this instance
	private Object lock = new Object();

	// the server socket created by the last call to the enableListener() method
	private ServerSocket serverSocket;

	// the reference to the main thread
	private Thread listener;

	// the peer that are currently online
	private Map<InetSocketAddress, List<RmiHandler>> handlers = new HashMap<>();

	// port of the TCP listener
	private int listenerPort;

	// socket factories
	private ServerSocketFactory ssFactory;
	private SocketFactory sFactory;

	// failure observers
	private Set<FailureObserver> failureObservers = new HashSet<>();

	// enable the stubs to throw a remote exception when invoked after a connection
	// failure
	private boolean remoteExceptionEnabled = true;

	// automatically referenced interfaces
	private Set<Class<?>> remotes = new HashSet<>();

	// map: object -> skeleton
	Map<Object, Skeleton> skeletonByObject = new IdentityHashMap<>();

	// map: identifier -> skeleton
	private Map<String, Skeleton> skeletonById = new HashMap<>();

	// filter factory used to customize network communication streams
	private ProtocolEndpointFactory protocolEndpointFactory;

	// multiple connection mode
	private boolean multiConnectionMode = false;

	// map: "address:port" -> "authIdentifier:authPassphrase"
	private Map<String, String[]> authenticationMap = new TreeMap<>();

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
					RmiHandler rmiHandler = new RmiHandler(socket, RmiRegistry.this, protocolEndpointFactory);
					if (!handlers.containsKey(rmiHandler.getInetSocketAddress()))
						handlers.put(rmiHandler.getInetSocketAddress(), new ArrayList<>(1));
					handlers.get(rmiHandler.getInetSocketAddress()).add(rmiHandler);
				} catch (IOException e) {
					// e.printStackTrace();
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

	/**
	 * Adds authentication details for a remote host
	 * 
	 * @param host           the remote host
	 * @param port           the remote port
	 * @param authId         the authentication identifier
	 * @param authPassphrase the authentication pass-phrase
	 */
	public void setAuthentication(String host, int port, String authId, String authPassphrase) {
		try {
			InetAddress address = InetAddress.getByName(host);
			host = address.getCanonicalHostName();
		} catch (UnknownHostException e) {
		}
		String key = host + ":" + port;
		String[] auth = new String[] { authId, authPassphrase };
		authenticationMap.put(key, auth);
	}

	/**
	 * Removes authentication details for a remote host
	 * 
	 * @param host the remote host
	 * @param port the remote port
	 */
	public void removeAuthentication(String host, int port) {
		try {
			InetAddress address = InetAddress.getByName(host);
			host = address.getCanonicalHostName();
		} catch (UnknownHostException e) {
		}
		String key = host + ":" + port;
		authenticationMap.remove(key);
	}

	/**
	 * Package-level method to get authentication relative to a remote process.
	 * 
	 * @param host the remote host
	 * @param port the remote port
	 * 
	 * @return String array that has authentication identifier at the 0 position and
	 *         the authentication pass-phrase at the 1 position or null if no
	 *         authentication has been specified for the rmeote process
	 * 
	 */
	String[] getAuthentication(String host, int port) {
		try {
			InetAddress address = InetAddress.getByName(host);
			host = address.getCanonicalHostName();
		} catch (UnknownHostException e) {
		}
		String key = host + ":" + port;
		return authenticationMap.get(key);
	}

	/**
	 * Package-level method to get authentication relative to a remote process.
	 * 
	 * @param address the remote address
	 * @param port    the remote port
	 * 
	 * @return String array that has authentication identifier at the 0 position and
	 *         the authentication pass-phrase at the 1 position or null if no
	 *         authentication has been specified for the rmeote process
	 */
	String[] getAuthentication(InetAddress address, int port) {
		String host = address.getCanonicalHostName();
		String key = host + ":" + port;
		return authenticationMap.get(key);
	}

	// authenticator objects that authenticates and authorize users
	private Authenticator authenticator;

	/**
	 * Creates a new {@link RmiRegistry.Builder} instance, used to configure and
	 * start a new {@link RmiRegistry} instance.
	 * 
	 * @return a new {@link RmiRegistry.Builder} instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for {@link RmiRegistry}. A new instance of this class can be returned
	 * by the {@link RmiRegistry#builder()} static method. A new instance of this
	 * class wraps all the defaults for {@link RmiRegistry} and allows to modify
	 * them. When the configuration has been terminated, a new {@link RmiRegistry}
	 * instance can be obtained by the {@link Builder#build()} method.
	 * 
	 * defaults:<br>
	 * <ul>
	 * <li>connection listener enabled: false</li>
	 * <li>connection listener port: 0 (a random port)</li>
	 * <li>connection listener daemon: true</li>
	 * <li>{@link ServerSocketFactory}: null (that is
	 * {@link ServerSocketFactory#getDefault()})</li>
	 * <li>{@link SocketFactory}: null (that is
	 * {@link SocketFactory#getDefault()})</li>
	 * <li>{@link ProtocolEndpointFactory}: null</li>
	 * <li>{@link Authenticator}: null</li>
	 * <li>authentication Identifier: null (that is guest identifier)</li>
	 * <li>authentication pass-phrase: null (that is guest pass-phrase)</li>
	 * </ul>
	 * 
	 * @author Salvatore Giampa'
	 *
	 */
	public static class Builder {

		private Builder() {
		}

		// underlyng protocols
		private ServerSocketFactory serverSocketFactory = ServerSocketFactory.getDefault();
		private SocketFactory socketFactory = SocketFactory.getDefault();
		private ProtocolEndpointFactory protocolEndpointFactory;

		// authentication
		private Authenticator authenticator;

		/**
		 * Set an {@link Authenticator} object that intercept authentication and
		 * authorization requests from remote machines.
		 * 
		 * @param authenticator the {@link Authenticator} instance to use
		 * @return this builder
		 */
		public Builder setAuthenticator(Authenticator authenticator) {
			this.authenticator = authenticator;
			return this;
		}

		/**
		 * 
		 * Sets the socket factories that the registry will use.
		 * 
		 * @param socketFactory       the {@link SocketFactory} instance to use to build
		 *                            client sockets
		 * @param serverSocketFactory the {@link ServerSocketFactory} instance to use to
		 *                            build the listener server socket
		 * @return this builder
		 */
		public Builder setSocketFactories(SocketFactory socketFactory, ServerSocketFactory serverSocketFactory) {
			this.socketFactory = socketFactory;
			this.serverSocketFactory = serverSocketFactory;
			return this;
		}

		/**
		 * Sets the {@link ProtocolEndpointFactory} instance to use in the registry to
		 * build.
		 * 
		 * @param protocolEndpointFactory the {@link ProtocolEndpointFactory} instance
		 *                                that gives the streams in which the underlying
		 *                                communication streams will be wrapped in
		 * @return this builder
		 */
		public Builder setFilterFactory(ProtocolEndpointFactory protocolEndpointFactory) {
			this.protocolEndpointFactory = protocolEndpointFactory;
			return this;
		}

		/**
		 * Builds the RmiRegistry.
		 * 
		 * @return the built {@link RmiRegistry} instance
		 */
		public RmiRegistry build() {
			RmiRegistry rmiRegistry = new RmiRegistry(serverSocketFactory, socketFactory, protocolEndpointFactory,
					authenticator);
			return rmiRegistry;
		}
	}

	/**
	 * Creates a new {@link RmiRegistry} with the given ServerSocketFactory,
	 * SocketFactory and ProtocolEndpointFactory instances, without starting the
	 * connection listener.
	 * 
	 * @param serverSocketFactory     the {@link ServerSocketFactory} instance to
	 *                                use to build the listener server socket
	 * @param socketFactory           the {@link SocketFactory} instance to use to
	 *                                build client sockets
	 * @param protocolEndpointFactory the {@link ProtocolEndpointFactory} instance
	 *                                that gives the streams in which the underlying
	 *                                communication streams will be wrapped in
	 * @param authenticator           an {@link Authenticator} instance that allows
	 *                                to authenticate and authorize users of
	 *                                incoming connection. For example, this
	 *                                instance can be an adapter that access a
	 *                                database or another pre-made authentication
	 *                                system.
	 * 
	 * @see RmiRegistry.Builder
	 * @see RmiRegistry#builder()
	 */
	private RmiRegistry(ServerSocketFactory serverSocketFactory, SocketFactory socketFactory,
			ProtocolEndpointFactory protocolEndpointFactory, Authenticator authenticator) {
		if (serverSocketFactory == null)
			serverSocketFactory = ServerSocketFactory.getDefault();
		if (socketFactory == null)
			socketFactory = SocketFactory.getDefault();

		Random random = new Random();
		this.registryKey = Long.toHexString(random.nextLong()) + Long.toHexString(random.nextLong())
				+ Long.toHexString(random.nextLong()) + Long.toHexString(random.nextLong())
				+ Long.toHexString(random.nextLong());

		this.ssFactory = serverSocketFactory;
		this.sFactory = socketFactory;
		this.protocolEndpointFactory = protocolEndpointFactory;
		this.authenticator = authenticator;
		attachFailureObserver(failureObserver);
	}

	/*
	 * public void setAuthentication(String authId, String authPassphrase) {
	 * this.auth }
	 */

	/**
	 * Finalizes this registry instance and all its current open connections. This
	 * method is also called by the Garbage Collector when the registry is no longer
	 * referenced
	 */
	@Override
	public void finalize() {
		synchronized (lock) {
			detachFailureObserver(failureObserver);
			disableListener();
			for (Iterator<InetSocketAddress> it = handlers.keySet().iterator(); it.hasNext(); it.remove())
				for (RmiHandler rmiHandler : handlers.get(it.next()))
					rmiHandler.dispose();
		}
	}

	/**
	 * Shows the multi-connection mode enable state. If it is enabled, tends to
	 * create new connections for each created or received stub. By default it is
	 * disabled.
	 * 
	 * @return true if multi-connection mode is enabled, false otherwise
	 */
	public boolean isMultiConnectionMode() {
		return multiConnectionMode;
	}

	/**
	 * Enable or disable multi-connection mode. If it is enabled, tends to create
	 * new connections for each created or received stub. By default it is disabled.
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

	public Object getStub(String address, int port, String objectId)
			throws UnknownHostException, IOException, InterruptedException {
		return getStub(address, port, objectId, multiConnectionMode);
	}

	public Object getStub(String address, int port, String objectId, boolean createNewHandler)
			throws UnknownHostException, IOException, InterruptedException {

		synchronized (lock) {
			RemoteInterfaceHandle hnd = new RemoteInterfaceHandle(objectId);
			RmiHandler rmiHandler = getRmiHandler(address, port, createNewHandler);
			rmiHandler.putHandle(hnd);
			hnd.semaphore.acquire();

			return rmiHandler.getStub(objectId, hnd.interfaces);
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
				rmiHandlers.add(
						rmiHandler = new RmiHandler(sFactory.createSocket(host, port), this, protocolEndpointFactory));
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
			this.listenerPort = serverSocket.getLocalPort();
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
	 * Gets the port on which the listener was started on last time
	 * 
	 * @return The last listener TCP port
	 */
	public int getListenerPort() {
		return listenerPort;
	}

	/**
	 * Gets the authenticator of this registry
	 * 
	 * @return the authenticator associated to this registry
	 */
	public Authenticator getAuthenticator() {
		return authenticator;
	}

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
	public void publish(String objectId, Object object) {
		synchronized (lock) {
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
	}

	/**
	 * Publish the given object respect to the specified interface. The identifier
	 * is automatically generated and returnedCondition
	 * 
	 * @param object the implementation of the service to publish
	 * @return the generated identifier
	 */
	public String publish(Object object) {
		synchronized (lock) {
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
	}

	/**
	 * Unpublish an object respect to the given interface
	 * 
	 * @param object the object to unpublish
	 */
	public void unpublish(Object object) {
		synchronized (lock) {
			Skeleton skeleton = skeletonByObject.remove(object);
			if (skeleton != null) {
				skeletonById.remove(skeleton.getId());
				for (String id : skeleton.names())
					skeletonById.remove(id);
			}
		}
	}

	/**
	 * Unpublish the remote object that is associated to the given identifier
	 * 
	 * @param objectId the object identifier of the remote object
	 */

	public void unpublish(String objectId) {
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
	public void attachFailureObserver(FailureObserver o) {
		synchronized (lock) {
			failureObservers.add(o);
		}
	}

	/**
	 * Detach a {@link FailureObserver} object
	 * 
	 * @param o the failure observer
	 */
	public void detachFailureObserver(FailureObserver o) {
		synchronized (lock) {
			failureObservers.remove(o);
		}
	}

	/**
	 * Gets a published remote object by identifier
	 * 
	 * @param objectId object identifier
	 * @return the remotized object
	 */
	public Object getRemoteObject(String objectId) {
		synchronized (lock) {
			Skeleton skeleton = skeletonById.get(objectId);
			if (skeleton != null)
				return skeleton.getObject();
			else
				return null;
		}
	}

	/**
	 * Gets the identifier of a remote object published on this registry
	 * 
	 * @param object the published object reference
	 * @return the associated identifier or null if no entry was found
	 */
	public String getRemoteObjectId(Object object) {
		synchronized (lock) {
			Skeleton skeleton = skeletonByObject.get(object);
			if (skeleton != null)
				return skeleton.getId();
			else
				return null;
		}
	}

	/**
	 * Marks an interface to automatically create remote references for objects that
	 * are sent as arguments for an invocation or as return values. The objects are
	 * automatically published on this registry when the related parameter of the
	 * stub method has a type that matches with the marked interface
	 * 
	 * @param remoteIf the interface to mark
	 */
	public void exportInterface(Class<?> remoteIf) {
		synchronized (lock) {
			if (remoteIf == Remote.class)
				throw new IllegalArgumentException("agilermi.Remote interface cannot be exported!");
			if (!remoteIf.isInterface())
				throw new IllegalArgumentException("the specified class is not an interface");
			remotes.add(remoteIf);
		}
	}

	/**
	 * Remove a automatic referencing mark for the interface. See the
	 * {@link RmiRegistry#exportInterface(Class)} method. All the objects
	 * automatically referenced until this call, remains published in the registry.
	 * 
	 * @param remoteIf the interface to unmark
	 */
	public void unexportInterface(Class<?> remoteIf) {
		synchronized (lock) {
			if (Remote.class.isAssignableFrom(remoteIf))
				throw new IllegalArgumentException(
						"An interface that is statically defined as remote cannot be unexported.");
			remotes.remove(remoteIf);
		}
	}

	/**
	 * Check if an interface is marked for automatic referencing. An interface is
	 * remote if it, directly or indirectly, extends the {@link Remote} interface or
	 * if it was exported or if it extends, directly or indirectly, an interface
	 * that was exported to be remote.<br>
	 * See the {@link RmiRegistry#exportInterface(Class)} method.
	 * 
	 * @param remoteIf the interface to check
	 * @return true if the interface is marked for automatic referencing, false
	 *         otherwise
	 */
	public boolean isRemote(Class<?> remoteIf) {
		if (remoteIf == Remote.class)
			return false;
		if (Remote.class.isAssignableFrom(remoteIf))
			return true;

		boolean isMapped;
		synchronized (lock) {
			isMapped = remotes.contains(remoteIf);
		}

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
	 * Gets the remote interfaces implemented by the class of the remote object
	 * associated to the specified object identifier
	 * 
	 * @param objectId the object identifier
	 * @return the remote interfaces of the remote object
	 */
	public List<Class<?>> getRemoteInterfaces(String objectId) {
		Object object = getRemoteObject(objectId);
		return getRemoteInterfaces(object);
	}

	/**
	 * Gets the remote interfaces implemented by the class of the specified object
	 * 
	 * @param object the object
	 * @return the remote interfaces of the object
	 */
	public List<Class<?>> getRemoteInterfaces(Object object) {
		if (object == null)
			return new ArrayList<>();
		return getRemoteInterfaces(object.getClass());
	}

	/**
	 * Gets the remote interfaces implemented by the specified class and its
	 * super-classes
	 * 
	 * @param cls the class
	 * @return the remote interfaces implemented by the class
	 */
	public List<Class<?>> getRemoteInterfaces(Class<?> cls) {
		List<Class<?>> remoteIfs = new ArrayList<>();
		Class<?> current = cls;
		while (current != null) {
			Class<?>[] ifaces = current.getInterfaces();
			for (Class<?> iface : ifaces) {
				if (isRemote(iface))
					remoteIfs.add(iface);
				else {
					List<Class<?>> remoteSupers = getRemoteInterfaces(iface);
					remoteIfs.addAll(remoteSupers);
				}
			}
			current = current.getSuperclass();
		}
		return remoteIfs;
	}

	/**
	 * Package-scoped. Operation used to broadcast a {@link RmiHandler} failure to
	 * the failure observers attached to this registry
	 * 
	 * @param rmiHandler the object peer that caused the failure
	 * @param exception  the exception thrown by the object peer
	 */
	void sendRmiHandlerFailure(RmiHandler rmiHandler, Exception exception) {
		synchronized (lock) {
			failureObservers.forEach(o -> {
				try {
					o.failure(rmiHandler, exception);
				} catch (Throwable e) {
				}
			});
		}
	}

	/**
	 * Package-scoped. Get the skeleton associated to the specified object
	 * identifier (server side)
	 * 
	 * @param id the object identifier
	 * @return the skeleton of the remote object
	 */
	Skeleton getSkeleton(String id) {
		synchronized (lock) {
			return skeletonById.get(id);
		}
	}

	/**
	 * Package-scoped. Get the skeleton associated to the specified remote object
	 * (server side)
	 * 
	 * @param objec the remote object
	 * @return the skeleton of the remote object
	 */
	Skeleton getSkeleton(Object object) {
		synchronized (lock) {
			return skeletonByObject.get(object);
		}
	}

}
