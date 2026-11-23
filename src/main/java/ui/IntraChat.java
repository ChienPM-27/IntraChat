package ui;

import ui.components.ChatStyle;
import ui.dialogs.LanConnectionDialog;
import vn.intrachat.application.AppInfo;
import vn.intrachat.application.LocalProfile;
import vn.intrachat.application.LiveChatService;

import javax.swing.*;
import java.awt.GraphicsEnvironment;

/** The only application entrypoint: local profile and real TCP LAN, no accounts or mock. */
public final class IntraChat {
    private IntraChat() {}

    public static void main(String[] args) {
        if (args.length == 1 && "--diagnostics".equals(args[0])) {
            System.out.println(AppInfo.NAME + " " + AppInfo.VERSION + " — TCP LAN plaintext (no encryption)");
            return;
        }
        if (args.length != 0) {
            System.err.println("Usage: java -jar intrachat.jar [--diagnostics]");
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("IntraChat requires a desktop.");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            ChatStyle.install();
            // Profile and history initialization include disk I/O; keep them off Swing EDT.
            Thread.ofVirtual().start(() -> {
                LiveChatService live = null;
                try {
                    LocalProfile profile = LocalProfile.loadOrCreate();
                    live = new LiveChatService(profile);
                    LiveChatService initialized = live;
                    SwingUtilities.invokeLater(() -> {
                        try {
                            LanConnectionDialog dialog = new LanConnectionDialog(profile, initialized);
                            dialog.setVisible(true);
                        } catch (RuntimeException failure) {
                            initialized.close();
                            JOptionPane.showMessageDialog(null, "Không thể mở Messenger Live: " + failure.getMessage(),
                                "IntraChat — Lỗi khởi động", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                } catch (Exception error) {
                    if (live != null) live.close();
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                        "Không thể đọc hồ sơ thiết bị: " + error.getMessage(),
                        "IntraChat — Lỗi cấu hình", JOptionPane.ERROR_MESSAGE));
                }
            });
        });
    }
}
