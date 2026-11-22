package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.intrachat.domain.MessageAttempt;
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

class DeliveryAckTest {
    @TempDir Path temp;

    private ChatV2Message ack(TlsChatSession peer,UUID conversation,UUID original){
        return new ChatV2Message(UUID.randomUUID(),ChatV2Type.DELIVERY_ACK,
            peer.sessionId(),peer.localId(),peer.remoteId(),conversation,Instant.now(),
            Map.of("originalMessageId",original.toString(),"acceptedAtUTC",Instant.now().toString(),
                   "result","ACCEPTED"));
    }

    @Test void forgedReceiptSenderSessionAndConversationCannotAcknowledgeOtherMessage() throws Exception{
        DeviceIdentity alice=DeviceCertificateFactory.create(UUID.randomUUID());
        DeviceIdentity bob=DeviceCertificateFactory.create(UUID.randomUUID());
        InboundApprovalPort consent=req->
            CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT);
        FingerprintApprovalPort human=req->CompletableFuture.completedFuture(true);
        int port;
        try(ServerSocket s=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            port=s.getLocalPort();
        }
        CountDownLatch ready=new CountDownLatch(1),disconnected=new CountDownLatch(1);
        AtomicInteger acknowledgements=new AtomicInteger();
        try(LiveChatService service=new LiveChatService(alice);
            SecureChatCoordinator server=new SecureChatCoordinator(alice,"A",
                new PinnedPeerStore(temp.resolve("forged-a.pin")),consent,human,session->{
                    try{service.attach(session,"Bob");ready.countDown();}
                    catch(Exception error){throw new IllegalStateException(error);}
                });
            SecureChatCoordinator attacker=new SecureChatCoordinator(bob,"B",
                new PinnedPeerStore(temp.resolve("forged-b.pin")),consent,human,s->{})){
            service.addListener(event->{
                if(event.kind()==LiveChatService.EventKind.DISCONNECTED)disconnected.countDown();
                if(event.kind()==LiveChatService.EventKind.STATUS_CHANGED
                    &&event.status()==MessageAttempt.Status.APP_ACKNOWLEDGED)
                    acknowledgements.incrementAndGet();
            });
            server.startListening(new NetworkConfig("127.0.0.1",port));
            TlsChatSession link=attacker.connect(new NetworkConfig("127.0.0.1",port))
                .get(12,TimeUnit.SECONDS);
            assertTrue(ready.await(5,TimeUnit.SECONDS));
            LiveChatService.Message sent=service.sendText(bob.deviceId(),"Sensitive to ACK spoof")
                .get(5,TimeUnit.SECONDS);
            assertEquals(ChatV2Type.CHAT_TEXT,link.receive().type());
            assertThrows(vn.intrachat.protocol.ProtocolException.class,()->link.send(new ChatV2Message(
                UUID.randomUUID(),ChatV2Type.DELIVERY_ACK,UUID.randomUUID(),
                bob.deviceId(),alice.deviceId(),sent.conversationId(),Instant.now(),
                Map.of("originalMessageId",sent.messageId().toString(),
                    "acceptedAtUTC",Instant.now().toString(),"result","ACCEPTED"))));
            assertThrows(vn.intrachat.protocol.ProtocolException.class,()->link.send(new ChatV2Message(
                UUID.randomUUID(),ChatV2Type.DELIVERY_ACK,link.sessionId(),
                UUID.randomUUID(),alice.deviceId(),sent.conversationId(),Instant.now(),
                Map.of("originalMessageId",sent.messageId().toString(),
                    "acceptedAtUTC",Instant.now().toString(),"result","ACCEPTED"))));
            assertEquals(0,acknowledgements.get());
            // A correctly TLS-authenticated peer can send a cross-conversation
            // receipt, but the application must fail closed, not upgrade its state.
            link.send(new ChatV2Message(UUID.randomUUID(),ChatV2Type.DELIVERY_ACK,
                link.sessionId(),bob.deviceId(),alice.deviceId(),UUID.randomUUID(),
                Instant.now(),Map.of("originalMessageId",sent.messageId().toString(),
                    "acceptedAtUTC",Instant.now().toString(),"result","ACCEPTED")));
            assertTrue(disconnected.await(5,TimeUnit.SECONDS),
                "Cross-conversation ACK must close invalid session. Connected="
                    +service.connectedPeers()+" status="+service.status(sent.messageId())
                    +" transportOpen="+link.canChat());
            assertEquals(0,acknowledgements.get());
            assertNull(service.status(sent.messageId()),
                "Closed session releases its RAM attempt ledger without fake delivered");
        }
    }

    @Test void onlyMatchingProtectedAckElevatesDeliveryAndDuplicateDoesNotRepaint() throws Exception {
        DeviceIdentity alice=DeviceCertificateFactory.create(UUID.randomUUID());
        DeviceIdentity bob=DeviceCertificateFactory.create(UUID.randomUUID());
        InboundApprovalPort consent=req->CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT);
        FingerprintApprovalPort human=req->CompletableFuture.completedFuture(true);
        int port;try(ServerSocket free=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            port=free.getLocalPort();
        }
        CountDownLatch connected=new CountDownLatch(1);
        AtomicInteger delivered=new AtomicInteger();
        try(LiveChatService sender=new LiveChatService(alice);
            SecureChatCoordinator receiver=new SecureChatCoordinator(alice,"A",
                new PinnedPeerStore(temp.resolve("a.pin")),consent,human,session->{
                    try{sender.attach(session,"B");}catch(Exception ex){throw new IllegalStateException(ex);}
                });
            SecureChatCoordinator rawPeer=new SecureChatCoordinator(bob,"B",
                new PinnedPeerStore(temp.resolve("b.pin")),consent,human,s->{})) {
            sender.addListener(event->{
                if(event.kind()==LiveChatService.EventKind.CONNECTED)connected.countDown();
                if(event.kind()==LiveChatService.EventKind.STATUS_CHANGED
                    &&event.status()==MessageAttempt.Status.APP_ACKNOWLEDGED)
                    delivered.incrementAndGet();
            });
            receiver.startListening(new NetworkConfig("127.0.0.1",port));
            TlsChatSession peer=rawPeer.connect(new NetworkConfig("127.0.0.1",port))
                .get(12,TimeUnit.SECONDS);
            assertTrue(connected.await(4,TimeUnit.SECONDS));
            LiveChatService.Message sent=sender.sendText(bob.deviceId(),"Kiểm tra nhận thực ACK")
                .get(5,TimeUnit.SECONDS);
            assertEquals(ChatV2Type.CHAT_TEXT,peer.receive().type());
            peer.send(ack(peer,sent.conversationId(),UUID.randomUUID()));
            Thread.sleep(140);
            assertEquals(MessageAttempt.Status.LOCAL_SENT,sender.status(sent.messageId()));
            assertEquals(0,delivered.get());
            peer.send(ack(peer,sent.conversationId(),sent.messageId()));
            for(int i=0;i<100&&sender.status(sent.messageId())!=MessageAttempt.Status.APP_ACKNOWLEDGED;i++)
                Thread.sleep(20);
            assertEquals(MessageAttempt.Status.APP_ACKNOWLEDGED,sender.status(sent.messageId()));
            assertEquals(1,delivered.get());
            peer.send(ack(peer,sent.conversationId(),sent.messageId()));
            Thread.sleep(120);
            assertEquals(1,delivered.get(),"Duplicate ACK cannot create repeated UI state updates");
            assertNotEquals(MessageAttempt.Status.READ,sender.status(sent.messageId()),
                "Delivery is not proof that UI displayed message");
        }
    }
}
