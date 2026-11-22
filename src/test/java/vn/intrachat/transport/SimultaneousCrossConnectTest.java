package vn.intrachat.transport;

import org.junit.jupiter.api.Test;
import vn.intrachat.application.*;
import vn.intrachat.domain.PeerIdentity;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Actual loopback sockets: when A and B dial each other simultaneously, the
 * deterministic direction rule must converge to one active socket per peer.
 * Identity here is self-declared and explicitly NOT TLS-authenticated.
 */
class SimultaneousCrossConnectTest {
    private static int port()throws Exception{
        try(ServerSocket s=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            return s.getLocalPort();
        }
    }
    @Test void bothSidesConvergeToOneDiagnosticSessionWithBidirectionalPing()throws Exception{
        var idA=UUID.fromString("00000000-0000-4000-8000-000000000001");
        var idB=UUID.fromString("00000000-0000-4000-8000-000000000002");
        var a=new PeerIdentity(idA,"Same display name");
        var b=new PeerIdentity(idB,"Same display name");
        int aPort=port(),bPort=port();
        while(bPort==aPort)bPort=port();
        InboundApprovalPort consent=req->CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT);
        java.util.List<NetworkEvents.Event> eventsA=new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.List<NetworkEvents.Event> eventsB=new java.util.concurrent.CopyOnWriteArrayList<>();
        try(NetworkCoordinator ca=new NetworkCoordinator(a,consent,eventsA::add);
            NetworkCoordinator cb=new NetworkCoordinator(b,consent,eventsB::add)){
            ca.startListening(new NetworkConfig("127.0.0.1",aPort));
            cb.startListening(new NetworkConfig("127.0.0.1",bPort));
            var outboundA=ca.connect(new NetworkConfig("127.0.0.1",bPort));
            var outboundB=cb.connect(new NetworkConfig("127.0.0.1",aPort));
            // A rejection on one competing physical socket is expected.
            try{outboundA.get(6,TimeUnit.SECONDS);}catch(ExecutionException expected){}
            try{outboundB.get(6,TimeUnit.SECONDS);}catch(ExecutionException expected){}
            boolean converged=false;
            for(int i=0;i<120;i++){
                if(ca.connectedCount()==1 && cb.connectedCount()==1
                    && ca.isConnectedTo(idB)&&cb.isConnectedTo(idA)){
                    converged=true;break;
                }
                Thread.sleep(20);
            }
            assertTrue(converged,"Cross-connect must converge to exactly one open peer session. "
                +"A="+ca.connectedCount()+" Aopen="+ca.isConnectedTo(idB)+" B="+cb.connectedCount()+" Bopen="+cb.isConnectedTo(idA)
                +" eventsA="+eventsA+" eventsB="+eventsB);
            assertNotNull(ca.ping(idB).get(5,TimeUnit.SECONDS));
            assertNotNull(cb.ping(idA).get(5,TimeUnit.SECONDS));
            ca.disconnect(idB);
            for(int i=0;i<100&&cb.connectedCount()!=0;i++)Thread.sleep(20);
            assertEquals(0,cb.connectedCount(),"Close must release remote peer lease");
        }
    }
}
