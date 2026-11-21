package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.intrachat.domain.PeerIdentity;
import vn.intrachat.domain.P2PConversation;
import vn.intrachat.protocol.*;
import vn.intrachat.security.*;
import vn.intrachat.transport.*;
import java.net.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SecureChatCoordinatorTest {
    @TempDir Path tmp;
    @Test void manuallyAcceptedFirstPairOverRealTlsAllowsChatOnlyAfterBothConfirm() throws Exception {
        DeviceIdentity a=DeviceCertificateFactory.create(UUID.randomUUID());
        DeviceIdentity b=DeviceCertificateFactory.create(UUID.randomUUID());
        PinnedPeerStore aPins=new PinnedPeerStore(tmp.resolve("a.pin"));
        PinnedPeerStore bPins=new PinnedPeerStore(tmp.resolve("b.pin"));
        int port;
        try(ServerSocket free=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){port=free.getLocalPort();}
        InboundApprovalPort consent=req->CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT);
        FingerprintApprovalPort human=view->CompletableFuture.completedFuture(true);
        BlockingQueue<TlsChatSession> arrived=new LinkedBlockingQueue<>();
        try(SecureChatCoordinator receiver=new SecureChatCoordinator(a,"Peer A",aPins,consent,human,arrived::add);
            SecureChatCoordinator sender=new SecureChatCoordinator(b,"Peer B",bPins,consent,human,s->{})){
            receiver.startListening(new NetworkConfig("127.0.0.1",port));
            TlsChatSession sessionB=sender.connect(new NetworkConfig("127.0.0.1",port)).get(12,TimeUnit.SECONDS);
            TlsChatSession sessionA=arrived.poll(5,TimeUnit.SECONDS);
            assertNotNull(sessionA);
            assertTrue(sessionA.canChat());
            assertTrue(sessionB.canChat());
            assertTrue(aPins.matches(b.deviceId(),b.fingerprint()));
            assertTrue(bPins.matches(a.deviceId(),a.fingerprint()));
            UUID conversation=P2PConversation.idFor(a.deviceId(),a.fingerprint(),b.deviceId(),b.fingerprint());
            ChatV2Message message=new ChatV2Message(UUID.randomUUID(),ChatV2Type.CHAT_TEXT,
                sessionB.sessionId(),b.deviceId(),a.deviceId(),conversation,Instant.now(),
                Map.of("text","Xin chào Việt Nam 👋"));
            sessionB.send(message);
            assertEquals(message,sessionA.receive());
        }
    }
    @Test void changedKeyForPreviouslyPinnedDeviceIsRejected() throws Exception {
        UUID claimed=UUID.randomUUID();
        DeviceIdentity receiver=DeviceCertificateFactory.create(UUID.randomUUID());
        DeviceIdentity legitimate=DeviceCertificateFactory.create(claimed);
        DeviceIdentity impostor=DeviceCertificateFactory.create(claimed);
        PinnedPeerStore receiverPins=new PinnedPeerStore(tmp.resolve("receiver-pinned.bin"));
        PinnedPeerStore impostorPins=new PinnedPeerStore(tmp.resolve("impostor-pinned.bin"));
        receiverPins.pinNew(claimed,legitimate.fingerprint());
        int port;
        try(ServerSocket free=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            port=free.getLocalPort();
        }
        InboundApprovalPort consent=req->CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT);
        FingerprintApprovalPort human=view->CompletableFuture.completedFuture(true);
        try(SecureChatCoordinator a=new SecureChatCoordinator(receiver,"A",receiverPins,consent,human,s->{});
            SecureChatCoordinator b=new SecureChatCoordinator(impostor,"Impostor",impostorPins,consent,human,s->{})){
            a.startListening(new NetworkConfig("127.0.0.1",port));
            assertThrows(ExecutionException.class,()->b.connect(
                new NetworkConfig("127.0.0.1",port)).get(8,TimeUnit.SECONDS));
            assertTrue(receiverPins.matches(claimed,legitimate.fingerprint()));
            assertFalse(receiverPins.matches(claimed,impostor.fingerprint()));
        }
    }
    @Test void rejectedInboundNeverProceedsToTlsOrPin() throws Exception {
        DeviceIdentity a=DeviceCertificateFactory.create(UUID.randomUUID());
        DeviceIdentity b=DeviceCertificateFactory.create(UUID.randomUUID());
        PinnedPeerStore aPins=new PinnedPeerStore(tmp.resolve("a2.pin"));
        PinnedPeerStore bPins=new PinnedPeerStore(tmp.resolve("b2.pin"));
        int port;try(ServerSocket free=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){port=free.getLocalPort();}
        InboundApprovalPort reject=req->CompletableFuture.completedFuture(InboundApprovalPort.Decision.REJECT);
        FingerprintApprovalPort human=view->CompletableFuture.completedFuture(true);
        try(SecureChatCoordinator receiver=new SecureChatCoordinator(a,"Peer A",aPins,reject,human,s->{});
            SecureChatCoordinator sender=new SecureChatCoordinator(b,"Peer B",bPins,reject,human,s->{})){
            receiver.startListening(new NetworkConfig("127.0.0.1",port));
            assertThrows(ExecutionException.class,()->sender.connect(
                new NetworkConfig("127.0.0.1",port)).get(6,TimeUnit.SECONDS));
            assertFalse(aPins.known(b.deviceId()));
            assertFalse(bPins.known(a.deviceId()));
        }
    }
}
