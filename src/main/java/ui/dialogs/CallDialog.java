package ui.dialogs;

import com.formdev.flatlaf.FlatClientProperties;
import listener.ChatActionHandler;
import model.CallSession;
import model.ChatUser;
import ui.components.AvatarIcon;
import ui.components.ChatIcon;
import ui.components.MediaFiles;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import static ui.components.ChatStyle.*;

public final class CallDialog extends JDialog {
    private static final Color ACTION_BLUE = new Color(0x0066C8);
    private static final Color VIDEO_STAGE = new Color(0x1B2738);
    private static final Color SELF_PREVIEW = new Color(0x2D405A);
    private static final Color VIDEO_SECONDARY = new Color(0xD2E1F5);
    private final ChatActionHandler actions;
    private final boolean video;
    private final Icon selfAvatar;
    private final JPanel stage = panel(new CardLayout());
    private final JLabel status = centered(" ", 15, Font.BOLD, INK);
    private final JLabel errorLabel = centered(" ", 12, Font.PLAIN, RED);
    private final JLabel preview = centered("Bạn · Camera bật", 12, Font.PLAIN, VIDEO_SECONDARY);
    private final JLabel previewIcon = new JLabel();
    private final JButton accept = primary("Chấp nhận");
    private final JButton reject = new JButton("Từ chối");
    private final JButton hangUp = primary("Kết thúc");
    private final JButton close = new JButton("Đóng");
    private final JToggleButton mic = new JToggleButton();
    private final JToggleButton camera = new JToggleButton();
    private final Timer clock = new Timer(250, event -> updateStatus());
    private CallSession session;
    private boolean busy;
    private boolean dismissOnEnd;

    public CallDialog(Window owner, ChatUser self, CallSession session, ChatActionHandler actions) {
        super(owner, session.mode().label() + " · " + session.peer().displayName(), ModalityType.MODELESS);
        this.session = session;
        this.actions = actions;
        video = session.mode() == CallSession.Mode.VIDEO;
        selfAvatar = avatar(self, 56);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setContentPane(build());
        update(session);
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(owner);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                requestClose();
            }
        });
        getRootPane().registerKeyboardAction(event -> requestClose(), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private JPanel build() {
        JPanel body = panel(new BorderLayout(0, 14));
        body.setBorder(new EmptyBorder(16, 16, 8, 16));
        JPanel heading = panel(new BorderLayout(8, 0));
        heading.add(label(session.mode().label(), 17, Font.BOLD, INK));
        heading.add(label("Cuộc gọi mô phỏng", 12, Font.PLAIN, MUTED), BorderLayout.EAST);
        body.add(heading, BorderLayout.NORTH);

        ChatUser peer = session.peer();
        stage.add(participant(new JLabel(avatar(peer, 74)), peer.displayName(), null, FIELD, INK), "avatar");
        if (video) {
            JPanel cameras = panel(new BorderLayout(8, 0));
            cameras.add(participant(new JLabel(avatar(peer, 74)), peer.displayName(),
                    centered("Khung video mẫu", 12, Font.PLAIN, VIDEO_SECONDARY), VIDEO_STAGE, Color.WHITE));
            JPanel local = participant(previewIcon, null, preview, SELF_PREVIEW, Color.WHITE);
            local.setPreferredSize(new Dimension(166, 0));
            cameras.add(local, BorderLayout.EAST);
            stage.add(cameras, "video");
        }
        stage.setPreferredSize(video ? new Dimension(544, 230) : new Dimension(400, 194));
        body.add(stage);
        body.add(buildFooter(), BorderLayout.SOUTH);
        return body;
    }

    private JPanel buildFooter() {
        accept.setBackground(ACTION_BLUE);
        reject.setForeground(RED);
        hangUp.setBackground(RED);
        accept.addActionListener(event -> submit(() -> actions.respondToCall(session.id(), true)));
        reject.addActionListener(event -> submit(() -> actions.respondToCall(session.id(), false)));
        hangUp.addActionListener(event -> submit(() -> actions.endCall(session.id())));
        close.addActionListener(event -> dispose());
        mic.addActionListener(event -> updateDeviceControls());
        camera.addActionListener(event -> updateDeviceControls());
        for (JToggleButton toggle : new JToggleButton[]{mic, camera}) {
            toggle.putClientProperty(FlatClientProperties.STYLE, "arc: 12");
        }
        updateDeviceControls();
        fitButton(mic, "Bật mic");
        fitButton(camera, "Bật camera");
        fitButton(hangUp, "Hủy cuộc gọi");
        fitButton(accept, accept.getText());
        fitButton(reject, reject.getText());
        fitButton(close, close.getText());
        JPanel buttons = panel(new FlowLayout(FlowLayout.CENTER, 7, 0));
        for (AbstractButton button : new AbstractButton[]{reject, accept, mic, camera, hangUp, close}) {
            buttons.add(button);
        }
        JPanel footer = panel(new BorderLayout(0, 9));
        footer.add(status, BorderLayout.NORTH);
        footer.add(buttons);
        footer.add(errorLabel, BorderLayout.SOUTH);
        return footer;
    }

    private static void fitButton(AbstractButton button, String alternateText) {
        Dimension preferred = button.getPreferredSize();
        FontMetrics metrics = button.getFontMetrics(button.getFont());
        int extra = Math.max(0, metrics.stringWidth(alternateText) - metrics.stringWidth(button.getText()));
        button.setPreferredSize(new Dimension(preferred.width + extra + 8, Math.max(34, preferred.height)));
    }

    private static JPanel participant(JLabel icon, String name, JLabel caption, Color background, Color foreground) {
        JPanel tile = panel(new GridBagLayout());
        tile.setBackground(background);
        tile.putClientProperty(FlatClientProperties.STYLE, "arc: 18");
        tile.setBorder(new EmptyBorder(10, 10, 10, 10));
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        GridBagConstraints row = new GridBagConstraints();
        row.gridx = 0;
        row.gridy = 0;
        row.weightx = 1;
        row.fill = GridBagConstraints.HORIZONTAL;
        tile.add(icon, row);
        row.insets = new Insets(8, 0, 0, 0);
        if (name != null) {
            JLabel title = centered(name, 19, Font.BOLD, foreground);
            title.setToolTipText(name);
            row.gridy++;
            tile.add(title, row);
        }
        if (caption != null) {
            row.gridy++;
            tile.add(caption, row);
        }
        return tile;
    }

    public String callId() {
        return session.id();
    }

    public void update(CallSession updated) {
        session = updated;
        if (dismissOnEnd && !session.status().ongoing()) {
            dispose();
            return;
        }
        boolean connected = session.status() == CallSession.Status.CONNECTED;
        ((CardLayout) stage.getLayout()).show(stage, connected && video ? "video" : "avatar");
        if (connected) {
            clock.start();
        } else {
            clock.stop();
        }
        updateStatus();
        refreshButtons();
    }

    private void updateStatus() {
        String elapsed = MediaFiles.duration(session.durationMillis(Instant.now()));
        status.setText(switch (session.status()) {
            case RINGING -> session.direction() == CallSession.Direction.INCOMING
                    ? "Cuộc gọi đến" : "Đang đổ chuông…";
            case CONNECTED -> "Đang gọi · " + elapsed;
            case CANCELLED -> "Đã hủy cuộc gọi";
            case DECLINED -> "Đã từ chối cuộc gọi";
            case MISSED -> "Cuộc gọi nhỡ";
            case ENDED -> "Đã kết thúc · " + elapsed;
        });
    }

    private void updateDeviceControls() {
        boolean muted = mic.isSelected();
        mic.setText(muted ? "Bật mic" : "Tắt mic");
        mic.setIcon(new ChatIcon(muted ? ChatIcon.Kind.MIC_OFF : ChatIcon.Kind.MIC, INK, 16));
        boolean cameraOff = camera.isSelected();
        camera.setText(cameraOff ? "Bật camera" : "Tắt camera");
        camera.setIcon(new ChatIcon(cameraOff ? ChatIcon.Kind.CAMERA_OFF : ChatIcon.Kind.CAMERA, INK, 16));
        preview.setText(cameraOff ? "Bạn · Camera tắt" : "Bạn · Camera bật");
        previewIcon.setIcon(cameraOff ? new ChatIcon(ChatIcon.Kind.CAMERA_OFF, VIDEO_SECONDARY, 56) : selfAvatar);
    }

    private void refreshButtons() {
        boolean connected = session.status() == CallSession.Status.CONNECTED;
        boolean incoming = session.status() == CallSession.Status.RINGING
                && session.direction() == CallSession.Direction.INCOMING;
        accept.setVisible(incoming);
        reject.setVisible(incoming);
        hangUp.setVisible(session.status().ongoing() && !incoming);
        hangUp.setText(connected ? "Kết thúc" : "Hủy cuộc gọi");
        mic.setVisible(connected);
        camera.setVisible(connected && video);
        close.setVisible(!session.status().ongoing());
        for (AbstractButton button : new AbstractButton[]{accept, reject, hangUp, mic, camera}) {
            button.setEnabled(!busy);
        }
    }

    private void requestClose() {
        if (!session.status().ongoing()) {
            dispose();
        } else if (!busy) {
            dismissOnEnd = true;
            submit(() -> actions.endCall(session.id()));
        }
    }

    private void submit(Supplier<CompletionStage<Void>> operation) {
        if (busy) {
            return;
        }
        busy = true;
        errorLabel.setText(" ");
        refreshButtons();
        CompletionStage<Void> result;
        try {
            result = operation.get();
        } catch (RuntimeException failure) {
            result = CompletableFuture.failedFuture(failure);
        }
        result.whenComplete((ignored, failure) -> SwingUtilities.invokeLater(() -> {
            if (!isDisplayable()) {
                return;
            }
            busy = false;
            if (failure != null) {
                dismissOnEnd = false;
                if (session.status().ongoing()) {
                    errorLabel.setText(error(failure, "Chưa cập nhật được cuộc gọi. Hãy thử lại."));
                }
            }
            refreshButtons();
        }));
    }

    private static JLabel centered(String text, int size, int weight, Color color) {
        JLabel title = label(text, size, weight, color);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        return title;
    }

    private static Icon avatar(ChatUser user, int size) {
        return new AvatarIcon(user.displayName(), user.id(), size, false, false);
    }

    @Override
    public void dispose() {
        clock.stop();
        super.dispose();
    }
}
