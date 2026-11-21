package vn.intrachat.protocol;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ChatV2BootstrapCodecTest {
    @Test void acceptRejectAndBoundariesStayDataFree() throws Exception {
        ChatV2BootstrapCodec codec=new ChatV2BootstrapCodec();
        byte[] nonce=new byte[32];java.util.Arrays.fill(nonce,(byte)0x47);
        UUID request=UUID.randomUUID(),receiver=UUID.randomUUID();
        ChatV2BootstrapCodec.Accept accept=new ChatV2BootstrapCodec.Accept(
                request,receiver,"Receiver",nonce);
        byte[] response=codec.encodeAccept(accept);
        ChatV2BootstrapCodec.Accept decoded=codec.decodeAccept(response);
        assertEquals(request,decoded.requestId());
        assertEquals(receiver,decoded.receiverId());
        assertArrayEquals(nonce,decoded.nonce());
        assertEquals("USER_REJECTED",assertThrows(ProtocolException.class,
                ()->codec.decodeAccept(codec.encodeReject(request))).code());
        assertEquals("FRAME_INVALID_LENGTH",assertThrows(ProtocolException.class,
                ()->codec.decodeAccept(new byte[ChatV2BootstrapCodec.LIMIT+1])).code());
        assertThrows(ProtocolException.class,()->codec.decodeAccept(
                (new String(response,StandardCharsets.UTF_8)+" {}")
                    .getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->codec.decodeAccept(
                new String(response,StandardCharsets.UTF_8).replace(
                  "\"prefaceVersion\":2","\"prefaceVersion\":1").getBytes(StandardCharsets.UTF_8)));
    }
    @Test void malformedDuplicateOrInjectedPlaintextPrefaceFailsClosed() throws Exception {
        ChatV2BootstrapCodec codec=new ChatV2BootstrapCodec();
        UUID request=UUID.randomUUID(),peer=UUID.randomUUID();
        byte[] normal=codec.encodeHello(new ChatV2BootstrapCodec.Hello(
                request,peer,"Alias",new byte[32]));
        String json=new String(normal,StandardCharsets.UTF_8);
        assertThrows(ProtocolException.class,()->codec.decodeHello(
                new byte[ChatV2BootstrapCodec.LIMIT+1]));
        assertThrows(ProtocolException.class,()->codec.decodeHello(
                new byte[]{(byte)0xc3,(byte)0x28}));
        assertThrows(ProtocolException.class,()->codec.decodeHello(
                json.replace("\"type\":\"CHAT_HELLO\"", "\"type\":\"CHAT_HELLO\",\"type\":\"CHAT_HELLO\"")
                    .getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->codec.decodeHello(
                json.replace("\"capabilities\"", "\"text\":\"private\",\"capabilities\"")
                    .getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->codec.decodeHello(
                json.replace("\"capabilities\":[\"CHAT_TLS13_V2\"]",
                        "\"capabilities\":[\"V1_PLAINTEXT\"]").getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->codec.decodeHello(
                json.replace("\"senderNonce\"", "\"senderNonceBackup\":\"bad\",\"senderNonce\"")
                    .getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->codec.decodeHello(
                json.replace("\"senderNonce\":\"", "\"senderNonce\":\"not-base64-")
                    .getBytes(StandardCharsets.UTF_8)));
    }
    @Test void allZeroUuidIsReservedOnlyForUnknownTargetNotDeviceOrRequest() throws Exception {
        ChatV2BootstrapCodec codec=new ChatV2BootstrapCodec();
        UUID zero=new UUID(0,0);
        UUID request=UUID.randomUUID(),sender=UUID.randomUUID();
        byte[] nonce=new byte[32];
        ChatV2BootstrapCodec.Hello legitimate=new ChatV2BootstrapCodec.Hello(request,sender,
                "Peer",nonce);
        byte[] normal=codec.encodeHello(legitimate);
        String encoded=new String(normal,StandardCharsets.UTF_8);
        byte[] invalidRequest=encoded.replace(request.toString(),zero.toString())
                .getBytes(StandardCharsets.UTF_8);
        byte[] invalidIdentity=encoded.replace(sender.toString(),zero.toString())
                .getBytes(StandardCharsets.UTF_8);
        assertNotNull(codec.decodeHello(normal));
        assertThrows(ProtocolException.class,()->codec.decodeHello(invalidRequest));
        assertThrows(ProtocolException.class,()->codec.decodeHello(invalidIdentity));
        assertThrows(IllegalArgumentException.class,()->new ChatV2BootstrapCodec.Hello(
                zero,sender,"Peer",nonce));
        assertThrows(IllegalArgumentException.class,()->new ChatV2BootstrapCodec.Accept(
                request,zero,"Peer",nonce));
    }
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
