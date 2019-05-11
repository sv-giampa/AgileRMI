package agilermi.example.server;

import java.io.IOException;

import agilermi.core.RMIRegistry;
import agilermi.example.service.Service;

public class AgileRmiServer {

	public static void main(String[] args) throws IOException, InterruptedException {
		System.out.println("Server started.");

		// rmi registry creation
		RMIRegistry rMIRegistry = RMIRegistry.builder().build();

		// remote objects creation
		Service service = new ServiceImpl();

		// remote objects publishing
		rMIRegistry.publish("service", service);

		rMIRegistry.enableRemoteException(false);

		rMIRegistry.enableListener(3031, true);

		while (true) {
			Thread.sleep(5000);
			System.gc();
		}
	}

}
