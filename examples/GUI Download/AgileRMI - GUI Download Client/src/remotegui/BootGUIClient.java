package remotegui;

import java.io.IOException;
import java.net.UnknownHostException;

import javax.swing.JFrame;

import agilermi.core.RMIRegistry;

public class BootGUIClient {

	public static void main(String[] args) throws UnknownHostException, IOException, InterruptedException {
		System.out.println("Booting client...");
		RMIRegistry registry = RMIRegistry.builder().setCodeDownloadingEnabled(true).build();

		// gets reference to the server
		GUIServer server = (GUIServer) registry.getStub("localhost", 1099, "server");

		// gets the GUI
		JFrame frame = server.getGUI();

		frame.setTitle("GUI sul client");
		frame.invalidate();

		// starts the GUI
		frame.setVisible(true);
	}
}
