package vn.intrachat.protocol;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WireGroupCodecTest {
    @Test void allGroupControlTypesRoundTrip() throws Exception {
        WireCodec codec=new WireCodec();
        for(WireMessage.Type type:List.of(WireMessage.Type.GROUP_INVITE,WireMessage.Type.GROUP_ACCEPT,
            WireMessage.Type.GROUP_REJECT,WireMessage.Type.GROUP_UPDATE,WireMessage.Type.GROUP_REMOVE,
            WireMessage.Type.GROUP_ACK,WireMessage.Type.GROUP_LEAVE,WireMessage.Type.GROUP_TEXT)){
            WireMessage original=new WireMessage(type,UUID.randomUUID(),UUID.randomUUID(),
                UUID.randomUUID(),UUID.randomUUID(),Instant.now(),Map.of("text","Chào nhóm 👋"));
            assertEquals(original,codec.decode(codec.encode(original)));
        }
    }
    @Test void malformedUuidRejected() throws Exception {
        WireCodec codec=new WireCodec();
        WireMessage original=new WireMessage(WireMessage.Type.GROUP_TEXT,UUID.randomUUID(),UUID.randomUUID(),
            UUID.randomUUID(),UUID.randomUUID(),Instant.now(),Map.of("text","Emoji 😀"));
        String corrupt=new String(codec.encode(original),StandardCharsets.UTF_8)
            .replace(original.senderId().toString(),"not-a-uuid");
        assertThrows(java.io.IOException.class,()->codec.decode(corrupt.getBytes(StandardCharsets.UTF_8)));
    }
}
