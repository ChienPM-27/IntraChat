package ui.dialogs;

import com.formdev.flatlaf.FlatClientProperties;
import listener.ChatActionHandler;
import model.FriendRequest;
import model.LanAddress;
import ui.components.AvatarIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static ui.components.ChatStyle.*;

public final class FriendRequestsDialog extends JDialog {
    private static final Color ACTION = new Color(0x0066C8);
    private static final Color CONFIRMED = new Color(0x236942);

    private final ChatActionHandler actions;
    private final Predicate<String> openChat;
    private final JTextField address = new JTextField();
    private final JButton send = primary("Gửi lời mời");
    private final JLabel addressNotice = label(" ", 12, Font.PLAIN, MUTED);
    private final JLabel responseNotice = label(" ", 12, Font.PLAIN, MUTED);
    private final JTabbedPane tabs = new JTabbedPane();
    private final JPanel incoming = panel(new GridBagLayout());
    private final JPanel outgoing = panel(new GridBagLayout());
    private final List<JButton> responseButtons = new ArrayList<>();
    private List<FriendRequest> requests;
    private boolean busy;

    public FriendRequestsDialog(Window owner, ChatActionHandler actions, List<FriendRequest> requests,
                                Predicate<String> openChat) {
        super(owner, "Kết bạn", ModalityType.MODELESS);
        this.actions = actions;
        this.openChat = openChat;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = panel(new BorderLayout(0, 14));
        root.setBorder(new EmptyBorder(18, 22, 14, 22));
        root.add(buildForm(), BorderLayout.NORTH);
        tabs.addTab("Đã nhận", scroll(incoming));
        tabs.addTab("Đã gửi", scroll(outgoing));
        root.add(tabs);
        root.add(responseNotice, BorderLayout.SOUTH);
        setContentPane(root);
        updateRequests(requests);

        getRootPane().setDefaultButton(send);
        getRootPane().registerKeyboardAction(event -> dispose(), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(owner);
    }

    private JPanel buildForm() {
        JPanel form = panel(new BorderLayout(0, 12));
        JPanel heading = panel(new BorderLayout(0, 4));
        heading.add(label("Kết bạn qua LAN", 22, Font.BOLD, INK));
        heading.add(label("Dùng địa chỉ IP do người kia cung cấp.", 13, Font.PLAIN, MUTED), BorderLayout.SOUTH);
        form.add(heading, BorderLayout.NORTH);

        JPanel invite = panel(new BorderLayout(0, 6));
        JLabel title = label("Địa chỉ IP", 13, Font.BOLD, INK);
        title.setLabelFor(address);
        invite.add(title, BorderLayout.NORTH);
        JPanel input = panel(new BorderLayout(8, 0));
        field(address, "Ví dụ 192.168.1.23");
        address.getAccessibleContext().setAccessibleName("Địa chỉ IP của người nhận");
        address.setPreferredSize(new Dimension(310, 38));
        send.setBackground(ACTION);
        send.addActionListener(event -> invite());
        address.addActionListener(event -> invite());
        input.add(address);
        input.add(send, BorderLayout.EAST);
        invite.add(input);

        JPanel info = panel(new GridLayout(2, 1, 0, 3));
        info.add(label("Thử IP mẫu 192.168.1.23. Phản hồi demo sau khoảng 6 giây.",
                12, Font.PLAIN, MUTED));
        info.add(addressNotice);
        invite.add(info, BorderLayout.SOUTH);
        form.add(invite);
        return form;
    }

    private static JScrollPane scroll(JPanel rows) {
        JScrollPane scroll = new JScrollPane(rows, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setPreferredSize(new Dimension(480, 178));
        scroll.getViewport().setBackground(Color.WHITE);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        return scroll;
    }

    public void updateRequests(List<FriendRequest> updated) {
        if (updated.equals(requests)) {
            return;
        }
        requests = List.copyOf(updated);
        responseButtons.clear();
        List<FriendRequest> inbox = requests.stream()
                .filter(request -> request.direction() == FriendRequest.Direction.INCOMING).toList();
        List<FriendRequest> sent = requests.stream()
                .filter(request -> request.direction() == FriendRequest.Direction.OUTGOING).toList();
        long waiting = inbox.stream().filter(request -> request.status() == FriendRequest.Status.PENDING).count();
        tabs.setTitleAt(0, waiting > 0 ? "Đã nhận (" + waiting + ")" : "Đã nhận");
        fill(incoming, inbox, "Lời mời đến sẽ xuất hiện ở đây.");
        fill(outgoing, sent, "Gửi lời mời bằng IP để theo dõi phản hồi ở đây.");
        refreshControls();
    }

    private void fill(JPanel container, List<FriendRequest> entries, String emptyText) {
        container.removeAll();
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        for (FriendRequest request : entries.stream()
                .sorted(Comparator.comparing(FriendRequest::sentAt).reversed()).toList()) {
            container.add(row(request), constraints);
            constraints.gridy++;
        }
        constraints.weighty = 1;
        constraints.insets = new Insets(12, 8, 0, 8);
        constraints.anchor = GridBagConstraints.NORTH;
        container.add(entries.isEmpty() ? label(emptyText, 13, Font.PLAIN, MUTED) : Box.createVerticalGlue(), constraints);
        container.revalidate();
        container.repaint();
    }

    private JPanel row(FriendRequest request) {
        JPanel row = panel(new BorderLayout(9, 0));
        row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE),
                new EmptyBorder(11, 7, 11, 5)));
        var peer = request.peer();
        row.add(new JLabel(new AvatarIcon(peer.displayName(), peer.id(), 36, false, false)), BorderLayout.WEST);
        JPanel details = panel(new GridLayout(0, 1, 0, 2));
        JLabel title = label(peer.displayName(), 14, Font.BOLD, INK);
        title.setToolTipText(peer.displayName());
        details.add(title);
        JLabel ip = label(request.ipAddress() + " · " + time(request.sentAt()), 12, Font.PLAIN, MUTED);
        ip.setToolTipText(request.ipAddress());
        details.add(ip);
        row.add(details);

        JPanel controls = panel(new GridBagLayout());
        if (request.status() == FriendRequest.Status.PENDING && request.direction() == FriendRequest.Direction.INCOMING) {
            GridBagConstraints spacing = new GridBagConstraints();
            spacing.insets = new Insets(0, 0, 0, 5);
            controls.add(responseButton("Từ chối", false, request), spacing);
            controls.add(responseButton("Chấp nhận", true, request));
        } else {
            String state = switch (request.status()) {
                case PENDING -> "Chờ chấp nhận";
                case ACCEPTED -> "Đã chấp nhận";
                case DECLINED -> "Đã từ chối";
            };
            details.add(label(state, 12, Font.PLAIN, request.status() == FriendRequest.Status.ACCEPTED ? CONFIRMED : MUTED));
            if (request.status() == FriendRequest.Status.ACCEPTED) {
                JButton chat = new JButton("Nhắn tin");
                chat.setForeground(ACTION);
                chat.getAccessibleContext().setAccessibleDescription("Mở đoạn chat với " + peer.displayName());
                chat.addActionListener(event -> {
                    if (openChat.test(peer.id())) {
                        dispose();
                    } else {
                        feedback(responseNotice, "Đoạn chat chưa sẵn sàng. Hãy thử lại.", RED);
                    }
                });
                controls.add(chat);
            }
        }
        row.add(controls, BorderLayout.EAST);
        return row;
    }

    private JButton responseButton(String text, boolean accept, FriendRequest request) {
        JButton button = accept ? primary(text) : new JButton(text);
        if (accept) {
            button.setBackground(ACTION);
        }
        button.getAccessibleContext().setAccessibleDescription(text + " lời mời từ " + request.peer().displayName());
        button.addActionListener(event -> respond(request.id(), accept));
        responseButtons.add(button);
        return button;
    }

    private void invite() {
        if (busy) {
            return;
        }
        String ip = address.getText().strip();
        try {
            LanAddress.normalize(ip);
        } catch (IllegalArgumentException failure) {
            address.putClientProperty(FlatClientProperties.OUTLINE, "error");
            feedback(addressNotice, failure.getMessage(), RED);
            address.requestFocusInWindow();
            return;
        }
        address.putClientProperty(FlatClientProperties.OUTLINE, null);
        submit(() -> actions.sendFriendRequest(ip), addressNotice, "Đang gửi lời mời…",
                "Chưa gửi được lời mời. Hãy thử lại.", () -> {
                    address.setText("");
                    tabs.setSelectedIndex(1);
                    feedback(addressNotice, "Đã gửi lời mời.", CONFIRMED);
                });
    }

    private void respond(String requestId, boolean accept) {
        submit(() -> actions.respondToFriendRequest(requestId, accept), responseNotice, "Đang cập nhật lời mời…",
                "Chưa cập nhật được lời mời. Hãy thử lại.", () -> feedback(responseNotice, " ", MUTED));
    }

    private void submit(Supplier<CompletionStage<Void>> operation, JLabel notice, String progress,
                        String fallback, Runnable complete) {
        if (busy) {
            return;
        }
        busy = true;
        refreshControls();
        feedback(notice, progress, MUTED);
        CompletionStage<Void> result;
        try {
            result = operation.get();
        } catch (RuntimeException failure) {
            result = java.util.concurrent.CompletableFuture.failedFuture(failure);
        }
        result.whenComplete((ignored, failure) -> SwingUtilities.invokeLater(() -> {
            if (!isDisplayable()) {
                return;
            }
            busy = false;
            refreshControls();
            if (failure == null) {
                complete.run();
            } else {
                feedback(notice, error(failure, fallback), RED);
            }
        }));
    }

    private void refreshControls() {
        address.setEditable(!busy);
        send.setEnabled(!busy);
        responseButtons.forEach(button -> button.setEnabled(!busy));
    }

    private static void feedback(JLabel label, String text, Color color) {
        label.setText(text);
        label.setForeground(color);
    }
}
