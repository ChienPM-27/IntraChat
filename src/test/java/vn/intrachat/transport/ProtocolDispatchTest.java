package vn.intrachat.transport;

import org.junit.jupiter.api.Test;
import vn.intrachat.domain.PeerIdentity;
import vn.intrachat.protocol.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Strict first-frame classification: no implicit TLS downgrade or private payload on M1. */
class ProtocolDispatchTest {
    private final ProtocolDispatch router=new ProtocolDispatch();
    private static final UUID PEER=UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static byte[] v1(){
        try{
            return new JsonEnvelopeCodec().encode(NetworkEnvelope.create(MessageType.HELLO,
                new PeerIdentity(PEER,"Peer"),NetworkEnvelope.UNKNOWN_PEER,
                Map.of("requestId",UUID.randomUUID().toString(),
                    "supportedVersions",List.of(1),"clientVersion","0.1-test")));
        }catch(Exception failure){throw new AssertionError(failure);}
    }
    private static byte[] v2(){
        try{return new ChatV2BootstrapCodec().encodeHello(
            new ChatV2BootstrapCodec.Hello(UUID.randomUUID(),PEER,"Peer",new byte[32]));}
        catch(Exception failure){throw new AssertionError(failure);}
    }
    private static byte[] v3(){
        try{return new GroupV3BootstrapCodec().encodeHello(
            new GroupV3BootstrapCodec.Hello(UUID.randomUUID(),PEER,"Peer",new byte[32]));}
        catch(Exception failure){throw new AssertionError(failure);}
    }
    @Test void strictlyRoutesOnlyValidV1DiagnosticHelloAndV2SecureHello()throws Exception{
        assertEquals(ProtocolDispatch.Profile.M1_DIAGNOSTICS,router.inspect(v1()));
        assertEquals(ProtocolDispatch.Profile.M2_TLS13,router.inspect(v2()));
        assertEquals(ProtocolDispatch.Profile.M3_GROUP_UNSUPPORTED,router.inspect(v3()));
    }
    @Test void rejectsUnknownVersionAndExplicitDowngradeProbe()throws Exception{
        String one=new String(v1(),StandardCharsets.UTF_8);
        String two=new String(v2(),StandardCharsets.UTF_8);
        assertThrows(ProtocolException.class,()->router.inspect(one
            .replace("\"protocolVersion\":1","\"protocolVersion\":2")
            .getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->router.inspect(two
            .replace("\"prefaceVersion\":2","\"prefaceVersion\":1")
            .getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->router.inspect(two
            .replace("\"capabilities\":[\"CHAT_TLS13_V2\"]",
                "\"capabilities\":[\"CHAT_TLS13_V2\",\"UNSAFE_PLAINTEXT\"]")
            .getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->router.inspect(
            "{\"protocolVersion\":1,\"prefaceVersion\":2}".getBytes(StandardCharsets.UTF_8)));
    }
    @Test void malformedDuplicateAndChatAsFirstFrameAreNeverDispatched()throws Exception{
        var json=new String(v1(),StandardCharsets.UTF_8);
        assertThrows(ProtocolException.class,()->router.inspect(
            json.replace("\"type\":","\"type\":\"CHAT_TEXT\",\"type\":")
                .getBytes(StandardCharsets.UTF_8)));
        var forbidden=NetworkEnvelope.create(MessageType.CHAT_TEXT,
            new PeerIdentity(PEER,"Peer"),UUID.randomUUID(),
            Map.of("text","must never be plaintext"));
        assertThrows(ProtocolException.class,()->new JsonEnvelopeCodec().encode(forbidden));
        assertThrows(ProtocolException.class,()->router.inspect(new byte[]{(byte)0xc3,0x28}));
        assertThrows(ProtocolException.class,()->router.inspect(new byte[0]));
        assertThrows(ProtocolException.class,()->router.inspect(new byte[16_385]));
        assertThrows(ProtocolException.class,()->router.inspect(
            "{\"protocolVersion\":1,\"type\":\"CHAT_TEXT\"}".getBytes(StandardCharsets.UTF_8)));
    }
}
