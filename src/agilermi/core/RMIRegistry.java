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
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
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

	private double faultSimulationProbability = 0;

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

	private boolean finalized = false;

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

	// the peer that are currently online
	private Map<InetSocketAddress, List<RMIHandler>> handlers = new HashMap<>();

	// socket factories
	private ServerSocketFactory serverSocketFactory;
	private SocketFactory socketFactory;

	// currently attached fault handler
	private Set<RMIFaultHandler> rmiFaultHandlers = Collections
			.newSetFromMap(new WeakHashMap<RMIFaultHandler, Boolean>());

	// enable the stubs to throw a remote exception when invoked after a connection
	// failure
	private boolean remoteExceptionEnabled = true;

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
	private Thread dgc;

	// the reference to the main thread
	private Thread listener;

	private LocalGarbageCollectionThread localGCInvoker = new LocalGarbageCollectionThread();

	private class LocalGarbageCollectionThread extends Thread {
		public LocalGarbageCollectionThread() {
			setName(LocalGarbageCollectionThread.class.getName());
			setDaemon(true);
			start();
		}

		@Override
		public void run() {
			try {
				while (!isInterrupted()) {
					System.gc();
					Thread.sleep(5000);
				}
			} catch (InterruptedException e) {
			}
		}
	}

	/**
	 * Distributed Garbage Collection service task
	 */
	private Runnable dgcTask = new Runnable() {
		@Override
		public void run() {
			try {
				while (!Thread.currentThread().isInterrupted()) {
					long waitTime = leaseTime;

					Set<Skeleton> skeletons;
					synchronized (skeletonByObject) {
						skeletons = new HashSet<>(skeletonByObject.values());
					}

					for (Skeleton skeleton : skeletons) {
						if (skeleton.isGarbage()) {
							skeleton.unpublish();
						} else {
							waitTime = Math.min(waitTime, System.currentTimeMillis() - skeleton.getLastUseTime());
						}
					}

					// invoke local garbage collector
					System.gc();

					// System.out.println("[DGC] end");
					Thread.sleep(waitTime);
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	};

	/**
	 * Defines the task that accepts new incoming connections and creates
	 * {@link RMIHandler} objects
	 */
	private Runnable listenerTask = new Runnable() {
		@Override
		public void run() {
			Thread.currentThread().setName("RMIRegistry.listenerTask");
			while (listener != null && !listener.isInterrupted())
				try {
					Socket socket = serverSocket.accept();
					RMIHandler rmiHandler = new RMIHandler(socket, RMIRegistry.this, protocolEndpointFactory, false);
					if (!handlers.containsKey(rmiHandler.getInetSocketAddress()))
						handlers.put(rmiHandler.getInetSocketAddress(), new ArrayList<>(1));
					handlers.get(rmiHandler.getInetSocketAddress()).add(rmiHandler);
					rmiHandler.start();
				} catch (IOException e) {
					// e.printStackTrace();
				}
		};
	};

	/**
	 * Builder for {@link RMIRegistry}. A new instance of this class can be returned
	 * by the {@link RMIRegistry#builder()} static method. A new instance of this
	 * class wraps all the defaults for {@link RMIRegistry} and allows to modify
	 * them. When the configuration has been terminated, a new {@link RMIRegistry}
	 * instance can be obtained by the {@link Builder#build()} method.<br>
	 * <br>
	 * An instance of this class can be re-used after each call to the
	 * {@link Builder#build()} method to generate new instances of
	 * {@link RMIRegistry}. Each call to the {@link Builder#build()} method does not
	 * reset the builder instance to its default state.
	 * 
	 * @author Salvatore Giampa'
	 *
	 */
	public static class Builder {

		private Builder() {
		}

		// multi-connection mode (one connection for each stub)
		boolean multiConnectionMode = false;

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

		/**
		 * Enable or disable multi-connection mode. If it is enabled, tends to create
		 * new connections for each created or received stub. <br>
		 * <br>
		 * When disabled it tends to share the same TCP connections across all local
		 * stubs.<br>
		 * <br>
		 * Default: false
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
		 * Default: false
		 * 
		 * @param stateConsistencyOnFaultEnabled true to enable status consistency
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
		 * Default: 50
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
		 * registry. This mechanism requires that a Distributed Garbage Collector
		 * service is active on this node, to remove objects that are not utilized for a
		 * time as long as the lease time (see {@link #setLeaseTime(int)}).<br>
		 * <br>
		 * When the automatic referencing is not active, the framework can be used as a
		 * plain old RPC middle-ware, where the Distributed Garbage Collection service
		 * is off and no object can be remote without explicitly publishing it on the
		 * RMI registry, so that this is a "service oriented" mode.<br>
		 * <br>
		 * 
		 * Default: true
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
		 * Default: 5000 milliseconds.
		 * 
		 * @param latencyTime a time in milliseconds
		 * @return this builder
		 */
		public Builder setLatencyTime(int latencyTime) {
			this.latencyTime = latencyTime;
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
		 * Enable dynamic code downloading. If code downloading is enabled, this
		 * registry will accept and download code from remote code-bases. <br>
		 * <br>
		 * Default: false
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
		 * Builds the {@link RMIRegistry} instance. After this call, the builder will
		 * not reset and it will be reusable to build new {@link RMIRegistry} instances.
		 * 
		 * @return the built {@link RMIRegistry} instance
		 */
		public RMIRegistry build() {
			return new RMIRegistry(multiConnectionMode, serverSocketFactory, socketFactory, protocolEndpointFactory,
					rmiAuthenticator, codeDownloadingEnabled, codebases, classLoaderFactory, leaseTime, latencyTime,
					automaticReferencingEnabled, skeletonInvocationCacheSize, stateConsistencyOnFaultEnabled);
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
			int skeletonInvocationCacheSize, boolean stateConsistencyOnFaultEnabled) {

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

		if (automaticReferencingEnabled && leaseTime > 0) {
			// starts Distributed Garbage Collector service
			this.dgc = new Thread(dgcTask);
			this.dgc.setName("Distributed Garbage Collector service");
			this.dgc.setDaemon(true);
			this.dgc.start();
		}
	}

	/**
	 * Creates a new {@link RMIRegistry.Builder} instance, used to configure and
	 * start a new {@link RMIRegistry} instance.
	 * 
	 * @return a new {@link RMIRegistry.Builder} instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Enable or disable multi-connection mode.<br>
	 * <br>
	 * If it is enabled, the RMI environment tends to create new connections for
	 * each created or received stub.<br>
	 * When disabled the RMI environment tends to share the same connections across
	 * all local stubs.<br>
	 * <br>
	 * By default it is disabled.
	 * 
	 * @param multiConnectionMode true to enable, false to disable
	 */
	public void setMultiConnectionMode(boolean multiConnectionMode) {
		this.multiConnectionMode = multiConnectionMode;
	}

	/**
	 * Shows the multi-connection mode enable state.
	 * 
	 * @see #setMultiConnectionMode(boolean)
	 * @return true if multi-connection mode is enabled, false otherwise
	 */
	public boolean isMultiConnectionMode() {
		return multiConnectionMode;
	}

	/**
	 * Gets the size of invocations cache for each skeleton.<br>
	 * See {@link Builder#setSkeletonInvocationCacheSize(int)} for more details.
	 * 
	 * @return the max number of invocation results that each skeleton stores in its
	 *         cache
	 */
	public int getSkeletonInvocationCacheSize() {
		return skeletonInvocationCacheSize;
	}

	/**
	 * Gets the enabling status of the mechanism that guarantees the status
	 * consistency between stubs and remote objects after a connection fault. See
	 * {@link Builder#setStateConsistencyOnFaultEnabled(boolean)} for more details.
	 * 
	 * @return true if state consistency is guaranteed, false otherwise
	 */
	public boolean getStateConsistencyOnFaultEnabled() {
		return stateConsistencyOnFaultEnabled;
	}

	/**
	 * Enables the developer to simulate connection faults between machines,
	 * specifying a fault probability. This is useful to test the application on
	 * simulated unreliable connections.
	 * 
	 * @param probability a double between 0 and 1 that is the probability to have a
	 *                    connection fault every time a message is sent to remote
	 *                    {@link RMIHandler} instances. Set this to a value equal to
	 *                    or smaller than 0 to disable fault simulation. The default
	 *                    value is 0.
	 */
	public void setFaultSimulationProbability(double probability) {
		this.faultSimulationProbability = Math.max(-0.0d, Math.min(probability, 1.0d));
	}

	/**
	 * Gets the fault simulation probability. See
	 * {@link #setFaultSimulationProbability(double)} for more details.
	 * 
	 * @return the last setted probability value
	 */
	public double getFaultSimulationProbability() {
		return faultSimulationProbability;
	}

	/**
	 * 
	 * Returns the status of the utomatic referencing. <br>
	 * <br>
	 * See {@link Builder#setAutomaticReferencingEnabled(boolean)} for more
	 * information.
	 * 
	 * @return true if automatic referencing is enabled, false otherwise
	 */
	public boolean isAutomaticReferencingEnabled() {
		return automaticReferencing;
	}

	/**
	 * Gets the class loader factory used by this registry to decode remote classes
	 * when code mobility is enabled.
	 * 
	 * @return the {@link ClassLoaderFactory} used by this registry
	 */
	public ClassLoaderFactory getClassLoaderFactory() {
		return classLoaderFactory;
	}

	/**
	 * Gets the lease timeout after that the distributed garbage collection
	 * mechanism will remove a non-named object from the registry.
	 * 
	 * @return the lease timeout value in milliseconds
	 */
	public int getLeaseTime() {
		return leaseTime;
	}

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
	public int getLatencyTime() {
		return latencyTime;
	}

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
	public void setLatencyTime(int latencyTime) {
		this.latencyTime = latencyTime;
	}

	public void setCodeDownloadingEnabled(boolean codeMobilityEnabled) {
		this.codeDownloadingEnabled = codeMobilityEnabled;
	}

	/**
	 * Equinvalent to calling {@link RMIClassLoader#clearCodebasesSet()} on the
	 * result of the method {@link #getRmiClassLoader()}.
	 */
	public void clearCodebasesSet() {
		getRmiClassLoader().clearCodebasesSet();
	}

	/**
	 * This will return all the static codebases and all the received codebases
	 * whose classes are currently instantiated.
	 * 
	 * @return all actually used codebases
	 */
	public Set<URL> getCodebases() {
		return getRmiClassLoader().getCodebasesSet();
	}

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
	public void removeCodebase(URL url) {
		getRmiClassLoader().removeCodebase(url);
	}

	/**
	 * Returns the {@link RMIClassLoader} instance used to load classes from remote
	 * codebase. This instance can be used to load specific classes that are not in
	 * the current classpath.
	 * 
	 * @return the {@link RMIClassLoader} instance used by this registry
	 */
	public RMIClassLoader getRmiClassLoader() {
		return rmiClassLoader;
	}

	/**
	 * Gets the code mobility enable flag
	 * 
	 * @return true if this registry accepts code from remote codebases, false
	 *         otherwise
	 */
	public boolean isCodeDownloadingEnabled() {
		return codeDownloadingEnabled;
	}

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
	 * Finalizes this registry instance and all its current open connections.
	 */
	@Override
	public void finalize() {
		finalize(true);
	}

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
			disableListener();
			finalized = true;
			for (Iterator<InetSocketAddress> it = handlers.keySet().iterator(); it.hasNext(); it.remove())
				for (RMIHandler rMIHandler : handlers.get(it.next()))
					rMIHandler.dispose(signalHandlersFailures);
		}
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
	 * performing TCP communications.
	 * 
	 * @param address        the host address
	 * @param port           the host port
	 * @param objectId       the remote object identifier
	 * @param stubInterfaces the interfaces implemented by the stub
	 * @return the stub object
	 */
	public Object getStub(String address, int port, String objectId, Class<?>... stubInterfaces) {
		return Proxy.newProxyInstance(stubInterfaces[0].getClassLoader(), stubInterfaces,
				new RemoteInvocationHandler(this, address, port, objectId));
	}

	/**
	 * Gets a stub for the specified object identifier representing a remote object
	 * on a remote machine. This method performs a request to the remote machine to
	 * get the remote interfaces of the remote object, then it creates the stub. All
	 * the remote interfaces of the remote object must be visible by the default
	 * class loader and they must be known by the local runtime.
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
	 */
	public Object getStub(String address, int port, String objectId)
			throws UnknownHostException, IOException, InterruptedException {
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
	 */
	public RMIHandler getRMIHandler(String host, int port, boolean newConnection) throws IOException {
		Callable<RMIHandler> callable = () -> {
			synchronized (lock) {
				InetSocketAddress inetAddress = new InetSocketAddress(host, port);
				if (!handlers.containsKey(inetAddress))
					handlers.put(inetAddress, new ArrayList<>(1));
				List<RMIHandler> rmiHandlers = handlers.get(inetAddress);
				RMIHandler rmiHandler = null;
				if (rmiHandlers.size() == 0 || newConnection) {
					rmiHandler = new RMIHandler(socketFactory.createSocket(host, port), RMIRegistry.this,
							protocolEndpointFactory, true);
					// bind handler to host
					rmiHandlers.add(rmiHandler);
					rmiHandler.start();
				} else
					rmiHandler = rmiHandlers.get(0);
				return rmiHandler;
			}
		};

		Future<RMIHandler> future = executorService.submit(callable);
		try {
			return future.get();
		} catch (InterruptedException e) {
			return null;
		} catch (ExecutionException e) {
			throw (IOException) e.getCause().fillInStackTrace();
		}
	}

	public void closeAllConnections(String host, int port) throws IOException {
		Callable<Void> callable = () -> {
			synchronized (lock) {
				InetSocketAddress inetAddress = new InetSocketAddress(host, port);
				if (!handlers.containsKey(inetAddress))
					return null;
				List<RMIHandler> rmiHandlers = handlers.get(inetAddress);
				for (RMIHandler hnd : rmiHandlers) {
					hnd.dispose(true);
				}
				rmiHandlers.clear();
			}
			return null;
		};

		Future<Void> future = executorService.submit(callable);
		try {
			future.get();
		} catch (InterruptedException e) {
			return;
		} catch (ExecutionException e) {
			throw (IOException) e.getCause().fillInStackTrace();
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
		Callable<Void> callable = () -> {
			synchronized (lock) {
				if (listener != null)
					disableListener();
				serverSocket = serverSocketFactory.createServerSocket(port);
				this.listenerPort = serverSocket.getLocalPort();
				listener = new Thread(listenerTask);
				listener.setDaemon(daemon);
				listener.start();
			}
			return null;
		};
		try {
			executorService.submit(callable).get();
		} catch (InterruptedException e) {
		} catch (ExecutionException e) {
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
		} catch (InterruptedException e) {
		} catch (ExecutionException e) {
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
	 * Gets the rmiAuthenticator of this registry
	 * 
	 * @return the rmiAuthenticator associated to this registry
	 */
	public RMIAuthenticator getAuthenticator() {
		return rmiAuthenticator;
	}

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
		} catch (ClassNotFoundException e) {
		}

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

	void removeRMIHandler(RMIHandler handler) {
		synchronized (lock) {
			List<RMIHandler> list = handlers.get(handler.getInetSocketAddress());
			if (list != null)
				list.remove(handler);
		}
	}

	/**
	 * Package-scoped. Operation used to broadcast a {@link RMIHandler} failure to
	 * the failure observers attached to this registry
	 * 
	 * @param handler   the object peer that caused the failure
	 * @param exception the exception thrown by the object peer
	 */
	void sendRMIHandlerFault(RMIHandler handler) {
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
				e.printStackTrace();
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
	String getRegistryKey() {
		return registryKey;
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

	/**
	 * @return the skeletonById
	 */
	Map<String, Skeleton> getSkeletonByIdMap() {
		return skeletonById;
	}

	/**
	 * @return the skeletonByObject
	 */
	Map<Object, Skeleton> getSkeletonByObjectMap() {
		return skeletonByObject;
	}
}
