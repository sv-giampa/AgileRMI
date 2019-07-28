package remotegui;

import java.io.IOException;
import java.net.URL;

import agilermi.codemobility.BasicCodeServer;
import agilermi.core.RMIRegistry;

public class BootGUIServer {
	public static void main(String[] args) throws IOException {
		System.out.println("Booting server...");
		BasicCodeServer.create().listen(80);
		RMIRegistry registry = RMIRegistry.builder().addCodebase(new URL("http://localhost:80/bin/")).build();
		registry.enableListener(1099, false);
		GUIServer server = new GUIServerImpl();
		registry.publish("server", server);
	}
}
