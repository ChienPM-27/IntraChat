package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Deterministic caps for a single in-RAM verified peer conversation. */
class ConversationRamBudgetTest {
    @Test void fiveHundredthMessageAcceptedFiveHundredAndFirstRejected()throws Exception{
        var budget=new ConversationRamBudget(500,2*1024*1024);
        for(int i=0;i<500;i++)budget.reserve(3);
        assertEquals(500,budget.count());
        assertEquals(1500,budget.bytes());
        assertThrows(IOException.class,()->budget.reserve(1));
        budget.clear();
        assertEquals(0,budget.count());
        assertEquals(0,budget.bytes());
        budget.reserve(1);
    }
    @Test void exactlyTwoMiBAcceptedThenOneExtraByteRejected()throws Exception{
        var budget=new ConversationRamBudget(500,2*1024*1024);
        budget.reserve(2*1024*1024);
        assertEquals(2*1024*1024,budget.bytes());
        assertThrows(IOException.class,()->budget.reserve(1));
        assertThrows(IOException.class,()->budget.reserve(2*1024*1024+1));
        assertThrows(IllegalArgumentException.class,()->budget.reserve(-1));
        assertEquals(1,budget.count());
    }
    @Test void concurrentReservationsCannotExceedMessageOrByteCap()throws Exception {
        var budget=new ConversationRamBudget(500,2*1024*1024);
        ExecutorService pool=Executors.newFixedThreadPool(24);
        CountDownLatch start=new CountDownLatch(1);
        try{
            List<Future<Boolean>> results=new ArrayList<>();
            for(int i=0;i<1024;i++)results.add(pool.submit(()->{
                start.await();
                try{budget.reserve(4096);return true;}
                catch(IOException full){return false;}
            }));
            start.countDown();
            int accepted=0;
            for(var result:results)if(result.get(7,TimeUnit.SECONDS))accepted++;
            assertEquals(500,accepted);
            assertEquals(500,budget.count());
            assertEquals(500*4096,budget.bytes());
            assertThrows(IOException.class,()->budget.reserve(1));
        }finally{start.countDown();pool.shutdownNow();}
    }
}
