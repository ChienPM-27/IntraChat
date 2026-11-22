package ui.dialogs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import vn.intrachat.domain.PeerIdentity;

import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Current Swing desktop GUI, not the removed M1 NetworkPanel/launcher.
 * Real loopback sockets; approval is a test-driven UI button click only.
 */
class LanDiagnosticsDialogTest {
    private static <T> T edt(Supplier<T> work)throws Exception{
        AtomicReference<T> value=new AtomicReference<>();
        AtomicReference<Throwable> problem=new AtomicReference<>();
        SwingUtilities.invokeAndWait(()->{
            try{value.set(work.get());}catch(Throwable ex){problem.set(ex);}
        });
        if(problem.get()!=null)throw new AssertionError("EDT failed",problem.get());
        return value.get();
    }
    private static void action(Runnable runnable)throws Exception{
        edt(()->{runnable.run();return true;});
    }
    private static <T extends Component> T find(Container container,String name){
        for(Component item:container.getComponents()){
            if(name.equals(item.getName()))return (T)item;
            if(item instanceof Container children){
                T found=find(children,name);
                if(found!=null)return found;
            }
        }
        return null;
    }
    private static JButton button(JDialog dialog,String name){
        JButton b=find(dialog.getContentPane(),name);
        assertNotNull(b,name+" missing");
        return b;
    }
    private static JTextField field(LanDiagnosticsDialog dialog,String name){
        JTextField f=find(dialog.getContentPane(),name);
        assertNotNull(f,name+" missing");
        return f;
    }
    private static int freePort()throws Exception{
        try(ServerSocket socket=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            return socket.getLocalPort();
        }
    }
    private static void await(Supplier<Boolean> condition,String description)throws Exception{
        for(int attempt=0;attempt<180;attempt++){
            if(edt(condition))return;
            Thread.sleep(25);
        }
        assertTrue(edt(condition),"Timed out waiting: "+description);
    }

    @Test void pingDisabledAndPlaintextWarningAlwaysVisibleBeforeConsent()throws Exception{
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),"Windows GUI required");
        var d=edt(()->new LanDiagnosticsDialog(null,
            new PeerIdentity(UUID.randomUUID(),"Diagnostic user")));
        try{
            assertFalse(edt(()->button(d,"m1-ping").isEnabled()));
            assertFalse(edt(()->button(d,"m1-reconnect").isEnabled()));
            assertFalse(edt(()->button(d,"m1-disconnect").isEnabled()));
            assertNotNull(edt(()->find(d.getContentPane(),"m1-untrusted-warning")));
            assertTrue(edt(()->((JLabel)find(d.getContentPane(),"m1-untrusted-warning"))
                .getText().contains("KHÔNG MÃ HÓA")));
        }finally{action(d::dispose);}
    }

    @Test void realSwingInboundConsentThenTwoWayPingAndDisconnect()throws Exception{
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),"Windows GUI required");
        int port=freePort();
        var receiver=edt(()->new LanDiagnosticsDialog(null,new PeerIdentity(UUID.randomUUID(),"A")));
        var sender=edt(()->new LanDiagnosticsDialog(null,new PeerIdentity(UUID.randomUUID(),"B")));
        try{
            action(()->{receiver.setVisible(true);sender.setVisible(true);
                field(receiver,"m1-local-port").setText(Integer.toString(port));
                field(sender,"m1-remote-port").setText(Integer.toString(port));
                button(receiver,"m1-listen").doClick();});
            await(()->button(receiver,"m1-stop").isEnabled(),"M1 receiver listen");
            action(()->button(sender,"m1-connect").doClick());
            await(()->{
                for(Window w:Window.getWindows()){
                    if(w instanceof JDialog popup&&popup.isShowing()
                        &&"m1-approval-dialog".equals(popup.getName())
                        &&popup.getOwner()==receiver)return true;
                }
                return false;
            },"inbound human approval popup");
            assertFalse(edt(()->button(sender,"m1-ping").isEnabled()),
                "Cannot send even technical PING before ACCEPT+READY");
            long began=System.nanoTime();
            action(()->assertTrue(SwingUtilities.isEventDispatchThread()));
            assertTrue((System.nanoTime()-began)<500_000_000L,"EDT blocked while approval pending");
            action(()->{
                for(Window w:Window.getWindows()){
                    if(w instanceof JDialog popup&&popup.isShowing()
                        &&"m1-approval-dialog".equals(popup.getName())
                        &&popup.getOwner()==receiver){
                        JButton accept=find(popup.getContentPane(),"m1-approve");
                        assertNotNull(accept);
                        accept.doClick();
                        return;
                    }
                }
                fail("Approval dialog disappeared before user consent");
            });
            await(()->button(sender,"m1-ping").isEnabled(),"sender READY Ping");
            await(()->button(receiver,"m1-ping").isEnabled(),"receiver READY Ping");
            action(()->button(sender,"m1-ping").doClick());
            await(()->((JLabel)find(sender.getContentPane(),"m1-status"))
                .getText().contains("PONG"),"sender Pong");
            action(()->button(receiver,"m1-ping").doClick());
            await(()->((JLabel)find(receiver.getContentPane(),"m1-status"))
                .getText().contains("PONG"),"receiver Pong");
            action(()->button(sender,"m1-disconnect").doClick());
            await(()->!button(sender,"m1-ping").isEnabled()
                &&!button(receiver,"m1-ping").isEnabled(),"disconnect disables Ping");
        }finally{action(()->{sender.dispose();receiver.dispose();});}
    }

    private static JDialog approvalFor(LanDiagnosticsDialog parent){
        for(Window w:Window.getWindows()){
            if(w instanceof JDialog popup&&popup.isShowing()
                &&"m1-approval-dialog".equals(popup.getName())
                &&popup.getOwner()==parent)return popup;
        }
        return null;
    }

    @Test void rejectMustRequireExplicitGuiReconnectBeforeAccept()throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),"Windows GUI required");
        int port=freePort();
        var receiver=edt(()->new LanDiagnosticsDialog(null,new PeerIdentity(UUID.randomUUID(),"A")));
        var sender=edt(()->new LanDiagnosticsDialog(null,new PeerIdentity(UUID.randomUUID(),"B")));
        try{
            action(()->{receiver.setVisible(true);sender.setVisible(true);
                field(receiver,"m1-local-port").setText(Integer.toString(port));
                field(sender,"m1-remote-port").setText(Integer.toString(port));
                button(receiver,"m1-listen").doClick();});
            await(()->button(receiver,"m1-stop").isEnabled(),"listening");
            action(()->button(sender,"m1-connect").doClick());
            await(()->approvalFor(receiver)!=null,"rejection dialog");
            action(()->button(approvalFor(receiver),"m1-reject").doClick());
            await(()->button(sender,"m1-reconnect").isEnabled(),"manual reconnect enabled");
            assertFalse(edt(()->button(sender,"m1-ping").isEnabled()));
            Thread.sleep(200);
            assertNull(edt(()->approvalFor(receiver)),"No automatic retry prompt");
            action(()->button(sender,"m1-reconnect").doClick());
            await(()->approvalFor(receiver)!=null,"second attempt requires explicit reconnect");
            action(()->button(approvalFor(receiver),"m1-approve").doClick());
            await(()->button(sender,"m1-ping").isEnabled(),"accepted manual retry READY");
            await(()->button(receiver,"m1-ping").isEnabled(),"receiver accepted manual retry READY");
        }finally{action(()->{sender.dispose();receiver.dispose();});}
    }

    @Test void guiCancelWhileAwaitingConsentNeverEnablesPing()throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),"Windows GUI required");
        int port=freePort();
        var receiver=edt(()->new LanDiagnosticsDialog(null,new PeerIdentity(UUID.randomUUID(),"A")));
        var sender=edt(()->new LanDiagnosticsDialog(null,new PeerIdentity(UUID.randomUUID(),"B")));
        try{
            action(()->{receiver.setVisible(true);sender.setVisible(true);
                field(receiver,"m1-local-port").setText(Integer.toString(port));
                field(sender,"m1-remote-port").setText(Integer.toString(port));
                button(receiver,"m1-listen").doClick();});
            await(()->button(receiver,"m1-stop").isEnabled(),"listen before cancellation");
            action(()->button(sender,"m1-connect").doClick());
            await(()->approvalFor(receiver)!=null,"unanswered prompt");
            assertFalse(edt(()->button(sender,"m1-ping").isEnabled()));
            action(()->button(sender,"m1-cancel").doClick());
            await(()->button(sender,"m1-connect").isEnabled(),"cancelled connect returns control");
            assertFalse(edt(()->button(sender,"m1-ping").isEnabled()));
            assertTrue(edt(()->button(sender,"m1-reconnect").isEnabled()),
                "Only manual user action can retry");
        }finally{action(()->{sender.dispose();receiver.dispose();});}
    }

    @Test void fiveGuiOpenCloseCyclesLeaveNoOpenWindows()throws Exception{
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),"Windows GUI required");
        for(int cycle=0;cycle<5;cycle++){
            int port=freePort();
            var dialog=edt(()->new LanDiagnosticsDialog(null,
                new PeerIdentity(UUID.randomUUID(),"Cycle test")));
            action(()->{
                dialog.setVisible(true);
                field(dialog,"m1-local-port").setText(Integer.toString(port));
                button(dialog,"m1-listen").doClick();
            });
            await(()->button(dialog,"m1-stop").isEnabled(),"GUI listener bound cycle "+cycle);
            action(dialog::dispose);
            assertFalse(edt(dialog::isDisplayable));
            try(Socket probe=new Socket()){
                assertThrows(java.io.IOException.class,
                    ()->probe.connect(new InetSocketAddress("127.0.0.1",port),250),
                    "GUI close must stop listening, cycle "+cycle);
            }
        }
    }
}
