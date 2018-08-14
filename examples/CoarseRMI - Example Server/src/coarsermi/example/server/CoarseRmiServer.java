package coarsermi.example.server;
import java.io.IOException;

import coarsermi.ObjectRegistry;
import coarsermi.ObjectServer;
import coarsermi.example.service.Service;

public class CoarseRmiServer {

	public static void main(String[] args) throws IOException {
		
		// object server creation
		ObjectServer objectServer = new ObjectServer();
		
		// remote objects creation
		Service service = new ServiceImpl();
		
		// remote objects publishing
		ObjectRegistry registry = objectServer.getRegistry();
		registry.publish("service", service, Service.class);
		
		// server start
		objectServer.start(3031);
	}

}
