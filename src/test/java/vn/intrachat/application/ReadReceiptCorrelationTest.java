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

/** Actual TLS session with a deliberately uncooperative but cryptographically verified peer. */
class ReadReceiptCorrelationTest {
    @TempDir Path tmp;

    private static ChatV2Message receipt(TlsChatSession sender,UUID conversation, UUID original) {
        return new ChatV2Message(UUID.randomUUID(),ChatV2Type.READ_RECEIPT,
                sender.sessionId(),sender.localId(),sender.remoteId(),conversation,
                Instant.now(),Map.of("originalMessageId",original.toString(),
                    "readEventId",UUID.randomUUID().toString(),
                    "viewedAtUTC",Instant.now().toString()));
    }

    private static ChatV2Message ack(TlsChatSession sender,UUID conversation,UUID original) {
        return new ChatV2Message(UUID.randomUUID(),ChatV2Type.DELIVERY_ACK,
                sender.sessionId(),sender.localId(),sender.remoteId(),conversation,
                Instant.now(),Map.of("originalMessageId",original.toString(),
                    "acceptedAtUTC",Instant.now().toString(),"result","ACCEPTED"));
    }

    private static void until(java.util.concurrent.Callable<Boolean> ready)throws Exception {
        for(int i=0;i<120;i++){if(ready.call())return;Thread.sleep(20);}
        fail("Missing expected authenticated state/event");
    }

    @Test void wrongOriginalIsIgnoredAndReadBeforeAckIsMonotonicWithoutDuplicateUiEvents() throws Exception {
        DeviceIdentity a=DeviceCertificateFactory.create(UUID.randomUUID());
        DeviceIdentity b=DeviceCertificateFactory.create(UUID.randomUUID());
        InboundApprovalPort consent=req->CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT);
        FingerprintApprovalPort comparison=req->CompletableFuture.completedFuture(true);
        int port;try(ServerSocket free=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            port=free.getLocalPort();
        }
        AtomicInteger readEvents=new AtomicInteger(),ackEvents=new AtomicInteger();
        CountDownLatch connected=new CountDownLatch(1);
        try(LiveChatService service=new LiveChatService(a);
            SecureChatCoordinator receiver=new SecureChatCoordinator(a,"Alice",
                new PinnedPeerStore(tmp.resolve("a.pin")),consent,comparison,session->{
                    try{service.attach(session,"Bob");}
                    catch(Exception problem){throw new IllegalStateException(problem);}
                });
            SecureChatCoordinator sender=new SecureChatCoordinator(b,"Bob",
                new PinnedPeerStore(tmp.resolve("b.pin")),consent,comparison,s->{})) {
            service.addListener(event->{
                if(event.kind()==LiveChatService.EventKind.CONNECTED)connected.countDown();
                if(event.kind()==LiveChatService.EventKind.STATUS_CHANGED){
                    if(event.status()==MessageAttempt.Status.READ)readEvents.incrementAndGet();
                    if(event.status()==MessageAttempt.Status.APP_ACKNOWLEDGED)ackEvents.incrementAndGet();
                }
            });
            receiver.startListening(new NetworkConfig("127.0.0.1",port));
            TlsChatSession malicious=sender.connect(new NetworkConfig("127.0.0.1",port))
                .get(12,TimeUnit.SECONDS);
            assertTrue(connected.await(4,TimeUnit.SECONDS));
            LiveChatService.Message outbound=service.sendText(b.deviceId(),"Kiểm tra ACK/READ")
                .get(5,TimeUnit.SECONDS);
            ChatV2Message transmitted=malicious.receive();
            assertEquals(ChatV2Type.CHAT_TEXT,transmitted.type());
            assertEquals(outbound.messageId(),transmitted.messageId());
            UUID conversation=outbound.conversationId();
            malicious.send(ack(malicious,conversation,UUID.randomUUID()));
            malicious.send(receipt(malicious,conversation,UUID.randomUUID()));
            Thread.sleep(200);
            assertEquals(MessageAttempt.Status.LOCAL_SENT,service.status(outbound.messageId()));
            assertEquals(0,readEvents.get());
            assertEquals(0,ackEvents.get());
            malicious.send(receipt(malicious,conversation,outbound.messageId()));
            until(()->service.status(outbound.messageId())==MessageAttempt.Status.READ);
            assertEquals(1,readEvents.get());
            assertEquals(0,ackEvents.get());
            malicious.send(receipt(malicious,conversation,outbound.messageId()));
            malicious.send(ack(malicious,conversation,outbound.messageId()));
            Thread.sleep(250);
            assertEquals(MessageAttempt.Status.READ,service.status(outbound.messageId()));
            assertEquals(1,readEvents.get(),"Duplicate authenticated READ must not create extra UI events");
            assertEquals(0,ackEvents.get(),"Late ACK must not downgrade READ");
        }
    }
}
