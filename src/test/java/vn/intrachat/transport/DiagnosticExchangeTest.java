package vn.intrachat.transport;

import org.junit.jupiter.api.Test;
import vn.intrachat.application.*;
import vn.intrachat.domain.PeerIdentity;
import java.net.*;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticExchangeTest {
    @Test void approvedPeersExchangePingPongBothDirectionsWithoutHistory() throws Exception {
        int port;
        try(ServerSocket tmp=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){port=tmp.getLocalPort();}
        InboundApprovalPort scriptedExplicitApproval=request->CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT);
        PeerIdentity a=new PeerIdentity(UUID.randomUUID(),"A");
        PeerIdentity b=new PeerIdentity(UUID.randomUUID(),"B");
        try(NetworkCoordinator receiver=new NetworkCoordinator(b,scriptedExplicitApproval,e->{});
            NetworkCoordinator sender=new NetworkCoordinator(a,scriptedExplicitApproval,e->{})){
            receiver.startListening(new NetworkConfig("127.0.0.1",port));
            PeerIdentity connected=sender.connect(new NetworkConfig("127.0.0.1",port)).get(6,TimeUnit.SECONDS);
            assertEquals(b.deviceId(),connected.deviceId());
            assertNotNull(sender.ping(b.deviceId()).get(6,TimeUnit.SECONDS));
            assertNotNull(receiver.ping(a.deviceId()).get(6,TimeUnit.SECONDS));
            sender.disconnect(b.deviceId());
            assertEquals(0,sender.connectedCount());
        }
    }
}
