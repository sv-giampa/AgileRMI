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
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import agilermi.codemobility.ClassLoaderFactory;

/**
 * This class extends the standard {@link ObjectInputStream} and it gives an RMI
 * context to the deserialized {@link RemoteInvocationHandler} instances. This
 * class is the left ventricle of the heart of the deep remote referencing
 * mechanism, that replaces all the remote object references with their remote
 * stub, when they are sent on the network. Its counterpart is the
 * {@link RmiObjectInputStream} class.
 * 
 * @author Salvatore Giampa'
 *
 */
public final class RmiObjectInputStream extends ObjectInputStream {
	private String remoteAddress;
	private int remotePort;
	private RmiRegistry rmiRegistry;
	private RmiClassLoader rmiClassLoader;
	// private Set<URL> remoteCodebases = new HashSet<>();
	private Map<URL, ClassLoader> remoteCodebases = new HashMap<>();

	public RmiObjectInputStream(InputStream inputStream, RmiRegistry rmiRegistry, InetSocketAddress address,
			ClassLoaderFactory classLoaderFactory) throws IOException {
		super(inputStream);
		this.rmiRegistry = rmiRegistry;
		this.remoteAddress = address.getHostString();
		this.remotePort = address.getPort();
		this.rmiClassLoader = rmiRegistry.getRmiClassLoader();
		this.enableResolveObject(true);
	}

	public RmiClassLoader getRmiClassLoader() {
		return rmiClassLoader;
	}

	public RmiRegistry getRmiRegistry() {
		return rmiRegistry;
	}

	public String getRemoteAddress() {
		return remoteAddress;
	}

	public int getRemotePort() {
		return remotePort;
	}

	synchronized void setRemoteCodebases(Set<URL> urls) {
		System.gc(); // try to garbage collect old classes and codebases
		remoteCodebases.keySet().retainAll(urls);
		for (URL url : urls)
			remoteCodebases.put(url, null);
	}

	@Override
	protected synchronized Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {

		try {
			return super.resolveClass(desc);
		} catch (Exception e) {
		}

		try {
			return rmiClassLoader.findClass(desc.getName());
		} catch (Exception e) {
		}

		for (URL codebase : remoteCodebases.keySet()) {
			try {
				// System.out.printf("Trying codebase %s to load class %s\n", codebase,
				// desc.getName());
				ClassLoader classLoader = remoteCodebases.get(codebase);
				if (classLoader == null) {
					classLoader = new WrapperClassLoader(rmiRegistry.getClassLoaderFactory(), codebase);
					remoteCodebases.put(codebase, classLoader);
				}
				Class<?> cls = classLoader.loadClass(desc.getName());
				rmiClassLoader.addActiveCodebase(codebase, classLoader);
				return cls;
			} catch (Exception e) {
				// System.out.printf("Codebase %s cannot be used to load class %s\n%s\n",
				// codebase, desc.getName(),
				// e.toString());
			}
		}
		return super.resolveClass(desc);
	}

	@Override
	protected Object resolveObject(Object obj) throws IOException {

		if (Proxy.isProxyClass(obj.getClass())) {
			InvocationHandler ih = Proxy.getInvocationHandler(obj);

			if (ih instanceof RemoteInvocationHandler) {
				RemoteInvocationHandler sih = (RemoteInvocationHandler) ih;
				if (sih.remoteRegistryKey.equals(rmiRegistry.registryKey)) {
					Skeleton skeleton = rmiRegistry.getSkeleton(sih.getObjectId());
					if (skeleton != null)
						return skeleton.getObject();
				}
			}

			if (ih instanceof ReferenceInvocationHandler) {
				ReferenceInvocationHandler lih = (ReferenceInvocationHandler) ih;
				Class<?>[] interfaces = obj.getClass().getInterfaces();
				Object found = rmiRegistry.getStub(remoteAddress, remotePort, lih.getObjectId(), interfaces);
				if (found != null)
					obj = found;
			}
		}

		return obj;
	}

	@Override
	protected void finalize() throws Throwable {
		reset();
		super.finalize();
	}

}
