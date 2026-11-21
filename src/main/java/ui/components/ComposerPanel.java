package ui.components;

import model.MessageContent;
import model.MessageType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static ui.components.ChatStyle.*;

public final class ComposerPanel extends JPanel implements AutoCloseable {
    private static final String[] EMOJIS = ("😀 😃 😄 😁 😂 🤣 😊 🙂 🙃 😉 😍 🥰 😘 😎 🤔 😅 😢 😭 "
            + "😮 😴 😡 🙌 👏 👍 👎 🙏 💙 ❤️ ✨ 🎉").split(" ");
    private final JTextField input = new JTextField();
    private final JPanel cards = new JPanel(new CardLayout());
    private final JPanel controls = panel(new FlowLayout(FlowLayout.LEADING, 1, 0));
    private final JButton emoji = iconButton(ChatIcon.Kind.SMILE, "Chèn emoji", BLUE);
    private final JButton send = primary("Gửi");
    private final JButton stop = primary("Dừng / Gửi");
    private final JLabel counter = label("00:00", 14, Font.BOLD, RED);
    private final Executor worker;
    private final Supplier<String> currentChat;
    private final BiConsumer<String, MessageContent> outgoing;
    private final Consumer<String> status;
    private final java.util.Map<String, String> drafts = new java.util.HashMap<>();
    private VoiceRecorder recorder;
    private boolean preparing;
    private boolean closed;

    public ComposerPanel(Executor worker, Supplier<String> currentChat,
                         BiConsumer<String, MessageContent> outgoing, Consumer<String> status) {
        super(new BorderLayout(8, 0));
        this.worker = worker;
        this.currentChat = currentChat;
        this.outgoing = outgoing;
        this.status = status;
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(0, 60));
        setBorder(new EmptyBorder(10, 13, 10, 15));

        addTool(ChatIcon.Kind.IMAGE, "Gửi ảnh", () -> choose(MessageType.IMAGE));
        addTool(ChatIcon.Kind.VIDEO, "Gửi video", () -> choose(MessageType.VIDEO));
        addTool(ChatIcon.Kind.FILE, "Gửi tệp", () -> choose(MessageType.FILE));
        addTool(ChatIcon.Kind.MIC, "Ghi âm", this::record);
        add(controls, BorderLayout.WEST);

        field(input, "Aa");
        input.getAccessibleContext().setAccessibleName("Nhập tin nhắn");
        JPanel compose = panel(new BorderLayout(5, 0));
        emoji.setContentAreaFilled(false);
        input.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, emoji);
        compose.add(input);
        compose.add(send, BorderLayout.EAST);
        cards.add(compose, "text");

        JPanel recording = panel(new FlowLayout(FlowLayout.TRAILING, 8, 0));
        recording.add(label("Đang ghi âm", 13, Font.PLAIN, RED));
        recording.add(counter);
        JButton cancel = new JButton("Hủy");
        cancel.addActionListener(event -> cancelRecording());
        stop.addActionListener(event -> {
            if (recorder != null) {
                stop.setEnabled(false);
                recorder.stop();
            }
        });
        recording.add(cancel);
        recording.add(stop);
        cards.add(recording, "record");
        add(cards);
        input.addActionListener(event -> sendText());
        send.addActionListener(event -> sendText());
        emoji.addActionListener(event -> showEmojis());
        refresh();
    }

    private void addTool(ChatIcon.Kind kind, String description, Runnable action) {
        JButton button = iconButton(kind, description, BLUE);
        button.addActionListener(event -> action.run());
        controls.add(button);
    }

    /** M2 Live cannot expose Group/File/Voice/Video features that have no real transport. */
    public void setTextOnly(boolean onlyText) {
        controls.setVisible(!onlyText);
        if (onlyText) cancelRecording();
        revalidate();
        repaint();
    }

    public void refresh() {
        boolean active = !closed && currentChat.get() != null && recorder == null;
        for (Component tool : controls.getComponents()) {
            tool.setEnabled(active && !preparing);
        }
        input.setEnabled(active);
        emoji.setEnabled(active);
        send.setEnabled(active);
    }

    private void sendText() {
        String chat = currentChat.get();
        String text = input.getText().strip();
        if (chat == null || text.isEmpty()) {
            return;
        }
        try {
            outgoing.accept(chat, MessageContent.text(text));
            input.setText("");
            input.requestFocusInWindow();
        } catch (IllegalArgumentException failure) {
            status.accept(failure.getMessage());
        }
    }

    public void switchDraft(String previous, String next) {
        if (previous != null) {
            drafts.put(previous, input.getText());
        }
        input.setText(next == null ? "" : drafts.getOrDefault(next, ""));
    }

    public void restoreText(String destination, String active, String text) {
        if (destination.equals(active)) {
            input.setText(input.getText().isBlank() ? text : text + " " + input.getText());
        } else {
            drafts.merge(destination, text, (draft, failed) -> draft.isBlank() ? failed : failed + " " + draft);
        }
    }

    private void showEmojis() {
        JPopupMenu popup = new JPopupMenu();
        JPanel grid = panel(new GridLayout(5, 6, 2, 2));
        grid.setBorder(new EmptyBorder(6, 6, 6, 6));
        Font font = new Font("Segoe UI Emoji", Font.PLAIN, 22);
        for (String glyph : EMOJIS) {
            JButton option = new JButton(glyph);
            option.setFont(font.canDisplayUpTo(glyph) < 0 ? font : new Font(Font.DIALOG, Font.PLAIN, 22));
            option.putClientProperty("FlatLaf.style", "arc: 8; margin: 0,0,0,0; borderWidth: 0");
            option.setBackground(Color.WHITE);
            option.setPreferredSize(new Dimension(40, 36));
            option.addActionListener(event -> {
                popup.setVisible(false);
                input.replaceSelection(glyph);
                input.requestFocusInWindow();
            });
            grid.add(option);
        }
        popup.add(grid);
        Dimension size = popup.getPreferredSize();
        popup.show(emoji, emoji.getWidth() - size.width, -size.height - 5);
        grid.getComponent(0).requestFocusInWindow();
    }

    private void choose(MessageType type) {
        String chat = currentChat.get();
        if (chat == null) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(switch (type) {
            case IMAGE -> "Chọn ảnh để gửi";
            case VIDEO -> "Chọn video để gửi";
            default -> "Chọn tệp để gửi";
        });
        if (type == MessageType.IMAGE || type == MessageType.VIDEO) {
            chooser.setAcceptAllFileFilterUsed(false);
            chooser.setFileFilter(type == MessageType.IMAGE
                    ? new FileNameExtensionFilter("Ảnh PNG / JPEG", "png", "jpg", "jpeg")
                    : new FileNameExtensionFilter("Video MP4 / AVI / MKV", "mp4", "avi", "mkv"));
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path path = chooser.getSelectedFile().toPath();
        preparing = true;
        refresh();
        status.accept("Đang kiểm tra tệp…");
        CompletableFuture.supplyAsync(() -> {
            try {
                return MediaFiles.inspect(path, type);
            } catch (java.io.IOException failure) {
                throw new java.util.concurrent.CompletionException(failure);
            }
        }, worker).whenComplete((attachment, failure) -> SwingUtilities.invokeLater(() -> {
            if (closed) {
                return;
            }
            preparing = false;
            refresh();
            if (failure == null) {
                outgoing.accept(chat, MessageContent.media(type, attachment));
            } else {
                status.accept(error(failure, "Không đọc được tệp đã chọn."));
            }
        }));
    }

    private void record() {
        String chat = currentChat.get();
        if (chat == null || recorder != null) {
            return;
        }
        VoiceRecorder session = new VoiceRecorder();
        recorder = session;
        counter.setText("00:00");
        stop.setEnabled(true);
        ((CardLayout) cards.getLayout()).show(cards, "record");
        refresh();
        session.record(worker, elapsed -> SwingUtilities.invokeLater(() -> {
            if (recorder == session) {
                counter.setText(MediaFiles.duration(elapsed));
            }
        })).whenComplete((attachment, failure) -> SwingUtilities.invokeLater(() -> {
            if (closed || recorder != session) {
                if (attachment != null) {
                    attachment.path().toFile().deleteOnExit();
                }
                return;
            }
            resetRecording();
            if (failure != null) {
                status.accept(error(failure, "Không thể ghi âm."));
            } else if (attachment != null) {
                outgoing.accept(chat, MessageContent.media(MessageType.SOUND, attachment));
            }
        }));
    }

    public void cancelRecording() {
        if (recorder != null) {
            recorder.close();
            resetRecording();
        }
    }

    private void resetRecording() {
        recorder = null;
        ((CardLayout) cards.getLayout()).show(cards, "text");
        refresh();
    }

    @Override
    public void close() {
        closed = true;
        cancelRecording();
    }
}
