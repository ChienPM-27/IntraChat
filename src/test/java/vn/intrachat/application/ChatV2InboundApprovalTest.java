package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.intrachat.security.*;
import vn.intrachat.transport.*;
import java.net.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Even a previously pinned device still requires explicit inbound consent each time. */
class ChatV2InboundApprovalTest {
    @TempDir Path dir;
    private static int port()throws Exception{
        try(ServerSocket free=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            return free.getLocalPort();
        }
    }
    @Test void priorPinnedPeerStillRequiresFreshInboundApprovalAndRejectCloses()throws Exception{
        var a=DeviceCertificateFactory.create(UUID.randomUUID());
        var b=DeviceCertificateFactory.create(UUID.randomUUID());
        var pinsA=new PinnedPeerStore(dir.resolve("a.pin"));
        var pinsB=new PinnedPeerStore(dir.resolve("b.pin"));
        var yes=(InboundApprovalPort)r->CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT);
        var human=(FingerprintApprovalPort)r->CompletableFuture.completedFuture(true);
        int port=port();
        try(var server=new SecureChatCoordinator(a,"A",pinsA,yes,human,s->{});
            var client=new SecureChatCoordinator(b,"B",pinsB,yes,human,s->{})){
            server.startListening(new NetworkConfig("127.0.0.1",port));
            assertTrue(client.connect(new NetworkConfig("127.0.0.1",port))
                .get(12,TimeUnit.SECONDS).canChat());
        }
        assertTrue(pinsA.matches(b.deviceId(),b.fingerprint()));
        assertTrue(pinsB.matches(a.deviceId(),a.fingerprint()));
        int nextPort=port();
        AtomicInteger freshPrompts=new AtomicInteger();
        AtomicInteger unnecessarySas=new AtomicInteger();
        InboundApprovalPort freshReject=r->{
            freshPrompts.incrementAndGet();
            return CompletableFuture.completedFuture(InboundApprovalPort.Decision.REJECT);
        };
        FingerprintApprovalPort countSas=r->{
            unnecessarySas.incrementAndGet();
            return CompletableFuture.completedFuture(true);
        };
        try(var server=new SecureChatCoordinator(a,"A",pinsA,freshReject,countSas,s->{});
            var client=new SecureChatCoordinator(b,"B",pinsB,yes,countSas,s->{})){
            server.startListening(new NetworkConfig("127.0.0.1",nextPort));
            assertThrows(ExecutionException.class,()->client.connect(
                new NetworkConfig("127.0.0.1",nextPort)).get(6,TimeUnit.SECONDS));
        }
        assertEquals(1,freshPrompts.get(),"Pinning must not suppress per-connection consent");
        assertEquals(0,unnecessarySas.get(),"Inbound reject happens before TLS and SAS");
        assertTrue(pinsA.matches(b.deviceId(),b.fingerprint()));
    }
    @Test void unansweredConsentExpiresAndClosesBeforeTlsWithoutPersistingPin()throws Exception{
        var a=DeviceCertificateFactory.create(UUID.randomUUID());
        var b=DeviceCertificateFactory.create(UUID.randomUUID());
        var pinsA=new PinnedPeerStore(dir.resolve("timeout-a.pin"));
        var pinsB=new PinnedPeerStore(dir.resolve("timeout-b.pin"));
        CountDownLatch prompted=new CountDownLatch(1);
        CompletableFuture<InboundApprovalPort.Decision> unanswered=new CompletableFuture<>();
        InboundApprovalPort consent=r->{prompted.countDown();return unanswered;};
        var other=(InboundApprovalPort)r->
            CompletableFuture.completedFuture(InboundApprovalPort.Decision.REJECT);
        AtomicInteger sas=new AtomicInteger();
        FingerprintApprovalPort fingerprint=view->{
            sas.incrementAndGet();
            return CompletableFuture.completedFuture(true);
        };
        int port=port();
        try(var server=new SecureChatCoordinator(a,"A",pinsA,consent,fingerprint,s->{},
                Duration.ofMillis(140));
            var client=new SecureChatCoordinator(b,"B",pinsB,other,fingerprint,s->{})){
            server.startListening(new NetworkConfig("127.0.0.1",port));
            assertThrows(ExecutionException.class,()->client.connect(
                new NetworkConfig("127.0.0.1",port)).get(5,TimeUnit.SECONDS));
            assertTrue(prompted.await(4,TimeUnit.SECONDS));
            assertEquals(0,sas.get());
            assertFalse(pinsA.known(b.deviceId()));
            assertFalse(pinsB.known(a.deviceId()));
        }finally{unanswered.complete(InboundApprovalPort.Decision.REJECT);}
    }
}
