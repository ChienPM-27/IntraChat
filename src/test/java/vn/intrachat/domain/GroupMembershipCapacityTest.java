package vn.intrachat.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GroupMembershipCapacityTest {
    static UUID uuid(int n){return UUID.fromString("00000000-0000-4000-8000-"+String.format("%012x",n));}
    static byte[] fingerprint(int n){byte[] p=new byte[32];Arrays.fill(p,(byte)n);return p;}
    static GroupIdentity id(){return new GroupIdentity(uuid(100),"Nhóm dự án",uuid(1),fingerprint(1));}
    static GroupMember member(int n){return new GroupMember(uuid(n),fingerprint(n),n==1?GroupMember.Role.OWNER:GroupMember.Role.MEMBER);}
    static GroupRoster roster(GroupMember... members){
        return new GroupRoster(id(),1L,new byte[32],List.of(members),uuid(500),Instant.parse("2026-11-22T12:00:00Z"));
    }
    @Test void acceptsTwoThroughFiveIncludingOwnerAndSortsDeterministically() {
        GroupRoster two=roster(member(2),member(1));
        assertEquals(2,two.members().size());
        assertEquals(uuid(1),two.members().getFirst().deviceId());
        GroupRoster five=roster(member(5),member(4),member(3),member(2),member(1));
        assertEquals(5,five.members().size());
        assertEquals(uuid(5),five.members().getLast().deviceId());
    }
    @Test void rejectsSixthDuplicateOrMissingOwner() {
        assertThrows(IllegalArgumentException.class,()->roster(member(1),member(2),member(3),member(4),member(5),member(6)));
        assertThrows(IllegalArgumentException.class,()->roster(member(1),member(1)));
        assertThrows(IllegalArgumentException.class,()->roster(member(2),member(3)));
        assertThrows(IllegalArgumentException.class,()->roster(member(1)));
        assertThrows(IllegalArgumentException.class,()->roster(member(1),new GroupMember(uuid(2),fingerprint(1),GroupMember.Role.MEMBER)));
        assertThrows(IllegalArgumentException.class,()->roster(member(1),new GroupMember(uuid(2),fingerprint(2),GroupMember.Role.OWNER)));
    }
    @Test void revisionAndPreviousHashAreValidatedAndCloned() {
        assertThrows(IllegalArgumentException.class,()->new GroupRoster(id(),0,new byte[32],List.of(member(1),member(2)),uuid(500),Instant.now()));
        assertThrows(IllegalArgumentException.class,()->new GroupRoster(id(),1,new byte[31],List.of(member(1),member(2)),uuid(500),Instant.now()));
        byte[] prev=new byte[32];GroupRoster valid=new GroupRoster(id(),1,prev,List.of(member(1),member(2)),uuid(500),Instant.now());
        prev[0]=100;
        assertEquals(0,valid.previousRosterHash()[0]);
        byte[] leaked=valid.previousRosterHash();leaked[1]=4;
        assertEquals(0,valid.previousRosterHash()[1]);
        assertThrows(UnsupportedOperationException.class,()->valid.members().add(member(3)));
        assertThrows(IllegalArgumentException.class,()->new GroupRoster(id(),2,new byte[32],List.of(member(1),member(2)),new UUID(0,0),Instant.now()));
    }
}
