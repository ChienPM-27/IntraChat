package vn.intrachat.protocol;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GroupV3BootstrapCodecTest {
    static UUID A=UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    static UUID B=UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private final GroupV3BootstrapCodec codec=new GroupV3BootstrapCodec();
    @Test void helloAcceptExactCapabilitiesAndNoncesRoundTrip()throws Exception{
        var hello=new GroupV3BootstrapCodec.Hello(UUID.randomUUID(),A,"Owner",new byte[32]);
        var raw=codec.encodeHello(hello);
        assertTrue(new String(raw,StandardCharsets.UTF_8).contains("GROUP_V1"));
        assertEquals(hello.requestId(),codec.decodeHello(raw).requestId());
        assertArrayEquals(hello.nonce(),codec.decodeHello(raw).nonce());
        var accept=new GroupV3BootstrapCodec.Accept(hello.requestId(),B,"Member",new byte[32]);
        assertEquals(B,codec.decodeAccept(codec.encodeAccept(accept)).receiverId());
    }
    @Test void strictV2WillRejectV3HelloAndV3WillRejectV2()throws Exception{
        var hello=new GroupV3BootstrapCodec.Hello(UUID.randomUUID(),A,"Owner",new byte[32]);
        var v3=codec.encodeHello(hello);
        assertThrows(ProtocolException.class,()->new ChatV2BootstrapCodec().decodeHello(v3));
        var v2=new ChatV2BootstrapCodec.Hello(UUID.randomUUID(),A,"Owner",new byte[32]);
        assertThrows(ProtocolException.class,()->codec.decodeHello(new ChatV2BootstrapCodec().encodeHello(v2)));
    }
    @Test void malformedUnknownDuplicateAndNonFixedCapabilitiesFail()throws Exception{
        var hello=new GroupV3BootstrapCodec.Hello(UUID.randomUUID(),A,"Owner",new byte[32]);
        String valid=new String(codec.encodeHello(hello),StandardCharsets.UTF_8);
        assertThrows(ProtocolException.class,()->codec.decodeHello(valid.replace("\"prefaceVersion\":3","\"prefaceVersion\":4").getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->codec.decodeHello(valid.replace("\"type\":","\"extra\":0,\"type\":").getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->codec.decodeHello(valid.replace("\"type\":","\"type\":\"CHAT_HELLO\",\"type\":").getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->codec.decodeHello(valid.replace("GROUP_V1","UNKNOWN_V1").getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProtocolException.class,()->codec.decodeHello(new byte[16385]));
        assertThrows(ProtocolException.class,()->codec.decodeHello(new byte[]{(byte)0xC3,0x28}));
    }
}
