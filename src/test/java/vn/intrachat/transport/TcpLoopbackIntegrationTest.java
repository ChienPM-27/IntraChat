package vn.intrachat.transport;

import org.junit.jupiter.api.Test;
import vn.intrachat.application.InboundApprovalPort;
import vn.intrachat.application.NetworkCoordinator;
import vn.intrachat.domain.PeerIdentity;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class TcpLoopbackIntegrationTest {
    @Test void fiveLoopbackConnectHandshakePingAndDisconnectCycles() throws Exception {
        for (int i = 0; i < 5; i++) {
            int port;
            try (ServerSocket test = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
                port = test.getLocalPort();
            }
            InboundApprovalPort approvedByTest = request ->
                    CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT);
            PeerIdentity a = new PeerIdentity(UUID.randomUUID(), "Peer A");
            PeerIdentity b = new PeerIdentity(UUID.randomUUID(), "Peer B");
            try (NetworkCoordinator receiver = new NetworkCoordinator(a, approvedByTest, e -> {});
                 NetworkCoordinator sender = new NetworkCoordinator(b, approvedByTest, e -> {})) {
                receiver.startListening(new NetworkConfig("127.0.0.1", port));
                assertEquals(a.deviceId(),
                    sender.connect(new NetworkConfig("127.0.0.1", port)).get(6, TimeUnit.SECONDS).deviceId());
                assertNotNull(sender.ping(a.deviceId()).get(6, TimeUnit.SECONDS));
                assertNotNull(receiver.ping(b.deviceId()).get(6, TimeUnit.SECONDS));
                sender.disconnect(a.deviceId());
                assertEquals(0, sender.connectedCount());
                receiver.stopListening();
                assertFalse(receiver.isListening());
            }
        }
    }
}
