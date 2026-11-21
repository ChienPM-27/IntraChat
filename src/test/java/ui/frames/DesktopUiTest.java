package ui.frames;

import model.mock.MockChatService;
import org.junit.jupiter.api.Test;
import ui.components.ChatStyle;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
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
                var frame = new LoginFrame(mock);
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
        return children(chat(), JLabel.class).stream().anyMatch(label -> label.isShowing() && text.equals(label.getText()));
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

    private static void capture(JFrame frame, String name) throws Exception {
        var content = frame.getContentPane();
        var image = new BufferedImage(content.getWidth(), content.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        content.printAll(graphics);
        graphics.dispose();
        ImageIO.write(image, "png", Path.of("target", name + ".png").toFile());
    }
}
