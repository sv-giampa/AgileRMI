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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import agilermi.exception.LocalAuthenticationException;

/**
 * Defines the invocation handler for the object stubs
 * 
 * @author Salvatore Giampa'
 *
 */
class RemoteInvocationHandler implements InvocationHandler, Serializable {
	private static final long serialVersionUID = 2428505156272752228L;

	private String objectId;
	private transient RMIRegistry rmiRegistry;
	private transient RMIHandler handler;

	private int hashCode;
	final String remoteRegistryKey;

	private String host;
	private int port;

	private void writeObject(ObjectOutputStream out) throws IOException {
		if (!(out instanceof RMIObjectOutputStream))
			System.err.println("** WARNING ** in " + getClass().getName() + ".writeObject():\n"
					+ "\tWe are writing a remote stub into a non-RMI stream!\n"
					+ "\tWe cannot say what will be read on the input stream of the other side!\n"
					+ "\tProbably this stub will not be able to establish the connection with its remote counterpart!\n"
					+ "\tWriting stub to a non-RMI output stream...");

		InetSocketAddress address = handler.getInetSocketAddress();
		host = address.getHostString();
		port = address.getPort();
		out.defaultWriteObject();
	}

	private void readObject(ObjectInputStream in)
			throws IOException, ClassNotFoundException, LocalAuthenticationException {
		in.defaultReadObject();

		if (in instanceof RMIObjectInputStream) {
			RMIObjectInputStream rmiInput = (RMIObjectInputStream) in;
			if (host.equals("localhost") || host.equals("127.0.0.1"))
				host = rmiInput.getRemoteAddress();
			RMIRegistry rmiRegistry = rmiInput.getRmiRegistry();
			boolean willBeReplacedByLocalReference = remoteRegistryKey.equals(rmiRegistry.getRegistryKey())
					&& rmiRegistry.getSkeleton(objectId) != null;
			if (willBeReplacedByLocalReference)
				return;
			handler = rmiRegistry.getRMIHandler(host, port, rmiRegistry.isMultiConnectionMode());
		} else {
			handler = RMIRegistry.builder().build().getRMIHandler(host, port);
		}

		rmiRegistry = handler.getRMIRegistry();
		handler.registerStub(this);

		try {
			handler.putMessage(new NewReferenceMessage(objectId));
		} catch (Exception e) {

		}
	}

	private void virtualiseStackTrace(Throwable e) {
		List<StackTraceElement> newStackList = new LinkedList<>();
		StackTraceElement[] remoteStack = e.getStackTrace();
		StackTraceElement[] localStack = Thread.currentThread().getStackTrace();

		// add remote part of stack trace
		String thisClassName = this.getClass().getName();
		for (int i = 0; i < remoteStack.length; i++) {
			newStackList.add(remoteStack[i]);
			String className = remoteStack[i].getClassName();
			if (className.equals(thisClassName))
				break;
		}

		// add RMI stack element
		newStackList.add(new StackTraceElement("============> Remote Method Invocation ============>", "", "", -1));

		// add local part of stack trace
		for (int i = 4; i < localStack.length; i++) {
			if (localStack[i].getClassName().equals(this.getClass().getName())
					|| localStack[i].getClassName().contains(".$Proxy"))
				continue;
			newStackList.add(localStack[i]);
		}

		StackTraceElement[] newStack = newStackList.toArray(new StackTraceElement[newStackList.size()]);
		e.setStackTrace(newStack);
	}

	private Lock handlerLock = new ReentrantLock();
	private Condition handlerCondition = handlerLock.newCondition();
	private boolean handlerReplaced = false;

	void replaceHandler(RMIHandler newHandler) {
		handlerLock.lock();
		try {
			if (newHandler != null) {
				this.handler = newHandler;
				while (true)
					try {
						handler.putMessage(new NewReferenceMessage(objectId));
						break;
					} catch (InterruptedException e) {
					}
			}
			handlerReplaced = true;
			handlerCondition.signalAll();
		} finally {
			handlerLock.unlock();
		}
	}

	private void invoke(InvocationMessage invocation) {
		boolean messageSent = false;
		boolean isInterrupted = false;
		boolean interruptionSent = false;
		while (true) {
			try {
				if (!messageSent) {
					handler.putMessage(invocation);
					messageSent = true;
				}
				if (isInterrupted && !interruptionSent) {
					handler.putMessage(new InterruptionMessage(invocation.id));
					interruptionSent = true;
				}
				invocation.awaitResult();
				break;
			} catch (InterruptedException e) {
				isInterrupted = true;
			}
		}

		if (isInterrupted)
			Thread.currentThread().interrupt();
	}

	String getObjectId() {
		return objectId;
	}

	RMIHandler getHandler() {
		return handler;
	}

	public RemoteInvocationHandler(String objectId, RMIHandler rmiHandler) {
		this.objectId = objectId;
		this.handler = rmiHandler;
		this.rmiRegistry = rmiHandler.getRMIRegistry();
		this.remoteRegistryKey = rmiHandler.getRemoteRegistryKey();
		handler.registerStub(this);

		InetSocketAddress address = handler.getInetSocketAddress();
		host = address.getHostString();
		port = address.getPort();

		try {
			handler.putMessage(new NewReferenceMessage(objectId));
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

		String methodName = method.getName();
		Class<?>[] parameterTypes = method.getParameterTypes();

		// create the invocation handle
		InvocationMessage invocation = new InvocationMessage(objectId, methodName, parameterTypes, args);

		// is calling hashCode? this flag is useful for hashCode caching
		boolean hashCodeCall = methodName.equals("hashCode") && parameterTypes.length == 0;

		if (Debug.INVOCATION_HANDLERS)
			if (methodName.equals("toString")) {
				return "[DEBUG toString()] Proxy:" + objectId + "@" + handler.getInetSocketAddress().getHostName() + ":"
						+ handler.getInetSocketAddress().getPort();
			}

		// verify alias in the equals() method. Try to compute equals() locally.
		if (methodName.equals("equals") && parameterTypes.length == 1 && parameterTypes[0] == Object.class) {
			if (args[0] == proxy)
				return Boolean.valueOf(true);
			if (Proxy.isProxyClass(args[0].getClass())) {
				InvocationHandler ih = Proxy.getInvocationHandler(args[0]);
				if (ih instanceof RemoteInvocationHandler) {
					RemoteInvocationHandler sih = (RemoteInvocationHandler) ih;
					String host = sih.handler.getInetSocketAddress().getHostString();
					int port = sih.handler.getInetSocketAddress().getPort();

					if (sih.objectId.equals(objectId) && host.equals(handler.getInetSocketAddress().getHostString())
							&& port == handler.getInetSocketAddress().getPort()) {
						return Boolean.valueOf(true);
					}
				}
			}
		}

		boolean finish = false;
		do {
			invoke(invocation);
			if (invocation.success)
				finish = true;
			else {
				handlerLock.lock();
				try {
					long timeout = handler.getDispositionTime() + rmiRegistry.getLeaseValue();
					while (!handlerReplaced && handler.isDisposed() && System.currentTimeMillis() < timeout)
						handlerCondition.await(timeout - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
					handlerReplaced = false;
					finish = handler.isDisposed();
				} finally {
					handlerLock.unlock();
				}
			}
		} while (!finish);

		if (invocation.returnValue == null) {
			if (methodName.equals("equals") && parameterTypes.length == 1 && parameterTypes[0] == Object.class)
				return Boolean.valueOf(false);
			if (methodName.equals("hashCode") && parameterTypes.length == 0)
				return Integer.valueOf(hashCode);
			if (handler.isDisposed() && methodName.equals("toString") && parameterTypes.length == 0)
				return "[broken remote reference " + objectId + "@" + handler.getInetSocketAddress().getHostString()
						+ ":" + handler.getInetSocketAddress().getPort() + "]";
		}

		if (invocation.thrownException != null) {
			virtualiseStackTrace(invocation.thrownException);
			throw invocation.thrownException;
		} else if (hashCodeCall) {
			// cache hashCode for next non-deliverable invocations
			hashCode = (Integer) invocation.returnValue;
		}

		Class<?> returnType = method.getReturnType();

		if (handler.isDisposed() && !returnType.equals(void.class) && invocation.returnValue == null) {
			if (returnType.isPrimitive()) {
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
			} else if (Number.class.isAssignableFrom(returnType)) {
				if (Byte.class.isAssignableFrom(returnType))
					return Byte.valueOf((byte) 0);
				else if (Short.class.isAssignableFrom(returnType))
					return Short.valueOf((short) 0);
				else if (Integer.class.isAssignableFrom(returnType))
					return Integer.valueOf(0);
				else if (Long.class.isAssignableFrom(returnType))
					return Long.valueOf(0);
				else if (Float.class.isAssignableFrom(returnType))
					return Float.valueOf(0f);
				else if (Double.class.isAssignableFrom(returnType))
					return Double.valueOf(0d);
			} else if (Boolean.class.isAssignableFrom(returnType))
				return Boolean.valueOf(false);
			else if (Character.class.isAssignableFrom(returnType))
				return Character.valueOf((char) 0);
			else if (returnType.isArray()) {
				return Array.newInstance(returnType.getComponentType(), 0);
			}
		}

		return invocation.returnValue;
	}

	/**
	 * The finalize method intercept the stub destruction and signal it to the
	 * remote skeleton, to act the distributed garbage collection mechanism
	 */
	@Override
	protected void finalize() throws Throwable {
		handler.putMessage(new FinalizeMessage(objectId));
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