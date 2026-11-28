import ui.components.ChatStyle;
import ui.dialogs.LanConnectionDialog;
import vn.intrachat.application.*;
import vn.intrachat.transport.NetworkConfig;
import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.*;

public class LanSettingsGuiSmoke {
    static <T> T edt(Callable<T> fn)throws Exception {
        var task=new FutureTask<T>(fn);SwingUtilities.invokeAndWait(task);return task.get();
    }
    static Component named(Component root,String name){
        if(name.equals(root.getName()))return root;
        if(root instanceof Container c)for(Component child:c.getComponents()){
            var result=named(child,name);if(result!=null)return result;
        }
        return null;
    }
    static JButton button(Component root,String name){return (JButton)Objects.requireNonNull(named(root,name),name);}
    static void until(Callable<Boolean> check)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        while(System.nanoTime()<end){if(check.call())return;Thread.sleep(50);}
        throw new AssertionError("Timed out");
    }
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]);Files.createDirectories(root);
        var a=LocalProfile.loadOrCreate(root.resolve("A"),"Alice");
        var b=LocalProfile.loadOrCreate(root.resolve("B"),"Bob");
        int port;try(var reserve=new ServerSocket(0)){port=reserve.getLocalPort();}
        try(var live=new LiveChatService(a);var other=new LiveChatService(b);
            var client=new LanCoordinator(b,r->CompletableFuture.completedFuture(true),peer->{
                try{other.attach(peer.session(),peer.name());}catch(Exception e){throw new RuntimeException(e);}
            })){
            live.history().saveLocalListen(new NetworkConfig("127.0.0.1",port));
            var dialog=edt(()->{
                ChatStyle.install();
                var d=new LanConnectionDialog(a,live,java.util.List.of("127.0.0.1"));
                d.start(false);return d;
            });
            try{
                until(()->edt(()->button(dialog,"lan-stop").isEnabled()));
                edt(()->{
                    check(button(dialog,"lan-listen").getBackground().equals(ChatStyle.BLUE),"Primary blue");
                    check(button(dialog,"lan-disconnect").getBackground().equals(ChatStyle.FIELD),"Secondary field");
                    check(dialog.getWidth()>=650,"Layout width");
                    button(dialog,"lan-stop").doClick();
                    check(button(dialog,"lan-listen").isEnabled(),"Stop enables start");
                    button(dialog,"lan-listen").doClick();return null;
                });
                until(()->edt(()->button(dialog,"lan-stop").isEnabled()));
                var future=client.connect(new NetworkConfig("127.0.0.1",port));
                until(()->edt(()->Arrays.stream(Window.getWindows()).anyMatch(w->w.isShowing()&&named(w,"lan-accept")!=null)));
                edt(()->{
                    for(Window w:Window.getWindows())if(w.isShowing()&&named(w,"lan-accept")!=null){button(w,"lan-accept").doClick();break;}
                    return null;
                });
                future.get(8,TimeUnit.SECONDS);
                until(()->live.connectedPeers().containsKey(b.deviceId()));
                until(()->edt(()->Arrays.stream(Window.getWindows()).anyMatch(w->w.isShowing()&&named(w,"messenger-lan-settings")!=null)));
                edt(()->{
                    for(Window w:Window.getWindows())if(w.isShowing()&&named(w,"messenger-lan-settings")!=null){button(w,"messenger-lan-settings").doClick();break;}
                    return null;
                });
                until(()->edt(dialog::isShowing));
                edt(()->{
                    check(((JLabel)named(dialog,"lan-peer-count")).getText().startsWith("1 "),"Live count");
                    button(dialog,"lan-disconnect").doClick();return null;
                });
                until(()->live.connectedPeers().isEmpty());
                edt(()->{button(dialog,"lan-close").doClick();check(!dialog.isShowing(),"Settings closes without disposing Messenger");return null;});
                System.out.println("PASS_GUI_LAN_STYLE_LISTEN_APPROVAL_SETTINGS_DISCONNECT");
            }finally{edt(()->{for(Window w:Window.getWindows())if(w.isDisplayable())w.dispose();return null;});}
        }
    }
}
