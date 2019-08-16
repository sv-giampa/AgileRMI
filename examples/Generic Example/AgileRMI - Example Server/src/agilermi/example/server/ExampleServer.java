package agilermi.example.server;

import java.io.IOException;

import agilermi.core.RMIRegistry;
import agilermi.example.service.Service;

public class ExampleServer {

	public static void main(String[] args) throws IOException, InterruptedException {
		System.out.println("Starting Example Server.");

		// rmi registry creation
		RMIRegistry registry = RMIRegistry.builder().build();

		// remote objects creation
		Service service = new ServiceImpl();

		// remote objects publishing
		registry.publish("service", service);

		// starts the object server listener
		registry.enableListener(3031, false);

		System.out.println("Example Server started.");
	}

}
