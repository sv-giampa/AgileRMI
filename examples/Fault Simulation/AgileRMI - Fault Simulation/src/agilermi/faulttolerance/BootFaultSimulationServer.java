package agilermi.faulttolerance;

import java.io.IOException;

import agilermi.core.RMIRegistry;

public class BootFaultSimulationServer {
	public static void main(String[] args) throws IOException {
		RMIRegistry registry = RMIRegistry.builder().build();
		registry.publish("server", new ServerImpl());
		registry.enableListener(10101, false);
		System.out.println("Server started.");
	}
}
