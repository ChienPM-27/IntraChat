package ui;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

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
    void legacyDemoClassesAreAbsent() {
        for (String old : new String[] {
            "ui.frames.LoginFrame", "model.mock.MockChatService", "model.mock.DemoMedia"
        }) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(old), old);
        }
    }

    @Test
    void diagnosticsRemainHeadless() {
        assertDoesNotThrow(() -> IntraChat.main(new String[]{"--diagnostics"}));
    }
}
