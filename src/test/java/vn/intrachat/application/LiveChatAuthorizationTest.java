package vn.intrachat.application;

import listener.LiveChatAdapter;
import model.MessageContent;
import org.junit.jupiter.api.Test;
import vn.intrachat.domain.MessageAttempt;
import vn.intrachat.security.*;
import vn.intrachat.transport.TlsChatSession;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** No Live application data before verified READY; offline send is a failed local action. */
class LiveChatAuthorizationTest {
    @Test void disconnectedUserAndWrongConversationNeverQueueUserText() throws Exception {
        DeviceIdentity local=DeviceCertificateFactory.create(UUID.randomUUID());
        AtomicInteger sent=new AtomicInteger();
        try(LiveChatService service=new LiveChatService(local)) {
            service.addListener(event->{
                if(event.kind()==LiveChatService.EventKind.SENT
                    || event.kind()==LiveChatService.EventKind.RECEIVED)sent.incrementAndGet();
            });
            assertTrue(service.connectedPeers().isEmpty());
            var offline=service.sendText(UUID.randomUUID(),"private message (synthetic)");
            assertThrows(ExecutionException.class,()->offline.get(3,TimeUnit.SECONDS));
            LiveChatAdapter adapter=new LiveChatAdapter(local,"Local",service,()->{});
            try {
                var misrouted=adapter.sendMessage(UUID.randomUUID().toString(),
                    MessageContent.text("synthetic"));
                assertThrows(ExecutionException.class,()->misrouted.toCompletableFuture()
                    .get(3,TimeUnit.SECONDS));
            }finally{adapter.close();}
            assertEquals(0,sent.get());
            assertNull(service.status(UUID.randomUUID()));
        }
    }

    @Test void socketObjectWithoutBilateralPairingReadyCannotBeAttachedOrSendChat() throws Exception {
        DeviceIdentity local=DeviceCertificateFactory.create(UUID.randomUUID());
        UUID other=UUID.randomUUID();
        try(LiveChatService service=new LiveChatService(local);
            SSLSocket socket=(SSLSocket)SSLSocketFactory.getDefault().createSocket()){
            var pairing=new PairingStateMachine(UUID.randomUUID());
            try(TlsChatSession transport=new TlsChatSession(socket,local.deviceId(),other,pairing)) {
                assertFalse(transport.canChat());
                assertThrows(IllegalStateException.class,()->service.attach(transport,"Unverified"));
                assertTrue(service.connectedPeers().isEmpty());
            }
        }
    }
}
