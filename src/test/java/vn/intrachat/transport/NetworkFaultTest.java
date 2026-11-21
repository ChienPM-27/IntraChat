package vn.intrachat.transport;

import org.junit.jupiter.api.Test;
import vn.intrachat.application.*;
import vn.intrachat.domain.PeerIdentity;
import vn.intrachat.protocol.*;
import java.io.*;
import java.net.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Real loopback sockets test malformed preapproval input without any GUI prompt. */
class NetworkFaultTest {
    @Test void oversizedPreapprovalFrameRejectedWithoutUserPrompt() throws Exception {
        AtomicInteger promptCount = new AtomicInteger();
        int port;
        try (ServerSocket test = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            port = test.getLocalPort();
        }
        try (NetworkCoordinator receiver = new NetworkCoordinator(
                new PeerIdentity(UUID.randomUUID(), "Receiver"),
                request -> {
                    promptCount.incrementAndGet();
                    return CompletableFuture.completedFuture(InboundApprovalPort.Decision.REJECT);
                }, event -> {})) {
            receiver.startListening(new NetworkConfig("127.0.0.1", port));
            try (Socket attacker = new Socket("127.0.0.1", port)) {
                attacker.setSoTimeout(2500);
                attacker.getOutputStream().write(new byte[]{0, 1, 0, 1});
                attacker.getOutputStream().flush();
                assertEquals(-1, attacker.getInputStream().read(),
                        "Overlong preapproval frame should close connection");
            }
            for (int i = 0; i < 50 && receiver.pendingApprovalCount() != 0; i++) {
                Thread.sleep(20);
            }
            assertEquals(0, promptCount.get(), "Malformed preapproval input must not trigger consent dialog");
            assertEquals(0, receiver.connectedCount());
            assertEquals(0, receiver.pendingApprovalCount());
        }
    }

    @Test void unexpectedFrameBeforeHelloNeverPrompts() throws Exception {
        AtomicInteger prompts = new AtomicInteger();
        int port;
        try (ServerSocket test = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            port = test.getLocalPort();
        }
        try (NetworkCoordinator receiver = new NetworkCoordinator(
                new PeerIdentity(UUID.randomUUID(), "Receiver"),
                request -> {
                    prompts.incrementAndGet();
                    return CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT);
                }, event -> {})) {
            receiver.startListening(new NetworkConfig("127.0.0.1", port));
            try (Socket incoming = new Socket("127.0.0.1", port)) {
                incoming.setSoTimeout(2500);
                JsonEnvelopeCodec codec = new JsonEnvelopeCodec();
                byte[] invalid = codec.encode(NetworkEnvelope.create(MessageType.DIAGNOSTIC_PING,
                    new PeerIdentity(UUID.randomUUID(), "Unknown"),
                    receiver.localPeer().deviceId(),
                    java.util.Map.of("probeId", UUID.randomUUID().toString())));
                FrameCodec.writeFrame(incoming.getOutputStream(), invalid, FrameCodec.HELLO_LIMIT);
                assertEquals(-1, incoming.getInputStream().read());
            }
            assertEquals(0, prompts.get(), "Only a valid HELLO may request human consent");
        }
    }
}
