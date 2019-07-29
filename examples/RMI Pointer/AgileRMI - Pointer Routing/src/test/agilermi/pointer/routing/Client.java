package test.agilermi.pointer.routing;

import java.io.IOException;

import agilermi.core.RMIRegistry;

public class Client {

	public static void main(String[] args) throws IOException, InterruptedException {
		RMIRegistry rMIRegistry = RMIRegistry.builder().build();

		Service routingService = (Service) rMIRegistry.getStub("localhost", 3333, "tunnel");
		Service routedService = routingService.getService();

		routedService.useThisService();
		Thread.sleep(5000);
		routingService.useThisService();
	}
}
