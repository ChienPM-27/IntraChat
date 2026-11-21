package model.mock;

import listener.ChatActionHandler;
import listener.ChatClientListener;
import model.*;

import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

public final class MockChatService implements ChatActionHandler {
    private static final String SELF = "demo-self";
    private static final String MAI = "demo-mai";
    private static final String KHANG = "demo-khang";
    private static final String DIRECT_MAI = "direct-mai";
    private static final String DIRECT_KHANG = "direct-khang";
    private static final String TEAM = "group-project";
    private static final String SELF_IP = "192.168.1.10";
    private static final String LINH_IP = "192.168.1.23";
    private static final String NAM_IP = "192.168.1.24";
    private static final int FRIEND_REPLY_SECONDS = 6;

    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "intrachat-demo");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, ChatUser> users = new LinkedHashMap<>();
    private final Map<String, Conversation> conversations = new LinkedHashMap<>();
    private final Map<String, List<ChatMessage>> histories = new HashMap<>();
    private final Map<String, ChatUser> peersByIp = new HashMap<>();
    private final Map<String, FriendRequest> friendRequests = new LinkedHashMap<>();
    private ChatClientListener listener;
    private final MockCalls calls = new MockCalls(worker, this::publishCall, Duration.ofSeconds(20));

    @Override
    public CompletionStage<ChatUser> signIn(String username, char[] password) {
        return startSession(username, password, false);
    }

    @Override
    public CompletionStage<ChatUser> register(String username, char[] password) {
        return startSession(username, password, true);
    }

    private CompletionStage<ChatUser> startSession(String username, char[] password, boolean registration) {
        String name = username.strip();
        boolean hasPassword = false;
        for (char letter : password) {
            hasPassword |= !Character.isWhitespace(letter);
        }
        if (name.isBlank() || name.length() > 32 || !hasPassword) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Nhập tên (tối đa 32 ký tự) và mật khẩu."));
        }
        if (registration && password.length < 4) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Mật khẩu demo cần ít nhất 4 ký tự."));
        }
        return submit(() -> {
            ChatUser self = new ChatUser(SELF, name, true);
            if (conversations.isEmpty()) {
                seed();
            }
            users.put(self.id(), self);
            return self;
        });
    }

    @Override
    public void connect(ChatClientListener subscriber) {
        worker.execute(() -> {
            listener = Objects.requireNonNull(subscriber);
            publishDirectory();
        });
    }

    @Override
    public CompletionStage<List<ChatMessage>> loadHistory(String conversationId) {
        return submit(() -> {
            requireConversation(conversationId);
            return histories.get(conversationId).stream().sorted(ChatMessage.ORDER).toList();
        });
    }

    @Override
    public CompletionStage<ChatMessage> sendMessage(String conversationId, MessageContent content) {
        return submit(() -> {
            Conversation conversation = requireConversation(conversationId);
            ChatMessage message = new ChatMessage(UUID.randomUUID().toString(), conversationId, SELF,
                    Instant.now(), content, DeliveryState.SENT);
            histories.get(conversationId).add(message);
            if (listener != null) {
                listener.onMessageReceived(message);
            }
            boolean allRecipientsOnline = conversation.memberIds().stream().filter(id -> !SELF.equals(id))
                    .map(users::get).allMatch(ChatUser::online);
            if (allRecipientsOnline) {
                worker.schedule(() -> updateState(message, DeliveryState.DELIVERED), 700, TimeUnit.MILLISECONDS);
                worker.schedule(() -> updateState(message, DeliveryState.SEEN), 2_100, TimeUnit.MILLISECONDS);
            }
            return message;
        });
    }

    @Override
    public CompletionStage<Conversation> createGroup(String name, List<String> memberIds) {
        String title = name.strip();
        List<String> recipients = List.copyOf(memberIds);
        return submit(() -> {
            if (title.isBlank() || title.length() > 60 || recipients.isEmpty() || recipients.contains(SELF)
                    || recipients.stream().anyMatch(id -> !users.containsKey(id))) {
                throw new IllegalArgumentException("Nhập tên nhóm và chọn thành viên hợp lệ.");
            }
            Set<String> members = new LinkedHashSet<>();
            members.add(SELF);
            members.addAll(recipients);
            Conversation conversation = new Conversation(UUID.randomUUID().toString(), title, true, List.copyOf(members));
            addConversation(conversation);
            publishDirectory();
            return conversation;
        });
    }

    @Override
    public CompletionStage<Void> sendFriendRequest(String ipAddress) {
        return submit(() -> {
            String address = LanAddress.normalize(ipAddress);
            if (SELF_IP.equals(address)) {
                throw new IllegalArgumentException("Đây là IP của bạn trong phiên demo.");
            }
            ChatUser peer = peersByIp.get(address);
            if (peer == null) {
                throw new IllegalArgumentException("IP này không có trong dữ liệu demo.");
            }
            if (users.containsKey(peer.id())) {
                throw new IllegalArgumentException("Người này đã là bạn.");
            }
            for (FriendRequest pending : friendRequests.values()) {
                if (pending.peer().id().equals(peer.id()) && pending.status() == FriendRequest.Status.PENDING) {
                    String message = pending.direction() == FriendRequest.Direction.INCOMING
                            ? "Người này đã gửi lời mời. Mở tab Đã nhận để phản hồi."
                            : "Lời mời đã gửi và đang chờ chấp nhận.";
                    throw new IllegalArgumentException(message);
                }
            }
            FriendRequest request = new FriendRequest(UUID.randomUUID().toString(), peer, ipAddress.strip(),
                    FriendRequest.Direction.OUTGOING, FriendRequest.Status.PENDING, Instant.now());
            friendRequests.put(request.id(), request);
            publishDirectory();
            worker.schedule(() -> finishFriendRequest(request, true), FRIEND_REPLY_SECONDS, TimeUnit.SECONDS);
            return null;
        });
    }

    @Override
    public CompletionStage<Void> respondToFriendRequest(String requestId, boolean accept) {
        return submit(() -> {
            FriendRequest request = friendRequests.get(requestId);
            if (request == null || request.direction() != FriendRequest.Direction.INCOMING
                    || request.status() != FriendRequest.Status.PENDING) {
                throw new IllegalArgumentException("Lời mời này không còn chờ bạn phản hồi.");
            }
            finishFriendRequest(request, accept);
            return null;
        });
    }

    private void finishFriendRequest(FriendRequest request, boolean accept) {
        if (accept) {
            ChatUser peer = request.peer();
            users.put(peer.id(), peer);
            addConversation(new Conversation("direct-" + peer.id(), peer.displayName(), false, List.of(SELF, peer.id())));
        }
        friendRequests.put(request.id(), request.withStatus(accept ? FriendRequest.Status.ACCEPTED
                : FriendRequest.Status.DECLINED));
        publishDirectory();
    }

    @Override
    public CompletionStage<Void> startCall(String conversationId, CallSession.Mode mode) {
        return ring(conversationId, mode, CallSession.Direction.OUTGOING);
    }

    public CompletionStage<Void> simulateIncomingCall(String conversationId, CallSession.Mode mode) {
        return ring(conversationId, mode, CallSession.Direction.INCOMING);
    }

    private CompletionStage<Void> ring(String conversationId, CallSession.Mode mode, CallSession.Direction direction) {
        return submit(() -> {
            Conversation conversation = requireConversation(conversationId);
            if (conversation.group() || !conversation.memberIds().contains(SELF)) {
                throw new IllegalArgumentException("Chọn một đoạn chat riêng để gọi.");
            }
            ChatUser peer = conversation.memberIds().stream().filter(id -> !SELF.equals(id))
                    .map(users::get).findFirst().orElseThrow();
            if (!peer.online()) {
                throw new IllegalArgumentException(peer.displayName() + " đang ngoại tuyến.");
            }
            calls.ring(conversationId, peer, mode, direction);
            return null;
        });
    }

    @Override
    public CompletionStage<Void> respondToCall(String callId, boolean accept) {
        return submit(() -> {
            calls.respond(callId, accept);
            return null;
        });
    }

    @Override
    public CompletionStage<Void> endCall(String callId) {
        return submit(() -> {
            calls.end(callId);
            return null;
        });
    }

    private void publishCall(CallSession session) {
        if (listener != null) {
            listener.onCallChanged(session);
        }
    }

    private void updateState(ChatMessage message, DeliveryState state) {
        histories.get(message.conversationId()).replaceAll(existing ->
                existing.id().equals(message.id()) ? existing.withState(state) : existing);
        if (listener != null) {
            listener.onDeliveryStateChanged(message.conversationId(), message.id(), state);
        }
    }

    private void publishDirectory() {
        if (listener == null) {
            return;
        }
        Map<String, ChatMessage> previews = new HashMap<>();
        histories.forEach((id, messages) -> messages.stream().max(ChatMessage.ORDER)
                .ifPresent(message -> previews.put(id, message)));
        listener.onDirectoryChanged(new ChatSnapshot(List.copyOf(users.values()), List.copyOf(conversations.values()),
                previews, List.copyOf(friendRequests.values())));
    }

    private Conversation requireConversation(String id) {
        Conversation conversation = conversations.get(id);
        if (conversation == null) {
            throw new IllegalArgumentException("Không tìm thấy đoạn chat.");
        }
        return conversation;
    }

    private void seed() {
        DemoMedia.DemoMediaFiles media;
        try {
            media = DemoMedia.create();
        } catch (java.io.IOException error) {
            throw new UncheckedIOException("Không thể chuẩn bị tệp mẫu.", error);
        }
        users.put(MAI, new ChatUser(MAI, "Mai Anh", true));
        users.put(KHANG, new ChatUser(KHANG, "Minh Khang", false));
        peersByIp.put("192.168.1.11", users.get(MAI));
        peersByIp.put("192.168.1.12", users.get(KHANG));
        ChatUser linh = new ChatUser("demo-linh", "Khánh Linh", true);
        ChatUser nam = new ChatUser("demo-nam", "Hải Nam", true);
        peersByIp.put(LINH_IP, linh);
        peersByIp.put(LanAddress.normalize("fd00::23"), linh);
        peersByIp.put(NAM_IP, nam);
        FriendRequest incoming = new FriendRequest("demo-incoming", nam, NAM_IP, FriendRequest.Direction.INCOMING,
                FriendRequest.Status.PENDING, Instant.now().minus(2, ChronoUnit.MINUTES));
        friendRequests.put(incoming.id(), incoming);
        addConversation(new Conversation(DIRECT_MAI, "Mai Anh", false, List.of(SELF, MAI)));
        addConversation(new Conversation(TEAM, "Nhóm dự án", true, List.of(SELF, MAI, KHANG)));
        addConversation(new Conversation(DIRECT_KHANG, "Minh Khang", false, List.of(SELF, KHANG)));

        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        add(DIRECT_MAI, MAI, now, 22, MessageContent.text("Mình vừa xong bố cục hội thoại. Bạn xem bản phác thảo này giúp mình nhé!"), DeliveryState.SEEN);
        add(DIRECT_MAI, MAI, now, 21, MessageContent.media(MessageType.IMAGE, media.image()), DeliveryState.SEEN);
        add(DIRECT_MAI, SELF, now, 17, MessageContent.text("Ổn đó! Mình thích cách phần nội dung có nhiều khoảng thở. ✨"), DeliveryState.SEEN);
        add(DIRECT_MAI, MAI, now, 12, MessageContent.text("Vậy mình dùng bản này cho buổi chạy thử chiều nay nhé."), DeliveryState.SEEN);
        add(DIRECT_MAI, SELF, now, 3, MessageContent.text("Nhất trí. Mình sẽ thử luồng gửi tệp trước. 😊"), DeliveryState.SEEN);

        add(TEAM, KHANG, now, 36, MessageContent.text("Mình để checklist và clip ngắn ở đây để cả nhóm tiện xem."), DeliveryState.SEEN);
        add(TEAM, KHANG, now, 35, MessageContent.media(MessageType.FILE, media.file()), DeliveryState.SEEN);
        add(TEAM, KHANG, now, 34, MessageContent.media(MessageType.VIDEO, media.video()), DeliveryState.SEEN);
        add(TEAM, SELF, now, 30, MessageContent.text("Đã nhận. Mình sẽ kiểm tra lại sau giờ nghỉ. 👍"), DeliveryState.DELIVERED);
        add(TEAM, MAI, now, 14, MessageContent.text("Mình để lại một đoạn ghi chú thoại mẫu nha."), DeliveryState.SEEN);
        add(TEAM, MAI, now, 13, MessageContent.media(MessageType.SOUND, media.voice()), DeliveryState.SEEN);

        add(DIRECT_KHANG, KHANG, now, 60, MessageContent.text("Khi nào có bản chạy thử thì gửi mình nhé."), DeliveryState.SEEN);
        add(DIRECT_KHANG, SELF, now, 28, MessageContent.text("Có bản mới mình báo bạn ngay."), DeliveryState.SENT);
    }

    private void addConversation(Conversation conversation) {
        conversations.put(conversation.id(), conversation);
        histories.put(conversation.id(), new ArrayList<>());
    }

    private void add(String conversationId, String senderId, Instant now, long minutesAgo,
                     MessageContent content, DeliveryState state) {
        List<ChatMessage> messages = histories.get(conversationId);
        messages.add(new ChatMessage(conversationId + "-" + messages.size(), conversationId, senderId,
                now.minus(minutesAgo, ChronoUnit.MINUTES), content, state));
    }

    private <Value> CompletionStage<Value> submit(Supplier<Value> task) {
        return CompletableFuture.supplyAsync(task, worker);
    }

    @Override
    public void close() {
        worker.shutdownNow();
    }
}
