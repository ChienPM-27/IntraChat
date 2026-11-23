package vn.intrachat.domain;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class P2PConversationTest {
    @Test void stableIndependentOfOrderAndName() {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
        UUID conversation=P2PConversation.idFor(a,b);
        assertEquals(conversation,P2PConversation.idFor(b,a));
        assertEquals(conversation,P2PConversation.idFor(a,b));
        assertNotEquals(conversation,P2PConversation.idFor(a,c));
        assertThrows(IllegalArgumentException.class,()->P2PConversation.idFor(a,a));
    }
}
