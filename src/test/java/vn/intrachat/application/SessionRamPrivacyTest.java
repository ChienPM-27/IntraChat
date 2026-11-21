package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.intrachat.domain.MessageAttempt;
import vn.intrachat.security.*;
import vn.intrachat.transport.*;
import java.net.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SessionRamPrivacyTest {
    @TempDir Path dir;

    @Test void disconnectedSessionErasesItsRamTranscriptAndAttemptLedger() throws Exception {
        DeviceIdentity a=DeviceCertificateFactory.create(UUID.randomUUID());
        DeviceIdentity b=DeviceCertificateFactory.create(UUID.randomUUID());
        InboundApprovalPort consent=req->CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT);
        FingerprintApprovalPort human=req->CompletableFuture.completedFuture(true);
        int port;try(ServerSocket free=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            port=free.getLocalPort();
        }
        CountDownLatch connected=new CountDownLatch(1),disconnected=new CountDownLatch(1);
        try(LiveChatService service=new LiveChatService(a);
            SecureChatCoordinator server=new SecureChatCoordinator(a,"Alice",
                new PinnedPeerStore(dir.resolve("alice.pin")),consent,human,s->{
                    try{service.attach(s,"Bob");}catch(Exception ex){throw new IllegalStateException(ex);}
                });
            SecureChatCoordinator client=new SecureChatCoordinator(b,"Bob",
                new PinnedPeerStore(dir.resolve("bob.pin")),consent,human,s->{})){
            service.addListener(event->{
                if(event.kind()==LiveChatService.EventKind.CONNECTED)connected.countDown();
                if(event.kind()==LiveChatService.EventKind.DISCONNECTED)disconnected.countDown();
            });
            server.startListening(new NetworkConfig("127.0.0.1",port));
            TlsChatSession remote=client.connect(new NetworkConfig("127.0.0.1",port)).get(12,TimeUnit.SECONDS);
            assertTrue(connected.await(4,TimeUnit.SECONDS));
            LiveChatService.Message message=service.sendText(b.deviceId(),"A RAM-only text")
                .get(5,TimeUnit.SECONDS);
            assertNotNull(service.status(message.messageId()));
            assertEquals(1,service.transcript(b.deviceId()).size());
            remote.receive(); // ensure actual protected text arrived
            remote.close();
            assertTrue(disconnected.await(5,TimeUnit.SECONDS),"TLS close must release application session");
            assertFalse(service.connectedPeers().containsKey(b.deviceId()));
            assertTrue(service.transcript(b.deviceId()).isEmpty());
            assertNull(service.status(message.messageId()),
                "Closing a per-session chat must not retain a hidden unbounded outbound ledger");
        }
    }
}
