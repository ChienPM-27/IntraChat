package vn.intrachat.application;

import vn.intrachat.transport.CallMediaTransport;

import javax.sound.sampled.*;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Full-duplex PCM 16 kHz / 16-bit / mono audio engine for P2P and Group LAN calls using Java Sound.
 * Mixes multiple incoming participant PCM streams simultaneously with clamped 16-bit addition.
 */
public final class AudioCallEngine implements AutoCloseable {
    public static final AudioFormat FORMAT = new AudioFormat(16_000f, 16, 1, true, false);
    private static final int FRAME_BYTES = 640; // 20 ms at 16kHz * 2 bytes/sample
    private static final UUID P2P_SENDER = new UUID(0L, 0L);

    private final CallMediaTransport transport;
    private final UUID groupSenderId;
    private final Consumer<String> onNotice;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean muted = new AtomicBoolean();
    private final ConcurrentHashMap<UUID, ArrayBlockingQueue<byte[]>> participantQueues = new ConcurrentHashMap<>();
    private final Object audioSignal = new Object();
    private volatile TargetDataLine micLine;
    private volatile SourceDataLine speakerLine;
    private Thread captureThread;
    private Thread playbackThread;

    public AudioCallEngine(CallMediaTransport transport, Consumer<String> onNotice) {
        this(transport, null, onNotice);
    }

    public AudioCallEngine(CallMediaTransport transport, UUID groupSenderId, Consumer<String> onNotice) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.groupSenderId = groupSenderId;
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
        enqueueIncomingAudio(P2P_SENDER, pcm);
    }

    public void enqueueIncomingAudio(UUID senderId, byte[] pcm) {
        if (!running.get() || pcm == null || pcm.length == 0) return;
        UUID key = senderId == null ? P2P_SENDER : senderId;
        ArrayBlockingQueue<byte[]> queue = participantQueues.computeIfAbsent(key, k -> new ArrayBlockingQueue<>(8));
        if (!queue.offer(pcm)) {
            queue.poll(); // drop oldest frame to keep realtime latency low
            queue.offer(pcm);
        }
        synchronized (audioSignal) {
            audioSignal.notify();
        }
    }

    public void removeParticipant(UUID senderId) {
        if (senderId != null) participantQueues.remove(senderId);
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
                        if (groupSenderId == null) {
                            transport.sendAudio(buffer, read);
                        } else {
                            transport.sendGroupAudio(groupSenderId, buffer, read);
                        }
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
                List<byte[]> frames = new ArrayList<>(6);
                while (running.get() && line.isOpen()) {
                    frames.clear();
                    for (ArrayBlockingQueue<byte[]> q : participantQueues.values()) {
                        byte[] f = q.poll();
                        if (f != null && f.length > 0) frames.add(f);
                    }
                    if (frames.isEmpty()) {
                        synchronized (audioSignal) {
                            if (running.get()) {
                                try { audioSignal.wait(25); } catch (InterruptedException ignored) {}
                            }
                        }
                        continue;
                    }
                    if (frames.size() == 1) {
                        byte[] single = frames.get(0);
                        line.write(single, 0, single.length);
                    } else {
                        byte[] mixed = mixPcm16Le(frames);
                        line.write(mixed, 0, mixed.length);
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

    /** Mixes two or more 16-bit signed little-endian PCM buffers with saturation clamping. */
    public static byte[] mixPcm16Le(List<byte[]> streams) {
        int maxLen = 0;
        for (byte[] s : streams) maxLen = Math.max(maxLen, s.length);
        int evenLen = maxLen & ~1;
        byte[] out = new byte[evenLen];
        for (int i = 0; i < evenLen; i += 2) {
            int sum = 0;
            for (byte[] s : streams) {
                if (i + 1 < s.length) {
                    short sample = (short) ((s[i] & 0xFF) | (s[i + 1] << 8));
                    sum += sample;
                }
            }
            if (sum > Short.MAX_VALUE) sum = Short.MAX_VALUE;
            else if (sum < Short.MIN_VALUE) sum = Short.MIN_VALUE;
            out[i] = (byte) (sum & 0xFF);
            out[i + 1] = (byte) ((sum >> 8) & 0xFF);
        }
        return out;
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) return;
        synchronized (audioSignal) {
            audioSignal.notifyAll();
        }
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
        participantQueues.clear();
    }
}
