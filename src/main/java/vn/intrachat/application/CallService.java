package vn.intrachat.application;

import vn.intrachat.protocol.WireMessage;
import vn.intrachat.transport.CallMediaTransport;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.InetAddress;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * P2P Voice and Video Call coordinator over TCP signaling (WireMessage) + UDP media (CallMediaTransport).
 * Supports one active call at a time with mute, camera toggle, and screen-share switching.
 */
public final class CallService implements AutoCloseable {
    public static final long RING_TIMEOUT_SECONDS = 45;

    public enum Mode { VOICE, VIDEO }
    public enum State { OUTGOING_RINGING, INCOMING_RINGING, CONNECTED, ENDED }

    public record CallSession(
            UUID callId,
            UUID conversationId,
            UUID peerId,
            String peerName,
            Mode mode,
            State state,
            boolean incoming,
            boolean muted,
            boolean cameraOn,
            boolean screenShare,
            boolean remoteMuted,
            boolean remoteCameraOn,
            Instant connectedAt,
            String reason) {}

    @FunctionalInterface
    public interface Sender {
        void send(UUID peerId, WireMessage.Type type, UUID conversationId, Map<String, String> payload) throws IOException;
    }

    private final UUID localId;
    private final Sender sender;
    private final boolean hardwareMedia;
    private final Object lock = new Object();
    private final CopyOnWriteArrayList<Consumer<CallSession>> stateListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<BufferedImage>> localVideoListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<BufferedImage>> remoteVideoListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<String>> noticeListeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "intrachat-call-timer");
        t.setDaemon(true);
        return t;
    });
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private ActiveCall active;
    private volatile boolean closed;

    private static final class ActiveCall {
        final UUID callId;
        final UUID conversationId;
        final UUID peerId;
        final String peerName;
        final Mode mode;
        final boolean incoming;
        State state;
        boolean muted;
        boolean cameraOn;
        boolean screenShare;
        boolean remoteMuted;
        boolean remoteCameraOn;
        Instant connectedAt;
        String reason;
        InetAddress remoteAddress;
        int remoteUdpPort;
        CallMediaTransport transport;
        AudioCallEngine audio;
        VideoCallEngine video;
        ScheduledFuture<?> ringTimeout;

        ActiveCall(UUID callId, UUID conversationId, UUID peerId, String peerName, Mode mode, boolean incoming, State state) {
            this.callId = callId;
            this.conversationId = conversationId;
            this.peerId = peerId;
            this.peerName = peerName == null || peerName.isBlank() ? peerId.toString() : peerName;
            this.mode = mode;
            this.incoming = incoming;
            this.state = state;
            this.cameraOn = mode == Mode.VIDEO;
            this.remoteCameraOn = mode == Mode.VIDEO;
        }

        CallSession snapshot() {
            return new CallSession(callId, conversationId, peerId, peerName, mode, state, incoming,
                muted, cameraOn, screenShare, remoteMuted, remoteCameraOn, connectedAt, reason);
        }
    }

    public CallService(UUID localId, Sender sender) {
        this(localId, sender, true);
    }

    CallService(UUID localId, Sender sender, boolean hardwareMedia) {
        this.localId = Objects.requireNonNull(localId, "localId");
        this.sender = Objects.requireNonNull(sender, "sender");
        this.hardwareMedia = hardwareMedia;
    }

    public void addStateListener(Consumer<CallSession> listener) {
        stateListeners.add(Objects.requireNonNull(listener));
    }

    public void addLocalVideoListener(Consumer<BufferedImage> listener) {
        localVideoListeners.add(Objects.requireNonNull(listener));
    }

    public void addRemoteVideoListener(Consumer<BufferedImage> listener) {
        remoteVideoListeners.add(Objects.requireNonNull(listener));
    }

    public void addNoticeListener(Consumer<String> listener) {
        noticeListeners.add(Objects.requireNonNull(listener));
    }

    public CallSession currentCall() {
        synchronized (lock) {
            return active == null ? null : active.snapshot();
        }
    }

    CallMediaTransport currentTransport() {
        synchronized (lock) {
            return active == null ? null : active.transport;
        }
    }

    public CallSession startCall(UUID peerId, UUID conversationId, String peerName, Mode mode, InetAddress remoteAddress) throws IOException {
        Objects.requireNonNull(peerId, "peerId");
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(remoteAddress, "remoteAddress");
        if (localId.equals(peerId)) throw new IOException("Không thể tự gọi chính mình");

        ActiveCall call;
        int localPort;
        synchronized (lock) {
            if (closed) throw new IOException("Dịch vụ cuộc gọi đã đóng");
            if (active != null) throw new IOException("Đang trong một cuộc gọi khác");
            UUID callId = UUID.randomUUID();
            CallMediaTransport transport = new CallMediaTransport(callId);
            call = new ActiveCall(callId, conversationId, peerId, peerName, mode, false, State.OUTGOING_RINGING);
            call.remoteAddress = remoteAddress;
            call.transport = transport;
            localPort = transport.localPort();
            call.ringTimeout = scheduleRingTimeout(callId);
            active = call;
        }

        CallSession snapshot = call.snapshot();
        emitState(snapshot);
        try {
            sender.send(peerId, WireMessage.Type.CALL_OFFER, conversationId, Map.of(
                "callId", call.callId.toString(),
                "mode", mode.name(),
                "udpPort", Integer.toString(localPort)));
            return snapshot;
        } catch (IOException ex) {
            terminateCall(call.callId, "Không thể gửi lời mời gọi: " + ex.getMessage(), false, WireMessage.Type.CALL_END);
            throw ex;
        }
    }

    public void acceptCall(UUID callId) throws IOException {
        Objects.requireNonNull(callId, "callId");
        ActiveCall call;
        int localPort;
        synchronized (lock) {
            if (closed || active == null || !active.callId.equals(callId)
                    || active.state != State.INCOMING_RINGING || !active.incoming) {
                throw new IOException("Cuộc gọi đến không còn hiệu lực");
            }
            call = active;
            cancelTimeout(call);
            CallMediaTransport transport = new CallMediaTransport(call.callId);
            transport.setRemote(call.remoteAddress, call.remoteUdpPort);
            call.transport = transport;
            localPort = transport.localPort();
            call.state = State.CONNECTED;
            call.connectedAt = Instant.now();
        }

        try {
            sender.send(call.peerId, WireMessage.Type.CALL_ACCEPT, call.conversationId, Map.of(
                "callId", call.callId.toString(),
                "udpPort", Integer.toString(localPort)));
        } catch (IOException ex) {
            terminateCall(call.callId, "Không thể kết nối cuộc gọi: " + ex.getMessage(), false, WireMessage.Type.CALL_END);
            throw ex;
        }

        startMedia(call);
        emitState(call.snapshot());
    }

    public void rejectCall(UUID callId, String reason) {
        String text = (reason == null || reason.isBlank()) ? "Đã từ chối cuộc gọi" : reason;
        terminateCall(callId, text, true, WireMessage.Type.CALL_REJECT);
    }

    public void endCall(UUID callId, String reason) {
        String text = (reason == null || reason.isBlank()) ? "Cuộc gọi đã kết thúc" : reason;
        terminateCall(callId, text, true, WireMessage.Type.CALL_END);
    }

    public void setMuted(boolean muted) {
        ActiveCall call;
        CallSession snapshot;
        synchronized (lock) {
            if (active == null || active.state == State.ENDED) return;
            call = active;
            call.muted = muted;
            if (call.audio != null) call.audio.setMuted(muted);
            snapshot = call.snapshot();
        }
        emitState(snapshot);
        sendMediaStateAsync(call);
    }

    public void setCameraOn(boolean on) {
        ActiveCall call;
        CallSession snapshot;
        synchronized (lock) {
            if (active == null || active.state == State.ENDED || active.mode != Mode.VIDEO) return;
            call = active;
            call.cameraOn = on;
            if (!on) call.screenShare = false;
            if (call.video != null) call.video.setCameraOn(on);
            snapshot = call.snapshot();
        }
        if (!on) emitLocalVideo(null);
        emitState(snapshot);
        sendMediaStateAsync(call);
    }

    public void setScreenShare(boolean enabled) {
        ActiveCall call;
        CallSession snapshot;
        synchronized (lock) {
            if (active == null || active.state == State.ENDED || active.mode != Mode.VIDEO) return;
            call = active;
            call.screenShare = enabled;
            if (enabled) call.cameraOn = true;
            if (call.video != null) call.video.setScreenShare(enabled);
            snapshot = call.snapshot();
        }
        emitState(snapshot);
        sendMediaStateAsync(call);
    }

    public void disconnected(UUID peerId) {
        UUID callId = null;
        synchronized (lock) {
            if (active != null && active.peerId.equals(peerId)) {
                callId = active.callId;
            }
        }
        if (callId != null) {
            terminateCall(callId, "Mất kết nối TCP với đối phương", false, WireMessage.Type.CALL_END);
        }
    }

    public void handleIncoming(UUID peerId, String peerName, InetAddress peerAddress, WireMessage frame) {
        if (!frame.type().name().startsWith("CALL_")) {
            throw new IllegalArgumentException("Not a call frame");
        }
        UUID callId;
        try {
            callId = UUID.fromString(frame.payload().get("callId"));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid callId", ex);
        }

        switch (frame.type()) {
            case CALL_OFFER -> handleOffer(peerId, peerName, peerAddress, frame.conversationId(), callId, frame.payload());
            case CALL_ACCEPT -> handleAccept(peerId, peerAddress, callId, frame.payload());
            case CALL_REJECT -> {
                String reason = frame.payload().getOrDefault("reason", "Đối phương đã từ chối cuộc gọi");
                terminateCallIfPeer(callId, peerId, reason);
            }
            case CALL_END -> {
                String reason = frame.payload().getOrDefault("reason", "Đối phương đã kết thúc cuộc gọi");
                terminateCallIfPeer(callId, peerId, reason);
            }
            case CALL_MEDIA_STATE -> handleMediaState(peerId, callId, frame.payload());
            default -> throw new IllegalArgumentException("Unexpected call frame: " + frame.type());
        }
    }

    private void handleOffer(UUID peerId, String peerName, InetAddress peerAddress, UUID conversationId,
                             UUID callId, Map<String, String> payload) {
        Mode mode;
        int remotePort;
        try {
            mode = Mode.valueOf(payload.getOrDefault("mode", "VOICE"));
            remotePort = Integer.parseInt(payload.getOrDefault("udpPort", "-1"));
            if (remotePort <= 0 || remotePort > 65535) throw new IllegalArgumentException("Invalid UDP port");
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid CALL_OFFER payload", ex);
        }

        CallSession incomingSession;
        synchronized (lock) {
            if (closed || active != null) {
                workers.submit(() -> {
                    try {
                        sender.send(peerId, WireMessage.Type.CALL_REJECT, conversationId, Map.of(
                            "callId", callId.toString(),
                            "reason", "Người nhận đang bận trong cuộc gọi khác"));
                    } catch (IOException ignored) {}
                });
                return;
            }
            ActiveCall call = new ActiveCall(callId, conversationId, peerId, peerName, mode, true, State.INCOMING_RINGING);
            call.remoteAddress = peerAddress;
            call.remoteUdpPort = remotePort;
            call.ringTimeout = scheduleRingTimeout(callId);
            active = call;
            incomingSession = call.snapshot();
        }
        emitState(incomingSession);
    }

    private void handleAccept(UUID peerId, InetAddress peerAddress, UUID callId, Map<String, String> payload) {
        int remotePort;
        try {
            remotePort = Integer.parseInt(payload.getOrDefault("udpPort", "-1"));
            if (remotePort <= 0 || remotePort > 65535) throw new IllegalArgumentException("Invalid UDP port");
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid CALL_ACCEPT payload", ex);
        }

        ActiveCall call;
        synchronized (lock) {
            if (active == null || !active.callId.equals(callId)
                    || !active.peerId.equals(peerId) || active.state != State.OUTGOING_RINGING) {
                return;
            }
            call = active;
            cancelTimeout(call);
            call.remoteAddress = peerAddress;
            call.remoteUdpPort = remotePort;
            call.transport.setRemote(peerAddress, remotePort);
            call.state = State.CONNECTED;
            call.connectedAt = Instant.now();
        }
        startMedia(call);
        emitState(call.snapshot());
    }

    private void handleMediaState(UUID peerId, UUID callId, Map<String, String> payload) {
        CallSession snapshot;
        boolean remoteCamOff;
        synchronized (lock) {
            if (active == null || !active.callId.equals(callId) || !active.peerId.equals(peerId)) return;
            active.remoteMuted = Boolean.parseBoolean(payload.getOrDefault("muted", "false"));
            active.remoteCameraOn = Boolean.parseBoolean(payload.getOrDefault("cameraOn", "false"));
            remoteCamOff = !active.remoteCameraOn;
            snapshot = active.snapshot();
        }
        if (remoteCamOff) emitRemoteVideo(null);
        emitState(snapshot);
    }

    private void startMedia(ActiveCall call) {
        synchronized (lock) {
            if (active != call || call.state != State.CONNECTED || call.transport == null) return;
            AudioCallEngine audio = null;
            VideoCallEngine video = null;
            if (hardwareMedia) {
                audio = new AudioCallEngine(call.transport, this::emitNotice);
                audio.setMuted(call.muted);
                call.audio = audio;
                if (call.mode == Mode.VIDEO) {
                    video = new VideoCallEngine(call.transport, this::emitLocalVideo, this::emitRemoteVideo, this::emitNotice);
                    call.video = video;
                }
            }
            AudioCallEngine audioRef = audio;
            VideoCallEngine videoRef = video;
            call.transport.startReceiving(
                pcm -> { if (audioRef != null) audioRef.enqueueIncomingAudio(pcm); },
                jpeg -> {
                    if (videoRef != null) {
                        videoRef.enqueueRemoteJpeg(jpeg);
                    } else if (call.mode == Mode.VIDEO) {
                        try {
                            BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(jpeg));
                            if (img != null) emitRemoteVideo(img);
                        } catch (Exception ignored) {}
                    }
                });
            if (audio != null) audio.start();
            if (video != null) video.start(call.cameraOn);
        }
    }

    private ScheduledFuture<?> scheduleRingTimeout(UUID callId) {
        return timer.schedule(() -> {
            boolean shouldEnd;
            synchronized (lock) {
                shouldEnd = active != null && active.callId.equals(callId)
                    && (active.state == State.OUTGOING_RINGING || active.state == State.INCOMING_RINGING);
            }
            if (shouldEnd) {
                terminateCall(callId, "Không có phản hồi (hết thời gian đổ chuông)", true, WireMessage.Type.CALL_END);
            }
        }, RING_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void terminateCallIfPeer(UUID callId, UUID peerId, String reason) {
        boolean matches;
        synchronized (lock) {
            matches = active != null && active.callId.equals(callId) && active.peerId.equals(peerId);
        }
        if (matches) {
            terminateCall(callId, reason, false, WireMessage.Type.CALL_END);
        }
    }

    private void terminateCall(UUID callId, String reason, boolean informPeer, WireMessage.Type signalType) {
        ActiveCall ended;
        synchronized (lock) {
            if (active == null || (callId != null && !active.callId.equals(callId))) return;
            ended = active;
            active = null;
            ended.state = State.ENDED;
            ended.reason = reason;
            cancelTimeout(ended);
        }
        stopMedia(ended);
        emitState(ended.snapshot());
        if (informPeer) {
            workers.submit(() -> {
                try {
                    sender.send(ended.peerId, signalType, ended.conversationId, Map.of(
                        "callId", ended.callId.toString(),
                        "reason", reason == null ? "" : reason));
                } catch (IOException ignored) {}
            });
        }
    }

    private void sendMediaStateAsync(ActiveCall call) {
        UUID callId = call.callId;
        UUID peerId = call.peerId;
        UUID convId = call.conversationId;
        boolean muted = call.muted;
        boolean cameraOn = call.cameraOn;
        workers.submit(() -> {
            try {
                sender.send(peerId, WireMessage.Type.CALL_MEDIA_STATE, convId, Map.of(
                    "callId", callId.toString(),
                    "muted", Boolean.toString(muted),
                    "cameraOn", Boolean.toString(cameraOn)));
            } catch (IOException ignored) {}
        });
    }

    private static void cancelTimeout(ActiveCall call) {
        if (call.ringTimeout != null) {
            call.ringTimeout.cancel(false);
            call.ringTimeout = null;
        }
    }

    private static void stopMedia(ActiveCall call) {
        if (call.audio != null) {
            try { call.audio.close(); } catch (Exception ignored) {}
            call.audio = null;
        }
        if (call.video != null) {
            try { call.video.close(); } catch (Exception ignored) {}
            call.video = null;
        }
        if (call.transport != null) {
            try { call.transport.close(); } catch (Exception ignored) {}
            call.transport = null;
        }
    }

    private void emitState(CallSession session) {
        for (var listener : stateListeners) {
            try { listener.accept(session); } catch (RuntimeException ignored) {}
        }
    }

    private void emitLocalVideo(BufferedImage frame) {
        for (var listener : localVideoListeners) {
            try { listener.accept(frame); } catch (RuntimeException ignored) {}
        }
    }

    private void emitRemoteVideo(BufferedImage frame) {
        for (var listener : remoteVideoListeners) {
            try { listener.accept(frame); } catch (RuntimeException ignored) {}
        }
    }

    private void emitNotice(String message) {
        for (var listener : noticeListeners) {
            try { listener.accept(message); } catch (RuntimeException ignored) {}
        }
    }

    @Override
    public void close() {
        closed = true;
        ActiveCall current;
        synchronized (lock) {
            current = active;
            active = null;
            if (current != null) {
                current.state = State.ENDED;
                current.reason = "Đóng ứng dụng";
                cancelTimeout(current);
            }
        }
        if (current != null) {
            stopMedia(current);
        }
        timer.shutdownNow();
        workers.shutdownNow();
    }
}
