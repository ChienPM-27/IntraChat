package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.intrachat.transport.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class MultiplePeersP1Test {
    @TempDir Path temp;
    @Test void oneListenerTwoPeersIndependentConversations()throws Exception{
        LocalProfile a=LocalProfile.loadOrCreate(temp.resolve("a"));
        LocalProfile b=LocalProfile.loadOrCreate(temp.resolve("b"));
        LocalProfile c=LocalProfile.loadOrCreate(temp.resolve("c"));
        int port;
        try(ServerSocket reserve=new ServerSocket(0)){port=reserve.getLocalPort();}
        try(LiveChatService service=new LiveChatService(a);
            LanCoordinator host=new LanCoordinator(a, i->CompletableFuture.completedFuture(true),p->{
                try{service.attach(p.session(),p.name());}catch(Exception e){throw new IllegalStateException(e);}
            });
            LanCoordinator pb=new LanCoordinator(b,i->CompletableFuture.completedFuture(true),p->{});
            LanCoordinator pc=new LanCoordinator(c,i->CompletableFuture.completedFuture(true),p->{})){
            host.startListening(new NetworkConfig("127.0.0.1",port));
            pb.connect(new NetworkConfig("127.0.0.1",port)).get(8,TimeUnit.SECONDS);
            pc.connect(new NetworkConfig("127.0.0.1",port)).get(8,TimeUnit.SECONDS);
            long deadline=System.nanoTime()+Duration.ofSeconds(8).toNanos();
            while(service.connectedPeers().size()<2&&System.nanoTime()<deadline)Thread.sleep(20);
            assertEquals(Set.of(b.deviceId(),c.deviceId()),service.connectedPeers().keySet());
            assertNotEquals(service.conversationId(b.deviceId()),service.conversationId(c.deviceId()));
            service.sendText(b.deviceId(),"For B").get(8,TimeUnit.SECONDS);
            service.sendText(c.deviceId(),"For C").get(8,TimeUnit.SECONDS);
            assertEquals("For B",service.transcript(b.deviceId()).get(0).text());
            assertEquals("For C",service.transcript(c.deviceId()).get(0).text());
            assertNotEquals(service.transcript(b.deviceId()).get(0).destinationId(),
                service.transcript(c.deviceId()).get(0).destinationId());
        }
    }
}
