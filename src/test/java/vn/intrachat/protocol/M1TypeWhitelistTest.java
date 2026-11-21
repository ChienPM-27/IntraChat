package vn.intrachat.protocol;

import org.junit.jupiter.api.Test;
import vn.intrachat.domain.PeerIdentity;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class M1TypeWhitelistTest {
    @Test void userChatGroupFileHistoryNeverAllowedInM1Codec() {
        JsonEnvelopeCodec codec = new JsonEnvelopeCodec();
        PeerIdentity peer = new PeerIdentity(UUID.randomUUID(), "Synthetic peer");
        for (MessageType type : new MessageType[]{
            MessageType.CHAT_TEXT, MessageType.GROUP_MESSAGE,
            MessageType.SYNC_SUMMARY, MessageType.SYNC_REQUEST,
            MessageType.SYNC_BATCH, MessageType.FILE_OFFER, MessageType.FILE_STATUS
        }) {
            NetworkEnvelope fake = NetworkEnvelope.create(
                type, peer, UUID.randomUUID(), Map.of("synthetic", "not private"));
            ProtocolException denied = assertThrows(ProtocolException.class, () -> codec.encode(fake), type.name());
            assertEquals("UNSUPPORTED_TYPE_FOR_PHASE", denied.code());
        }
    }
}
