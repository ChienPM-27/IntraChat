package vn.intrachat.transport;

import org.junit.jupiter.api.Test;
import vn.intrachat.application.*;
import vn.intrachat.domain.PeerIdentity;
import vn.intrachat.protocol.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Raw M1 preapproval tests: never upgrade a self-claimed UUID into authentication. */
class HandshakeNegativeTest {
    private static int port()throws Exception{
        try(ServerSocket s=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            return s.getLocalPort();
        }
    }
    private static void send(Socket socket,NetworkEnvelope envelope)throws Exception{
        FrameCodec.writeFrame(socket.getOutputStream(),new JsonEnvelopeCodec().encode(envelope),FrameCodec.HELLO_LIMIT);
    }
    @Test void selfConnectClaimIsRejectedBeforeHumanApproval()throws Exception{
        var local=new PeerIdentity(UUID.randomUUID(),"Receiver");
        AtomicInteger prompts=new AtomicInteger();
        int port=port();
        try(var receiver=new NetworkCoordinator(local,request->{
            prompts.incrementAndGet();
            return CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT);
        },e->{})){
            receiver.startListening(new NetworkConfig("127.0.0.1",port));
            try(Socket bad=new Socket("127.0.0.1",port)){
                bad.setSoTimeout(2500);
                var hello=NetworkEnvelope.create(MessageType.HELLO,local,
                    NetworkEnvelope.UNKNOWN_PEER,Map.of("requestId",UUID.randomUUID().toString(),
                    "supportedVersions",List.of(1),"clientVersion","m1-test"));
                send(bad,hello);
                assertEquals(-1,bad.getInputStream().read());
            }
            assertEquals(0,prompts.get());
            assertEquals(0,receiver.connectedCount());
        }
    }
    @Test void badConfirmationRequestIdMustCloseWithoutReadyOrDiagnostics()throws Exception{
        var local=new PeerIdentity(UUID.randomUUID(),"Receiver");
        var attacker=new PeerIdentity(UUID.randomUUID(),"Attacker");
        int port=port();
        try(var receiver=new NetworkCoordinator(local,
            request->CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT),e->{})){
            receiver.startListening(new NetworkConfig("127.0.0.1",port));
            try(Socket bad=new Socket("127.0.0.1",port)){
                bad.setSoTimeout(3000);
                UUID requestId=UUID.randomUUID();
                send(bad,NetworkEnvelope.create(MessageType.HELLO,attacker,NetworkEnvelope.UNKNOWN_PEER,
                    Map.of("requestId",requestId.toString(),"supportedVersions",List.of(1),"clientVersion","m1-test")));
                NetworkEnvelope accept=new JsonEnvelopeCodec().decode(
                    FrameCodec.readFrame(bad.getInputStream(),FrameCodec.HELLO_LIMIT));
                assertEquals(MessageType.HELLO_ACCEPT,accept.type());
                send(bad,NetworkEnvelope.create(MessageType.HELLO_CONFIRM,attacker,local.deviceId(),
                    Map.of("requestId",UUID.randomUUID().toString(),
                    "sessionId",accept.payload().get("sessionId"),"chosenVersion",1)));
                assertEquals(-1,bad.getInputStream().read(),"Wrong correlation must never receive READY");
            }
            assertEquals(0,receiver.connectedCount());
        }
    }
    @Test void wrongChosenVersionCannotDowngradeToPlaintextChat()throws Exception{
        var local=new PeerIdentity(UUID.randomUUID(),"Receiver");
        var attacker=new PeerIdentity(UUID.randomUUID(),"Attacker");
        int port=port();
        try(var receiver=new NetworkCoordinator(local,
            request->CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT),e->{})){
            receiver.startListening(new NetworkConfig("127.0.0.1",port));
            try(Socket bad=new Socket("127.0.0.1",port)){
                bad.setSoTimeout(3000);
                UUID requestId=UUID.randomUUID();
                send(bad,NetworkEnvelope.create(MessageType.HELLO,attacker,NetworkEnvelope.UNKNOWN_PEER,
                    Map.of("requestId",requestId.toString(),"supportedVersions",List.of(1),"clientVersion","m1-test")));
                var accept=new JsonEnvelopeCodec().decode(
                    FrameCodec.readFrame(bad.getInputStream(),FrameCodec.HELLO_LIMIT));
                String frame=new String(new JsonEnvelopeCodec().encode(
                    NetworkEnvelope.create(MessageType.HELLO_CONFIRM,attacker,local.deviceId(),
                        Map.of("requestId",requestId.toString(),
                            "sessionId",accept.payload().get("sessionId"),"chosenVersion",1))),
                    StandardCharsets.UTF_8);
                assertTrue(frame.contains("\"chosenVersion\":1"));
                byte[] malformed=frame.replace("\"chosenVersion\":1",
                    "\"chosenVersion\":2").getBytes(StandardCharsets.UTF_8);
                FrameCodec.writeFrame(bad.getOutputStream(),malformed,FrameCodec.HELLO_LIMIT);
                assertEquals(-1,bad.getInputStream().read());
            }
            assertEquals(0,receiver.connectedCount());
        }
    }
}
