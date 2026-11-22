package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import vn.intrachat.domain.PeerIdentity;
import vn.intrachat.transport.NetworkConfig;

import javax.swing.SwingUtilities;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M1 diagnostic-only lifecycle: a TCP candidate is not an approved session,
 * cancellation never auto-retries and pending socket work must not block EDT.
 */
class ConnectionLifecycleTest {
    private static int unusedLoopbackPort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }

    @Test void pendingApprovalCancelClosesAttemptAndCannotClaimConnected() throws Exception {
        int port = unusedLoopbackPort();
        CountDownLatch prompted = new CountDownLatch(1);
        CompletableFuture<InboundApprovalPort.Decision> unanswered = new CompletableFuture<>();
        AtomicInteger invitationCount = new AtomicInteger();
        List<NetworkEvents.Kind> events = new CopyOnWriteArrayList<>();
        try (NetworkCoordinator receiver = new NetworkCoordinator(
                    new PeerIdentity(UUID.randomUUID(), "Receiver"),
                    request -> {
                        invitationCount.incrementAndGet();
                        prompted.countDown();
                        return unanswered;
                    }, event -> {});
             NetworkCoordinator sender = new NetworkCoordinator(
                    new PeerIdentity(UUID.randomUUID(), "Sender"),
                    request -> CompletableFuture.completedFuture(InboundApprovalPort.Decision.REJECT),
                    event -> events.add(event.kind()))) {
            receiver.startListening(new NetworkConfig("127.0.0.1", port));
            CompletableFuture<PeerIdentity> connecting = sender.connect(new NetworkConfig("127.0.0.1", port));
            assertTrue(prompted.await(4, TimeUnit.SECONDS));
            assertFalse(connecting.isDone());
            assertEquals(0, sender.connectedCount());
            SwingUtilities.invokeAndWait(() -> assertTrue(SwingUtilities.isEventDispatchThread()));
            sender.cancelPendingConnect();
            assertThrows(ExecutionException.class, () -> connecting.get(5, TimeUnit.SECONDS));
            assertFalse(events.contains(NetworkEvents.Kind.CONNECTED));
            assertEquals(0, sender.connectedCount());
            Thread.sleep(100);
            assertEquals(1, invitationCount.get(), "Cancelling must not trigger automatic reconnect");
        } finally {
            unanswered.complete(InboundApprovalPort.Decision.REJECT);
        }
    }

    @Test void waitingApprovalIsExplicitAndSecondConnectClickIsRejected() throws Exception {
        int port = unusedLoopbackPort();
        CountDownLatch incomingPrompt = new CountDownLatch(1);
        CompletableFuture<InboundApprovalPort.Decision> pending = new CompletableFuture<>();
        List<NetworkEvents.Kind> events = new CopyOnWriteArrayList<>();
        try(NetworkCoordinator receiver=new NetworkCoordinator(new PeerIdentity(UUID.randomUUID(),"B"),
                req->{incomingPrompt.countDown();return pending;},e->{});
            NetworkCoordinator sender=new NetworkCoordinator(new PeerIdentity(UUID.randomUUID(),"A"),
                req->CompletableFuture.completedFuture(InboundApprovalPort.Decision.REJECT),
                e->events.add(e.kind()))){
            receiver.startListening(new NetworkConfig("127.0.0.1",port));
            var first=sender.connect(new NetworkConfig("127.0.0.1",port));
            assertTrue(incomingPrompt.await(4,TimeUnit.SECONDS));
            for(int i=0;i<40&&!events.contains(NetworkEvents.Kind.WAITING_REMOTE_APPROVAL);i++)Thread.sleep(10);
            assertTrue(events.contains(NetworkEvents.Kind.WAITING_REMOTE_APPROVAL));
            var duplicate=sender.connect(new NetworkConfig("127.0.0.1",port));
            assertThrows(ExecutionException.class,()->duplicate.get(3,TimeUnit.SECONDS));
            assertFalse(events.contains(NetworkEvents.Kind.CONNECTED));
            sender.cancelPendingConnect();
            assertThrows(ExecutionException.class,()->first.get(5,TimeUnit.SECONDS));
            assertEquals(0,sender.connectedCount());
            assertFalse(events.contains(NetworkEvents.Kind.CONNECTED));
        }finally{
            pending.complete(InboundApprovalPort.Decision.REJECT);
        }
    }

    @Test void cancelBeforeWorkerObtainsSocketDoesNotPublishConnected()throws Exception {
        int port=unusedLoopbackPort();
        List<NetworkEvents.Kind> events=new CopyOnWriteArrayList<>();
        // Keep the receiver in an unanswered consent state; then cancellation
        // must work even if the connector has not yet installed its socket.
        CompletableFuture<InboundApprovalPort.Decision> unanswered=new CompletableFuture<>();
        try(NetworkCoordinator receiver=new NetworkCoordinator(new PeerIdentity(UUID.randomUUID(),"B"),
                 req->unanswered,e->{});
            NetworkCoordinator sender=new NetworkCoordinator(new PeerIdentity(UUID.randomUUID(),"A"),
                 req->CompletableFuture.completedFuture(InboundApprovalPort.Decision.REJECT),
                 e->events.add(e.kind()))){
            receiver.startListening(new NetworkConfig("127.0.0.1",port));
            var pending=sender.connect(new NetworkConfig("127.0.0.1",port));
            sender.cancelPendingConnect();
            assertThrows(ExecutionException.class,()->pending.get(5,TimeUnit.SECONDS));
            assertEquals(0,sender.connectedCount());
            assertFalse(events.contains(NetworkEvents.Kind.CONNECTED));
        } finally {unanswered.complete(InboundApprovalPort.Decision.REJECT);}
    }

    @Test void rejectionKeepsConnectedCountZeroAndNeverRetriesAutomatically() throws Exception {
        int port = unusedLoopbackPort();
        AtomicInteger invitations = new AtomicInteger();
        List<NetworkEvents.Kind> events = new CopyOnWriteArrayList<>();
        try (NetworkCoordinator receiver = new NetworkCoordinator(
                    new PeerIdentity(UUID.randomUUID(), "Receiver"),
                    request -> {
                        invitations.incrementAndGet();
                        return CompletableFuture.completedFuture(InboundApprovalPort.Decision.REJECT);
                    }, event -> {});
             NetworkCoordinator sender = new NetworkCoordinator(
                    new PeerIdentity(UUID.randomUUID(), "Sender"),
                    request -> CompletableFuture.completedFuture(InboundApprovalPort.Decision.REJECT),
                    event -> events.add(event.kind()))) {
            receiver.startListening(new NetworkConfig("127.0.0.1", port));
            assertThrows(ExecutionException.class, () ->
                    sender.connect(new NetworkConfig("127.0.0.1", port)).get(5, TimeUnit.SECONDS));
            assertTrue(events.contains(NetworkEvents.Kind.CONNECTING));
            assertTrue(events.contains(NetworkEvents.Kind.FAILED));
            assertFalse(events.contains(NetworkEvents.Kind.CONNECTED));
            assertEquals(0, sender.connectedCount());
            Thread.sleep(100);
            assertEquals(1, invitations.get());
        }
    }
}
