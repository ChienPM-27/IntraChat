package vn.intrachat.transport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.intrachat.application.PairingStateMachine;
import vn.intrachat.protocol.*;
import vn.intrachat.security.*;
import javax.net.ssl.*;
import java.net.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual JSSE SSL sockets, not Boolean TLS stubs. */
class TlsChatHandshakeTest {
    @TempDir Path root;
    private static PairingStateMachine manuallyReady(){
        var state=new PairingStateMachine(UUID.randomUUID());
        state.acceptLocalFingerprint();state.acceptRemoteConfirmation();
        state.localReady();state.remoteReady();
        return state;
    }
    private static SSLServerSocket startServer(TlsContextFactory factory)throws Exception{
        SSLServerSocket socket=(SSLServerSocket)factory.context().getServerSocketFactory()
            .createServerSocket(0,4,InetAddress.getByName("127.0.0.1"));
        socket.setEnabledProtocols(new String[]{"TLSv1.3"});
        socket.setNeedClientAuth(true);
        return socket;
    }
    @Test void mutualTls13CandidateCannotChatBeforeReadyOrWithForgedPeerUuid()throws Exception {
        var alice=DeviceCertificateFactory.create(UUID.randomUUID());
        var bob=DeviceCertificateFactory.create(UUID.randomUUID());
        var own=new TlsContextFactory(alice,new PinnedPeerStore(root.resolve("a.pin")),
            bob.deviceId(),true);
        var other=new TlsContextFactory(bob,new PinnedPeerStore(root.resolve("b.pin")),
            alice.deviceId(),true);
        try(SSLServerSocket server=startServer(own);
            ExecutorService pool=Executors.newVirtualThreadPerTaskExecutor()){
            Future<SSLSocket> incoming=pool.submit(()->{
                SSLSocket socket=(SSLSocket)server.accept();
                socket.setEnabledProtocols(new String[]{"TLSv1.3"});
                socket.setNeedClientAuth(true);
                socket.setSoTimeout(4000);
                socket.startHandshake();
                return socket;
            });
            try(SSLSocket outgoing=(SSLSocket)other.context().getSocketFactory()
                .createSocket("127.0.0.1",server.getLocalPort())){
                outgoing.setEnabledProtocols(new String[]{"TLSv1.3"});
                outgoing.setSoTimeout(4000);
                outgoing.startHandshake();
                try(SSLSocket accepted=incoming.get(6,TimeUnit.SECONDS)){
                    assertEquals("TLSv1.3",accepted.getSession().getProtocol());
                    assertEquals("TLSv1.3",outgoing.getSession().getProtocol());
                    assertEquals(1,accepted.getSession().getPeerCertificates().length);
                    // The peer cert is cryptographically valid and possession-proven,
                    // but without two-sided PAIR_READY there is NO app authorization.
                    try(TlsChatSession quarantined=new TlsChatSession(accepted,
                        alice.deviceId(),bob.deviceId(),new PairingStateMachine(UUID.randomUUID()))){
                        assertFalse(quarantined.canChat());
                        assertThrows(IllegalStateException.class,()->quarantined.send(
                            new ChatV2Message(UUID.randomUUID(),ChatV2Type.CHAT_TEXT,
                                quarantined.sessionId(),alice.deviceId(),bob.deviceId(),
                                UUID.randomUUID(),Instant.now(),Map.of("text","no pre-ready payload"))));
                    }
                    // A public socket wrapper must not permit a caller to manually
                    // mark READY on a TLS peer certificate for a DIFFERENT UUID.
                    try(TlsChatSession forged=new TlsChatSession(outgoing,
                        bob.deviceId(),UUID.randomUUID(),manuallyReady())){
                        assertFalse(forged.canChat(),
                            "TLS certificate subject must bind to the claimed remote device ID");
                        assertThrows(IllegalStateException.class,()->forged.send(
                            new ChatV2Message(UUID.randomUUID(),ChatV2Type.CHAT_TEXT,
                                forged.sessionId(),bob.deviceId(),forged.remoteId(),
                                UUID.randomUUID(),Instant.now(),Map.of("text","spoof"))));
                    }
                }
            }
        }
    }
    @Test void candidateTlsNotTrustedWhenStrictModeRequiresAnExistingPin()throws Exception{
        var alice=DeviceCertificateFactory.create(UUID.randomUUID());
        var bob=DeviceCertificateFactory.create(UUID.randomUUID());
        var strict=new TlsContextFactory(alice,new PinnedPeerStore(root.resolve("strict.pin")),
            bob.deviceId(),false);
        var peer=new TlsContextFactory(bob,new PinnedPeerStore(root.resolve("client.pin")),
            alice.deviceId(),true);
        assertFalse(strict.isTrustedBeforeUserComparison());
        try(SSLServerSocket server=startServer(strict);
            ExecutorService pool=Executors.newVirtualThreadPerTaskExecutor()){
            Future<?> received=pool.submit(()->{
                try(SSLSocket socket=(SSLSocket)server.accept()){
                    socket.setUseClientMode(false);
                    socket.setEnabledProtocols(new String[]{"TLSv1.3"});
                    socket.setNeedClientAuth(true);
                    socket.setSoTimeout(4000);
                    socket.startHandshake();
                }
                return null;
            });
            try(SSLSocket outbound=(SSLSocket)peer.context().getSocketFactory()
                .createSocket("127.0.0.1",server.getLocalPort())){
                outbound.setEnabledProtocols(new String[]{"TLSv1.3"});
                outbound.setSoTimeout(4000);
                try{outbound.startHandshake();}catch(java.io.IOException expected){}
            }
            assertThrows(ExecutionException.class,()->received.get(7,TimeUnit.SECONDS),
                "Strict unknown-cert quarantine must not complete mutual TLS");
        }
    }
    @Test void changedSpkiSameUuidFailsMutualTlsBeforeAnyPrivateChat()throws Exception{
        var alice=DeviceCertificateFactory.create(UUID.randomUUID());
        var expected=DeviceCertificateFactory.create(UUID.randomUUID());
        var imposter=DeviceCertificateFactory.create(expected.deviceId());
        PinnedPeerStore pins=new PinnedPeerStore(root.resolve("pinned.pin"));
        pins.pinNew(expected.deviceId(),expected.fingerprint());
        var strict=new TlsContextFactory(alice,pins,expected.deviceId(),false);
        var fake=new TlsContextFactory(imposter,new PinnedPeerStore(root.resolve("imposter.pin")),
            alice.deviceId(),true);
        try(SSLServerSocket server=startServer(strict);
            ExecutorService pool=Executors.newVirtualThreadPerTaskExecutor()){
            Future<?> received=pool.submit(()->{
                try(SSLSocket socket=(SSLSocket)server.accept()){
                    socket.setEnabledProtocols(new String[]{"TLSv1.3"});
                    socket.setNeedClientAuth(true);
                    socket.setSoTimeout(4000);
                    socket.startHandshake();
                }
                return null;
            });
            try(SSLSocket outbound=(SSLSocket)fake.context().getSocketFactory()
                .createSocket("127.0.0.1",server.getLocalPort())){
                outbound.setEnabledProtocols(new String[]{"TLSv1.3"});
                outbound.setSoTimeout(4000);
                try{outbound.startHandshake();}catch(SSLException expectedFailure){}
            }
            assertThrows(ExecutionException.class,()->received.get(7,TimeUnit.SECONDS));
            assertTrue(pins.matches(expected.deviceId(),expected.fingerprint()),
                "Failed TLS authentication must never overwrite the saved trust pin");
        }
    }
}
