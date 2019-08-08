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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.NotSerializableException;
import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Handler;

import javax.net.SocketFactory;

import agilermi.authentication.RMIAuthenticator;
import agilermi.communication.ProtocolEndpoint;
import agilermi.communication.ProtocolEndpointFactory;
import agilermi.configuration.RMIFaultHandler;
import agilermi.exception.AuthorizationException;
import agilermi.exception.LocalAuthenticationException;
import agilermi.exception.ObjectNotFoundException;
import agilermi.exception.RemoteAuthenticationException;
import agilermi.exception.RemoteException;

/**
 * This class defines a RMI connection handler. The instances of this class
 * manages all the RMI communication protocol between the local machine and a
 * remote machine. This class can be instantiated through the RMIRegistry only.
 * See the {@link RMIRegistry#getRMIHandler(String, int, boolean)
 * getRMIHandler(...)} method
 * 
 * @author Salvatore Giampa'
 *
 */
public final class RMIHandler {

	// connection details, socket and streams
	private InetSocketAddress inetSocketAddress;
	private int remotePort;
	private Socket socket;
	private RMIObjectOutputStream outputStream;
	private RMIObjectInputStream inputStream;

	// registry associated to this handler
	private RMIRegistry registry;

	// Map for invocations that are waiting a response
	private Map<Long, InvocationMessage> invocations = Collections.synchronizedMap(new HashMap<>());

	// Map for interface requests that are waiting a response
	private Map<Long, RemoteInterfaceMessage> interfaceRequests = Collections.synchronizedMap(new HashMap<>());

	// The queue for buffered invocations that are ready to be sent over the socket
	private BlockingQueue<RMIMessage> messageQueue = new LinkedBlockingQueue<>();

	// Flag that indicates if this RMIHandler has been disposed.
	private boolean disposed = false;

	// the cause of the disposition. This is null if the dispose() method was called
	// externally.
	private Exception dispositionException = null;

	// timestamp in milliseconds of the disposition of this handler
	private long dispositionTime = 0;

	// remote references requested by the other machine
	private Set<String> references = new TreeSet<>();

	// the authentication identifier received by the remote machine
	private String remoteAuthIdentifier;

	// authentication to send to the remote machine during handshake
	private String authIdentifier;
	private String authPassphrase;

	// on the other side of the connection there is a RMIHandler that lies on this
	// same machine and uses the same registry of this one
	private boolean sameRegistryAuthentication = false;

	private ProtocolEndpoint protocolEndpoint;

	private int codebasesModification;

	private String remoteRegistryKey;

	private boolean connectionStarter;

	private Set<RemoteInvocationHandler> connectedStubs = Collections
			.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

	private boolean started = false;

	private TransmitterThread transmitter = new TransmitterThread();

	private ReceiverThread receiver = new ReceiverThread();

	/**
	 * Executes the handshake with the remote {@link RMIHandler}. During the
	 * handshake phase the handlers exchange the registry keys, the registry
	 * listener port and the authentication information. This function acts the
	 * handshake and validate the authentication of the remote handler on the local
	 * registry. It also ensures that authentication was successful on the remote
	 * side.
	 * 
	 * @throws IOException                   if an I/O error occurs
	 * @throws LocalAuthenticationException  if the local {@link RMIHandler} cannot
	 *                                       authenticate the remote one
	 * @throws RemoteAuthenticationException if the remote {@link RMIHandler} cannot
	 *                                       authenticate the local one
	 */
	private void handshake(OutputStream output, InputStream input)
			throws LocalAuthenticationException, RemoteAuthenticationException, IOException {
		DataOutputStream outputStream = new DataOutputStream(output);
		DataInputStream inputStream = new DataInputStream(input);

		// checks that on the other side of the connection there is a handler that uses
		// the same registry of this one
		outputStream.writeUTF(registry.getRegistryKey());
		outputStream.writeInt(registry.getListenerPort());
		outputStream.flush();

		remoteRegistryKey = inputStream.readUTF();
		remotePort = inputStream.readInt();

		if (Debug.RMI_HANDLER)
			System.out.println("[new handler] remoteAddress=" + socket.getInetAddress().getHostAddress()
					+ ", remotePort=" + remotePort);

		if (remotePort != 0)
			inetSocketAddress = new InetSocketAddress(socket.getInetAddress(), remotePort);
		else
			inetSocketAddress = new InetSocketAddress(socket.getInetAddress(), socket.getPort());

		String[] auth = registry.getAuthentication(inetSocketAddress.getAddress(), inetSocketAddress.getPort());

		if (auth != null) {
			authIdentifier = auth[0];
			authPassphrase = auth[1];
		}
		if (authIdentifier == null)
			authIdentifier = "";
		if (authPassphrase == null)
			authPassphrase = "";

		if (remoteRegistryKey.equals(registry.getRegistryKey()) && socket.getInetAddress().getCanonicalHostName()
				.equals(InetAddress.getLocalHost().getCanonicalHostName())) {
			sameRegistryAuthentication = true;
			outputStream.writeBoolean(true);
			outputStream.flush();
		} else {
			sameRegistryAuthentication = false;
			outputStream.writeBoolean(false);
			outputStream.flush();
		}

		if (inputStream.readBoolean()) // the remote handler recognized the registry key
			return;

		// if on the other side there is not the same registry of this handler, do
		// normal authentication
		outputStream.writeUTF(authIdentifier);
		outputStream.writeUTF(authPassphrase);
		outputStream.flush();

		String authPass;
		try {
			remoteAuthIdentifier = inputStream.readUTF();
			authPass = inputStream.readUTF();
		} catch (Exception e) {
			e.printStackTrace();
			try {
				outputStream.writeBoolean(false);
				outputStream.flush();
			} catch (IOException e1) {
			}
			throw new LocalAuthenticationException();
		}

		RMIAuthenticator authenticator = registry.getAuthenticator();
		if (authenticator == null || authenticator.authenticate(inetSocketAddress, remoteAuthIdentifier, authPass)) {
			try {
				outputStream.writeBoolean(true);
				outputStream.flush();
			} catch (IOException e1) {
			}
		} else {
			try {
				outputStream.writeBoolean(false);
				outputStream.flush();
			} catch (IOException e1) {
			}
			throw new LocalAuthenticationException();
		}

		boolean authResult = inputStream.readBoolean();

		if (!authResult)
			throw new RemoteAuthenticationException();
	}

	private void forceInvocationReturn(InvocationMessage invocation) {
		invocation.thrownException = new RemoteException(dispositionException);
		invocation.signalResult();
	}

	/**
	 * Constructs a new RMIHandler over the connection specified by the given
	 * socket, with the specified {@link RMIRegistry}.
	 * 
	 * @param socket                  the socket over which the {@link RMIHandler}
	 *                                will be created
	 * @param registry                the {@link RMIRegistry} to use
	 * @param protocolEndpointFactory a {@link ProtocolEndpointFactory} that allows
	 *                                to add communication levels, such as levels
	 *                                for cryptography or data compression
	 * @see RMIHandler#connect(String, int, RMIRegistry, SocketFactory,
	 *      ProtocolEndpointFactory)
	 * @see RMIHandler#connect(String, int)
	 * @throws IOException if an I/O error occurs
	 */
	RMIHandler(Socket socket, RMIRegistry registry, ProtocolEndpointFactory protocolEndpointFactory,
			boolean connectionStarter) throws IOException {
		this.socket = socket;
		this.registry = registry;
		this.connectionStarter = connectionStarter;

		socket.setSoLinger(true, registry.getLatencyTime());
		OutputStream output = socket.getOutputStream();
		InputStream input = socket.getInputStream();

		if (protocolEndpointFactory != null) {
			protocolEndpoint = protocolEndpointFactory.createEndpoint(output, input);
			output = protocolEndpoint.getOutputStream();
			input = protocolEndpoint.getInputStream();
		}

		handshake(output, input);

		outputStream = new RMIObjectOutputStream(output, this);
		outputStream.flush(); // flushes ObjectOutputStream header
		inputStream = new RMIObjectInputStream(input, this, inetSocketAddress, registry.getClassLoaderFactory());
	}

	void registerStub(RemoteInvocationHandler rih) {
		connectedStubs.add(rih);
	}

	Set<RemoteInvocationHandler> getConnectedStubs() {
		return connectedStubs;
	}

	boolean isConnectionStarter() {
		return connectionStarter;
	}

	/**
	 * A utility function used by library components to decide how to share stubs
	 * with other machines.<br>
	 * <br>
	 * Given three machines A, B and C, and supposing that they are as follows:<br>
	 * - A is the remote machine connected to this handler<br>
	 * - B is the local machine<br>
	 * - C is another machine who should receive from B a stub that refers to a
	 * remote object on A<br>
	 * <br>
	 * this function determines if stubs on B connected to A are really shareable
	 * with C. A stub is shareable by B when it can be serialized and reconstructed
	 * on C maintaining a direct connection to A, the original remote machine. This
	 * condition is possible when A has an active server listener enabled on its
	 * registry. In this case when C receive the stub from B, it creates a new
	 * direct connection to A. If A has no listener currently active, then its
	 * remote listener port results to be <= 0 and the stubs that originated on that
	 * machine must be proxyfied when they are sent from B to C, that is they must
	 * be published on the registry of B and stubbed on C. In this case the stub of
	 * A on B becomes itself a remote object whose stub is sent to C. So this policy
	 * allows to "share" stubs by using this routing mechanism of the remote
	 * invocation from C to A through B (truly, the stub on C is a new totally
	 * different object respect to the stub on B, but this fact is transparent to
	 * the developer).
	 * 
	 * @return true if and only if the stub that refers to the remote machine
	 *         connected to this {@link Handler handler} are shareable and can be
	 *         sent to other machines
	 */
	boolean areStubsShareable() {
		return remotePort > 0;
	}

	synchronized void start() {
		if (started)
			return;

		started = true;
		receiver.start();
		transmitter.start();

		if (registry.getHandlerFaultMaxLife() > 0) {
			// inverse exponential distribution
			double lambda = -(Math.log(0.001) / registry.getHandlerFaultMaxLife());
			long actualLife = (long) -(Math.log(1 - Math.random()) / lambda);
			new FaultTrigger(actualLife);
		}
	}

	/**
	 * Gets the key of the remote RMI registry
	 * 
	 * @return the key of the remote RMI registry
	 */
	String getRemoteRegistryKey() {
		return remoteRegistryKey;
	}

	/**
	 * Gets the registry used by this {@link RMIHandler}
	 * 
	 * @return the registry used by this peer
	 */
	RMIRegistry getRMIRegistry() {
		return registry;
	}

	/**
	 * Package-level operation used by stub invocation handlers to put new
	 * invocations
	 * 
	 * @param invocation the invocation request
	 * @throws InterruptedException
	 */
	synchronized void putMessage(RMIMessage rmiMessage) throws InterruptedException {
		if (disposed) {
			if (rmiMessage instanceof InvocationMessage) {
				forceInvocationReturn((InvocationMessage) rmiMessage);
			}
			if (rmiMessage instanceof RemoteInterfaceMessage) {
				((RemoteInterfaceMessage) rmiMessage).signalResult();
			}
			return;
		}
		if (codebasesModification != registry.getRmiClassLoader().getModificationNumber()) {
			Set<URL> codebases = new HashSet<>(registry.getRmiClassLoader().getCodebasesSet());
			codebasesModification = registry.getRmiClassLoader().getModificationNumber();
			if (!codebases.isEmpty()) {
				CodebaseUpdateMessage cbhandle = new CodebaseUpdateMessage();
				cbhandle.codebases = codebases;
				messageQueue.put(cbhandle);
			}
		}
		messageQueue.put(rmiMessage);
	}

	/**
	 * Dispose this {@link RMIHandler} and frees all the used resources and threads.
	 * A call to this method can cause a callback on the {@link RMIFaultHandler}
	 * instances attached to the {@link RMIRegistry} associated to this instance
	 * sending them the disposition exception of this handler (See
	 * {@link #getDispositionException()}).
	 * 
	 * @param signalFault set to true if this {@link RMIHandler} should send a
	 *                    signal to the {@link RMIFaultHandler} instances attached
	 *                    to the {@link RMIRegistry} that created this instance
	 */
	public void dispose(boolean signalFault) {
		synchronized (this) {
			if (disposed)
				return;

			dispositionTime = System.currentTimeMillis();
			disposed = true;
			receiver.interrupt();
			transmitter.interrupt();

			try {
				socket.close();
			} catch (IOException e) {
			}

			try {
				outputStream.close();
			} catch (IOException e1) {
			}

			try {
				inputStream.close();
			} catch (IOException e1) {
			}

			if (protocolEndpoint != null)
				protocolEndpoint.connectionEnd();

			// let the stubs to return
			for (InvocationMessage handle : invocations.values()) {
				forceInvocationReturn(handle);
			}

			// release all the handle in the handleQueue
			// synchronized (messageQueue) {
			for (RMIMessage message : messageQueue) {
				if (message instanceof InvocationMessage) {
					forceInvocationReturn((InvocationMessage) message);
				}
				if (message instanceof RemoteInterfaceMessage) {
					((RemoteInterfaceMessage) message).signalResult();
				}
			}
			// }

			for (Iterator<String> it = references.iterator(); it.hasNext();) {
				Skeleton sk = registry.getSkeleton(it.next());
				if (sk != null) {
					sk.removeAllRefs(this);
					it.remove();
				}
			}

			socket = null;
			outputStream = null;
			inputStream = null;
			invocations.clear();
			messageQueue.clear();
		}

		registry.removeHandler(this);

		if (signalFault)
			registry.notifyFault(this);

		System.gc();
	}

	/**
	 * Gets the address of the remote process (IP address + TCP port)
	 * 
	 * @return the {@link InetSocketAddress} containing remote host address and port
	 */
	public InetSocketAddress getInetSocketAddress() {
		return inetSocketAddress;
	}

	/**
	 * Check if the given object is a stub created by this handler
	 * 
	 * @param stub the object to check
	 * @return true if the object is a stub created by this handler, false otherwise
	 */
	public boolean isOwnStub(Object stub) {
		if (Proxy.isProxyClass(stub.getClass())) {
			InvocationHandler ih = Proxy.getInvocationHandler(stub);
			if (ih instanceof RemoteInvocationHandler) {
				RemoteInvocationHandler rih = (RemoteInvocationHandler) ih;
				return rih.getHandler() == this;
			}
		}
		return false;
	}

	/**
	 * Removes all the objects that are stubs created by this handler from the
	 * specified iterable object
	 * 
	 * @param objects the iterable object to analyze
	 */
	public void removeOwnStubs(Iterable<?> objects) {
		for (Iterator<?> it = objects.iterator(); it.hasNext();)
			if (isOwnStub(it.next()))
				it.remove();
	}

	/**
	 * Removes all the objects that are not stubs created by this handler from the
	 * specified iterable object
	 * 
	 * @param objects the iterable object to analyze
	 */
	public void retainOwnStubs(Iterable<?> objects) {
		for (Iterator<?> it = objects.iterator(); it.hasNext();)
			if (!isOwnStub(it.next()))
				it.remove();
	}

	/**
	 * Gets the disposed status of this peer
	 * 
	 * @return true if this peer has been disposed, false otherwise
	 */
	public boolean isDisposed() {
		return disposed;
	}

	/**
	 * Gets a stub for the specified object identifier representing a remote object
	 * on the remote machine. This method performs a request to the remote machine
	 * to get the remote interfaces of the remote object, then creates its stub. All
	 * the remote interfaces of the remote object must be visible by the default
	 * class loader and they must be known by the local runtime.
	 * 
	 * @param objectId the object identifier
	 * @return A dynamic proxy object that represents the remote instance. It is an
	 *         instance for the specified stub interface
	 * 
	 * @throws UnknownHostException if the host cannot be resolved
	 * @throws IOException          if an I/O error occurs
	 * @throws InterruptedException if the current thread is iterrupted during
	 *                              operation
	 */
	public Object getStub(String objectId) throws UnknownHostException, IOException, InterruptedException {
		RemoteInterfaceMessage msg = new RemoteInterfaceMessage(objectId);
		putMessage(msg);
		if (!disposed)
			msg.awaitResult();
		return getStub(objectId, msg.interfaces);
	}

	/**
	 * Gets a stub for the specified object identifier respect to the specified
	 * interfaces, representing a remote object on the remote machine. This method
	 * performs no network operation, just creates the stub. All the interfaces
	 * passed must be visible by the class loader of the first interface.
	 * 
	 * @param objectId       the object identifier
	 * @param stubInterfaces the interface whose methods must be stubbed, that is
	 *                       the interface used to access the remote object
	 *                       operations
	 * @return A dynamic proxy object that represents the remote instance. It is an
	 *         instance for the specified stub interface
	 */
	public Object getStub(String objectId, Class<?>... stubInterfaces) {
		if (disposed)
			throw new IllegalStateException("This RMIHandler has been disposed");

		if (stubInterfaces.length == 0)
			throw new IllegalArgumentException("No interface has been passed");

		Object stub;
		stub = Proxy.newProxyInstance(stubInterfaces[0].getClassLoader(), stubInterfaces,
				new RemoteInvocationHandler(this, objectId));
		return stub;
	}

	/**
	 * Gets the time at which this handler has been disposed.
	 * 
	 * @return a long value in milliseconds
	 */
	public long getDispositionTime() {
		return dispositionTime;
	}

	/**
	 * Gets the exception that caused this handler to be disposed.
	 * 
	 * @return an Exception
	 */
	public Exception getDispositionException() {
		return dispositionException;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + remotePort;
		result = prime * result + ((remoteRegistryKey == null) ? 0 : remoteRegistryKey.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		return false;
	}

	/**
	 * This is the thread that manages the output stream of the connection only. It
	 * send new method invocations to the other peer or the invocation results. It
	 * reads new invocations from the handleQueue.
	 */
	private class TransmitterThread extends Thread implements RMIMessageHandler {
		public TransmitterThread() {
			setName(this.getClass().getName());
			setDaemon(true);
		}

		@Override
		public void run() {

			RMIMessage rmiMessage = null;
			try {

				while (!isInterrupted()) {
					rmiMessage = null;
					rmiMessage = messageQueue.take();
					rmiMessage.accept(this);
				}
			} catch (Exception e) { // something gone wrong, destroy the handler
				Exception exception = e;
				if (Debug.RMI_HANDLER) {
					System.out.println("[RMIHandler.transmitter] transmitter thrown the following exception:");
					exception.printStackTrace();
				}

				if (rmiMessage != null)
					messageQueue.add(rmiMessage);

				if (disposed)
					return;

				dispositionException = e;
				dispose(true);
			}
		}

		@Override
		public void handle(ReferenceUseMessage msg) throws Exception {
			outputStream.writeUnshared(msg);
			outputStream.flush();
		}

		@Override
		public void handle(InterruptionMessage msg) throws Exception {
			outputStream.writeUnshared(msg);
			outputStream.flush();
		}

		@Override
		public void handle(CodebaseUpdateMessage msg) throws Exception {
			outputStream.writeUnshared(msg);
			outputStream.flush();
		}

		@Override
		public void handle(InvocationMessage msg) throws Exception {
			try {
				outputStream.writeUnshared(msg);
				outputStream.flush();
				if (!msg.asynch)
					invocations.put(msg.id, msg);
				else {
					msg.signalResult();
				}
			} catch (NotSerializableException e) {
				System.out.println(msg.method);
				msg.thrownException = e;
				synchronized (msg) {
					msg.signalResult();
				}
				throw e;
			}
		}

		@Override
		public void handle(ReturnMessage msg) throws Exception {
			// send invocation response
			try {
				outputStream.writeUnshared(msg);
				outputStream.flush();
			} catch (NotSerializableException e) {
				msg.returnValue = null;
				msg.returnClass = null;
				msg.thrownException = e;
				outputStream.writeUnshared(msg);
				e.printStackTrace();
			}
		}

		@Override
		public void handle(RemoteInterfaceMessage msg) throws Exception {
			if (msg.interfaces == null)
				interfaceRequests.put(msg.handleId, msg);
			outputStream.writeUnshared(msg);
			outputStream.flush();
		}

		@Override
		public void handle(NewReferenceMessage msg) throws Exception {
			outputStream.writeUnshared(msg);
			outputStream.flush();
		}

		@Override
		public void handle(FinalizeMessage msg) throws Exception {
			outputStream.writeUnshared(msg);
			outputStream.flush();
		}
	}

	/**
	 * This is the thread that manages the input stream of the connection only. It
	 * receives new method invocations by the other peer or the invocation results.
	 * In the first case it calls the method of the implementation object. In the
	 * second case it notifies the invocation handlers that are waiting for the
	 * remote method to return.
	 */
	private class ReceiverThread extends Thread implements RMIMessageHandler {
		private Map<Long, InvocationTask> activeInvocations = Collections.synchronizedMap(new HashMap<>());

		public ReceiverThread() {
			setName(this.getClass().getName());
			this.setDaemon(true);
		}

		@Override
		public void run() {
			try {
				while (!isInterrupted()) {
					RMIMessage rmiMessage = null;

					// receive message
					try {
						rmiMessage = (RMIMessage) (inputStream.readUnshared());
					} catch (Exception e) {
						if (Debug.RMI_HANDLER) {
							e.printStackTrace();
						}
						ReturnMessage retHandle = new ReturnMessage();
						retHandle.thrownException = e;
						putMessage(retHandle);
						Thread.sleep(registry.getLatencyTime());
						throw e; // connection is broken
					}

					// interpret message
					rmiMessage.accept(this);
				}
			} catch (Exception e) { // something gone wrong, dispose this handler
				if (Debug.RMI_HANDLER) {
					e.printStackTrace();
				}

				if (disposed)
					return;

				dispositionException = e;
				dispose(true);
			}
		}

		@Override
		public void handle(ReferenceUseMessage msg) throws Exception {
			String objectId = msg.objectId;
			Skeleton skeleton = registry.getSkeleton(objectId);
			if (skeleton != null)
				skeleton.updateLastUseTime();
		}

		@Override
		public void handle(InterruptionMessage msg) throws Exception {
			long invocationId = msg.invocationId;
			synchronized (activeInvocations) {
				if (activeInvocations.containsKey(invocationId))
					activeInvocations.get(invocationId).interrupt();
			}
		}

		@Override
		public void handle(CodebaseUpdateMessage msg) throws Exception {
			if (registry.isCodeDownloadingEnabled()) {
				inputStream.setRemoteCodebases(msg.codebases);
			}
		}

		@Override
		public void handle(InvocationMessage msg) throws Exception {

			// get the skeleton
			Skeleton skeleton = registry.getSkeleton(msg.objectId);

			if (skeleton == null) {
				// object not found
				ReturnMessage retHandle = new ReturnMessage();
				retHandle.invocationId = msg.id;
				retHandle.thrownException = new ObjectNotFoundException(msg.objectId);
				putMessage(retHandle);
				return;
			}

			// retrieve the object
			Object object = skeleton.getRemoteObject();

			// get the correct method
			Method method;
			try {
				method = object.getClass().getMethod(msg.method, msg.parameterTypes);
			} catch (Exception e) {
				ReturnMessage retHandle = new ReturnMessage();
				retHandle.invocationId = msg.id;
				retHandle.thrownException = new RemoteException(e);
				putMessage(retHandle);
				return;
			}

			// get authorization
			boolean authorized = sameRegistryAuthentication || registry.getAuthenticator() == null
					|| registry.getAuthenticator().authorize(remoteAuthIdentifier, object, method);

			// if authorized, starts the invocation thread
			if (authorized) {
				registry.invocationExecutor.submit(new InvocationTask(msg, skeleton, method));
			} else {
				// not authorized: send an authorization exception
				ReturnMessage response = new ReturnMessage();
				response.invocationId = msg.id;
				response.thrownException = new AuthorizationException();
				messageQueue.put(response);
			}
		}

		private class InvocationTask implements Runnable {
			InvocationMessage msg;
			Skeleton skeleton;
			Method method;
			Thread currentThread;
			boolean interrupted;

			public InvocationTask(InvocationMessage msg, Skeleton skeleton, Method method) {
				this.msg = msg;
				this.skeleton = skeleton;
				this.method = method;
				activeInvocations.put(msg.id, this);

			}

			public void interrupt() {
				synchronized (this) {
					interrupted = true;
					if (currentThread != null) {
						currentThread.interrupt();
						currentThread = null;
					}
				}
			}

			@Override
			public void run() {
				ReturnMessage response = new ReturnMessage();
				response.invocationId = msg.id;
				try {

					synchronized (this) {
						this.currentThread = Thread.currentThread();
						if (interrupted)
							currentThread.interrupt();
					}

					response.returnValue = skeleton.invoke(msg.method, msg.parameterTypes, msg.parameters,
							remoteRegistryKey, msg.id);

					// set invocation return class
					response.returnClass = method.getReturnType();

				} catch (InvocationTargetException e) {
					// e.printStackTrace();
					response.thrownException = e.getCause();
				} catch (NoSuchMethodException e) {
					// e.printStackTrace();
					response.thrownException = new NoSuchMethodException(
							"The method '" + msg.method + "(" + Arrays.toString(msg.parameterTypes)
									+ ")' does not exists for the object with identifier '" + msg.objectId + "'.");
				} catch (SecurityException e) {
					// e.printStackTrace();
					response.thrownException = e;
				} catch (IllegalAccessException e) {
					// e.printStackTrace();
				} catch (IllegalArgumentException e) {
					// e.printStackTrace();
					response.thrownException = e;
				} catch (NullPointerException e) {
					// e.printStackTrace();
					response.thrownException = e;
				}

				activeInvocations.remove(msg.id);

				// send invocation response after method execution, if invocation is not
				// asynchronous (the method is not annotated with @RMIAsynch)
				if (!msg.asynch)
					while (true)
						try {
							messageQueue.put(response);
							return;
						} catch (InterruptedException e) {
						}
				else if (response.thrownException != null) {
					System.err.println("RMI asynchronous method '" + method
							+ "' (annotated with @RMIAsynch) thrown the following exception.");
					response.thrownException.printStackTrace();
				}
			}
		}

		@Override
		public void handle(ReturnMessage msg) throws Exception {

			// remove the waiting invocation
			InvocationMessage invocation = invocations.remove(msg.invocationId);

			if (invocation != null) {

				// set return
				invocation.returnClass = msg.returnClass;
				invocation.returnValue = msg.returnValue;
				invocation.thrownException = msg.thrownException;

				// notify the invocation handler that is waiting on it
				invocation.signalResult();
			} else if (msg.thrownException != null) {
				throw (Exception) msg.thrownException;
			}
		}

		@Override
		public void handle(RemoteInterfaceMessage msg) throws Exception {
			if (msg.interfaces == null) {
				List<Class<?>> remotes = registry.getRemoteInterfaces(msg.objectId);
				msg.interfaces = new Class<?>[remotes.size()];
				msg.interfaces = remotes.toArray(msg.interfaces);
				putMessage(msg);
			} else {
				RemoteInterfaceMessage req = interfaceRequests.get(msg.handleId);
				req.interfaces = msg.interfaces;
				req.signalResult();
			}
		}

		@Override
		public void handle(NewReferenceMessage msg) throws Exception {
			if (msg.objectId != null) {
				Skeleton sk = registry.getSkeleton(msg.objectId);
				sk.addRef(RMIHandler.this);
				references.add(sk.getId());
			}
		}

		@Override
		public void handle(FinalizeMessage msg) throws Exception {
			Skeleton sk = registry.getSkeleton(msg.objectId);
			if (sk != null)
				sk.removeRef(RMIHandler.this);
		}
	}

	private class FaultTrigger extends Thread {
		private long timeout;

		public FaultTrigger(long timeout) {
			this.timeout = timeout;
			setName(this.getClass().getName());
			setDaemon(true);
			start();
		}

		@Override
		public void run() {
			try {
				Thread.sleep(timeout);
				socket.close();
				System.out.println("[" + this.getClass().getName() + "] fault triggered");
			} catch (InterruptedException e) {
			} catch (IOException e) {
			}
		}
	}

}
