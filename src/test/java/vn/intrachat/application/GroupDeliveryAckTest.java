package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import vn.intrachat.protocol.*;

import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent recipient ACK/READ evidence, not a group-wide optimistic state. */
class GroupDeliveryAckTest {
    private final UUID group=UUID.randomUUID(),local=UUID.randomUUID(),
        b=UUID.randomUUID(),c=UUID.randomUUID(),
        sessionB=UUID.randomUUID(),sessionC=UUID.randomUUID(),message=UUID.randomUUID();
    private final Instant now=Instant.parse("2026-11-22T12:34:00Z");
    private GroupV1Message ack(UUID actualSender,UUID activeSession,
                               UUID claimedGroup,long revision,UUID original){
        return new GroupV1Message(UUID.randomUUID(),GroupV1Type.GROUP_DELIVERY_ACK,
            activeSession,actualSender,local,claimedGroup,revision,now,
            Map.of("originalMessageId",original.toString(),"result","ACCEPTED",
                "acceptedAtUTC",now.toString()));
    }
    private GroupV1Message read(UUID sender,UUID activeSession,UUID original){
        return new GroupV1Message(UUID.randomUUID(),GroupV1Type.GROUP_READ_RECEIPT,
            activeSession,sender,local,group,3,now,
            Map.of("originalMessageId",original.toString(),"readEventId",
                UUID.randomUUID().toString(),"viewedAtUTC",now.toString()));
    }
    @Test void ackFromOnlyBDoesNotClaimDeliveredOrReadForC()throws Exception {
        try(var ledger=new GroupRecipientLedger()){
            ledger.register(message,group,3,local,Map.of(b,sessionB,c,sessionC));
            ledger.localWriteCompleted(message,b);
            ledger.localWriteCompleted(message,c);
            assertEquals(GroupRecipientLedger.Status.LOCAL_SENT,ledger.status(message,b));
            assertEquals(GroupRecipientLedger.Status.LOCAL_SENT,ledger.status(message,c));
            assertEquals(2,ledger.pendingCount());
            assertTrue(ledger.onVerifiedReceipt(ack(b,sessionB,group,3,message),
                b,sessionB,local));
            assertEquals(GroupRecipientLedger.Status.APP_ACKNOWLEDGED,
                ledger.status(message,b));
            assertEquals(GroupRecipientLedger.Status.LOCAL_SENT,ledger.status(message,c));
            assertEquals(1,ledger.pendingCount());
            assertFalse(ledger.onVerifiedReceipt(ack(b,sessionB,group,3,message),
                b,sessionB,local),"Authenticated duplicate ACK must be idempotent");
            assertEquals(1,ledger.pendingCount());
        }
    }
    @Test void wrongTlsPeerGroupSessionOriginalOrRevisionCannotElevateStatus()throws Exception{
        try(var ledger=new GroupRecipientLedger()){
            ledger.register(message,group,3,local,Map.of(b,sessionB,c,sessionC));
            var variants=List.of(ack(b,sessionB,UUID.randomUUID(),3,message),
                ack(b,sessionB,group,2,message),
                ack(b,sessionB,group,3,UUID.randomUUID()),
                ack(b,UUID.randomUUID(),group,3,message));
            for(GroupV1Message malicious:variants)
                assertThrows(ProtocolException.class,()->
                    ledger.onVerifiedReceipt(malicious,b,sessionB,local));
            assertThrows(ProtocolException.class,()->
                ledger.onVerifiedReceipt(ack(b,sessionB,group,3,message),
                    c,sessionC,local));
            assertEquals(GroupRecipientLedger.Status.SENDING,ledger.status(message,b));
            assertEquals(GroupRecipientLedger.Status.SENDING,ledger.status(message,c));
        }
    }
    @Test void readBeforeAckIsMonotonicAndNotAnAutomaticNetworkSend()throws Exception{
        try(var ledger=new GroupRecipientLedger()){
            ledger.register(message,group,3,local,Map.of(b,sessionB,c,sessionC));
            ledger.localWriteCompleted(message,b);
            assertTrue(ledger.onVerifiedReceipt(read(b,sessionB,message),b,sessionB,local));
            assertEquals(GroupRecipientLedger.Status.READ,ledger.status(message,b));
            assertFalse(ledger.onVerifiedReceipt(ack(b,sessionB,group,3,message),
                b,sessionB,local));
            assertEquals(GroupRecipientLedger.Status.READ,ledger.status(message,b));
            assertEquals(GroupRecipientLedger.Status.SENDING,ledger.status(message,c));
            assertEquals(1,ledger.pendingCount());
        }
    }
    @Test void ackThenReadForBMustNotReleasePendingSlotOfUnacknowledgedC()throws Exception{
        try(var ledger=new GroupRecipientLedger()){
            ledger.register(message,group,3,local,Map.of(b,sessionB,c,sessionC));
            assertTrue(ledger.onVerifiedReceipt(ack(b,sessionB,group,3,message),
                b,sessionB,local));
            assertEquals(1,ledger.pendingCount());
            assertTrue(ledger.onVerifiedReceipt(read(b,sessionB,message),b,sessionB,local));
            assertEquals(GroupRecipientLedger.Status.READ,ledger.status(message,b));
            assertEquals(1,ledger.pendingCount(),
                "READ after ACK must not consume the separate outstanding C slot");
            assertEquals(GroupRecipientLedger.Status.SENDING,ledger.status(message,c));
        }
    }
    @Test void atomicHundredTwentyEightPendingCapacityAndEraseOnClose()throws Exception{
        try(var ledger=new GroupRecipientLedger()){
            for(int i=0;i<128;i++)
                ledger.register(UUID.randomUUID(),group,3,local,
                    Map.of(b,UUID.randomUUID()));
            assertEquals(128,ledger.pendingCount());
            assertThrows(java.io.IOException.class,()->ledger.register(
                UUID.randomUUID(),group,3,local,Map.of(c,sessionC)));
            ledger.close();
            assertEquals(0,ledger.pendingCount());
            assertThrows(IllegalStateException.class,()->ledger.register(
                UUID.randomUUID(),group,3,local,Map.of(b,sessionB)));
        }
    }
}
