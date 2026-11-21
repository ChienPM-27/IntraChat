package vn.intrachat.transport;

import org.junit.jupiter.api.Test;
import vn.intrachat.application.*;
import vn.intrachat.domain.PeerIdentity;
import java.net.ServerSocket;
import java.net.InetAddress;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class HandshakeTest {
    private int freePort() throws Exception {
        try(ServerSocket s=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){return s.getLocalPort();}
    }
    @Test void requiresExplicitRemoteApprovalThenDiagnosticSession() throws Exception {
        int port=freePort();
        AtomicInteger prompts=new AtomicInteger();
        CompletableFuture<InboundApprovalPort.Decision> answer=new CompletableFuture<>();
        InboundApprovalPort consent=request->{prompts.incrementAndGet();return answer;};
        try(NetworkCoordinator receiver=new NetworkCoordinator(new PeerIdentity(UUID.randomUUID(),"Máy B"),consent,e->{});
            NetworkCoordinator sender=new NetworkCoordinator(new PeerIdentity(UUID.randomUUID(),"Máy A"),
                req->CompletableFuture.completedFuture(InboundApprovalPort.Decision.REJECT),e->{})){
            receiver.startListening(new NetworkConfig("127.0.0.1",port));
            CompletableFuture<PeerIdentity> connection=sender.connect(new NetworkConfig("127.0.0.1",port));
            for(int i=0;i<100 && prompts.get()==0;i++)Thread.sleep(20);
            assertEquals(1,prompts.get());
            assertFalse(connection.isDone(),"No CONNECTED while waiting user approval");
            answer.complete(InboundApprovalPort.Decision.ACCEPT);
            PeerIdentity remote=connection.get(5,TimeUnit.SECONDS);
            assertEquals(receiver.localPeer().deviceId(),remote.deviceId());
            assertTrue(sender.isConnectedTo(remote.deviceId()));
            assertNotNull(sender.ping(remote.deviceId()).get(5,TimeUnit.SECONDS));
        }
    }
    @Test void explicitRejectDoesNotCreateSession() throws Exception {
        int port=freePort();
        try(NetworkCoordinator receiver=new NetworkCoordinator(new PeerIdentity(UUID.randomUUID(),"B"),
                req->CompletableFuture.completedFuture(InboundApprovalPort.Decision.REJECT),e->{});
            NetworkCoordinator sender=new NetworkCoordinator(new PeerIdentity(UUID.randomUUID(),"A"),
                req->CompletableFuture.completedFuture(InboundApprovalPort.Decision.REJECT),e->{})){
            receiver.startListening(new NetworkConfig("127.0.0.1",port));
            assertThrows(ExecutionException.class,()->sender.connect(new NetworkConfig("127.0.0.1",port)).get(5,TimeUnit.SECONDS));
            assertEquals(0,sender.connectedCount());
            assertEquals(0,receiver.connectedCount());
        }
    }
}
