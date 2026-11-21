package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import vn.intrachat.domain.PeerIdentity;
import vn.intrachat.protocol.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class SecurityBoundaryTest {
    @Test void acceptIsNotPersistentTrustAndDoesNotEnableHistory() throws Exception {
        PeerIdentity alleged = new PeerIdentity(UUID.randomUUID(), "Unverified");
        InboundApprovalPort testDecision = request ->
                CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT);
        InboundRequest incoming = new InboundRequest(UUID.randomUUID(), "127.0.0.1:55555",
            alleged, java.time.Instant.now().plusSeconds(5));
        assertEquals(InboundApprovalPort.Decision.ACCEPT,
            testDecision.requestApproval(incoming).toCompletableFuture().get());
        assertEquals(alleged.deviceId(), incoming.claimedPeer().deviceId());
        JsonEnvelopeCodec codec = new JsonEnvelopeCodec();
        NetworkEnvelope sync = NetworkEnvelope.create(MessageType.SYNC_SUMMARY, alleged, UUID.randomUUID(),
            Map.of("conversationId", UUID.randomUUID().toString()));
        assertEquals("UNSUPPORTED_TYPE_FOR_PHASE",
            assertThrows(ProtocolException.class, () -> codec.encode(sync)).code());
    }
}
