package vn.intrachat.application;

import vn.intrachat.transport.CallMediaTransport;

import javax.sound.sampled.*;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Full-duplex PCM 16 kHz / 16-bit / mono audio engine for P2P LAN calls using Java Sound.
 * Gracefully degrades if microphone or speaker hardware is unavailable.
 */
public final class AudioCallEngine implements AutoCloseable {
    public static final AudioFormat FORMAT = new AudioFormat(16_000f, 16, 1, true, false);
    private static final int FRAME_BYTES = 640; // 20 ms at 16kHz * 2 bytes/sample

    private final CallMediaTransport transport;
    private final Consumer<String> onNotice;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean muted = new AtomicBoolean();
    private final ArrayBlockingQueue<byte[]> playbackQueue = new ArrayBlockingQueue<>(8);
    private volatile TargetDataLine micLine;
    private volatile SourceDataLine speakerLine;
    private Thread captureThread;
    private Thread playbackThread;

    public AudioCallEngine(CallMediaTransport transport, Consumer<String> onNotice) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.onNotice = onNotice == null ? s -> {} : onNotice;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        startCapture();
        startPlayback();
    }

    public void setMuted(boolean muted) {
        this.muted.set(muted);
    }

    public boolean isMuted() {
        return muted.get();
    }

    public void enqueueIncomingAudio(byte[] pcm) {
        if (!running.get() || pcm == null || pcm.length == 0) return;
        if (!playbackQueue.offer(pcm)) {
            playbackQueue.poll(); // drop oldest frame to keep realtime latency low
            playbackQueue.offer(pcm);
        }
    }

    private void startCapture() {
        captureThread = Thread.ofVirtual().name("intrachat-call-mic").start(() -> {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
            if (!AudioSystem.isLineSupported(info)) {
                onNotice.accept("Không tìm thấy micro hỗ trợ 16 kHz — bạn vẫn có thể nghe đối phương.");
                return;
            }
            try (TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info)) {
                micLine = line;
                line.open(FORMAT, FRAME_BYTES * 8);
                line.start();
                byte[] buffer = new byte[FRAME_BYTES];
                while (running.get() && line.isOpen()) {
                    int read = line.read(buffer, 0, buffer.length);
                    if (read > 0 && !muted.get()) {
                        transport.sendAudio(buffer, read);
                    }
                }
            } catch (Exception ex) {
                if (running.get()) {
                    onNotice.accept("Không mở được micro: " + ex.getMessage());
                }
            } finally {
                micLine = null;
            }
        });
    }

    private void startPlayback() {
        playbackThread = Thread.ofVirtual().name("intrachat-call-spk").start(() -> {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, FORMAT);
            if (!AudioSystem.isLineSupported(info)) {
                onNotice.accept("Không tìm thấy thiết bị loa hỗ trợ phát âm thanh.");
                return;
            }
            try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                speakerLine = line;
                line.open(FORMAT, FRAME_BYTES * 8);
                line.start();
                while (running.get() && line.isOpen()) {
                    byte[] pcm = playbackQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (pcm != null && pcm.length > 0) {
                        line.write(pcm, 0, pcm.length);
                    }
                }
            } catch (Exception ex) {
                if (running.get()) {
                    onNotice.accept("Không mở được loa: " + ex.getMessage());
                }
            } finally {
                speakerLine = null;
            }
        });
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) return;
        TargetDataLine mic = micLine;
        if (mic != null) {
            try { mic.stop(); mic.close(); } catch (Exception ignored) {}
        }
        SourceDataLine spk = speakerLine;
        if (spk != null) {
            try { spk.stop(); spk.flush(); spk.close(); } catch (Exception ignored) {}
        }
        if (captureThread != null) captureThread.interrupt();
        if (playbackThread != null) playbackThread.interrupt();
        playbackQueue.clear();
    }
}
