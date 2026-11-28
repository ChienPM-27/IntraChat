package ui.components;

import listener.ChatClientListener;
import listener.LiveChatAdapter;
import model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.intrachat.application.*;
import vn.intrachat.transport.NetworkConfig;

import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.ServerSocket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class MediaComposerEndToEndTest {
    @TempDir Path dir;

    private static void until(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(20);
        }
        fail("Timed out waiting for media event");
    }
    private static List<JButton> buttons(Component root) {
        List<JButton> result = new ArrayList<>();
        if (root instanceof JButton button) result.add(button);
        if (root instanceof Container container)
            for (Component child : container.getComponents()) result.addAll(buttons(child));
        return result;
    }
    private static JButton button(Component root, String name) {
        return buttons(root).stream().filter(b -> name.equals(b.getAccessibleContext().getAccessibleName()))
            .findFirst().orElseThrow();
    }

    @Test void oneMicrophoneAndDistinctAudioToolRespectAvailability() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var composer = new ComposerPanel(Runnable::run, () -> "chat", (c,m) -> {}, s -> {})) {
                assertEquals(1, buttons(composer).stream().filter(b ->
                    b.getIcon() instanceof ChatIcon icon && icon.kind() == ChatIcon.Kind.MIC).count());
                var audio = button(composer, "Chọn âm thanh");
                var mic = button(composer, "Ghi âm WAV");
                assertEquals(ChatIcon.Kind.AUDIO, ((ChatIcon)audio.getIcon()).kind());
                assertTrue(audio.isEnabled()); assertTrue(mic.isEnabled());
                composer.setMediaEnabled(false, "Nhóm chỉ hỗ trợ text/emoji");
                assertFalse(audio.isEnabled()); assertFalse(mic.isEnabled());
                assertTrue(audio.getToolTipText().contains("Nhóm"));
                assertTrue(button(composer, "Gửi").isEnabled());
                composer.setMediaEnabled(true);
                assertTrue(audio.isEnabled()); assertTrue(mic.isEnabled());
            }
        });
    }

    private static class Listener implements ChatClientListener {
        final Map<UUID, FileTransferService.Transfer> transfers = new ConcurrentHashMap<>();
        final Map<String, ChatMessage> messages = new ConcurrentHashMap<>();
        volatile ChatSnapshot snapshot;
        public void onDirectoryChanged(ChatSnapshot s) { snapshot=s; }
        public void onMessageReceived(ChatMessage m) { messages.put(m.id(),m); }
        public void onDeliveryStateChanged(String c,String m,DeliveryState s) {}
        public void onConnectionError(String m) {}
        public void onFileTransfer(FileTransferService.Transfer t) { transfers.put(t.transferId(),t); }
    }

    @Test void validatedComposerMediaThroughRealTcpAdapterAndReceivedBubble() throws Exception {
        Path png=dir.resolve("photo.png"), wav=dir.resolve("voice.wav"), mp4=dir.resolve("video.mp4"), bin=dir.resolve("file.bin");
        assertTrue(ImageIO.write(new BufferedImage(64,32,BufferedImage.TYPE_INT_RGB),"png",png.toFile()));
        byte[] pcm=new byte[16000];
        try (var audio=new AudioInputStream(new ByteArrayInputStream(pcm), new AudioFormat(8000,16,1,true,false),8000)) {
            AudioSystem.write(audio,AudioFileFormat.Type.WAVE,wav.toFile());
        }
        // Signature-valid fixture for transfer/validation, not a playable video.
        Files.write(mp4,new byte[]{0,0,0,20,'f','t','y','p','i','s','o','m'});
        Files.write(bin,new byte[]{1,2,3,4});
        var a=LocalProfile.loadOrCreate(dir.resolve("A"));
        var b=LocalProfile.loadOrCreate(dir.resolve("B"));
        int port; try (var reserve=new ServerSocket(0)) { port=reserve.getLocalPort(); }
        try (var sa=new LiveChatService(a); var sb=new LiveChatService(b);
             var ca=new LanCoordinator(a,i->CompletableFuture.completedFuture(true),p->{try{sa.attach(p.session(),p.name());}catch(Exception e){throw new IllegalStateException(e);}});
             var cb=new LanCoordinator(b,i->CompletableFuture.completedFuture(true),p->{try{sb.attach(p.session(),p.name());}catch(Exception e){throw new IllegalStateException(e);}});
             var aa=new LiveChatAdapter(a,a.displayName(),sa,()->{});
             var ab=new LiveChatAdapter(b,b.displayName(),sb,()->{})) {
            var left=new Listener(); var right=new Listener();
            aa.connect(left); ab.connect(right);
            ca.startListening(new NetworkConfig("127.0.0.1",port));
            cb.connect(new NetworkConfig("127.0.0.1",port)).get(8,TimeUnit.SECONDS);
            until(()->left.snapshot!=null&&!left.snapshot.conversations().isEmpty()&&right.snapshot!=null&&!right.snapshot.conversations().isEmpty());
            String conversation=left.snapshot.conversations().getFirst().id();
            AtomicReference<CompletableFuture<ChatMessage>> send=new AtomicReference<>();
            AtomicReference<ComposerPanel> composer=new AtomicReference<>();
            List<String> notices=new CopyOnWriteArrayList<>();
            SwingUtilities.invokeAndWait(()->composer.set(new ComposerPanel(Runnable::run,()->conversation,
                (chat,content)->send.set(aa.sendMessage(chat,content).toCompletableFuture()),notices::add)));
            try {
                for(var sample: Map.of(MessageType.IMAGE,png,MessageType.SOUND,wav,MessageType.VIDEO,mp4,MessageType.FILE,bin).entrySet()){
                    send.set(null);
                    SwingUtilities.invokeAndWait(()->composer.get().prepareMedia(conversation,sample.getValue(),sample.getKey()));
                    until(()->send.get()!=null);
                    ChatMessage offered=send.get().get(8,TimeUnit.SECONDS);
                    UUID id=UUID.fromString(offered.id());
                    until(()->right.transfers.containsKey(id)&&right.messages.containsKey(offered.id()));
                    var incoming=right.transfers.get(id);
                    assertEquals(FileTransferService.State.WAITING_ACCEPT,incoming.state());
                    assertEquals(sample.getKey(),incoming.messageType());
                    ChatMessage pending=right.messages.get(offered.id());
                    Path destination=dir.resolve("received-"+sample.getValue().getFileName());
                    SwingUtilities.invokeAndWait(()->{
                        try(var media=new MessageMedia(Runnable::run,new JPanel(),s->{})){
                            var bubble=new MessageBubblePanel(pending,false,false,"A",media,null,incoming,(action,uuid)->{});
                            assertFalse(buttons(bubble).stream().anyMatch(x->x.getText().equals("Nhận / Chọn nơi lưu")));
                        }
                    });
                    ab.acceptFile(id.toString(),destination);
                    until(()->left.transfers.get(id).state()==FileTransferService.State.COMPLETED
                        && right.transfers.get(id).state()==FileTransferService.State.COMPLETED
                        && right.messages.get(offered.id()).state()==DeliveryState.DELIVERED);
                    assertArrayEquals(Files.readAllBytes(sample.getValue()),Files.readAllBytes(destination));
                    assertEquals(destination,right.messages.get(offered.id()).content().attachment().path());
                    var complete=right.transfers.get(id);
                    AtomicReference<MessageBubblePanel> bubble=new AtomicReference<>();
                    AtomicReference<MessageMedia> media=new AtomicReference<>();
                    SwingUtilities.invokeAndWait(()->{
                        media.set(new MessageMedia(Runnable::run,new JPanel(),notices::add));
                        // Deliberately pass the original pending message: use the verified completed path.
                        bubble.set(new MessageBubblePanel(pending,false,false,"A",media.get(),null,complete,(action,uuid)->{}));
                    });
                    SwingUtilities.invokeAndWait(()->{
                        assertTrue(buttons(bubble.get()).stream().anyMatch(x->x.getText().equals("Mở tệp / vị trí")));
                        assertFalse(buttons(bubble.get()).stream().anyMatch(x->x.getText().equals("Nhận / Chọn nơi lưu")));
                        if(sample.getKey()==MessageType.IMAGE) {
                            assertTrue(buttons(bubble.get()).stream().anyMatch(x->x.getIcon()!=null && x.isEnabled() &&
                                x.getAccessibleContext().getAccessibleName().startsWith("Xem ảnh")));
                        }
                        if(sample.getKey()==MessageType.SOUND) assertNotNull(button(bubble.get(),"Phát tin thoại"));
                        media.get().close();
                    });
                }
                assertTrue(notices.stream().allMatch(n->n.equals("Đang kiểm tra tệp…")),notices.toString());
            } finally { SwingUtilities.invokeAndWait(composer.get()::close); }
        }
    }
}
