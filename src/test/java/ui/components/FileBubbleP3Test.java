package ui.components;

import model.*;
import org.junit.jupiter.api.Test;
import vn.intrachat.application.FileTransferService;
import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class FileBubbleP3Test {
    static void buttons(Component root,List<String> titles){
        if(root instanceof JButton b)titles.add(b.getText());
        if(root instanceof Container c)for(Component child:c.getComponents())buttons(child,titles);
    }
    @Test void incomingOfferHasAcceptRejectCancelControls()throws Exception{
        AtomicReference<List<String>> result=new AtomicReference<>();
        SwingUtilities.invokeAndWait(()->{
            UUID a=UUID.randomUUID(),b=UUID.randomUUID(),cid=UUID.randomUUID(),id=UUID.randomUUID();
            var transfer=new FileTransferService.Transfer(id,cid,a,b,
                "Bản thử.bin",512,0,FileTransferService.State.WAITING_ACCEPT,null,null,Instant.now());
            var message=new ChatMessage(id.toString(),cid.toString(),a.toString(),transfer.sentAt(),
                MessageContent.media(MessageType.FILE,new Attachment(Path.of("incoming"),"Bản thử.bin",512,0,0,0)),
                DeliveryState.SENDING);
            try(var media=new MessageMedia(Runnable::run,new JPanel(),text->{})){
                var bubble=new MessageBubblePanel(message,false,false,"Sender",media,
                    null,transfer,(action,uuid)->{});
                List<String> titles=new ArrayList<>();
                buttons(bubble,titles);result.set(titles);
            }
        });
        assertTrue(result.get().contains("Nhận / Chọn nơi lưu"));
        assertTrue(result.get().contains("Từ chối"));
        assertTrue(result.get().contains("Hủy"));
    }
    @Test void completeHasOpenControlNoReject()throws Exception {
        AtomicReference<List<String>> result=new AtomicReference<>();
        SwingUtilities.invokeAndWait(()->{
            UUID a=UUID.randomUUID(),b=UUID.randomUUID(),cid=UUID.randomUUID(),id=UUID.randomUUID();
            var transfer=new FileTransferService.Transfer(id,cid,a,b,
                "saved.bin",1024,1024,FileTransferService.State.COMPLETED,
                Path.of("saved.bin"),null,Instant.now());
            var message=new ChatMessage(id.toString(),cid.toString(),a.toString(),transfer.sentAt(),
                MessageContent.media(MessageType.FILE,new Attachment(Path.of("saved.bin"),"saved.bin",1024,0,0,0)),
                DeliveryState.SENDING);
            try(var media=new MessageMedia(Runnable::run,new JPanel(),text->{})){
                var bubble=new MessageBubblePanel(message,false,false,"Sender",media,
                    null,transfer,(action,uuid)->{});
                List<String> titles=new ArrayList<>();buttons(bubble,titles);result.set(titles);
            }
        });
        assertTrue(result.get().contains("Mở tệp / vị trí"));
        assertFalse(result.get().contains("Từ chối"));
        assertFalse(result.get().contains("Hủy"));
    }
}
