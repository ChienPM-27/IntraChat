package vn.intrachat.transport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.intrachat.security.*;
import vn.intrachat.application.PairingStateMachine;
import vn.intrachat.protocol.*;
import javax.net.ssl.*;
import java.net.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class TlsChatSessionSecurityTest {
    @TempDir Path dir;
    @Test void realTls13RequiresPairReadyBeforeAnyChatFrame() throws Exception {
        DeviceIdentity a=DeviceCertificateFactory.create(UUID.randomUUID());
        DeviceIdentity b=DeviceCertificateFactory.create(UUID.randomUUID());
        PinnedPeerStore aPins=new PinnedPeerStore(dir.resolve("a.bin"));
        PinnedPeerStore bPins=new PinnedPeerStore(dir.resolve("b.bin"));
        TlsContextFactory aCtx=new TlsContextFactory(a,aPins,b.deviceId(),true);
        TlsContextFactory bCtx=new TlsContextFactory(b,bPins,a.deviceId(),true);
        try(SSLServerSocket server=(SSLServerSocket)aCtx.context().getServerSocketFactory()
                .createServerSocket(0,5,InetAddress.getByName("127.0.0.1"))) {
            server.setEnabledProtocols(new String[]{"TLSv1.3"});
            server.setNeedClientAuth(true);
            ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor();
            try{
                Future<SSLSocket> incoming=workers.submit(()->{
                    SSLSocket socket=(SSLSocket)server.accept();
                    socket.setEnabledProtocols(new String[]{"TLSv1.3"});
                    socket.setNeedClientAuth(true);
                    socket.setSoTimeout(4000);
                    socket.startHandshake();
                    return socket;
                });
                try(SSLSocket outgoing=(SSLSocket)bCtx.context().getSocketFactory()
                        .createSocket("127.0.0.1",server.getLocalPort())) {
                    outgoing.setEnabledProtocols(new String[]{"TLSv1.3"});
                    outgoing.setSoTimeout(4000);
                    outgoing.startHandshake();
                    try(SSLSocket accepted=incoming.get(5,TimeUnit.SECONDS)) {
                        assertEquals("TLSv1.3",accepted.getSession().getProtocol());
                        UUID request=UUID.randomUUID();
                        PairingStateMachine aPair=new PairingStateMachine(request);
                        PairingStateMachine bPair=new PairingStateMachine(request);
                        try(TlsChatSession sessionA=new TlsChatSession(accepted,a.deviceId(),b.deviceId(),aPair);
                            TlsChatSession sessionB=new TlsChatSession(outgoing,b.deviceId(),a.deviceId(),bPair)){
                            ChatV2Message message=new ChatV2Message(UUID.randomUUID(),ChatV2Type.CHAT_TEXT,
                                request,b.deviceId(),a.deviceId(),UUID.randomUUID(),Instant.now(),Map.of("text","hello"));
                            assertThrows(IllegalStateException.class,()->sessionB.send(message));
                            for(PairingStateMachine state:new PairingStateMachine[]{aPair,bPair}){
                                state.acceptLocalFingerprint();state.acceptRemoteConfirmation();
                                state.localReady();state.remoteReady();
                            }
                            sessionB.send(message);
                            assertEquals(message,sessionA.receive());
                        }
                    }
                }
            }finally{workers.shutdownNow();}
        }
    }
}
