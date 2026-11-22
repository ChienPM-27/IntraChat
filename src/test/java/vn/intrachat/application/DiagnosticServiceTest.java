package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import vn.intrachat.domain.PeerIdentity;
import vn.intrachat.protocol.*;
import vn.intrachat.transport.NetworkConfig;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** M1 diagnostics only; a successful TCP write is neither a chat ACK nor a READ. */
class DiagnosticServiceTest {
    @Test void cannotProbeBeforeHumanConsentButCanPingAfterReadyInBothDirections()throws Exception {
        int port;
        try(ServerSocket s=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){port=s.getLocalPort();}
        var a=new PeerIdentity(UUID.randomUUID(),"Sender");
        var b=new PeerIdentity(UUID.randomUUID(),"Receiver");
        var manualChoice=new CompletableFuture<InboundApprovalPort.Decision>();
        try(var receiver=new NetworkCoordinator(b,req->manualChoice,e->{});
            var sender=new NetworkCoordinator(a,req->CompletableFuture.completedFuture(
                InboundApprovalPort.Decision.REJECT),e->{})){
            var diagA=new DiagnosticService(sender);
            var diagB=new DiagnosticService(receiver);
            assertThrows(ExecutionException.class,()->diagA.ping(b.deviceId()).get(2,TimeUnit.SECONDS));
            receiver.startListening(new NetworkConfig("127.0.0.1",port));
            var pending=sender.connect(new NetworkConfig("127.0.0.1",port));
            for(int i=0;i<100&&receiver.pendingApprovalCount()==0;i++)Thread.sleep(10);
            assertEquals(0,sender.connectedCount());
            assertThrows(ExecutionException.class,()->diagB.ping(a.deviceId()).get(2,TimeUnit.SECONDS));
            manualChoice.complete(InboundApprovalPort.Decision.ACCEPT);
            assertEquals(b.deviceId(),pending.get(6,TimeUnit.SECONDS).deviceId());
            assertNotNull(diagA.ping(b.deviceId()).get(6,TimeUnit.SECONDS));
            assertNotNull(diagB.ping(a.deviceId()).get(6,TimeUnit.SECONDS));
            sender.disconnect(b.deviceId());
            assertThrows(ExecutionException.class,()->diagA.ping(b.deviceId()).get(2,TimeUnit.SECONDS));
        }finally{manualChoice.complete(InboundApprovalPort.Decision.REJECT);}
    }
    @Test void plaintextCodecRejectsChatGroupFileAndSyncTypes() throws Exception {
        var codec=new JsonEnvelopeCodec();
        var source=new PeerIdentity(UUID.randomUUID(),"Alice");
        for(MessageType forbidden:List.of(MessageType.CHAT_TEXT,MessageType.GROUP_MESSAGE,
                MessageType.FILE_OFFER,MessageType.SYNC_SUMMARY)){
            NetworkEnvelope message=NetworkEnvelope.create(forbidden,source,UUID.randomUUID(),
                Map.of("text","top secret"));
            assertThrows(ProtocolException.class,()->codec.encode(message));
        }
    }
}
