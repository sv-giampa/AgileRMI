package coarsersi;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

/**
 * Defines a simple class that accepts new TCP connections over a port of the
 * local machine and automatically creates and manages the object peers to
 * simply export services. It uses a single {@link ObjectRegistry} shared among
 * all the created object peers to export the specified services.
 * 
 * @author Salvatore Giampa'
 *
 */
public class ObjectServer {

	// the registry of exported objects
	private ObjectRegistry registry = new ObjectRegistry();

	// the server socket used from last call to the start() method
	private ServerSocket serverSocket;

	// the reference to the main thread
	private Thread listener;

	// the peer currently online
	private Set<ObjectPeer> peers = new HashSet<>();

	/**
	 * Creates a new Object server with a new empty object registry
	 */
	public ObjectServer() {
		this(new ObjectRegistry());
	}

	/**
	 * Creates a new Object server, with the specified registry
	 * 
	 * @param registry the registry that must be used by the server
	 */
	public ObjectServer(ObjectRegistry registry) {
		this.registry = registry;
		registry.attachFailureObserver(failureObserver);
	}

	/**
	 * Gets the {@link ObjectRegistry} used by this server
	 * 
	 * @return the {@link ObjectRegistry} of this server
	 */
	public ObjectRegistry getRegistry() {
		return registry;
	}

	/**
	 * Start the server on the selected port
	 * 
	 * @param port the port to start the server on
	 * @throws IOException if I/O errors occur
	 */
	public synchronized void start(int port) throws IOException {
		serverSocket = new ServerSocket(port);
		listener = new Thread(accepter);
		listener.setDaemon(false);
		listener.start();
	}

	/**
	 * Stop the server
	 */
	public synchronized void stop() {
		listener.interrupt();
		try {
			serverSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		for (ObjectPeer peer : peers)
			peer.dispose();

		peers.clear();
	}

	/**
	 * Defines the main thread that accepts new incoming connections and creates
	 * {@link ObjectPeer} objects
	 */
	private Runnable accepter = new Runnable() {
		public void run() {
			while (!listener.isInterrupted())
				try {
					Socket socket = serverSocket.accept();
					ObjectPeer objectPeer = new ObjectPeer(socket, registry);
					peers.add(objectPeer);
				} catch (IOException e) {
					e.printStackTrace();
				}
		};
	};

	/**
	 * Defines the {@link FailureObserver} used to manage the peer which closed the
	 * connection
	 */
	private FailureObserver failureObserver = new FailureObserver() {
		@Override
		public void failure(ObjectPeer objectPeer, Exception exception) {
			peers.remove(objectPeer);
		}
	};

}
