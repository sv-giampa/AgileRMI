package agilermi;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;

/**
 * Defines the invocation handler for the object stubs
 * 
 * @author Salvatore Giampa'
 *
 */
class RemoteInvocationHandler implements InvocationHandler, Serializable {
	private static final long serialVersionUID = 2428505156272752228L;

	private String objectId;
	private RmiHandler handler;

	private boolean hashCodeRequested = false;
	private int hashCode;

	public RemoteInvocationHandler(String objectId, RmiHandler rmiHandler) {
		this.objectId = objectId;
		this.handler = rmiHandler;
		try {
			handler.putHandle(new RefHandle(objectId));
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void writeObject(ObjectOutputStream out) throws IOException {
		InetSocketAddress address = handler.getInetSocketAddress();
		out.writeUTF(objectId);
		out.writeUTF(address.getHostString());
		out.writeInt(address.getPort());
	}

	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		objectId = in.readUTF();
		String host = in.readUTF();
		int port = in.readInt();

		if (in instanceof RmiObjectInputStream) {
			RmiObjectInputStream cois = (RmiObjectInputStream) in;
			if (host.equals("localhost") || host.equals("127.0.0.1"))
				host = cois.getRemoteAddress();
			handler = ((RmiObjectInputStream) in).getObjectContext().getRmiHandler(host, port);
		} else {
			handler = RmiHandler.connect(host, port);
		}

		try {
			handler.putHandle(new RefHandle(objectId));
		} catch (Exception e) {

		}
	}

	public String getObjectId() {
		return objectId;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

		// create the invocation handle
		InvocationHandle invocation = new InvocationHandle(objectId, method.getName(), method.getParameterTypes(),
				args);

		String methodName = method.getName();
		Class<?>[] parameterTypes = method.getParameterTypes();

		// compute hashCode() method locally
		boolean hashCodeCall = methodName.equals("hashCode") && parameterTypes.length == 0;
		if (hashCodeCall && hashCodeRequested) {
			return hashCode;
		}

		// verify alias in the equals() method
		if (methodName.equals("equals") && parameterTypes.length == 1 && parameterTypes[0] == Object.class) {
			if (Proxy.isProxyClass(args[0].getClass())) {
				InvocationHandler ih = Proxy.getInvocationHandler(args[0]);
				if (ih instanceof RemoteInvocationHandler) {
					RemoteInvocationHandler sih = (RemoteInvocationHandler) ih;
					String host = sih.handler.getInetSocketAddress().getHostString();
					int port = sih.handler.getInetSocketAddress().getPort();

					if (sih.objectId.equals(objectId) && host.equals(handler.getInetSocketAddress().getHostString())
							&& port == handler.getInetSocketAddress().getPort())
						return true;
				}
			}
		}

		/*
		 * if(methodName.equals("writeObject") &&
		 * parameterTypes[0].isAssignableFrom(cls)) { return hashCode(); }
		 */

		if (handler.isDisposed()) {
			throw new RmiDispositionException();
		} else {

			handler.putHandle(invocation);

			try {
				synchronized (invocation) {
					while (!invocation.returned) {
						invocation.wait();
					}

					if (invocation.thrownException != null) {
						invocation.thrownException.fillInStackTrace();
						throw invocation.thrownException;
					} else if (hashCodeCall) {
						hashCode = (Integer) invocation.returnValue;
						hashCodeRequested = true;
					}
				}
			} catch (InterruptedException e) {
			}

		}

		Class<?> returnType = method.getReturnType();

		if (!returnType.equals(void.class) && invocation.returnValue == null && returnType.isPrimitive()) {
			if (returnType == boolean.class)
				return false;
			if (returnType == byte.class)
				return (byte) 0;
			else if (returnType == char.class)
				return (char) 0;
			else if (returnType == short.class)
				return (short) 0;
			else if (returnType == int.class)
				return (int) 0;
			else if (returnType == long.class)
				return (long) 0;
			else if (returnType == float.class)
				return (float) 0;
			else if (returnType == double.class)
				return (double) 0;
		}

		return invocation.returnValue;
	}

	@Override
	protected void finalize() throws Throwable {
		handler.putHandle(new FinalizeHandle(objectId));
		super.finalize();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((objectId == null) ? 0 : objectId.hashCode());
		result = prime * result + ((handler == null) ? 0 : handler.hashCode());
		return result;
	}

}