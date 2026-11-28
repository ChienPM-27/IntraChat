package vn.intrachat.transport;

import org.junit.jupiter.api.Test;
import vn.intrachat.application.VideoCallEngine;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.InetAddress;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CallMediaTransportTest {
    @Test
    void streamsAudioAndChunkedJpegVideoOverUdp() throws Exception {
        UUID callId = UUID.randomUUID();
        InetAddress loopback = InetAddress.getByName("127.0.0.1");

        try (CallMediaTransport a = new CallMediaTransport(callId);
             CallMediaTransport b = new CallMediaTransport(callId)) {
            a.setRemote(loopback, b.localPort());
            b.setRemote(loopback, a.localPort());

            CountDownLatch audioLatch = new CountDownLatch(1);
            CountDownLatch videoLatch = new CountDownLatch(1);
            AtomicReference<byte[]> receivedAudio = new AtomicReference<>();
            AtomicReference<byte[]> receivedVideo = new AtomicReference<>();

            b.startReceiving(
                pcm -> { receivedAudio.set(pcm); audioLatch.countDown(); },
                jpeg -> { receivedVideo.set(jpeg); videoLatch.countDown(); }
            );

            byte[] pcm = new byte[640];
            for (int i = 0; i < pcm.length; i++) pcm[i] = (byte) (i * 7);
            a.sendAudio(pcm, pcm.length);

            BufferedImage img = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.BLUE);
            g.fillRect(0, 0, 320, 240);
            g.setColor(Color.YELLOW);
            g.fillOval(60, 40, 200, 160);
            g.dispose();
            byte[] jpeg = VideoCallEngine.encodeJpeg(img, 0.75f);
            assertTrue(jpeg.length > CallMediaTransport.MAX_VIDEO_CHUNK, "JPEG should span multiple UDP chunks");
            a.sendVideoJpeg(jpeg);

            assertTrue(audioLatch.await(3, TimeUnit.SECONDS), "Audio UDP packet not received");
            assertTrue(videoLatch.await(3, TimeUnit.SECONDS), "Chunked video UDP frame not reassembled");
            assertArrayEquals(pcm, receivedAudio.get());
            assertArrayEquals(jpeg, receivedVideo.get());
        }
    }

    @Test
    void ignoresPacketsFromDifferentCallId() throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        try (CallMediaTransport sender = new CallMediaTransport(UUID.randomUUID());
             CallMediaTransport receiver = new CallMediaTransport(UUID.randomUUID())) {
            sender.setRemote(loopback, receiver.localPort());
            CountDownLatch unexpected = new CountDownLatch(1);
            receiver.startReceiving(pcm -> unexpected.countDown(), jpeg -> unexpected.countDown());
            sender.sendAudio(new byte[320], 320);
            assertFalse(unexpected.await(250, TimeUnit.MILLISECONDS));
        }
    }
}
