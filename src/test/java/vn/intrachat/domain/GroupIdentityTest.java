package vn.intrachat.domain;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GroupIdentityTest {
    private static final UUID OWNER=UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID GROUP=UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ZERO=new UUID(0,0);
    private static byte[] fp(int start){byte[] b=new byte[32];for(int i=0;i<32;i++)b[i]=(byte)(start+i);return b;}

    @Test void ownerStableIdAndUnicodeTitleAreIndependentOfAlias() {
        GroupIdentity group=new GroupIdentity(GROUP,"Đồ án 😊",OWNER,fp(0));
        assertEquals(OWNER,group.ownerDeviceId());
        assertEquals(GROUP,group.groupId());
        assertEquals("Đồ án 😊",group.title());
        assertNotEquals(OWNER,group.groupId());
        assertThrows(IllegalArgumentException.class,()->new GroupIdentity(ZERO,"Tên",OWNER,fp(0)));
        assertThrows(IllegalArgumentException.class,()->new GroupIdentity(GROUP,"Tên",ZERO,fp(0)));
    }
    @Test void titleRejectsEmptyControlsAndOversize() {
        assertThrows(IllegalArgumentException.class,()->new GroupIdentity(GROUP,"  ",OWNER,fp(0)));
        assertThrows(IllegalArgumentException.class,()->new GroupIdentity(GROUP,"a\n",OWNER,fp(0)));
        assertThrows(IllegalArgumentException.class,()->new GroupIdentity(GROUP,"a".repeat(61),OWNER,fp(0)));
        assertThrows(IllegalArgumentException.class,()->new GroupIdentity(GROUP,"🙂".repeat(61),OWNER,fp(0)));
        assertThrows(IllegalArgumentException.class,()->new GroupIdentity(GROUP,"a\uD800",OWNER,fp(0)));
        String decomposed="o\u0300";
        GroupIdentity normalized=new GroupIdentity(GROUP,decomposed,OWNER,fp(0));
        assertEquals("ò",normalized.title());
        assertTrue(normalized.title().getBytes(StandardCharsets.UTF_8).length<=240);
    }
    @Test void neverExposeMutableFingerprintArray() {
        byte[] input=fp(0);
        GroupIdentity identity=new GroupIdentity(GROUP,"Nhóm",OWNER,input);
        input[0]=127;
        assertEquals(0,identity.ownerSpkiSha256()[0]);
        byte[] leaked=identity.ownerSpkiSha256();leaked[1]=99;
        assertEquals(1,identity.ownerSpkiSha256()[1]);
        assertThrows(IllegalArgumentException.class,()->new GroupIdentity(GROUP,"Nhóm",OWNER,new byte[31]));
    }
}