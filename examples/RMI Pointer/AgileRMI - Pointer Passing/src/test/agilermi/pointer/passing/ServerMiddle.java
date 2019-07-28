package test.agilermi.pointer.passing;

import java.io.IOException;

import agilermi.core.RMIRegistry;

public class ServerMiddle {

	private static class MyService implements Service {
		Service service;

		public MyService(Service service) {
			this.service = service;
		}

		@Override
		public Service service() {
			return service;
		}
	}

	public static void main(String[] args) throws IOException, InterruptedException {
		RMIRegistry rMIRegistry = RMIRegistry.builder().build();
		rMIRegistry.enableListener(3333, false);
		Service remoteService = (Service) rMIRegistry.getStub("localhost", 2222, "service");
		Service service = new MyService(remoteService);
		rMIRegistry.publish("service", service);
	}
}
