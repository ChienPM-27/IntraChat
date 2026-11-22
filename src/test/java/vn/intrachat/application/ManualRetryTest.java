package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.intrachat.domain.MessageAttempt;
import vn.intrachat.protocol.*;
import vn.intrachat.security.*;
import vn.intrachat.transport.*;
import java.net.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** A genuine verified TLS peer drops ACK; no automatic resend is allowed. */
class ManualRetryTest {
    @TempDir Path temp;
    private static int port()throws Exception{
        try(ServerSocket s=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            return s.getLocalPort();
        }
    }
    private static void until(Callable<Boolean> goal)throws Exception{
        for(int i=0;i<150;i++){if(goal.call())return;Thread.sleep(15);}
        fail("Expected state was never reached");
    }
    @Test void lostAckBecomesUncertainButNeverAutoResendsBeforeManualRetry()throws Exception{
        var a=DeviceCertificateFactory.create(UUID.randomUUID());
        var b=DeviceCertificateFactory.create(UUID.randomUUID());
        var consent=(InboundApprovalPort)req->CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT);
        var human=(FingerprintApprovalPort)view->CompletableFuture.completedFuture(true);
        var ready=new CountDownLatch(1);
        var uncertainties=new AtomicInteger();
        int port=port();
        try(var service=new LiveChatService(a,Duration.ofMillis(110));
            var server=new SecureChatCoordinator(a,"Alice",
                new PinnedPeerStore(temp.resolve("a.pin")),consent,human,s->{
                    try{service.attach(s,"Bob");ready.countDown();}
                    catch(Exception error){throw new IllegalStateException(error);}
                });
            var client=new SecureChatCoordinator(b,"Bob",
                new PinnedPeerStore(temp.resolve("b.pin")),consent,human,s->{})){
            service.addListener(e->{
                if(e.kind()==LiveChatService.EventKind.STATUS_CHANGED
                    &&e.status()==MessageAttempt.Status.UNCERTAIN)uncertainties.incrementAndGet();
            });
            server.startListening(new NetworkConfig("127.0.0.1",port));
            TlsChatSession peer=client.connect(new NetworkConfig("127.0.0.1",port))
                .get(12,TimeUnit.SECONDS);
            assertTrue(ready.await(5,TimeUnit.SECONDS));
            LiveChatService.Message sent=service.sendText(b.deviceId(),"Xin chào 👩🏽‍💻")
                .get(5,TimeUnit.SECONDS);
            ChatV2Message first=peer.receive();
            assertEquals(ChatV2Type.CHAT_TEXT,first.type());
            assertEquals(sent.messageId(),first.messageId());
            until(()->service.status(sent.messageId())==MessageAttempt.Status.UNCERTAIN);
            assertEquals(1,uncertainties.get());
            peer.setReadTimeout(250);
            assertThrows(SocketTimeoutException.class,peer::receive,
                "Missing ACK cannot trigger an automatic retransmission");
            LiveChatService.Message retried=service.retryUncertain(b.deviceId(),sent.messageId())
                .get(5,TimeUnit.SECONDS);
            ChatV2Message second=peer.receive();
            assertEquals(first.messageId(),second.messageId());
            assertEquals(first.sentAt(),second.sentAt());
            assertEquals(first.conversationId(),second.conversationId());
            assertEquals(first.payload(),second.payload());
            assertEquals(sent.messageId(),retried.messageId());
            assertEquals(1,service.transcript(b.deviceId()).size(),
                "Retry cannot invent another outgoing chat bubble");
            peer.send(new ChatV2Message(UUID.randomUUID(),ChatV2Type.DELIVERY_ACK,
                peer.sessionId(),peer.localId(),peer.remoteId(),sent.conversationId(),
                Instant.now(),Map.of("originalMessageId",sent.messageId().toString(),
                    "acceptedAtUTC",Instant.now().toString(),"result","ACCEPTED")));
            until(()->service.status(sent.messageId())==MessageAttempt.Status.APP_ACKNOWLEDGED);
            assertThrows(ExecutionException.class,()->service.retryUncertain(b.deviceId(),
                sent.messageId()).get(5,TimeUnit.SECONDS),
                "Delivered message must not be retried without a new explicit user action");
        }
    }
    @Test void retryUnknownOrOfflineAttemptFailsWithoutQueueing()throws Exception{
        var local=DeviceCertificateFactory.create(UUID.randomUUID());
        try(var service=new LiveChatService(local,Duration.ofMillis(90))){
            assertThrows(ExecutionException.class,()->service.retryUncertain(UUID.randomUUID(),
                UUID.randomUUID()).get(5,TimeUnit.SECONDS));
            assertTrue(service.connectedPeers().isEmpty());
        }
    }
}
