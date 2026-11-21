package ui.components;

import com.formdev.flatlaf.FlatClientProperties;
import listener.ChatActionHandler;
import model.CallSession;
import model.ChatUser;
import ui.dialogs.CallDialog;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static ui.components.ChatStyle.*;

public final class CallControls extends JPanel implements AutoCloseable {
    private final Window owner;
    private final ChatUser self;
    private final ChatActionHandler actions;
    private final Consumer<String> feedback;
    private final JButton voice = iconButton(ChatIcon.Kind.PHONE, "Gọi thoại", BLUE);
    private final JButton video = iconButton(ChatIcon.Kind.CAMERA, "Gọi video", BLUE);
    private final JButton demo = new JButton("Thử gọi đến");
    private String conversationId;
    private CallDialog dialog;
    private boolean requesting;
    private boolean ongoing;
    private boolean closed;

    public CallControls(Window owner, ChatUser self, ChatActionHandler actions,
                        BiFunction<String, CallSession.Mode, CompletionStage<Void>> simulateIncoming,
                        Consumer<String> feedback) {
        super(new GridBagLayout());
        this.owner = owner;
        this.self = self;
        this.actions = actions;
        this.feedback = feedback;
        setOpaque(false);
        voice.addActionListener(event -> start(CallSession.Mode.VOICE, actions::startCall));
        video.addActionListener(event -> start(CallSession.Mode.VIDEO, actions::startCall));
        voice.setBackground(FIELD);
        video.setBackground(FIELD);
        GridBagConstraints spacing = new GridBagConstraints();
        spacing.insets = new Insets(0, 6, 0, 0);
        if (simulateIncoming != null) {
            demo.putClientProperty(FlatClientProperties.STYLE, "arc: 12; borderWidth: 0");
            demo.setBackground(FIELD);
            demo.setForeground(MUTED);
            demo.setToolTipText("Mô phỏng cuộc gọi từ người trong đoạn chat");
            JPopupMenu options = new JPopupMenu();
            for (CallSession.Mode mode : CallSession.Mode.values()) {
                JMenuItem choice = new JMenuItem(mode == CallSession.Mode.VOICE ? "Cuộc gọi thoại đến" : "Cuộc gọi video đến");
                choice.addActionListener(event -> start(mode, simulateIncoming));
                options.add(choice);
            }
            demo.addActionListener(event -> options.show(demo, 0, demo.getHeight()));
            add(demo, spacing);
        }
        add(voice, spacing);
        add(video, spacing);
        selectConversation(null);
    }

    public void selectConversation(String id) {
        conversationId = id;
        setVisible(id != null);
        refreshButtons();
    }

    public void update(CallSession session) {
        if (closed) {
            return;
        }
        if (dialog != null && dialog.callId().equals(session.id())) {
            if (dialog.isDisplayable()) {
                dialog.update(session);
            }
        } else if (session.status().ongoing() && !ongoing) {
            if (dialog != null) {
                dialog.dispose();
            }
            dialog = new CallDialog(owner, self, session, actions);
            dialog.setVisible(true);
        } else {
            return;
        }
        ongoing = session.status().ongoing();
        refreshButtons();
    }

    private void start(CallSession.Mode mode, BiFunction<String, CallSession.Mode, CompletionStage<Void>> request) {
        if (closed || conversationId == null || requesting || ongoing) {
            return;
        }
        requesting = true;
        refreshButtons();
        feedback.accept("");
        String requestedChat = conversationId;
        CompletionStage<Void> result;
        try {
            result = request.apply(requestedChat, mode);
        } catch (RuntimeException failure) {
            result = CompletableFuture.failedFuture(failure);
        }
        result.whenComplete((ignored, failure) -> SwingUtilities.invokeLater(() -> {
            if (closed) {
                return;
            }
            requesting = false;
            refreshButtons();
            if (failure != null && Objects.equals(requestedChat, conversationId)) {
                feedback.accept(error(failure, "Không mở được cuộc gọi. Hãy thử lại."));
            }
        }));
    }

    private void refreshButtons() {
        boolean enabled = conversationId != null && !requesting && !ongoing;
        voice.setEnabled(enabled);
        video.setEnabled(enabled);
        demo.setEnabled(enabled);
    }

    @Override
    public void close() {
        closed = true;
        if (dialog != null) {
            dialog.dispose();
        }
    }
}
