package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.intrachat.domain.*;
import vn.intrachat.protocol.*;
import vn.intrachat.security.*;
import vn.intrachat.transport.*;
import javax.net.ssl.*;
import java.net.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real mutual TLS wire handshake; all human approvals here are test-only. */
class PairConfirmIntegrationTest {
    @TempDir Path root;
    private record WirePair(TlsChatSession alice,TlsChatSession bob)implements AutoCloseable {
        @Override public void close(){alice.close();bob.close();}
    }
    private WirePair connectTls(DeviceIdentity a,DeviceIdentity b,UUID request)throws Exception{
        var ac=new TlsContextFactory(a,new PinnedPeerStore(root.resolve("ca-"+request+".pin")),
            b.deviceId(),true);
        var bc=new TlsContextFactory(b,new PinnedPeerStore(root.resolve("cb-"+request+".pin")),
            a.deviceId(),true);
        try(SSLServerSocket server=(SSLServerSocket)ac.context().getServerSocketFactory()
            .createServerSocket(0,3,InetAddress.getByName("127.0.0.1"));
            ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor()){
            server.setNeedClientAuth(true);
            server.setEnabledProtocols(new String[]{"TLSv1.3"});
            Future<SSLSocket> accept=workers.submit(()->{
                SSLSocket s=(SSLSocket)server.accept();
                s.setNeedClientAuth(true);s.setEnabledProtocols(new String[]{"TLSv1.3"});
                s.setSoTimeout(6000);s.startHandshake();return s;
            });
            SSLSocket client=(SSLSocket)bc.context().getSocketFactory()
                .createSocket("127.0.0.1",server.getLocalPort());
            client.setEnabledProtocols(new String[]{"TLSv1.3"});
            client.setSoTimeout(6000);client.startHandshake();
            SSLSocket serverTls=accept.get(7,TimeUnit.SECONDS);
            serverTls.setSoTimeout(450);client.setSoTimeout(450);
            return new WirePair(
                new TlsChatSession(serverTls,a.deviceId(),b.deviceId(),
                    new PairingStateMachine(request)),
                new TlsChatSession(client,b.deviceId(),a.deviceId(),
                    new PairingStateMachine(request)));
        }
    }
    private static ChatV2Message confirm(TlsChatSession sender,UUID conversation,
                                         String digest){
        return new ChatV2Message(UUID.randomUUID(),ChatV2Type.PAIR_CONFIRM,
            sender.sessionId(),sender.localId(),sender.remoteId(),conversation,
            Instant.now(),Map.of("requestId",sender.sessionId().toString(),
                "transcriptHash",digest,"confirmationId",UUID.randomUUID().toString()));
    }
    private static ChatV2Message ready(TlsChatSession sender,UUID conversation,String digest){
        return new ChatV2Message(UUID.randomUUID(),ChatV2Type.PAIR_READY,
            sender.sessionId(),sender.localId(),sender.remoteId(),conversation,
            Instant.now(),Map.of("requestId",sender.sessionId().toString(),"transcriptHash",digest));
    }
    @Test void actualJsseProtectedBilateralConfirmAndReadyThenNoPairControlReplay()
        throws Exception{
        var a=DeviceCertificateFactory.create(UUID.randomUUID());
        var b=DeviceCertificateFactory.create(UUID.randomUUID());
        UUID request=UUID.randomUUID();
        byte[] na=new byte[32],nb=new byte[32];na[0]=12;nb[0]=34;
        var aPins=new PinnedPeerStore(root.resolve("a-trust.pin"));
        var bPins=new PinnedPeerStore(root.resolve("b-trust.pin"));
        try(WirePair wire=connectTls(a,b,request)){
            var left=new PeerTrustService(a,b.deviceId(),b.fingerprint(),na,nb,
                request,aPins,wire.alice().pairingState(),System::nanoTime,Duration.ofMinutes(2));
            var right=new PeerTrustService(b,a.deviceId(),a.fingerprint(),nb,na,
                request,bPins,wire.bob().pairingState(),System::nanoTime,Duration.ofMinutes(2));
            assertEquals(left.transcriptHex(),right.transcriptHex());
            assertEquals(left.conversationId(),right.conversationId());
            left.approveLocalComparison(true);right.approveLocalComparison(true);
            assertFalse(wire.alice().canChat());assertFalse(wire.bob().canChat());
            wire.alice().sendPairControl(confirm(wire.alice(),left.conversationId(),left.transcriptHex()));
            wire.bob().sendPairControl(confirm(wire.bob(),right.conversationId(),right.transcriptHex()));
            left.acceptPeerConfirmation(wire.alice().receivePairControl());
            right.acceptPeerConfirmation(wire.bob().receivePairControl());
            left.markLocalReady();right.markLocalReady();
            wire.alice().sendPairControl(ready(wire.alice(),left.conversationId(),left.transcriptHex()));
            wire.bob().sendPairControl(ready(wire.bob(),right.conversationId(),right.transcriptHex()));
            left.acceptPeerReadyAndCommit(wire.alice().receivePairControl());
            right.acceptPeerReadyAndCommit(wire.bob().receivePairControl());
            assertTrue(wire.alice().canChat());assertTrue(wire.bob().canChat());
            assertTrue(aPins.matches(b.deviceId(),b.fingerprint()));
            assertTrue(bPins.matches(a.deviceId(),a.fingerprint()));
            assertThrows(IllegalStateException.class,()->wire.alice().sendPairControl(
                confirm(wire.alice(),left.conversationId(),left.transcriptHex())),
                "A verified session must not accept further pairing controls as data");
            wire.bob().send(new ChatV2Message(UUID.randomUUID(),ChatV2Type.CHAT_TEXT,
                request,b.deviceId(),a.deviceId(),left.conversationId(),
                Instant.now(),Map.of("text","Chỉ sau READY ✅")));
            ChatV2Message text=wire.alice().receive();
            assertEquals("Chỉ sau READY ✅",text.payload().get("text"));
        }
    }
    @Test void mismatchedTranscriptAndAbsentRemoteReadyNeverPersistPinOrAuthorize()
        throws Exception{
        var a=DeviceCertificateFactory.create(UUID.randomUUID());
        var b=DeviceCertificateFactory.create(UUID.randomUUID());
        byte[] na=new byte[32],nb=new byte[32];na[0]=3;nb[0]=4;
        UUID wrongReq=UUID.randomUUID();
        Path badPath=root.resolve("bad.pin");
        try(WirePair wire=connectTls(a,b,wrongReq)){
            var left=new PeerTrustService(a,b.deviceId(),b.fingerprint(),na,nb,wrongReq,
                new PinnedPeerStore(badPath),wire.alice().pairingState(),System::nanoTime,
                Duration.ofMinutes(2));
            left.approveLocalComparison(true);
            wire.bob().sendPairControl(confirm(wire.bob(),left.conversationId(),"0".repeat(64)));
            assertThrows(IllegalArgumentException.class,
                ()->left.acceptPeerConfirmation(wire.alice().receivePairControl()));
            assertFalse(wire.alice().canChat());
            assertFalse(new PinnedPeerStore(badPath).known(b.deviceId()));
        }
        UUID lostReq=UUID.randomUUID();
        Path lostPath=root.resolve("lost.pin");
        try(WirePair wire=connectTls(a,b,lostReq)){
            var left=new PeerTrustService(a,b.deviceId(),b.fingerprint(),na,nb,lostReq,
                new PinnedPeerStore(lostPath),wire.alice().pairingState(),System::nanoTime,
                Duration.ofMinutes(2));
            left.approveLocalComparison(true);
            wire.bob().sendPairControl(confirm(wire.bob(),left.conversationId(),left.transcriptHex()));
            left.acceptPeerConfirmation(wire.alice().receivePairControl());
            left.markLocalReady();
            wire.alice().sendPairControl(ready(wire.alice(),left.conversationId(),left.transcriptHex()));
            assertThrows(SocketTimeoutException.class,wire.alice()::receivePairControl,
                "Lost peer READY must not turn into unilateral authenticated chat");
            assertFalse(wire.alice().canChat());
            assertFalse(new PinnedPeerStore(lostPath).known(b.deviceId()));
        }
    }
}
