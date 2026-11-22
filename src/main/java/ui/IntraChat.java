package ui;

import model.mock.MockChatService;
import ui.components.ChatStyle;
import ui.dialogs.LanConnectionDialog;
import ui.frames.LoginFrame;
import vn.intrachat.application.AppInfo;
import vn.intrachat.security.DeviceIdentity;
import vn.intrachat.security.DeviceIdentityStore;
import vn.intrachat.security.PinnedPeerStore;

import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;

/**
 * The only production entrypoint and visual shell.
 * Demo remains isolated from network credentials; Live requires explicit user action.
 */
public final class IntraChat {
    private static boolean openingLan;
    private static LanConnectionDialog lanDialog;

    private IntraChat() { }

    public static void main(String[] args) {
        if (args.length == 1 && "--diagnostics".equals(args[0])) {
            System.out.println(AppInfo.NAME + " " + AppInfo.VERSION + " — " + AppInfo.INITIAL_CONNECTION_LABEL);
            return;
        }
        if (args.length != 0) {
            System.err.println("Usage: java -jar intrachat.jar [--diagnostics]");
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("IntraChat requires a desktop. Use --diagnostics in headless mode.");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            ChatStyle.install();
            final LoginFrame[] launcher = new LoginFrame[1];
            launcher[0] = new LoginFrame(new MockChatService(), () -> openLan(launcher[0]));
            launcher[0].setVisible(true);
        });
    }

    private static void openLan(LoginFrame launcher) {
        if (lanDialog != null && lanDialog.isDisplayable()) {
            lanDialog.setVisible(true);
            lanDialog.toFront();
            return;
        }
        if (openingLan) return;
        openingLan = true;
        Thread.ofVirtual().name("intrachat-live-startup").start(() -> {
            try {
                String localAppData = System.getenv("LOCALAPPDATA");
                if (localAppData == null || localAppData.isBlank()) {
                    throw new IllegalStateException("Windows LOCALAPPDATA is unavailable");
                }
                Path root = Path.of(System.getProperty("intrachat.secureRoot",
                    Path.of(localAppData, "IntraChat", "security").toString()))
                    .toAbsolutePath().normalize();
                DeviceIdentity identity = new DeviceIdentityStore(root.resolve("identity.bin")).loadOrCreate();
                PinnedPeerStore peers = new PinnedPeerStore(root.resolve("peers.bin"));
                SwingUtilities.invokeLater(() -> {
                    openingLan = false;
                    if (!launcher.isDisplayable()) return;
                    lanDialog = new LanConnectionDialog(launcher, identity, peers);
                    lanDialog.setVisible(true);
                });
            } catch (Exception failure) {
                SwingUtilities.invokeLater(() -> {
                    openingLan = false;
                    if (launcher.isDisplayable()) {
                        JOptionPane.showMessageDialog(launcher,
                            "Không thể mở Chat LAN: không đọc được danh tính bảo vệ Windows. "
                            + "Kiểm tra cấu hình và quyền truy cập. (" + failure.getClass().getSimpleName() + ")",
                            "IntraChat — Lỗi bảo mật", JOptionPane.ERROR_MESSAGE);
                    }
                });
            }
        });
    }
}
