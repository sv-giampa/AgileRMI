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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;

import agilermi.configuration.StubRetriever;

/**
 * This class extends the standard {@link ObjectInputStream} to give an object
 * context to the deserializing {@link RemoteInvocationHandler} instances. This
 * class allows to implement the automatic remote referencing mechanism, that
 * replaces all the remote object references with their remote stubs, when they
 * are sent on the network. Its counterpart is the {@link RMIObjectInputStream}
 * class.
 * 
 * @author Salvatore Giampa'
 *
 */
final class RMIObjectOutputStream extends ObjectOutputStream {
	private RMIRegistry registry;
	private Class<?> rootType = null;

	public RMIObjectOutputStream(OutputStream outputStream, RMIRegistry registry) throws IOException {
		super(outputStream);
		this.registry = registry;
		this.enableReplaceObject(true);
	}

	public RMIRegistry getRegistry() {
		return registry;
	}

	public void setRootType(Class<?> rootType) {
		this.rootType = rootType;
	}

	@Override
	protected Object replaceObject(Object obj) throws IOException {
		if (obj instanceof StubRetriever)
			return null;
		List<Class<?>> remoteIfs = registry.getRemoteInterfaces(obj);
		if (!remoteIfs.isEmpty()) {
			if (!registry.isAutomaticReferencingEnabled() && registry.getRemoteObjectId(obj) == null) {
				if (!(obj instanceof Serializable)) {
					throw new RuntimeException(String
							.format("Object {%s} is a remote and non-serializable object, "
									+ "but the automatic referencing is disabled on the RMI registry. "
									+ "So it cannot be sent to the remote machine.", obj));
				}
				return obj;
			} else {
				if (Proxy.isProxyClass(obj.getClass())) {
					InvocationHandler ih = Proxy.getInvocationHandler(obj);
					if (ih instanceof RemoteInvocationHandler) {
						RemoteInvocationHandler rih = (RemoteInvocationHandler) ih;
						RMIHandler handler = rih.getHandler();
						if (handler != null && handler.areStubsShareable())
							return obj;
					}
				}
				String objectId = registry.publish(obj);
				return Proxy
						.newProxyInstance(remoteIfs.get(0).getClassLoader(),
								remoteIfs.toArray(remoteIfs.toArray(new Class<?>[remoteIfs.size()])),
								new ReferenceInvocationHandler(objectId));
			}
		} else {
			return obj;
		}
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
