package ui;

import listener.*;
import model.*;
import model.mock.MockChatService;
import org.junit.jupiter.api.Test;
import vn.intrachat.application.LiveChatService;
import vn.intrachat.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Demo's seeded messages and timed fake Seen may occur ONLY inside the Demo
 * action handler, never in a Live account/device session without verified TLS.
 */
class DemoIsolationTest {
    @Test void mockCredentialsSeededHistoryAndSyntheticReceiptsNeverEnterLive()throws Exception{
        var device=DeviceCertificateFactory.create(UUID.randomUUID());
        var demoReceipts=new CountDownLatch(2);
        var liveEvents=new AtomicInteger();
        try(var mock=new MockChatService();
            var protectedService=new LiveChatService(device);
            var live=new LiveChatAdapter(device,"My device",protectedService,()->{})){
            var account=mock.signIn("Demo only",new char[]{'p','a','s','s'})
                .toCompletableFuture().get(3,TimeUnit.SECONDS);
            assertEquals("demo-self",account.id());
            assertFalse(live.self().id().startsWith("demo-"));
            CountDownLatch demoDirectory=new CountDownLatch(1);
            mock.connect(new ChatClientListener(){
                @Override public void onDirectoryChanged(ChatSnapshot snapshot){
                    assertTrue(snapshot.users().stream().anyMatch(u->u.id().startsWith("demo-")));
                    assertTrue(snapshot.conversations().stream().anyMatch(Conversation::group));
                    demoDirectory.countDown();
                }
                @Override public void onMessageReceived(ChatMessage message){}
                @Override public void onDeliveryStateChanged(String c,String m,DeliveryState s){
                    if(s==DeliveryState.DELIVERED||s==DeliveryState.SEEN)
                        demoReceipts.countDown();
                }
                @Override public void onConnectionError(String note){}
            });
            assertTrue(demoDirectory.await(3,TimeUnit.SECONDS));
            live.connect(new ChatClientListener(){
                @Override public void onDirectoryChanged(ChatSnapshot snapshot){
                    assertTrue(snapshot.conversations().isEmpty());
                    assertTrue(snapshot.users().isEmpty());
                }
                @Override public void onMessageReceived(ChatMessage m){liveEvents.incrementAndGet();}
                @Override public void onDeliveryStateChanged(String c,String m,DeliveryState s){
                    liveEvents.incrementAndGet();
                }
                @Override public void onConnectionError(String note){}
            });
            assertFalse(mock.loadHistory("direct-mai").toCompletableFuture()
                .get(3,TimeUnit.SECONDS).isEmpty());
            var demoMessage=mock.sendMessage("direct-mai",MessageContent.text("Demo synthetic"))
                .toCompletableFuture().get(3,TimeUnit.SECONDS);
            assertEquals("demo-self",demoMessage.senderId());
            assertTrue(demoReceipts.await(5,TimeUnit.SECONDS),
                "Only demo may emit timed simulated DELIVERY/SEEN");
            assertEquals(0,liveEvents.get());
            assertThrows(ExecutionException.class,()->live.loadHistory("direct-mai")
                .toCompletableFuture().get(3,TimeUnit.SECONDS));
            assertThrows(ExecutionException.class,()->live.sendMessage("direct-mai",
                MessageContent.text("No demo-to-Live network fallback"))
                .toCompletableFuture().get(3,TimeUnit.SECONDS));
            assertThrows(ExecutionException.class,()->live.signIn("Demo only",new char[]{'p'})
                .toCompletableFuture().get(3,TimeUnit.SECONDS));
        }
    }
}
