package agilermi.faulttolerance;

import agilermi.core.RMIRegistry;
import agilermi.exception.RemoteException;

public class BootFaultSimulationClient {
	public static void main(String[] args) throws InterruptedException {
		RMIRegistry registry = RMIRegistry.builder().setHandlerFaultMaxLife(2000).suppressAllInvocationFaults(true)
				.build();

		registry.setLatencyTime(5000);

		Server server = (Server) registry.getStub("localhost", 10101, "server", Server.class);

		while (true) {
			try {
				double number = server.nextNumber();
				System.out.println("next number: " + number);
			} catch (RemoteException e) {
				System.out.println("connection fault!");
			}
			Thread.sleep(1000);
		}

	}
}
