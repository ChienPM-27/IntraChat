package vn.intrachat.transport;

import vn.intrachat.domain.PeerIdentity;
import vn.intrachat.protocol.*;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** One approved plaintext, unauthenticated, diagnostic-only TCP session. */
public final class TcpPeerSession implements AutoCloseable {
    private record Probe(long startedNanos, CompletableFuture<Duration> future) { }
    private final Socket socket;
    private final PeerIdentity local;
    private final PeerIdentity remote;
    private final UUID sessionId;
    private final JsonEnvelopeCodec codec;
    private final ExecutorService workers;
    private final Consumer<UUID> onClosed;
    private final ConcurrentMap<UUID, Probe> outstanding = new ConcurrentHashMap<>();
    private final OutboundBudget outbound = new OutboundBudget(
        NetworkConfig.MAX_OUTBOUND_FRAMES, NetworkConfig.MAX_QUEUED_BYTES);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "intrachat-heartbeat");
        thread.setDaemon(true);
        return thread;
    });
    private final Object writeLock = new Object();
    private final DiagnosticHeartbeat heartbeatPolicy = new DiagnosticHeartbeat(System::nanoTime);

    public TcpPeerSession(Socket socket, PeerIdentity local, PeerIdentity remote, UUID sessionId,
                          JsonEnvelopeCodec codec, ExecutorService workers, Consumer<UUID> onClosed) {
        this.socket = socket;
        this.local = local;
        this.remote = remote;
        this.sessionId = sessionId;
        this.codec = codec;
        this.workers = workers;
        this.onClosed = onClosed;
    }

    public UUID remoteId() { return remote.deviceId(); }
    public UUID sessionId() { return sessionId; }
    public PeerIdentity remote() { return remote; }
    public boolean isOpen() { return !closed.get() && !socket.isClosed(); }

    public void start() throws SocketException {
        socket.setSoTimeout(NetworkConfig.ACTIVE_READ_TIMEOUT_MS);
        workers.submit(this::readerLoop);
        heartbeat.scheduleAtFixedRate(() -> {
            if (closed.get()) return;
            switch (heartbeatPolicy.tick()) {
                case CLOSE -> close();
                case PING -> ping().exceptionally(error -> null);
                case NONE -> { /* closed by user; no stray probe */ }
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    /** Sends a synthetic test probe; no user message text is allowed in this phase. */
    public CompletableFuture<Duration> ping() {
        if (!isOpen()) return CompletableFuture.failedFuture(new IOException("Session disconnected"));
        if (outstanding.size() >= NetworkConfig.MAX_OUTBOUND_FRAMES) {
            return CompletableFuture.failedFuture(new ProtocolException("CAPACITY_EXCEEDED", "Too many pending probes"));
        }
        UUID id = UUID.randomUUID();
        CompletableFuture<Duration> future = new CompletableFuture<>();
        outstanding.put(id, new Probe(System.nanoTime(), future));
        // On timeout/cancellation, release this probe slot even when no PONG arrives.
        future.whenComplete((duration, failure) -> outstanding.remove(id));
        NetworkEnvelope envelope = NetworkEnvelope.create(MessageType.DIAGNOSTIC_PING, local,
            remote.deviceId(), Map.of("probeId", id.toString(), "nonce", "intrachat-m1-test"));
        workers.submit(() -> {
            try { send(envelope); }
            catch (IOException failure) {
                Probe pending = outstanding.remove(id);
                if (pending != null) pending.future().completeExceptionally(failure);
                close();
            }
        });
        return future.orTimeout(32, TimeUnit.SECONDS);
    }

    private void send(NetworkEnvelope envelope) throws IOException {
        byte[] bytes = codec.encode(envelope);
        OutboundBudget.Reservation reserved = outbound.tryReserve(bytes.length);
        if (reserved == null)
            throw new ProtocolException("CAPACITY_EXCEEDED", "Session frame/byte queue capacity reached");
        try (reserved) {
            synchronized (writeLock) {
                if (closed.get()) throw new IOException("Session is closed");
                FrameCodec.writeFrame(socket.getOutputStream(), bytes, FrameCodec.CONTROL_LIMIT);
            }
        }
    }

    private void readerLoop() {
        try {
            while (!closed.get()) {
                try {
                    byte[] raw = FrameCodec.readFrame(socket.getInputStream(), FrameCodec.CONTROL_LIMIT);
                    NetworkEnvelope incoming = codec.decode(raw);
                    if (!incoming.sender().deviceId().equals(remote.deviceId())
                            || !incoming.destination().id().equals(local.deviceId())) {
                        throw new ProtocolException("HANDSHAKE_INVALID", "Unexpected diagnostic endpoint identity");
                    }
                    heartbeatPolicy.recordInbound();
                    switch (incoming.type()) {
                        case DIAGNOSTIC_PING -> {
                            String probeId = HandshakeValidator.requireUuid(incoming.payload(), "probeId").toString();
                            Map<String,Object> response = Map.of("probeId", probeId, "nonce", "intrachat-m1-test");
                            send(NetworkEnvelope.create(MessageType.DIAGNOSTIC_PONG, local, remote.deviceId(), response));
                        }
                        case DIAGNOSTIC_PONG -> {
                            UUID id = HandshakeValidator.requireUuid(incoming.payload(), "probeId");
                            Probe probe = outstanding.remove(id);
                            if (probe != null) probe.future().complete(Duration.ofNanos(
                                Math.max(0, System.nanoTime()-probe.startedNanos())));
                        }
                        default -> throw new ProtocolException("UNSUPPORTED_TYPE_FOR_PHASE", "Only diagnostics after READY");
                    }
                } catch (SocketTimeoutException idle) {
                    if (heartbeatPolicy.silenceExpired()) break;
                }
            }
        } catch (IOException | RuntimeException remoteFailure) {
            // Error classification surfaced by coordinator; never log peer payload.
        } finally {
            close();
        }
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        heartbeatPolicy.close();
        heartbeat.shutdownNow();
        try { socket.close(); } catch (IOException ignored) { }
        IOException error = new IOException("Diagnostic session disconnected");
        for (Probe probe : outstanding.values()) probe.future().completeExceptionally(error);
        outstanding.clear();
        onClosed.accept(remote.deviceId());
    }
}
