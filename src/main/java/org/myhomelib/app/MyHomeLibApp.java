package org.myhomelib.app;

import org.myhomelib.ui.MainFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.nio.file.Path;

public final class MyHomeLibApp {
    private MyHomeLibApp() {
    }

    public static void main(String[] args) {
        // Гарантуємо, що будь-яка помилка в потоці Swing (EDT) буде повністю роздрукована в консоль
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.err.println("=== CRITICAL EMERGENCY STACK TRACE ===");
            throwable.printStackTrace(System.err);
            System.err.println("=======================================");
        });

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