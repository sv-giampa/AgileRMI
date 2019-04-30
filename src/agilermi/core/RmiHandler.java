/**
 *  Copyright 2018-2019 Salvatore Giamp�
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import javax.net.SocketFactory;

import agilermi.communication.ProtocolEndpoint;
import agilermi.communication.ProtocolEndpointFactory;
import agilermi.exception.LocalAuthenticationException;
import agilermi.exception.RemoteAuthenticationException;
import agilermi.exception.RemoteException;

/**
 * This class defines a RMI connection handler. The instances of this class
 * manages all the RMI communication protocol between the local machine and a
 * remote machine. This class can be instantiated through the RmiRegistry only.
 * See the {@link RmiRegistry#getRmiHandler(String, int, boolean)} method
 * 
 * @author Salvatore Giampa'
 *
 */
public final class RmiHandler {

	// connection details, socket and streams
	private InetSocketAddress inetSocketAddress;
	private int remotePort;
	private Socket socket;
	private RmiObjectOutputStream outputStream;
	private RmiObjectInputStream inputStream;

	// registry associated to this handler
	private RmiRegistry rmiRegistry;

	// Map for invocations that are waiting a response
	private Map<Long, InvocationMessage> invocations = Collections.synchronizedMap(new HashMap<>());

	// Map for interface requests that are waiting a response
	private Map<Long, RemoteInterfaceMessage> interfaceRequests = Collections.synchronizedMap(new HashMap<>());

	// Map for codebases update requests that are waiting a response
	private Map<Long, CodebaseUpdateMessage> codebasesUpdateRequests = Collections.synchronizedMap(new HashMap<>());

	// The queue for buffered invocations that are ready to be sent over the socket
	private BlockingQueue<RmiMessage> messageQueue = new ArrayBlockingQueue<>(200);

	// Flag that indicates if this RmiHandler has been disposed.
	private boolean disposed = false;

	// remote references requested by the other machine
	private Set<String> references = new TreeSet<>();

	// the authentication identifier received by the remote machine
	private String remoteAuthIdentifier;

	// authentication to send to the remote machine
	private String authIdentifier;
	private String authPassphrase;

	// on the other side of the connection there is a RmiHandler that lies on this
	// same machine and uses the same registry of this one
	private boolean sameRegistryAuthentication = false;

	private ProtocolEndpoint protocolEndpoint;

	private int codebasesModification;

	private String remoteRegistryKey;

	/**
	 * Gets the address of the remote process (IP address + TCP port)
	 * 
	 * @return the {@link InetSocketAddress} containing remote host address and port
	 */
	public InetSocketAddress getInetSocketAddress() {
		return inetSocketAddress;
	}

	/**
	 * Dispose this {@link RmiHandler} and frees all the used resources and threads.
	 * A call to this method cause a callback on the failure observers attached to
	 * the {@link RmiRegistry} associated to this instance sending them an instance
	 * of {@link RemoteException}
	 * 
	 * @param signalFailure set to true if you want this {@link RmiHandler) to send
	 *                      a signal to the failure observers attached to the
	 *                      {@link RmiRegistry} that created this instance
	 */
	public synchronized void dispose(boolean signalFailure) {
		if (disposed)
			return;

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
		for (RmiMessage rmiMessage : messageQueue) {
			if (rmiMessage instanceof InvocationMessage) {
				forceInvocationReturn((InvocationMessage) rmiMessage);
			}
		}

		for (Iterator<String> it = references.iterator(); it.hasNext();) {
			Skeleton sk = rmiRegistry.getSkeleton(it.next());
			if (sk != null) {
				sk.removeAllRefs(this);
				it.remove();
			}
		}

		if (signalFailure) {
			RemoteException dispositionException = new RemoteException();
			rmiRegistry.sendRmiHandlerFailure(this, dispositionException);
		}

		try {
			rmiRegistry.failureObserver.failure(this, null);
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

	boolean areStubsShareable() {
		return remotePort != 0;
	}

	/**
	 * Executes the authentication negotiation
	 * 
	 * @throws IOException                   if an I/O error occurs
	 * @throws LocalAuthenticationException  if the local {@link RmiHandler} cannot
	 *                                       authenticate the remote one
	 * @throws RemoteAuthenticationException if the remote {@link RmiHandler} cannot
	 *                                       authenticate the local one
	 */
	private void handshake(OutputStream output, InputStream input)
			throws LocalAuthenticationException, RemoteAuthenticationException, IOException {
		DataOutputStream outputStream = new DataOutputStream(output);
		DataInputStream inputStream = new DataInputStream(input);

		// checks that on the other side of the connection there is a handler that uses
		// the same registry of this one
		outputStream.writeUTF(rmiRegistry.registryKey);
		outputStream.flush();
		remoteRegistryKey = inputStream.readUTF();

		outputStream.writeInt(rmiRegistry.getListenerPort());
		remotePort = inputStream.readInt();

		if (remotePort != 0)
			inetSocketAddress = new InetSocketAddress(socket.getInetAddress(), remotePort);
		else
			inetSocketAddress = new InetSocketAddress(socket.getInetAddress(), socket.getPort());

		if (remoteRegistryKey.equals(rmiRegistry.registryKey)) {
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

		if (rmiRegistry.getAuthenticator() == null
				|| rmiRegistry.getAuthenticator().authenticate(inetSocketAddress, remoteAuthIdentifier, authPass)) {
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

	/**
	 * Constructs a new RmiHandler over the connection specified by the given
	 * socket, with the specified {@link RmiRegistry}.
	 * 
	 * @param socket      the socket over which the {@link RmiHandler} will be
	 *                    created
	 * @param rmiRegistry the {@link RmiRegistry} to use
	 * @see RmiHandler#connect(String, int, RmiRegistry, SocketFactory,
	 *      ProtocolEndpointFactory)
	 * @see RmiHandler#connect(String, int)
	 * @throws IOException if an I/O error occurs
	 */
	RmiHandler(Socket socket, RmiRegistry registry) throws IOException {
		this(socket, registry, null);
	}

	/**
	 * Constructs a new RmiHandler over the connection specified by the given
	 * socket, with the specified {@link RmiRegistry}.
	 * 
	 * @param socket                  the socket over which the {@link RmiHandler}
	 *                                will be created
	 * @param rmiRegistry             the {@link RmiRegistry} to use
	 * @param protocolEndpointFactory a {@link ProtocolEndpointFactory} that allows
	 *                                to add communication levels, such as levels
	 *                                for cryptography or data compression
	 * @see RmiHandler#connect(String, int, RmiRegistry, SocketFactory,
	 *      ProtocolEndpointFactory)
	 * @see RmiHandler#connect(String, int)
	 * @throws IOException if an I/O error occurs
	 */
	RmiHandler(Socket socket, RmiRegistry rmiRegistry, ProtocolEndpointFactory protocolEndpointFactory)
			throws IOException {
		this.socket = socket;
		this.rmiRegistry = rmiRegistry;

		String[] auth = rmiRegistry.getAuthentication(socket.getInetAddress(), socket.getPort());

		if (auth != null) {
			authIdentifier = auth[0];
			authPassphrase = auth[1];
		}
		if (authIdentifier == null)
			authIdentifier = "";
		if (authPassphrase == null)
			authPassphrase = "";

		OutputStream output = socket.getOutputStream();
		InputStream input = socket.getInputStream();

		if (protocolEndpointFactory != null) {
			protocolEndpoint = protocolEndpointFactory.createEndpoint(output, input);
			output = protocolEndpoint.getOutputStream();
			input = protocolEndpoint.getInputStream();
		}

		// send and receive authentication
		handshake(output, input);

		outputStream = new RmiObjectOutputStream(output, rmiRegistry);
		outputStream.flush();
		inputStream = new RmiObjectInputStream(input, rmiRegistry, inetSocketAddress,
				rmiRegistry.getClassLoaderFactory());

		receiver.setDaemon(true);
		transmitter.setDaemon(true);
		receiver.start();
		transmitter.start();
	}

	/**
	 * Gets a stub for the specified object identifier representing a remote object
	 * on the remote machine. This method performs a request to the remote machine
	 * to get the remote interfaces of the remote object, then creates its stub. All
	 * the remote interfaces of the remote object must be visible by the default
	 * class loader and they must be known by the local runtime.
	 * 
	 * @param objectId the object identifier
	 * @param <T>      the stub interface type
	 * @return A dynamic proxy object that represents the remote instance. It is an
	 *         instance for the specified stub interface
	 */
	public Object getStub(String objectId) throws UnknownHostException, IOException, InterruptedException {

		RemoteInterfaceMessage msg = new RemoteInterfaceMessage(objectId);
		putHandle(msg);
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
	 * @param <T>            the stub interface type
	 * @return A dynamic proxy object that represents the remote instance. It is an
	 *         instance for the specified stub interface
	 */
	public Object getStub(String objectId, Class<?>... stubInterfaces) {
		if (disposed)
			throw new IllegalStateException("This RmiHandler has been disposed");
		if (stubInterfaces.length == 0)
			throw new IllegalArgumentException("No interface has been passed");

		Object stub;
		stub = Proxy.newProxyInstance(stubInterfaces[0].getClassLoader(), stubInterfaces,
				new RemoteInvocationHandler(objectId, this));
		return stub;
	}

	String getRemoteRegistryKey() {
		return remoteRegistryKey;
	}

	/**
	 * Gets the rmiRegistry used by this {@link RmiHandler}
	 * 
	 * @return the rmiRegistry used by this peer
	 */
	RmiRegistry getRmiRegistry() {
		return rmiRegistry;
	}

	private void forceInvocationReturn(InvocationMessage invocation) {
		if (rmiRegistry.isRemoteExceptionEnabled())
			invocation.thrownException = new RemoteException();
		invocation.returned = true;
		invocation.signalResult();
	}

	/**
	 * Package-level operation used by stub invocation handlers to put new
	 * invocations
	 * 
	 * @param invocation the invocation request
	 * @throws InterruptedException
	 */
	void putHandle(RmiMessage rmiMessage) throws InterruptedException {
		if (messageQueue != null) {
			if (codebasesModification != rmiRegistry.getRmiClassLoader().getModificationNumber()) {
				Set<URL> codebases = new HashSet<>(rmiRegistry.getRmiClassLoader().getCodebases());
				codebasesModification = rmiRegistry.getRmiClassLoader().getModificationNumber();
				if (!codebases.isEmpty()) {
					CodebaseUpdateMessage cbhandle = new CodebaseUpdateMessage();
					cbhandle.codebases = codebases;
					messageQueue.put(cbhandle);
				}
			}
			messageQueue.put(rmiMessage);
		}
	}

	/**
	 * This is the thread that manages the output stream of the connection only. It
	 * send new method invocations to the other peer or the invocation results. It
	 * reads new invocations from the handleQueue.
	 */
	private Thread transmitter = new Thread() {
		@Override
		public void run() {

			RmiMessage rmiMessage = null;
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
							invocation.thrownException = e;
							synchronized (invocation) {
								invocation.notifyAll();
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

				if (RmiRegistry.DEBUG) {
					System.out.println("[RmiHandler.transmitter] transmitter thrown the following exception:");
					e.printStackTrace();
				}

				if (rmiMessage != null) {
					if (rmiMessage instanceof InvocationMessage) {
						// release invocation
						InvocationMessage invocation = (InvocationMessage) rmiMessage;
						invocation.thrownException = e;
						invocation.signalResult();
					}
				}

				if (disposed)
					return;

				dispose(true);

				try {
					socket.close();
				} catch (Exception e1) {
				}

				rmiRegistry.sendRmiHandlerFailure(RmiHandler.this, e);
			}
		}
	};

	/**
	 * This is the thread that manages the input stream of the connection only. It
	 * receives new method invocations by the other peer or the invocation results.
	 * In the first case it calls the method of the implementation object. In the
	 * second case it notifies the invocation handlers that are waiting for the
	 * remote method to return.
	 */
	private Thread receiver = new Thread() {
		@Override
		public void run() {
			try {
				while (!isInterrupted()) {
					RmiMessage rmiMessage = null;

					// receive message
					try {
						rmiMessage = (RmiMessage) (inputStream.readUnshared());
					} catch (Exception e) {
						if (RmiRegistry.DEBUG) {
							System.out.println("[RmiHandler.receiver] Exception while receiving RMI message: " + e);
							e.printStackTrace();
						}
						ReturnMessage retHandle = new ReturnMessage();
						retHandle.thrownException = e;
						putHandle(retHandle);
						Thread.sleep(rmiRegistry.getDgcLeaseValue());
						throw e; // connection is broken
					}

					// interpret message
					if (rmiMessage instanceof InvocationMessage) {
						InvocationMessage invocation = (InvocationMessage) rmiMessage;

						// get the skeleton
						Skeleton skeleton = rmiRegistry.getSkeleton(invocation.objectId);

						// retrieve the object
						Object object = skeleton.getObject();

						// get the correct method
						Method method = object.getClass().getMethod(invocation.method, invocation.parameterTypes);

						// get authorization
						boolean authorized = sameRegistryAuthentication || rmiRegistry.getAuthenticator() == null
								|| rmiRegistry.getAuthenticator().authorize(remoteAuthIdentifier, object, method);

						// if authorized, starts the delegation thread
						if (authorized) {
							new Thread(() -> {
								ReturnMessage retHandle = new ReturnMessage();
								retHandle.invocationId = invocation.id;
								try {

									retHandle.returnValue = skeleton.invoke(invocation.method,
											invocation.parameterTypes, invocation.parameters);

									// set invocation return class
									retHandle.returnClass = method.getReturnType();

								} catch (InvocationTargetException e) {
									// e.printStackTrace();
									retHandle.thrownException = e.getCause();
								} catch (NoSuchMethodException e) {
									e.printStackTrace();
									retHandle.thrownException = new NoSuchMethodException("The method '"
											+ invocation.method + "(" + Arrays.toString(invocation.parameterTypes)
											+ ")' does not exists for the object with identifier '"
											+ invocation.objectId + "'.");
								} catch (SecurityException e) {
									e.printStackTrace();
									retHandle.thrownException = e;
								} catch (IllegalAccessException e) {
									e.printStackTrace();
								} catch (IllegalArgumentException e) {
									e.printStackTrace();
									retHandle.thrownException = e;
								} catch (NullPointerException e) {
									e.printStackTrace();
									retHandle.thrownException = new NullPointerException("The object identifier '"
											+ invocation.objectId + "' of the stub is not bound to a remote object");
								}

								// send invocation response after method execution
								try {
									messageQueue.put(retHandle);
								} catch (InterruptedException e) {
								}

							}).start();
						} else {
							// not authorized: send an authorization exception
							ReturnMessage retHandle = new ReturnMessage();
							retHandle.invocationId = invocation.id;
							retHandle.thrownException = new RuntimeException("authentication exception");
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
							invocation.returned = true;

							// notify the invocation handler that is waiting on it
							invocation.signalResult();
						} else if (ret.thrownException != null && ret.thrownException instanceof Exception) {
							throw (Exception) ret.thrownException;
						}

					} else if (rmiMessage instanceof FinalizeMessage) {
						FinalizeMessage finHandle = (FinalizeMessage) rmiMessage;
						Skeleton sk = rmiRegistry.getSkeleton(finHandle.objectId);
						if (sk != null)
							sk.removeRef(RmiHandler.this);

					} else if (rmiMessage instanceof NewReferenceMessage) {
						NewReferenceMessage newReferenceMessage = (NewReferenceMessage) rmiMessage;
						if (newReferenceMessage.objectId != null) {
							Skeleton sk = rmiRegistry.getSkeleton(newReferenceMessage.objectId);
							sk.addRef(RmiHandler.this);
							references.add(sk.getId());
						}

					} else if (rmiMessage instanceof RemoteInterfaceMessage) {
						RemoteInterfaceMessage rih = (RemoteInterfaceMessage) rmiMessage;
						if (rih.interfaces == null) {
							List<Class<?>> remotes = rmiRegistry.getRemoteInterfaces(rih.objectId);
							rih.interfaces = new Class<?>[remotes.size()];
							rih.interfaces = remotes.toArray(rih.interfaces);
							putHandle(rih);
						} else {
							RemoteInterfaceMessage req = interfaceRequests.get(rih.handleId);
							req.interfaces = rih.interfaces;
							req.signalResult();
						}

					} else if (rmiMessage instanceof CodebaseUpdateMessage) {
						if (rmiRegistry.isCodeMobilityEnabled()) {
							CodebaseUpdateMessage codebaseUpdateMessage = (CodebaseUpdateMessage) rmiMessage;
							inputStream.setRemoteCodebases(codebaseUpdateMessage.codebases);
						}

					} else {
						throw new RuntimeException("AgileRMI INTERNAL ERROR");
					}

				}
			} catch (Exception e) { // something gone wrong, destroy this handler and dispose it
				if (RmiRegistry.DEBUG) {
					System.out.println("[RmiHandler.receiver] receiver thrown the following exception:");
					e.printStackTrace();
				}

				if (disposed)
					return;

				dispose(true);

				try {
					socket.close();
				} catch (Exception e1) {
				}

				rmiRegistry.sendRmiHandlerFailure(RmiHandler.this, e);
			}
		}
	};

}
