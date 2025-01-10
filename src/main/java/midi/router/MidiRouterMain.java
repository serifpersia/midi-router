package midi.router;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class MidiRouterMain {
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			try {
				UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatDarkLaf());
			} catch (Exception e) {
				e.printStackTrace();
			}
			MidiRouter app = new MidiRouter();
			app.setVisible(true);
		});
	}
}
