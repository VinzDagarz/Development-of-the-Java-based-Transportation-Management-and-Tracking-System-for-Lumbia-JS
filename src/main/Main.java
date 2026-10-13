package main;

import gui.UserLogin;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {
    public static void main(String[] args) {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {
            // The application can still run with the default Swing look and feel.
        }

        SwingUtilities.invokeLater(() -> {
            UserLogin frame = new UserLogin();
            frame.setVisible(true);
        });
    }
}
