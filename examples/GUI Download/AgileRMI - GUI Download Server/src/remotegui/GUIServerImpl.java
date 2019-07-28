package remotegui;

import javax.swing.JFrame;

import gui.MainFrame;

public class GUIServerImpl implements GUIServer {

	@Override
	public JFrame getGUI() {
		return new MainFrame();
	}

}
