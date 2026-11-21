package vn.intrachat.protocol;

import org.junit.jupiter.api.Test;
import vn.intrachat.domain.PeerIdentity;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class JsonEnvelopeCodecTest {
    private final JsonEnvelopeCodec codec=new JsonEnvelopeCodec();
    private NetworkEnvelope ping(){
        return NetworkEnvelope.create(MessageType.DIAGNOSTIC_PING,
            new PeerIdentity(UUID.randomUUID(),"Hoài 😊"),UUID.randomUUID(),
            Map.of("probeId",UUID.randomUUID().toString(),"nonce","Xin chào 👋"));
    }
    @Test void unicodeRoundTripAndMetadata() throws Exception {
        NetworkEnvelope original=ping();
        NetworkEnvelope restored=codec.decode(codec.encode(original));
        assertEquals(original.messageId(),restored.messageId());
        assertEquals(original.sender().displayName(),restored.sender().displayName());
        assertEquals(original.destination().id(),restored.destination().id());
        assertEquals(original.sentAt(),restored.sentAt());
        assertEquals("Xin chào 👋",restored.payload().get("nonce"));
    }
    @Test void malformedUtf8AndUnknownTypeRejected() throws Exception {
        assertThrows(ProtocolException.class,()->codec.decode(new byte[]{(byte)0xC3,(byte)0x28}));
        String valid=new String(codec.encode(ping()),StandardCharsets.UTF_8);
        assertThrows(ProtocolException.class,()->codec.decode(valid.replace("DIAGNOSTIC_PING","CHAT_TEXT").getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->codec.decode(valid.replace("DIAGNOSTIC_PING","UNKNOWN_KIND").getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->codec.decode((valid+"{}").getBytes(StandardCharsets.UTF_8)));
    }
    @Test void strictSchemaAndDuplicateKeysRejected() throws Exception {
        String valid=new String(codec.encode(ping()),StandardCharsets.UTF_8);
        assertThrows(ProtocolException.class,()->codec.decode(valid.replace("\"protocolVersion\":1","\"protocolVersion\":1,\"protocolVersion\":1").getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->codec.decode(valid.replace("\"protocolVersion\":1","\"protocolVersion\":2").getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->codec.decode(valid.replace("\"messageId\":","\"extra\":false,\"messageId\":").getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->codec.decode(valid.replace("\"sender\":","\"sender2\":{},\"sender\":").getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->codec.decode(valid.replace("\"messageId\":","\"wrong\":null,").getBytes(StandardCharsets.UTF_8)));
    }
    @Test void helloSentinelIsOnlyForHello() throws Exception {
        NetworkEnvelope good=NetworkEnvelope.create(MessageType.HELLO,new PeerIdentity(UUID.randomUUID(),"A"),NetworkEnvelope.UNKNOWN_PEER,
            Map.of("requestId",UUID.randomUUID().toString(),"supportedVersions",List.of(1),"clientVersion","0.1.0"));
        assertEquals(MessageType.HELLO,codec.decode(codec.encode(good)).type());
        assertThrows(ProtocolException.class,()->codec.encode(NetworkEnvelope.create(MessageType.DIAGNOSTIC_PING,new PeerIdentity(UUID.randomUUID(),"A"),NetworkEnvelope.UNKNOWN_PEER,Map.of("probeId",UUID.randomUUID().toString()))));
    }
}
