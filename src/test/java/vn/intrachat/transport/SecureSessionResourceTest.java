package vn.intrachat.transport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.intrachat.application.*;
import vn.intrachat.security.*;
import java.net.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Four TLS ACTIVE peers maximum, candidate admission cannot silently grow forever. */
class SecureSessionResourceTest {
    @TempDir Path temp;
    private static int freePort()throws Exception{
        try(ServerSocket available=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            return available.getLocalPort();
        }
    }
    @Test void fifthVerifiedPeerRejectedAndOldClosedPeerDoesNotHoldSlotForever()throws Exception{
        DeviceIdentity owner=DeviceCertificateFactory.create(UUID.randomUUID());
        InboundApprovalPort consent=req->CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT);
        FingerprintApprovalPort human=view->CompletableFuture.completedFuture(true);
        List<SecureChatCoordinator> clients=new ArrayList<>();
        int port=freePort();
        try(LiveChatService live=new LiveChatService(owner);
            SecureChatCoordinator server=new SecureChatCoordinator(owner,"Host",
                new PinnedPeerStore(temp.resolve("owner.pin")),consent,human,session->{
                    try{live.attach(session,"Peer");}
                    catch(Exception problem){throw new IllegalStateException(problem);}
                })){
            server.startListening(new NetworkConfig("127.0.0.1",port));
            try{
                for(int i=0;i<4;i++){
                    var device=DeviceCertificateFactory.create(UUID.randomUUID());
                    var client=new SecureChatCoordinator(device,"Peer-"+i,
                        new PinnedPeerStore(temp.resolve("client-"+i+".pin")),consent,human,s->{});
                    clients.add(client);
                    assertTrue(client.connect(new NetworkConfig("127.0.0.1",port))
                        .get(12,TimeUnit.SECONDS).canChat());
                }
                for(int i=0;i<150&&(server.activeCount()!=4||live.connectedPeers().size()!=4);i++)
                    Thread.sleep(20); // local TLS client READY precedes inbound attach callback
                assertEquals(4,server.activeCount());
                assertEquals(4,live.connectedPeers().size());
                DeviceIdentity extra=DeviceCertificateFactory.create(UUID.randomUUID());
                var fifth=new SecureChatCoordinator(extra,"Fifth",
                    new PinnedPeerStore(temp.resolve("fifth.pin")),consent,human,s->{});
                clients.add(fifth);
                assertThrows(ExecutionException.class,()->fifth.connect(
                    new NetworkConfig("127.0.0.1",port)).get(8,TimeUnit.SECONDS));
                assertEquals(4,server.activeCount());
                clients.getFirst().close();
                for(int i=0;i<150&&live.connectedPeers().size()!=3;i++)Thread.sleep(20);
                assertEquals(3,live.connectedPeers().size());
                assertTrue(fifth.connect(new NetworkConfig("127.0.0.1",port))
                    .get(12,TimeUnit.SECONDS).canChat());
                for(int i=0;i<150&&(server.activeCount()!=4||live.connectedPeers().size()!=4);i++)
                    Thread.sleep(20); // inbound ready/attach callbacks complete asynchronously
                assertEquals(4,server.activeCount());
                assertEquals(4,live.connectedPeers().size());
            }finally{
                for(var c:clients)c.close();
            }
        }
    }
}
