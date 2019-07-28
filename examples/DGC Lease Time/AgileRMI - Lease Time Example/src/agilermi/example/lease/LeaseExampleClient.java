package agilermi.example.lease;

import java.io.IOException;
import java.net.UnknownHostException;

import agilermi.core.RMIHandler;
import agilermi.core.RMIRegistry;
import agilermi.exception.RemoteException;

public class LeaseExampleClient {

	public static void main(String[] args)
			throws UnknownHostException, IOException, InterruptedException, RemoteException {

		RMIRegistry registry = RMIRegistry.builder()

				// .setSocketFactories(SSLSocketFactory.getDefault(),SSLServerSocketFactory.getDefault())

				.build();
		Server server = (Server) registry.getStub("localhost", 2000, "server");
		RMIHandler handler = registry.getRMIHandler("localhost", 2000);

		Test test = server.getTest();

		handler.dispose(false);

		test = server.getTest();

		/**
		 * Errore alla prossima riga di codice:
		 * 
		 * agilermi.core.RemoteInvocationHandler invocation success!
		 * agilermi.core.RemoteInvocationHandler invocation error! Exception in thread
		 * "main" java.lang.NullPointerException at
		 * agilermi.example.lease.LeaseExampleClient.main(LeaseExampleClient.java:24)
		 * 
		 */
		test.test();

		Thread.sleep(5000);
		test.test();
	}

}
