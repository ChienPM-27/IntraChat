package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.intrachat.domain.P2PConversation;
import vn.intrachat.protocol.*;
import vn.intrachat.security.*;
import vn.intrachat.transport.*;
import java.net.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class IncomingChatRoutingTest {
    @TempDir Path dir;

    @Test void duplicateAcceptedMessageGetsRepeatedAckButOnlyOneBubble() throws Exception {
        DeviceIdentity a=DeviceCertificateFactory.create(UUID.randomUUID());
        DeviceIdentity b=DeviceCertificateFactory.create(UUID.randomUUID());
        InboundApprovalPort consent=req->CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT);
        FingerprintApprovalPort human=req->CompletableFuture.completedFuture(true);
        int port;try(ServerSocket socket=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            port=socket.getLocalPort();
        }
        CountDownLatch ready=new CountDownLatch(1);
        AtomicInteger bubbles=new AtomicInteger();
        try(LiveChatService app=new LiveChatService(a);
            SecureChatCoordinator server=new SecureChatCoordinator(a,"Alice",
                new PinnedPeerStore(dir.resolve("pinsA.bin")),consent,human,session->{
                    try{app.attach(session,"Same Display Name");}catch(Exception ex){throw new IllegalStateException(ex);}
                });
            SecureChatCoordinator client=new SecureChatCoordinator(b,"Same Display Name",
                new PinnedPeerStore(dir.resolve("pinsB.bin")),consent,human,s->{})) {
            app.addListener(event->{
                if(event.kind()==LiveChatService.EventKind.CONNECTED)ready.countDown();
                if(event.kind()==LiveChatService.EventKind.RECEIVED)bubbles.incrementAndGet();
            });
            server.startListening(new NetworkConfig("127.0.0.1",port));
            TlsChatSession raw=client.connect(new NetworkConfig("127.0.0.1",port)).get(12,TimeUnit.SECONDS);
            assertTrue(ready.await(4,TimeUnit.SECONDS));
            raw.setReadTimeout(700);
            UUID convo=P2PConversation.idFor(a.deviceId(),a.fingerprint(),b.deviceId(),b.fingerprint());
            UUID id=UUID.randomUUID();
            Instant sentAt=Instant.now();
            ChatV2Message text=new ChatV2Message(id,ChatV2Type.CHAT_TEXT,raw.sessionId(),
                b.deviceId(),a.deviceId(),convo,sentAt,Map.of("text","Xin chào 👋"));
            raw.send(text);
            ChatV2Message first=raw.receive();
            assertEquals(ChatV2Type.DELIVERY_ACK,first.type());
            assertEquals(id.toString(),first.payload().get("originalMessageId"));
            raw.send(text);
            ChatV2Message second=raw.receive();
            assertEquals(ChatV2Type.DELIVERY_ACK,second.type(),
                    "Lost first ACK must be recoverable by manual same-ID retry");
            assertEquals(id.toString(),second.payload().get("originalMessageId"));
            assertEquals(1,bubbles.get(),"Dedup must avoid a second incoming bubble");
            assertEquals(1,app.transcript(b.deviceId()).size());
            assertTrue(app.connectedPeers().containsKey(b.deviceId()),
                "Authenticated peer directory must use UUID, never the shared display label");
            raw.send(new ChatV2Message(UUID.randomUUID(),ChatV2Type.CHAT_TEXT,
                raw.sessionId(),b.deviceId(),a.deviceId(),UUID.randomUUID(),
                Instant.now(),Map.of("text","wrong conversation must not display")));
            for(int i=0;i<150&&!app.connectedPeers().isEmpty();i++)Thread.sleep(20);
            assertTrue(app.connectedPeers().isEmpty(),
                "A forged conversation must fail closed on the authenticated TLS link");
            assertEquals(1,bubbles.get(),"Wrong conversation must not create a bubble");
        }
    }
}
