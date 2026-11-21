package vn.intrachat.protocol;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ChatV2CodecTest {
    @Test void reservedUuidCannotBeUsedAsProtectedIdentityOrConversation() throws Exception {
        UUID zero=new UUID(0,0);
        ChatV2Message normal=text("okay");
        assertThrows(ProtocolException.class,()->codec.encode(new ChatV2Message(
                normal.messageId(),normal.type(),normal.sessionId(),zero,normal.destinationId(),
                normal.conversationId(),normal.sentAt(),normal.payload())));
        assertThrows(ProtocolException.class,()->codec.encode(new ChatV2Message(
                normal.messageId(),normal.type(),normal.sessionId(),normal.senderId(),zero,
                normal.conversationId(),normal.sentAt(),normal.payload())));
        assertThrows(ProtocolException.class,()->codec.encode(new ChatV2Message(
                normal.messageId(),normal.type(),normal.sessionId(),normal.senderId(),normal.destinationId(),
                zero,normal.sentAt(),normal.payload())));
        assertThrows(ProtocolException.class,()->codec.encode(new ChatV2Message(
                normal.messageId(),normal.type(),zero,normal.senderId(),normal.destinationId(),
                normal.conversationId(),normal.sentAt(),normal.payload())));
    }
    @Test void ackAndReadHaveDistinctStrictAuthenticatedWireShapes() throws Exception {
        ChatV2Message base=text("ok");
        ChatV2Message ack=new ChatV2Message(UUID.randomUUID(),ChatV2Type.DELIVERY_ACK,
                base.sessionId(),base.senderId(),base.destinationId(),base.conversationId(),
                Instant.now(),Map.of("originalMessageId",base.messageId().toString(),
                                     "acceptedAtUTC",Instant.now().toString(),"result","ACCEPTED"));
        ChatV2Message read=new ChatV2Message(UUID.randomUUID(),ChatV2Type.READ_RECEIPT,
                base.sessionId(),base.senderId(),base.destinationId(),base.conversationId(),
                Instant.now(),Map.of("originalMessageId",base.messageId().toString(),
                                     "readEventId",UUID.randomUUID().toString(),
                                     "viewedAtUTC",Instant.now().toString()));
        assertEquals(ack,codec.decode(codec.encode(ack)));
        assertEquals(read,codec.decode(codec.encode(read)));
        assertNotEquals(read.type(),ack.type());
        assertThrows(ProtocolException.class,()->codec.encode(new ChatV2Message(
                ack.messageId(),ack.type(),ack.sessionId(),ack.senderId(),ack.destinationId(),
                ack.conversationId(),ack.sentAt(),Map.of("originalMessageId",base.messageId().toString()))));
        assertThrows(ProtocolException.class,()->codec.encode(new ChatV2Message(
                read.messageId(),read.type(),read.sessionId(),read.senderId(),read.destinationId(),
                read.conversationId(),read.sentAt(),Map.of("originalMessageId",base.messageId().toString(),
                    "readEventId","invalid","viewedAtUTC",Instant.now().toString()))));
    }
    @Test void v2FramesRejectOversizeMalformedAndUnsupportedType() throws Exception {
        assertEquals("FRAME_INVALID_LENGTH",assertThrows(ProtocolException.class,
                ()->codec.decode(new byte[ChatV2Codec.LIMIT+1])).code());
        byte[] original=codec.encode(text("valid"));
        String raw=new String(original,StandardCharsets.UTF_8);
        assertThrows(ProtocolException.class,()->codec.decode(new byte[]{(byte)0xc3,0x28}));
        assertThrows(ProtocolException.class,()->codec.decode((raw+"false").getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->codec.decode(raw.replace(
                "\"type\":\"CHAT_TEXT\"","\"type\":\"FILE_TRANSFER\"").getBytes(StandardCharsets.UTF_8)));
    }
    private final ChatV2Codec codec=new ChatV2Codec();
    private ChatV2Message text(String body) {
        return new ChatV2Message(UUID.randomUUID(),ChatV2Type.CHAT_TEXT,UUID.randomUUID(),
            UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),Instant.now(),Map.of("text",body));
    }
    @Test void realTextIncludingVietnameseAndEmojiRoundTrips() throws Exception {
        ChatV2Message expected=text("Xin chào 🚀👩🏽‍💻");
        ChatV2Message decoded=codec.decode(codec.encode(expected));
        assertEquals(expected,decoded);
    }
    @Test void invalidVersionDuplicateKeysOrUnrelatedTypesAreRejected() throws Exception {
        byte[] normal=codec.encode(text("ok"));
        String plain=new String(normal,StandardCharsets.UTF_8);
        assertThrows(ProtocolException.class,()->codec.decode(plain.replace("\"protocolVersion\":2", "\"protocolVersion\":1").getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->codec.decode(plain.replace("\"protocolVersion\":2", "\"protocolVersion\":2,\"protocolVersion\":2").getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->codec.decode(plain.replace("CHAT_TEXT","GROUP_MESSAGE").getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->codec.decode((plain+" {}").getBytes(StandardCharsets.UTF_8)));
    }
    @Test void textMustRemainValidAndUnknownFieldsForbidden() throws Exception {
        assertThrows(ProtocolException.class,()->codec.encode(text(" ")));
        ChatV2Message original=text("hi");
        assertThrows(ProtocolException.class,()->codec.encode(new ChatV2Message(
            original.messageId(),original.type(),original.sessionId(),original.senderId(),original.destinationId(),
            original.conversationId(),original.sentAt(),Map.of("text","hi","privateKey","secret"))));
        byte[] data=codec.encode(original);
        byte[] corrupted=data.clone();corrupted[0]=(byte)0xFF;
        assertThrows(ProtocolException.class,()->codec.decode(corrupted));
    }
}
