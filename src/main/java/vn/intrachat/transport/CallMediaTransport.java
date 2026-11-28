package vn.intrachat.transport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Plaintext LAN UDP transport for realtime P2P voice (PCM) and video (chunked JPEG) frames.
 * Every datagram is tagged with the active callId and kept under standard 1500-byte Ethernet MTU.
 */
public final class CallMediaTransport implements AutoCloseable {
    private static final byte MAGIC = 0x49; // 'I'
    private static final byte KIND_AUDIO = 1;
    private static final byte KIND_VIDEO = 2;
    private static final int AUDIO_HEADER = 22; // 1+1+16+4
    private static final int VIDEO_HEADER = 26; // 1+1+16+4+2+2
    public static final int MAX_VIDEO_CHUNK = 1350;
    public static final int MAX_VIDEO_FRAME_BYTES = 256 * 1024;
    private static final int MAX_DATAGRAM = 2048;

    private final UUID callId;
    private final DatagramSocket socket;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger audioSeq = new AtomicInteger();
    private final AtomicInteger videoFrameSeq = new AtomicInteger();
    private volatile InetSocketAddress remoteEndpoint;
    private Thread receiverThread;

    private static final class FrameAssembly {
        final byte[][] chunks;
        final long createdAtMs = System.currentTimeMillis();
        int received;
        int totalBytes;

        FrameAssembly(int count) {
            this.chunks = new byte[count][];
        }

        byte[] add(int index, byte[] data) {
            if (index < 0 || index >= chunks.length || chunks[index] != null) return null;
            chunks[index] = data;
            received++;
            totalBytes += data.length;
            if (received < chunks.length) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream(totalBytes);
            for (byte[] chunk : chunks) {
                out.writeBytes(chunk);
            }
            return out.toByteArray();
        }
    }

    public CallMediaTransport(UUID callId) throws SocketException {
        this.callId = Objects.requireNonNull(callId, "callId");
        this.socket = new DatagramSocket(0);
        this.socket.setSoTimeout(500);
        this.socket.setReceiveBufferSize(256 * 1024);
        this.socket.setSendBufferSize(256 * 1024);
    }

    public int localPort() {
        return socket.getLocalPort();
    }

    public void setRemote(InetAddress address, int port) {
        Objects.requireNonNull(address, "address");
        if (port <= 0 || port > 65535) throw new IllegalArgumentException("Invalid UDP port: " + port);
        this.remoteEndpoint = new InetSocketAddress(address, port);
    }

    public void startReceiving(Consumer<byte[]> onAudio, Consumer<byte[]> onVideoJpeg) {
        if (receiverThread != null) return;
        receiverThread = Thread.ofVirtual().name("intrachat-call-udp-" + localPort()).start(() -> {
            byte[] buf = new byte[MAX_DATAGRAM];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            Map<Integer, FrameAssembly> assemblies = new HashMap<>();
            int lastCompletedFrame = -1;
            while (!closed.get() && !socket.isClosed()) {
                try {
                    packet.setLength(buf.length);
                    socket.receive(packet);
                    int len = packet.getLength();
                    if (len < AUDIO_HEADER || buf[0] != MAGIC) continue;
                    ByteBuffer view = ByteBuffer.wrap(buf, 0, len);
                    view.get(); // magic
                    byte kind = view.get();
                    long msb = view.getLong();
                    long lsb = view.getLong();
                    if (msb != callId.getMostSignificantBits() || lsb != callId.getLeastSignificantBits()) {
                        continue;
                    }
                    if (kind == KIND_AUDIO) {
                        view.getInt(); // seq
                        int pcmLen = len - AUDIO_HEADER;
                        if (pcmLen <= 0 || onAudio == null) continue;
                        byte[] pcm = new byte[pcmLen];
                        view.get(pcm);
                        try { onAudio.accept(pcm); } catch (RuntimeException ignored) {}
                    } else if (kind == KIND_VIDEO && len >= VIDEO_HEADER) {
                        int frameId = view.getInt();
                        int chunkIdx = Short.toUnsignedInt(view.getShort());
                        int chunkCount = Short.toUnsignedInt(view.getShort());
                        int sliceLen = len - VIDEO_HEADER;
                        if (chunkCount <= 0 || chunkCount > 200 || chunkIdx >= chunkCount || sliceLen <= 0) continue;
                        if (frameId <= lastCompletedFrame) continue;
                        byte[] slice = new byte[sliceLen];
                        view.get(slice);
                        FrameAssembly assembly = assemblies.computeIfAbsent(frameId, k -> new FrameAssembly(chunkCount));
                        if (assembly.chunks.length != chunkCount) continue;
                        byte[] completed = assembly.add(chunkIdx, slice);
                        if (completed != null) {
                            lastCompletedFrame = frameId;
                            assemblies.remove(frameId);
                            int threshold = frameId;
                            assemblies.entrySet().removeIf(e -> e.getKey() <= threshold);
                            if (onVideoJpeg != null) {
                                try { onVideoJpeg.accept(completed); } catch (RuntimeException ignored) {}
                            }
                        } else if (assemblies.size() > 6) {
                            long now = System.currentTimeMillis();
                            assemblies.entrySet().removeIf(e -> now - e.getValue().createdAtMs > 400);
                            if (assemblies.size() > 6) {
                                int oldest = assemblies.keySet().stream().mapToInt(Integer::intValue).min().orElse(frameId);
                                assemblies.remove(oldest);
                            }
                        }
                    }
                } catch (SocketTimeoutException idle) {
                    long now = System.currentTimeMillis();
                    assemblies.entrySet().removeIf(e -> now - e.getValue().createdAtMs > 500);
                } catch (IOException error) {
                    if (closed.get() || socket.isClosed()) break;
                }
            }
        });
    }

    public void sendAudio(byte[] pcm, int length) throws IOException {
        InetSocketAddress target = remoteEndpoint;
        if (closed.get() || target == null || pcm == null || length <= 0) return;
        int safeLen = Math.min(length, MAX_DATAGRAM - AUDIO_HEADER);
        ByteBuffer buf = ByteBuffer.allocate(AUDIO_HEADER + safeLen);
        buf.put(MAGIC);
        buf.put(KIND_AUDIO);
        buf.putLong(callId.getMostSignificantBits());
        buf.putLong(callId.getLeastSignificantBits());
        buf.putInt(audioSeq.incrementAndGet());
        buf.put(pcm, 0, safeLen);
        byte[] data = buf.array();
        socket.send(new DatagramPacket(data, data.length, target));
    }

    public void sendVideoJpeg(byte[] jpeg) throws IOException {
        InetSocketAddress target = remoteEndpoint;
        if (closed.get() || target == null || jpeg == null || jpeg.length == 0 || jpeg.length > MAX_VIDEO_FRAME_BYTES) {
            return;
        }
        int frameId = videoFrameSeq.incrementAndGet();
        int chunkCount = (jpeg.length + MAX_VIDEO_CHUNK - 1) / MAX_VIDEO_CHUNK;
        if (chunkCount > 200) return;
        for (int idx = 0; idx < chunkCount; idx++) {
            if (closed.get()) return;
            int offset = idx * MAX_VIDEO_CHUNK;
            int len = Math.min(MAX_VIDEO_CHUNK, jpeg.length - offset);
            ByteBuffer buf = ByteBuffer.allocate(VIDEO_HEADER + len);
            buf.put(MAGIC);
            buf.put(KIND_VIDEO);
            buf.putLong(callId.getMostSignificantBits());
            buf.putLong(callId.getLeastSignificantBits());
            buf.putInt(frameId);
            buf.putShort((short) idx);
            buf.putShort((short) chunkCount);
            buf.put(jpeg, offset, len);
            byte[] data = buf.array();
            socket.send(new DatagramPacket(data, data.length, target));
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            socket.close();
        }
    }
}
