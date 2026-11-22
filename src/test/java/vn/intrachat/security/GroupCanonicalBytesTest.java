package vn.intrachat.security;

import org.junit.jupiter.api.Test;
import vn.intrachat.domain.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GroupCanonicalBytesTest {
    static final UUID GROUP=UUID.fromString("11111111-1111-4111-8111-111111111111");
    static final UUID OWNER=UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    static final UUID PEER=UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    static final UUID EVENT=UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    static final UUID MSG=UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");
    static final Instant INSTANT=Instant.parse("2026-11-22T12:00:00Z");
    static byte[] fingerprint(int base){byte[] bytes=new byte[32];for(int i=0;i<32;i++)bytes[i]=(byte)(base+i);return bytes;}
    static GroupRoster roster(){
        GroupIdentity group=new GroupIdentity(GROUP,"Do an",OWNER,fingerprint(0));
        return new GroupRoster(group,1L,new byte[32],
            List.of(new GroupMember(PEER,fingerprint(32),GroupMember.Role.MEMBER),
                    new GroupMember(OWNER,fingerprint(0),GroupMember.Role.OWNER)),EVENT,INSTANT);
    }
    static GroupGrant grant(){
        byte[] nonce=new byte[16];for(int i=0;i<16;i++)nonce[i]=(byte)(i+1);
        return new GroupGrant(GROUP,MSG,OWNER,fingerprint(0),1L,
            GroupCanonicalBytes.sha256(GroupCanonicalBytes.roster(roster())),
            OWNER,List.of(PEER),INSTANT,INSTANT.plusSeconds(10),nonce);
    }
    @Test void rosterCanonicalVectorMatchesFrozenContract(){
        byte[] bytes=GroupCanonicalBytes.roster(roster());
        assertEquals(260,bytes.length);
        assertEquals("FE5AA540B53CE02A64457F0A95BEAEE6274828EC743ACD3598F310C085DF915D",
            HexFormat.of().withUpperCase().formatHex(GroupCanonicalBytes.sha256(bytes)));
        assertArrayEquals(bytes,GroupCanonicalBytes.roster(roster()));
    }
    @Test void grantCanonicalVectorMatchesFrozenContract(){
        byte[] bytes=GroupCanonicalBytes.grant(grant());
        assertEquals(210,bytes.length);
        assertEquals("0C31739C8BD77B2E44B21BF5054FB343116C48449EAA1BAD3D533B78323BA869",
            HexFormat.of().withUpperCase().formatHex(GroupCanonicalBytes.sha256(bytes)));
    }
    @Test void domainSeparationAndTamperingChangeHashes(){
        assertNotEquals(HexFormat.of().formatHex(GroupCanonicalBytes.sha256(GroupCanonicalBytes.roster(roster()))),
            HexFormat.of().formatHex(GroupCanonicalBytes.sha256(GroupCanonicalBytes.grant(grant()))));
        GroupRoster edited=new GroupRoster(new GroupIdentity(GROUP,"Do an!",OWNER,fingerprint(0)),1L,
            new byte[32],roster().members(),EVENT,INSTANT);
        assertFalse(Arrays.equals(GroupCanonicalBytes.roster(roster()),GroupCanonicalBytes.roster(edited)));
        assertThrows(IllegalArgumentException.class,()->new GroupGrant(GROUP,MSG,OWNER,fingerprint(0),1L,
            new byte[32],OWNER,List.of(PEER),INSTANT,INSTANT.plusSeconds(11),new byte[16]));
    }
}
