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
        assertTrue(MainChatFrame.shouldShowOutgoingReceipt(older,newer,self));
        assertTrue(MainChatFrame.shouldShowOutgoingReceipt(newer,newer,self));
    }
    @Test void incomingAndOlderDeliveredMessagesDoNotShowManualRetry(){
        String self=UUID.randomUUID().toString();
        var older=message(self,DeliveryState.DELIVERED);
        var newest=message(self,DeliveryState.SEEN);
        var incoming=message(UUID.randomUUID().toString(),DeliveryState.UNCERTAIN);
        assertFalse(MainChatFrame.shouldShowOutgoingReceipt(older,newest,self));
        assertFalse(MainChatFrame.shouldShowOutgoingReceipt(incoming,newest,self));
    }
}
