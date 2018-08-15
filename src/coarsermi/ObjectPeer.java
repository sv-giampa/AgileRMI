package coarsermi;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * 
 * @author Salvatore Giampa'
 *
 */
public class ObjectPeer {

	/**
	 * static fields useful to retrieve wrappers from primitives and vice versa.
	 */
	private static final Map<Class<?>, Class<?>> PRIMITIVES_TO_WRAPPERS = new HashMap<Class<?>, Class<?>>();
	private static final Map<Class<?>, Class<?>> WRAPPERS_TO_PRIMITIVES = new HashMap<Class<?>, Class<?>>();
	static {
		PRIMITIVES_TO_WRAPPERS.put(boolean.class, Boolean.class);
		PRIMITIVES_TO_WRAPPERS.put(byte.class, Byte.class);
		PRIMITIVES_TO_WRAPPERS.put(char.class, Character.class);
		PRIMITIVES_TO_WRAPPERS.put(double.class, Double.class);
		PRIMITIVES_TO_WRAPPERS.put(float.class, Float.class);
		PRIMITIVES_TO_WRAPPERS.put(int.class, Integer.class);
		PRIMITIVES_TO_WRAPPERS.put(long.class, Long.class);
		PRIMITIVES_TO_WRAPPERS.put(short.class, Short.class);
		PRIMITIVES_TO_WRAPPERS.put(void.class, Void.class);

		WRAPPERS_TO_PRIMITIVES.put(Boolean.class, boolean.class);
		WRAPPERS_TO_PRIMITIVES.put(Byte.class, byte.class);
		WRAPPERS_TO_PRIMITIVES.put(Character.class, char.class);
		WRAPPERS_TO_PRIMITIVES.put(Double.class, double.class);
		WRAPPERS_TO_PRIMITIVES.put(Float.class, float.class);
		WRAPPERS_TO_PRIMITIVES.put(Integer.class, int.class);
		WRAPPERS_TO_PRIMITIVES.put(Long.class, long.class);
		WRAPPERS_TO_PRIMITIVES.put(Short.class, short.class);
		WRAPPERS_TO_PRIMITIVES.put(Void.class, void.class);
	}

	// connection details, socket and streams
	private InetSocketAddress inetSocketAddress;
	private Socket socket;
	private ObjectOutputStream out;
	private ObjectInputStream in;

	private ObjectRegistry registry;

	/**
	 * Map for invocations that are waiting a response
	 */
	private Map<Long, StubInvocation> invocations = Collections.synchronizedMap(new HashMap<>());

	/**
	 * The queue for buffered invocations that are ready to be sent over the socket
	 */
	private BlockingQueue<StubInvocation> invokeQueue = new ArrayBlockingQueue<>(200);

	/**
	 * Flag that indicates if this ObjectPeer has been disposed. When
	 */
	private boolean disposed = false;

	/**
	 * Implements the flyweight pattern for stubs creation
	 */
	private Map<StubKey, Object> stubFlyweight = Collections.synchronizedMap(new HashMap<>());

	/**
	 * Connects to the selected address and port and creates a new ObjectPeer over
	 * that connection, with a new empty {@link ObjectRegistry}
	 * 
	 * @param address the address of the object server
	 * @param port    the port of the object server
	 * @return the ObjectPeer object representing the remote object server
	 * @throws UnknownHostException if the host cannot be found
	 * @throws IOException          if an I/O error occurs
	 */
	public static ObjectPeer connect(String address, int port) throws UnknownHostException, IOException {
		Socket socket = new Socket(address, port);
		return new ObjectPeer(socket, new ObjectRegistry());
	}

	/**
	 * Connects to the selected address and port and creates a new ObjectPeer over
	 * that connection, with the specified {@link ObjectRegistry}
	 * 
	 * @param address  the address of the object server
	 * @param port     the port of the object server
	 * @param registry the registry that must be used by the ObjectPeer
	 * @return the ObjectPeer object representing the remote object server
	 * @throws UnknownHostException if the host cannot be found
	 * @throws IOException          if an I/O error occurs
	 */
	public static ObjectPeer connect(String address, int port, ObjectRegistry registry)
			throws UnknownHostException, IOException {
		Socket socket = new Socket(address, port);
		return new ObjectPeer(socket, registry);
	}

	/**
	 * Constructs a new ObjectPeer over the connection specified by the given
	 * socket, with the specified {@link ObjectRegistry}.
	 * 
	 * @param socket   the socket over which the ObjectPeer will be created
	 * @param registry the {@link ObjectRegistry} to use
	 * @see ObjectPeer#connect(String, int, ObjectRegistry)
	 * @see ObjectPeer#connect(String, int)
	 * @throws IOException if an I/O error occurs
	 */
	public ObjectPeer(Socket socket, ObjectRegistry registry) throws IOException {
		inetSocketAddress = new InetSocketAddress(socket.getInetAddress(), socket.getPort());
		this.socket = socket;
		this.registry = registry;

		out = new ObjectOutputStream(socket.getOutputStream());
		in = new ObjectInputStream(socket.getInputStream());
		receiver.setDaemon(true);
		sender.setDaemon(true);
		receiver.start();
		sender.start();
	}

	/**
	 * Gets a stub for the specified object identifier respect to the specified
	 * interface, representing a remote object on the object server. This method
	 * performs no network operation, just creates the stub.
	 * 
	 * @param objectId      the object identifier
	 * @param stubInterface the interface whose methods must be stubbed, that is the
	 *                      interface used to access the remote object operations
	 * @param               <T> the stub interface type
	 * @return A dynamic proxy object that represents the remote instance. It is an
	 *         instance for the specified stub interface
	 */
	@SuppressWarnings("unchecked")
	public synchronized <T> T getStub(String objectId, Class<T> stubInterface) {
		if (disposed)
			throw new IllegalStateException("The ObjectPeer has been disposed");
		if (!stubInterface.isInterface())
			throw new IllegalArgumentException("the specified class is not an interface");

		T stub;

		StubKey key = new StubKey(objectId, stubInterface);

		if (stubFlyweight.containsKey(key)) {
			stub = (T) stubFlyweight.get(key);
		} else {
			stub = (T) Proxy.newProxyInstance(stubInterface.getClassLoader(), new Class<?>[] { stubInterface },
					new StubInvocationHandler(objectId, this));
			stubFlyweight.put(key, stub);
		}
		return stub;
	}

	/**
	 * Gets the registry used by this ObjectPeer
	 * 
	 * @return the registry used by this peer
	 */
	public ObjectRegistry getRegistry() {
		return registry;
	}

	/**
	 * Gets the remote connection details of this peer
	 * @return the {@link InetSocketAddress} containing remote host address and port
	 */
	public InetSocketAddress getInetSocketAddress() {
		return inetSocketAddress;
	}
	
	/**
	 * Dispose this ObjectPeer and frees all the used resources and threads. After
	 * the call to this method, the call to the
	 * {@link ObjectPeer#getStub(String, Class)} method will result in an
	 * {@link IllegalStateException} and all the stubs generated by this
	 * {@link ObjectPeer} object will not function properly.
	 */
	public synchronized void dispose() {
		if (disposed)
			return;

		disposed = true;
		receiver.interrupt();
		sender.interrupt();

		try {
			socket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// let the stubs to return
		for (StubInvocation invocation : invocations.values()) {
			synchronized (invocation) {
				invocation.returned = true;
				invocation.notifyAll();
			}
		}

		socket = null;
		out = null;
		in = null;
		invocations = null;
		invokeQueue = null;
	}

	/**
	 * Gets an unmodifiable collection containing all the stubs created by this
	 * ObjectPeer
	 * 
	 * @return an unmodifiable collection containing stubs
	 */
	public Collection<Object> getCreatedStubs() {
		return Collections.unmodifiableCollection(stubFlyweight.values());
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
	 * Package-level operation used by stub invocation handlers to put new
	 * invocations
	 * 
	 * @param invocation the invocation request
	 * @throws InterruptedException
	 */
	void putInvocation(StubInvocation invocation) throws InterruptedException {
		invokeQueue.put(invocation);
	}

	/**
	 * This is the thread that manages the output stream of the connection only. It
	 * send new method invocations to the other peer or the invocation results. It
	 * reads new invocations from the invokeQueue.
	 */
	private Thread sender = new Thread() {
		public void run() {
			try {
				while (!isInterrupted()) {
					StubInvocation invocation = null;
					invocation = invokeQueue.take();

					// System.out.println("ObjectPeer.sender new invocation!");

					out.writeBoolean(invocation.isRequest);
					out.writeLong(invocation.id);

					if (invocation.isRequest) {
						// send invocation request
						sendArguments(invocation);
						out.writeUTF(invocation.objectId);
						out.writeUTF(invocation.method.getName());

						invocations.put(invocation.id, invocation);

					} else { // send invocation response

						// send the return value
						sendReturn(invocation);

					}
					out.flush();
				}
			} catch (Exception e) { // something gone wrong, destroy the ObjectPeer
				if (disposed)
					return;

				dispose();

				e.printStackTrace();

				try {
					socket.close();
				} catch (Exception e1) {
				}

				registry.sendFailure(ObjectPeer.this, e);
			}
		}

		/**
		 * Send all the invocation arguments. This is used only when sending an
		 * invocation request
		 * 
		 * @param invocation
		 * @throws IOException
		 */
		private void sendArguments(StubInvocation invocation) throws IOException {
			/*
			 * if (invocation.params == null) { out.writeInt(0); return; }
			 */

			// determine all the arguments classes by the method
			Class<?>[] parameterTypes = invocation.method.getParameterTypes();

			out.writeUnshared(parameterTypes);

			if (parameterTypes.length > 0) {
				int i = 0;
				for (Object arg : invocation.params) {

					String id = registry.getId(arg, parameterTypes[i]);

					// if it is possible create a remote reference automatically
					if (id == null && registry.isAutoReferenced(parameterTypes[i])) {
						id = registry.publish(arg, parameterTypes[i]);
					}

					// distinguish between remote references and serializable objects
					if (id != null) {
						out.writeBoolean(true); // it is a remote reference
						out.writeUnshared(id); // write the object id only on the stream
					} else {
						out.writeBoolean(false); // it is not a remote reference
						out.writeUnshared(arg); // serialize the object to the stream (throws a NotSerializableException
												// if the argument is not serializable)
					}

					i++;
				}
			}
		}

		/**
		 * This is the procedure that send a single invocation argument or the return
		 * value
		 * 
		 * @param arg
		 * @param argClass
		 * @throws IOException
		 */
		private void sendReturn(StubInvocation invocation) throws IOException {

			/*
			 * if the object 'invocation.returnValue' is sent as an 'invocation.returnClass'
			 * type for the method and the couple <invocation.returnValue,
			 * invocation.returnClass> is published in the ObjectRegistry, than creates a
			 * remote reference, otherwise serialize 'invocation.returnValue' over the
			 * stream
			 */
			String id = registry.getId(invocation.returnValue, invocation.returnClass);

			// if it is possible create a remote reference automatically
			if (id == null && registry.isAutoReferenced(invocation.returnClass)) {
				id = registry.publish(invocation.returnValue, invocation.returnClass);
			}

			// distinguish between remote references and serializable objects
			if (id != null) {
				out.writeBoolean(true); // it is a remote reference
				out.writeUnshared(invocation.returnClass);
				out.writeUnshared(id);
			} else {
				out.writeBoolean(false); // it is not a remote reference
				out.writeUnshared(invocation.returnValue);
			}

			// send an eventually thrown exception
			out.writeUnshared(invocation.thrownException);
		}
	};
	/**
	 * This is the thread that manages the input stream of the connection only. It
	 * receives new method invocations by the other peer or the invocation results.
	 * In the first case it calls the method of the implementation object. In the
	 * second case it notifies the invocation handlers thata are waiting for the
	 * remote method to return.
	 */
	private Thread receiver = new Thread() {
		public void run() {
			try {
				while (!isInterrupted()) {

					// invocation header
					boolean isRequest = in.readBoolean();
					long invocationId = in.readLong();

					if (isRequest) { // receive invocation request

						Class<?>[] parameterTypes = (Class<?>[]) in.readUnshared();

						// receive invocation arguments
						Object[] args = new Object[parameterTypes.length];
						for (int i = 0; i < args.length; i++) {
							boolean remoteRef = in.readBoolean();
							if (remoteRef) {
								String argObjectId = (String) in.readUnshared();
								args[i] = getStub(argObjectId, parameterTypes[i]);
							} else {
								args[i] = in.readUnshared();
							}
						}

						String objectId = in.readUTF();
						String methodName = in.readUTF();

						// fork the method execution and let the receiver to receive new invocations
						new Thread(() -> {

							StubInvocation invocation = new StubInvocation();

							// set the invocation header
							invocation.isRequest = false;
							invocation.id = invocationId;

							try {
								// retrieve the object
								Object object = registry.getObject(objectId);

								// find the correct method
								Method method = object.getClass().getMethod(methodName, parameterTypes);

								// set the method accessible
								method.setAccessible(true);

								// set invocation return class
								invocation.returnClass = method.getReturnType();

								// invoke the method
								invocation.returnValue = method.invoke(object, args);

							} catch (InvocationTargetException e) {
								e.printStackTrace();
								invocation.thrownException = e.getCause();
							} catch (NoSuchMethodException e) {
								e.printStackTrace();
								invocation.thrownException = new NoSuchMethodException("The method '" + methodName + "("
										+ Arrays.toString(parameterTypes)
										+ ")' does not exists for the object with identifier '" + objectId + "'.");
							} catch (SecurityException e) {
								e.printStackTrace();
								invocation.thrownException = e;
							} catch (IllegalAccessException e) {
								e.printStackTrace();
							} catch (IllegalArgumentException e) {
								e.printStackTrace();
								invocation.thrownException = e;
							} catch (NullPointerException e) {
								e.printStackTrace();
								invocation.thrownException = new NullPointerException("The object identifier '"
										+ objectId + "' of the stub is not bound to a remote object");
							}

							// send invocation response after method execution
							try {
								invokeQueue.put(invocation);
							} catch (InterruptedException e) {
							}

						}).start();

					} else { // receive invocation response

						// remove the waiting invocation
						StubInvocation invocation = invocations.remove(invocationId);

						// receive return value or the thrown exception
						receiveReturn(invocation);
					}
				}
			} catch (Exception e) { // something gone wrong, destroy the ObjectPeer

				e.printStackTrace();

				if (disposed)
					return;

				dispose();

				try {
					socket.close();
				} catch (Exception e1) {
				}

				registry.sendFailure(ObjectPeer.this, e);
			}
		}

		/**
		 * This is the procedure used to receive the invocation arguments or the return
		 * value. A single call of this reads only one argument.
		 * 
		 * @param result The two-lengthened object array that will contain the argument
		 *               Object (result[0]) and the argument class (result[1]), after
		 *               the call.
		 * @throws ClassNotFoundException
		 * @throws IOException
		 */
		private void receiveReturn(StubInvocation invocation) throws ClassNotFoundException, IOException {

			Object returnValue;
			Class<?> returnClass = null;

			boolean remoteRef = in.readBoolean(); // is it a remote reference?

			if (remoteRef) { // create stub
				returnClass = (Class<?>) in.readUnshared();
				String argObjectId = (String) in.readUnshared();
				returnValue = getStub(argObjectId, returnClass);
			} else { // deserialize
				returnValue = in.readUnshared();
			}

			Throwable thrownException = (Throwable) in.readUnshared();

			if (invocation != null) {

				// set return
				invocation.returnClass = returnClass;
				invocation.returnValue = returnValue;
				invocation.thrownException = (Throwable) thrownException;
				invocation.returned = true;

				// notify the invocation handler that is waiting on it
				synchronized (invocation) {
					invocation.notifyAll();
				}
			}
		}
	};

}
