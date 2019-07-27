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
import java.io.OutputStream;
import java.io.Serializable;
import java.io.WriteAbortedException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.UnknownHostException;
import java.util.List;

import agilermi.configuration.Remote;
import agilermi.configuration.StubRetriever;

/**
 * This class extends the standard {@link ObjectInputStream} to give an object
 * context to the deserializing {@link RemoteInvocationHandler} instances. This
 * class is the right ventricle of the heart of the deep remote referencing
 * mechanism, that replaces all the remote object references with their remote
 * stub, when they are sent on the network. Its counterpart is the
 * {@link RMIObjectInputStream} class.
 * 
 * @author Salvatore Giampa'
 *
 */
final class RMIObjectOutputStream extends ObjectOutputStream {
	private RMIHandler handler;
	private RMIRegistry rmiRegistry;
	private Class<?> rootType = null;

	public RMIObjectOutputStream(OutputStream outputStream, RMIHandler handler) throws IOException {
		super(outputStream);
		this.handler = handler;
		this.rmiRegistry = handler.getRMIRegistry();
		this.enableReplaceObject(true);
	}

	public RMIRegistry getObjectContext() {
		return rmiRegistry;
	}

	public void setRootType(Class<?> rootType) {
		this.rootType = rootType;
	}

	/**
	 * Verify that an object contains values that must be remotized
	 * 
	 * @param obj
	 * @return
	 */
	private boolean mustBeRemotized(Object obj) {
		Class<?> objClass = obj.getClass();
		if (objClass.isArray()) {
			Class<?> type = objClass.getComponentType();
			int len = Array.getLength(obj);
			for (int i = 0; i < len; i++) {
				if (type == StubRetriever.class)
					return true;

				Object value = Array.get(obj, i);
				if ((value != null && rmiRegistry.isRemote(type)))
					return true;
			}
		} else if (obj instanceof Serializable) {
			try {
				do {
					Field[] fields = objClass.getDeclaredFields();

					for (Field field : fields) {
						Class<?> type = field.getType();
						if (type == StubRetriever.class)
							return true;

						int modifiers = field.getModifiers();
						if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers)) {
							field.setAccessible(true);
							Object value = field.get(obj);
							if ((value != null && rmiRegistry.isRemote(type)))
								return true;
						}
					}
				} while ((objClass = objClass.getSuperclass()) != null);
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
		}
		return false;
	}

	/**
	 * Replaces the values contained in an object that with their remote reference,
	 * where necessary
	 * 
	 * @param obj the object whose values must be remotized
	 * @return a complete new copy of the serializable object
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	private Object remotize(Object obj) throws UnknownHostException, IOException {
		Class<?> objClass = obj.getClass();
		if (objClass.isArray()) {
			Class<?> type = objClass.getComponentType();
			int len = Array.getLength(obj);
			Object newArray = Array.newInstance(type, len);

			if (type == StubRetriever.class) {
				for (int i = 0; i < len; i++) {
					Array.set(newArray, i, null);
				}
			} else {
				for (int i = 0; i < len; i++) {
					Object value = Array.get(obj, i);
					value = remotize(value, type);
					Array.set(newArray, i, value);
				}
			}
			return newArray;
		} else if (obj instanceof Serializable) {
			boolean accessible = false;
			try {
				Object shallowCopy = null;
				Constructor<?> defaultConstructor = objClass.getConstructor();
				if (defaultConstructor != null) {
					accessible = defaultConstructor.isAccessible();
					defaultConstructor.setAccessible(true);
				}
				try {
					shallowCopy = defaultConstructor.newInstance();
				} finally {
					if (defaultConstructor != null)
						defaultConstructor.setAccessible(accessible);
				}

				do {
					Field[] fields = objClass.getDeclaredFields();
					for (Field field : fields) {
						int modifiers = field.getModifiers();
						if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers)) {
							accessible = field.isAccessible();
							field.setAccessible(true);
							Object value;
							if (field.getType() == StubRetriever.class) {
								value = null;
								field.set(obj, rmiRegistry.getStubRetriever());
							} else {
								value = field.get(obj);
								if (rmiRegistry.isRemote(field.getType())) {
									value = remotize(value, field.getType());
								}
							}
							field.set(shallowCopy, value);
							field.setAccessible(accessible);
						}
					}
				} while ((objClass = objClass.getSuperclass()) != null);
				return shallowCopy;
			} catch (InvocationTargetException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();

			} catch (IllegalAccessException e) {
				throw new WriteAbortedException(
						"No-arg constructor is not accessible in the class " + objClass.getName(), e);
			} catch (SecurityException e) {
				throw new WriteAbortedException("SecurityException has been thrown", e);
			} catch (NoSuchMethodException e) {
				throw new WriteAbortedException("No-arg constructor not present in the class " + objClass.getName(), e);
			} catch (InstantiationException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			}
		}
		return obj;

	}

	private InvocationMessage remotize(InvocationMessage msg) throws UnknownHostException, IOException {
		if (msg.parameters == null)
			return msg;
		if (msg.parameters.length != msg.parameterTypes.length)
			return msg;
		Object[] parameters = new Object[msg.parameters.length];
		System.arraycopy(msg.parameters, 0, parameters, 0, parameters.length);

		for (int i = 0; i < parameters.length; i++) {
			if (msg.parameterTypes[i] == null || !msg.parameterTypes[i].isInstance(msg.parameters[i]))
				continue;
			parameters[i] = remotize(msg.parameters[i], msg.parameterTypes[i]);
		}

		InvocationMessage newMsg = new InvocationMessage(msg.id, msg.objectId, msg.method, msg.parameterTypes,
				parameters, msg.asynch);
		return newMsg;
	}

	private ReturnMessage remotize(ReturnMessage handle) throws UnknownHostException, IOException {
		if (handle.returnValue == null)
			return handle;
		if (handle.returnClass == null || !handle.returnClass.isInstance(handle.returnValue))
			return handle;
		Object returnValue = remotize(handle.returnValue, handle.returnClass);
		ReturnMessage newHandle = new ReturnMessage(handle.invocationId, handle.returnClass, returnValue,
				handle.thrownException);
		return newHandle;
	}

	private Object remotize(Object obj, Class<?> formalType) throws UnknownHostException, IOException {
		if (obj == null)
			return null;

		if (formalType == StubRetriever.class)
			return null;

		// if necesseray routes the stub connection (if the remote machine connected to
		// the sending stub has not an active RMI listener on its RMIRegistry)
		boolean isStub = false;
		if (obj instanceof Proxy) {
			InvocationHandler ih = Proxy.getInvocationHandler(obj);
			if (ih instanceof RemoteInvocationHandler) {
				RemoteInvocationHandler rih = (RemoteInvocationHandler) ih;
				RMIHandler handler = rih.getHandler();

				// if obj is a shareable stub, serializes it
				if (handler != null && rih.getHandler().areStubsShareable())
					return obj;

				isStub = true;
			}
		}

		// the object is a non-shareable stub or its formal type is remote
		if (isStub || rmiRegistry.isRemote(formalType)) {
			if (Debug.RMI_OUTPUT_STREAM)
				System.out.printf("[RMIObjectOutputStream] Remotizing object=%s, class=%s\n", obj,
						formalType.getName());

			String objectId = rmiRegistry.publish(obj);
			Object stub = Proxy.newProxyInstance(formalType.getClassLoader(), new Class<?>[] { formalType },
					new ReferenceInvocationHandler(objectId));
			return stub;

		}

		// remote object sent as a Object type (possibly type-erased)
		if (formalType == Object.class) { // anti-type-erasure
			List<Class<?>> remoteIfs = rmiRegistry.getRemoteInterfaces(obj.getClass());
			if (remoteIfs.size() >= 1) {
				String objectId = rmiRegistry.publish(obj);

				Object stub = Proxy.newProxyInstance(remoteIfs.get(0).getClassLoader(),
						remoteIfs.toArray(new Class<?>[] { remoteIfs.get(0) }),
						new ReferenceInvocationHandler(objectId));
				return stub;
			}
		}

		// serializable object
		return obj;
	}

	@Override
	protected Object replaceObject(Object obj) throws IOException {
		if (!rmiRegistry.isAutomaticReferencingEnabled()) {
			if (!(obj instanceof Serializable) && obj instanceof Remote) {
				throw new RuntimeException(String.format("Object {%s} is a remote and non-serializable object, "
						+ "but the automatic referencing is disabled on the RMI registry. "
						+ "So it cannot be sent to the remote machine.", obj));
			}
			return obj;
		}

		if (obj instanceof String || obj instanceof Number || obj instanceof Boolean || obj instanceof Character)
			return obj;

		if (obj instanceof InvocationMessage)
			return remotize((InvocationMessage) obj);
		if (obj instanceof ReturnMessage)
			return remotize((ReturnMessage) obj);

		if (rootType != null) {
			Object newObj = remotize(obj, rootType);
			rootType = null;

			if (newObj != obj)
				return newObj;
		}

		if (mustBeRemotized(obj))
			return remotize(obj);

		return obj;
	}

	@Override
	public void flush() throws IOException {
		super.flush();
		this.reset();
	}

	@Override
	public void close() throws IOException {
		reset();
		super.close();
		System.gc();
	}

}
