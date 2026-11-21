package vn.intrachat.domain.history;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HistoryConversationKeyTest {
    static byte[] pin(int value) { byte[] pin=new byte[32]; java.util.Arrays.fill(pin,(byte)value); return pin; }

    @Test void exactVerifiedPairIsSymmetricAndCannotMergeThirdDevice() {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
        HistoryScope ab=HistoryConversationKey.forVerifiedPeers(a,pin(1),b,pin(2));
        HistoryScope ba=HistoryConversationKey.forVerifiedPeers(b,pin(2),a,pin(1));
        assertEquals(ab,ba);
        assertEquals(HistoryScope.Kind.P2P,ab.kind());
        assertNotEquals(ab,HistoryConversationKey.forVerifiedPeers(a,pin(1),c,pin(2)));
        assertNotEquals(ab,HistoryConversationKey.forVerifiedPeers(a,pin(3),b,pin(2)));
        assertNotEquals(ab,HistoryScope.group(ab.scopeId()));
    }

    @Test void groupIdentityIsNotDisplayNameOrP2PConversation() {
        UUID group=UUID.randomUUID();
        assertEquals(HistoryScope.group(group),HistoryScope.group(group));
        assertNotEquals(HistoryScope.group(group),HistoryScope.p2p(group));
        assertThrows(IllegalArgumentException.class,()->HistoryScope.group(new UUID(0,0)));
    }

    @Test void refusesUnverifiedInvalidOrSameDeviceIdentity() {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,()->HistoryConversationKey.forVerifiedPeers(a,pin(1),a,pin(1)));
        assertThrows(IllegalArgumentException.class,()->HistoryConversationKey.forVerifiedPeers(a,new byte[31],b,pin(2)));
        assertThrows(IllegalArgumentException.class,()->HistoryConversationKey.forVerifiedPeers(new UUID(0,0),pin(1),b,pin(2)));
        assertThrows(NullPointerException.class,()->HistoryScope.group(null));
    }
}
