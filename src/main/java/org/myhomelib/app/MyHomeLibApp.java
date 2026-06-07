package org.myhomelib.app;

import org.myhomelib.ui.MainFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.nio.file.Path;

public final class MyHomeLibApp {
    private MyHomeLibApp() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Keep Swing's default look and feel if the platform one is unavailable.
            }

            Path dbPath = args.length > 0 ? Path.of(args[0]) : Path.of("myhomelib-java.db");
            MainFrame frame = new MainFrame(dbPath);
            frame.setVisible(true);
        });
    }
}
