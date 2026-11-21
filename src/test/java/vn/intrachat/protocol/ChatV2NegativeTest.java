package vn.intrachat.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.intrachat.application.*;
import vn.intrachat.domain.*;
import vn.intrachat.security.*;
import vn.intrachat.transport.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Bounded untrusted raw preface + protected V2 + v1 downgrade tests. No Internet. */
class ChatV2NegativeTest {
    @TempDir Path dir;
    private final ChatV2Codec v2=new ChatV2Codec();
    private final ChatV2BootstrapCodec hello=new ChatV2BootstrapCodec();

    @Test void untrustedFirstPrefaceRejectsOversizeInvalidUtf8DuplicateAndUnknownVersion() throws Exception {
        byte[] valid=hello.encodeHello(new ChatV2BootstrapCodec.Hello(
            UUID.randomUUID(),UUID.randomUUID(),"diagnostic-only",new byte[32]));
        String json=new String(valid,StandardCharsets.UTF_8);
        assertEquals("FRAME_INVALID_LENGTH",assertThrows(ProtocolException.class,
            ()->hello.decodeHello(new byte[16_385])).code());
        assertThrows(ProtocolException.class,()->hello.decodeHello(new byte[]{(byte)0xC3,(byte)0x28}));
        assertThrows(ProtocolException.class,()->hello.decodeHello(json.replace(
            "\"requestId\":","\"requestId\":\"fake\",\"requestId\":").getBytes(StandardCharsets.UTF_8)));
        assertEquals("VERSION_UNSUPPORTED",assertThrows(ProtocolException.class,
            ()->hello.decodeHello(json.replace("\"prefaceVersion\":2",
                "\"prefaceVersion\":1").getBytes(StandardCharsets.UTF_8))).code());
        assertThrows(ProtocolException.class,()->hello.decodeHello(json.replace(
            "\"type\":\"CHAT_HELLO\"","\"type\":\"CHAT_TEXT\"").getBytes(StandardCharsets.UTF_8)));
    }

    @Test void protectedOnlyFrameRejectsInvalidUtf8OversizeNestedAndForeignTypes() throws Exception {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        ChatV2Message msg=new ChatV2Message(UUID.randomUUID(),ChatV2Type.CHAT_TEXT,
            UUID.randomUUID(),a,b,UUID.randomUUID(),Instant.now(),Map.of("text","Xin chào"));
        byte[] valid=v2.encode(msg);
        String raw=new String(valid,StandardCharsets.UTF_8);
        assertEquals("FRAME_INVALID_LENGTH",assertThrows(ProtocolException.class,
            ()->v2.decode(new byte[65_537])).code());
        assertThrows(ProtocolException.class,()->v2.decode(new byte[]{(byte)0xFF}));
        assertThrows(ProtocolException.class,()->v2.decode(raw.replace(
            "\"type\":\"CHAT_TEXT\"","\"type\":\"GROUP_MESSAGE\"").getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->v2.decode(raw.replace(
            "\"type\":\"CHAT_TEXT\"","\"type\":\"CHAT_TEXT\",\"type\":\"CHAT_TEXT\"")
            .getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->v2.decode((raw+" []").getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->v2.decode(raw.replace(
            "\"protocolVersion\":2","\"protocolVersion\":999").getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->v2.decode(raw.replace(
            "\"payload\":{","\"payload\":{\"secret\":\"not allowed\",").getBytes(StandardCharsets.UTF_8)));
    }

    @Test void rawV1NeverSerializesOrParsesPrivateTextEvenWithValidMetadata() throws Exception {
        PeerIdentity alleged=new PeerIdentity(UUID.randomUUID(),"Unverified");
        NetworkEnvelope envelope=NetworkEnvelope.create(MessageType.CHAT_TEXT,
            alleged,UUID.randomUUID(),Map.of("text","test synthetic"));
        JsonEnvelopeCodec v1=new JsonEnvelopeCodec();
        assertEquals("UNSUPPORTED_TYPE_FOR_PHASE",assertThrows(ProtocolException.class,
            ()->v1.encode(envelope)).code());
        NetworkEnvelope ping=NetworkEnvelope.create(MessageType.DIAGNOSTIC_PING,
            alleged,UUID.randomUUID(),Map.of("probeId",UUID.randomUUID().toString(),"nonce","test"));
        String raw=new String(v1.encode(ping),StandardCharsets.UTF_8);
        assertEquals("UNSUPPORTED_TYPE_FOR_PHASE",assertThrows(ProtocolException.class,
            ()->v1.decode(raw.replace("DIAGNOSTIC_PING","CHAT_TEXT")
                .getBytes(StandardCharsets.UTF_8))).code());
    }

    @Test void verifiedTlsCannotSendMessageWithWrongSessionOrDestination() throws Exception {
        DeviceIdentity a=DeviceCertificateFactory.create(UUID.randomUUID());
        DeviceIdentity b=DeviceCertificateFactory.create(UUID.randomUUID());
        InboundApprovalPort consent=req->CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT);
        FingerprintApprovalPort approve=req->CompletableFuture.completedFuture(true);
        int port;try(ServerSocket s=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            port=s.getLocalPort();
        }
        BlockingQueue<TlsChatSession> incoming=new LinkedBlockingQueue<>();
        try(SecureChatCoordinator receiver=new SecureChatCoordinator(a,"A",
                new PinnedPeerStore(dir.resolve("pinsA")),consent,approve,incoming::add);
            SecureChatCoordinator sender=new SecureChatCoordinator(b,"B",
                new PinnedPeerStore(dir.resolve("pinsB")),consent,approve,s->{})) {
            receiver.startListening(new NetworkConfig("127.0.0.1",port));
            TlsChatSession outbound=sender.connect(new NetworkConfig("127.0.0.1",port)).get(12,TimeUnit.SECONDS);
            TlsChatSession accepted=incoming.poll(5,TimeUnit.SECONDS);
            assertNotNull(accepted);
            UUID conversation=P2PConversation.idFor(a.deviceId(),a.fingerprint(),
                b.deviceId(),b.fingerprint());
            ChatV2Message wrongSession=new ChatV2Message(UUID.randomUUID(),ChatV2Type.CHAT_TEXT,
                UUID.randomUUID(),b.deviceId(),a.deviceId(),conversation,
                Instant.now(),Map.of("text","must not send"));
            assertEquals("IDENTITY_MISMATCH",assertThrows(ProtocolException.class,
                ()->outbound.send(wrongSession)).code());
            ChatV2Message wrongDest=new ChatV2Message(UUID.randomUUID(),ChatV2Type.CHAT_TEXT,
                outbound.sessionId(),b.deviceId(),UUID.randomUUID(),conversation,
                Instant.now(),Map.of("text","must not send"));
            assertEquals("IDENTITY_MISMATCH",assertThrows(ProtocolException.class,
                ()->outbound.send(wrongDest)).code());
            outbound.close();
            accepted.close();
        }
    }
}
