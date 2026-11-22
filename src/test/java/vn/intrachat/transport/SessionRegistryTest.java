package vn.intrachat.transport;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SessionRegistryTest {
    @Test void pendingCapacityIsBoundedAndReleased() {
        SessionRegistry registry=new SessionRegistry();
        for(int i=0;i<4;i++)assertTrue(registry.tryReservePending());
        assertFalse(registry.tryReservePending());
        registry.releasePending();
        assertTrue(registry.tryReservePending());
        registry.close();
    }
    @Test void deterministicCrossConnectPicksSamePhysicalSocketOnBothPeers() {
        UUID a=UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID b=UUID.fromString("00000000-0000-4000-8000-000000000002");
        UUID incomingToA=UUID.randomUUID();
        UUID outgoingFromA=UUID.randomUUID();
        try(SessionRegistry onA=new SessionRegistry();SessionRegistry onB=new SessionRegistry()){
            assertEquals(SessionRegistry.Reservation.ACCEPTED,
                onA.reservePeer(b,incomingToA,SessionRegistry.Direction.INBOUND,a));
            assertEquals(SessionRegistry.Reservation.REPLACED,
                onA.reservePeer(b,outgoingFromA,SessionRegistry.Direction.OUTBOUND,a),
                "Lower UUID local device keeps outgoing socket");
            assertFalse(onA.releasePeer(b,incomingToA), "Closing old socket must not evict replacement");
            assertEquals(1,onA.activeCount());
            assertEquals(SessionRegistry.Reservation.ACCEPTED,
                onB.reservePeer(a,outgoingFromA,SessionRegistry.Direction.INBOUND,b));
            assertEquals(SessionRegistry.Reservation.REJECTED,
                onB.reservePeer(a,incomingToA,SessionRegistry.Direction.OUTBOUND,b),
                "Higher UUID local device keeps inbound socket");
            assertTrue(onB.releasePeer(a,outgoingFromA));
            assertEquals(0,onB.activeCount());
            assertTrue(onA.releasePeer(b,outgoingFromA));
        }
    }
    @Test void duplicateAndSelfConnectAreRejectedButSameDisplayNameDoesNotMatter(){
        UUID local=UUID.randomUUID(),peer=UUID.randomUUID(),session=UUID.randomUUID();
        try(SessionRegistry registry=new SessionRegistry()){
            assertEquals(SessionRegistry.Reservation.REJECTED,registry.reservePeer(local,
                UUID.randomUUID(),SessionRegistry.Direction.INBOUND,local));
            assertEquals(SessionRegistry.Reservation.REJECTED,registry.reservePeer(new UUID(0,0),
                UUID.randomUUID(),SessionRegistry.Direction.OUTBOUND,local));
            assertEquals(SessionRegistry.Reservation.ACCEPTED,registry.reservePeer(peer,session,
                SessionRegistry.Direction.OUTBOUND,local));
            assertEquals(SessionRegistry.Reservation.REJECTED,registry.reservePeer(peer,session,
                SessionRegistry.Direction.OUTBOUND,local));
            assertEquals(SessionRegistry.Reservation.REJECTED,registry.reservePeer(peer,
                UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff"),
                SessionRegistry.Direction.OUTBOUND,local));
        }
    }
    @Test void rejectsFifthActiveAndClosedRegistryNeverResurrects(){
        UUID local=UUID.randomUUID();
        try(SessionRegistry registry=new SessionRegistry()){
            UUID[] ids=new UUID[5],tokens=new UUID[5];
            for(int i=0;i<5;i++){ids[i]=UUID.randomUUID();tokens[i]=UUID.randomUUID();}
            for(int i=0;i<4;i++)assertEquals(SessionRegistry.Reservation.ACCEPTED,
                registry.reservePeer(ids[i],tokens[i],SessionRegistry.Direction.INBOUND,local));
            assertEquals(SessionRegistry.Reservation.REJECTED,
                registry.reservePeer(ids[4],tokens[4],SessionRegistry.Direction.INBOUND,local));
            assertTrue(registry.releasePeer(ids[0],tokens[0]));
            assertEquals(SessionRegistry.Reservation.ACCEPTED,
                registry.reservePeer(ids[4],tokens[4],SessionRegistry.Direction.INBOUND,local));
            registry.close();
            assertEquals(SessionRegistry.Reservation.REJECTED,
                registry.reservePeer(ids[0],UUID.randomUUID(),SessionRegistry.Direction.INBOUND,local));
            assertFalse(registry.tryReservePending());
            assertEquals(0,registry.activeCount());
        }
    }
    @Test void pendingReleaseCannotMintExtraPermits(){
        try(SessionRegistry registry=new SessionRegistry()){
            assertThrows(IllegalStateException.class,registry::releasePending);
            for(int i=0;i<NetworkConfig.MAX_PENDING_APPROVALS;i++)assertTrue(registry.tryReservePending());
            assertFalse(registry.tryReservePending());
            for(int i=0;i<NetworkConfig.MAX_PENDING_APPROVALS;i++)registry.releasePending();
            assertEquals(0,registry.pendingCount());
            assertThrows(IllegalStateException.class,registry::releasePending);
        }
    }
    @Test void sameDeclaredDeviceCannotRegisterTwiceAndCloseClears() {
        SessionRegistry registry=new SessionRegistry();
        UUID remote=UUID.randomUUID();
        assertTrue(registry.reservePeer(remote));
        assertFalse(registry.reservePeer(remote));
        registry.releasePeer(remote);
        assertTrue(registry.reservePeer(remote));
        registry.close();
    }
}
