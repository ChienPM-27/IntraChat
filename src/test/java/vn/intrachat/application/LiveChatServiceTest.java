package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.intrachat.domain.*;
import vn.intrachat.security.*;
import vn.intrachat.transport.*;
import java.net.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class LiveChatServiceTest {
    @TempDir Path tmp;

    @Test void genuineMessageDeliveryAckThenExplicitViewReceiptOverMutualTls() throws Exception {
        DeviceIdentity a=DeviceCertificateFactory.create(UUID.randomUUID());
        DeviceIdentity b=DeviceCertificateFactory.create(UUID.randomUUID());
        PinnedPeerStore pa=new PinnedPeerStore(tmp.resolve("a.pin"));
        PinnedPeerStore pb=new PinnedPeerStore(tmp.resolve("b.pin"));
        InboundApprovalPort accept=req->CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT);
        FingerprintApprovalPort human=req->CompletableFuture.completedFuture(true); // test double, never production
        int port;try(ServerSocket s=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){port=s.getLocalPort();}
        CountDownLatch received=new CountDownLatch(1);
        try(LiveChatService alice=new LiveChatService(a);
            LiveChatService bob=new LiveChatService(b);
            SecureChatCoordinator sa=new SecureChatCoordinator(a,"Alice",pa,accept,human,s->{try{alice.attach(s,"Bob");}catch(Exception e){throw new IllegalStateException(e);}});
            SecureChatCoordinator sb=new SecureChatCoordinator(b,"Bob",pb,accept,human,s->{})){
            alice.addListener(event->{if(event.kind()==LiveChatService.EventKind.RECEIVED)received.countDown();});
            sa.startListening(new NetworkConfig("127.0.0.1",port));
            TlsChatSession session=sb.connect(new NetworkConfig("127.0.0.1",port)).get(12,TimeUnit.SECONDS);
            bob.attach(session,"Alice");
            LiveChatService.Message sent=bob.sendText(a.deviceId(),"Xin chào 👩🏽‍💻").get(5,TimeUnit.SECONDS);
            assertTrue(received.await(5,TimeUnit.SECONDS));
            assertEquals("Xin chào 👩🏽‍💻",alice.transcript(b.deviceId()).get(0).text());
            waitFor(()->bob.status(sent.messageId())==MessageAttempt.Status.APP_ACKNOWLEDGED);
            assertNotEquals(MessageAttempt.Status.READ,bob.status(sent.messageId()),
                    "Delivery ACK must never imply READ");
            UUID conversation=sent.conversationId();
            assertFalse(alice.markViewed(b.deviceId(),sent.messageId(),UUID.randomUUID()));
            assertTrue(alice.markViewed(b.deviceId(),sent.messageId(),conversation));
            waitFor(()->bob.status(sent.messageId())==MessageAttempt.Status.READ);
            assertEquals(MessageAttempt.Status.READ,bob.status(sent.messageId()));
        }
    }
    private static void waitFor(java.util.concurrent.Callable<Boolean> condition)throws Exception{
        for(int i=0;i<150;i++){if(condition.call())return;Thread.sleep(20);}
        fail("Expected authenticated receipt/state update never arrived");
    }
}
