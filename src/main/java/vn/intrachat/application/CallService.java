package vn.intrachat.application;

import vn.intrachat.domain.ChatGroup;
import vn.intrachat.protocol.WireMessage;
import vn.intrachat.transport.CallMediaTransport;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Unified P2P and Owner-Relayed Group Voice/Video Call coordinator over TCP signaling + UDP media.
 * Supports one active call at a time with mute, camera toggle, screen-share, and multi-participant SFU relay.
 */
public final class CallService implements AutoCloseable {
    public static final long RING_TIMEOUT_SECONDS = 45;

    public enum Mode { VOICE, VIDEO }
    public enum State { OUTGOING_RINGING, INCOMING_RINGING, CONNECTED, ENDED }

    public record Participant(UUID id, String name, boolean muted, boolean cameraOn, boolean self) {}

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
            String reason,
            boolean group,
            List<Participant> participants) {}

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
    private final CopyOnWriteArrayList<BiConsumer<UUID, BufferedImage>> participantVideoListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<String>> noticeListeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "intrachat-call-timer");
        t.setDaemon(true);
        return t;
    });
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final ExecutorService mediaStates = Executors.newSingleThreadExecutor(
        Thread.ofVirtual().name("intrachat-call-state").factory());
    private ActiveCall active;
    private volatile boolean closed;

    private static final class ActiveCall {
        final UUID callId;
        final UUID conversationId;
        final UUID peerId;      // P2P remote peer OR group ownerId
        final String peerName;  // P2P remote name OR group display title
        final Mode mode;
        final boolean incoming;
        final boolean group;
        final boolean groupOwnerRelay;
        final String localDisplayName;
        final LinkedHashMap<UUID, Participant> participants = new LinkedHashMap<>();
        final Set<UUID> invitedGroupMembers = new LinkedHashSet<>();
        boolean ownerJoined;
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

        ActiveCall(UUID callId, UUID conversationId, UUID peerId, String peerName, Mode mode,
                   boolean incoming, State state, boolean group, boolean groupOwnerRelay, String localDisplayName) {
            this.callId = callId;
            this.conversationId = conversationId;
            this.peerId = peerId;
            this.peerName = peerName == null || peerName.isBlank() ? peerId.toString() : peerName;
            this.mode = mode;
            this.incoming = incoming;
            this.group = group;
            this.groupOwnerRelay = groupOwnerRelay;
            this.localDisplayName = localDisplayName == null || localDisplayName.isBlank() ? "Bạn" : localDisplayName;
            this.state = state;
            this.cameraOn = mode == Mode.VIDEO;
            this.remoteCameraOn = mode == Mode.VIDEO;
        }

        CallSession snapshot() {
            return new CallSession(callId, conversationId, peerId, peerName, mode, state, incoming,
                muted, cameraOn, screenShare, remoteMuted, remoteCameraOn, connectedAt, reason,
                group, List.copyOf(participants.values()));
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

    public void addParticipantVideoListener(BiConsumer<UUID, BufferedImage> listener) {
        participantVideoListeners.add(Objects.requireNonNull(listener));
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

    // ==================== P2P CALL ====================

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
            call = new ActiveCall(callId, conversationId, peerId, peerName, mode, false,
                State.OUTGOING_RINGING, false, false, null);
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

    // ==================== GROUP CALL (OWNER-RELAY SFU) ====================

    public CallSession startGroupCall(ChatGroup group, String localName, Mode mode,
                                      Map<UUID, InetAddress> peerAddresses) throws IOException {
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(peerAddresses, "peerAddresses");
        if (!group.contains(localId)) throw new IOException("Bạn không phải thành viên của nhóm này");

        boolean isOwner = group.ownerId().equals(localId);
        if (isOwner) {
            List<UUID> onlineMembers = group.memberIds().stream()
                .filter(id -> !id.equals(localId) && peerAddresses.containsKey(id))
                .toList();
            if (onlineMembers.isEmpty()) {
                throw new IOException("Không có thành viên nào khác đang trực tuyến trong nhóm.");
            }
            ActiveCall call;
            int ownerUdpPort;
            synchronized (lock) {
                if (closed) throw new IOException("Dịch vụ cuộc gọi đã đóng");
                if (active != null) throw new IOException("Đang trong một cuộc gọi khác");
                UUID callId = UUID.randomUUID();
                CallMediaTransport transport = new CallMediaTransport(callId);
                call = new ActiveCall(callId, group.groupId(), localId, group.groupName(), mode, false,
                    State.CONNECTED, true, true, localName);
                call.ownerJoined = true;
                call.connectedAt = Instant.now();
                call.transport = transport;
                call.invitedGroupMembers.addAll(onlineMembers);
                call.participants.put(localId, new Participant(localId, call.localDisplayName, false, mode == Mode.VIDEO, true));
                ownerUdpPort = transport.localPort();
                active = call;
            }
            startGroupMedia(call, true);
            CallSession snapshot = call.snapshot();
            emitState(snapshot);

            Map<String, String> payload = Map.of(
                "callId", call.callId.toString(),
                "mode", mode.name(),
                "udpPort", Integer.toString(ownerUdpPort),
                "groupName", group.groupName(),
                "initiatorId", localId.toString(),
                "initiatorName", call.localDisplayName);
            for (UUID member : onlineMembers) {
                workers.submit(() -> {
                    try { sender.send(member, WireMessage.Type.GROUP_CALL_START, group.groupId(), payload); }
                    catch (IOException ignored) {}
                });
            }
            return snapshot;
        } else {
            InetAddress ownerAddr = peerAddresses.get(group.ownerId());
            if (ownerAddr == null) {
                throw new IOException("Chủ nhóm đang ngoại tuyến — không thể mở cuộc gọi nhóm.");
            }
            ActiveCall call;
            int localPort;
            synchronized (lock) {
                if (closed) throw new IOException("Dịch vụ cuộc gọi đã đóng");
                if (active != null) throw new IOException("Đang trong một cuộc gọi khác");
                UUID callId = UUID.randomUUID();
                CallMediaTransport transport = new CallMediaTransport(callId);
                call = new ActiveCall(callId, group.groupId(), group.ownerId(), group.groupName(), mode, false,
                    State.OUTGOING_RINGING, true, false, localName);
                call.remoteAddress = ownerAddr;
                call.transport = transport;
                localPort = transport.localPort();
                call.ringTimeout = scheduleRingTimeout(callId);
                active = call;
            }
            CallSession snapshot = call.snapshot();
            emitState(snapshot);
            try {
                sender.send(group.ownerId(), WireMessage.Type.GROUP_CALL_START, group.groupId(), Map.of(
                    "callId", call.callId.toString(),
                    "mode", mode.name(),
                    "udpPort", Integer.toString(localPort),
                    "groupName", group.groupName(),
                    "initiatorId", localId.toString(),
                    "initiatorName", call.localDisplayName));
                return snapshot;
            } catch (IOException ex) {
                terminateCall(call.callId, "Không thể liên hệ chủ nhóm: " + ex.getMessage(), false, WireMessage.Type.GROUP_CALL_LEAVE);
                throw ex;
            }
        }
    }

    // ==================== ACCEPT / REJECT / END ====================

    public void acceptCall(UUID callId) throws IOException {
        Objects.requireNonNull(callId, "callId");
        ActiveCall call;
        boolean isGroup;
        boolean isGroupOwner;
        int localPort;
        synchronized (lock) {
            if (closed || active == null || !active.callId.equals(callId)
                    || active.state != State.INCOMING_RINGING || !active.incoming) {
                throw new IOException("Cuộc gọi đến không còn hiệu lực");
            }
            call = active;
            isGroup = call.group;
            isGroupOwner = call.groupOwnerRelay;
            cancelTimeout(call);
            if (isGroup && isGroupOwner) {
                call.ownerJoined = true;
                call.state = State.CONNECTED;
                call.connectedAt = Instant.now();
                call.participants.put(localId, new Participant(localId, call.localDisplayName, call.muted, call.cameraOn, true));
                localPort = call.transport.localPort();
            } else {
                CallMediaTransport transport = new CallMediaTransport(call.callId);
                transport.setRemote(call.remoteAddress, call.remoteUdpPort);
                call.transport = transport;
                localPort = transport.localPort();
                call.state = State.CONNECTED;
                call.connectedAt = Instant.now();
                if (isGroup) {
                    call.participants.put(localId, new Participant(localId, call.localDisplayName, call.muted, call.cameraOn, true));
                }
            }
        }

        if (!isGroup) {
            try {
                sender.send(call.peerId, WireMessage.Type.CALL_ACCEPT, call.conversationId, Map.of(
                    "callId", call.callId.toString(),
                    "udpPort", Integer.toString(localPort)));
            } catch (IOException ex) {
                terminateCall(call.callId, "Không thể kết nối cuộc gọi: " + ex.getMessage(), false, WireMessage.Type.CALL_END);
                throw ex;
            }
            startP2pMedia(call);
            emitState(call.snapshot());
        } else if (isGroupOwner) {
            startGroupMedia(call, true);
            broadcastGroupRoster(call);
            emitState(call.snapshot());
        } else {
            try {
                sender.send(call.peerId, WireMessage.Type.GROUP_CALL_JOIN, call.conversationId, Map.of(
                    "callId", call.callId.toString(),
                    "udpPort", Integer.toString(localPort),
                    "displayName", call.localDisplayName));
            } catch (IOException ex) {
                terminateCall(call.callId, "Không thể tham gia cuộc gọi nhóm: " + ex.getMessage(), false, WireMessage.Type.GROUP_CALL_LEAVE);
                throw ex;
            }
            startGroupMedia(call, false);
            emitState(call.snapshot());
        }
    }

    public void rejectCall(UUID callId, String reason) {
        String text = (reason == null || reason.isBlank()) ? "Đã từ chối cuộc gọi" : reason;
        ActiveCall current;
        synchronized (lock) {
            current = active != null && (callId == null || active.callId.equals(callId)) ? active : null;
        }
        if (current == null) return;
        if (current.group) {
            terminateGroupCallLocal(current.callId, text, true);
        } else {
            terminateCall(callId, text, true, WireMessage.Type.CALL_REJECT);
        }
    }

    public void endCall(UUID callId, String reason) {
        String text = (reason == null || reason.isBlank()) ? "Cuộc gọi đã kết thúc" : reason;
        ActiveCall current;
        synchronized (lock) {
            current = active != null && (callId == null || active.callId.equals(callId)) ? active : null;
        }
        if (current == null) return;
        if (current.group) {
            terminateGroupCallLocal(current.callId, text, true);
        } else {
            terminateCall(callId, text, true, WireMessage.Type.CALL_END);
        }
    }

    // ==================== MEDIA CONTROLS ====================

    public void setMuted(boolean muted) {
        ActiveCall call;
        CallSession snapshot;
        synchronized (lock) {
            if (active == null || active.state == State.ENDED) return;
            call = active;
            call.muted = muted;
            if (call.audio != null) call.audio.setMuted(muted);
            if (call.group && call.participants.containsKey(localId)) {
                Participant self = call.participants.get(localId);
                call.participants.put(localId, new Participant(localId, self.name(), muted, self.cameraOn(), true));
            }
            snapshot = call.snapshot();
        }
        emitState(snapshot);
        if (call.group) {
            if (call.groupOwnerRelay) broadcastGroupRoster(call);
            else sendGroupMediaStateAsync(call);
        } else {
            sendMediaStateAsync(call);
        }
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
            if (call.group && call.participants.containsKey(localId)) {
                Participant self = call.participants.get(localId);
                call.participants.put(localId, new Participant(localId, self.name(), self.muted(), on, true));
            }
            snapshot = call.snapshot();
        }
        if (!on) emitLocalVideo(null);
        emitState(snapshot);
        if (call.group) {
            if (call.groupOwnerRelay) broadcastGroupRoster(call);
            else sendGroupMediaStateAsync(call);
        } else {
            sendMediaStateAsync(call);
        }
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
            if (call.group && call.participants.containsKey(localId)) {
                Participant self = call.participants.get(localId);
                call.participants.put(localId, new Participant(localId, self.name(), self.muted(), call.cameraOn, true));
            }
            snapshot = call.snapshot();
        }
        emitState(snapshot);
        if (call.group) {
            if (call.groupOwnerRelay) broadcastGroupRoster(call);
            else sendGroupMediaStateAsync(call);
        } else {
            sendMediaStateAsync(call);
        }
    }

    public void disconnected(UUID peerId) {
        ActiveCall current;
        boolean removeMember = false;
        boolean endRoom = false;
        synchronized (lock) {
            current = active;
            if (current == null) return;
            if (!current.group) {
                if (!current.peerId.equals(peerId)) return;
            } else if (current.groupOwnerRelay) {
                current.invitedGroupMembers.remove(peerId);
                if (current.transport != null) current.transport.removeRelayPeer(peerId);
                if (current.audio != null) current.audio.removeParticipant(peerId);
                removeMember = current.participants.remove(peerId) != null;
                if (!current.ownerJoined && current.participants.isEmpty()) {
                    endRoom = true;
                }
            } else {
                if (!current.peerId.equals(peerId)) return;
            }
        }
        if (!current.group) {
            terminateCall(current.callId, "Mất kết nối TCP với đối phương", false, WireMessage.Type.CALL_END);
        } else if (current.groupOwnerRelay) {
            if (endRoom) {
                terminateGroupCallLocal(current.callId, "Thành viên khởi tạo đã ngắt kết nối", false);
            } else if (removeMember) {
                emitParticipantVideo(peerId, null);
                broadcastGroupRoster(current);
                emitState(current.snapshot());
            }
        } else {
            terminateGroupCallLocal(current.callId, "Chủ nhóm đã ngắt kết nối", false);
        }
    }

    // ==================== INCOMING P2P SIGNALING ====================

    public void handleIncoming(UUID peerId, String peerName, InetAddress peerAddress, WireMessage frame) {
        if (!frame.type().name().startsWith("CALL_")) {
            throw new IllegalArgumentException("Not a call frame");
        }
        UUID callId = parseCallId(frame);

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

    // ==================== INCOMING GROUP SIGNALING ====================

    public void handleIncomingGroup(UUID peerId, String peerName, InetAddress peerAddress, WireMessage frame,
                                    Function<UUID, ChatGroup> groupLookup,
                                    Supplier<Map<UUID, InetAddress>> peerAddresses,
                                    String localName) {
        if (!frame.type().name().startsWith("GROUP_CALL_")) {
            throw new IllegalArgumentException("Not a group call frame");
        }
        UUID callId = parseCallId(frame);
        UUID groupId = frame.conversationId();

        switch (frame.type()) {
            case GROUP_CALL_START -> handleGroupStart(peerId, peerName, peerAddress, groupId, callId,
                frame.payload(), groupLookup, peerAddresses, localName);
            case GROUP_CALL_JOIN -> handleGroupJoin(peerId, peerName, peerAddress, groupId, callId, frame.payload());
            case GROUP_CALL_ROSTER -> handleGroupRoster(peerId, peerAddress, groupId, callId, frame.payload());
            case GROUP_CALL_MEDIA_STATE -> handleGroupMediaState(peerId, callId, frame.payload());
            case GROUP_CALL_LEAVE -> handleGroupLeave(peerId, callId, frame.payload());
            case GROUP_CALL_END -> {
                String reason = frame.payload().getOrDefault("reason", "Chủ nhóm đã kết thúc cuộc gọi nhóm");
                boolean matches;
                synchronized (lock) {
                    matches = active != null && active.group && !active.groupOwnerRelay
                        && active.callId.equals(callId) && active.peerId.equals(peerId);
                }
                if (matches) terminateGroupCallLocal(callId, reason, false);
            }
            default -> throw new IllegalArgumentException("Unexpected group call frame: " + frame.type());
        }
    }

    private void handleGroupStart(UUID peerId, String peerName, InetAddress peerAddress, UUID groupId, UUID callId,
                                  Map<String, String> payload, Function<UUID, ChatGroup> groupLookup,
                                  Supplier<Map<UUID, InetAddress>> peerAddresses, String localName) {
        Mode mode;
        int remotePort;
        try {
            mode = Mode.valueOf(payload.getOrDefault("mode", "VOICE"));
            remotePort = Integer.parseInt(payload.getOrDefault("udpPort", "-1"));
            if (remotePort <= 0 || remotePort > 65535) throw new IllegalArgumentException("Invalid UDP port");
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid GROUP_CALL_START payload", ex);
        }
        ChatGroup group = groupLookup == null ? null : groupLookup.apply(groupId);
        if (group == null || !group.contains(localId)) return;

        String groupName = payload.getOrDefault("groupName", group.groupName());
        String initiatorName = payload.getOrDefault("initiatorName", peerName == null ? peerId.toString() : peerName);

        if (group.ownerId().equals(localId)) {
            // Received by Group Owner from a non-owner member requesting to start a group call
            if (!group.contains(peerId)) return;
            Map<UUID, InetAddress> online = peerAddresses == null ? Map.of() : peerAddresses.get();
            List<UUID> otherMembers = group.memberIds().stream()
                .filter(id -> !id.equals(localId) && !id.equals(peerId) && online.containsKey(id))
                .toList();

            ActiveCall call;
            int ownerUdpPort;
            synchronized (lock) {
                if (closed || active != null) {
                    workers.submit(() -> {
                        try {
                            sender.send(peerId, WireMessage.Type.GROUP_CALL_LEAVE, groupId, Map.of(
                                "callId", callId.toString(),
                                "reason", "Chủ nhóm đang bận trong cuộc gọi khác"));
                        } catch (IOException ignored) {}
                    });
                    return;
                }
                try {
                    CallMediaTransport transport = new CallMediaTransport(callId);
                    transport.addRelayPeer(peerId, peerAddress, remotePort);
                    call = new ActiveCall(callId, groupId, localId, groupName, mode, true,
                        State.INCOMING_RINGING, true, true, localName);
                    call.ownerJoined = false;
                    call.transport = transport;
                    call.invitedGroupMembers.add(peerId);
                    call.invitedGroupMembers.addAll(otherMembers);
                    call.participants.put(peerId, new Participant(peerId, initiatorName, false, mode == Mode.VIDEO, false));
                    call.ringTimeout = scheduleRingTimeout(callId);
                    ownerUdpPort = transport.localPort();
                    active = call;
                } catch (IOException ex) {
                    return;
                }
            }
            // Start SFU UDP relay immediately so initiator and any other joining members can talk even while owner rings
            startGroupMedia(call, false);
            broadcastGroupRoster(call);
            Map<String, String> invitePayload = Map.of(
                "callId", callId.toString(),
                "mode", mode.name(),
                "udpPort", Integer.toString(ownerUdpPort),
                "groupName", groupName,
                "initiatorId", peerId.toString(),
                "initiatorName", initiatorName);
            for (UUID member : otherMembers) {
                workers.submit(() -> {
                    try { sender.send(member, WireMessage.Type.GROUP_CALL_START, groupId, invitePayload); }
                    catch (IOException ignored) {}
                });
            }
            emitState(call.snapshot());
        } else if (group.ownerId().equals(peerId)) {
            // Received by a Non-Owner Member from the Group Owner
            CallSession incomingSession;
            synchronized (lock) {
                if (closed || active != null) {
                    workers.submit(() -> {
                        try {
                            sender.send(peerId, WireMessage.Type.GROUP_CALL_LEAVE, groupId, Map.of(
                                "callId", callId.toString(),
                                "reason", "Thành viên đang bận trong cuộc gọi khác"));
                        } catch (IOException ignored) {}
                    });
                    return;
                }
                ActiveCall call = new ActiveCall(callId, groupId, peerId, groupName, mode, true,
                    State.INCOMING_RINGING, true, false, localName);
                call.remoteAddress = peerAddress;
                call.remoteUdpPort = remotePort;
                call.ringTimeout = scheduleRingTimeout(callId);
                active = call;
                incomingSession = call.snapshot();
            }
            emitState(incomingSession);
        }
    }

    private void handleGroupJoin(UUID peerId, String peerName, InetAddress peerAddress,
                                 UUID groupId, UUID callId, Map<String, String> payload) {
        int memberUdpPort;
        try {
            memberUdpPort = Integer.parseInt(payload.getOrDefault("udpPort", "-1"));
            if (memberUdpPort <= 0 || memberUdpPort > 65535) throw new IllegalArgumentException("Invalid UDP port");
        } catch (RuntimeException ex) {
            return;
        }
        String displayName = payload.getOrDefault("displayName", peerName == null ? peerId.toString() : peerName);
        ActiveCall call;
        synchronized (lock) {
            if (active == null || !active.group || !active.groupOwnerRelay
                    || !active.callId.equals(callId) || !active.conversationId.equals(groupId)) {
                return;
            }
            call = active;
            call.invitedGroupMembers.add(peerId);
            if (call.transport != null) {
                call.transport.addRelayPeer(peerId, peerAddress, memberUdpPort);
            }
            call.participants.put(peerId, new Participant(peerId, displayName, false, call.mode == Mode.VIDEO, false));
        }
        broadcastGroupRoster(call);
        emitState(call.snapshot());
    }

    private void handleGroupRoster(UUID peerId, InetAddress peerAddress, UUID groupId,
                                   UUID callId, Map<String, String> payload) {
        int ownerUdpPort;
        try {
            ownerUdpPort = Integer.parseInt(payload.getOrDefault("udpPort", "-1"));
            if (ownerUdpPort <= 0 || ownerUdpPort > 65535) throw new IllegalArgumentException("Invalid UDP port");
        } catch (RuntimeException ex) {
            return;
        }
        List<Participant> parsed = decodeParticipants(payload.getOrDefault("participants", ""), localId);
        ActiveCall call;
        boolean becameConnected = false;
        List<UUID> camOffIds = new ArrayList<>();
        synchronized (lock) {
            if (active == null || !active.group || active.groupOwnerRelay
                    || !active.callId.equals(callId) || !active.conversationId.equals(groupId)
                    || !active.peerId.equals(peerId)) {
                return;
            }
            call = active;
            if (call.state == State.OUTGOING_RINGING) {
                cancelTimeout(call);
                call.remoteAddress = peerAddress;
                call.remoteUdpPort = ownerUdpPort;
                if (call.transport != null) {
                    call.transport.setRemote(peerAddress, ownerUdpPort);
                }
                call.state = State.CONNECTED;
                call.connectedAt = Instant.now();
                becameConnected = true;
            }
            Set<UUID> previousIds = new HashSet<>(call.participants.keySet());
            call.participants.clear();
            for (Participant p : parsed) {
                call.participants.put(p.id(), p);
                if (!p.self() && !p.cameraOn()) camOffIds.add(p.id());
                previousIds.remove(p.id());
            }
            for (UUID departed : previousIds) {
                if (call.audio != null) call.audio.removeParticipant(departed);
                camOffIds.add(departed);
            }
        }
        if (becameConnected) {
            startGroupMedia(call, false);
        }
        for (UUID id : camOffIds) emitParticipantVideo(id, null);
        emitState(call.snapshot());
    }

    private void handleGroupMediaState(UUID peerId, UUID callId, Map<String, String> payload) {
        boolean muted = Boolean.parseBoolean(payload.getOrDefault("muted", "false"));
        boolean cameraOn = Boolean.parseBoolean(payload.getOrDefault("cameraOn", "false"));
        ActiveCall call;
        synchronized (lock) {
            if (active == null || !active.group || !active.groupOwnerRelay || !active.callId.equals(callId)) return;
            Participant existing = active.participants.get(peerId);
            if (existing == null) return;
            active.participants.put(peerId, new Participant(peerId, existing.name(), muted, cameraOn, false));
            call = active;
        }
        if (!cameraOn) emitParticipantVideo(peerId, null);
        broadcastGroupRoster(call);
        emitState(call.snapshot());
    }

    private void handleGroupLeave(UUID peerId, UUID callId, Map<String, String> payload) {
        String reason = payload.getOrDefault("reason", "Đã rời cuộc gọi nhóm");
        ActiveCall call;
        boolean ownerEndRoom = false;
        boolean memberRejected = false;
        synchronized (lock) {
            if (active == null || !active.group || !active.callId.equals(callId)) return;
            call = active;
            if (call.groupOwnerRelay) {
                if (call.transport != null) call.transport.removeRelayPeer(peerId);
                if (call.audio != null) call.audio.removeParticipant(peerId);
                call.participants.remove(peerId);
                if (!call.ownerJoined && call.participants.isEmpty()) {
                    ownerEndRoom = true;
                }
            } else if (call.peerId.equals(peerId) && call.state == State.OUTGOING_RINGING) {
                memberRejected = true;
            } else {
                return;
            }
        }
        if (ownerEndRoom) {
            terminateGroupCallLocal(callId, reason, false);
        } else if (memberRejected) {
            terminateGroupCallLocal(callId, reason, false);
        } else {
            emitParticipantVideo(peerId, null);
            broadcastGroupRoster(call);
            emitState(call.snapshot());
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
            ActiveCall call = new ActiveCall(callId, conversationId, peerId, peerName, mode, true,
                State.INCOMING_RINGING, false, false, null);
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
            if (active == null || active.group || !active.callId.equals(callId)
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
        startP2pMedia(call);
        emitState(call.snapshot());
    }

    private void handleMediaState(UUID peerId, UUID callId, Map<String, String> payload) {
        CallSession snapshot;
        boolean remoteCamOff;
        synchronized (lock) {
            if (active == null || active.group || !active.callId.equals(callId) || !active.peerId.equals(peerId)) return;
            active.remoteMuted = Boolean.parseBoolean(payload.getOrDefault("muted", "false"));
            active.remoteCameraOn = Boolean.parseBoolean(payload.getOrDefault("cameraOn", "false"));
            remoteCamOff = !active.remoteCameraOn;
            snapshot = active.snapshot();
        }
        if (remoteCamOff) {
            emitRemoteVideo(null);
            emitParticipantVideo(peerId, null);
        }
        emitState(snapshot);
    }

    // ==================== MEDIA STARTUP ====================

    private void startP2pMedia(ActiveCall call) {
        synchronized (lock) {
            if (active != call || call.state != State.CONNECTED || call.transport == null) return;
            AudioCallEngine audio = null;
            VideoCallEngine video = null;
            if (hardwareMedia) {
                audio = new AudioCallEngine(call.transport, this::emitNotice);
                audio.setMuted(call.muted);
                call.audio = audio;
                if (call.mode == Mode.VIDEO) {
                    video = new VideoCallEngine(call.transport, this::emitLocalVideo,
                        img -> { emitRemoteVideo(img); emitParticipantVideo(call.peerId, img); },
                        this::emitNotice);
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
                        decodeAndEmitVideo(call.peerId, jpeg, true);
                    }
                });
            if (audio != null) audio.start();
            if (video != null) video.start(call.cameraOn);
        }
    }

    private void startGroupMedia(ActiveCall call, boolean startLocalCapture) {
        synchronized (lock) {
            if (active != call || call.transport == null) return;
            if (startLocalCapture && hardwareMedia) {
                if (call.audio == null) {
                    call.audio = new AudioCallEngine(call.transport, localId, this::emitNotice);
                    call.audio.setMuted(call.muted);
                    call.audio.start();
                }
                if (call.mode == Mode.VIDEO && call.video == null) {
                    call.video = new VideoCallEngine(call.transport, localId,
                        this::emitLocalVideo, this::emitParticipantVideo, this::emitNotice);
                    call.video.start(call.cameraOn);
                }
            }
            // Start UDP receiver once (safe no-op if already started during SFU ringing)
            call.transport.startReceivingGroup(
                (senderId, pcm) -> {
                    if (localId.equals(senderId)) return;
                    AudioCallEngine engine;
                    boolean joined;
                    synchronized (lock) {
                        engine = call.audio;
                        joined = !call.groupOwnerRelay || call.ownerJoined;
                    }
                    if (joined && engine != null) engine.enqueueIncomingAudio(senderId, pcm);
                },
                (senderId, jpeg) -> {
                    if (localId.equals(senderId)) return;
                    VideoCallEngine engine;
                    boolean joined;
                    synchronized (lock) {
                        engine = call.video;
                        joined = !call.groupOwnerRelay || call.ownerJoined;
                    }
                    if (!joined) return;
                    if (engine != null) {
                        engine.enqueueRemoteJpeg(senderId, jpeg);
                    } else if (call.mode == Mode.VIDEO) {
                        decodeAndEmitVideo(senderId, jpeg, false);
                    }
                },
                call.groupOwnerRelay);
        }
    }

    private void decodeAndEmitVideo(UUID senderId, byte[] jpeg, boolean alsoP2pRemote) {
        try {
            BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(jpeg));
            if (img != null) {
                if (alsoP2pRemote) emitRemoteVideo(img);
                emitParticipantVideo(senderId, img);
            }
        } catch (Exception ignored) {}
    }

    // ==================== HELPERS ====================

    private void broadcastGroupRoster(ActiveCall call) {
        UUID callId;
        UUID groupId;
        int ownerUdpPort;
        String encoded;
        List<UUID> targets;
        synchronized (lock) {
            if (active != call || !call.groupOwnerRelay || call.transport == null) return;
            callId = call.callId;
            groupId = call.conversationId;
            ownerUdpPort = call.transport.localPort();
            encoded = encodeParticipants(call.participants.values());
            targets = call.participants.keySet().stream().filter(id -> !id.equals(localId)).toList();
        }
        Map<String, String> payload = Map.of(
            "callId", callId.toString(),
            "udpPort", Integer.toString(ownerUdpPort),
            "participants", encoded);
        for (UUID member : targets) {
            mediaStates.submit(() -> {
                try { sender.send(member, WireMessage.Type.GROUP_CALL_ROSTER, groupId, payload); }
                catch (IOException ignored) {}
            });
        }
    }

    private void sendGroupMediaStateAsync(ActiveCall call) {
        UUID callId = call.callId;
        UUID ownerId = call.peerId;
        UUID groupId = call.conversationId;
        boolean muted = call.muted;
        boolean cameraOn = call.cameraOn;
        mediaStates.submit(() -> {
            try {
                sender.send(ownerId, WireMessage.Type.GROUP_CALL_MEDIA_STATE, groupId, Map.of(
                    "callId", callId.toString(),
                    "muted", Boolean.toString(muted),
                    "cameraOn", Boolean.toString(cameraOn)));
            } catch (IOException ignored) {}
        });
    }

    private void terminateGroupCallLocal(UUID callId, String reason, boolean informPeers) {
        ActiveCall ended;
        List<UUID> notifyTargets = List.of();
        synchronized (lock) {
            if (active == null || !active.group || (callId != null && !active.callId.equals(callId))) return;
            ended = active;
            active = null;
            ended.state = State.ENDED;
            ended.reason = reason;
            cancelTimeout(ended);
            if (informPeers) {
                if (ended.groupOwnerRelay) {
                    Set<UUID> all = new LinkedHashSet<>(ended.invitedGroupMembers);
                    all.addAll(ended.participants.keySet());
                    all.remove(localId);
                    notifyTargets = List.copyOf(all);
                } else {
                    notifyTargets = List.of(ended.peerId);
                }
            }
        }
        stopMedia(ended);
        emitState(ended.snapshot());
        if (informPeers && !notifyTargets.isEmpty()) {
            WireMessage.Type signal = ended.groupOwnerRelay ? WireMessage.Type.GROUP_CALL_END : WireMessage.Type.GROUP_CALL_LEAVE;
            Map<String, String> payload = Map.of(
                "callId", ended.callId.toString(),
                "reason", reason == null ? "" : reason);
            for (UUID target : notifyTargets) {
                workers.submit(() -> {
                    try { sender.send(target, signal, ended.conversationId, payload); }
                    catch (IOException ignored) {}
                });
            }
        }
    }

    private static String encodeParticipants(Collection<Participant> list) {
        StringBuilder sb = new StringBuilder();
        for (Participant p : list) {
            if (!sb.isEmpty()) sb.append(';');
            sb.append(p.id()).append('|')
              .append(URLEncoder.encode(p.name(), StandardCharsets.UTF_8)).append('|')
              .append(p.muted()).append('|')
              .append(p.cameraOn());
        }
        return sb.toString();
    }

    private static List<Participant> decodeParticipants(String encoded, UUID selfId) {
        if (encoded == null || encoded.isBlank()) return List.of();
        List<Participant> result = new ArrayList<>();
        for (String token : encoded.split(";")) {
            if (token.isBlank()) continue;
            String[] parts = token.split("\\|", -1);
            if (parts.length < 4) continue;
            try {
                UUID id = UUID.fromString(parts[0]);
                String name = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                boolean muted = Boolean.parseBoolean(parts[2]);
                boolean camOn = Boolean.parseBoolean(parts[3]);
                result.add(new Participant(id, name, muted, camOn, id.equals(selfId)));
            } catch (RuntimeException ignored) {}
        }
        return result;
    }

    private static UUID parseCallId(WireMessage frame) {
        try {
            return UUID.fromString(frame.payload().get("callId"));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid callId", ex);
        }
    }

    private ScheduledFuture<?> scheduleRingTimeout(UUID callId) {
        return timer.schedule(() -> {
            ActiveCall current;
            synchronized (lock) {
                current = active != null && active.callId.equals(callId)
                    && (active.state == State.OUTGOING_RINGING || active.state == State.INCOMING_RINGING)
                    ? active : null;
            }
            if (current != null) {
                if (current.group) {
                    terminateGroupCallLocal(callId, "Không có phản hồi (hết thời gian đổ chuông)", true);
                } else {
                    terminateCall(callId, "Không có phản hồi (hết thời gian đổ chuông)", true, WireMessage.Type.CALL_END);
                }
            }
        }, RING_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void terminateCallIfPeer(UUID callId, UUID peerId, String reason) {
        boolean matches;
        synchronized (lock) {
            matches = active != null && !active.group && active.callId.equals(callId) && active.peerId.equals(peerId);
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
        mediaStates.submit(() -> {
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

    private void emitParticipantVideo(UUID participantId, BufferedImage frame) {
        for (var listener : participantVideoListeners) {
            try { listener.accept(participantId, frame); } catch (RuntimeException ignored) {}
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
        mediaStates.shutdownNow();
        workers.shutdownNow();
    }
}
