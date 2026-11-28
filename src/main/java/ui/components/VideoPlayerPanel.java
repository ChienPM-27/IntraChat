package ui.components;

import model.Attachment;
import model.MessageType;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16;

/** Local-file video playback inside Swing. Decode and audio output stay off the EDT. */
final class VideoPlayerPanel extends JPanel {
    private final Attachment attachment;
    private final Consumer<String> notice;
    private final Consumer<VideoPlayerPanel> activate;
    private final AtomicReference<BufferedImage> nextImage = new AtomicReference<>();
    private final JPanel screen = new JPanel() {
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            BufferedImage image = visibleImage;
            if (image == null) {
                graphics.setColor(Color.WHITE);
                new ChatIcon(ChatIcon.Kind.PLAY, Color.WHITE, 36).paintIcon(this, graphics, (getWidth()-36)/2, (getHeight()-36)/2);
                return;
            }
            double scale = Math.min(getWidth() / (double) image.getWidth(), getHeight() / (double) image.getHeight());
            int w = Math.max(1, (int) (image.getWidth() * scale)), h = Math.max(1, (int) (image.getHeight() * scale));
            graphics.drawImage(image, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
        }
    };
    private final JButton play = new JButton("Phát video");
    private final JButton stop = new JButton("Dừng");
    private final JLabel clock = new JLabel("00:00");
    private final JProgressBar progress = new JProgressBar(0, 1000);
    private final Timer refresh = new Timer(35, e -> refreshFrame());
    private final Object pauseLock = new Object();
    private volatile boolean paused, cancelled;
    private volatile long positionUs, durationUs;
    private volatile Thread decoder;
    private BufferedImage visibleImage;

    VideoPlayerPanel(Attachment attachment, Consumer<String> notice, Consumer<VideoPlayerPanel> activate) {
        super(new BorderLayout(0, 4));
        this.attachment = attachment;
        this.notice = notice;
        this.activate = activate;
        setName("messenger-video-player");
        setOpaque(false);
        screen.setBackground(Color.BLACK);
        screen.setPreferredSize(new Dimension(300, 169));
        screen.getAccessibleContext().setAccessibleName("Khung phát video");
        screen.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        screen.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (play.isEnabled()) play.doClick(); }
        });
        play.setName("video-play-pause");
        stop.setName("video-stop");
        play.addActionListener(e -> {
            if (decoder == null) activate.accept(this);
            else {
                paused = !paused;
                play.setText(paused ? "Tiếp tục" : "Tạm dừng");
                synchronized (pauseLock) { pauseLock.notifyAll(); }
            }
        });
        stop.setEnabled(false);
        stop.addActionListener(e -> stopDecoding());
        progress.getAccessibleContext().setAccessibleName("Tiến trình video");
        JPanel controls = new JPanel(new BorderLayout(8, 4));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 2));
        play.setFont(play.getFont().deriveFont(11f));
        stop.setFont(stop.getFont().deriveFont(11f));
        clock.setFont(clock.getFont().deriveFont(10f));
        buttons.add(play); buttons.add(stop);
        controls.add(clock, BorderLayout.SOUTH);
        controls.add(progress, BorderLayout.NORTH);
        controls.add(buttons, BorderLayout.CENTER);
        JLabel title = new JLabel(attachment.fileName());
        title.putClientProperty("html.disable", true);
        title.setForeground(ChatStyle.INK);
        title.setToolTipText(attachment.fileName());
        title.setPreferredSize(new Dimension(300, 18));
        title.setOpaque(true);
        title.setBackground(ChatStyle.FIELD);
        add(title, BorderLayout.NORTH);
        add(screen, BorderLayout.CENTER);
        add(controls, BorderLayout.SOUTH);
    }

    void begin() {
        if (decoder != null) return;
        cancelled = false; paused = false; positionUs = 0; durationUs = 0;
        visibleImage = null; nextImage.set(null);
        progress.setValue(0); screen.repaint();
        play.setText("Tạm dừng"); stop.setEnabled(true);
        clock.setText("Đang mở video…");
        refresh.start();
        decoder = Thread.ofVirtual().name("intrachat-video-player").unstarted(this::decode);
        decoder.start();
    }

    boolean hasVideoFrame() { return visibleImage != null; }
    boolean isPlaying() { return decoder != null; }

    private void refreshFrame() {
        BufferedImage image = nextImage.getAndSet(null);
        if (image != null) { visibleImage = image; screen.repaint(); }
        long duration = durationUs, pos = positionUs;
        if (duration > 0) progress.setValue((int) Math.min(1000, 1000.0 * pos / duration));
        if (pos > 0 || duration > 0)
            clock.setText(MediaFiles.duration(pos / 1000) + " / " + MediaFiles.duration(duration / 1000));
    }

    void stopDecoding() {
        cancelled = true;
        paused = false;
        synchronized (pauseLock) { pauseLock.notifyAll(); }
        Thread worker = decoder;
        if (worker != null) { worker.interrupt(); play.setEnabled(false); }
        play.setText("Phát lại");
        stop.setEnabled(false);
    }

    private long waitUntil(long startNanos, long timestampUs, SourceDataLine audio) throws InterruptedException {
        while (!cancelled) {
            if (paused) {
                long before = System.nanoTime();
                if (audio != null) audio.stop();
                synchronized (pauseLock) {
                    while (paused && !cancelled) pauseLock.wait();
                }
                if (audio != null && !cancelled) audio.start();
                startNanos += System.nanoTime() - before;
                continue;
            }
            long left = startNanos + timestampUs * 1000 - System.nanoTime();
            if (left <= 0) break;
            Thread.sleep(Math.min(25, Math.max(1, left / 1_000_000)));
        }
        return startNanos;
    }

    private void decode() {
        SourceDataLine audio = null;
        boolean audioUnavailable = false;
        String failure = null;
        try {
            MediaFiles.inspect(attachment.path(), MessageType.VIDEO);
            try (var grabber = new FFmpegFrameGrabber(attachment.path().toFile());
                 var converter = new Java2DFrameConverter()) {
                // Only local files may be opened, including any nested FFmpeg references.
                grabber.setOption("protocol_whitelist", "file");
                grabber.setSampleFormat(AV_SAMPLE_FMT_S16);
                grabber.setAudioChannels(2);
                grabber.setSampleRate(44100);
                grabber.start();
                if (grabber.getImageWidth() <= 0 || grabber.getImageHeight() <= 0
                        || (long) grabber.getImageWidth() * grabber.getImageHeight() > 16_000_000)
                    throw new IllegalArgumentException("Video không có hình ảnh hợp lệ hoặc độ phân giải quá lớn.");
                double scale = Math.min(1, 640.0 / Math.max(grabber.getImageWidth(), grabber.getImageHeight()));
                int width = grabber.getImageWidth(), height = grabber.getImageHeight();
                grabber.setImageWidth(Math.max(1, (int) (width * scale)));
                grabber.setImageHeight(Math.max(1, (int) (height * scale)));
                durationUs = Math.max(0, grabber.getLengthInTime());
                long firstUs = Long.MIN_VALUE, startNanos = System.nanoTime();
                org.bytedeco.javacv.Frame frame;
                while (!cancelled && (frame = grabber.grab()) != null) {
                    if (firstUs == Long.MIN_VALUE) firstUs = frame.timestamp;
                    long timestamp = Math.max(0, frame.timestamp - firstUs);
                    startNanos = waitUntil(startNanos, timestamp, audio);
                    if (cancelled) break;
                    positionUs = Math.max(positionUs, timestamp);
                    if (frame.image != null) {
                        BufferedImage image = converter.convert(frame);
                        if (image != null) {
                            BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                            Graphics2D g = copy.createGraphics();
                            g.drawImage(image, 0, 0, null); g.dispose();
                            nextImage.set(copy);
                        }
                    }
                    if (frame.samples != null && !audioUnavailable && frame.samples[0] instanceof ShortBuffer samples) {
                        if (audio == null) {
                            try {
                                AudioFormat format = new AudioFormat(frame.sampleRate, 16, frame.audioChannels, true, false);
                                audio = AudioSystem.getSourceDataLine(format);
                                audio.open(format, Math.max(4096, (int) (format.getFrameRate() * format.getFrameSize() / 5)));
                                audio.start();
                            } catch (Exception ex) {
                                if (audio != null) { audio.close(); audio = null; }
                                audioUnavailable = true;
                                SwingUtilities.invokeLater(() -> notice.accept("Không mở được loa; video vẫn phát hình ảnh."));
                            }
                        }
                        if (audio != null) {
                            ShortBuffer pcm = samples.duplicate();
                            ByteBuffer data = ByteBuffer.allocate(pcm.remaining() * 2).order(ByteOrder.LITTLE_ENDIAN);
                            while (pcm.hasRemaining()) data.putShort(pcm.get());
                            byte[] bytes = data.array();
                            int frameSize = audio.getFormat().getFrameSize();
                            int chunk = Math.max(frameSize, 4096 / frameSize * frameSize);
                            for (int offset = 0; offset < bytes.length && !cancelled;) {
                                startNanos = waitUntil(startNanos, timestamp, audio);
                                if (cancelled) break;
                                int len = Math.min(chunk, bytes.length - offset);
                                int written = audio.write(bytes, offset, len);
                                if (written <= 0) break;
                                offset += written;
                            }
                        }
                    }
                }
                if (!cancelled && audio != null) audio.drain();
            }
        } catch (Exception | LinkageError ex) {
            if (!cancelled) failure = "Không thể phát video: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        } finally {
            if (audio != null) { audio.stop(); audio.flush(); audio.close(); }
            String error = failure;
            SwingUtilities.invokeLater(() -> {
                decoder = null;
                play.setEnabled(true);
                refreshFrame();
                refresh.stop();
                stop.setEnabled(false);
                play.setText("Phát lại");
                if (error != null) { clock.setText("Không thể phát"); notice.accept(error); }
                else if (!cancelled) { progress.setValue(1000); clock.setText("Đã phát xong"); }
                else { progress.setValue(0); clock.setText("Đã dừng"); }
            });
        }
    }

    @Override public void removeNotify() {
        stopDecoding();
        refresh.stop();
        super.removeNotify();
    }
}
