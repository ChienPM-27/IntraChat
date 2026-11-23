package ui;

import model.mock.MockChatService;
import org.junit.jupiter.api.Test;
import ui.frames.LoginFrame;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Regression gate for the single Messenger UI migration. */
class SingleFrontendEntrypointTest {
    @Test
    void onlyMessengerLauncherAndNoObsoleteTechnicalUiClasses() throws Exception {
        assertNotNull(Class.forName("ui.IntraChat"));
        assertNotNull(Class.forName("ui.dialogs.LanConnectionDialog"));
        for (String obsolete : new String[] {
            "vn.intrachat.IntraChatApplication",
            "vn.intrachat.ui.MainWindow",
            "vn.intrachat.ui.NetworkPanel",
            "vn.intrachat.ui.InboundRequestDialog",
            "ui.LiveIntraChat",
            "ui.frames.LiveConnectionFrame"
        }) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(obsolete), obsolete);
        }
        String pom = Files.readString(Path.of("pom.xml"));
        assertFalse(pom.contains("vn.intrachat.IntraChatApplication"));
        assertTrue(pom.contains("<mainClass>ui.IntraChat</mainClass>"));
    }

    @Test
    void demoLoginOffersLanConnectionWithoutChangingDemoCredentials() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        AtomicInteger opens = new AtomicInteger();
        try (MockChatService demo = new MockChatService()) {
            final LoginFrame[] window = new LoginFrame[1];
            try {
                SwingUtilities.invokeAndWait(() -> {
                    window[0] = new LoginFrame(demo, opens::incrementAndGet);
                    window[0].setVisible(true);
                    JButton lan = findButton(window[0], "Chat trong LAN (TCP)");
                    assertNotNull(lan);
                    lan.doClick();
                    assertEquals(1, opens.get());
                });
            } finally {
                SwingUtilities.invokeAndWait(() -> {
                    if (window[0] != null) window[0].dispose();
                });
            }
        }
    }

    private static JButton findButton(Container root, String caption) {
        for (Component component : root.getComponents()) {
            if (component instanceof JButton button && caption.equals(button.getText())) return button;
            if (component instanceof Container nested) {
                JButton found = findButton(nested, caption);
                if (found != null) return found;
            }
        }
        return null;
    }
}
