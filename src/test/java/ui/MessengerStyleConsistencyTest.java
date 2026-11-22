package ui;

import model.mock.MockChatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ui.components.ChatStyle;
import ui.dialogs.LanConnectionDialog;
import ui.dialogs.LanDiagnosticsDialog;
import ui.frames.LoginFrame;
import vn.intrachat.domain.PeerIdentity;
import vn.intrachat.security.DeviceCertificateFactory;
import vn.intrachat.security.PinnedPeerStore;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Guard visual style without relying on desktop screenshot privileges. */
class MessengerStyleConsistencyTest {
    @TempDir Path root;

    private static <T extends Component> T find(Container parent, Class<T> kind, String name) {
        for (Component child : parent.getComponents()) {
            if (kind.isInstance(child) && name.equals(child.getName()))
                return kind.cast(child);
            if (child instanceof Container nested) {
                T found = find(nested, kind, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test void sharedMessengerDialogComponentsUseOnePaletteAndStyle() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            ChatStyle.install();
            JButton secondary = ChatStyle.secondary("Hủy");
            assertEquals(ChatStyle.FIELD, secondary.getBackground());
            assertEquals(ChatStyle.INK, secondary.getForeground());
            assertNotNull(secondary.getClientProperty("FlatLaf.style"));
            JTextField field = new JTextField();
            ChatStyle.dialogField(field, "Địa chỉ IP");
            assertEquals(ChatStyle.FIELD, field.getBackground());
            assertNotNull(field.getClientProperty("FlatLaf.style"));
            JPanel card = ChatStyle.dialogCard(new BorderLayout());
            assertEquals(Color.WHITE, card.getBackground());
            assertNotNull(card.getBorder());
        });
    }

    @Test void liveNetworkAndDiagnosticDialogsFollowMessengerCardsAndPrimaryActions() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        var identity = DeviceCertificateFactory.create(UUID.randomUUID());
        try (var demo = new MockChatService()) {
            final LoginFrame[] login = new LoginFrame[1];
            final LanConnectionDialog[] live = new LanConnectionDialog[1];
            final LanDiagnosticsDialog[] diag = new LanDiagnosticsDialog[1];
            try {
                SwingUtilities.invokeAndWait(() -> {
                    ChatStyle.install();
                    login[0] = new LoginFrame(demo, () -> {});
                    live[0] = new LanConnectionDialog(login[0], identity,
                        new PinnedPeerStore(root.resolve("peers.bin")));
                    diag[0] = new LanDiagnosticsDialog(live[0],
                        new PeerIdentity(UUID.randomUUID(), "Test diagnostics"));

                    assertNotNull(find(live[0], JPanel.class, "m2-listen-card"));
                    assertNotNull(find(live[0], JPanel.class, "m2-connect-card"));
                    assertNotNull(find(live[0], JTextField.class, "m2-listen-ip"));
                    assertNotNull(find(live[0], JTextField.class, "m2-connect-ip"));
                    JButton start = find(live[0], JButton.class, "m2-listen");
                    JButton connect = find(live[0], JButton.class, "m2-connect");
                    JButton tools = find(live[0], JButton.class, "open-m1-diagnostics");
                    assertNotNull(start);
                    assertNotNull(connect);
                    assertNotNull(tools);
                    assertEquals(ChatStyle.BLUE, start.getBackground());
                    assertEquals(ChatStyle.BLUE, connect.getBackground());
                    assertEquals(ChatStyle.FIELD, tools.getBackground());

                    assertNotNull(find(diag[0], JPanel.class, "m1-listen-card"));
                    assertNotNull(find(diag[0], JPanel.class, "m1-connect-card"));
                    assertNotNull(find(diag[0], JPanel.class, "m1-actions-card"));
                    JButton m1Connect = find(diag[0], JButton.class, "m1-connect");
                    assertNotNull(m1Connect);
                    assertEquals(ChatStyle.BLUE, m1Connect.getBackground());
                    assertNotNull(find(diag[0], JLabel.class, "m1-status"));
                });
            } finally {
                SwingUtilities.invokeAndWait(() -> {
                    if (diag[0] != null) diag[0].dispose();
                    if (live[0] != null) live[0].dispose();
                    if (login[0] != null) login[0].dispose();
                });
            }
        }
    }
    @Test void loginAndGroupDialogsReuseMessengerActions() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var demo = new MockChatService()) {
            final LoginFrame[] login = new LoginFrame[1];
            final ui.dialogs.CreateGroupDialog[] group = new ui.dialogs.CreateGroupDialog[1];
            try {
                SwingUtilities.invokeAndWait(() -> {
                    ChatStyle.install();
                    login[0] = new LoginFrame(demo, () -> {});
                    JButton entry = find(login[0], JButton.class, "live-lan-entry");
                    assertNotNull(entry);
                    assertEquals(ChatStyle.FIELD, entry.getBackground());
                    assertEquals(ChatStyle.BLUE, entry.getForeground());
                    group[0] = new ui.dialogs.CreateGroupDialog(login[0],
                        java.util.List.of(new model.ChatUser("p", "Bạn thử nghiệm", true)));
                    JButton create = find(group[0], JButton.class, "group-create");
                    JButton cancel = find(group[0], JButton.class, "group-cancel");
                    assertNotNull(create);
                    assertNotNull(cancel);
                    assertEquals(ChatStyle.BLUE, create.getBackground());
                    assertEquals(ChatStyle.FIELD, cancel.getBackground());
                });
            } finally {
                SwingUtilities.invokeAndWait(() -> {
                    if (group[0] != null) group[0].dispose();
                    if (login[0] != null) login[0].dispose();
                });
            }
        }
    }

}