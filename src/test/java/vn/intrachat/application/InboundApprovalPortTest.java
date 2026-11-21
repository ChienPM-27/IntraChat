package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import vn.intrachat.domain.PeerIdentity;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class InboundApprovalPortTest {
    @Test void explicitDecisionCanBeInjectedIntoTestsWithoutAutomaticProductionApproval() throws Exception {
        InboundApprovalPort reject = request -> CompletableFuture.completedFuture(InboundApprovalPort.Decision.REJECT);
        InboundRequest request=new InboundRequest(UUID.randomUUID(),"127.0.0.1:47321",
            new PeerIdentity(UUID.randomUUID(),"Test peer"),Instant.now().plusSeconds(30));
        assertEquals(InboundApprovalPort.Decision.REJECT,reject.requestApproval(request).toCompletableFuture().get());
        InboundApprovalPort accept = ignored -> CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT);
        assertEquals(InboundApprovalPort.Decision.ACCEPT,accept.requestApproval(request).toCompletableFuture().get());
    }
}
