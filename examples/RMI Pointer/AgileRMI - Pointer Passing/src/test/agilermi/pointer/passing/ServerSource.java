package test.agilermi.pointer.passing;

import java.io.IOException;

import agilermi.core.RMIRegistry;

public class ServerSource {
	private static class MyService implements Service {
		@Override
		public Service service() {
			System.out.println("service called!");
			return null;
		}
	}

	public static void main(String[] args) throws IOException {
		RMIRegistry rMIRegistry = RMIRegistry.builder().build();
		rMIRegistry.enableListener(2222, false);

		Service service = new MyService();
		rMIRegistry.publish("service", service);
	}
}
