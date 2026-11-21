package vn.intrachat.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class P2PConversationTest {
    @Test void duplicateDisplayNameDoesNotCollapseTwoVerifiedDeviceIdentities() {
        PeerIdentity first=new PeerIdentity(UUID.randomUUID(),"Same Display Name");
        PeerIdentity second=new PeerIdentity(UUID.randomUUID(),"Same Display Name");
        PeerIdentity third=new PeerIdentity(UUID.randomUUID(),"Same Display Name");
        byte[] fpA=new byte[32],fpB=new byte[32],fpC=new byte[32];
        fpA[0]=1;fpB[0]=2;fpC[0]=3;
        UUID ab=P2PConversation.idFor(first.deviceId(),fpA,second.deviceId(),fpB);
        UUID ac=P2PConversation.idFor(first.deviceId(),fpA,third.deviceId(),fpC);
        assertNotEquals(ab,ac);
    }
    @Test void independentCanonicalConversationUuidVector() {
        UUID a=UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID b=UUID.fromString("22222222-2222-4222-8222-222222222222");
        byte[] fpA=new byte[32],fpB=new byte[32];
        for(int i=0;i<32;i++){fpA[i]=(byte)i;fpB[i]=(byte)(i+32);}
        UUID expected=UUID.fromString("5bc726bf-5e70-5d11-8b49-774e77637482");
        assertEquals(expected,P2PConversation.idFor(a,fpA,b,fpB));
        assertEquals(expected,P2PConversation.idFor(b,fpB,a,fpA));
        assertEquals(5,expected.version());
        assertEquals(2,expected.variant());
    }
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
