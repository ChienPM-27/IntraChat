package vn.intrachat.security.history;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import vn.intrachat.domain.history.HistoryConversationKey;
import vn.intrachat.domain.history.HistoryScope;
import static org.junit.jupiter.api.Assertions.*;

class P2PHistoryAuthorizationTest {
    static byte[] key(int b) { byte[] v=new byte[32]; v[0]=(byte)b; return v; }

    @Test void exactPairWithAllVerifiedGatesCanDiscloseOnlyOwnHistory() {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        HistoryScope ab=HistoryConversationKey.forVerifiedPeers(a,key(1),b,key(2));
        assertTrue(VerifiedP2PHistoryAccess.canDisclose(ab,a,key(1),b,key(2),true,true,true,true));
        assertTrue(VerifiedP2PHistoryAccess.canDisclose(ab,b,key(2),a,key(1),true,true,true,true));
        assertFalse(VerifiedP2PHistoryAccess.canDisclose(ab,a,key(1),b,key(2),false,true,true,true));
        assertFalse(VerifiedP2PHistoryAccess.canDisclose(ab,a,key(1),b,key(2),true,false,true,true));
        assertFalse(VerifiedP2PHistoryAccess.canDisclose(ab,a,key(1),b,key(2),true,true,false,true));
        assertFalse(VerifiedP2PHistoryAccess.canDisclose(ab,a,key(1),b,key(2),true,true,true,false));
    }

    @Test void authenticatedOutsiderWhoGuessesConversationIdGetsNoCountOrGrant() {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
        HistoryScope ab=HistoryConversationKey.forVerifiedPeers(a,key(1),b,key(2));
        assertFalse(VerifiedP2PHistoryAccess.canDisclose(ab,a,key(1),c,key(3),true,true,true,true));
        assertFalse(VerifiedP2PHistoryAccess.canDisclose(ab,a,key(9),b,key(2),true,true,true,true));
        assertFalse(VerifiedP2PHistoryAccess.canDisclose(HistoryScope.group(ab.scopeId()),a,key(1),b,key(2),true,true,true,true));
        assertFalse(VerifiedP2PHistoryAccess.canDisclose(ab,a,new byte[3],b,key(2),true,true,true,true));
        assertFalse(VerifiedP2PHistoryAccess.canDisclose(ab,null,key(1),b,key(2),true,true,true,true));
    }
}
