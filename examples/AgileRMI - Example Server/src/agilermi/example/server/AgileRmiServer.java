package agilermi.example.server;

import java.io.IOException;

import agilermi.RmiRegistry;
import agilermi.example.service.Service;

public class AgileRmiServer {

	public static void main(String[] args) throws IOException, InterruptedException {
		System.out.println("Server started.");

		// rmi registry creation
		RmiRegistry rmiRegistry = new RmiRegistry(3031, false);

		// remote objects creation
		Service service = new ServiceImpl();

		// remote objects publishing
		rmiRegistry.publish("service", service);

		while (true) {
			Thread.sleep(5000);
			System.gc();
		}
	}

}
