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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import agilermi.configuration.annotation.RMIAsynch;
import agilermi.configuration.annotation.RMICached;
import agilermi.configuration.annotation.RMISuppressFaults;
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
	private String host;
	private int port;

	private int hashCode;
	String remoteRegistryKey;

	private static class RMICache {
		long time;
		Object result;
	}

	private transient Map<Method, RMICache> cache = Collections.synchronizedMap(new HashMap<>());
	private transient RMIRegistry rmiRegistry;
	private transient RMIHandler handler;

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

		cache = Collections.synchronizedMap(new HashMap<>());

		if (in instanceof RMIObjectInputStream) {
			RMIObjectInputStream rmiInput = (RMIObjectInputStream) in;
			if (host.equals("localhost") || host.equals("127.0.0.1"))
				host = rmiInput.getRemoteAddress();
			rmiRegistry = rmiInput.getRmiRegistry();
		} else {
			rmiRegistry = RMIRegistry.builder().build();
		}

		boolean willBeReplacedByLocalReference = rmiRegistry.getRegistryKey().equals(remoteRegistryKey)
				&& rmiRegistry.getSkeleton(objectId) != null;

		if (willBeReplacedByLocalReference)
			return;

		try {
			getRMIHandler();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void getRMIHandler() throws Exception {
		try {
			handler = rmiRegistry.getRMIHandler(host, port);
			handler.registerStub(this);
			handler.putMessage(new NewReferenceMessage(objectId));
			this.remoteRegistryKey = handler.getRemoteRegistryKey();
		} catch (Exception e) {
			handler = null;
			throw e;
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

		// add RMI stack trace element
		newStackList.add(new StackTraceElement("=====> Remote Method Invocation ======>", "", "", -1));

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

	private void invoke(InvocationMessage invocation) {
		boolean messageSent = false;
		boolean isInterrupted = false;
		boolean interruptionSent = false;
		while (true) {
			if (handler == null || handler.isDisposed())
				return;
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

	public RemoteInvocationHandler(RMIRegistry rmiRegistry, String host, int port, String objectId) {
		this.objectId = objectId;
		this.rmiRegistry = rmiRegistry;

		this.host = host;
		this.port = port;

		try {
			getRMIHandler();
		} catch (Exception e) {

		}

	}

	private void updateLastUse() {
		ReferenceUseMessage msg = new ReferenceUseMessage(objectId);
		try {
			handler.putMessage(msg);
		} catch (InterruptedException e) {
		}
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		boolean isAsynch = method.isAnnotationPresent(RMIAsynch.class) && method.getReturnType() == void.class;
		boolean isCached = method.isAnnotationPresent(RMICached.class) && method.getParameterTypes().length == 0;
		boolean noEffectOnError = method.isAnnotationPresent(RMISuppressFaults.class);

		if (isCached) {
			if (cache.containsKey(method)) {
				RMICache rmiCache = cache.get(method);
				if (System.currentTimeMillis() < rmiCache.time) {
					updateLastUse();
					return rmiCache.result;
				}
			}
		}

		String methodName = method.getName();
		Class<?>[] parameterTypes = method.getParameterTypes();

		// create the invocation message
		InvocationMessage invocation = new InvocationMessage(objectId, methodName, parameterTypes, args, isAsynch);

		// is calling hashCode? this flag is useful for hashCode caching
		boolean hashCodeCall = methodName.equals("hashCode") && parameterTypes.length == 0;

		if (Debug.INVOCATION_HANDLERS)
			if (methodName.equals("toString")) {
				updateLastUse();
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
						updateLastUse();
						return Boolean.valueOf(true);
					}
				}
			}
		}

		boolean finishInvocation = false;
		do {
			invoke(invocation);

			if (invocation.success) {
				if (Debug.INVOCATION_HANDLERS)
					System.out.println(RemoteInvocationHandler.class.getName() + " invocation success!");
				finishInvocation = true;
			} else {
				if (Debug.INVOCATION_HANDLERS)
					System.out.println(RemoteInvocationHandler.class.getName() + " invocation error!");

				long timeout;
				if (handler == null)
					timeout = System.currentTimeMillis() + rmiRegistry.getLatencyTime();
				else
					timeout = handler.getDispositionTime() + rmiRegistry.getLatencyTime();

				do {
					try {
						getRMIHandler();
						if (Debug.INVOCATION_HANDLERS)
							System.out.println(RemoteInvocationHandler.class.getName() + " handler repaired!");
					} catch (Exception e) {
						invocation.thrownException = e;
						if (Debug.INVOCATION_HANDLERS)
							System.out.println(RemoteInvocationHandler.class.getName() + " handler repair error!");
					}

					if (Debug.INVOCATION_HANDLERS) {
						System.out.println(
								RemoteInvocationHandler.class.getName() + " handler==null? " + (handler == null));
						System.out.println(RemoteInvocationHandler.class.getName() + " handler.isDisposed()? "
								+ handler.isDisposed());
					}
				} while ((finishInvocation = handler == null || handler.isDisposed())
						&& System.currentTimeMillis() < timeout);
			}
		} while (!finishInvocation);

		if (invocation.returnValue == null) {
			if (methodName.equals("equals") && parameterTypes.length == 1 && parameterTypes[0] == Object.class)
				return Boolean.valueOf(false);
			if (methodName.equals("hashCode") && parameterTypes.length == 0)
				return Integer.valueOf(hashCode);
			if (methodName.equals("toString") && parameterTypes.length == 0)
				return "[broken remote reference " + objectId + "@" + host + ":" + port + "]";
		} else if (isCached) {
			RMICached cached = method.getAnnotation(RMICached.class);
			RMICache rmiCache;
			if (cache.containsKey(method)) {
				rmiCache = cache.get(method);
				rmiCache.result = invocation.returnValue;
				rmiCache.time = System.currentTimeMillis() + cached.timeout();
			} else {
				rmiCache = new RMICache();
				rmiCache.result = invocation.returnValue;
				rmiCache.time = System.currentTimeMillis() + cached.timeout();
				cache.put(method, rmiCache);
			}
		}

		if (invocation.thrownException != null) {
			if (!(invocation.thrownException instanceof RemoteException && noEffectOnError)) {
				virtualiseStackTrace(invocation.thrownException);
				throw invocation.thrownException;
			}
		} else if (hashCodeCall) {
			// cache hashCode for next non-deliverable invocations
			hashCode = (Integer) invocation.returnValue;
		}

		Class<?> returnType = method.getReturnType();

		if (handler == null && handler.isDisposed() && !returnType.equals(void.class)
				&& invocation.returnValue == null) {
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