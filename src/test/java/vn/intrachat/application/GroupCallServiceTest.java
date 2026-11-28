package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import vn.intrachat.domain.ChatGroup;
import vn.intrachat.protocol.WireMessage;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.InetAddress;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GroupCallServiceTest {
    @Test
    void threeMemberOwnerRelayedGroupVideoCallAndLeaveEnd() throws Exception {
        UUID aId = UUID.randomUUID(); // owner
        UUID bId = UUID.randomUUID();
        UUID cId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        ChatGroup group = new ChatGroup(groupId, "Nhóm Test", aId, List.of(aId, bId, cId), 1L);
        InetAddress loopback = InetAddress.getByName("127.0.0.1");

        Map<UUID, CallService> nodes = new HashMap<>();
        Map<UUID, String> names = Map.of(aId, "Alice", bId, "Bob", cId, "Carol");
        Map<UUID, InetAddress> ownerPeers = Map.of(bId, loopback, cId, loopback);

        CallService.Sender routerForA = (peer, type, cid, payload) ->
            nodes.get(peer).handleIncomingGroup(aId, "Alice", loopback,
                new WireMessage(type, UUID.randomUUID(), cid, aId, peer, Instant.now(), payload),
                id -> groupId.equals(id) ? group : null, () -> Map.of(aId, loopback), names.get(peer));
        CallService.Sender routerForB = (peer, type, cid, payload) ->
            nodes.get(peer).handleIncomingGroup(bId, "Bob", loopback,
                new WireMessage(type, UUID.randomUUID(), cid, bId, peer, Instant.now(), payload),
                id -> groupId.equals(id) ? group : null, () -> ownerPeers, names.get(peer));
        CallService.Sender routerForC = (peer, type, cid, payload) ->
            nodes.get(peer).handleIncomingGroup(cId, "Carol", loopback,
                new WireMessage(type, UUID.randomUUID(), cid, cId, peer, Instant.now(), payload),
                id -> groupId.equals(id) ? group : null, () -> ownerPeers, names.get(peer));

        try (CallService a = new CallService(aId, routerForA, false);
             CallService b = new CallService(bId, routerForB, false);
             CallService c = new CallService(cId, routerForC, false)) {
            nodes.put(aId, a);
            nodes.put(bId, b);
            nodes.put(cId, c);

            CountDownLatch aGotBVideo = new CountDownLatch(1);
            CountDownLatch cGotBVideo = new CountDownLatch(1);
            AtomicReference<UUID> cReceivedSender = new AtomicReference<>();

            a.addParticipantVideoListener((sender, img) -> {
                if (bId.equals(sender) && img != null) aGotBVideo.countDown();
            });
            c.addParticipantVideoListener((sender, img) -> {
                if (bId.equals(sender) && img != null) {
                    cReceivedSender.set(sender);
                    cGotBVideo.countDown();
                }
            });

            // Owner A starts group video call
            CallService.CallSession offer = a.startGroupCall(group, "Alice", CallService.Mode.VIDEO, ownerPeers);
            assertTrue(offer.group());
            assertEquals(CallService.State.CONNECTED, offer.state());

            await(() -> b.currentCall() != null && b.currentCall().state() == CallService.State.INCOMING_RINGING);
            await(() -> c.currentCall() != null && c.currentCall().state() == CallService.State.INCOMING_RINGING);

            // Both B and C accept
            b.acceptCall(offer.callId());
            c.acceptCall(offer.callId());

            await(() -> a.currentCall().participants().size() == 3);
            await(() -> b.currentCall().participants().size() == 3);
            await(() -> c.currentCall().participants().size() == 3);

            // B sends a JPEG frame via UDP -> A receives it AND relays it zero-copy to C!
            BufferedImage frame = new BufferedImage(120, 90, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = frame.createGraphics();
            g.setColor(Color.CYAN);
            g.fillRect(0, 0, 120, 90);
            g.dispose();
            b.currentTransport().sendGroupVideoJpeg(bId, VideoCallEngine.encodeJpeg(frame, 0.7f));

            assertTrue(aGotBVideo.await(3, TimeUnit.SECONDS), "Owner A should receive Bob's video frame");
            assertTrue(cGotBVideo.await(3, TimeUnit.SECONDS), "Member C should receive Bob's relayed video frame");
            assertEquals(bId, cReceivedSender.get());

            // Member C leaves -> A and B remain connected with 2 participants
            c.endCall(offer.callId(), "Rời nhóm");
            await(() -> c.currentCall() == null);
            await(() -> a.currentCall().participants().size() == 2);
            await(() -> b.currentCall().participants().size() == 2);

            // Owner A ends call -> B is disconnected too
            a.endCall(offer.callId(), "Kết thúc phòng gọi");
            await(() -> a.currentCall() == null);
            await(() -> b.currentCall() == null);
        }
    }

    @Test
    void nonOwnerMemberInitiatesGroupVoiceCallViaOwnerRelay() throws Exception {
        UUID aId = UUID.randomUUID(); // owner
        UUID bId = UUID.randomUUID();
        UUID cId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        ChatGroup group = new ChatGroup(groupId, "Nhóm Voice", aId, List.of(aId, bId, cId), 1L);
        InetAddress loopback = InetAddress.getByName("127.0.0.1");

        Map<UUID, CallService> nodes = new HashMap<>();
        Map<UUID, String> names = Map.of(aId, "Alice", bId, "Bob", cId, "Carol");
        Map<UUID, InetAddress> ownerPeers = Map.of(bId, loopback, cId, loopback);

        CallService.Sender routerForA = (peer, type, cid, payload) ->
            nodes.get(peer).handleIncomingGroup(aId, "Alice", loopback,
                new WireMessage(type, UUID.randomUUID(), cid, aId, peer, Instant.now(), payload),
                id -> groupId.equals(id) ? group : null, () -> Map.of(aId, loopback), names.get(peer));
        CallService.Sender routerForB = (peer, type, cid, payload) ->
            nodes.get(peer).handleIncomingGroup(bId, "Bob", loopback,
                new WireMessage(type, UUID.randomUUID(), cid, bId, peer, Instant.now(), payload),
                id -> groupId.equals(id) ? group : null, () -> ownerPeers, names.get(peer));
        CallService.Sender routerForC = (peer, type, cid, payload) ->
            nodes.get(peer).handleIncomingGroup(cId, "Carol", loopback,
                new WireMessage(type, UUID.randomUUID(), cid, cId, peer, Instant.now(), payload),
                id -> groupId.equals(id) ? group : null, () -> ownerPeers, names.get(peer));

        try (CallService a = new CallService(aId, routerForA, false);
             CallService b = new CallService(bId, routerForB, false);
             CallService c = new CallService(cId, routerForC, false)) {
            nodes.put(aId, a);
            nodes.put(bId, b);
            nodes.put(cId, c);

            // Non-owner B starts voice call -> Owner A sets up SFU and invites C
            CallService.CallSession offer = b.startGroupCall(group, "Bob", CallService.Mode.VOICE, Map.of(aId, loopback));
            await(() -> b.currentCall() != null && b.currentCall().state() == CallService.State.CONNECTED);
            await(() -> a.currentCall() != null && a.currentCall().state() == CallService.State.INCOMING_RINGING);
            await(() -> c.currentCall() != null && c.currentCall().state() == CallService.State.INCOMING_RINGING);

            a.acceptCall(offer.callId());
            c.acceptCall(offer.callId());

            await(() -> a.currentCall().participants().size() == 3);
            await(() -> b.currentCall().participants().size() == 3);
            await(() -> c.currentCall().participants().size() == 3);

            a.endCall(offer.callId(), "Done");
            await(() -> b.currentCall() == null && c.currentCall() == null);
        }
    }

    @Test
    void pcm16MixerAddsAndClampsWithoutOverflow() {
        byte[] s1 = pcm(10_000, 25_000, -25_000);
        byte[] s2 = pcm(5_000, 20_000, -20_000);
        byte[] mixed = AudioCallEngine.mixPcm16Le(List.of(s1, s2));
        assertEquals(15_000, sampleAt(mixed, 0));
        assertEquals(Short.MAX_VALUE, sampleAt(mixed, 1));
        assertEquals(Short.MIN_VALUE, sampleAt(mixed, 2));
    }

    private static byte[] pcm(int... samples) {
        byte[] out = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            out[i * 2] = (byte) (samples[i] & 0xFF);
            out[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
        }
        return out;
    }

    private static short sampleAt(byte[] buf, int index) {
        return (short) ((buf[index * 2] & 0xFF) | (buf[index * 2 + 1] << 8));
    }

    private static void await(java.util.function.BooleanSupplier cond) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (cond.getAsBoolean()) return;
            Thread.sleep(20);
        }
        fail("Timed out waiting for group call condition");
    }
}
