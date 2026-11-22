package ui;

import org.junit.jupiter.api.Test;
import model.*;
import ui.components.MessageBubblePanel;
import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Status updates are evidence-driven; UNKNOWN is never mistaken for DELIVERED. */
class LiveDeliveryStateRenderingTest {
    private static ChatMessage message(){
        return new ChatMessage(UUID.randomUUID().toString(),UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),Instant.now(),MessageContent.text("hello"),
            DeliveryState.SENDING);
    }
    private static <T extends Component> T find(Container root,String name,Class<T> type){
        for(Component child:root.getComponents()){
            if(name.equals(child.getName())&&type.isInstance(child))return type.cast(child);
            if(child instanceof Container nested){
                T found=find(nested,name,type);
                if(found!=null)return found;
            }
        }
        return null;
    }
    @Test void uncertainAndFailureAreNotAutomaticallyTreatedAsDeliveryOrRead(){
        var msg=message();
        ChatTimeline timeline=new ChatTimeline(msg.conversationId());
        timeline.merge(msg);
        assertTrue(timeline.advanceState(msg.id(),DeliveryState.SENT));
        assertTrue(timeline.advanceState(msg.id(),DeliveryState.UNCERTAIN));
        assertEquals(DeliveryState.UNCERTAIN,timeline.orderedMessages().getFirst().state());
        assertTrue(timeline.advanceState(msg.id(),DeliveryState.DELIVERED));
        assertFalse(timeline.advanceState(msg.id(),DeliveryState.UNCERTAIN));
        assertTrue(timeline.advanceState(msg.id(),DeliveryState.SEEN));
        assertFalse(timeline.advanceState(msg.id(),DeliveryState.FAILED));
        assertEquals(DeliveryState.SEEN,timeline.orderedMessages().getFirst().state());
    }
    @Test void uncertainOutgoingBubbleRequiresExplicitRetryAction(){
        var msg=message();
        var calls=new AtomicInteger();
        var bubble=new MessageBubblePanel(msg,true,false,"Bạn",null,
            messageId->{assertEquals(msg.id(),messageId);calls.incrementAndGet();});
        bubble.showReceipt(DeliveryState.SENT);
        JButton retry=find(bubble,"live-manual-retry",JButton.class);
        assertNotNull(retry);
        assertFalse(retry.isVisible());
        bubble.showReceipt(DeliveryState.UNCERTAIN);
        assertTrue(retry.isVisible());
        assertTrue(retry.getAccessibleContext().getAccessibleName().contains("trùng"));
        retry.doClick();
        assertEquals(1,calls.get());
        bubble.showReceipt(DeliveryState.DELIVERED);
        assertFalse(retry.isVisible());
        var incoming=new MessageBubblePanel(msg,false,false,"Peer",null,
            id->fail("Incoming message cannot be resent"));
        incoming.showReceipt(DeliveryState.UNCERTAIN);
        assertNull(find(incoming,"live-manual-retry",JButton.class));
    }
    @Test void firstStatusAfterFailedCannotDisplaySeenWithoutAuthenticatedEvidence(){
        assertEquals("Chưa rõ",DeliveryState.UNCERTAIN.label());
        assertEquals("Thất bại",DeliveryState.FAILED.label());
        assertEquals("Đang gửi",DeliveryState.SENDING.label());
        assertNotEquals(DeliveryState.SEEN,DeliveryState.UNCERTAIN);
    }
}
