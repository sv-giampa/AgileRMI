package agilermi;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * A standard implementation of {@link SSLSocketFactory} that uses one
 * combination of the supported protocols and ciphers.
 * 
 * @author Salvatore Giampa'
 *
 */
public final class DefaultSSLSocketFactory extends SSLSocketFactory {

	@Override
	public String[] getDefaultCipherSuites() {
		return ((SSLSocketFactory) SSLSocketFactory.getDefault()).getDefaultCipherSuites();
	}

	@Override
	public String[] getSupportedCipherSuites() {
		return ((SSLSocketFactory) SSLSocketFactory.getDefault()).getSupportedCipherSuites();
	}

	@Override
	public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
		SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(s, host, port,
				autoClose);
		socketSetup(ssl);
		return ssl;
	}

	@Override
	public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
		SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(host, port);
		socketSetup(ssl);
		return ssl;
	}

	@Override
	public Socket createSocket(InetAddress host, int port) throws IOException {
		SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(host, port);
		socketSetup(ssl);
		return ssl;
	}

	@Override
	public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
			throws IOException, UnknownHostException {
		SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(host, port,
				localHost, localPort);
		socketSetup(ssl);
		return ssl;
	}

	@Override
	public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
			throws IOException {
		SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(address, port,
				localAddress, localPort);
		socketSetup(ssl);
		return ssl;
	}

	/**
	 * Sets up the socket.
	 * 
	 * @param serverSocket the created server socket
	 */
	private void socketSetup(SSLSocket sslSocket) throws IOException {
		sslSocket.setKeepAlive(true);
		sslSocket.setUseClientMode(true);
		sslSocket.setEnabledProtocols(sslSocket.getSupportedProtocols());
		sslSocket.setEnabledCipherSuites(sslSocket.getSupportedCipherSuites());
		sslSocket.startHandshake();
	}

}
