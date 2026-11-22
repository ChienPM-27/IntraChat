package vn.intrachat.transport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.intrachat.application.*;
import vn.intrachat.domain.*;
import vn.intrachat.protocol.*;
import vn.intrachat.security.*;
import java.net.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** Two genuinely different first-frame profiles using the SAME physical loopback port. */
class SharedProtocolListenerTest {
    @TempDir Path temp;
    static int port()throws Exception{
        try(ServerSocket available=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            return available.getLocalPort();
        }
    }
    @Test void oneSocketListenerRoutesPlainDiagnosticAndMutualTlsSecureChatWithoutDowngrade()throws Exception{
        DeviceIdentity receiver=DeviceCertificateFactory.create(UUID.randomUUID());
        DeviceIdentity chatPeer=DeviceCertificateFactory.create(UUID.randomUUID());
        PeerIdentity diagnosticPeer=new PeerIdentity(UUID.randomUUID(),"M1-Test");
        var receiverPins=new PinnedPeerStore(temp.resolve("a.pins"));
        var senderPins=new PinnedPeerStore(temp.resolve("b.pins"));
        AtomicInteger m1Prompts=new AtomicInteger(),v2Prompts=new AtomicInteger();
        InboundApprovalPort consent=req->{
            m1Prompts.incrementAndGet();
            return CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT);
        };
        InboundApprovalPort secureConsent=req->{
            v2Prompts.incrementAndGet();
            return CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT);
        };
        FingerprintApprovalPort human=req->CompletableFuture.completedFuture(true);
        BlockingQueue<TlsChatSession> secured=new LinkedBlockingQueue<>();
        int port=port();
        try(var m1Receiver=new NetworkCoordinator(
                new PeerIdentity(receiver.deviceId(),"M1-Receiver"),consent,e->{});
            var m2Receiver=new SecureChatCoordinator(receiver,"M2-Receiver",receiverPins,
                secureConsent,human,secured::add);
            var m1Sender=new NetworkCoordinator(diagnosticPeer,req->
                CompletableFuture.completedFuture(InboundApprovalPort.Decision.REJECT),e->{});
            var m2Sender=new SecureChatCoordinator(chatPeer,"M2-Sender",senderPins,
                secureConsent,human,s->{});
            var listener=new SharedProtocolListener(m1Receiver::acceptSharedCandidate,
                m2Receiver::acceptSharedCandidate)){
            listener.start(new NetworkConfig("127.0.0.1",port));
            var diagnostic=m1Sender.connect(new NetworkConfig("127.0.0.1",port)).get(8,TimeUnit.SECONDS);
            assertEquals(receiver.deviceId(),diagnostic.deviceId());
            assertNotNull(m1Sender.ping(receiver.deviceId()).get(5,TimeUnit.SECONDS));
            assertEquals(1,m1Prompts.get());
            assertEquals(0,v2Prompts.get());
            TlsChatSession protectedClient=m2Sender.connect(new NetworkConfig("127.0.0.1",port))
                .get(15,TimeUnit.SECONDS);
            TlsChatSession protectedServer=secured.poll(5,TimeUnit.SECONDS);
            assertNotNull(protectedServer);
            assertTrue(protectedServer.canChat());
            assertTrue(protectedClient.canChat());
            UUID convo=P2PConversation.idFor(receiver.deviceId(),receiver.fingerprint(),
                chatPeer.deviceId(),chatPeer.fingerprint());
            var outgoing=new ChatV2Message(UUID.randomUUID(),ChatV2Type.CHAT_TEXT,
                protectedClient.sessionId(),chatPeer.deviceId(),receiver.deviceId(),convo,
                Instant.now(),Map.of("text","Thử chung cổng v1/v2 🔒"));
            protectedClient.send(outgoing);
            assertEquals(outgoing,protectedServer.receive());
            assertEquals(1,v2Prompts.get());
            assertEquals(1,m1Prompts.get(),"M2 must NEVER run the v1 unverified diagnostic handler");
            assertTrue(m1Sender.isConnectedTo(receiver.deviceId()));
            assertNotNull(m1Sender.ping(receiver.deviceId()).get(5,TimeUnit.SECONDS));
        }
    }
    @Test void unsupportedV3AndMalformedFirstFramesMustCloseWithoutPrompt()throws Exception{
        var prompts=new AtomicInteger();
        var v2Prompts=new AtomicInteger();
        var a=new PeerIdentity(UUID.randomUUID(),"A");
        int port=port();
        try(var m1=new NetworkCoordinator(a,req->{
                prompts.incrementAndGet();return CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT);
            },e->{});
            var listener=new SharedProtocolListener(m1::acceptSharedCandidate,(socket,frame)->{
                v2Prompts.incrementAndGet();
                try{socket.close();}catch(java.io.IOException ignored){}
            })){
            listener.start(new NetworkConfig("127.0.0.1",port));
            byte[] v3=new GroupV3BootstrapCodec().encodeHello(
                new GroupV3BootstrapCodec.Hello(UUID.randomUUID(),UUID.randomUUID(),"Future",new byte[32]));
            for(byte[] frame:List.of(v3,"{\"prefaceVersion\":9}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "{\"protocolVersion\":1,\"type\":\"CHAT_TEXT\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8))){
                try(Socket bad=new Socket("127.0.0.1",port)){
                    bad.setSoTimeout(3000);
                    FrameCodec.writeFrame(bad.getOutputStream(),frame,FrameCodec.HELLO_LIMIT);
                    assertEquals(-1,bad.getInputStream().read());
                }
            }
            assertEquals(0,prompts.get());
            assertEquals(0,v2Prompts.get());
        }
    }
}
