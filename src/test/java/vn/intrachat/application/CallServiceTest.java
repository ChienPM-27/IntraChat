package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import vn.intrachat.domain.P2PConversation;
import vn.intrachat.protocol.WireMessage;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CallServiceTest {
    @Test
    void videoCallSignalingMediaStateUdpVideoAndEnd() throws Exception {
        UUID aId = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        UUID convId = P2PConversation.idFor(aId, bId);
        InetAddress loopback = InetAddress.getByName("127.0.0.1");

        AtomicReference<CallService> refA = new AtomicReference<>();
        AtomicReference<CallService> refB = new AtomicReference<>();

        try (CallService a = new CallService(aId, (peer, type, cid, payload) ->
                refB.get().handleIncoming(aId, "Alice", loopback,
                    new WireMessage(type, UUID.randomUUID(), cid, aId, bId, Instant.now(), payload)), false);
             CallService b = new CallService(bId, (peer, type, cid, payload) ->
                refA.get().handleIncoming(bId, "Bob", loopback,
                    new WireMessage(type, UUID.randomUUID(), cid, bId, aId, Instant.now(), payload)), false)) {
            refA.set(a);
            refB.set(b);

            CountDownLatch remoteVideoLatch = new CountDownLatch(1);
            AtomicReference<BufferedImage> receivedImg = new AtomicReference<>();
            b.addRemoteVideoListener(img -> {
                if (img != null) {
                    receivedImg.set(img);
                    remoteVideoLatch.countDown();
                }
            });

            CallService.CallSession offer = a.startCall(bId, convId, "Bob", CallService.Mode.VIDEO, loopback);
            assertEquals(CallService.State.OUTGOING_RINGING, offer.state());
            assertNotNull(b.currentCall());
            assertEquals(CallService.State.INCOMING_RINGING, b.currentCall().state());

            b.acceptCall(offer.callId());
            assertEquals(CallService.State.CONNECTED, a.currentCall().state());
            assertEquals(CallService.State.CONNECTED, b.currentCall().state());

            // Send a real JPEG frame over the negotiated UDP sockets
            BufferedImage frame = new BufferedImage(160, 120, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = frame.createGraphics();
            g.setColor(Color.GREEN);
            g.fillRect(0, 0, 160, 120);
            g.dispose();
            a.currentTransport().sendVideoJpeg(VideoCallEngine.encodeJpeg(frame, 0.7f));

            assertTrue(remoteVideoLatch.await(3, TimeUnit.SECONDS), "Remote video frame should arrive via UDP");
            assertEquals(160, receivedImg.get().getWidth());
            assertEquals(120, receivedImg.get().getHeight());

            // Sync mute & camera state
            a.setMuted(true);
            a.setCameraOn(false);
            long deadline = System.currentTimeMillis() + 2_000;
            while (System.currentTimeMillis() < deadline
                    && (!b.currentCall().remoteMuted() || b.currentCall().remoteCameraOn())) {
                Thread.sleep(15);
            }
            assertTrue(b.currentCall().remoteMuted());
            assertFalse(b.currentCall().remoteCameraOn());

            // End call propagates to both sides
            a.endCall(offer.callId(), "Done");
            deadline = System.currentTimeMillis() + 2_000;
            while (System.currentTimeMillis() < deadline && b.currentCall() != null) {
                Thread.sleep(15);
            }
            assertNull(a.currentCall());
            assertNull(b.currentCall());
        }
    }

    @Test
    void rejectsCallAndDisconnectTerminatesCleanly() throws Exception {
        UUID aId = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        UUID convId = P2PConversation.idFor(aId, bId);
        InetAddress loopback = InetAddress.getByName("127.0.0.1");

        AtomicReference<CallService> refA = new AtomicReference<>();
        AtomicReference<CallService> refB = new AtomicReference<>();

        try (CallService a = new CallService(aId, (peer, type, cid, payload) ->
                refB.get().handleIncoming(aId, "Alice", loopback,
                    new WireMessage(type, UUID.randomUUID(), cid, aId, bId, Instant.now(), payload)), false);
             CallService b = new CallService(bId, (peer, type, cid, payload) ->
                refA.get().handleIncoming(bId, "Bob", loopback,
                    new WireMessage(type, UUID.randomUUID(), cid, bId, aId, Instant.now(), payload)), false)) {
            refA.set(a);
            refB.set(b);

            CallService.CallSession offer = a.startCall(bId, convId, "Bob", CallService.Mode.VOICE, loopback);
            b.rejectCall(offer.callId(), "Bận rồi");
            long deadline = System.currentTimeMillis() + 2_000;
            while (System.currentTimeMillis() < deadline && a.currentCall() != null) {
                Thread.sleep(15);
            }
            assertNull(a.currentCall());
            assertNull(b.currentCall());

            // Start again and simulate TCP disconnect
            CallService.CallSession second = a.startCall(bId, convId, "Bob", CallService.Mode.VOICE, loopback);
            b.acceptCall(second.callId());
            a.disconnected(bId);
            assertNull(a.currentCall());
        }
    }
}
