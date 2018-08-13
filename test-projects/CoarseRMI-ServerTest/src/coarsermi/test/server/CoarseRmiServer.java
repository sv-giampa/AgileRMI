package coarsermi.test.server;
import java.io.IOException;

import coarsermi.ObjectRegistry;
import coarsermi.ObjectServer;
import coarsermi.test.service.TestIF;
import coarsermi.test.service.TestImpl;

public class CoarseRmiServer {

	public static void main(String[] args) throws IOException {
		
		// object server creation
		ObjectServer objectServer = new ObjectServer();
		
		// remote objects creation
		TestIF test = new TestImpl();
		
		// remote objects publishing
		ObjectRegistry registry = objectServer.getRegistry();
		registry.publish("test", test, TestIF.class);
		
		// server start
		objectServer.start(3031);

	}

}
