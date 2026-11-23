package ui.frames;

import org.junit.jupiter.api.Test;
import model.*;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Every uncertain outgoing Live bubble must keep its own manual retry control visible. */
class LiveReceiptVisibilityTest {
    private static ChatMessage message(String self,DeliveryState state){
        return new ChatMessage(UUID.randomUUID().toString(),UUID.randomUUID().toString(),
            self,Instant.now(),MessageContent.text("Tin thử"),state);
    }
    @Test void earlierUncertainLiveMessagesStillDisplayStateAndManualRetry(){
        String self=UUID.randomUUID().toString();
        var older=message(self,DeliveryState.UNCERTAIN);
        var newer=message(self,DeliveryState.DELIVERED);
        assertTrue(MainChatFrame.shouldShowOutgoingReceipt(true,older,newer,self));
        assertTrue(MainChatFrame.shouldShowOutgoingReceipt(true,newer,newer,self));
        assertFalse(MainChatFrame.shouldShowOutgoingReceipt(false,older,newer,self),
            "Demo keeps its existing last-outgoing-only receipt behavior");
    }
    @Test void incomingAndOlderDeliveredMessagesDoNotShowManualRetry(){
        String self=UUID.randomUUID().toString();
        var older=message(self,DeliveryState.DELIVERED);
        var newest=message(self,DeliveryState.SEEN);
        var incoming=message(UUID.randomUUID().toString(),DeliveryState.UNCERTAIN);
        assertFalse(MainChatFrame.shouldShowOutgoingReceipt(true,older,newest,self));
        assertFalse(MainChatFrame.shouldShowOutgoingReceipt(true,incoming,newest,self));
    }
}
