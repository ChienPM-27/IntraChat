package ui;

import org.junit.jupiter.api.Test;
import ui.components.LiveMessageVisibilityObserver;
import java.awt.Rectangle;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class LiveMessageVisibilityTest {
    @Test void onlyRealForegroundViewportRowAfterDebounceEmitsRead() {
        AtomicLong clock=new AtomicLong();
        AtomicInteger receipts=new AtomicInteger();
        LiveMessageVisibilityObserver observer=new LiveMessageVisibilityObserver(clock::get,
            (convo,id)->receipts.incrementAndGet());
        var incoming=new LiveMessageVisibilityObserver.Row("chat-a","message-1",true,true,
            new Rectangle(20,20,80,40));
        var outgoing=new LiveMessageVisibilityObserver.Row("chat-a","self",false,true,
            new Rectangle(20,40,80,50));
        Rectangle viewport=new Rectangle(0,0,400,300);
        observer.observe(false,true,"chat-a",viewport,List.of(incoming));
        observer.observe(true,false,"chat-a",viewport,List.of(incoming));
        observer.observe(true,true,"chat-b",viewport,List.of(incoming));
        observer.observe(true,true,"chat-a",new Rectangle(0,600,200,300),List.of(incoming));
        observer.observe(true,true,"chat-a",viewport,List.of(outgoing,incoming));
        clock.addAndGet(200_000_000L);
        observer.observe(true,true,"chat-a",viewport,List.of(incoming));
        assertEquals(0,receipts.get());
        clock.addAndGet(60_000_000L);
        observer.observe(true,true,"chat-a",viewport,List.of(incoming));
        assertEquals(1,receipts.get());
        clock.addAndGet(1_000_000_000L);
        observer.observe(true,true,"chat-a",viewport,List.of(incoming));
        assertEquals(1,receipts.get(),"Read event must be idempotent");
    }
}
