package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import vn.intrachat.domain.PeerIdentity;
import vn.intrachat.transport.NetworkConfig;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Current M1 coordinator/backend shutdown; obsolete standalone M1 Swing launcher was removed. */
class ShutdownTest {
    private static int freePort()throws Exception {
        try(ServerSocket s=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            return s.getLocalPort();
        }
    }
    @Test void fiveListenConnectPingCloseCyclesReleaseSessionsAndListener()throws Exception {
        for(int cycle=0;cycle<5;cycle++){
            int port=freePort();
            var a=new PeerIdentity(UUID.randomUUID(),"A"+cycle);
            var b=new PeerIdentity(UUID.randomUUID(),"B"+cycle);
            InboundApprovalPort scripted=req->CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT);
            var receiver=new NetworkCoordinator(a,scripted,e->{});
            var sender=new NetworkCoordinator(b,scripted,e->{});
            long began=System.nanoTime();
            try {
                receiver.startListening(new NetworkConfig("127.0.0.1",port));
                sender.connect(new NetworkConfig("127.0.0.1",port)).get(6,TimeUnit.SECONDS);
                assertNotNull(sender.ping(a.deviceId()).get(6,TimeUnit.SECONDS));
                assertNotNull(receiver.ping(b.deviceId()).get(6,TimeUnit.SECONDS));
            } finally {
                sender.close();
                receiver.close();
            }
            long millis=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-began);
            assertFalse(receiver.isListening(),"Listener must stop on close");
            assertEquals(0,sender.connectedCount());
            assertEquals(0,receiver.connectedCount());
            assertTrue(millis<4_000,"Local 5-cycle close must not hang, cycle "+cycle+" took "+millis+"ms");
            // The listener must no longer ACCEPT connections. Windows TCP TIME_WAIT
            // can temporarily prevent immediate port rebinding even after correct close,
            // so use a direct refused-connect probe rather than a false-positive bind.
            try(Socket probe=new Socket()){
                assertThrows(java.io.IOException.class,
                    ()->probe.connect(new InetSocketAddress("127.0.0.1",port),250));
            }
        }
    }
    @Test void cancellingAndClosingAnUnansweredInboundRequestReleasesResources()throws Exception{
        int port=freePort();
        CountDownLatch prompted=new CountDownLatch(1);
        var unanswered=new CompletableFuture<InboundApprovalPort.Decision>();
        var receiver=new NetworkCoordinator(new PeerIdentity(UUID.randomUUID(),"Receiver"),
            req->{prompted.countDown();return unanswered;},e->{});
        var sender=new NetworkCoordinator(new PeerIdentity(UUID.randomUUID(),"Sender"),
            req->CompletableFuture.completedFuture(InboundApprovalPort.Decision.REJECT),e->{});
        try{
            receiver.startListening(new NetworkConfig("127.0.0.1",port));
            var pending=sender.connect(new NetworkConfig("127.0.0.1",port));
            assertTrue(prompted.await(4,TimeUnit.SECONDS));
            receiver.close();
            sender.cancelPendingConnect();
            assertThrows(ExecutionException.class,()->pending.get(5,TimeUnit.SECONDS));
            for(int i=0;i<100&&receiver.pendingApprovalCount()!=0;i++)Thread.sleep(10);
            assertEquals(0,receiver.connectedCount());
            assertEquals(0,sender.connectedCount());
            assertEquals(0,receiver.pendingApprovalCount());
        }finally{
            unanswered.complete(InboundApprovalPort.Decision.REJECT);
            sender.close();receiver.close();
        }
    }
}
