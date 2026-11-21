package vn.intrachat.protocol;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ChatV2CodecTest {
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
