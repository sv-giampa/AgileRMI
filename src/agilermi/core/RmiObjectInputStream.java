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
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;

/**
 * This class extends the standard {@link ObjectInputStream} to give an object
 * context to the deserializing {@link RemoteInvocationHandler} instances. This
 * class is the left ventricle of the heart of the deep remote referencing
 * mechanism, that replaces all the remote object references with their remote
 * stub, when they are sent on the network. Its counterpart is the
 * {@link RmiObjectInputStream} class.
 * 
 * @author Salvatore Giampa'
 *
 */
class RmiObjectInputStream extends ObjectInputStream {
	private String remoteAddress;
	private int remotePort;
	private RmiRegistry rmiRegistry;

	public RmiObjectInputStream(InputStream inputStream, RmiRegistry rmiRegistry, String remoteAddress, int remotePort)
			throws IOException {
		super(inputStream);
		this.rmiRegistry = rmiRegistry;
		this.remoteAddress = remoteAddress;
		this.remotePort = remotePort;
		this.enableResolveObject(true);
	}

	public RmiObjectInputStream(InputStream inputStream, RmiRegistry rmiRegistry, InetSocketAddress address)
			throws IOException {
		super(inputStream);
		this.rmiRegistry = rmiRegistry;
		this.remoteAddress = address.getHostString();
		this.remotePort = address.getPort();
		this.enableResolveObject(true);
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

	@Override
	protected Object resolveObject(Object obj) throws IOException {

		if (Proxy.isProxyClass(obj.getClass())) {
			InvocationHandler ih = Proxy.getInvocationHandler(obj);

			if (ih instanceof RemoteInvocationHandler) {
				RemoteInvocationHandler sih = (RemoteInvocationHandler) ih;
				if (sih.registryKey.equals(rmiRegistry.registryKey)) {
					System.out.println("remote reference rplaced with local object");
					return rmiRegistry.getRemoteObject(sih.getObjectId());
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

}
