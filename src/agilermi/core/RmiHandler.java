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
import java.io.InputStream;
import java.io.NotSerializableException;
import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;

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
public class RmiHandler {

	// connection details, socket and streams
	private InetSocketAddress inetSocketAddress;
	private Socket socket;
	private RmiObjectOutputStream out;
	private RmiObjectInputStream in;

	// registry associated to this handler
	private RmiRegistry rmiRegistry;

	// Map for invocations that are waiting a response
	private Map<Long, InvocationHandle> invocations = Collections.synchronizedMap(new HashMap<>());

	private Map<Long, RemoteInterfaceHandle> interfaceRequests = Collections.synchronizedMap(new HashMap<>());

	// The queue for buffered invocations that are ready to be sent over the socket
	private BlockingQueue<Handle> handleQueue = new ArrayBlockingQueue<>(200);

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
		sender.interrupt();

		try {
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		// let the stubs to return
		for (InvocationHandle handle : invocations.values()) {
			forceInvocationReturn(handle);
		}

		// release all the handle in the handleQueue
		for (Handle handle : handleQueue) {
			if (handle instanceof InvocationHandle) {
				forceInvocationReturn((InvocationHandle) handle);
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
		out = null;
		in = null;
		invocations.clear();
		handleQueue.clear();
	}

	/**
	 * Check if the given object is a stub created by this handler
	 * 
	 * @param obj the object to check
	 * @return true if the object is a stub created by this handler, false otherwise
	 */
	public boolean isMyStub(Object obj) {
		if (Proxy.isProxyClass(obj.getClass())) {
			InvocationHandler ih = Proxy.getInvocationHandler(obj);
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
	public void removeMyStubs(Iterable<?> objects) {
		for (Iterator<?> it = objects.iterator(); it.hasNext();)
			if (isMyStub(it.next()))
				it.remove();
	}

	/**
	 * Removes all the objects that are not stubs created by this handler from the
	 * specified iterable object
	 * 
	 * @param objects the iterable object to analyze
	 */
	public void retainMyStubs(Iterable<?> objects) {
		for (Iterator<?> it = objects.iterator(); it.hasNext();)
			if (!isMyStub(it.next()))
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
	 * Executes the authentication negotiation
	 * 
	 * @throws IOException                   if an I/O error occurs
	 * @throws LocalAuthenticationException  if the local {@link RmiHandler} cannot
	 *                                       authenticate the remote one
	 * @throws RemoteAuthenticationException if the remote {@link RmiHandler} cannot
	 *                                       authenticate the local one
	 */
	private void authentication() throws LocalAuthenticationException, RemoteAuthenticationException, IOException {
		// checks that on the other side of the connection there is a handler that uses
		// the same registry of this one
		out.writeUTF(rmiRegistry.registryKey);
		out.flush();
		String remoteRegistryKey = in.readUTF();

		if (remoteRegistryKey.equals(rmiRegistry.registryKey)) {
			sameRegistryAuthentication = true;
			out.writeBoolean(true);
			out.flush();
		} else {
			sameRegistryAuthentication = false;
			out.writeBoolean(false);
			out.flush();
		}

		if (in.readBoolean()) // the remote handler recognized the registry key
			return;

		// if on the other side there is not the same registry of this handler, do
		// normal authentication
		out.writeUnshared(authIdentifier);
		out.writeUnshared(authPassphrase);
		out.flush();

		String authPass;
		try {
			remoteAuthIdentifier = (String) in.readUnshared();
			authPass = (String) in.readUnshared();
		} catch (Exception e) {
			e.printStackTrace();
			try {
				out.writeBoolean(false);
				out.flush();
			} catch (IOException e1) {
			}
			throw new LocalAuthenticationException();
		}

		if (rmiRegistry.getAuthenticator() == null
				|| rmiRegistry.getAuthenticator().authenticate(inetSocketAddress, remoteAuthIdentifier, authPass)) {
			try {
				out.writeBoolean(true);
				out.flush();
			} catch (IOException e1) {
			}
		} else {
			try {
				out.writeBoolean(false);
				out.flush();
			} catch (IOException e1) {
			}
			throw new LocalAuthenticationException();
		}

		boolean authResult = in.readBoolean();

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
		inetSocketAddress = new InetSocketAddress(socket.getInetAddress(), socket.getPort());
		this.socket = socket;
		this.rmiRegistry = rmiRegistry;

		String[] auth = rmiRegistry.getAuthentication(socket.getInetAddress(), socket.getPort());

		if (auth != null) {
			authIdentifier = auth[0];
			authPassphrase = auth[1];
		}

		OutputStream output = socket.getOutputStream();
		InputStream input = socket.getInputStream();

		if (protocolEndpointFactory != null) {
			ProtocolEndpoint protocolEndpoint = protocolEndpointFactory.createEndpoint(output, input);
			output = protocolEndpoint.getOutputStream();
			input = protocolEndpoint.getInputStream();
		}

		out = new RmiObjectOutputStream(output, rmiRegistry);
		out.flush();
		in = new RmiObjectInputStream(input, rmiRegistry, inetSocketAddress);

		// send authentication
		authentication();
		receiver.setDaemon(true);
		sender.setDaemon(true);
		receiver.start();
		sender.start();
	}

	/**
	 * Gets a stub for the specified object identifier representing a remote object
	 * on the remote machine. This method performs a request to the remote machine
	 * to get the remote interfaces of the remote object, then creates its stub. All
	 * the remote interfaces of the remote object must be visible by the default
	 * class loader and they must be known by the local runtime.
	 * 
	 * @param objectId the object identifier
	 * @param          <T> the stub interface type
	 * @return A dynamic proxy object that represents the remote instance. It is an
	 *         instance for the specified stub interface
	 */
	public Object getStub(String objectId) throws UnknownHostException, IOException, InterruptedException {

		RemoteInterfaceHandle hnd = new RemoteInterfaceHandle(objectId);
		putHandle(hnd);
		hnd.semaphore.acquire();

		return getStub(objectId, hnd.interfaces);
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
	 * @param                <T> the stub interface type
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

	/**
	 * Gets the rmiRegistry used by this {@link RmiHandler}
	 * 
	 * @return the rmiRegistry used by this peer
	 */
	RmiRegistry getRmiRegistry() {
		return rmiRegistry;
	}

	private void forceInvocationReturn(InvocationHandle invocation) {
		if (rmiRegistry.isRemoteExceptionEnabled())
			invocation.thrownException = new RemoteException();
		invocation.returned = true;
		invocation.semaphone.release();
	}

	/**
	 * Package-level operation used by stub invocation handlers to put new
	 * invocations
	 * 
	 * @param invocation the invocation request
	 * @throws InterruptedException
	 */
	void putHandle(Handle handle) throws InterruptedException {
		if (handleQueue != null)
			handleQueue.put(handle);
	}

	/**
	 * This is the thread that manages the output stream of the connection only. It
	 * send new method invocations to the other peer or the invocation results. It
	 * reads new invocations from the handleQueue.
	 */
	private Thread sender = new Thread() {
		@Override
		public void run() {

			Handle handle = null;
			try {

				startRead.release();

				while (!isInterrupted()) {
					handle = null;
					handle = handleQueue.take();

					if (handle instanceof InvocationHandle) {
						InvocationHandle invocation = (InvocationHandle) handle;
						try {
							out.writeUnshared(handle);
							invocations.put(invocation.id, invocation);
						} catch (NotSerializableException e) {
							invocation.thrownException = e;
							synchronized (invocation) {
								invocation.notifyAll();
							}
							throw e;
						}
					} else if (handle instanceof ReturnHandle) { // send
																	// invocation
																	// response
						ReturnHandle ret = (ReturnHandle) handle;
						try {
							out.writeUnshared(ret);
						} catch (NotSerializableException e) {
							ret.returnValue = null;
							ret.returnClass = null;
							ret.thrownException = e;
							out.writeUnshared(ret);
							e.printStackTrace();
						}

					} else if (handle instanceof RemoteInterfaceHandle) {
						RemoteInterfaceHandle rih = (RemoteInterfaceHandle) handle;
						if (rih.interfaces == null) {
							interfaceRequests.put(rih.handleId, rih);
						}
						out.writeUnshared(rih);
					} else {
						out.writeUnshared(handle);
					}

					out.flush();
				}
			} catch (IOException | InterruptedException e) { // something gone wrong, destroy the handler

				// e.printStackTrace();

				if (handle != null) {
					if (handle instanceof InvocationHandle) {
						InvocationHandle invocation = (InvocationHandle) handle;
						invocation.thrownException = e;
						invocation.semaphone.release();
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

	private Semaphore startRead = new Semaphore(0);

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
				// receive authentication
				startRead.acquire();

				while (!isInterrupted()) {
					Handle handle = (Handle) (in.readUnshared());
					if (handle instanceof InvocationHandle) {
						InvocationHandle invocation = (InvocationHandle) handle;

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
								ReturnHandle retHandle = new ReturnHandle();
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
									handleQueue.put(retHandle);
								} catch (InterruptedException e) {
								}

							}).start();
						} else {
							// not authorized: send an authorization exception
							ReturnHandle retHandle = new ReturnHandle();
							retHandle.invocationId = invocation.id;
							retHandle.thrownException = new RuntimeException("authentication exception");
						}
					} else if (handle instanceof ReturnHandle) {
						ReturnHandle ret = (ReturnHandle) handle;

						// remove the waiting invocation
						InvocationHandle invocation = invocations.remove(ret.invocationId);

						if (invocation != null) {

							// set return
							invocation.returnClass = ret.returnClass;
							invocation.returnValue = ret.returnValue;
							invocation.thrownException = ret.thrownException;
							invocation.returned = true;

							// notify the invocation handler that is waiting on it
							invocation.semaphone.release();
						}
					} else if (handle instanceof FinalizeHandle) {
						FinalizeHandle finHandle = (FinalizeHandle) handle;
						Skeleton sk = rmiRegistry.getSkeleton(finHandle.objectId);
						if (sk != null)
							sk.removeRef(RmiHandler.this);
					} else if (handle instanceof NewReferenceHandle) {
						NewReferenceHandle newReferenceHandle = (NewReferenceHandle) handle;
						if (newReferenceHandle.objectId != null) {
							Skeleton sk = rmiRegistry.getSkeleton(newReferenceHandle.objectId);
							sk.addRef(RmiHandler.this);
							references.add(sk.getId());
						}
					} else if (handle instanceof RemoteInterfaceHandle) {
						RemoteInterfaceHandle rih = (RemoteInterfaceHandle) handle;
						if (rih.interfaces == null) {
							List<Class<?>> remotes = rmiRegistry.getRemoteInterfaces(rih.objectId);
							rih.interfaces = new Class<?>[remotes.size()];
							rih.interfaces = remotes.toArray(rih.interfaces);
							putHandle(rih);
						} else {
							RemoteInterfaceHandle req = interfaceRequests.get(rih.handleId);
							req.interfaces = rih.interfaces;
							req.semaphore.release();
						}
					} else {
						throw new RuntimeException("AgileRMI INTERNAL ERROR");
					}

				}
			} catch (Exception e) { // something gone wrong, destroy this handler and dispose it

				// e.printStackTrace();

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
