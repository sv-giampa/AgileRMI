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

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;

import agilermi.authentication.RMIAuthenticator;
import agilermi.codemobility.ClassLoaderFactory;
import agilermi.codemobility.URLClassLoaderFactory;
import agilermi.communication.ProtocolEndpoint;
import agilermi.communication.ProtocolEndpointFactory;
import agilermi.configuration.RMIFaultHandler;
import agilermi.configuration.Remote;
import agilermi.configuration.StubRetriever;
import agilermi.exception.RemoteException;

/**
 * Defines a class that accepts new TCP connections over a port of the local
 * machine and automatically creates and manages the object sockets to export
 * remote objects. It integrates a registry of exported objects. The instances
 * of this class can be shared among more than one RMIHandler to obtain a
 * multi-peer interoperability. This class can be instantiated through its
 * {@link Builder} whose instances are constructed by the {@link #builder()}
 * factory method.<br>
 * <br>
 * Instantiation example:<br>
 * <code>RMIRegistry registry = RMIRegistry.builder().build();</code>
 * 
 * @author Salvatore Giampa'
 *
 */
public final class RMIRegistry {

	ExecutorService invocationExecutor = Executors.newCachedThreadPool(new ThreadFactory() {
		@Override
		public Thread newThread(Runnable task) {
			Thread th = new Thread(task);
			th.setDaemon(true);
			th.setName("RMIRegistry.invocationExecutor");
			return th;
		}
	});

	// executor service used to move network operations on other threads
	ScheduledExecutorService executorService = Executors
			.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() + 1, new ThreadFactory() {
				@Override
				public Thread newThread(Runnable task) {
					Thread th = new Thread(task);
					th.setDaemon(true);
					th.setName("RMIRegistry.executorService");
					return th;
				}
			});

	private boolean suppressAllInvocationFaults;

	private long handlerFaultMaxLife = 0;

	private int skeletonInvocationCacheSize;

	private boolean codeDownloadingEnabled;

	private final boolean automaticReferencing;

	// multiple connection mode
	private boolean multiConnectionMode = false;

	// enabling state of the state consistency after a connection fault
	private boolean stateConsistencyOnFaultEnabled;

	private int latencyTime;

	private int leaseTime;

	// port of the TCP listener
	private int listenerPort;

	private volatile boolean finalized = false;

	// identifies the current instance of RMIRegistry. It serves to avoid loop-back
	// connections and to replace remote pointer that point to an object on this
	// same registry with their local instance. It is used to repair broken
	// connections, too.
	private final String registryKey;

	// lock for synchronized access to this instance
	private Object lock = new Object();

	// codebases for code mobility
	private RMIClassLoader rmiClassLoader;

	// the server socket created by the last call to the enableListener() method
	private ServerSocket serverSocket;

	// the handlers that are currently online
	private Map<InetSocketAddress, List<RMIHandler>> handlers = new HashMap<>();

	private Map<String, List<RMIHandler>> handlersByKey = new HashMap<>();

	// socket factories
	private ServerSocketFactory serverSocketFactory;
	private SocketFactory socketFactory;

	// currently attached fault handler
	private Set<RMIFaultHandler> rmiFaultHandlers = Collections
			.newSetFromMap(new WeakHashMap<RMIFaultHandler, Boolean>());

	// automatically referenced interfaces
	private Set<Class<?>> remotes = new HashSet<>();

	// map: object -> skeleton
	private Map<Object, Skeleton> skeletonByObject = Collections.synchronizedMap(new IdentityHashMap<>());

	// map: identifier -> skeleton
	private Map<String, Skeleton> skeletonById = Collections.synchronizedMap(new HashMap<>());

	// filter factory used to customize network communication streams
	private ProtocolEndpointFactory protocolEndpointFactory;

	// map: "address:port" -> "authIdentifier:authPassphrase"
	private Map<String, String[]> authenticationMap = new TreeMap<>();

	private ClassLoaderFactory classLoaderFactory;

	// rmiAuthenticator objects that authenticates and authorize users
	private RMIAuthenticator rmiAuthenticator;

	// reference to the Distributed Garbage Collector thread
	private DGC dgc;

	// the reference to the main thread
	private TCPListener listener;

	private LocalGCInvoker localGCInvoker = new LocalGCInvoker();

	private Class<Exception> remoteExceptionReplace = null;

	/**
	 * Local Garbage Collector invoker service
	 */
	private class LocalGCInvoker extends Thread {
		public LocalGCInvoker() {
			setName(LocalGCInvoker.class.getName());
			setDaemon(true);
			start();
		}

		@Override
		public void run() {
			try {
				while (!isInterrupted()) { Thread.sleep(latencyTime); System.gc(); }
			} catch (InterruptedException e) {}
		}
	}

	/**
	 * Distributed Garbage Collection service
	 */
	private class DGC extends Thread {
		public DGC() {
			setName(this.getClass().getName());
			setDaemon(true);
			start();
		}

		@Override
		public void run() {
			Set<Skeleton> skeletons = new HashSet<>();
			try {
				while (!Thread.currentThread().isInterrupted()) {
					long waitTime = leaseTime;

					synchronized (skeletonByObject) {
						skeletons.addAll(skeletonByObject.values());
					}

					for (Skeleton skeleton : skeletons) {
						if (skeleton.isGarbage()) {
							skeleton.unpublish();
						} else {
							waitTime = Math.min(waitTime, System.currentTimeMillis() - skeleton.getLastUseTime());
						}
					}
					// clear temporary structure
					skeletons.clear();
					// invoke local garbage collector
					System.gc();
					// wait next DGC cycle
					Thread.sleep(waitTime);
				}
			} catch (InterruptedException e) {}
		}
	}

	/**
	 * Defines the thread that accepts new incoming connections and creates
	 * {@link RMIHandler} objects
	 */
	private class TCPListener extends Thread {
		public TCPListener(boolean daemon) {
			setName(this.getClass().getName());
			setDaemon(daemon);
			start();
		}

		@Override
		public void run() {
			while (listener != null && !listener.isInterrupted())
				try {
					Socket socket = serverSocket.accept();
					RMIHandler rmiHandler = new RMIHandler(socket, RMIRegistry.this, protocolEndpointFactory, false);
					if (!handlers.containsKey(rmiHandler.getInetSocketAddress()))
						handlers.put(rmiHandler.getInetSocketAddress(), new ArrayList<>(1));
					handlers.get(rmiHandler.getInetSocketAddress()).add(rmiHandler);
					rmiHandler.start();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
	}

	/**
	 * Builder for {@link RMIRegistry}. A new instance of this class can be returned
	 * by the {@link RMIRegistry#builder()} static method. A new instance of this
	 * class wraps all the defaults for {@link RMIRegistry} and allows to modify
	 * them. When the configuration has been terminated, a new {@link RMIRegistry}
	 * instance can be obtained by the {@link Builder#build() build()} method.<br>
	 * <br>
	 * An instance of this class can be re-used after each call to the
	 * {@link Builder#build() build()} method to generate new instances of
	 * {@link RMIRegistry}. Each call to the {@link Builder#build() build()} method
	 * does not reset the builder instance to its default state.
	 * 
	 * @author Salvatore Giampa'
	 *
	 */
	public static class Builder {

		private Builder() {}

		// multi-connection mode (one connection for each stub)
		private boolean multiConnectionMode = false;

		// underlyng protocols
		private ServerSocketFactory serverSocketFactory = ServerSocketFactory.getDefault();
		private SocketFactory socketFactory = SocketFactory.getDefault();
		private ProtocolEndpointFactory protocolEndpointFactory;

		// authentication and authorization
		private RMIAuthenticator rmiAuthenticator;

		// code mobility
		private Set<URL> codebases = new HashSet<>();
		private boolean codeDownloadingEnabled = false;
		private ClassLoaderFactory classLoaderFactory;

		// Distributed Garbage Collector parameters
		private int leaseTime = 600000;
		private boolean automaticReferencingEnabled = true;

		// fault tolerance parameters
		private int latencyTime = 10000;
		private int skeletonInvocationCacheSize = 50;
		private boolean stateConsistencyOnFaultEnabled = true;
		private boolean suppressAllInvocationFaults = false;
		private Class<Exception> remoteExceptionReplace;

		// fault simulation
		private long handlerFaultMaxLife;

		/**
		 * Let the stubs to throw the specified exception alternatively to
		 * {@link RemoteException} on communication errors.<br>
		 * <br>
		 * 
		 * Default: null (RemoteException is not replaced)
		 * 
		 * @param exceptionClass the alternative exception class or null if the
		 *                       {@link RemoteException} must not be replaced.
		 * @return this builder
		 */
		public Builder replaceRemoteException(Class<Exception> exceptionClass) {
			remoteExceptionReplace = exceptionClass;
			return this;
		}

		/**
		 * Enables or disables the suppression of all remote invocation faults. If this
		 * flag is enabled all the stubs that are connected to the registry want not
		 * throw {@link RemoteException remote exceptions}, so the remote methods can be
		 * declared without throwing these exceptions. This is useful when turning an
		 * existing code into a distributed one. <br>
		 * <br>
		 * When all faults are suppressed, each remote method that is called from remote
		 * which encounter an RMI fault, will return the default value for its return
		 * type. The default values are <code>0</code> for all numerical primitive (such
		 * as int) and non-primitive (such as {@link Integer}) types, <code>false</code>
		 * for the boolean type and <code>null</code> for object types.<br>
		 * <br>
		 * Default: false
		 * 
		 * @param suppressAllInvocationFaults true to suppress all invocation faults on
		 *                                    the stubs
		 * @return this builder
		 */
		public Builder suppressAllInvocationFaults(boolean suppressAllInvocationFaults) {
			this.suppressAllInvocationFaults = suppressAllInvocationFaults;
			return this;
		}

		/**
		 * Enables or disables multi-connection mode. If this mode is enabled, the
		 * registry tends to create a new connection for each created or received stub.
		 * <br>
		 * <br>
		 * When disabled the registry tends to share the same TCP connections across all
		 * local stubs.<br>
		 * <br>
		 * Default: false (multi-connection mode is disabled)
		 * 
		 * @param multiConnectionMode true to enable, false to disable
		 * @return this builder
		 */
		public Builder setMultiConnectionMode(boolean multiConnectionMode) {
			this.multiConnectionMode = multiConnectionMode;
			return this;
		}

		/**
		 * When enabled, this flag ensures that, after a connection fault, a stub will
		 * be reconnected with the same remote object instance that existed before the
		 * fault. If this is not possible, for instance, due to a crash of the remote
		 * JVM process, the stub will be no longer reusable. <br>
		 * <br>
		 * If this is disabled and a new instance of the remote object is re-published,
		 * the stub will be reconnected, but, given the state reached by the remote
		 * object before the connection fault, there is no guarantee about that it will
		 * be the same state after the connection repair.<br>
		 * <br>
		 * Default: false (state consistency is not guaranteed)
		 * 
		 * @param stateConsistencyOnFaultEnabled true to enable state consistency
		 *                                       guarantee on connection fault
		 * @return this builder
		 */
		public Builder setStateConsistencyOnFaultEnabled(boolean stateConsistencyOnFaultEnabled) {
			this.stateConsistencyOnFaultEnabled = stateConsistencyOnFaultEnabled;
			return this;
		}

		/**
		 * Sets the cache size of invocations results for each skeleton. The invocation
		 * cache is used to prevent duplicate invocation requests due to connection
		 * errors.<br>
		 * <br>
		 * Default: 50 entries
		 * 
		 * @param skeletonInvocationCacheSize the max number of invocations return
		 *                                    values to store in the cache of the
		 *                                    skeleton
		 * @return this builder
		 */
		public Builder setSkeletonInvocationCacheSize(int skeletonInvocationCacheSize) {
			this.skeletonInvocationCacheSize = skeletonInvocationCacheSize;
			return this;
		}

		/**
		 * Enables or disables the automatic referencing which is the mechanism that
		 * replace all the remote objects with its stubs when they are transmitted over
		 * RMI streams.<br>
		 * <br>
		 * When automatic referencing is active and a remote object that was not
		 * published on the registry is transmitted as a parameter for a remote
		 * invocation or as a return value, it is automatically published on the
		 * registry. Enabling this mechanism starts a Distributed Garbage Collection
		 * service on the current node, used to remove objects that are not utilized for
		 * a time as long as the {@link #setLeaseTime(int) lease time}.<br>
		 * <br>
		 * When the automatic referencing is not active, the framework can be used as a
		 * plain old RPC middle-ware, where the Distributed Garbage Collection service
		 * is off and no object can be remote without explicitly publishing it on the
		 * RMI registry, so that this is a "service oriented" mode.<br>
		 * <br>
		 * 
		 * Default: true (automatic referencing is enabled)
		 * 
		 * @param automaticReferencingEnabled set to true to enable automatic
		 *                                    referencig, false otherwise.
		 * @return this builder
		 */
		public Builder setAutomaticReferencingEnabled(boolean automaticReferencingEnabled) {
			this.automaticReferencingEnabled = automaticReferencingEnabled;
			return this;
		}

		/**
		 * Sets the lease timeout after that the distributed garbage collection
		 * mechanism will remove a non-named object from the registry.<br>
		 * <br>
		 * 
		 * Default: 600000 milliseconds (10 minutes).
		 * 
		 * @param leaseTime the lease timeout value in milliseconds
		 * @return this builder
		 */
		public Builder setLeaseTime(int leaseTime) {
			this.leaseTime = leaseTime;
			return this;
		}

		/**
		 * Sets an estimate of the TCP connection latency time. This constant is used to
		 * determine update times across the RMI network (e.g. when a remote object has
		 * zero pointers its removal from registry is scheduled to be executed after the
		 * latency time passed).<br>
		 * <br>
		 * 
		 * Default: 10000 milliseconds (10 seconds).
		 * 
		 * @param latencyTime a time in milliseconds
		 * @return this builder
		 */
		public Builder setLatencyTime(int latencyTime) {
			this.latencyTime = latencyTime;
			return this;
		}

		/**
		 * Set an {@link RMIAuthenticator} object that intercept authentication and
		 * authorization requests from remote machines.
		 * 
		 * Default: null (no {@link RMIAuthenticator})
		 * 
		 * @param rmiAuthenticator the {@link RMIAuthenticator} instance to use
		 * @return this builder
		 */
		public Builder setAuthenticator(RMIAuthenticator rmiAuthenticator) {
			this.rmiAuthenticator = rmiAuthenticator;
			return this;
		}

		/**
		 * 
		 * Sets the socket factories that the registry will use. <br>
		 * <br>
		 * Default: default socket factories provided by the JDK
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
		 * build. <br>
		 * <br>
		 * Default: null (no protocol customization)
		 * 
		 * @param protocolEndpointFactory the {@link ProtocolEndpointFactory} instance
		 *                                that gives the {@link ProtocolEndpoint}
		 *                                instance in which the underlying communication
		 *                                streams will be wrapped in
		 * @return this builder
		 */
		public Builder setProtocolEndpointFactory(ProtocolEndpointFactory protocolEndpointFactory) {
			this.protocolEndpointFactory = protocolEndpointFactory;
			return this;
		}

		/**
		 * Enable dynamic code downloading. If code downloading is enabled, this
		 * registry will accept and download code from remote code-bases. <br>
		 * <br>
		 * Default: false (the registry does not download remote code)
		 * 
		 * @param codeDownloadingEnabled true if the dynamic code downloading must be
		 *                               enabled, false otherwise
		 * @return this builder
		 */
		public Builder setCodeDownloadingEnabled(boolean codeDownloadingEnabled) {
			this.codeDownloadingEnabled = codeDownloadingEnabled;
			return this;
		}

		/**
		 * Sets the class loader factory used by this registry to decode remote classes
		 * when code mobility is enabled. It is necessary on some platforms that uses a
		 * different implementation of the Java Virtual Machine. <br>
		 * <br>
		 * Default: null (uses an instance of {@link URLClassLoaderFactory})
		 * 
		 * @param classLoaderFactory the factory to use
		 * @return this builder
		 */
		public Builder setClassLoaderFactory(ClassLoaderFactory classLoaderFactory) {
			this.classLoaderFactory = classLoaderFactory;
			return this;
		}

		/**
		 * Utility method to add a codebase at building time. Codebases can be added and
		 * removed after the registry construction, too.
		 * 
		 * @param url the url to the codebase
		 * @return this builder
		 * @see #addCodebase(URL)
		 */
		public Builder addCodebase(URL url) {
			codebases.add(url);
			return this;
		}

		/**
		 * Utility method to add code-bases at building time. Code-bases can be added
		 * and removed after the registry construction, too.
		 * 
		 * @param urls the URLs of the code-bases
		 * @return this builder
		 * @see #addCodebase(URL)
		 */
		public Builder addCodebases(Iterable<URL> urls) {
			if (urls == null)
				return this;
			for (URL url : urls)
				codebases.add(url);
			return this;
		}

		/**
		 * Utility method to add code-bases at building time. Code-bases can be added
		 * and removed after the registry construction, too.
		 * 
		 * @param urls the URLs of the code-bases
		 * @return this builder
		 * @see #addCodebase(URL)
		 */
		public Builder addCodebases(URL... urls) {
			if (urls == null)
				return this;
			for (URL url : urls)
				codebases.add(url);
			return this;
		}

		/**
		 * Remove all the codebases added to this builder.
		 * 
		 * @return this builder
		 * @see #addCodebase(URL)
		 */
		public Builder clearCodebasesSet() {
			codebases.clear();
			return this;
		}

		/**
		 * Sets the maximum life of each {@link RMIHandler handler}. The actual life is
		 * picked up by using an exponential distribution with a decay chosen basing on
		 * this maximum life parameter.<br>
		 * <br>
		 * Setting this parameter to a value larger than 0, enables the connection
		 * failure simulation, that can be used to generically test the connection fault
		 * tolerance of the application.<br>
		 * <br>
		 * Default: 0 (fault simulation is not active)
		 * 
		 * @param handlerFaultMaxLife the maximum {@link RMIHandler handler} life before
		 *                            a connection failure is simulated, in
		 *                            milliseconds.
		 * @return this builder
		 */
		public Builder setHandlerFaultMaxLife(long handlerFaultMaxLife) {
			this.handlerFaultMaxLife = handlerFaultMaxLife;
			return this;
		}

		/**
		 * Builds the {@link RMIRegistry} instance. After this call, the builder will
		 * not reset and it will be reusable to build new {@link RMIRegistry} instances.
		 * 
		 * @return the built {@link RMIRegistry} instance
		 */
		public RMIRegistry build() {
			return new RMIRegistry(multiConnectionMode, serverSocketFactory, socketFactory, protocolEndpointFactory,
					rmiAuthenticator, codeDownloadingEnabled, codebases, classLoaderFactory, leaseTime, latencyTime,
					automaticReferencingEnabled, skeletonInvocationCacheSize, stateConsistencyOnFaultEnabled,
					suppressAllInvocationFaults, remoteExceptionReplace, handlerFaultMaxLife);
		}
	}

	/**
	 * Creates a new {@link RMIRegistry} with the given ServerSocketFactory,
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
	 * @param authenticator           an {@link RMIAuthenticator} instance that
	 *                                allows to authenticate and authorize users of
	 *                                incoming connection. For example, this
	 *                                instance can be an adapter that access a
	 *                                database or another pre-made authentication
	 *                                system.
	 * @param classLoaderFactory2
	 * 
	 * @see RMIRegistry.Builder
	 * @see RMIRegistry#builder()
	 */
	private RMIRegistry(boolean multiconnectionMode, ServerSocketFactory serverSocketFactory,
			SocketFactory socketFactory, ProtocolEndpointFactory protocolEndpointFactory,
			RMIAuthenticator authenticator, boolean codeDownloadingEnabled, Set<URL> codebases,
			ClassLoaderFactory classLoaderFactory, int leaseTime, int latencyTime, boolean automaticReferencingEnabled,
			int skeletonInvocationCacheSize, boolean stateConsistencyOnFaultEnabled,
			boolean suppressAllInvocationFaults, Class<Exception> remoteExceptionReplace, long handlerFaultMaxLife) {

		Random random = new Random();
		this.registryKey = Long.toHexString(random.nextLong()) + Long.toHexString(random.nextLong())
				+ Long.toHexString(random.nextLong()) + Long.toHexString(random.nextLong())
				+ Long.toHexString(random.nextLong());

		if (serverSocketFactory == null)
			serverSocketFactory = ServerSocketFactory.getDefault();
		if (socketFactory == null)
			socketFactory = SocketFactory.getDefault();
		if (classLoaderFactory == null)
			classLoaderFactory = new URLClassLoaderFactory();

		this.serverSocketFactory = serverSocketFactory;
		this.socketFactory = socketFactory;
		this.protocolEndpointFactory = protocolEndpointFactory;
		this.rmiAuthenticator = authenticator;
		this.codeDownloadingEnabled = codeDownloadingEnabled;
		this.classLoaderFactory = classLoaderFactory;
		this.rmiClassLoader = new RMIClassLoader(codebases, classLoaderFactory);
		this.leaseTime = leaseTime;
		this.latencyTime = latencyTime;
		this.automaticReferencing = automaticReferencingEnabled;
		this.skeletonInvocationCacheSize = skeletonInvocationCacheSize;
		this.stateConsistencyOnFaultEnabled = stateConsistencyOnFaultEnabled;
		this.multiConnectionMode = multiconnectionMode;
		this.handlerFaultMaxLife = handlerFaultMaxLife;
		this.suppressAllInvocationFaults = suppressAllInvocationFaults;
		this.remoteExceptionReplace = remoteExceptionReplace;

		if (automaticReferencingEnabled && leaseTime > 0) {
			// starts Distributed Garbage Collector service
			this.dgc = new DGC();
		}
	}

	/**
	 * Creates a new {@link RMIRegistry.Builder} instance, used to configure and
	 * start a new {@link RMIRegistry} instance.
	 * 
	 * @return a new {@link RMIRegistry.Builder} instance
	 */
	public static Builder builder() { return new Builder(); }

	/**
	 * Let the stubs to throw the specified exception alternatively to
	 * {@link RemoteException} on communication errors.
	 * 
	 * @param exceptionClass the alternative exception class or null if the
	 *                       {@link RemoteException} must not be replaced.
	 */
	public void replaceRemoteException(Class<Exception> exceptionClass) { remoteExceptionReplace = exceptionClass; }

	/**
	 * Gets the {@link RemoteException} replacing class set by invoking the
	 * {@link #replaceRemoteException(Class)} method.
	 * 
	 * @return the exception class that is currently replacing
	 *         {@link RemoteException} or null if {@link RemoteException} is not
	 *         currently replaced.
	 */
	public Class<Exception> getRemoteExceptionReplace() { return remoteExceptionReplace; }

	/**
	 * Gets the suppression state of the RMI faults.
	 * 
	 * @return true if and only if all invocation faults are suppressed
	 * @see Builder#suppressAllInvocationFaults(boolean)
	 */
	public boolean allInvocationFaultsSuppressed() { return suppressAllInvocationFaults; }

	/**
	 * Enables or disables multi-connection mode.
	 * 
	 * @param multiConnectionMode true to enable, false to disable
	 * @see Builder#setMultiConnectionMode(boolean)
	 */
	public void setMultiConnectionMode(boolean multiConnectionMode) {
		this.multiConnectionMode = multiConnectionMode;
	}

	/**
	 * Gets the multi-connection mode enable state.
	 * 
	 * @see #setMultiConnectionMode(boolean)
	 * @return true if multi-connection mode is enabled, false otherwise
	 */
	public boolean isMultiConnectionMode() { return multiConnectionMode; }

	/**
	 * Gets the size of invocations cache for each skeleton.<br>
	 * See {@link Builder#setSkeletonInvocationCacheSize(int)} for more details.
	 * 
	 * @return the max number of invocation results that each skeleton stores in its
	 *         cache
	 */
	public int getSkeletonInvocationCacheSize() { return skeletonInvocationCacheSize; }

	/**
	 * Gets the enabling status of the mechanism that guarantees the status
	 * consistency between stubs and remote objects after a connection fault. See
	 * {@link Builder#setStateConsistencyOnFaultEnabled(boolean)} for more details.
	 * 
	 * @return true if state consistency is guaranteed, false otherwise
	 */
	public boolean getStateConsistencyOnFaultEnabled() { return stateConsistencyOnFaultEnabled; }

	/**
	 * Gets the {@link RMIHandler handler} maximum life used to simulate connection
	 * failures. See {@link Builder#setHandlerFaultMaxLife(long)} for more details.
	 * 
	 * @return the maximum {@link RMIHandler handler} life before a connection
	 *         failure is simulated, in milliseconds.
	 */
	public long getHandlerFaultMaxLife() { return handlerFaultMaxLife; }

	/**
	 * 
	 * Returns the status of the automatic referencing. <br>
	 * <br>
	 * See {@link Builder#setAutomaticReferencingEnabled(boolean)} for more
	 * information.
	 * 
	 * @return true if automatic referencing is enabled, false otherwise
	 */
	public boolean isAutomaticReferencingEnabled() { return automaticReferencing; }

	/**
	 * Gets the class loader factory used by this registry to decode remote classes
	 * when code mobility is enabled.
	 * 
	 * @return the {@link ClassLoaderFactory} used by this registry
	 */
	public ClassLoaderFactory getClassLoaderFactory() { return classLoaderFactory; }

	/**
	 * Gets the lease timeout after that the distributed garbage collection
	 * mechanism will remove a non-named object from the registry.
	 * 
	 * @return the lease timeout value in milliseconds
	 */
	public int getLeaseTime() { return leaseTime; }

	/**
	 * Gets the estimate of the TCP connection latency time given by the developer.
	 * This constant is used to determine update times across the RMI network (e.g.
	 * when a remote object has zero pointers its removal from registry is scheduled
	 * to be executed after the latency time passed).<br>
	 * <br>
	 * 
	 * @see Builder#setLatencyTime(int)
	 * @return the latency time in milliseconds
	 */
	public int getLatencyTime() { return latencyTime; }

	/**
	 * Sets an estimate of the TCP connection latency time. This constant is used to
	 * determine update times across the RMI network (e.g. when a remote object has
	 * zero pointers its removal from registry is scheduled to be executed after the
	 * latency time passed).<br>
	 * <br>
	 * 
	 * @see Builder#setLatencyTime(int)
	 * @param latencyTime a time in milliseconds
	 */
	public void setLatencyTime(int latencyTime) { this.latencyTime = latencyTime; }

	public void setCodeDownloadingEnabled(boolean codeMobilityEnabled) {
		this.codeDownloadingEnabled = codeMobilityEnabled;
	}

	/**
	 * Gets the code mobility enable flag
	 * 
	 * @return true if this registry accepts code from remote codebases, false
	 *         otherwise
	 */
	public boolean isCodeDownloadingEnabled() { return codeDownloadingEnabled; }

	/**
	 * Equinvalent to calling {@link RMIClassLoader#clearCodebasesSet()} on the
	 * result of the method {@link #getRmiClassLoader()}.
	 */
	public void clearCodebasesSet() { getRmiClassLoader().clearCodebasesSet(); }

	/**
	 * This will return all the static codebases and all the received codebases
	 * whose classes are currently instantiated.
	 * 
	 * @return all actually used codebases
	 */
	public Set<URL> getCodebases() { return getRmiClassLoader().getCodebasesSet(); }

	/**
	 * Add new static codebases that will be sent to the other machines.
	 * 
	 * @see #addCodebase(URL)
	 * @param urls the codebases to add
	 */
	public void addCodebases(Iterable<URL> urls) {
		if (urls == null)
			return;
		for (URL url : urls)
			getRmiClassLoader().addCodebase(url);
	}

	/**
	 * Add new static codebases that will be sent to the other machines.
	 * 
	 * @see #addCodebase(URL)
	 * @param urls the codebases to add
	 */
	public void addCodebases(URL... urls) {
		if (urls == null)
			return;
		for (URL url : urls)
			getRmiClassLoader().addCodebase(url);
	}

	/**
	 * Add a new static codebase that will be sent to the other machines.<br>
	 * <br>
	 * A static codebase is constantly active and it is supposed its classes are
	 * always used and available in the current RMI node. It is always propagated to
	 * other RMI nodes to be ready to load it if a class from it is sent over the
	 * stream.
	 * 
	 * @param url the codebase to add
	 */
	public void addCodebase(URL url) {
		if (url == null)
			return;
		getRmiClassLoader().addCodebase(url);
	}

	/**
	 * Removes a static codebase previously added.
	 * 
	 * @see #addCodebase(URL)
	 * @param url the URL of the codebase
	 */
	public void removeCodebase(URL url) { getRmiClassLoader().removeCodebase(url); }

	/**
	 * Returns the {@link RMIClassLoader} instance used to load classes from remote
	 * codebase. This instance can be used to load specific classes that are not in
	 * the current classpath.
	 * 
	 * @return the {@link RMIClassLoader} instance used by this registry
	 */
	public RMIClassLoader getRmiClassLoader() { return rmiClassLoader; }

	/**
	 * Adds authentication details for a remote host
	 * 
	 * @param host           the remote host
	 * @param port           the remote port
	 * @param authId         the authentication identifier
	 * @param authPassphrase the authentication pass-phrase
	 */
	public void setAuthentication(String host, int port, String authId, String authPassphrase) {
		if (finalized)
			throw new IllegalStateException("this registry has been finalized");
		try {
			InetAddress address = InetAddress.getByName(host);
			host = address.getCanonicalHostName();
		} catch (UnknownHostException e) {}
		String key = host + ":" + port;
		String[] auth = new String[] {
				authId, authPassphrase
		};
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
		} catch (UnknownHostException e) {}
		String key = host + ":" + port;
		authenticationMap.remove(key);
	}

	/**
	 * Finalizes this registry instance and all its current open connections.
	 */
	@Override
	public void finalize() { finalize(true); }

	/**
	 * Finalizes this registry instance and all its current open connections.
	 * 
	 * @param signalHandlersFailures set to true if all the {@link RMIHandler}
	 *                               instances created by this registry should send
	 *                               a signal to the attached {@link RMIFaultHandler
	 *                               fault handlers}
	 */
	public void finalize(boolean signalHandlersFailures) {
		synchronized (lock) {
			if (finalized)
				return;
			finalized = true;
		}

		disableListener();

		synchronized (lock) {

			for (Iterator<InetSocketAddress> it = handlers.keySet().iterator(); it.hasNext();) {
				Iterator<RMIHandler> hndit = handlers.get(it.next()).iterator();
				it.remove();
				while (hndit.hasNext()) {
					RMIHandler handler = hndit.next();
					hndit.remove();
					handler.dispose(signalHandlersFailures);
				}
			}

			if (dgc != null)
				dgc.interrupt();
			if (localGCInvoker != null)
				localGCInvoker.interrupt();
		}
		System.gc();
	}

	/**
	 * Gets a {@link StubRetriever} instance linked to this registry
	 * 
	 * @return a {@link StubRetriever} object
	 */
	public StubRetriever getStubRetriever() {
		return new StubRetriever() {
			@Override
			public Object getStub(String address, int port, String objectId, Class<?>... stubInterfaces)
					throws IOException {
				if (System.getSecurityManager() != null)
					System.getSecurityManager().checkConnect(address, port);
				return RMIRegistry.this.getStub(address, port, objectId, stubInterfaces);
			}

			@Override
			public Object getStub(String address, int port, String objectId) throws IOException, InterruptedException {
				if (System.getSecurityManager() != null)
					System.getSecurityManager().checkConnect(address, port);
				return RMIRegistry.this.getStub(address, port, objectId);
			}
		};
	}

	/**
	 * Builds a stub for the specified object identifier on the specified host
	 * respect to the given interface. This method creates the stub locally without
	 * performing TCP communications. Therefore, this method allows to get a stub
	 * when the remote machine is actually not connected, too, and it is preferable
	 * to the {@link #getStub(String, int, String)} method in most real and complex
	 * applications.<br>
	 * <br>
	 * To know about the possible unchecked exceptions thrown by this method see
	 * documentation of
	 * {@link Proxy#newProxyInstance(ClassLoader, Class[], java.lang.reflect.InvocationHandler)
	 * newProxyInstance} method.
	 * 
	 * @param address        the host address
	 * @param port           the host port
	 * @param objectId       the remote object identifier
	 * @param stubInterfaces the interfaces implemented by the stub
	 * @return the stub object
	 * 
	 * @see #getStub(String, int, String)
	 */
	public Object getStub(String address, int port, String objectId, Class<?>... stubInterfaces) {
		if (finalized)
			throw new IllegalStateException("this registry has been finalized");
		return Proxy
				.newProxyInstance(stubInterfaces[0].getClassLoader(), stubInterfaces,
						new RemoteInvocationHandler(this, address, port, objectId));
	}

	/**
	 * Gets a stub for the specified object identifier representing a remote object
	 * on a remote machine. This method performs a request to the remote machine to
	 * get the remote interfaces of the remote object, then it creates the stub. All
	 * the remote interfaces of the remote object must be visible by the default
	 * class loader and they must be known by each local runtime.<br>
	 * <br>
	 * To know about the possible unchecked exceptions thrown by this method see
	 * documentation of
	 * {@link Proxy#newProxyInstance(ClassLoader, Class[], java.lang.reflect.InvocationHandler)
	 * newProxyInstance} method.
	 * 
	 * @param address  the host address
	 * @param port     the host port
	 * @param objectId the object identifier
	 * @return A dynamic proxy object that represents the remote instance. It is an
	 *         instance for the specified stub interface
	 * 
	 * @throws UnknownHostException if the remote host cannot be found
	 * @throws IOException          if an I/O error occurs while contacting the
	 *                              remote machine
	 * @throws InterruptedException if the current thread is interrupted during
	 *                              operation
	 * @see #getStub(String, int, String, Class...)
	 */
	public Object getStub(String address, int port, String objectId)
			throws UnknownHostException, IOException, InterruptedException {
		if (finalized)
			throw new IllegalStateException("this registry has been finalized");
		return getRMIHandler(address, port, multiConnectionMode).getStub(objectId);
	}

	/**
	 * Gets an {@link RMIHandler handler} for the specified host. If it has not been
	 * created, then creates it. If any {@link RMIHandler handler} already exists
	 * and {@link #setMultiConnectionMode(boolean) multi-connection mode} is
	 * disabled, gets one of them. If {@link #setMultiConnectionMode(boolean)
	 * multi-connection mode} is enabled, this method creates a new, never used,
	 * {@link RMIHandler handler}.
	 * 
	 * @param host the host address
	 * @param port the host port
	 * @return a handler related to the specified host
	 * @throws UnknownHostException if the host address cannot be resolved
	 * @throws IOException          if I/O errors occur
	 * 
	 * @see #getRMIHandler(String, int, boolean)
	 */
	public RMIHandler getRMIHandler(String host, int port) throws IOException {
		return getRMIHandler(host, port, multiConnectionMode);
	}

	/**
	 * Gets an {@link RMIHandler handler} for the specified host. If it has not been
	 * created, then creates it. If a new connection must be created, this method
	 * returns a new, never used, {@link RMIHandler handler}.
	 * 
	 * @param host          the host address
	 * @param port          the host port
	 * @param newConnection set to true to create a new handler without getting an
	 *                      already existing one
	 * @return a handler related to the specified host
	 * @throws UnknownHostException if the host address cannot be resolved
	 * @throws IOException          if I/O errors occurs
	 * 
	 * @see #getRMIHandler(String, int)
	 */
	public RMIHandler getRMIHandler(String host, int port, boolean newConnection) throws IOException {
		if (finalized)
			throw new IllegalStateException("this registry has been finalized");
		Callable<RMIHandler> callable = () -> {
			synchronized (lock) {
				InetSocketAddress inetAddress = new InetSocketAddress(host, port);
				if (!handlers.containsKey(inetAddress))
					handlers.put(inetAddress, new ArrayList<>(1));
				List<RMIHandler> rmiHandlers = handlers.get(inetAddress);
				RMIHandler handler = null;
				if (rmiHandlers.size() == 0 || newConnection) {
					handler = new RMIHandler(socketFactory.createSocket(host, port), RMIRegistry.this,
							protocolEndpointFactory, true);
					// bind handler to host
					rmiHandlers.add(handler);
					if (!handlersByKey.containsKey(handler.getRemoteRegistryKey()))
						handlersByKey.put(handler.getRemoteRegistryKey(), new LinkedList<>());
					handlersByKey.get(handler.getRemoteRegistryKey()).add(handler);
					handler.start();
				} else
					handler = rmiHandlers.get(0);
				return handler;
			}
		};

		Future<RMIHandler> future = executorService.submit(callable);
		try {
			return future.get();
		} catch (InterruptedException e) {
			return null;
		} catch (ExecutionException e) {
			throw new IOException(e.getCause());
		}
	}

	/**
	 * Close all currently active connections to the specified remote socket
	 * (host/port).<br>
	 * The stubs referring to the objects on the specified socket can create new
	 * connections if they are used.
	 * 
	 * @param host        the string address of the remote host
	 * @param port        the port of the remote socket
	 * @param signalFault set to true if each {@link RMIHandler handler} connected
	 *                    to the remote host should send a signal to the
	 *                    {@link RMIFaultHandler fault handlers} instances attached
	 *                    to this {@link RMIRegistry registry}
	 */
	public void closeAllConnections(String host, int port, boolean signalFault) {
		if (finalized)
			return;
		Callable<Void> callable = () -> {
			synchronized (lock) {
				InetSocketAddress inetAddress = new InetSocketAddress(host, port);
				if (!handlers.containsKey(inetAddress))
					return null;
				List<RMIHandler> rmiHandlers = handlers.get(inetAddress);
				for (RMIHandler hnd : rmiHandlers) { hnd.dispose(signalFault); }
			}
			return null;
		};

		Future<Void> future = executorService.submit(callable);
		try {
			future.get();
		} catch (InterruptedException e) {
			return;
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Enable the registry listener on the selected port. This method enables the
	 * registry to accept new external incoming connections for RMI
	 * 
	 * @param port   the port to start the listener on
	 * @param daemon if true, the listener is started as daemon, that is it will be
	 *               stopped when all other non-daemon threads in the application
	 *               will be terminated.
	 * @throws IOException if I/O errors occur
	 */
	public void enableListener(int port, boolean daemon) throws IOException {
		if (finalized)
			throw new IllegalStateException("this registry has been finalized");
		Callable<Void> callable = () -> {
			synchronized (lock) {
				if (listener != null)
					disableListener();
				serverSocket = serverSocketFactory.createServerSocket(port);
				this.listenerPort = serverSocket.getLocalPort();
				listener = new TCPListener(daemon);
			}
			return null;
		};
		try {
			executorService.submit(callable).get();
		} catch (InterruptedException e) {} catch (ExecutionException e) {
			throw (IOException) e.getCause();
		}
	}

	/**
	 * Disable the registry listener. This method will disallow the registry to
	 * accept new incoming connections, but does not close the current open ones.
	 */
	public void disableListener() {
		Callable<Void> callable = () -> {
			synchronized (lock) {
				if (listener == null)
					return null;

				listener.interrupt();
				listener = null;
				try {
					serverSocket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				listenerPort = 0;
			}
			return null;
		};
		try {
			executorService.submit(callable).get();
		} catch (InterruptedException e) {} catch (ExecutionException e) {}
	}

	/**
	 * Gets the port on which the listener was started on last time
	 * 
	 * @return The last listener TCP port
	 */
	public int getListenerPort() { return listenerPort; }

	/**
	 * Gets the rmiAuthenticator of this registry
	 * 
	 * @return the rmiAuthenticator associated to this registry
	 */
	public RMIAuthenticator getAuthenticator() { return rmiAuthenticator; }

	/**
	 * Publish the given object respect to the specified interface.
	 * 
	 * @param name   the identifier to use for this service
	 * @param object the implementation of the service to publish
	 * @throws IllegalArgumentException if the specified identifier was already
	 *                                  bound or if the objectId parameter matches
	 *                                  the automatic referencing objectId pattern
	 *                                  that is /\#[0-9]+/
	 */
	public void publish(String name, Object object) {
		if (finalized)
			throw new IllegalStateException("this registry has been finalized");
		if (name.startsWith(Skeleton.IDENTIFIER_PREFIX))
			throw new IllegalArgumentException("The name prefix '" + Skeleton.IDENTIFIER_PREFIX
					+ "' is reserved to atomatic referencing. Please use another name pattern.");

		synchronized (lock) {
			Skeleton sk = null;
			if (skeletonByObject.containsKey(object)) {
				sk = skeletonByObject.get(object);
				sk.addNames(name);
				getSkeletonByIdMap().put(name, sk);

				if (getSkeletonByIdMap().containsKey(name) && getSkeletonByIdMap().get(name) != sk)
					throw new IllegalArgumentException("the given object name '" + name + "' is already bound.");

				if (sk.getRemoteObject() != object)
					throw new IllegalStateException(
							"INTERNAL ERROR: the given object is associated to a skeleton that does not references it");
			} else {
				if (getSkeletonByIdMap().containsKey(name))
					throw new IllegalArgumentException("the given object name '" + name + "' is already bound.");
				sk = new Skeleton(object, this);
				sk.addNames(name);
				getSkeletonByIdMap().put(name, sk);
				getSkeletonByIdMap().put(sk.getId(), sk);
				skeletonByObject.put(object, sk);
			}
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
		if (finalized)
			throw new IllegalStateException("this registry has been finalized");
		synchronized (lock) {
			if (skeletonByObject.containsKey(object)) {
				Skeleton sk = skeletonByObject.get(object);
				if (sk.getRemoteObject() != object)
					throw new IllegalStateException(
							"the given object is associated to a skeleton that does not references it");
				return sk.getId();
			} else {
				Skeleton sk = new Skeleton(object, this);
				getSkeletonByIdMap().put(sk.getId(), sk);
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
		if (finalized)
			throw new IllegalStateException("this registry has been finalized");
		synchronized (lock) {
			Skeleton skeleton = skeletonByObject.remove(object);
			if (skeleton != null) {
				getSkeletonByIdMap().remove(skeleton.getId());
				for (String id : skeleton.names())
					getSkeletonByIdMap().remove(id);
			}
		}
	}

	/**
	 * Attach a {@link RMIFaultHandler} object
	 * 
	 * @param o the fault handler
	 */
	public void attachFaultHandler(RMIFaultHandler o) {
		if (finalized)
			throw new IllegalStateException("this registry has been finalized");
		synchronized (lock) {
			rmiFaultHandlers.add(o);
		}
	}

	/**
	 * Detach a {@link RMIFaultHandler} object
	 * 
	 * @param o the fault handler
	 */
	public void detachFailureHandler(RMIFaultHandler o) {
		if (finalized)
			throw new IllegalStateException("this registry has been finalized");
		synchronized (lock) {
			rmiFaultHandlers.remove(o);
		}
	}

	/**
	 * Gets a published remote object by identifier
	 * 
	 * @param objectId object identifier
	 * @return the remotized object
	 */
	public Object getRemoteObject(String objectId) {
		if (finalized)
			throw new IllegalStateException("this registry has been finalized");
		synchronized (lock) {
			Skeleton skeleton = getSkeletonByIdMap().get(objectId);
			if (skeleton != null)
				return skeleton.getRemoteObject();
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
		if (finalized)
			throw new IllegalStateException("this registry has been finalized");
		synchronized (lock) {
			Skeleton skeleton = skeletonByObject.get(object);
			if (skeleton != null)
				return skeleton.getId();
			else
				return null;
		}
	}

	/**
	 * Exports the specified interfaces as remote. This method calls the
	 * {@link #exportInterface(Class)} method for each specified interface.
	 * 
	 * @param interfaces the interfaces to export
	 * @return this registry
	 * @see #exportInterface(Class)
	 */
	public RMIRegistry exportInterfaces(Class<?>... interfaces) {
		for (Class<?> cls : interfaces)
			exportInterface(cls);
		return this;
	}

	/**
	 * Marks an interface to automatically create remote references for objects that
	 * are sent as arguments for an invocation or as return values. The objects are
	 * automatically published on this registry when the related parameter of the
	 * stub method has a type that matches with the marked interface
	 * 
	 * @param remoteIf the interface to mark
	 * @return this registry
	 */
	public RMIRegistry exportInterface(Class<?> remoteIf) {
		synchronized (lock) {
			if (remoteIf == Remote.class)
				throw new IllegalArgumentException("agilermi.Remote interface cannot be exported!");
			if (!remoteIf.isInterface())
				throw new IllegalArgumentException("class " + remoteIf.getCanonicalName() + " is not an interface");
			remotes.add(remoteIf);
		}
		return this;
	}

	/**
	 * Remove a automatic referencing mark for the interface. See the
	 * {@link RMIRegistry#exportInterface(Class)} method. All the objects
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
	 * Check if a class is marked for automatic referencing. A concrete or abstract
	 * class is never remote. An interface is remote if it, directly or indirectly,
	 * extends the {@link Remote} interface or if it was exported or if it extends,
	 * directly or indirectly, an interface that was exported on this registry to be
	 * remote.<br>
	 * See the {@link RMIRegistry#exportInterface(Class)} method.
	 * 
	 * @param remoteIf the interface to check
	 * @return true if the interface is marked for automatic referencing, false
	 *         otherwise
	 */
	public boolean isRemote(Class<?> remoteIf) {
		if (!remoteIf.isInterface() || remoteIf == Remote.class)
			return false;

		// is it statically marked as remote?
		if (Remote.class.isAssignableFrom(remoteIf))
			return true;

		try {
			Class<?> javaRemote = ClassLoader.getSystemClassLoader().loadClass("java.rmi.Remote");
			if (javaRemote.isAssignableFrom(remoteIf))
				return true;
		} catch (ClassNotFoundException e) {}

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
	 * Gets the remote host of the specified RMI stub.
	 * 
	 * @param stub the RMI stub
	 * @return the host address or name
	 * @throws IllegalArgumentException if the specified object is not an RMI stub
	 */
	public String getStubHost(Object stub) {
		if (Proxy.isProxyClass(stub.getClass())) {
			InvocationHandler ih = Proxy.getInvocationHandler(stub);
			if (ih instanceof RemoteInvocationHandler)
				return ((RemoteInvocationHandler) ih).getHost();
		}
		throw new IllegalArgumentException("The specified object is not an RMI stub");
	}

	/**
	 * Gets the remote host port of the specified RMI stub.
	 * 
	 * @param stub the RMI stub
	 * @return the host port
	 * @throws IllegalArgumentException if the specified object is not an RMI stub
	 */
	public int getStubPort(Object stub) {
		if (Proxy.isProxyClass(stub.getClass())) {
			InvocationHandler ih = Proxy.getInvocationHandler(stub);
			if (ih instanceof RemoteInvocationHandler)
				return ((RemoteInvocationHandler) ih).getPort();
		}
		throw new IllegalArgumentException("The specified parameter is not an RMI stub");
	}

	/**
	 * Gets the object identifier of the specified RMI stub.
	 * 
	 * @param stub the RMI stub
	 * @return the identifier of the remote object
	 * @throws IllegalArgumentException if the specified object is not an RMI stub
	 */
	public String getStubObjectIdentifier(Object stub) {
		if (Proxy.isProxyClass(stub.getClass())) {
			InvocationHandler ih = Proxy.getInvocationHandler(stub);
			if (ih instanceof RemoteInvocationHandler)
				return ((RemoteInvocationHandler) ih).getObjectId();
		}
		throw new IllegalArgumentException("The specified parameter is not an RMI stub");
	}

	/**
	 * Package-scoped. Find a handler associated to the specified remote registry
	 * key. This method is primarily used by {@link RemoteInvocationHandler stubs}
	 * to restore a failed connection.
	 * 
	 * @param remoteRegistryKey the key of the remote registry the handler must be
	 *                          associated to
	 * @return an {@link RMIHandler handler}
	 */
	RMIHandler findHandlerByRegistryKey(String remoteRegistryKey) {
		synchronized (lock) {
			List<RMIHandler> handlers = handlersByKey.get(remoteRegistryKey);
			if (handlers != null && !handlers.isEmpty())
				return handlersByKey.get(remoteRegistryKey).get(0);
		}
		return null;
	}

	/**
	 * Package-scoped. Removes a {@link RMIHandler handler} from this registry. This
	 * method should be used by {@link RMIHandler} itself, only.
	 * 
	 * @param handler the {@link RMIHandler handler to remove}
	 */
	void removeHandler(RMIHandler handler) {
		synchronized (lock) {
			List<RMIHandler> list = handlers.get(handler.getInetSocketAddress());
			if (list != null)
				list.remove(handler);

			List<RMIHandler> byKey = handlersByKey.get(handler.getRemoteRegistryKey());
			if (byKey != null)
				byKey.remove(handler);
		}
	}

	/**
	 * Package-scoped. Operation used to broadcast a {@link RMIHandler} failure to
	 * the failure observers attached to this registry
	 * 
	 * @param handler   the object peer that caused the failure
	 * @param exception the exception thrown by the object peer
	 */
	void notifyFault(RMIHandler handler) {
		if (finalized)
			return;

		Set<RMIFaultHandler> faultHandlers;
		synchronized (lock) {
			faultHandlers = new HashSet<>(rmiFaultHandlers);
		}

		faultHandlers.forEach(o -> {
			try {
				o.onFault(handler, handler.getDispositionException());
			} catch (Throwable e) {
				// e.printStackTrace();
			}
		});
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

	/**
	 * Gets the key of this registry. This key is a randomly generated string that
	 * is used to identify this registry in a RMI network without using the IP/Name
	 * of the local host.
	 * 
	 * @return the registry key
	 */
	String getRegistryKey() { return registryKey; }

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
		} catch (UnknownHostException e) {}
		String key = host + ":" + port;
		return authenticationMap.get(key);
	}

	/**
	 * Package-scoped. Gets authentication relative to a remote process.
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

	/**
	 * Package-scoped. Gets the map (name/id => skeleton)
	 * 
	 * @return the skeletonById map
	 */
	Map<String, Skeleton> getSkeletonByIdMap() { return skeletonById; }

	/**
	 * Package-scoped. Gets the map (object => skeleton)
	 * 
	 * @return the skeletonByObject map
	 */
	Map<Object, Skeleton> getSkeletonByObjectMap() { return skeletonByObject; }
}
