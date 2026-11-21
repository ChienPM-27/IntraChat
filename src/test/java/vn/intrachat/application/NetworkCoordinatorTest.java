package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import vn.intrachat.domain.PeerIdentity;
import vn.intrachat.transport.NetworkConfig;
import java.net.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Reconnect requires an explicit caller command; declined peer does not become trusted. */
class NetworkCoordinatorTest {
    @Test void rejectThenExplicitManualRetryCanBeAccepted() throws Exception {
        int port;
        try (ServerSocket test = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            port = test.getLocalPort();
        }
        AtomicInteger invitations = new AtomicInteger();
        InboundApprovalPort explicitlyScripted = request -> CompletableFuture.completedFuture(
                invitations.incrementAndGet() == 1 ? InboundApprovalPort.Decision.REJECT
                                                    : InboundApprovalPort.Decision.ACCEPT);
        PeerIdentity b = new PeerIdentity(UUID.randomUUID(), "Receiver");
        try (NetworkCoordinator receiver = new NetworkCoordinator(b, explicitlyScripted, event -> {});
             NetworkCoordinator sender = new NetworkCoordinator(
                  new PeerIdentity(UUID.randomUUID(), "Sender"),
                  request -> CompletableFuture.completedFuture(InboundApprovalPort.Decision.REJECT),
                  event -> {})) {
            receiver.startListening(new NetworkConfig("127.0.0.1", port));
            assertThrows(ExecutionException.class,
                    () -> sender.connect(new NetworkConfig("127.0.0.1", port)).get(5, TimeUnit.SECONDS));
            assertEquals(1, invitations.get());
            Thread.sleep(100);
            assertEquals(1, invitations.get(), "No automatic reconnect after denial");
            assertEquals(0, sender.connectedCount());
            assertEquals(b.deviceId(), sender.reconnect().get(5, TimeUnit.SECONDS).deviceId());
            assertEquals(2, invitations.get(), "Only explicit reconnect sends another HELLO");
            sender.disconnect(b.deviceId());
        }
    }
}
