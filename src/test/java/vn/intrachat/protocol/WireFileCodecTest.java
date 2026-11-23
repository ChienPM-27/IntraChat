package vn.intrachat.protocol;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WireFileCodecTest {
    private final WireCodec codec=new WireCodec();
    private WireMessage frame(WireMessage.Type type,Map<String,String> data){
        return new WireMessage(type,UUID.randomUUID(),UUID.randomUUID(),
            UUID.randomUUID(),UUID.randomUUID(),Instant.now(),data);
    }
    @Test void maximumChunkFitsUnderFrameCap()throws Exception{
        byte[] bytes=new byte[32*1024];new Random(4).nextBytes(bytes);
        var message=frame(WireMessage.Type.FILE_CHUNK,Map.of(
            "transferId",UUID.randomUUID().toString(),"offset","0",
            "data",Base64.getEncoder().encodeToString(bytes)));
        byte[] serialized=codec.encode(message);
        assertTrue(serialized.length<60_000,"Base64 must stay bounded under 1 MiB");
        assertEquals(message.payload(),codec.decode(serialized).payload());
    }
    @Test void oversizedFrameRejected() {
        var message=frame(WireMessage.Type.FILE_CHUNK,Map.of(
            "transferId",UUID.randomUUID().toString(),"data","A".repeat(WireCodec.MAX_FRAME)));
        assertThrows(java.io.IOException.class,()->codec.encode(message));
    }
    @Test void fileControlMessagesRemainDistinctFromGroupAndText()throws Exception{
        for(var type:List.of(WireMessage.Type.FILE_OFFER,WireMessage.Type.FILE_ACCEPT,
                WireMessage.Type.FILE_REJECT,WireMessage.Type.FILE_CHUNK,WireMessage.Type.FILE_DONE,
                WireMessage.Type.FILE_RESULT,WireMessage.Type.FILE_CANCEL,WireMessage.Type.FILE_ERROR)){
            var message=frame(type,Map.of("transferId",UUID.randomUUID().toString()));
            assertEquals(type,codec.decode(codec.encode(message)).type());
            assertNotEquals(WireMessage.Type.TEXT,type);
        }
    }
}
