package vn.intrachat.transport;

import vn.intrachat.protocol.WireCodec;
import vn.intrachat.protocol.WireMessage;
import java.io.*;
import java.net.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** One TCP connection, one reader worker, synchronized frame writes. */
public final class LanSession implements AutoCloseable {
    private final Socket socket;
    private final UUID localId, remoteId, sessionId = UUID.randomUUID();
    private final WireCodec codec = new WireCodec();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object writeLock = new Object();
    private Thread reader;

    public LanSession(Socket socket, UUID localId, UUID remoteId) throws SocketException {
        this.socket = java.util.Objects.requireNonNull(socket);
        this.localId = java.util.Objects.requireNonNull(localId);
        this.remoteId = java.util.Objects.requireNonNull(remoteId);
        if (localId.equals(remoteId)) throw new IllegalArgumentException("Peer UUID equals local UUID");
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(NetworkConfig.ACTIVE_READ_TIMEOUT_MS);
    }
    public UUID localId() { return localId; }
    public UUID remoteId() { return remoteId; }
    public UUID sessionId() { return sessionId; }
    public InetAddress remoteAddress() { return socket.getInetAddress(); }
    public InetAddress localAddress() { return socket.getLocalAddress(); }
    public boolean isOpen() { return !closed.get() && !socket.isClosed(); }
    // Gửi một gói tin qua socket của phiên hiện tại.
    public void send(WireMessage message) throws IOException {
        if (!localId.equals(message.senderId()) || !remoteId.equals(message.recipientId()))
            throw new IOException("Message endpoints mismatch session");
        // Khóa ghi để các frame từ nhiều thread không bị xen lẫn.
        synchronized (writeLock) {
            if (!isOpen()) throw new IOException("Session closed");
            try { codec.write(socket.getOutputStream(), message); }
            catch (IOException error) { close(); throw error; }
        }
    }
    public synchronized void start(Consumer<WireMessage> onMessage, Consumer<Throwable> onDisconnect) {
        if (reader != null) throw new IllegalStateException("Session reader already running");
        // Virtual thread riêng đọc socket liên tục, không chặn giao diện.
        reader = Thread.ofVirtual().name("intrachat-tcp-" + remoteId).start(() -> {
            Throwable cause = null;
            try {
                while (isOpen()) {
                    try {
                        // Đọc đủ một frame và giải mã thành WireMessage.
                        WireMessage incoming = codec.read(socket.getInputStream());
                        if (!remoteId.equals(incoming.senderId()) || !localId.equals(incoming.recipientId()))
                            throw new IOException("Frame sender/recipient differs from session");
                        // Chuyển gói hợp lệ cho tầng xử lý nghiệp vụ.
                        onMessage.accept(incoming);
                    } catch (SocketTimeoutException idle) { /* idle, poll closed state */ }
                }
            } catch (Throwable failure) { cause = failure; }
            finally {
                close();
                onDisconnect.accept(cause);
            }
        });
    }
    @Override public void close() {
        if (closed.compareAndSet(false, true)) {
            try { socket.close(); } catch (IOException ignored) { }
        }
    }
}
