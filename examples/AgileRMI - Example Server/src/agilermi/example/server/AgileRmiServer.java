package agilermi.example.server;

import java.io.IOException;

import agilermi.core.RmiRegistry;
import agilermi.example.service.Service;

public class AgileRmiServer {

	public static void main(String[] args) throws IOException, InterruptedException {
		System.out.println("Server started.");

		// rmi registry creation
		RmiRegistry rmiRegistry = RmiRegistry.builder().build();

		// remote objects creation
		Service service = new ServiceImpl();

		// remote objects publishing
		rmiRegistry.publish("service", service);

		rmiRegistry.enableRemoteException(false);

		rmiRegistry.enableListener(3031, true);

		while (true) {
			Thread.sleep(5000);
			System.gc();
		}
	}

}
