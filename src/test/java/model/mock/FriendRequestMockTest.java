package model.mock;

import listener.ChatClientListener;
import model.ChatMessage;
import model.ChatSnapshot;
import model.DeliveryState;
import model.FriendRequest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class FriendRequestMockTest {
    @Test
    void acceptsIncomingOnceAndCreatesTheRightDirectChat() throws Exception {
        try (var mock = new MockChatService()) {
            Directory listener = start(mock);
            FriendRequest request = listener.snapshot.friendRequests().get(0);
            assertEquals(FriendRequest.Direction.INCOMING, request.direction());
            assertEquals(FriendRequest.Status.PENDING, request.status());
            assertFalse(hasDirectChat(listener.snapshot, request));

            result(mock.respondToFriendRequest(request.id(), true));

            assertEquals(FriendRequest.Status.ACCEPTED, listener.snapshot.friendRequests().get(0).status());
            assertTrue(hasDirectChat(listener.snapshot, request));
            assertRejected(mock.respondToFriendRequest(request.id(), true));
            assertRejected(mock.sendFriendRequest(request.ipAddress()));
            assertEquals(4, listener.snapshot.conversations().size());
        }
    }

    @Test
    void decliningDoesNotCreateAChat() throws Exception {
        try (var mock = new MockChatService()) {
            Directory listener = start(mock);
            FriendRequest request = listener.snapshot.friendRequests().get(0);
            result(mock.respondToFriendRequest(request.id(), false));
            assertEquals(FriendRequest.Status.DECLINED, listener.snapshot.friendRequests().get(0).status());
            assertFalse(hasDirectChat(listener.snapshot, request));
        }
    }

    @Test
    void avoidsSelfUnknownAndDuplicateRequestsAndOnlyLetsTheReceiverRespond() throws Exception {
        try (var mock = new MockChatService()) {
            Directory listener = start(mock);
            assertRejected(mock.sendFriendRequest("192.168.1.10"));
            assertRejected(mock.sendFriendRequest("192.168.1.99"));
            assertRejected(mock.sendFriendRequest("192.168.1.11"));
            assertRejected(mock.sendFriendRequest("192.168.1.24"));
            assertEquals(1, listener.snapshot.friendRequests().size());

            result(mock.sendFriendRequest("fd00::23"));

            FriendRequest request = listener.snapshot.friendRequests().get(1);
            assertEquals(FriendRequest.Direction.OUTGOING, request.direction());
            assertEquals(FriendRequest.Status.PENDING, request.status());
            assertFalse(hasDirectChat(listener.snapshot, request));
            assertRejected(mock.sendFriendRequest("192.168.1.23"));
            assertRejected(mock.respondToFriendRequest(request.id(), true));
            assertEquals(2, listener.snapshot.friendRequests().size());
        }
    }

    private static boolean hasDirectChat(ChatSnapshot snapshot, FriendRequest request) {
        return snapshot.conversations().stream().anyMatch(chat -> !chat.group()
                && chat.memberIds().containsAll(java.util.List.of("demo-self", request.peer().id())));
    }

    private static Directory start(MockChatService mock) throws Exception {
        result(mock.signIn("Người dùng demo", "demo".toCharArray()));
        Directory listener = new Directory();
        mock.connect(listener);
        assertTrue(listener.ready.await(5, TimeUnit.SECONDS));
        return listener;
    }

    private static void assertRejected(CompletionStage<Void> stage) {
        ExecutionException failure = assertThrows(ExecutionException.class, () -> result(stage));
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
    }

    private static <Value> Value result(CompletionStage<Value> stage) throws Exception {
        return stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    private static final class Directory implements ChatClientListener {
        private final CountDownLatch ready = new CountDownLatch(1);
        private volatile ChatSnapshot snapshot;

        @Override
        public void onDirectoryChanged(ChatSnapshot updated) {
            snapshot = updated;
            ready.countDown();
        }

        @Override public void onMessageReceived(ChatMessage message) {}
        @Override public void onDeliveryStateChanged(String conversationId, String messageId, DeliveryState state) {}
        @Override public void onConnectionError(String message) {}
    }
}
