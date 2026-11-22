package vn.intrachat.protocol;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GroupV1CodecTest {
    private static UUID id(int n){return UUID.fromString("00000000-0000-4000-8000-"+String.format("%012x",n));}
    private final GroupV1Codec codec=new GroupV1Codec();
    private GroupV1Message text(String content) {
        return new GroupV1Message(id(1),GroupV1Type.GROUP_TEXT,id(2),id(3),id(4),id(5),1,
            Instant.parse("2026-11-22T12:00:00Z"),Map.of(
                "text",content,"grantCanonicalBase64",Base64.getEncoder().encodeToString(new byte[]{1,2,3}),
                "ownerSignatureBase64",Base64.getEncoder().encodeToString(new byte[70])));
    }
    @Test void groupTextUnicodeRoundTripsWithExplicitV3Profile()throws Exception {
        var m=text("Xin chào nhóm 😊");
        byte[] raw=codec.encode(m);
        String json=new String(raw,StandardCharsets.UTF_8);
        assertTrue(json.contains("\"feature\":\"GROUP_V1\""));
        assertTrue(json.contains("\"protocolVersion\":3"));
        assertEquals(m,codec.decode(raw));
        assertEquals("Xin chào nhóm 😊",codec.decode(raw).payload().get("text"));
    }
    @Test void rejectsVersionUnknownTypesDuplicateKeysAndMalformedUTF8() throws Exception {
        String raw=new String(codec.encode(text("okay")),StandardCharsets.UTF_8);
        assertEquals("VERSION_UNSUPPORTED",assertThrows(ProtocolException.class,
            ()->codec.decode(raw.replace("\"protocolVersion\":3", "\"protocolVersion\":2").getBytes(StandardCharsets.UTF_8))).code());
        assertThrows(ProtocolException.class,()->codec.decode(raw.replace("GROUP_TEXT","CHAT_TEXT").getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->codec.decode(raw.replace("\"type\":", "\"type\":\"GROUP_TEXT\",\"type\":").getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->codec.decode((raw+" {}").getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->codec.decode(new byte[]{(byte)0xC3,0x28}));
        assertEquals("FRAME_INVALID_LENGTH",assertThrows(ProtocolException.class,
            ()->codec.decode(new byte[65_537])).code());
    }
    @Test void rejectsWrongPeerAndUntrustedSessionMetadata() throws Exception {
        var m=text("hi");
        assertDoesNotThrow(()->codec.requireSessionBinding(m,id(2),id(3),id(4)));
        assertThrows(ProtocolException.class,()->codec.requireSessionBinding(m,id(99),id(3),id(4)));
        assertThrows(ProtocolException.class,()->codec.requireSessionBinding(m,id(2),id(88),id(4)));
        assertThrows(ProtocolException.class,()->codec.requireSessionBinding(m,id(2),id(3),id(77)));
        assertThrows(ProtocolException.class,()->codec.encode(new GroupV1Message(id(1),GroupV1Type.GROUP_TEXT,
            id(2),id(3),id(3),id(5),1,Instant.now(),m.payload())));
    }
    @Test void rejectsOversizedTextAndUnexpectedPayloadKeys() throws Exception {
        assertThrows(ProtocolException.class,()->codec.encode(text("🧪".repeat(8001))));
        var t=text("hi");
        assertThrows(ProtocolException.class,()->codec.encode(new GroupV1Message(
            t.messageId(),t.type(),t.sessionId(),t.senderId(),t.destinationId(),t.groupId(),
            t.membershipRevision(),t.sentAt(),Map.of("text","hi","ownerSignatureBase64","YWJj",
                "grantCanonicalBase64","AQID","privateKey","never"))));
        String json=new String(codec.encode(t),StandardCharsets.UTF_8);
        assertThrows(ProtocolException.class,()->codec.decode(json.replace(
            "\"feature\":\"GROUP_V1\"","\"feature\":\"GROUP_V1\",\"unknown\":1").getBytes(StandardCharsets.UTF_8)));
    }
    @Test void receiptsAreDistinctAndCannotSpoofAppAck() throws Exception {
        var m=text("hi");
        var ack=new GroupV1Message(id(10),GroupV1Type.GROUP_DELIVERY_ACK,id(2),id(3),id(4),id(5),1,
            Instant.now(),Map.of("originalMessageId",id(1).toString(),"acceptedAtUTC","2026-11-22T12:00:00Z","result","ACCEPTED"));
        var read=new GroupV1Message(id(11),GroupV1Type.GROUP_READ_RECEIPT,id(2),id(3),id(4),id(5),1,
            Instant.now(),Map.of("originalMessageId",id(1).toString(),"readEventId",id(12).toString(),"viewedAtUTC","2026-11-22T12:00:00Z"));
        assertEquals(ack,codec.decode(codec.encode(ack)));
        assertEquals(read,codec.decode(codec.encode(read)));
        assertThrows(ProtocolException.class,()->codec.encode(new GroupV1Message(
            ack.messageId(),ack.type(),ack.sessionId(),ack.senderId(),ack.destinationId(),ack.groupId(),1,
            ack.sentAt(),Map.of("originalMessageId",id(1).toString(),"result","ACCEPTED"))));
    }
}
