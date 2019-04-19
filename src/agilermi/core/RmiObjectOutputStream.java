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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.WriteAbortedException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.UnknownHostException;

import agilermi.configuration.Remote;

/**
 * This class extends the standard {@link ObjectInputStream} to give an object
 * context to the deserializing {@link RemoteInvocationHandler} instances. This
 * class is the right ventricle of the heart of the deep remote referencing
 * mechanism, that replaces all the remote object references with their remote
 * stub, when they are sent on the network. Its counterpart is the
 * {@link RmiObjectInputStream} class.
 * 
 * @author Salvatore Giampa'
 *
 */
class RmiObjectOutputStream extends ObjectOutputStream {
	private RmiRegistry rmiRegistry;
	private Class<?> rootType = null;

	public RmiObjectOutputStream(OutputStream outputStream, RmiRegistry rmiRegistry) throws IOException {
		super(outputStream);
		this.rmiRegistry = rmiRegistry;
		this.enableReplaceObject(true);
	}

	public RmiRegistry getObjectContext() {
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
	private boolean mustRemotize(Object obj) {
		Class<?> objClass = obj.getClass();
		if (objClass.isArray()) {
			Class<?> type = objClass.getComponentType();
			int len = Array.getLength(obj);
			for (int i = 0; i < len; i++) {
				Object value = Array.get(obj, i);
				if (value != null && (rmiRegistry.isRemote(type) || (value instanceof Remote && type.isInterface())
						|| (rmiRegistry.getRemoteObjectId(value) != null && type.isInterface())))
					return true;
			}
		} else if (obj instanceof Serializable) {

			try {

				Field[] fields = objClass.getDeclaredFields();

				for (Field field : fields) {
					int modifiers = field.getModifiers();
					if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers)) {
						field.setAccessible(true);
						Class<?> type = field.getType();
						Object value = field.get(obj);
						if (value != null
								&& (rmiRegistry.isRemote(type) || (value instanceof Remote && type.isInterface())
										|| (rmiRegistry.getRemoteObjectId(value) != null && type.isInterface())))
							return true;
					}
				}
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
			for (int i = 0; i < len; i++) {
				Object value = Array.get(obj, i);
				value = remotize(value, type);
				Array.set(newArray, i, value);
			}
			return newArray;
		} else if (obj instanceof Serializable) {

			try {
				Constructor<?> defaultConstructor = objClass.getConstructor();
				defaultConstructor.setAccessible(true);
				Object newObj = defaultConstructor.newInstance();

				Field[] fields = objClass.getDeclaredFields();

				for (Field field : fields) {
					int modifiers = field.getModifiers();
					if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers)) {
						field.setAccessible(true);
						Object value = field.get(obj);
						value = remotize(value, field.getType());
						field.set(newObj, value);
					}
				}
				return newObj;
			} catch (IllegalAccessException e) {
				throw new WriteAbortedException(
						"No-arg constructor is not accessible in the class " + objClass.getName(), e);
			} catch (SecurityException e) {
				throw new WriteAbortedException(
						"No-arg constructor is not accessible in the class " + objClass.getName(), e);
			} catch (NoSuchMethodException e) {
				throw new WriteAbortedException("No-arg constructor not present in the class " + objClass.getName(), e);
			} catch (InvocationTargetException e) {
				throw new WriteAbortedException("No-arg constructor thrown an exception", e);
			} catch (InstantiationException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			}
		}
		return obj;
	}

	private InvocationMessage remotize(InvocationMessage handle) throws UnknownHostException, IOException {
		if (handle.parameters == null)
			return handle;
		if (handle.parameters.length != handle.parameterTypes.length)
			return handle;
		Object[] parameters = new Object[handle.parameters.length];
		System.arraycopy(handle.parameters, 0, parameters, 0, parameters.length);

		for (int i = 0; i < parameters.length; i++) {
			if (handle.parameterTypes[i] == null || !handle.parameterTypes[i].isInstance(parameters[i]))
				return handle;
			parameters[i] = remotize(parameters[i], handle.parameterTypes[i]);
		}

		InvocationMessage newHandle = new InvocationMessage(handle.id, handle.objectId, handle.method,
				handle.parameterTypes, parameters);
		return newHandle;
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
		if (obj != null && (rmiRegistry.isRemote(formalType) || (obj instanceof Remote && formalType.isInterface())
				|| (rmiRegistry.getRemoteObjectId(obj) != null && formalType.isInterface()))) {

			String objectId = rmiRegistry.publish(obj);

			Object stub = Proxy.newProxyInstance(formalType.getClassLoader(), new Class<?>[] { formalType },
					new ReferenceInvocationHandler(objectId));
			return stub;

		}

		return obj;
	}

	@Override
	protected Object replaceObject(Object obj) throws IOException {
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

		if (mustRemotize(obj))
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
