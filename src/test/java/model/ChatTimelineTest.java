package model;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChatTimelineTest {
    @Test
    void sortsDeduplicatesAndPreservesEarlyReceipt() {
        ChatTimeline timeline = new ChatTimeline("chat");
        ChatMessage first = message("first", "chat", Instant.EPOCH, "sender-first");
        ChatMessage second = message("second", "chat", Instant.EPOCH.plusSeconds(1), "sender-second");
        assertFalse(timeline.advanceState(second.id(), DeliveryState.SEEN));
        assertTrue(timeline.merge(second));
        assertTrue(timeline.merge(first));
        assertFalse(timeline.merge(second));
        assertFalse(timeline.merge(message("third", "other-chat", Instant.EPOCH, "sender-third")));
        assertEquals(List.of(first, second.withState(DeliveryState.SEEN)), timeline.orderedMessages());
    }

    private ChatMessage message(String id, String conversation, Instant instant, String sender) {
        return new ChatMessage(id, conversation, sender, instant, MessageContent.text("Xin chào 😊"), DeliveryState.SENT);
    }
}
