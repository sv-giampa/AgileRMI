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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import javax.net.SocketFactory;

import agilermi.communication.ProtocolEndpoint;
import agilermi.communication.ProtocolEndpointFactory;
import agilermi.configuration.RMIFaultHandler;
import agilermi.exception.AuthorizationException;
import agilermi.exception.LocalAuthenticationException;
import agilermi.exception.RemoteAuthenticationException;
import agilermi.exception.RemoteException;

/**
 * This class defines a RMI connection handler. The instances of this class
 * manages all the RMI communication protocol between the local machine and a
 * remote machine. This class can be instantiated through the RMIRegistry only.
 * See the {@link RMIRegistry#getRMIHandler(String, int, boolean)} method
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
	private BlockingQueue<RMIMessage> messageQueue = new ArrayBlockingQueue<>(200, true);

	// Flag that indicates if this RMIHandler has been disposed.
	private boolean disposed = false;

	private Exception dispositionException = null;

	private long dispositionTime = 0;

	// remote references requested by the other machine
	private Set<String> references = new TreeSet<>();

	// the authentication identifier received by the remote machine
	private String remoteAuthIdentifier;

	// authentication to send to the remote machine
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
	 * handshake pahase the handlers exchange the registry keys, the registry
	 * listener port and the authentication information. This function act the
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

		if (registry.getAuthenticator() == null
				|| registry.getAuthenticator().authenticate(inetSocketAddress, remoteAuthIdentifier, authPass)) {
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
		if (registry.isRemoteExceptionEnabled()) {
			invocation.success = false;
			invocation.thrownException = new RemoteException();
		}
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

		socket.setSoLinger(true, registry.getLeaseValue());
		OutputStream output = socket.getOutputStream();
		InputStream input = socket.getInputStream();

		if (protocolEndpointFactory != null) {
			protocolEndpoint = protocolEndpointFactory.createEndpoint(output, input);
			output = protocolEndpoint.getOutputStream();
			input = protocolEndpoint.getInputStream();
		}

		// send and receive authentication
		handshake(output, input);

		outputStream = new RMIObjectOutputStream(output, registry);
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
	 * @return
	 */
	boolean areStubsShareable() {
		return remotePort > 0;
	}

	synchronized void start() {
		if (started)
			return;
		started = true;
		receiver.setDaemon(true);
		transmitter.setDaemon(true);
		receiver.start();
		transmitter.start();
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
			Set<URL> codebases = new HashSet<>(registry.getRmiClassLoader().getCodebases());
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
	 * @param tryRepair   set to true to try to create a new {@link RMIHandler} used
	 *                    as a replacement of this one. This will repair the RMI
	 *                    connections of the stubs.
	 * @param signalFault set to true if this {@link RMIHandler} should send a
	 *                    signal to the {@link RMIFaultHandler} instances attached
	 *                    to the {@link RMIRegistry} that created this instance
	 */
	synchronized void dispose(boolean tryRepair, boolean signalFault) {
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

		try {
			registry.fault(this, tryRepair, signalFault);
		} catch (Throwable e) {
			e.printStackTrace();
		}

		socket = null;
		outputStream = null;
		inputStream = null;
		invocations.clear();
		messageQueue.clear();
		System.gc();
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
		dispose(false, signalFault);
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
				new RemoteInvocationHandler(objectId, this));
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
	private class TransmitterThread extends Thread {
		public TransmitterThread() {
			setName("RMIHandler.transmitter");
		}

		@Override
		public void run() {

			RMIMessage rmiMessage = null;
			try {

				while (!isInterrupted()) {
					rmiMessage = null;
					rmiMessage = messageQueue.take();

					if (rmiMessage instanceof InvocationMessage) {
						InvocationMessage invocation = (InvocationMessage) rmiMessage;
						try {
							outputStream.writeUnshared(rmiMessage);
							invocations.put(invocation.id, invocation);
						} catch (NotSerializableException e) {
							System.out.println(invocation.method);
							invocation.thrownException = e;
							synchronized (invocation) {
								invocation.signalResult();
							}
							throw e;
						}

					} else if (rmiMessage instanceof ReturnMessage) {
						// send invocation response
						ReturnMessage retm = (ReturnMessage) rmiMessage;
						try {
							outputStream.writeUnshared(retm);
						} catch (NotSerializableException e) {
							retm.returnValue = null;
							retm.returnClass = null;
							retm.thrownException = e;
							outputStream.writeUnshared(retm);
							e.printStackTrace();
						}

					} else if (rmiMessage instanceof RemoteInterfaceMessage) {
						RemoteInterfaceMessage rim = (RemoteInterfaceMessage) rmiMessage;
						if (rim.interfaces == null) {
							interfaceRequests.put(rim.handleId, rim);
						}
						outputStream.writeUnshared(rim);

					} else {
						outputStream.writeUnshared(rmiMessage);
					}

					outputStream.flush();
				}
			} catch (IOException | InterruptedException e) { // something gone wrong, destroy the handler
				Exception exception = e;
				if (Debug.RMI_HANDLER) {
					System.out.println("[RMIHandler.transmitter] transmitter thrown the following exception:");
					exception.printStackTrace();
				}
				// exception.printStackTrace();

				if (rmiMessage != null) {
					if (rmiMessage instanceof InvocationMessage) {
						// release invocation
						InvocationMessage invocation = (InvocationMessage) rmiMessage;
						invocation.thrownException = new RemoteException(exception);
						invocation.success = false;
						invocation.signalResult();
					}
				}

				if (disposed)
					return;

				dispositionException = e;
				dispose(true, true);
			}
		}
	}

	/**
	 * This is the thread that manages the input stream of the connection only. It
	 * receives new method invocations by the other peer or the invocation results.
	 * In the first case it calls the method of the implementation object. In the
	 * second case it notifies the invocation handlers that are waiting for the
	 * remote method to return.
	 */
	private class ReceiverThread extends Thread {
		private Map<Long, Thread> activeInvocations = Collections.synchronizedMap(new HashMap<>());

		public ReceiverThread() {
			setName("RMIHandler.receiver");
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
							System.out.println("[RMIHandler.receiver] Exception while receiving RMI message: " + e);
							e.printStackTrace();
						}
						ReturnMessage retHandle = new ReturnMessage();
						retHandle.thrownException = e;
						putMessage(retHandle);
						Thread.sleep(registry.getLeaseValue());
						throw e; // connection is broken
					}

					// interpret message
					if (rmiMessage instanceof InvocationMessage) {
						InvocationMessage invocation = (InvocationMessage) rmiMessage;

						// get the skeleton
						Skeleton skeleton = registry.getSkeleton(invocation.objectId);

						// retrieve the object
						Object object = skeleton.getObject();

						// get the correct method
						Method method = object.getClass().getMethod(invocation.method, invocation.parameterTypes);

						// get authorization
						boolean authorized = sameRegistryAuthentication || registry.getAuthenticator() == null
								|| registry.getAuthenticator().authorize(remoteAuthIdentifier, object, method);

						// if authorized, starts the delegation thread
						if (authorized) {
							Thread delegated = new Thread(() -> {
								ReturnMessage retMessage = new ReturnMessage();
								retMessage.invocationId = invocation.id;
								try {

									retMessage.returnValue = skeleton.invoke(invocation.method,
											invocation.parameterTypes, invocation.parameters);

									// set invocation return class
									retMessage.returnClass = method.getReturnType();

								} catch (InvocationTargetException e) {
									// e.printStackTrace();
									retMessage.thrownException = e.getCause();
								} catch (NoSuchMethodException e) {
									// e.printStackTrace();
									retMessage.thrownException = new NoSuchMethodException("The method '"
											+ invocation.method + "(" + Arrays.toString(invocation.parameterTypes)
											+ ")' does not exists for the object with identifier '"
											+ invocation.objectId + "'.");
								} catch (SecurityException e) {
									// e.printStackTrace();
									retMessage.thrownException = e;
								} catch (IllegalAccessException e) {
									// e.printStackTrace();
								} catch (IllegalArgumentException e) {
									// e.printStackTrace();
									retMessage.thrownException = e;
								} catch (NullPointerException e) {
									// e.printStackTrace();
									retMessage.thrownException = new NullPointerException("The object identifier '"
											+ invocation.objectId + "' of the stub is not bound to a remote object");
								}

								activeInvocations.remove(invocation.id);

								// send invocation response after method execution
								while (true)
									try {
										messageQueue.put(retMessage);
										return;
									} catch (InterruptedException e) {
									}

							});
							delegated.setName("RMIHandler.ReceiverThread.delegated");
							activeInvocations.put(invocation.id, delegated);
							delegated.start();
						} else {
							// not authorized: send an authorization exception
							ReturnMessage retHandle = new ReturnMessage();
							retHandle.invocationId = invocation.id;
							retHandle.thrownException = new AuthorizationException();
						}

					} else if (rmiMessage instanceof ReturnMessage) {
						ReturnMessage ret = (ReturnMessage) rmiMessage;

						// remove the waiting invocation
						InvocationMessage invocation = invocations.remove(ret.invocationId);

						if (invocation != null) {

							// set return
							invocation.returnClass = ret.returnClass;
							invocation.returnValue = ret.returnValue;
							invocation.thrownException = ret.thrownException;
							invocation.success = true;

							// notify the invocation handler that is waiting on it
							invocation.signalResult();
						} else if (ret.thrownException != null && ret.thrownException instanceof Exception) {
							throw (Exception) ret.thrownException;
						}

					} else if (rmiMessage instanceof FinalizeMessage) {
						FinalizeMessage finHandle = (FinalizeMessage) rmiMessage;
						Skeleton sk = registry.getSkeleton(finHandle.objectId);
						if (sk != null)
							sk.removeRef(RMIHandler.this);

					} else if (rmiMessage instanceof NewReferenceMessage) {
						NewReferenceMessage newReferenceMessage = (NewReferenceMessage) rmiMessage;
						if (newReferenceMessage.objectId != null) {
							Skeleton sk = registry.getSkeleton(newReferenceMessage.objectId);
							sk.addRef(RMIHandler.this);
							references.add(sk.getId());
						}

					} else if (rmiMessage instanceof RemoteInterfaceMessage) {
						RemoteInterfaceMessage rih = (RemoteInterfaceMessage) rmiMessage;
						if (rih.interfaces == null) {
							List<Class<?>> remotes = registry.getRemoteInterfaces(rih.objectId);
							rih.interfaces = new Class<?>[remotes.size()];
							rih.interfaces = remotes.toArray(rih.interfaces);
							putMessage(rih);
						} else {
							RemoteInterfaceMessage req = interfaceRequests.get(rih.handleId);
							req.interfaces = rih.interfaces;
							req.signalResult();
						}

					} else if (rmiMessage instanceof CodebaseUpdateMessage) {
						if (registry.isCodeMobilityEnabled()) {
							CodebaseUpdateMessage codebaseUpdateMessage = (CodebaseUpdateMessage) rmiMessage;
							inputStream.setRemoteCodebases(codebaseUpdateMessage.codebases);
						}

					} else if (rmiMessage instanceof InterruptionMessage) {
						long invocationId = ((InterruptionMessage) rmiMessage).invocationId;
						synchronized (activeInvocations) {
							if (activeInvocations.containsKey(invocationId))
								activeInvocations.get(invocationId).interrupt();
						}
					} else {
						throw new RuntimeException("INTERNAL ERROR: Unknown RMI message type");
					}

				}
			} catch (Exception e) { // something gone wrong, destroy this handler and dispose it
				if (Debug.RMI_HANDLER) {
					System.out.println("[RMIHandler.receiver] receiver thrown the following exception:");
					e.printStackTrace();
				}
				// e.printStackTrace();

				if (disposed)
					return;

				dispositionException = e;
				dispose(true, true);
			}
		}
	}

}
