package ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import model.mock.MockChatService;
import ui.frames.MainChatFrame;
import listener.LiveChatAdapter;
import vn.intrachat.application.LiveChatService;
import vn.intrachat.security.DeviceCertificateFactory;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** Live M2 must not expose Demo Group/File/Image/Video/Sound actions. */
class LiveFutureFeatureIsolationTest {
    private static JButton find(Container parent,String caption){
        for(Component component:parent.getComponents()){
            if(component instanceof JButton b
                &&(caption.equals(b.getText())
                    ||caption.equals(b.getAccessibleContext().getAccessibleName())
                    ||caption.equals(b.getToolTipText())))return b;
            if(component instanceof Container children){
                JButton found=find(children,caption);
                if(found!=null)return found;
            }
        }
        return null;
    }
    @Test void liveFutureControlsHiddenWhileDemoControlsRemainVisible()throws Exception{
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),"Windows Swing desktop required");
        var identity=DeviceCertificateFactory.create(UUID.randomUUID());
        try(var service=new LiveChatService(identity);
            var live=new LiveChatAdapter(identity,"Live",service,()->{});
            var demo=new MockChatService()){
            AtomicReference<MainChatFrame> liveFrame=new AtomicReference<>();
            AtomicReference<MainChatFrame> demoFrame=new AtomicReference<>();
            SwingUtilities.invokeAndWait(()->{
                liveFrame.set(new MainChatFrame(live.self(),live));
                liveFrame.get().setVisible(true);
                demoFrame.set(new MainChatFrame(
                    new model.ChatUser("demo-self","Demo",true),demo));
                demoFrame.get().setVisible(true);
            });
            try{
                SwingUtilities.invokeAndWait(()->{
                    JButton liveGroup=find(liveFrame.get(),"Tạo nhóm mới");
                    JButton demoGroup=find(demoFrame.get(),"Tạo nhóm mới");
                    JButton liveFile=find(liveFrame.get(),"Gửi tệp");
                    JButton demoFile=find(demoFrame.get(),"Gửi tệp");
                    assertNotNull(liveGroup);assertNotNull(demoGroup);
                    assertNotNull(liveFile);assertNotNull(demoFile);
                    assertFalse(liveGroup.isShowing());
                    assertFalse(liveFile.isShowing());
                    assertTrue(demoGroup.isShowing(),"Demo group creation remains visible");
                    assertTrue(demoFile.isShowing(),"Demo media toolbar remains visible");
                });
                assertThrows(java.util.concurrent.ExecutionException.class,()->
                    live.createGroup("Forbidden in M2",java.util.List.of(identity.deviceId().toString()))
                        .toCompletableFuture().get(3,java.util.concurrent.TimeUnit.SECONDS));
            }finally{
                SwingUtilities.invokeAndWait(()->{
                    liveFrame.get().dispose();
                    demoFrame.get().dispose();
                });
            }
        }
    }
}
