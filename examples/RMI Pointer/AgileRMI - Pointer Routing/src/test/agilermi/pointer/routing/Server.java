package test.agilermi.pointer.routing;

import java.io.IOException;

import agilermi.core.RMIRegistry;

public class Server {
	private static class MyService implements Service {

		@Override
		public Service getService() {
			return null;
		}

		@Override
		public void setService(Service service) {
			return;
		}

		@Override
		public void useThisService() {
			System.out.println(System.currentTimeMillis() + ": service on the server invoked!");
		}
	}

	public static void main(String[] args) throws IOException, InterruptedException {
		RMIRegistry rMIRegistry = RMIRegistry.builder().build();

		Service service = new MyService();
		// rMIRegistry.publish("service", service);

		Service remoteService = (Service) rMIRegistry.getStub("localhost", 3333, "service");
		remoteService.setService(service);
	}
}
