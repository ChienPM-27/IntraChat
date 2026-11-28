package ui.components;

import model.Attachment;
import model.MessageContent;
import model.MessageType;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static ui.components.ChatStyle.*;

public final class MessageMedia implements AutoCloseable {
    private final Executor executor;
    private final Component owner;
    private final Consumer<String> status;
    private final Function<Attachment, Path> saveDestination;
    private VideoPlayerDialog videoPlayer;
    private Clip active;
    private JButton activeButton;
    private boolean closed;

    public MessageMedia(Executor executor, Component owner, Consumer<String> status) {
        this(executor, owner, status, null);
    }

    MessageMedia(Executor executor, Component owner, Consumer<String> status,
                 Function<Attachment, Path> saveDestination) {
        this.executor = executor;
        this.owner = owner;
        this.status = status;
        this.saveDestination = saveDestination == null ? this::chooseSaveDestination : saveDestination;
    }

    JComponent render(MessageContent content, boolean mine) {
        Attachment attachment = content.attachment();
        Color ink = mine ? Color.WHITE : INK;
        return switch (content.type()) {
            case IMAGE -> thumbnail(attachment, ink);
            case VIDEO -> video(attachment);
            case FILE -> file(attachment, ink);
            case SOUND -> attachment.fileName().toLowerCase(java.util.Locale.ROOT).endsWith(".wav")
                ? new VoicePlayer(attachment, ink) : externalAudio(attachment, ink);
            default -> throw new IllegalArgumentException();
        };
    }

    private JComponent thumbnail(Attachment attachment, Color ink) {
        JButton button = new JButton("Đang tải ảnh…");
        button.setForeground(ink);
        button.setContentAreaFilled(false);
        button.setBorder(new EmptyBorder(0, 0, 0, 0));
        button.setToolTipText("Xem ảnh: " + attachment.fileName());
        button.getAccessibleContext().setAccessibleName("Xem ảnh " + attachment.fileName());
        button.setEnabled(false);
        if (attachment.width() > 0 && attachment.height() > 0) {
            double scale = Math.min(1, 240.0 / Math.max(attachment.width(), attachment.height()));
            button.setPreferredSize(new Dimension(Math.max(1, (int) (attachment.width() * scale)),
                    Math.max(1, (int) (attachment.height() * scale))));
        }
        button.addActionListener(event -> run(() -> MediaFiles.image(attachment.path(), 1600), image -> {
            JScrollPane scroll = new JScrollPane(new JLabel(new ImageIcon(image)));
            Dimension size = owner.getToolkit().getScreenSize();
            scroll.setPreferredSize(new Dimension(Math.min(image.getWidth() + 24, size.width * 3 / 4),
                    Math.min(image.getHeight() + 24, size.height * 3 / 4)));
            JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(owner), attachment.fileName(),
                    Dialog.ModalityType.MODELESS);
            dialog.setName("messenger-image-viewer");
            dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            JPanel viewer = panel(new BorderLayout(0, 12));
            viewer.setBorder(new EmptyBorder(14, 16, 16, 16));
            JPanel header = panel(new BorderLayout(10, 0));
            JLabel title = label(shortName(attachment.fileName(), 55), 16, Font.BOLD, INK);
            title.setToolTipText(attachment.fileName());
            header.add(title, BorderLayout.CENTER);
            JButton close = secondary("Đóng");
            close.setName("image-viewer-close");
            close.addActionListener(e -> dialog.dispose());
            header.add(close, BorderLayout.EAST);
            viewer.add(header, BorderLayout.NORTH);
            scroll.setBorder(BorderFactory.createLineBorder(LINE, 1, true));
            scroll.getViewport().setBackground(FIELD);
            viewer.add(scroll, BorderLayout.CENTER);
            dialog.setContentPane(viewer);
            dialog.pack();
            dialog.setLocationRelativeTo(owner);
            dialog.setVisible(true);
        }, "Không thể mở ảnh."));
        run(() -> {
            BufferedImage image = MediaFiles.image(attachment.path(), 240);
            double scale = Math.min(1, 240.0 / Math.max(image.getWidth(), image.getHeight()));
            return new RoundedImage(image, Math.max(1, (int) (image.getWidth() * scale)),
                    Math.max(1, (int) (image.getHeight() * scale)));
        }, icon -> {
            button.setPreferredSize(null);
            button.setText("");
            button.setIcon(icon);
            button.setEnabled(true);
        }, "Không đọc được ảnh.", () -> {
            button.setPreferredSize(null);
            button.setText("Ảnh không khả dụng");
            button.setToolTipText("Không tìm thấy hoặc không đọc được ảnh.");
        });
        return button;
    }

    private JComponent video(Attachment attachment) {
        JButton play = new JButton();
        play.putClientProperty("html.disable", true);
        play.putClientProperty("FlatLaf.style", "arc: 16; borderWidth: 0");
        play.setIcon(new ChatIcon(ChatIcon.Kind.PLAY, Color.WHITE, 30));
        play.setVerticalTextPosition(SwingConstants.BOTTOM);
        play.setHorizontalTextPosition(SwingConstants.CENTER);
        play.setBackground(new Color(0x202733));
        play.setForeground(Color.WHITE);
        play.setPreferredSize(new Dimension(240, 140));
        play.setText(shortName(attachment.fileName(), 27));
        play.setToolTipText("Phát video trong IntraChat: " + attachment.fileName());
        play.getAccessibleContext().setAccessibleName("Mở video " + attachment.fileName());
        play.addActionListener(event -> openVideo(attachment));
        return play;
    }

    public void openVideo(Attachment attachment) {
        if (closed) return;
        stopPlayback();
        videoPlayer = new VideoPlayerDialog(SwingUtilities.getWindowAncestor(owner), attachment, status);
        videoPlayer.open();
    }

    private JComponent externalAudio(Attachment attachment, Color ink) {
        JButton open = new JButton("Mở âm thanh: " + shortName(attachment.fileName(), 25));
        open.setIcon(new ChatIcon(ChatIcon.Kind.AUDIO, ink, 19));
        open.setToolTipText("MP3/M4A: mở bằng trình phát mặc định");
        open.addActionListener(event -> run(() -> {
            if (!java.nio.file.Files.isRegularFile(attachment.path()))
                throw new IOException("File không còn tồn tại");
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN))
                throw new IOException("Không có ứng dụng mặc định");
            Desktop.getDesktop().open(attachment.path().toFile());
            return true;
        }, result -> {}, "Không mở được âm thanh bằng ứng dụng mặc định."));
        return open;
    }

    private JComponent file(Attachment attachment, Color ink) {
        JPanel card = new JPanel(new BorderLayout(9, 0));
        card.setOpaque(false);
        card.add(new JLabel(new ChatIcon(ChatIcon.Kind.FILE, ink, 24)), BorderLayout.WEST);
        JPanel details = new JPanel(new GridLayout(2, 1));
        details.setOpaque(false);
        JLabel name = label(shortName(attachment.fileName(), 28), 13, Font.BOLD, ink);
        name.setToolTipText(attachment.fileName());
        details.add(name);
        details.add(label(MediaFiles.size(attachment.sizeBytes()), 12, Font.PLAIN, ink));
        card.add(details);
        card.add(saveButton(attachment), BorderLayout.EAST);
        return card;
    }

    /** Export a copy; never move the managed attachment referenced by chat history. */
    JButton saveButton(Attachment attachment) {
        JButton save = new JButton("Lưu về máy", new ChatIcon(ChatIcon.Kind.DOWNLOAD, MUTED, 15));
        save.setName("media-save-as");
        save.setFont(save.getFont().deriveFont(11f));
        save.setToolTipText("Chọn thư mục và tên tệp để lưu bản sao");
        save.getAccessibleContext().setAccessibleName("Lưu về máy");
        save.addActionListener(event -> {
            if (closed) return;
            Path target;
            try {
                target = saveDestination.apply(attachment);
            } catch (RuntimeException failure) {
                status.accept(error(failure, "Không thể chọn nơi lưu tệp."));
                return;
            }
            if (target == null) return;
            save.setEnabled(false);
            status.accept("Đang lưu tệp…");
            CompletableFuture.supplyAsync(() -> {
                try {
                    MediaFiles.save(attachment, target);
                    return target.toAbsolutePath().normalize();
                } catch (IOException failure) {
                    throw new CompletionException(failure);
                }
            }, executor).whenComplete((destination, failure) -> SwingUtilities.invokeLater(() -> {
                save.setEnabled(true);
                if (closed) return;
                if (failure == null) status.accept("Đã lưu tệp: " + destination);
                else status.accept(error(failure, "Không thể lưu tệp."));
            }));
        });
        return save;
    }

    private Path chooseSaveDestination(Attachment attachment) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Lưu tệp về máy");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setSelectedFile(new java.io.File(MediaFiles.safeName(attachment.fileName())));
        return chooser.showSaveDialog(owner) == JFileChooser.APPROVE_OPTION
            ? chooser.getSelectedFile().toPath() : null;
    }

    private static String shortName(String name, int limit) {
        return name.codePointCount(0, name.length()) <= limit ? name
                : name.substring(0, name.offsetByCodePoints(0, limit - 1)) + "…";
    }

    private <Value> void run(Callable<Value> work, Consumer<Value> success, String fallback) {
        run(work, success, fallback, () -> status.accept(fallback));
    }

    private <Value> void run(Callable<Value> work, Consumer<Value> success, String fallback, Runnable onFailure) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return work.call();
            } catch (Exception failure) {
                throw new CompletionException(failure);
            }
        }, executor).whenComplete((result, failure) -> SwingUtilities.invokeLater(() -> {
            if (closed) {
                return;
            }
            if (failure == null) {
                success.accept(result);
            } else {
                onFailure.run();
                String message = error(failure, fallback);
                if (!message.equals(fallback)) {
                    status.accept(message);
                }
            }
        }));
    }

    public void stopPlayback() {
        if (videoPlayer != null) {
            videoPlayer.dispose();
            videoPlayer = null;
        }
        JButton previous = activeButton;
        if (active != null) {
            active.close();
            active = null;
        }
        activeButton = null;
        if (previous != null && previous.getParent() instanceof VoicePlayer player) {
            player.tick();
        }
    }

    @Override
    public void close() {
        closed = true;
        stopPlayback();
    }

    private record RoundedImage(BufferedImage image, int width, int height) implements Icon {
        @Override public int getIconWidth() { return width; }
        @Override public int getIconHeight() { return height; }

        @Override
        public void paintIcon(Component component, Graphics graphics, int left, int top) {
            Graphics2D draw = drawing(graphics);
            draw.clip(new RoundRectangle2D.Double(left, top, width, height, 14, 14));
            draw.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            draw.drawImage(image, left, top, width, height, null);
            draw.dispose();
        }
    }

    private final class VoicePlayer extends JPanel {
        private final JButton play;
        private final JProgressBar progress = new JProgressBar(0, 1000);
        private final JLabel time;
        private final Color ink;
        private final Attachment attachment;
        private long durationMillis;
        private final Timer timer = new Timer(85, event -> tick());

        VoicePlayer(Attachment attachment, Color ink) {
            super(new BorderLayout(8, 0));
            this.attachment = attachment;
            this.durationMillis = attachment.durationMillis();
            this.ink = ink;
            setOpaque(false);
            play = iconButton(ChatIcon.Kind.PLAY, "Phát tin thoại", ink);
            play.setContentAreaFilled(false);
            play.addActionListener(event -> toggle());
            JPanel track = new JPanel(new GridBagLayout());
            track.setOpaque(false);
            progress.setPreferredSize(new Dimension(110, 7));
            progress.setForeground(ink);
            progress.setBackground(ink.equals(Color.WHITE) ? new Color(0x529FFF) : new Color(0xC2C7D0));
            progress.getAccessibleContext().setAccessibleName("Tiến trình tin thoại");
            track.add(progress);
            time = label(MediaFiles.duration(durationMillis), 12, Font.PLAIN, ink);
            if (durationMillis == 0) {
                run(() -> MediaFiles.inspect(attachment.path(), MessageType.SOUND).durationMillis(),
                    duration -> { durationMillis = duration; if (activeButton != play) time.setText(MediaFiles.duration(duration)); },
                    "Không đọc được thời lượng WAV.");
            }
            add(play, BorderLayout.WEST);
            add(track);
            add(time, BorderLayout.EAST);
            JButton external=new JButton("Mở ngoài");
            external.setToolTipText("Mở bằng ứng dụng mặc định nếu không thể phát nội bộ");
            external.addActionListener(event -> run(() -> {
                if(!java.nio.file.Files.isRegularFile(attachment.path()))
                    throw new IOException("File không còn tồn tại");
                if(!Desktop.isDesktopSupported()||!Desktop.getDesktop().isSupported(Desktop.Action.OPEN))
                    throw new IOException("Không có ứng dụng mặc định");
                Desktop.getDesktop().open(attachment.path().toFile());
                return true;
            }, result -> {}, "Không mở được âm thanh bằng ứng dụng mặc định."));
            JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT,2,0));
            actions.setOpaque(false);
            JButton stop=new JButton("Dừng");
            stop.addActionListener(event -> stopPlayback());
            actions.add(stop);
            actions.add(external);
            add(actions,BorderLayout.SOUTH);
        }

        private void toggle() {
            if (activeButton == play && active != null) {
                if (active.isRunning()) {
                    active.stop();
                    updateIcon(false);
                } else {
                    if (active.getFramePosition() >= active.getFrameLength()) {
                        active.setFramePosition(0);
                    }
                    active.start();
                    updateIcon(true);
                    timer.start();
                }
                return;
            }
            stopPlayback();
            activeButton = play;
            play.setEnabled(false);
            CompletableFuture.supplyAsync(() -> {
                Clip clip = null;
                try (var audio = MediaFiles.audio(attachment.path())) {
                    clip = AudioSystem.getClip();
                    clip.open(audio);
                    return clip;
                } catch (Exception failure) {
                    if (clip != null) {
                        clip.close();
                    }
                    throw new CompletionException(failure);
                }
            }, executor).whenComplete((clip, failure) -> SwingUtilities.invokeLater(() -> {
                play.setEnabled(true);
                if (closed || activeButton != play || !isShowing()) {
                    if (clip != null) {
                        clip.close();
                    }
                    return;
                }
                if (failure != null) {
                    activeButton = null;
                    status.accept(error(failure, "Không thể phát tin thoại. Hãy kiểm tra thiết bị âm thanh."));
                    return;
                }
                active = clip;
                active.start();
                updateIcon(true);
                timer.start();
            }));
        }

        private void tick() {
            if (activeButton != play || active == null) {
                progress.setValue(0);
                time.setText(MediaFiles.duration(durationMillis));
                updateIcon(false);
                timer.stop();
                return;
            }
            long position = active.getMicrosecondPosition();
            long length = active.getMicrosecondLength();
            progress.setValue(length == 0 ? 0 : (int) (1000.0 * position / length));
            time.setText(MediaFiles.duration(Math.max(0, length - position) / 1000));
            if (!active.isRunning()) {
                updateIcon(false);
                timer.stop();
                if(active.getFramePosition()>=active.getFrameLength())stopPlayback();
            }
        }

        private void updateIcon(boolean playing) {
            String action = playing ? "Tạm dừng tin thoại" : "Phát tin thoại";
            play.setIcon(new ChatIcon(playing ? ChatIcon.Kind.PAUSE : ChatIcon.Kind.PLAY, ink, 19));
            play.setToolTipText(action);
            play.getAccessibleContext().setAccessibleName(action);
        }
    }
}
