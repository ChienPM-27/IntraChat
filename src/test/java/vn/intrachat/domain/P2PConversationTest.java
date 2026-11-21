package vn.intrachat.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class P2PConversationTest {
    @Test void sharedPairHasStableUuidIndependentOfOrder() {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        byte[] ak=new byte[32],bk=new byte[32];ak[0]=5;bk[0]=2;
        UUID first=P2PConversation.idFor(a,ak,b,bk);
        assertEquals(first,P2PConversation.idFor(b,bk,a,ak));
        bk[1]=22;
        assertNotEquals(first,P2PConversation.idFor(a,ak,b,bk));
        assertThrows(IllegalArgumentException.class,()->P2PConversation.idFor(a,ak,a,bk));
    }
}
