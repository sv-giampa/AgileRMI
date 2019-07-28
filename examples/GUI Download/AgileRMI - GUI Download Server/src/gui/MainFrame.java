package gui;

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.Serializable;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import agilermi.utility.logging.RMILogger;

public class MainFrame extends JFrame {
	private static final long serialVersionUID = 4490460023427712758L;

	private RMILogger logger = RMILogger.get(MainFrame.class);

	private JPanel contentPane;
	private final JPanel panel = new JPanel();
	private final JButton btnNewButton = new JButton("Stampa");

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					MainFrame frame = new MainFrame();
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the frame.
	 */
	public MainFrame() {
		initGUI();
	}

	private void initGUI() {
		setTitle("GUI dal server");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 451, 132);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		contentPane.setLayout(new BorderLayout(0, 0));
		setContentPane(contentPane);

		contentPane.add(panel, BorderLayout.CENTER);
		btnNewButton.addActionListener(new BtnNewButtonActionListener());

		panel.add(btnNewButton);
	}

	private class BtnNewButtonActionListener implements ActionListener, Serializable {
		private static final long serialVersionUID = -7283751268115124246L;

		@Override
		public void actionPerformed(ActionEvent e) {
			System.out.println("Button pressed!");
			logger.log(this.getClass().getName(), "Button pressed on the client!");
		}
	}
}
