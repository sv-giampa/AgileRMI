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

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import agilermi.configuration.RMIFaultHandler;
import agilermi.configuration.StubRetriever;

/**
 * This class extends the standard {@link ObjectInputStream} and it gives an RMI
 * context to the deserialized {@link RemoteInvocationHandler} instances. This
 * class allows to implement of the automatic remote referencing mechanism, that
 * replaces all the remote object references with their remote stubs, when they
 * are sent on the network. Its counterpart is the {@link RMIObjectInputStream}
 * class.
 * 
 * @author Salvatore Giampa'
 *
 */
public final class RMIObjectInputStream extends ObjectInputStream {
	private String remoteAddress;
	private int remotePort;
	private RMIRegistry registry;
	private RMIHandler rmiHandler;
	private RMIClassLoader rmiClassLoader;
	private Map<URL, WeakReference<ClassLoader>> remoteCodebases = new HashMap<>();

	/**
	 * Initializes an {@link RMIObjectInputStream} over an handler to communicate
	 * with a remote machine.
	 * 
	 * @param handlerInputStream the {@link InputStream} the stubs will be read
	 *                           from.
	 * @param handler            the {@link RMIHandler} that represents the remote
	 *                           machine.
	 * @throws IOException if an I/O error occurs
	 */
	RMIObjectInputStream(InputStream handlerInputStream, RMIHandler handler) throws IOException {
		super(handlerInputStream);
		this.rmiHandler = handler;
		this.registry = handler.getRMIRegistry();
		this.remoteAddress = handler.getInetSocketAddress().getHostString();
		this.remotePort = handler.getInetSocketAddress().getPort();
		this.rmiClassLoader = registry.getRmiClassLoader();
		this.enableResolveObject(true);
	}

	/**
	 * Initializes an {@link RMIObjectInputStream} over the given
	 * {@link InputStream} with the specified registry. This allows to read
	 * persisted object stubs providing them the information needed to become
	 * active.
	 * 
	 * @param inputStream the {@link InputStream} the stubs will be read from.
	 * @param registry    the registry to provide to the persisted stubs.
	 * @throws IOException if an I/O error occurs
	 */
	public RMIObjectInputStream(InputStream inputStream, RMIRegistry registry) throws IOException {
		super(inputStream);
		this.registry = registry;
		this.remoteAddress = "localhost";
		this.remotePort = 0;
		this.rmiClassLoader = registry.getRmiClassLoader();
		this.enableResolveObject(false);
	}

	public RMIClassLoader getRmiClassLoader() {
		return rmiClassLoader;
	}

	public RMIRegistry getRmiRegistry() {
		return registry;
	}

	public String getRemoteAddress() {
		return remoteAddress;
	}

	public int getRemotePort() {
		return remotePort;
	}

	synchronized void setRemoteCodebases(Set<URL> urls) {
		remoteCodebases.keySet().retainAll(urls);
		for (URL url : urls)
			if (!remoteCodebases.containsKey(url))
				remoteCodebases.put(url, null);
		System.gc(); // try to garbage collect old classes and codebases
	}

	@Override
	protected synchronized Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
		if (desc.getName().equals(StubRetriever.class.getName())) { return StubRetriever.class; }
		// tries to solve class through the system class-loaders
		try {
			return super.resolveClass(desc);
		} catch (Exception e) {}

		// tries to solve class through a currently active codebase
		try {
			return rmiClassLoader.findClass(desc.getName());
		} catch (Exception e) {}

		// tries to load class by activating a new codebase
		for (URL codebase : remoteCodebases.keySet()) {
			try {
				// System.out.printf("Trying codebase %s to load class %s\n", codebase,
				// desc.getName());
				ClassLoader classLoader = null;
				WeakReference<ClassLoader> wr = remoteCodebases.get(codebase);
				if (wr != null)
					classLoader = wr.get();

				if (classLoader == null) {
					classLoader = new WrapperClassLoader(registry.getClassLoaderFactory(), codebase);
					remoteCodebases.put(codebase, new WeakReference<ClassLoader>(classLoader));
				}
				Class<?> cls = classLoader.loadClass(desc.getName());
				rmiClassLoader.addActiveCodebase(codebase, classLoader);
				return cls;
			} catch (Exception e) {
				if (Debug.RMI_INPUT_STREAM)
					System.out
							.printf("Codebase %s cannot be used to load class %s\n%s\n", codebase, desc.getName(),
									e.toString());
			}
		}
		try {
			return super.resolveClass(desc);
		} catch (ClassNotFoundException e) {
			throw new ClassNotFoundException(
					"No codebase can be used to load the class. Check the codebase settings on the registry.");
		}
	}

	@Override
	protected Object resolveObject(Object obj) throws IOException {
		if (obj instanceof StubRetriever)
			return obj;

		if (Proxy.isProxyClass(obj.getClass())) {
			InvocationHandler ih = Proxy.getInvocationHandler(obj);

			if (ih instanceof RemoteInvocationHandler) {
				RemoteInvocationHandler sih = (RemoteInvocationHandler) ih;
				if (registry.getRegistryKey().equals(sih.remoteRegistryKey)) {
					Skeleton skeleton = registry.getSkeleton(sih.getObjectId());
					if (skeleton != null)
						return skeleton.getRemoteObject();
				}
			}

			if (ih instanceof ReferenceInvocationHandler) {
				ReferenceInvocationHandler lih = (ReferenceInvocationHandler) ih;
				Class<?>[] interfaces = obj.getClass().getInterfaces();
				Object found = rmiHandler.getStub(lih.getObjectId(), interfaces);
				if (found != null)
					return found;
			}
		}

		replaceFields(obj);
		return obj;
	}

	private void replaceFields(Object obj) throws IOException {
		Class<?> cls = obj.getClass();
		try {
			do {
				for (Field field : cls.getDeclaredFields()) {
					if (field.getType() == StubRetriever.class) {
						boolean accessible = field.isAccessible();
						field.setAccessible(true);
						field.set(obj, registry.getStubRetriever());
						field.setAccessible(accessible);
					}
					if (field.getType() == RMIFaultHandler.class) {
						boolean accessible = field.isAccessible();
						field.setAccessible(true);
						RMIFaultHandler faultHandler = (RMIFaultHandler) field.get(obj);
						if (faultHandler != null)
							registry.attachFaultHandler(faultHandler);
						field.setAccessible(accessible);
					}
				}
			} while ((cls = cls.getSuperclass()) != null);
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
			throw new IOException(e);
		} catch (IllegalAccessException e) {
			e.printStackTrace();
			throw new IOException(e);
		}
	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
	}

}
