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

    @Test void concurrentSendersCannotReserveMoreThan128UnacknowledgedMessages()throws Exception {
        DeviceIdentity a=DeviceCertificateFactory.create(UUID.randomUUID());
        DeviceIdentity b=DeviceCertificateFactory.create(UUID.randomUUID());
        InboundApprovalPort consent=req->
            CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT);
        FingerprintApprovalPort fingerprint=req->CompletableFuture.completedFuture(true);
        int port;
        try(ServerSocket free=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            port=free.getLocalPort();
        }
        CountDownLatch ready=new CountDownLatch(1);
        try(LiveChatService service=new LiveChatService(a);
            SecureChatCoordinator server=new SecureChatCoordinator(a,"A",
                new PinnedPeerStore(dir.resolve("limit-a.pin")),consent,fingerprint,session->{
                    try{service.attach(session,"B");ready.countDown();}
                    catch(Exception problem){throw new IllegalStateException(problem);}
                });
            SecureChatCoordinator peer=new SecureChatCoordinator(b,"B",
                new PinnedPeerStore(dir.resolve("limit-b.pin")),consent,fingerprint,s->{})) {
            server.startListening(new NetworkConfig("127.0.0.1",port));
            TlsChatSession noAck=peer.connect(new NetworkConfig("127.0.0.1",port))
                .get(12,TimeUnit.SECONDS);
            assertTrue(ready.await(5,TimeUnit.SECONDS));
            ExecutorService producers=Executors.newFixedThreadPool(16);
            try{
                List<Future<CompletableFuture<LiveChatService.Message>>> calls=new ArrayList<>();
                for(int i=0;i<160;i++){
                    final int n=i;
                    calls.add(producers.submit(()->service.sendText(b.deviceId(),"M2 concurrent "+n)));
                }
                int accepted=0,rejected=0;
                for(var launched:calls){
                    try{
                        launched.get(5,TimeUnit.SECONDS).get(10,TimeUnit.SECONDS);
                        accepted++;
                    }catch(ExecutionException ex){rejected++;}
                }
                assertEquals(128,accepted,
                    "Exactly 128 unacknowledged messages may be admitted across concurrent senders");
                assertEquals(32,rejected);
                assertEquals(128,service.pendingReceiptCount());
                assertEquals(128,service.transcript(b.deviceId()).size());
                for(var line:service.transcript(b.deviceId()))
                    assertNotEquals(MessageAttempt.Status.APP_ACKNOWLEDGED,service.status(line.messageId()));
                noAck.close();
                for(int i=0;i<150&&service.pendingReceiptCount()!=0;i++)Thread.sleep(20);
                assertEquals(0,service.pendingReceiptCount(),
                    "Closing TLS must release every unacknowledged receipt slot");
                assertTrue(service.transcript(b.deviceId()).isEmpty());
            }finally{producers.shutdownNow();}
        }
    }

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
