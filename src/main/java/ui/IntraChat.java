package ui;

import model.mock.MockChatService;
import ui.components.ChatStyle;
import ui.frames.LoginFrame;

import javax.swing.SwingUtilities;

public final class IntraChat {
    private IntraChat() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ChatStyle.install();
            new LoginFrame(new MockChatService()).setVisible(true);
        });
    }
}
