package vn.intrachat.transport;
import org.junit.jupiter.api.Test;
import vn.intrachat.protocol.*;
import java.io.*;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class LanSessionTest {
    private record Pair(LanSession a,LanSession b) implements AutoCloseable {
        public void close(){a.close();b.close();}
    }
    private static Pair connected()throws Exception{
        try(ServerSocket listener=new ServerSocket(0)){
            Socket client=new Socket("127.0.0.1",listener.getLocalPort());
            Socket server=listener.accept();
            UUID a=UUID.randomUUID(),b=UUID.randomUUID();
            return new Pair(new LanSession(client,a,b),new LanSession(server,b,a));
        }
    }
    @Test void concurrentWritersDoNotInterleaveFrames()throws Exception{
        try(Pair pair=connected()){
            int total=60;
            CountDownLatch arrived=new CountDownLatch(total);
            Set<UUID> ids=ConcurrentHashMap.newKeySet();
            pair.b.start(msg->{ids.add(msg.messageId());arrived.countDown();},ex->{});
            try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
                List<Future<?>> tasks=new ArrayList<>();
                for(int i=0;i<total;i++){
                    int value=i;
                    tasks.add(pool.submit(()->{
                        try{
                            pair.a.send(new WireMessage(WireMessage.Type.TEXT,UUID.randomUUID(),UUID.randomUUID(),
                                pair.a.localId(),pair.a.remoteId(),Instant.now(),Map.of("text","T"+value)));
                        }catch(IOException e){throw new CompletionException(e);}
                    }));
                }
                for(var task:tasks)task.get(8,TimeUnit.SECONDS);
            }
            assertTrue(arrived.await(8,TimeUnit.SECONDS));
            assertEquals(total,ids.size());
            pair.a.close();
            pair.a.close();
            assertThrows(IOException.class,()->pair.a.send(new WireMessage(WireMessage.Type.TEXT,
                UUID.randomUUID(),UUID.randomUUID(),pair.a.localId(),pair.a.remoteId(),
                Instant.now(),Map.of("text","closed"))));
        }
    }
    @Test void senderBindingRejectsWrongPeer()throws Exception{
        try(Pair pair=connected()){
            assertThrows(IOException.class,()->pair.a.send(new WireMessage(WireMessage.Type.TEXT,
                UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),pair.a.remoteId(),
                Instant.now(),Map.of("text","bad sender"))));
            assertTrue(pair.a.isOpen());
        }
    }
    @Test void malformedOrOversizedFrameDisconnects()throws Exception{
        try(ServerSocket listening=new ServerSocket(0)){
            Socket attacker=new Socket("127.0.0.1",listening.getLocalPort());
            Socket receiving=listening.accept();
            UUID a=UUID.randomUUID(),b=UUID.randomUUID();
            try(attacker;LanSession session=new LanSession(receiving,b,a)){
                CountDownLatch closed=new CountDownLatch(1);
                session.start(msg->fail("Must reject corrupt frame"),ex->closed.countDown());
                DataOutputStream out=new DataOutputStream(attacker.getOutputStream());
                out.writeInt(WireCodec.MAX_FRAME+1);out.flush();
                assertTrue(closed.await(8,TimeUnit.SECONDS));
                assertFalse(session.isOpen());
            }
        }
    }
}
