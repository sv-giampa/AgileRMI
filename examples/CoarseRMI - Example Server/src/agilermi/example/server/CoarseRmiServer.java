package agilermi.example.server;
import java.io.IOException;

import agilermi.RmiRegistry;
import agilermi.example.service.Service;

public class CoarseRmiServer {

	public static void main(String[] args) throws IOException {
		System.out.println("Server started.");
		
		// object server creation
		RmiRegistry objectServer = new RmiRegistry(3031, false, null, null);
		
		// remote objects creation
		Service service = new ServiceImpl();
		
		// remote objects publishing
		objectServer.publish("service", service);
	}

}
