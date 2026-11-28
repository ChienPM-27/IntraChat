package ui.components;

import model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.intrachat.application.FileTransferService;
import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MediaSaveButtonTest {
    @TempDir Path folder;

    @Test void completedTransfersAndHistoryExportExactBytesToSelectedPath() throws Exception {
        for (MessageType type : List.of(MessageType.IMAGE, MessageType.VIDEO, MessageType.SOUND, MessageType.FILE)) {
            String name = switch (type) {
                case IMAGE -> "ảnh.png"; case VIDEO -> "video.mp4"; case SOUND -> "ghi âm.mp3"; default -> "tệp.bin";
            };
            // Fixture tests export bytes, not decoding; use valid PNG for thumbnail.
            Path source = folder.resolve(name);
            if (type == MessageType.IMAGE)
                javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(8,8,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",source.toFile());
            else Files.write(source,new byte[]{1,2,3,4});
            byte[] original = Files.readAllBytes(source);
            Path custom = Files.createDirectory(folder.resolve(type.name()));
            UUID a=UUID.randomUUID(), b=UUID.randomUUID(), id=UUID.randomUUID(), cid=UUID.randomUUID();
            var time=Instant.now();
            var attachment=new Attachment(source,name,original.length,0,0,0);
            var message=new ChatMessage(id.toString(),cid.toString(),a.toString(),time,
                MessageContent.media(type,attachment),DeliveryState.DELIVERED);
            var complete=new FileTransferService.Transfer(id,cid,a,b,name,original.length,original.length,
                FileTransferService.State.COMPLETED,source,null,time);
            var pending=new FileTransferService.Transfer(id,cid,a,b,name,original.length,0,
                FileTransferService.State.WAITING_ACCEPT,null,null,time);
            var notices=new CopyOnWriteArrayList<String>();
            var chosen=new AtomicReference<Path>(custom.resolve("bản sao-"+name));
            var media=new AtomicReference<MessageMedia>();
            SwingUtilities.invokeAndWait(()->{
                media.set(new MessageMedia(Runnable::run,new JPanel(),notices::add, selected->{
                    assertEquals(source.toAbsolutePath(),selected.path());
                    return chosen.get();
                }));
                var unavailable=new MessageBubblePanel(message,false,true,"A",media.get(),null,pending,(action,uuid)->{});
                assertNull(save(unavailable));
                var live=new MessageBubblePanel(message,false,true,"A",media.get(),null,complete,(action,uuid)->{});
                assertNotNull(save(live));
                save(live).doClick(0);
            });
            SwingUtilities.invokeAndWait(()->{});
            assertArrayEquals(original,Files.readAllBytes(chosen.get()));
            assertArrayEquals(original,Files.readAllBytes(source));
            assertTrue(notices.stream().anyMatch(n->n.startsWith("Đã lưu tệp: ")));
            Path historyCopy=custom.resolve("lịch sử-"+name);
            chosen.set(historyCopy);
            SwingUtilities.invokeAndWait(()->{
                var history=new MessageBubblePanel(message,true,false,"A",media.get());
                assertNotNull(save(history));
                save(history).doClick(0);
            });
            SwingUtilities.invokeAndWait(()->{});
            assertArrayEquals(original,Files.readAllBytes(historyCopy));
            // Choosing an existing target must not overwrite it.
            Files.write(historyCopy,new byte[]{99});
            SwingUtilities.invokeAndWait(()->{
                var history=new MessageBubblePanel(message,true,false,"A",media.get());
                save(history).doClick(0);
            });
            SwingUtilities.invokeAndWait(()->{});
            assertArrayEquals(new byte[]{99},Files.readAllBytes(historyCopy));
            assertTrue(notices.stream().anyMatch(n->n.contains("đã tồn tại")));
            chosen.set(null);
            int count=notices.size();
            SwingUtilities.invokeAndWait(()->{
                var history=new MessageBubblePanel(message,true,false,"A",media.get());
                save(history).doClick(0);
            });
            SwingUtilities.invokeAndWait(()->{});
            assertEquals(count,notices.size(),"Cancel must not export");
            SwingUtilities.invokeAndWait(media.get()::close);
        }
    }

    private static JButton save(Component root) {
        if(root instanceof JButton button && "media-save-as".equals(button.getName())) return button;
        if(root instanceof Container container) for(Component child:container.getComponents()){
            JButton found=save(child);
            if(found!=null)return found;
        }
        return null;
    }
}
