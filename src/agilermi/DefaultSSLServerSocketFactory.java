package agilermi;

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
