package vn.intrachat.application;

import vn.intrachat.domain.PeerIdentity;
import vn.intrachat.protocol.*;
import vn.intrachat.transport.*;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Coordinates M1 direct LAN P2P and user consent.
 * All socket operations run on workers, never the Swing EDT.
 * A CONNECTED session permits only synthetic diagnostics; it is NOT authenticated or encrypted.
 */
public final class NetworkCoordinator implements AutoCloseable {
    private final PeerIdentity localPeer;
    private final InboundApprovalPort approvalPort;
    private final Consumer<NetworkEvents.Event> events;
    private final JsonEnvelopeCodec codec = new JsonEnvelopeCodec();
    private final SessionRegistry registry = new SessionRegistry();
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<UUID,TcpPeerSession> sessions = new ConcurrentHashMap<>();
    // Admissions and close callbacks must be atomic together, not separately per registry/map.
    private final Object sessionAdmissionLock = new Object();
    private final AtomicBoolean connecting = new AtomicBoolean();
    private final AtomicBoolean connectCancelled = new AtomicBoolean();
    private volatile TcpListener listener;
    private volatile Socket connectingSocket;
    private volatile NetworkConfig previousRemote;
    private volatile boolean closed;

    public NetworkCoordinator(PeerIdentity localPeer, InboundApprovalPort approvalPort,
                              Consumer<NetworkEvents.Event> events) {
        this.localPeer = Objects.requireNonNull(localPeer, "localPeer");
        this.approvalPort = Objects.requireNonNull(approvalPort, "approvalPort");
        this.events = Objects.requireNonNull(events, "events");
    }

    public PeerIdentity localPeer() { return localPeer; }
    public int connectedCount() { return sessions.size(); }
    public boolean isConnectedTo(UUID remoteId) {
        TcpPeerSession session = sessions.get(remoteId);
        return session != null && session.isOpen();
    }
    public int pendingApprovalCount() { return registry.pendingCount(); }
    public boolean isListening() { return listener != null && listener.isListening(); }

    public synchronized void startListening(NetworkConfig bind) throws IOException {
        requireOpen();
        if (listener != null) {
            if (listener.isListening()) throw new IllegalStateException("Already listening");
            listener.close();
        }
        TcpListener next = new TcpListener();
        try {
            next.start(bind, this::handleIncoming);
            listener = next;
            publish(NetworkEvents.Kind.LISTENING, "Đang lắng nghe " + bind.ipv4() + ":" + bind.port(), null);
        } catch (IOException | RuntimeException failure) {
            next.close();
            publish(NetworkEvents.Kind.FAILED, "Không thể mở cổng: " + failure.getMessage(), null);
            throw failure;
        }
    }

    public synchronized void stopListening() {
        if (listener != null) {
            listener.close();
            listener = null;
            publish(NetworkEvents.Kind.LISTENER_STOPPED, "Đã dừng lắng nghe", null);
        }
    }

    public CompletableFuture<PeerIdentity> connect(NetworkConfig remote) {
        Objects.requireNonNull(remote);
        if (closed) return CompletableFuture.failedFuture(new IOException("Application is closing"));
        if (!connecting.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("A connection is already pending"));
        }
        connectCancelled.set(false);
        previousRemote = remote;
        publish(NetworkEvents.Kind.CONNECTING,
            "Đang kết nối " + remote.ipv4() + ":" + remote.port() + " — chưa xác thực", null);
        return CompletableFuture.supplyAsync(() -> {
            Socket socket = null;
            boolean handedOff = false;
            try {
                socket = TcpConnector.connect(remote);
                connectingSocket = socket;
                requireConnectNotCancelled();
                UUID requestId = UUID.randomUUID();
                Map<String,Object> helloBody = Map.of(
                    "requestId", requestId.toString(),
                    "supportedVersions", java.util.List.of(1),
                    "clientVersion", "0.1.0-SNAPSHOT");
                send(socket, NetworkEnvelope.create(MessageType.HELLO, localPeer,
                    NetworkEnvelope.UNKNOWN_PEER, helloBody), FrameCodec.HELLO_LIMIT);
                publish(NetworkEvents.Kind.WAITING_REMOTE_APPROVAL,
                    "Đang chờ người nhận Chấp nhận/Từ chối (tối đa 30 giây)", null);
                socket.setSoTimeout(35_000);
                NetworkEnvelope response = receive(socket, FrameCodec.HELLO_LIMIT);
                if (response.type() == MessageType.HELLO_REJECT) {
                    throw new ProtocolException("USER_REJECTED", "Người nhận đã từ chối kết nối");
                }
                if (response.type() != MessageType.HELLO_ACCEPT) {
                    throw new ProtocolException("HANDSHAKE_INVALID", "Expected HELLO_ACCEPT");
                }
                if (!requestId.equals(HandshakeValidator.requireUuid(response.payload(), "requestId"))
                        || response.sender().deviceId().equals(localPeer.deviceId())
                        || !response.destination().id().equals(localPeer.deviceId())) {
                    throw new ProtocolException("HANDSHAKE_INVALID", "Unexpected HELLO_ACCEPT identity/correlation");
                }
                requireConnectNotCancelled();
                UUID sessionId = HandshakeValidator.requireUuid(response.payload(), "sessionId");
                PeerIdentity peer = response.sender(); // claimed, NOT verified!
                send(socket, NetworkEnvelope.create(MessageType.HELLO_CONFIRM, localPeer,
                    peer.deviceId(), Map.of("requestId", requestId.toString(),
                        "sessionId", sessionId.toString(), "chosenVersion", 1)),
                    FrameCodec.HELLO_LIMIT);
                socket.setSoTimeout(NetworkConfig.HELLO_TIMEOUT_MS);
                NetworkEnvelope ready = receive(socket, FrameCodec.HELLO_LIMIT);
                if (ready.type() != MessageType.HELLO_READY
                        || !ready.sender().deviceId().equals(peer.deviceId())
                        || !ready.destination().id().equals(localPeer.deviceId())
                        || !sessionId.equals(HandshakeValidator.requireUuid(ready.payload(), "sessionId"))) {
                    throw new ProtocolException("HANDSHAKE_INVALID", "Expected matching HELLO_READY");
                }
                requireConnectNotCancelled();
                TcpPeerSession session = new TcpPeerSession(socket, localPeer, peer, sessionId, codec,
                    workers, id -> removeSession(id, sessionId));
                TcpPeerSession previous;
                synchronized (sessionAdmissionLock) {
                    SessionRegistry.Reservation reservedSession = registry.reservePeer(
                        peer.deviceId(), sessionId, SessionRegistry.Direction.OUTBOUND, localPeer.deviceId());
                    if (reservedSession == SessionRegistry.Reservation.REJECTED) {
                        throw new ProtocolException("CAPACITY_EXCEEDED",
                            "Peer already connected on preferred connection or limit reached");
                    }
                    previous = sessions.put(peer.deviceId(), session);
                }
                try {
                    session.start();
                    handedOff = true;
                } catch (IOException | RuntimeException startupFailure) {
                    session.close();
                    throw startupFailure;
                }
                if (previous != null) previous.close();
                if (connectCancelled.get() || closed) {
                    session.close();
                    throw new IOException("Connection attempt was cancelled");
                }
                if (!session.isOpen()) throw new IOException("Connection closed before diagnostic READY");
                publish(NetworkEvents.Kind.CONNECTED, "Đã kết nối chẩn đoán với "
                    + peer.displayName() + " (chưa xác thực, chưa mã hóa)", peer.deviceId());
                return peer;
            } catch (IOException failure) {
                publish(NetworkEvents.Kind.FAILED, "Kết nối thất bại: " + failure.getMessage(), null);
                throw new CompletionException(failure);
            } finally {
                connecting.set(false);
                connectingSocket = null;
                if (!handedOff && socket != null) {
                    try { socket.close(); } catch (IOException ignored) { }
                }
            }
        }, workers);
    }

    /** Explicit user command only, never invoked on a timer or socket close. */
    public CompletableFuture<PeerIdentity> reconnect() {
        NetworkConfig previous = previousRemote;
        if (previous == null) return CompletableFuture.failedFuture(
            new IllegalStateException("Chưa có địa chỉ IP/cổng để kết nối lại"));
        return connect(previous);
    }

    public void cancelPendingConnect() {
        if (connecting.get()) connectCancelled.set(true);
        Socket socket = connectingSocket;
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    public CompletableFuture<Duration> ping(UUID remoteId) {
        TcpPeerSession session = sessions.get(remoteId);
        if (session == null || !session.isOpen()) {
            return CompletableFuture.failedFuture(new IOException("Peer is not connected"));
        }
        CompletableFuture<Duration> result = session.ping();
        result.thenAccept(duration ->
            publish(NetworkEvents.Kind.DIAGNOSTIC,
                "PONG chẩn đoán từ " + remoteId + " (" + duration.toMillis() + " ms)", remoteId));
        return result;
    }

    public void disconnect(UUID remoteId) {
        TcpPeerSession session = sessions.remove(remoteId);
        if (session != null) session.close();
    }

    public void disconnectAll() {
        for (TcpPeerSession session : sessions.values()) session.close();
        sessions.clear();
    }

    private void handleIncoming(Socket candidate) {
        handleIncoming(candidate,null);
    }

    /**
     * Called only after SharedProtocolListener strictly validates the first M1 HELLO.
     * M1 device UUID is still an untrusted plaintext claim.
     */
    public void acceptSharedCandidate(Socket candidate,byte[] validatedFirstFrame) {
        Objects.requireNonNull(validatedFirstFrame,"validatedFirstFrame");
        handleIncoming(candidate,validatedFirstFrame);
    }

    private void handleIncoming(Socket candidate,byte[] firstFrame) {
        if (closed || !registry.tryReservePending()) {
            try { candidate.close(); } catch (IOException ignored) { }
            return;
        }
        boolean handedOff = false;
        UUID claimedId = null;
        UUID reservedSessionId = null;
        boolean reserved = false;
        try {
            candidate.setSoTimeout(NetworkConfig.HELLO_TIMEOUT_MS);
            NetworkEnvelope hello = firstFrame==null
                ? receive(candidate, FrameCodec.HELLO_LIMIT)
                : codec.decode(firstFrame);
            UUID requestId = HandshakeValidator.validateHello(hello);
            PeerIdentity peer = hello.sender(); // UNVERIFIED network claim.
            claimedId = peer.deviceId();
            if (claimedId.equals(localPeer.deviceId())) {
                throw new ProtocolException("HANDSHAKE_INVALID", "Same local and remote device ID");
            }
            InboundRequest request = new InboundRequest(requestId,
                candidate.getRemoteSocketAddress().toString(), peer, Instant.now().plusSeconds(30));
            publish(NetworkEvents.Kind.PENDING_APPROVAL,
                "Yêu cầu kết nối đến từ " + request.remoteEndpoint() + " (chưa xác thực)", peer.deviceId());
            InboundApprovalPort.Decision decision;
            try {
                decision = approvalPort.requestApproval(request).toCompletableFuture()
                    .get(NetworkConfig.APPROVAL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException | CancellationException | ExecutionException | InterruptedException cancelled) {
                decision = InboundApprovalPort.Decision.REJECT;
                if (cancelled instanceof InterruptedException) Thread.currentThread().interrupt();
            }
            if (decision != InboundApprovalPort.Decision.ACCEPT || closed) {
                sendReject(candidate, requestId, claimedId, "USER_REJECTED");
                return;
            }
            UUID sessionId = UUID.randomUUID();
            send(candidate, NetworkEnvelope.create(MessageType.HELLO_ACCEPT, localPeer, peer.deviceId(),
                Map.of("requestId", requestId.toString(), "sessionId", sessionId.toString(),
                    "chosenVersion", 1)), FrameCodec.HELLO_LIMIT);
            candidate.setSoTimeout(NetworkConfig.HELLO_TIMEOUT_MS);
            NetworkEnvelope confirm = receive(candidate, FrameCodec.HELLO_LIMIT);
            if (confirm.type() != MessageType.HELLO_CONFIRM
                    || !confirm.sender().deviceId().equals(peer.deviceId())
                    || !confirm.destination().id().equals(localPeer.deviceId())
                    || !requestId.equals(HandshakeValidator.requireUuid(confirm.payload(), "requestId"))
                    || !sessionId.equals(HandshakeValidator.requireUuid(confirm.payload(), "sessionId"))
                    || !(confirm.payload().get("chosenVersion") instanceof Number version)
                    || version.intValue() != 1) {
                throw new ProtocolException("HANDSHAKE_INVALID", "HELLO_CONFIRM mismatch/version");
            }
            TcpPeerSession session = new TcpPeerSession(candidate, localPeer, peer, sessionId,
                codec, workers, id -> removeSession(id, sessionId));
            TcpPeerSession previous;
            SessionRegistry.Reservation reservedSession;
            synchronized (sessionAdmissionLock) {
                reservedSession = registry.reservePeer(
                    claimedId, sessionId, SessionRegistry.Direction.INBOUND, localPeer.deviceId());
                previous = reservedSession == SessionRegistry.Reservation.REJECTED
                    ? null : sessions.put(peer.deviceId(), session);
            }
            if (reservedSession == SessionRegistry.Reservation.REJECTED) {
                sendReject(candidate, requestId, claimedId, "CAPACITY_EXCEEDED");
                return;
            }
            reserved = true;
            reservedSessionId = sessionId;
            send(candidate, NetworkEnvelope.create(MessageType.HELLO_READY, localPeer, peer.deviceId(),
                Map.of("sessionId", sessionId.toString())), FrameCodec.HELLO_LIMIT);
            try {
                session.start();
                handedOff = true;
            } catch (IOException | RuntimeException startupFailure) {
                session.close();
                throw startupFailure;
            }
            if (previous != null) previous.close();
            if (!session.isOpen()) throw new IOException("Connection closed before diagnostic READY");
            publish(NetworkEvents.Kind.CONNECTED, "Chấp nhận kết nối chẩn đoán: "
                + peer.displayName() + " — chưa xác thực/chưa mã hóa", peer.deviceId());
        } catch (IOException | RuntimeException failure) {
            publish(NetworkEvents.Kind.FAILED,
                "Yêu cầu đến không thành công: " + failure.getClass().getSimpleName(), claimedId);
        } finally {
            registry.releasePending();
            if (!handedOff) {
                if (reserved && claimedId != null) registry.releasePeer(claimedId, reservedSessionId);
                try { candidate.close(); } catch (IOException ignored) { }
            }
        }
    }

    private void sendReject(Socket candidate, UUID requestId, UUID peerId, String reason) {
        try {
            send(candidate, NetworkEnvelope.create(MessageType.HELLO_REJECT, localPeer, peerId,
                Map.of("requestId", requestId.toString(), "reasonCode", reason)),
                FrameCodec.HELLO_LIMIT);
        } catch (IOException ignored) {
            // Reject is best-effort; never hang waiting to send an error.
        }
    }

    private void send(Socket socket, NetworkEnvelope envelope, int cap) throws IOException {
        FrameCodec.writeFrame(socket.getOutputStream(), codec.encode(envelope), cap);
    }

    private NetworkEnvelope receive(Socket socket, int cap) throws IOException {
        return codec.decode(FrameCodec.readFrame(socket.getInputStream(), cap));
    }

    private void removeSession(UUID remoteId, UUID sessionId) {
        boolean removed = false;
        synchronized (sessionAdmissionLock) {
            TcpPeerSession active = sessions.get(remoteId);
            if (active != null && sessionId.equals(active.sessionId()))
                removed = sessions.remove(remoteId, active);
            // Old-session callbacks may release only their own lease, never a replacement.
            registry.releasePeer(remoteId, sessionId);
        }
        if (removed)
            publish(NetworkEvents.Kind.DISCONNECTED,
                "Đã ngắt kết nối; chỉ thử lại khi bạn bấm Kết nối lại", remoteId);
    }

    private void publish(NetworkEvents.Kind kind, String text, UUID peerId) {
        try { events.accept(new NetworkEvents.Event(kind, text, peerId)); }
        catch (RuntimeException ignored) { /* UI callbacks may have been disposed. */ }
    }

    private void requireConnectNotCancelled() throws IOException {
        if (connectCancelled.get() || closed)
            throw new IOException("Connection attempt was cancelled");
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Coordinator is closed");
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        cancelPendingConnect();
        stopListening();
        disconnectAll();
        registry.close();
        workers.shutdownNow();
    }
}
