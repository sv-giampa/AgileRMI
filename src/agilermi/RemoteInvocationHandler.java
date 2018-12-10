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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.Socket;

import agilermi.exception.LocalAuthenticationException;
import agilermi.exception.RemoteException;

/**
 * Defines the invocation handler for the object stubs
 * 
 * @author Salvatore Giampa'
 *
 */
class RemoteInvocationHandler implements InvocationHandler, Serializable {
	private static final long serialVersionUID = 2428505156272752228L;

	private String objectId;
	private transient RmiHandler handler;

	private boolean hashCodeRequested = false;
	private int hashCode;
	String registryKey;

	public RemoteInvocationHandler(String objectId, RmiHandler rmiHandler) {
		this.objectId = objectId;
		this.handler = rmiHandler;
		this.registryKey = rmiHandler.getRmiRegistry().registryKey;
		try {
			handler.putHandle(new NewReferenceHandle(objectId));
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void writeObject(ObjectOutputStream out) throws IOException {
		if (!(out instanceof RmiObjectOutputStream))
			System.err.println("** WARNING ** in agilermi.RemoteInvocationHandler.writeObject():\n"
					+ "\tWe are writing a remote stub into a non-RMI stream!\n"
					+ "\tWe cannot say what will be read on the input stream of the other side!\n"
					+ "\tProbably this stub will not be able to establish the connection with its remote counterpart!\n"
					+ "\tWriting stub to a non-RMI output stream...");
		out.defaultWriteObject();
		InetSocketAddress address = handler.getInetSocketAddress();
		out.writeUTF(address.getHostString());
		out.writeInt(address.getPort());
	}

	private void readObject(ObjectInputStream in)
			throws IOException, ClassNotFoundException, LocalAuthenticationException {
		in.defaultReadObject();
		String host = in.readUTF();
		int port = in.readInt();

		if (in instanceof RmiObjectInputStream) {
			RmiObjectInputStream cois = (RmiObjectInputStream) in;
			if (host.equals("localhost") || host.equals("127.0.0.1"))
				host = cois.getRemoteAddress();
			RmiRegistry rmiRegistry = ((RmiObjectInputStream) in).getRmiRegistry();
			handler = rmiRegistry.getRmiHandler(host, port, rmiRegistry.isMultiConnectionMode());
		} else {
			handler = new RmiHandler(new Socket(host, port), new RmiRegistry());
		}

		try {
			handler.putHandle(new NewReferenceHandle(objectId));
		} catch (Exception e) {

		}
	}

	public String getObjectId() {
		return objectId;
	}

	public RmiHandler getHandler() {
		return handler;
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

		try {
			synchronized (handler) {
				if (!handler.isDisposed()) {
					handler.putHandle(invocation);
				}
			}

			if (handler.isDisposed()) {
				if (handler.getRmiRegistry().isRemoteExceptionEnabled())
					throw new RemoteException();
			} else {

				invocation.semaphone.acquire();

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

	/**
	 * The finalize method intercept the stub destruction and signal it to the
	 * remote skeleton, to act the distributed garbage collection mechanism
	 */
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