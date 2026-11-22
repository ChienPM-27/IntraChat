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

    @Test void truncatedHelloAfterClaimedLengthClosesWithoutPrompt() throws Exception {
        int port;
        try(ServerSocket free=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            port=free.getLocalPort();
        }
        AtomicInteger prompts=new AtomicInteger();
        try(var receiver=new NetworkCoordinator(new PeerIdentity(UUID.randomUUID(),"Receiver"),
                req->{prompts.incrementAndGet();
                    return CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT);},e->{})){
            receiver.startListening(new NetworkConfig("127.0.0.1",port));
            try(Socket partial=new Socket("127.0.0.1",port)){
                partial.setSoTimeout(2500);
                // 64-byte advertised HELLO body, but only five bytes transmitted.
                partial.getOutputStream().write(new byte[]{0,0,0,64,'h','e','l','l','o'});
                partial.getOutputStream().flush();
                partial.shutdownOutput();
                assertEquals(-1,partial.getInputStream().read());
            }
            for(int i=0;i<100&&receiver.pendingApprovalCount()!=0;i++)Thread.sleep(10);
            assertEquals(0,prompts.get());
            assertEquals(0,receiver.connectedCount());
            assertEquals(0,receiver.pendingApprovalCount());
        }
    }

    @Test void refusedRemoteAndPortConflictFailWithoutAutoReconnect() throws Exception {
        int unavailable;
        try(ServerSocket free=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            unavailable=free.getLocalPort();
        }
        AtomicInteger outboundAttempts=new AtomicInteger();
        try(var sender=new NetworkCoordinator(new PeerIdentity(UUID.randomUUID(),"Sender"),
                req->CompletableFuture.completedFuture(InboundApprovalPort.Decision.REJECT),
                event->{if(event.kind()==NetworkEvents.Kind.CONNECTING)outboundAttempts.incrementAndGet();})){
            assertThrows(ExecutionException.class,()->sender.connect(
                new NetworkConfig("127.0.0.1",unavailable)).get(6,TimeUnit.SECONDS));
            Thread.sleep(120);
            assertEquals(1,outboundAttempts.get(),"No auto reconnect after refusal");
            assertEquals(0,sender.connectedCount());
        }
        int used;
        try(ServerSocket free=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            used=free.getLocalPort();
        }
        InboundApprovalPort deny=req->CompletableFuture.completedFuture(InboundApprovalPort.Decision.REJECT);
        try(var a=new NetworkCoordinator(new PeerIdentity(UUID.randomUUID(),"A"),deny,e->{});
            var b=new NetworkCoordinator(new PeerIdentity(UUID.randomUUID(),"B"),deny,e->{})){
            a.startListening(new NetworkConfig("127.0.0.1",used));
            assertThrows(IOException.class,()->b.startListening(new NetworkConfig("127.0.0.1",used)));
            assertFalse(b.isListening());
            assertTrue(a.isListening());
        }
    }

    @Test void stalledHelloHonorsFiveSecondPreapprovalTimeout() throws Exception {
        int port;
        try(ServerSocket free=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            port=free.getLocalPort();
        }
        AtomicInteger prompts=new AtomicInteger();
        try(var receiver=new NetworkCoordinator(new PeerIdentity(UUID.randomUUID(),"B"),
                req->{prompts.incrementAndGet();
                    return CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT);},e->{})){
            receiver.startListening(new NetworkConfig("127.0.0.1",port));
            long started=System.nanoTime();
            try(Socket silent=new Socket("127.0.0.1",port)){
                silent.setSoTimeout(NetworkConfig.HELLO_TIMEOUT_MS+3500);
                // No HELLO bytes. The server must drop the candidate itself.
                assertEquals(-1,silent.getInputStream().read());
            }
            long elapsedMs=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
            assertTrue(elapsedMs >= NetworkConfig.HELLO_TIMEOUT_MS-300,
                "Premature stop is okay only for explicit error, not a fake completed handshake");
            assertTrue(elapsedMs < NetworkConfig.HELLO_TIMEOUT_MS+3500);
            assertEquals(0,prompts.get());
            assertEquals(0,receiver.connectedCount());
        }
    }
}
