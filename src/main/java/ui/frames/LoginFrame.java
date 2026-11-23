package ui.frames;

import listener.ChatActionHandler;
import model.ChatUser;
import ui.components.ChatIcon;
import ui.components.ChatStyle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.concurrent.CompletionStage;

import static ui.components.ChatStyle.*;

public final class LoginFrame extends JFrame {
    private final ChatActionHandler actions;
    private final JTextField username = new JTextField(22);
    private final JPasswordField password = new JPasswordField(22);
    private final JLabel title = label("Đăng nhập", 28, Font.BOLD, INK);
    private final JLabel error = label(" ", 12, Font.PLAIN, RED);
    private final JButton submit = primary("Đăng nhập");
    private final JButton toggle = new JButton("Đăng ký");
    private boolean registration;
    private final Runnable launchLan;
    private boolean transferred;

    public LoginFrame(ChatActionHandler actions) {
        this(actions, null);
    }

    public LoginFrame(ChatActionHandler actions, Runnable launchLan) {
        super("IntraChat — Demo");
        this.launchLan = launchLan;
        this.actions = actions;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setIconImage(iconImage());
        setMinimumSize(new Dimension(440, 560));
        setSize(520, 610);
        setLocationRelativeTo(null);
        JPanel root = panel(new GridBagLayout());
        JPanel form = panel(new GridBagLayout());
        root.add(form);
        setContentPane(root);

        JLabel logo = new JLabel(new ChatIcon(ChatIcon.Kind.LOGO, BLUE, 55));
        JLabel brand = label("IntraChat", 16, Font.BOLD, INK);
        JLabel demo = label("Bản demo · Không kết nối máy chủ", 13, Font.PLAIN, MUTED);
        for (JLabel heading : new JLabel[]{logo, brand, title, demo}) {
            heading.setHorizontalAlignment(SwingConstants.CENTER);
        }
        addRow(form, logo, 0, 0, true);
        addRow(form, brand, 1, 8, true);
        addRow(form, title, 2, 18, true);
        addRow(form, demo, 3, 5, true);
        addRow(form, fieldLabel("Username", username), 4, 25, true);
        addRow(form, username, 5, 6, true);
        addRow(form, fieldLabel("Password", password), 6, 13, true);
        addRow(form, password, 7, 6, true);
        addRow(form, error, 8, 7, true);
        addRow(form, submit, 9, 8, true);
        addRow(form, toggle, 10, 6, false);
        if (launchLan != null) {
            JButton lan = secondary("Chat trong LAN (TCP)");
            lan.setName("live-lan-entry");
            lan.setForeground(BLUE);
            lan.setToolTipText("Chat Live không cần tài khoản Demo; TCP không mã hóa, chỉ dùng LAN tin cậy");
            lan.getAccessibleContext().setAccessibleDescription(
                "Không cần tài khoản Demo hoặc mật khẩu Demo. Chọn Live để kết nối bằng TCP không mã hóa.");
            lan.addActionListener(event -> launchLan.run());
            addRow(form, lan, 11, 14, true);
            setMinimumSize(new Dimension(440, 600));
            setSize(520, 650);
        }

        styleField(username, "Tên người dùng");
        styleField(password, "Mật khẩu demo");
        submit.setPreferredSize(new Dimension(330, 42));
        toggle.putClientProperty("FlatLaf.style", "arc: 12; borderWidth: 0");
        toggle.setBackground(Color.WHITE);
        toggle.setForeground(new Color(0x0065C5));
        toggle.addActionListener(event -> switchMode());
        submit.addActionListener(event -> submit());
        getRootPane().setDefaultButton(submit);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                if (!transferred) {
                    actions.close();
                }
            }
        });
    }

    private static JLabel fieldLabel(String text, JComponent field) {
        JLabel label = label(text, 13, Font.BOLD, INK);
        label.setLabelFor(field);
        return label;
    }

    private static void styleField(JTextField field, String placeholder) {
        ChatStyle.field(field, placeholder);
        field.putClientProperty("FlatLaf.style", "arc: 12; borderWidth: 0; margin: 4,12,4,12");
        field.setPreferredSize(new Dimension(330, 42));
    }

    private static void addRow(JPanel form, JComponent control, int row, int spacing, boolean fill) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.weightx = 1;
        constraints.insets = new Insets(spacing, 0, 0, 0);
        constraints.fill = fill ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        form.add(control, constraints);
    }

    private void switchMode() {
        registration = !registration;
        String mode = registration ? "Đăng ký" : "Đăng nhập";
        title.setText(mode);
        submit.setText(mode);
        toggle.setText(registration ? "Quay lại đăng nhập" : "Đăng ký");
        error.setText(" ");
        password.setText("");
        username.requestFocusInWindow();
    }

    private void submit() {
        char[] secret = password.getPassword();
        error.setText(" ");
        setBusy(true);
        CompletionStage<ChatUser> attempt;
        try {
            attempt = registration ? actions.register(username.getText(), secret)
                    : actions.signIn(username.getText(), secret);
        } catch (RuntimeException failure) {
            attempt = java.util.concurrent.CompletableFuture.failedFuture(failure);
        } finally {
            password.setText("");
        }
        attempt.whenComplete((user, failure) -> {
            Arrays.fill(secret, '\0');
            SwingUtilities.invokeLater(() -> {
                if (!isDisplayable()) {
                    return;
                }
                if (failure != null) {
                    setBusy(false);
                    error.setText(ChatStyle.error(failure, "Không thể mở phiên. Hãy thử lại."));
                    password.requestFocusInWindow();
                    return;
                }
                MainChatFrame chat = new MainChatFrame(user, actions);
                transferred = true;
                chat.setVisible(true);
                dispose();
            });
        });
    }

    private void setBusy(boolean busy) {
        username.setEnabled(!busy);
        password.setEnabled(!busy);
        submit.setEnabled(!busy);
        toggle.setEnabled(!busy);
        submit.setText(busy ? "Đang mở phiên…" : registration ? "Đăng ký" : "Đăng nhập");
    }

    static Image iconImage() {
        var image = new java.awt.image.BufferedImage(48, 48, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D draw = image.createGraphics();
        new ChatIcon(ChatIcon.Kind.LOGO, BLUE, 48).paintIcon(new JLabel(), draw, 0, 0);
        draw.dispose();
        return image;
    }
}