package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import vn.intrachat.domain.*;
import vn.intrachat.security.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GroupOwnerGrantServiceTest {
    static UUID ownerId=UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    static UUID b=UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    static UUID c=UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    static UUID outsider=UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");
    static byte[] pin(int n){byte[] bytes=new byte[32];Arrays.fill(bytes,(byte)n);return bytes;}
    static GroupMetadata roster(DeviceIdentity owner,UUID gid,int rev,byte[] prev,boolean includeB)throws Exception {
        var group=new GroupIdentity(gid,"Nhóm bảo mật",owner.deviceId(),owner.fingerprint());
        List<GroupMember> members=new ArrayList<>();
        members.add(new GroupMember(ownerId,owner.fingerprint(),GroupMember.Role.OWNER));
        if(includeB)members.add(new GroupMember(b,pin(10),GroupMember.Role.MEMBER));
        members.add(new GroupMember(c,pin(20),GroupMember.Role.MEMBER));
        var roster=new GroupRoster(group,rev,prev,members,UUID.randomUUID(),Instant.parse("2026-11-22T12:00:00Z"));
        return new GroupMetadata(roster,GroupOwnerSignature.signRoster(roster,
            owner.keyPair().getPrivate(),owner.keyPair().getPublic()),false);
    }
    @Test void grantsExactRecipientsAndRejectsNonmembersWrongSets()throws Exception {
        DeviceIdentity owner=DeviceCertificateFactory.create(ownerId);
        UUID group=UUID.randomUUID();
        var initial=roster(owner,group,1,new byte[32],true);
        try(var authority=new GroupOwnerGrantService(owner)){
            authority.activate(initial);
            Instant now=Instant.parse("2026-11-22T12:00:00Z");
            var grant=authority.issue(ownerId,UUID.randomUUID(),List.of(b,c),now);
            assertEquals(Set.of(b,c),new HashSet<>(grant.grant().recipientIds()));
            assertEquals(ownerId,grant.grant().senderId());
            assertEquals(1,grant.grant().revision());
            assertTrue(GroupOwnerSignature.verifyGrant(grant.grant(),grant.signature(),owner.keyPair().getPublic()));
            assertEquals(10,java.time.Duration.between(grant.grant().issuedAt(),grant.grant().expiresAt()).toSeconds());
            assertThrows(Exception.class,()->authority.issue(outsider,UUID.randomUUID(),List.of(b,c),now));
            assertThrows(Exception.class,()->authority.issue(ownerId,UUID.randomUUID(),List.of(b),now));
            assertThrows(Exception.class,()->authority.issue(ownerId,UUID.randomUUID(),List.of(b,outsider),now));
            assertThrows(Exception.class,()->authority.issue(ownerId,UUID.randomUUID(),List.of(b,b,c),now));
        }
    }
    @Test void removalChangesGrantSetMonotonicallyAndOfflinePauses()throws Exception {
        DeviceIdentity owner=DeviceCertificateFactory.create(ownerId);
        UUID group=UUID.randomUUID();
        var first=roster(owner,group,1,new byte[32],true);
        try(var authority=new GroupOwnerGrantService(owner)){
            authority.activate(first);
            Instant when=Instant.now();
            var before=authority.issue(c,UUID.randomUUID(),List.of(b,ownerId),when);
            assertTrue(before.grant().recipientIds().contains(b));
            var prev=GroupCanonicalBytes.sha256(GroupCanonicalBytes.roster(first.roster()));
            var second=roster(owner,group,2,prev,false);
            authority.activate(second);
            var after=authority.issue(c,UUID.randomUUID(),List.of(ownerId),when);
            assertEquals(List.of(ownerId),after.grant().recipientIds());
            assertThrows(Exception.class,()->authority.issue(c,UUID.randomUUID(),List.of(ownerId,b),when));
            assertThrows(Exception.class,()->authority.issue(b,UUID.randomUUID(),List.of(ownerId,c),when));
            assertThrows(Exception.class,()->authority.activate(first)); // stale signed rollback
            authority.close();
            assertThrows(Exception.class,()->authority.issue(c,UUID.randomUUID(),List.of(ownerId),when));
        }
    }
    @Test void cannotActivateUnsignedOrWrongOwnerMetadata()throws Exception {
        DeviceIdentity owner=DeviceCertificateFactory.create(ownerId);
        DeviceIdentity fake=DeviceCertificateFactory.create(UUID.randomUUID());
        var candidate=roster(owner,UUID.randomUUID(),1,new byte[32],true);
        try(var authority=new GroupOwnerGrantService(fake)){
            assertThrows(Exception.class,()->authority.activate(candidate));
        }
        var bogus=new GroupMetadata(candidate.roster(),new byte[70],true);
        try(var authority=new GroupOwnerGrantService(owner)){
            assertThrows(Exception.class,()->authority.activate(bogus));
        }
    }
}
