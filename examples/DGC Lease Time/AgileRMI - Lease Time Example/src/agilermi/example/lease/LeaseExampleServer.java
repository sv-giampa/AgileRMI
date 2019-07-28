package agilermi.example.lease;

import java.io.IOException;

import agilermi.core.RMIRegistry;

public class LeaseExampleServer {

	public static void main(String[] args) throws IOException {
		RMIRegistry registry = RMIRegistry.builder().setLeaseTime(1000)

				// .setSocketFactories(SSLSocketFactory.getDefault(),SSLServerSocketFactory.getDefault())

				.build();

		Test test = new TestImpl();
		Server server = new ServerImpl(test);

		// registry.publish("test", test);

		registry.publish("server", server);
		registry.enableListener(2000, false);
	}

}
