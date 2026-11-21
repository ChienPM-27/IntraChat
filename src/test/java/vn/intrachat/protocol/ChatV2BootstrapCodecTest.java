package vn.intrachat.protocol;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ChatV2BootstrapCodecTest {
    @Test void helloIsUntrustedMetadataOnlyNotUserChat() throws Exception {
        ChatV2BootstrapCodec codec=new ChatV2BootstrapCodec();
        byte[] nonce=new byte[32];new java.security.SecureRandom().nextBytes(nonce);
        ChatV2BootstrapCodec.Hello hello=new ChatV2BootstrapCodec.Hello(
            UUID.randomUUID(),UUID.randomUUID(),"Sender",nonce);
        assertEquals(hello.requestId(),codec.decodeHello(codec.encodeHello(hello)).requestId());
        assertArrayEquals(nonce,codec.decodeHello(codec.encodeHello(hello)).nonce());
    }
    @Test void neverAcceptsChatOrUnknownVersionsInPlaintextPreface() throws Exception {
        ChatV2BootstrapCodec codec=new ChatV2BootstrapCodec();
        byte[] nonce=new byte[32];new java.security.SecureRandom().nextBytes(nonce);
        byte[] bytes=codec.encodeHello(new ChatV2BootstrapCodec.Hello(UUID.randomUUID(),UUID.randomUUID(),"Peer",nonce));
        String raw=new String(bytes,StandardCharsets.UTF_8);
        assertThrows(ProtocolException.class,()->codec.decodeHello(raw.replace("CHAT_HELLO","CHAT_TEXT").getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->codec.decodeHello(raw.replace("\"prefaceVersion\":2","\"prefaceVersion\":1").getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->codec.decodeHello((raw+" {}").getBytes(StandardCharsets.UTF_8)));
    }
}
