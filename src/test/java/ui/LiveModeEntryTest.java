package ui;

import model.mock.MockChatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ui.frames.LoginFrame;
import ui.dialogs.LanConnectionDialog;
import vn.intrachat.security.*;
import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Live entry is separate from Demo password and has explicit security wording. */
class LiveModeEntryTest {
    @TempDir Path root;
    private static <T extends Component> T find(Container parent,Class<T> kind,String text){
        for(Component c:parent.getComponents()){
            if(kind.isInstance(c)){
                if(c instanceof AbstractButton b && text.equals(b.getText()))return kind.cast(c);
                if(c instanceof JLabel l && text.equals(l.getText()))return kind.cast(c);
            }
            if(c instanceof Container nested){
                T found=find(nested,kind,text);
                if(found!=null)return found;
            }
        }
        return null;
    }
    private static <T extends Component> int count(Container parent,Class<T> kind){
        int n=0;
        for(Component c:parent.getComponents()){
            if(kind.isInstance(c))n++;
            if(c instanceof Container nested)n+=count(nested,kind);
        }
        return n;
    }
    @Test void demoLauncherExplicitlyExplainsSeparateLivePathWithoutDemoPassword()throws Exception{
        assumeFalse(GraphicsEnvironment.isHeadless());
        AtomicInteger clicked=new AtomicInteger();
        try(var mock=new MockChatService()){
            final LoginFrame[] window=new LoginFrame[1];
            try{
                SwingUtilities.invokeAndWait(()->{
                    window[0]=new LoginFrame(mock,clicked::incrementAndGet);
                    window[0].setVisible(true);
                    JButton lan=find(window[0],JButton.class,"Chat trong LAN (TLS 1.3)");
                    assertNotNull(lan);
                    assertTrue(window[0].getTitle().contains("Demo"),
                        "Launcher must identify the Demo credential form explicitly");
                    assertTrue(lan.getToolTipText().contains("không cần tài khoản Demo"),
                        "Live entry must explain that no Demo password is required");
                    assertEquals(1,count(window[0],JPasswordField.class));
                    lan.doClick();
                    assertEquals(1,clicked.get());
                });
            }finally{
                SwingUtilities.invokeAndWait(()->{
                    if(window[0]!=null)window[0].dispose();
                });
            }
        }
    }
    @Test void realSecureConnectionDialogContainsNoDemoLoginOrAutoTrustedSession()throws Exception{
        assumeFalse(GraphicsEnvironment.isHeadless());
        var identity=DeviceCertificateFactory.create(UUID.randomUUID());
        try(var demo=new MockChatService()){
            final LoginFrame[] launcher=new LoginFrame[1];
            final LanConnectionDialog[] dialog=new LanConnectionDialog[1];
            try{
                SwingUtilities.invokeAndWait(()->{
                    launcher[0]=new LoginFrame(demo,()->{});
                    launcher[0].setVisible(true);
                    dialog[0]=new LanConnectionDialog(launcher[0],identity,
                        new PinnedPeerStore(root.resolve("pins.bin")));
                    dialog[0].setVisible(true);
                    assertEquals(0,count(dialog[0],JPasswordField.class),
                        "Live must never ask for demo password");
                    assertNotNull(find(dialog[0],JButton.class,"Bắt đầu lắng nghe"));
                    assertNotNull(find(dialog[0],JButton.class,"Kết nối & xác minh"));
                    assertFalse(launcher[0].isEnabled(),
                        "Demo and Live cannot be used concurrently");
                });
            }finally{
                SwingUtilities.invokeAndWait(()->{
                    if(dialog[0]!=null)dialog[0].dispose();
                    if(launcher[0]!=null)launcher[0].dispose();
                });
            }
        }
    }
}
