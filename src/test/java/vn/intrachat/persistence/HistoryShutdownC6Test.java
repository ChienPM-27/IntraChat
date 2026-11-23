package vn.intrachat.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class HistoryShutdownC6Test {
    @TempDir Path dir;

    @Test void acceptedEventsSurviveConcurrentCloseAndRestart() throws Exception {
        UUID conversation=UUID.randomUUID(),sender=UUID.randomUUID(),recipient=UUID.randomUUID();
        var accepted=ConcurrentHashMap.<UUID>newKeySet();
        var pool=Executors.newFixedThreadPool(5);
        try {
            var repo=new HistoryRepository(dir);
            var start=new CountDownLatch(1);
            List<Future<?>> producers=new ArrayList<>();
            for(int worker=0;worker<4;worker++) {
                producers.add(pool.submit(()->{
                    try {
                        start.await();
                        for(int i=0;i<70;i++) {
                            UUID id=UUID.randomUUID();
                            var e=new HistoryRepository.Entry(id,conversation,"DIRECT",sender,recipient,
                                Instant.parse("2026-11-24T00:00:00Z"),"TEXT","test","SENDING",null,"",0,"","");
                            try {
                                if(repo.appendMessage(e))accepted.add(id);
                            } catch(IllegalStateException closed) {
                                assertTrue(closed.getMessage().contains("closed"));
                                break;
                            }
                        }
                    } catch(InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(ex);
                    }
                }));
            }
            Future<?> closer=pool.submit(()->{
                try {start.await();repo.close();}
                catch(InterruptedException ex){Thread.currentThread().interrupt();throw new RuntimeException(ex);}
            });
            start.countDown();
            for(var f:producers)f.get(20,TimeUnit.SECONDS);
            closer.get(20,TimeUnit.SECONDS);
            assertThrows(IllegalStateException.class,()->repo.appendMessage(
                new HistoryRepository.Entry(UUID.randomUUID(),conversation,"DIRECT",sender,recipient,
                    Instant.now(),"TEXT","late","SENDING",null,"",0,"","")));
            try(var restarted=new HistoryRepository(dir)) {
                for(UUID id:accepted)assertNotNull(restarted.find(id),"Lost accepted event "+id);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
