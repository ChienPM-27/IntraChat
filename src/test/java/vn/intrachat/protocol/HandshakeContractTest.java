package vn.intrachat.protocol;

import org.junit.jupiter.api.Test;
import vn.intrachat.domain.PeerIdentity;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HandshakeContractTest {
    @Test void requestMustContainSupportedVersionAndValidUuid() throws Exception {
        PeerIdentity local=new PeerIdentity(UUID.randomUUID(),"A");
        String request=UUID.randomUUID().toString();
        NetworkEnvelope hello=NetworkEnvelope.create(MessageType.HELLO,local,NetworkEnvelope.UNKNOWN_PEER,
            Map.of("requestId",request,"supportedVersions",List.of(1),"clientVersion","0.1.0"));
        assertEquals(UUID.fromString(request),HandshakeValidator.validateHello(hello));
        assertThrows(ProtocolException.class,()->HandshakeValidator.validateHello(NetworkEnvelope.create(MessageType.HELLO,local,NetworkEnvelope.UNKNOWN_PEER,
            Map.of("requestId",request,"supportedVersions",List.of(2),"clientVersion","0.1.0"))));
        assertThrows(ProtocolException.class,()->HandshakeValidator.validateHello(NetworkEnvelope.create(MessageType.HELLO,local,NetworkEnvelope.UNKNOWN_PEER,Map.of("requestId","bad-id","supportedVersions",List.of(1),"clientVersion","0.1.0"))));
    }
    @Test void diagnosticMessageCannotBeHello() {
        NetworkEnvelope ping=NetworkEnvelope.create(MessageType.DIAGNOSTIC_PING,
            new PeerIdentity(UUID.randomUUID(),"A"),UUID.randomUUID(),Map.of("probeId",UUID.randomUUID().toString()));
        assertThrows(ProtocolException.class,()->HandshakeValidator.validateHello(ping));
    }
}
