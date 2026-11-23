package vn.intrachat.protocol;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WireCodecP1Test {
    @Test void roundTripUnicodeAndFraming()throws Exception {
        WireCodec codec=new WireCodec();
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        var msg=new WireMessage(WireMessage.Type.TEXT,UUID.randomUUID(),UUID.randomUUID(),
            a,b,Instant.now(),Map.of("text","Xin chào 🧑‍💻 🇻🇳 👨‍👩‍👧‍👦"));
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        codec.write(out,msg);
        assertEquals(msg,codec.read(new ByteArrayInputStream(out.toByteArray())));
        assertEquals(msg,codec.decode(codec.encode(msg)));
    }
    @Test void rejectsUnknownAndOversizedAndMalformed()throws Exception{
        WireCodec codec=new WireCodec();
        assertThrows(IOException.class,()->codec.decode("{\"type\":\"UNKNOWN\"}".getBytes()));
        assertThrows(IOException.class,()->codec.decode(new byte[]{(byte)0xC3,(byte)0x28}));
        assertThrows(IOException.class,()->codec.decode(new byte[WireCodec.MAX_FRAME+1]));
        var oversized=new WireMessage(WireMessage.Type.TEXT,UUID.randomUUID(),UUID.randomUUID(),
            UUID.randomUUID(),UUID.randomUUID(),Instant.now(),Map.of("text","x".repeat(WireCodec.MAX_FRAME)));
        assertThrows(IOException.class,()->codec.encode(oversized));
    }
}
