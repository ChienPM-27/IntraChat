package vn.intrachat.transport;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Deterministic queue frame/byte admission tests, without slow sockets or sleeps. */
class OutboundBudgetTest {
    @Test void sixteenthFrameAcceptedSeventeenthRejectedAndReleasedExactlyOnce() {
        var budget=new OutboundBudget(16,2*1024*1024);
        List<OutboundBudget.Reservation> leases=new ArrayList<>();
        for(int i=0;i<16;i++){
            OutboundBudget.Reservation lease=budget.tryReserve(1024);
            assertNotNull(lease);
            leases.add(lease);
        }
        assertNull(budget.tryReserve(1));
        assertEquals(16,budget.queuedFrames());
        assertEquals(16384,budget.queuedBytes());
        leases.getFirst().close();
        leases.getFirst().close(); // stale callback must never invent a permit
        assertEquals(15,budget.queuedFrames());
        assertNotNull(budget.tryReserve(1));
        assertNull(budget.tryReserve(1));
        for(var lease:leases)lease.close();
    }
    @Test void twoMegabyteLimitRejectsOneExtraByteAndAllowsCapacityRecovery(){
        var budget=new OutboundBudget(16,2*1024*1024);
        List<OutboundBudget.Reservation> leases=new ArrayList<>();
        for(int i=0;i<4;i++)leases.add(budget.tryReserve(512*1024));
        assertTrue(leases.stream().allMatch(Objects::nonNull));
        assertNull(budget.tryReserve(1),"At 2 MiB exactly, one more byte must fail");
        assertNull(budget.tryReserve(2*1024*1024+1));
        leases.getFirst().close();
        assertNotNull(budget.tryReserve(512*1024));
    }
    @Test void concurrentProducersCannotExceedEitherLimit()throws Exception{
        var budget=new OutboundBudget(16,2*1024*1024);
        var pool=Executors.newFixedThreadPool(32);
        CountDownLatch start=new CountDownLatch(1);
        CountDownLatch attempted=new CountDownLatch(32);
        CountDownLatch release=new CountDownLatch(1);
        try{
            List<Future<Boolean>> results=new ArrayList<>();
            for(int i=0;i<32;i++){
                results.add(pool.submit(()->{
                    start.await();
                    var lease=budget.tryReserve(64*1024);
                    attempted.countDown();
                    if(lease==null)return false;
                    try{release.await(3,TimeUnit.SECONDS);return true;}
                    finally{lease.close();}
                }));
            }
            start.countDown();
            int success=0;
            // Release only after every producer has attempted the same bounded queue.
            assertTrue(attempted.await(4,TimeUnit.SECONDS));
            assertEquals(16,budget.queuedFrames());
            release.countDown();
            for(var result:results)if(result.get(4,TimeUnit.SECONDS))success++;
            assertTrue(success<=16);
            assertEquals(0,budget.queuedFrames());
            assertEquals(0,budget.queuedBytes());
        }finally{release.countDown();pool.shutdownNow();}
    }
}
