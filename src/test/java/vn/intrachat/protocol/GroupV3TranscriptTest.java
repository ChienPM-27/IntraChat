package vn.intrachat.protocol;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GroupV3TranscriptTest {
    static final UUID REQUEST=UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");
    static final UUID A=UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    static final UUID B=UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    static byte[] bytes(int start){byte[] x=new byte[32];for(int i=0;i<32;i++)x[i]=(byte)(start+i);return x;}
    @Test void fixedVectorMatchesContractAndBothDirections() {
        byte[] v=GroupV3Transcript.hash(REQUEST,A,bytes(0),bytes(64),B,bytes(32),bytes(96));
        assertEquals("3B4952931FF836E84EA4F21BF167BFC4DB78B8077BC114F931D3632C51388959",
            HexFormat.of().withUpperCase().formatHex(v));
        assertArrayEquals(v,GroupV3Transcript.hash(REQUEST,B,bytes(32),bytes(96),A,bytes(0),bytes(64)));
        assertEquals("3B49-5293-1FF8-36E8",GroupV3Transcript.sas(v));
    }
    @Test void cannotReuseV2DigestOrAlterNonceKeyOrRequest() {
        byte[] original=GroupV3Transcript.hash(REQUEST,A,bytes(0),bytes(64),B,bytes(32),bytes(96));
        assertFalse(Arrays.equals(original,GroupV3Transcript.hash(UUID.randomUUID(),A,bytes(0),bytes(64),B,bytes(32),bytes(96))));
        assertFalse(Arrays.equals(original,GroupV3Transcript.hash(REQUEST,A,bytes(0),bytes(65),B,bytes(32),bytes(96))));
        assertFalse(Arrays.equals(original,GroupV3Transcript.hash(REQUEST,A,bytes(0),bytes(64),B,bytes(33),bytes(96))));
        assertThrows(IllegalArgumentException.class,()->GroupV3Transcript.hash(REQUEST,A,bytes(0),bytes(64),A,bytes(32),bytes(96)));
        assertThrows(IllegalArgumentException.class,()->GroupV3Transcript.hash(REQUEST,A,new byte[31],bytes(64),B,bytes(32),bytes(96)));
    }
}
