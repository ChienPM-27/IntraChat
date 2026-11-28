package ui.components;

import org.bytedeco.javacv.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import model.MessageType;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_MPEG4;

class VideoPlayerPanelTest {
    @TempDir Path folder;

    @Test void embeddedPlayerDecodesLocalMp4WithoutExternalApplication() throws Exception {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"));
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path path = folder.resolve("video.mp4");
        try (var recorder = new FFmpegFrameRecorder(path.toFile(), 96, 64);
             var converter = new Java2DFrameConverter()) {
            recorder.setFormat("mp4");
            recorder.setVideoCodec(AV_CODEC_ID_MPEG4);
            recorder.setFrameRate(10);
            recorder.start();
            BufferedImage frame = new BufferedImage(96, 64, BufferedImage.TYPE_3BYTE_BGR);
            Graphics2D g = frame.createGraphics();
            g.setColor(Color.ORANGE); g.fillRect(0,0,96,64); g.dispose();
            for (int i = 0; i < 6; i++) recorder.record(converter.convert(frame));
            recorder.stop();
        }
        var attachment = MediaFiles.inspect(path, MessageType.VIDEO);
        var notices = new CopyOnWriteArrayList<String>();
        var dialog = new AtomicReference<VideoPlayerPanel>();
        SwingUtilities.invokeAndWait(() -> {
            var media = new MessageMedia(Runnable::run, new JPanel(), notices::add);
            var id = java.util.UUID.randomUUID();
            var chatId = java.util.UUID.randomUUID();
            var sender = java.util.UUID.randomUUID();
            var now = java.time.Instant.now();
            var transfer = new vn.intrachat.application.FileTransferService.Transfer(id, chatId, sender,
                java.util.UUID.randomUUID(), attachment.fileName(), attachment.sizeBytes(), 0,
                vn.intrachat.application.FileTransferService.State.TRANSFERRING, path, null, now, MessageType.VIDEO);
            var message = new model.ChatMessage(id.toString(), chatId.toString(), sender.toString(), now,
                model.MessageContent.media(MessageType.VIDEO, attachment), model.DeliveryState.SENDING);
            var bubble = new MessageBubblePanel(message, true, false, "Sender", media, null, transfer, (a,b)->{});
            dialog.set(find(bubble, VideoPlayerPanel.class));
            assertNotNull(dialog.get());
            JPanel chat = new JPanel();
            chat.add(bubble);
            assertTrue(SwingUtilities.isDescendingFrom(dialog.get(), bubble));
            assertNull(SwingUtilities.getWindowAncestor(dialog.get()));
            find(dialog.get(), JButton.class).doClick(0);
        });
        try {
            long end = System.nanoTime() + 10_000_000_000L;
            boolean[] result = new boolean[2];
            do {
                SwingUtilities.invokeAndWait(() -> {
                    result[0] = dialog.get().hasVideoFrame();
                    result[1] = dialog.get().isPlaying();
                });
                if (!result[1]) break;
                Thread.sleep(30);
            } while (System.nanoTime() < end);
            assertTrue(result[0], "Decoded frame must reach the Swing player: " + notices);
            assertFalse(result[1], "Playback must finish");
            assertTrue(notices.isEmpty(), notices.toString());
        } finally {
            SwingUtilities.invokeAndWait(dialog.get()::stopDecoding);
        }
    }
    @Test void compressedAudioUsesEmbeddedControlsForBothSides() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var media = new MessageMedia(Runnable::run, new JPanel(), s -> {})) {
                for (String extension : new String[]{"mp3", "m4a"}) {
                    var attachment = new model.Attachment(folder.resolve("sample." + extension),
                        "sample." + extension, 1, 0, 0, 0);
                    for (boolean mine : new boolean[]{true, false}) {
                        var panel = media.render(model.MessageContent.media(MessageType.SOUND, attachment), mine);
                        assertEquals("messenger-audio-player", panel.getName());
                        assertEquals("audio-play-pause", find(panel, JButton.class).getName());
                    }
                }
            }
        });
    }

    private static <T extends Component> T find(Component root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof Container container) for (Component child : container.getComponents()) {
            T found = find(child, type);
            if (found != null) return found;
        }
        return null;
    }
}
