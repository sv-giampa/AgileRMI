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
package agilermi.communication;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

/**
 * A standard implementation of {@link SSLServerSocketFactory} that uses one
 * combination of the supported protocols and ciphers.
 * 
 * @author Salvatore Giampa'
 *
 */
public final class DefaultSSLServerSocketFactory extends SSLServerSocketFactory {

	@Override
	public String[] getDefaultCipherSuites() {
		return ((SSLServerSocketFactory) SSLServerSocketFactory.getDefault()).getDefaultCipherSuites();
	}

	@Override
	public String[] getSupportedCipherSuites() {
		return ((SSLServerSocketFactory) SSLServerSocketFactory.getDefault()).getSupportedCipherSuites();
	}

	@Override
	public ServerSocket createServerSocket(int port) throws IOException {
		SSLServerSocket serverSocket = (SSLServerSocket) SSLServerSocketFactory.getDefault().createServerSocket(port);
		socketSetup(serverSocket);
		return serverSocket;
	}

	@Override
	public ServerSocket createServerSocket(int port, int backlog) throws IOException {
		SSLServerSocket serverSocket = (SSLServerSocket) SSLServerSocketFactory.getDefault().createServerSocket(port,
				backlog);
		socketSetup(serverSocket);
		return serverSocket;
	}

	@Override
	public ServerSocket createServerSocket(int port, int backlog, InetAddress ifAddress) throws IOException {
		SSLServerSocket serverSocket = (SSLServerSocket) SSLServerSocketFactory.getDefault().createServerSocket(port,
				backlog, ifAddress);
		socketSetup(serverSocket);
		return serverSocket;
	}

	/**
	 * Sets up the server socket.
	 * 
	 * @param serverSocket the created server socket
	 */
	private void socketSetup(SSLServerSocket serverSocket) {
		serverSocket.setUseClientMode(false);
		serverSocket.setEnabledProtocols(serverSocket.getSupportedProtocols());
		serverSocket.setEnabledCipherSuites(serverSocket.getSupportedCipherSuites());
	}

}
