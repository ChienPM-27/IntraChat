package vn.intrachat.transport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Plaintext LAN UDP transport for realtime P2P and Owner-Relayed Group voice (PCM) and video (chunked JPEG).
 * Every datagram is tagged with the active callId (and senderId for group calls) and kept under 1500-byte MTU.
 */
public final class CallMediaTransport implements AutoCloseable {
    private static final byte MAGIC = 0x49; // 'I'
    private static final byte KIND_AUDIO = 1;
    private static final byte KIND_VIDEO = 2;
    private static final byte KIND_GROUP_AUDIO = 3;
    private static final byte KIND_GROUP_VIDEO = 4;
    private static final int AUDIO_HEADER = 22;       // 1+1+16+4
    private static final int VIDEO_HEADER = 26;       // 1+1+16+4+2+2
    private static final int GROUP_AUDIO_HEADER = 38; // 1+1+16+16+4
    private static final int GROUP_VIDEO_HEADER = 42; // 1+1+16+16+4+2+2
    public static final int MAX_VIDEO_CHUNK = 1350;
    public static final int MAX_GROUP_VIDEO_CHUNK = 1320;
    public static final int MAX_VIDEO_FRAME_BYTES = 256 * 1024;
    private static final int MAX_DATAGRAM = 2048;

    private final UUID callId;
    private final DatagramSocket socket;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger audioSeq = new AtomicInteger();
    private final AtomicInteger videoFrameSeq = new AtomicInteger();
    private final ConcurrentHashMap<UUID, InetSocketAddress> relayPeers = new ConcurrentHashMap<>();
    private volatile InetSocketAddress remoteEndpoint;
    private Thread receiverThread;

    private record FrameKey(UUID senderId, int frameId) {}

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
        this.socket.setReceiveBufferSize(512 * 1024);
        this.socket.setSendBufferSize(512 * 1024);
    }

    public int localPort() {
        return socket.getLocalPort();
    }

    public void setRemote(InetAddress address, int port) {
        Objects.requireNonNull(address, "address");
        if (port <= 0 || port > 65535) throw new IllegalArgumentException("Invalid UDP port: " + port);
        this.remoteEndpoint = new InetSocketAddress(address, port);
    }

    public void addRelayPeer(UUID peerId, InetAddress address, int port) {
        Objects.requireNonNull(peerId, "peerId");
        Objects.requireNonNull(address, "address");
        if (port <= 0 || port > 65535) throw new IllegalArgumentException("Invalid UDP port: " + port);
        relayPeers.put(peerId, new InetSocketAddress(address, port));
    }

    public void removeRelayPeer(UUID peerId) {
        if (peerId != null) relayPeers.remove(peerId);
    }

    public void clearRelayPeers() {
        relayPeers.clear();
    }

    public void startReceiving(Consumer<byte[]> onAudio, Consumer<byte[]> onVideoJpeg) {
        startReceivingInternal(
            onAudio == null ? null : (sender, pcm) -> onAudio.accept(pcm),
            onVideoJpeg == null ? null : (sender, jpeg) -> onVideoJpeg.accept(jpeg),
            false);
    }

    public void startReceivingGroup(BiConsumer<UUID, byte[]> onAudio, BiConsumer<UUID, byte[]> onVideoJpeg, boolean relayMode) {
        startReceivingInternal(onAudio, onVideoJpeg, relayMode);
    }

    private void startReceivingInternal(BiConsumer<UUID, byte[]> onAudio, BiConsumer<UUID, byte[]> onVideoJpeg, boolean relayMode) {
        if (receiverThread != null) return;
        UUID p2pSender = new UUID(0L, 0L);
        receiverThread = Thread.ofVirtual().name("intrachat-call-udp-" + localPort()).start(() -> {
            byte[] buf = new byte[MAX_DATAGRAM];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            Map<FrameKey, FrameAssembly> assemblies = new HashMap<>();
            Map<UUID, Integer> lastCompletedBySender = new HashMap<>();

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
                        try { onAudio.accept(p2pSender, pcm); } catch (RuntimeException ignored) {}
                    } else if (kind == KIND_VIDEO && len >= VIDEO_HEADER) {
                        handleVideoChunk(p2pSender, view, len - VIDEO_HEADER, assemblies, lastCompletedBySender, onVideoJpeg);
                    } else if (kind == KIND_GROUP_AUDIO && len >= GROUP_AUDIO_HEADER) {
                        UUID senderId = new UUID(view.getLong(), view.getLong());
                        if (relayMode) {
                            forwardRaw(buf, len, senderId);
                        }
                        view.getInt(); // seq
                        int pcmLen = len - GROUP_AUDIO_HEADER;
                        if (pcmLen <= 0 || onAudio == null) continue;
                        byte[] pcm = new byte[pcmLen];
                        view.get(pcm);
                        try { onAudio.accept(senderId, pcm); } catch (RuntimeException ignored) {}
                    } else if (kind == KIND_GROUP_VIDEO && len >= GROUP_VIDEO_HEADER) {
                        UUID senderId = new UUID(view.getLong(), view.getLong());
                        if (relayMode) {
                            forwardRaw(buf, len, senderId);
                        }
                        handleVideoChunk(senderId, view, len - GROUP_VIDEO_HEADER, assemblies, lastCompletedBySender, onVideoJpeg);
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

    private void forwardRaw(byte[] buf, int len, UUID excludeSender) {
        if (closed.get() || relayPeers.isEmpty()) return;
        for (var entry : relayPeers.entrySet()) {
            if (entry.getKey().equals(excludeSender)) continue;
            try {
                socket.send(new DatagramPacket(buf, len, entry.getValue()));
            } catch (IOException ignored) {}
        }
    }

    private static void handleVideoChunk(UUID senderId, ByteBuffer view, int sliceLen,
                                         Map<FrameKey, FrameAssembly> assemblies,
                                         Map<UUID, Integer> lastCompletedBySender,
                                         BiConsumer<UUID, byte[]> onVideoJpeg) {
        int frameId = view.getInt();
        int chunkIdx = Short.toUnsignedInt(view.getShort());
        int chunkCount = Short.toUnsignedInt(view.getShort());
        if (chunkCount <= 0 || chunkCount > 220 || chunkIdx >= chunkCount || sliceLen <= 0) return;
        int lastDone = lastCompletedBySender.getOrDefault(senderId, -1);
        if (frameId <= lastDone) return;

        byte[] slice = new byte[sliceLen];
        view.get(slice);
        FrameKey key = new FrameKey(senderId, frameId);
        FrameAssembly assembly = assemblies.computeIfAbsent(key, k -> new FrameAssembly(chunkCount));
        if (assembly.chunks.length != chunkCount) return;

        byte[] completed = assembly.add(chunkIdx, slice);
        if (completed != null) {
            lastCompletedBySender.put(senderId, frameId);
            assemblies.remove(key);
            assemblies.entrySet().removeIf(e -> e.getKey().senderId().equals(senderId) && e.getKey().frameId() <= frameId);
            if (onVideoJpeg != null) {
                try { onVideoJpeg.accept(senderId, completed); } catch (RuntimeException ignored) {}
            }
        } else if (assemblies.size() > 18) {
            long now = System.currentTimeMillis();
            assemblies.entrySet().removeIf(e -> now - e.getValue().createdAtMs > 400);
            if (assemblies.size() > 18) {
                assemblies.keySet().stream().findFirst().ifPresent(assemblies::remove);
            }
        }
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

    public void sendGroupAudio(UUID senderId, byte[] pcm, int length) throws IOException {
        if (closed.get() || senderId == null || pcm == null || length <= 0) return;
        int safeLen = Math.min(length, MAX_DATAGRAM - GROUP_AUDIO_HEADER);
        ByteBuffer buf = ByteBuffer.allocate(GROUP_AUDIO_HEADER + safeLen);
        buf.put(MAGIC);
        buf.put(KIND_GROUP_AUDIO);
        buf.putLong(callId.getMostSignificantBits());
        buf.putLong(callId.getLeastSignificantBits());
        buf.putLong(senderId.getMostSignificantBits());
        buf.putLong(senderId.getLeastSignificantBits());
        buf.putInt(audioSeq.incrementAndGet());
        buf.put(pcm, 0, safeLen);
        sendToGroupTargets(buf.array(), senderId);
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

    public void sendGroupVideoJpeg(UUID senderId, byte[] jpeg) throws IOException {
        if (closed.get() || senderId == null || jpeg == null || jpeg.length == 0 || jpeg.length > MAX_VIDEO_FRAME_BYTES) {
            return;
        }
        int frameId = videoFrameSeq.incrementAndGet();
        int chunkCount = (jpeg.length + MAX_GROUP_VIDEO_CHUNK - 1) / MAX_GROUP_VIDEO_CHUNK;
        if (chunkCount > 220) return;
        for (int idx = 0; idx < chunkCount; idx++) {
            if (closed.get()) return;
            int offset = idx * MAX_GROUP_VIDEO_CHUNK;
            int len = Math.min(MAX_GROUP_VIDEO_CHUNK, jpeg.length - offset);
            ByteBuffer buf = ByteBuffer.allocate(GROUP_VIDEO_HEADER + len);
            buf.put(MAGIC);
            buf.put(KIND_GROUP_VIDEO);
            buf.putLong(callId.getMostSignificantBits());
            buf.putLong(callId.getLeastSignificantBits());
            buf.putLong(senderId.getMostSignificantBits());
            buf.putLong(senderId.getLeastSignificantBits());
            buf.putInt(frameId);
            buf.putShort((short) idx);
            buf.putShort((short) chunkCount);
            buf.put(jpeg, offset, len);
            sendToGroupTargets(buf.array(), senderId);
        }
    }

    private void sendToGroupTargets(byte[] data, UUID excludeSender) throws IOException {
        InetSocketAddress single = remoteEndpoint;
        if (single != null) {
            socket.send(new DatagramPacket(data, data.length, single));
        }
        for (var entry : relayPeers.entrySet()) {
            if (entry.getKey().equals(excludeSender)) continue;
            try {
                socket.send(new DatagramPacket(data, data.length, entry.getValue()));
            } catch (IOException ignored) {}
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            relayPeers.clear();
            socket.close();
        }
    }
}
