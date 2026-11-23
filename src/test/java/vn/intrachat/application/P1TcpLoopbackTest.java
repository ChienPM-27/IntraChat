package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.intrachat.transport.NetworkConfig;
import vn.intrachat.domain.MessageAttempt;
import java.net.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class P1TcpLoopbackTest {
    @TempDir Path temp;
    private static void until(BooleanSupplier condition)throws Exception{
        long deadline=System.nanoTime()+Duration.ofSeconds(8).toNanos();
        while(System.nanoTime()<deadline){if(condition.getAsBoolean())return;Thread.sleep(20);}
        fail("Timed out waiting for actual TCP event");
    }
    @Test void twoWayTextAckReadReconnectAndIdentity()throws Exception{
        LocalProfile a=LocalProfile.loadOrCreate(temp.resolve("A"));
        LocalProfile b=LocalProfile.loadOrCreate(temp.resolve("B"));
        assertEquals(vn.intrachat.domain.P2PConversation.idFor(a.deviceId(),b.deviceId()),
            vn.intrachat.domain.P2PConversation.idFor(b.deviceId(),a.deviceId()));
        try(ServerSocket reserve=new ServerSocket(0)){
            int port=reserve.getLocalPort();reserve.close();
            try(LiveChatService sa=new LiveChatService(a);
                LiveChatService sb=new LiveChatService(b);
                LanCoordinator ca=new LanCoordinator(a, request->CompletableFuture.completedFuture(true),
                    peer->{try{sa.attach(peer.session(),peer.name());}catch(Exception ex){throw new RuntimeException(ex);}});
                LanCoordinator cb=new LanCoordinator(b, request->CompletableFuture.completedFuture(true),
                    peer->{try{sb.attach(peer.session(),peer.name());}catch(Exception ex){throw new RuntimeException(ex);}})){
                ca.startListening(new NetworkConfig("127.0.0.1",port));
                cb.connect(new NetworkConfig("127.0.0.1",port)).get(8,TimeUnit.SECONDS);
                until(()->sa.connectedPeers().containsKey(b.deviceId())&&sb.connectedPeers().containsKey(a.deviceId()));
                var first=sb.sendText(a.deviceId(),"Tiếng Việt 🧑‍💻 👨‍👩‍👧").get(8,TimeUnit.SECONDS);
                until(()->!sa.transcript(b.deviceId()).isEmpty());
                assertEquals(first.text(),sa.transcript(b.deviceId()).get(0).text());
                until(()->sb.status(first.messageId())==MessageAttempt.Status.APP_ACKNOWLEDGED);
                assertTrue(sa.markViewed(b.deviceId(),first.messageId(),first.conversationId()));
                until(()->sb.status(first.messageId())==MessageAttempt.Status.READ);
                var reply=sa.sendText(b.deviceId(),"Chào B").get(8,TimeUnit.SECONDS);
                until(()->sb.transcript(a.deviceId()).stream().anyMatch(m->m.messageId().equals(reply.messageId())));
                assertEquals(reply.text(),sb.transcript(a.deviceId()).stream().filter(m->m.messageId().equals(reply.messageId())).findFirst().orElseThrow().text());
                assertEquals(first.conversationId(),reply.conversationId());
                cb.disconnect(a.deviceId());sb.disconnect(a.deviceId());
                until(()->sa.connectedPeers().isEmpty());
                assertTrue(sa.transcript(b.deviceId()).stream().anyMatch(m->m.messageId().equals(first.messageId())),
                    "Offline RAM timeline must remain available (not yet durable History)");
                cb.connect(new NetworkConfig("127.0.0.1",port)).get(8,TimeUnit.SECONDS);
                until(()->sa.connectedPeers().containsKey(b.deviceId()));
                var newMessage=sb.sendText(a.deviceId(),"Reconnect").get(8,TimeUnit.SECONDS);
                until(()->sa.transcript(b.deviceId()).stream().anyMatch(m->m.messageId().equals(newMessage.messageId())));
            }
        }
    }
    @Test void rejectsConnection()throws Exception{
        LocalProfile a=LocalProfile.loadOrCreate(temp.resolve("x"));
        LocalProfile b=LocalProfile.loadOrCreate(temp.resolve("y"));
        try(ServerSocket reserve=new ServerSocket(0)){
            int port=reserve.getLocalPort();reserve.close();
            try(LanCoordinator ca=new LanCoordinator(a, request->CompletableFuture.completedFuture(false), p->{});
                LanCoordinator cb=new LanCoordinator(b, request->CompletableFuture.completedFuture(true), p->{})){
                ca.startListening(new NetworkConfig("127.0.0.1",port));
                assertThrows(ExecutionException.class,()->cb.connect(new NetworkConfig("127.0.0.1",port)).get(8,TimeUnit.SECONDS));
                assertTrue(ca.connectedPeers().isEmpty());
            }
        }
    }
}
