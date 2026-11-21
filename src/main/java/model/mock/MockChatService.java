package model.mock;

import listener.ChatActionHandler;
import listener.ChatClientListener;
import model.*;

import java.io.UncheckedIOException;
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

    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "intrachat-demo");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, ChatUser> users = new LinkedHashMap<>();
    private final Map<String, Conversation> conversations = new LinkedHashMap<>();
    private final Map<String, List<ChatMessage>> histories = new HashMap<>();
    private ChatClientListener listener;

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
        listener.onDirectoryChanged(new ChatSnapshot(List.copyOf(users.values()), List.copyOf(conversations.values()), previews));
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
