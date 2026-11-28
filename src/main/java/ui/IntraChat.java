package ui;

import ui.components.ChatStyle;
import ui.dialogs.LanConnectionDialog;
import vn.intrachat.application.AppInfo;
import vn.intrachat.application.LocalProfile;
import vn.intrachat.application.LiveChatService;
import vn.intrachat.transport.LocalIpv4;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.GraphicsEnvironment;
import java.util.List;

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
            String explicitDataDir = System.getProperty("intrachat.dataDir");
            String explicitUser = System.getProperty("intrachat.user");
            String chosenUser = null;
            if ((explicitDataDir == null || explicitDataDir.isBlank())
                    && (explicitUser == null || explicitUser.isBlank())) {
                chosenUser = promptUserName();
                if (chosenUser == null) {
                    return;
                }
            } else if (explicitUser != null && !explicitUser.isBlank()) {
                chosenUser = explicitUser.trim();
            }
            final String userName = chosenUser;
            // Profile and history initialization include disk I/O; keep them off Swing EDT.
            Thread.ofVirtual().start(() -> {
                LiveChatService live = null;
                try {
                    LocalProfile profile = (explicitDataDir != null && !explicitDataDir.isBlank())
                        ? LocalProfile.loadOrCreate()
                        : LocalProfile.loadOrCreate(LocalProfile.userDataDir(userName), userName);
                    live = new LiveChatService(profile);
                    LiveChatService initialized = live;
                    var localAddresses = LocalIpv4.detect();
                    SwingUtilities.invokeLater(() -> {
                        try {
                            LanConnectionDialog dialog = new LanConnectionDialog(profile, initialized, localAddresses);
                            dialog.setVisible(true);
                            dialog.startListening();
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

    private static String promptUserName() {
        List<String> existing = LocalProfile.listExistingUsers(LocalProfile.defaultDataRoot());
        JComboBox<String> input = new JComboBox<>(existing.toArray(String[]::new));
        input.setEditable(true);
        if (existing.isEmpty()) {
            String osUser = System.getProperty("user.name", "").trim();
            if (!osUser.isEmpty()) {
                input.setSelectedItem(osUser);
            }
        }
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel("Nhập hoặc chọn tên người dùng (lưu riêng trong data/<tên>):"), BorderLayout.NORTH);
        panel.add(input, BorderLayout.CENTER);
        while (true) {
            int choice = JOptionPane.showConfirmDialog(null, panel,
                "IntraChat — Chọn người dùng", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return null;
            }
            Object raw = input.getEditor().getItem();
            String name = raw == null ? "" : raw.toString().trim();
            if (!name.isEmpty() && name.length() <= 80) {
                return name;
            }
            JOptionPane.showMessageDialog(null, "Tên người dùng phải từ 1 đến 80 ký tự.",
                "IntraChat — Tên không hợp lệ", JOptionPane.WARNING_MESSAGE);
        }
    }
}
