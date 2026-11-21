package ui.frames;

import model.Conversation;
import model.mock.MockChatService;
import org.junit.jupiter.api.Test;
import ui.components.ChatStyle;
import ui.dialogs.CallDialog;
import ui.dialogs.FriendRequestsDialog;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class DesktopUiTest {
    @Test
    void mockLoginChatLayoutSearchAndComposer() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var mock = new MockChatService()) {
            LoginFrame login = onEdt(() -> {
                ChatStyle.install();
                var frame = new LoginFrame(mock, mock::simulateIncomingCall);
                frame.setVisible(true);
                return frame;
            });
            try {
                onEdt(() -> {
                    capture(login, "login");
                    var inputs = children(login, JTextField.class);
                    inputs.get(0).setText("Người dùng demo");
                    inputs.get(1).setText("demo");
                    login.getRootPane().getDefaultButton().doClick();
                    return null;
                });
                await(() -> chat() != null && children(chat(), JTextPane.class).size() >= 4);
                await(() -> children(chat(), JButton.class).stream().anyMatch(button ->
                        button.isEnabled() && button.getToolTipText() != null && button.getToolTipText().startsWith("Xem ảnh:")));
                onEdt(() -> {
                    var frame = chat();
                    assertEquals(1100, frame.getWidth());
                    assertEquals(720, frame.getHeight());
                    assertNotNull(visibleButton(frame, "Thử gọi đến"));
                    capture(frame, "chat");
                    var search = children(frame, JTextField.class).get(0);
                    var list = children(frame, JList.class).get(0);
                    search.setText("nhom du an");
                    assertEquals(1, list.getModel().getSize());
                    list.setSelectedIndex(0);
                    return null;
                });
                await(() -> hasLabel("3 thành viên") && children(chat(), JProgressBar.class).size() == 1);
                onEdt(() -> {
                    var frame = chat();
                    frame.setSize(frame.getMinimumSize());
                    frame.validate();
                    var history = children(frame, JScrollPane.class).stream().filter(pane ->
                            "Lịch sử tin nhắn".equals(pane.getAccessibleContext().getAccessibleName())).findFirst().orElseThrow();
                    history.getVerticalScrollBar().setValue(history.getVerticalScrollBar().getMaximum());
                    capture(frame, "group-minimum");
                    var input = children(frame, JTextField.class).get(1);
                    input.setText("Kiểm tra giao diện ❤️");
                    input.postActionEvent();
                    return null;
                });
                await(() -> children(chat(), JTextPane.class).stream()
                        .anyMatch(pane -> pane.getText().equals("Kiểm tra giao diện ❤️")));
                assertTrue(onEdt(() -> hasLabel("Đã gửi")));
                SwingUtilities.invokeLater(() -> children(chat(), JButton.class).stream()
                        .filter(button -> "Tạo nhóm mới".equals(button.getToolTipText())).findFirst().orElseThrow().doClick());
                await(() -> groupDialog() != null);
                onEdt(() -> {
                    JDialog dialog = groupDialog();
                    children(dialog, JTextField.class).get(0).setText("Nhóm kiểm thử");
                    children(dialog, JCheckBox.class).forEach(option -> option.setSelected(true));
                    dialog.getRootPane().getDefaultButton().doClick();
                    return null;
                });
                await(() -> hasLabel("Nhóm kiểm thử") && hasLabel("3 thành viên"));
            } finally {
                onEdt(() -> {
                    for (Window window : Window.getWindows()) {
                        if (window instanceof LoginFrame || window instanceof MainChatFrame) {
                            window.dispose();
                        }
                    }
                    return null;
                });
            }
        }
    }

    @Test
    void sendsByIpAndAcceptsTheIncomingInvitation() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var mock = new MockChatService()) {
            var self = mock.signIn("Người dùng demo", "demo".toCharArray()).toCompletableFuture().get(5, TimeUnit.SECONDS);
            MainChatFrame frame = onEdt(() -> {
                ChatStyle.install();
                MainChatFrame window = new MainChatFrame(self, mock);
                window.setSize(window.getMinimumSize());
                window.setVisible(true);
                return window;
            });
            try {
                JButton friends = onEdt(() -> children(frame, JButton.class).stream()
                        .filter(button -> "Kết bạn qua LAN".equals(button.getToolTipText())).findFirst().orElseThrow());
                await(() -> friends.getText().contains("1 lời mời"));
                FriendRequestsDialog dialog = onEdt(() -> {
                    friends.doClick();
                    return friendRequests(frame);
                });
                onEdt(() -> {
                    assertTrue(hasLabel(dialog, "Hải Nam"));
                    capture(dialog, "friend-requests-incoming");
                    JTextField ip = children(dialog, JTextField.class).get(0);
                    ip.setText("intrachat.local");
                    ip.postActionEvent();
                    assertTrue(hasLabel(dialog, "Nhập địa chỉ IPv4 hoặc IPv6 hợp lệ."));
                    ip.setText("192.168.1.23");
                    visibleButton(dialog, "Gửi lời mời").doClick();
                    return null;
                });
                await(() -> hasLabel(dialog, "Chờ chấp nhận"));
                onEdt(() -> {
                    assertFalse(hasConversation(frame, "Khánh Linh"));
                    assertFalse(children(dialog, JButton.class).stream()
                            .anyMatch(button -> button.isShowing() && "Chấp nhận".equals(button.getText())));
                    capture(dialog, "friend-requests-sent");
                    children(dialog, JTabbedPane.class).get(0).setSelectedIndex(0);
                    visibleButton(dialog, "Chấp nhận").doClick();
                    return null;
                });
                await(() -> hasLabel(dialog, "Đã chấp nhận") && hasConversation(frame, "Hải Nam")
                        && friends.getText().equals("Kết bạn"));
                onEdt(() -> {
                    visibleButton(dialog, "Nhắn tin").doClick();
                    assertFalse(dialog.isShowing());
                    assertTrue(hasLabel(frame, "Hải Nam"));
                    return null;
                });
                await(() -> hasConversation(frame, "Khánh Linh"));
                onEdt(() -> {
                    friends.doClick();
                    FriendRequestsDialog reopened = friendRequests(frame);
                    children(reopened, JTabbedPane.class).get(0).setSelectedIndex(1);
                    assertTrue(hasLabel(reopened, "Khánh Linh"));
                    assertTrue(hasLabel(reopened, "Đã chấp nhận"));
                    return null;
                });
            } finally {
                onEdt(() -> {
                    frame.dispose();
                    return null;
                });
            }
        }
    }

    @Test
    void exercisesOutgoingVoiceAndIncomingVideoCalls() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var mock = new MockChatService()) {
            var self = mock.signIn("Người dùng demo", "demo".toCharArray()).toCompletableFuture().get(5, TimeUnit.SECONDS);
            MainChatFrame frame = onEdt(() -> {
                ChatStyle.install();
                MainChatFrame window = new MainChatFrame(self, mock, mock::simulateIncomingCall);
                window.setSize(window.getMinimumSize());
                window.setVisible(true);
                return window;
            });
            try {
                JButton voice = onEdt(() -> callButton(frame, "Gọi thoại"));
                JButton video = onEdt(() -> callButton(frame, "Gọi video"));
                await(() -> voice.isShowing() && voice.isEnabled());
                onEdt(() -> {
                    capture(frame, "call-header-minimum");
                    voice.doClick();
                    return null;
                });
                await(() -> callDialog(frame) != null && hasLabel(callDialog(frame), "Đang đổ chuông…"));
                CallDialog outgoing = onEdt(() -> callDialog(frame));
                onEdt(() -> {
                    assertFalse(video.isEnabled());
                    capture(outgoing, "call-voice-ringing");
                    return null;
                });
                await(() -> hasLabelStartingWith(outgoing, "Đang gọi · "));
                onEdt(() -> {
                    visibleToggle(outgoing, "Tắt mic").doClick();
                    assertNotNull(visibleToggle(outgoing, "Bật mic"));
                    assertTrue(children(outgoing, JToggleButton.class).stream()
                            .noneMatch(button -> button.isShowing() && button.getText().contains("camera")));
                    return null;
                });
                await(() -> hasLabelStartingWith(outgoing, "Đang gọi · ") && !hasLabel(outgoing, "Đang gọi · 00:00"));
                onEdt(() -> {
                    visibleButton(outgoing, "Kết thúc").doClick();
                    return null;
                });
                await(() -> hasLabelStartingWith(outgoing, "Đã kết thúc · ") && video.isEnabled());

                CallDialog declined = incomingVideo(frame);
                onEdt(() -> {
                    capture(declined, "call-video-incoming");
                    visibleButton(declined, "Từ chối").doClick();
                    return null;
                });
                await(() -> hasLabel(declined, "Đã từ chối cuộc gọi") && video.isEnabled());

                CallDialog incoming = incomingVideo(frame);
                onEdt(() -> {
                    visibleButton(incoming, "Chấp nhận").doClick();
                    return null;
                });
                await(() -> hasLabel(incoming, "Khung video mẫu"));
                onEdt(() -> {
                    visibleToggle(incoming, "Tắt camera").doClick();
                    assertTrue(hasLabel(incoming, "Bạn · Camera tắt"));
                    capture(incoming, "call-video-connected");
                    incoming.dispatchEvent(new WindowEvent(incoming, WindowEvent.WINDOW_CLOSING));
                    return null;
                });
                await(() -> !incoming.isShowing() && video.isEnabled());
                onEdt(() -> {
                    selectChat(frame, "Nhóm dự án");
                    assertFalse(video.isShowing());
                    selectChat(frame, "Minh Khang");
                    assertTrue(video.isShowing());
                    video.doClick();
                    return null;
                });
                await(() -> hasLabel(frame, "Minh Khang đang ngoại tuyến."));
                assertNull(onEdt(() -> callDialog(frame)));
            } finally {
                onEdt(() -> {
                    frame.dispose();
                    return null;
                });
            }
        }
    }

    private static CallDialog incomingVideo(MainChatFrame frame) throws Exception {
        onEdt(() -> {
            visibleButton(frame, "Thử gọi đến").doClick();
            MenuSelectionManager selection = MenuSelectionManager.defaultManager();
            for (MenuElement element : selection.getSelectedPath()) {
                if (element instanceof JPopupMenu menu) {
                    children(menu, JMenuItem.class).stream()
                            .filter(item -> "Cuộc gọi video đến".equals(item.getText()))
                            .findFirst().orElseThrow().doClick();
                    selection.clearSelectedPath();
                    return null;
                }
            }
            throw new AssertionError("Menu thử gọi đến chưa mở.");
        });
        await(() -> callDialog(frame) != null && hasLabel(callDialog(frame), "Cuộc gọi đến"));
        return onEdt(() -> callDialog(frame));
    }

    private static JButton callButton(MainChatFrame frame, String tip) {
        return children(frame, JButton.class).stream().filter(button -> tip.equals(button.getToolTipText()))
                .findFirst().orElseThrow();
    }

    private static CallDialog callDialog(MainChatFrame frame) {
        for (Window window : frame.getOwnedWindows()) {
            if (window instanceof CallDialog dialog && dialog.isShowing()) {
                return dialog;
            }
        }
        return null;
    }

    private static JToggleButton visibleToggle(Component root, String text) {
        return children(root, JToggleButton.class).stream().filter(button -> button.isShowing() && text.equals(button.getText()))
                .findFirst().orElseThrow();
    }

    private static boolean hasLabelStartingWith(Component root, String prefix) {
        return children(root, JLabel.class).stream().anyMatch(label -> label.isShowing() && label.getText() != null
                && label.getText().startsWith(prefix));
    }

    private static void selectChat(MainChatFrame frame, String title) {
        var list = children(frame, JList.class).get(0);
        for (int index = 0; index < list.getModel().getSize(); index++) {
            if (list.getModel().getElementAt(index) instanceof Conversation chat && title.equals(chat.title())) {
                list.setSelectedIndex(index);
                return;
            }
        }
        throw new AssertionError("Không tìm thấy đoạn chat " + title);
    }

    private static FriendRequestsDialog friendRequests(MainChatFrame frame) {
        for (Window window : frame.getOwnedWindows()) {
            if (window instanceof FriendRequestsDialog dialog && dialog.isShowing()) {
                return dialog;
            }
        }
        throw new AssertionError("Hộp thoại kết bạn chưa mở.");
    }

    private static JButton visibleButton(Component root, String text) {
        return children(root, JButton.class).stream().filter(button -> button.isShowing() && text.equals(button.getText()))
                .findFirst().orElseThrow();
    }

    private static boolean hasConversation(MainChatFrame frame, String title) {
        var model = children(frame, JList.class).get(0).getModel();
        for (int index = 0; index < model.getSize(); index++) {
            if (model.getElementAt(index) instanceof Conversation chat && title.equals(chat.title())) {
                return true;
            }
        }
        return false;
    }

    private static JDialog groupDialog() {
        for (Window window : Window.getWindows()) {
            if (window instanceof JDialog dialog && dialog.isShowing() && dialog.getTitle().equals("Tạo nhóm mới")) {
                return dialog;
            }
        }
        return null;
    }

    private static MainChatFrame chat() {
        for (Window window : Window.getWindows()) {
            if (window instanceof MainChatFrame frame && frame.isShowing()) {
                return frame;
            }
        }
        return null;
    }

    private static boolean hasLabel(String text) {
        return hasLabel(chat(), text);
    }

    private static boolean hasLabel(Component root, String text) {
        return children(root, JLabel.class).stream().anyMatch(label -> label.isShowing() && text.equals(label.getText()));
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        do {
            if (onEdt(condition::getAsBoolean)) {
                return;
            }
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        fail("Không quan sát được trạng thái UI mock.");
    }

    private static <Result> Result onEdt(java.util.concurrent.Callable<Result> operation) throws Exception {
        var task = new FutureTask<>(operation);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }

    private static <Control extends Component> List<Control> children(Component root, Class<Control> type) {
        List<Control> result = new ArrayList<>();
        if (type.isInstance(root)) {
            result.add(type.cast(root));
        }
        if (root instanceof Container parent) {
            for (Component child : parent.getComponents()) {
                result.addAll(children(child, type));
            }
        }
        return result;
    }

    private static void capture(RootPaneContainer frame, String name) throws Exception {
        var content = frame.getContentPane();
        var image = new BufferedImage(content.getWidth(), content.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        content.printAll(graphics);
        graphics.dispose();
        ImageIO.write(image, "png", Path.of("target", name + ".png").toFile());
    }
}
