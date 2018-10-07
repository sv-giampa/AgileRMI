package coarsersi;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Class whose static memebers unify the use of more client object peers. This
 * class contributes to the {@link ObjectPeer} objects transparency, allowing
 * the developer to deal with rmeote objects stubs only. Moreover it exports a
 * method to get the backing object peers. All the created object peers uses the
 * same instance of {@link ObjectRegistry}, allowing a server to access objects
 * published on the client for other servers.
 * 
 * @author Salvatore Giampa'
 *
 */
public final class UnifiedClient {

	private static Object lock = new Object();

	private static ObjectRegistry registry = new ObjectRegistry();

	private static Map<InetSocketAddress, ObjectPeer> peers = Collections.synchronizedMap(new HashMap<>());

	private static FailureObserver failureObserver = (objectPeer, exception) -> {
		synchronized (lock) {
			peers.values().remove(objectPeer);
		}
	};

	static {
		registry.attachFailureObserver(failureObserver);
	}

	/**
	 * Gets the global registry used by ObjectPeer instances created by the unified client
	 */
	public static ObjectRegistry getRegistry() {
		return registry;
	}

	/**
	 * Gets the stub for the specified object identifier on the specified host
	 * respect to the given interface. Similar to the
	 * {@link ObjectPeer#getStub(String, Class)} method, but creates a new
	 * ObjectPeer if necessary to communicate with the specified host.
	 * 
	 * @param address       the host address
	 * @param port          the host port
	 * @param objectId      the remote object identifier
	 * @param stubInterface the stub's interface
	 * @param               <T> type of the stub object
	 * @return the stub object
	 * @throws UnknownHostException if the host address cannot be resolved
	 * @throws IOException          if I/O errors occur
	 */
	public static <T> T getStub(String address, int port, String objectId, Class<T> stubInterface)
			throws UnknownHostException, IOException {
		synchronized (lock) {
			return getObjectPeer(address, port).getStub(objectId, stubInterface);
		}
	}

	/**
	 * Gets the {@link ObjectPeer} object for the specified host. If it has not been
	 * created, creates it.
	 * 
	 * @param address the host address
	 * @param port    the host port
	 * @return the object peer related to the specified host
	 * @throws UnknownHostException if the host address cannot be resolved
	 * @throws IOException          if I/O errors occur
	 */
	public static ObjectPeer getObjectPeer(String address, int port) throws UnknownHostException, IOException {
		synchronized (lock) {
			InetSocketAddress inetAddress = new InetSocketAddress(address, port);
			ObjectPeer peer = peers.get(inetAddress);
			if (peer != null)
				peer = peers.get(inetAddress);
			else {
				peer = ObjectPeer.connect(address, port, registry);
				peers.put(inetAddress, peer);
			}
			return peer;
		}
	}

}
