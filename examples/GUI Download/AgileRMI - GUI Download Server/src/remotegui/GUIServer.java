package remotegui;

import javax.swing.JFrame;

import agilermi.configuration.Remote;

public interface GUIServer extends Remote {
	public JFrame getGUI();
}
