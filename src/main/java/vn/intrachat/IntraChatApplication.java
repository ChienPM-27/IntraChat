package vn.intrachat;

import vn.intrachat.application.AppInfo;
import vn.intrachat.ui.MainWindow;
import javax.swing.*;
import java.awt.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/** The single application entrypoint. */
public final class IntraChatApplication {
    private static final Logger LOG = Logger.getLogger(IntraChatApplication.class.getName());

    private IntraChatApplication() {}

    public static void main(String[] args) {
        if (args.length == 1 && "--diagnostics".equals(args[0])) {
            System.out.println(AppInfo.NAME + " " + AppInfo.VERSION + " — " + AppInfo.INITIAL_CONNECTION_LABEL);
            return;
        }
        if (args.length != 0) {
            System.err.println("Usage: java -jar intrachat.jar [--diagnostics]");
            System.exit(2);
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("IntraChat requires a Windows desktop session. Use --diagnostics for CLI verification.");
            System.exit(1);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                MainWindow window = new MainWindow();
                window.setVisible(true);
            } catch (RuntimeException ex) {
                LOG.log(Level.SEVERE, "Unable to start IntraChat window", ex);
                System.exit(1);
            }
        });
    }
}
