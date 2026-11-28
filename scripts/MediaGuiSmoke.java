import listener.LiveChatAdapter;
import model.Conversation;
import ui.components.ChatIcon;
import ui.components.ChatStyle;
import ui.frames.MainChatFrame;
import vn.intrachat.application.*;
import vn.intrachat.transport.NetworkConfig;

import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.ServerSocket;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Explicit desktop smoke: three real Messenger frames, real TCP, isolated profiles.
 * No microphone capture, codec playback, or physical LAN acceptance. */
public class MediaGuiSmoke {
    static void check(boolean condition,String message) {
        if(!condition) throw new AssertionError(message);
    }
    static <T> T edt(Callable<T> action) throws Exception {
        FutureTask<T> task=new FutureTask<>(action);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }
    static void until(Callable<Boolean> condition,String description)throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        while(System.nanoTime()<deadline){if(condition.call())return;Thread.sleep(50);}
        throw new AssertionError("Timeout: "+description);
    }
    static <T extends Component> List<T> components(Component root,Class<T> type) {
        List<T> found=new ArrayList<>();
        if(type.isInstance(root))found.add(type.cast(root));
        if(root instanceof Container c)for(Component child:c.getComponents())found.addAll(components(child,type));
        return found;
    }
    static JButton button(Component root,Predicate<JButton> predicate) {
        return components(root,JButton.class).stream().filter(predicate).findFirst().orElse(null);
    }
    static JButton tool(Component root,ChatIcon.Kind kind) {
        return button(root,b->b.getIcon() instanceof ChatIcon icon && icon.kind()==kind && !"call-video".equals(b.getName()));
    }
    static void clickChooser(JFrame frame,JButton button,Path file)throws Exception {
        check(button!=null,"Missing chooser button");
        edt(()->{
            javax.swing.Timer timer=new javax.swing.Timer(80,null);
            timer.addActionListener(e->{
                for(Window window:Window.getWindows()){
                    if(!window.isShowing()||!(window instanceof JDialog dialog)||dialog.getOwner()!=frame)continue;
                    var chooser=components(dialog,JFileChooser.class).stream().findFirst().orElse(null);
                    if(chooser!=null){timer.stop();chooser.setSelectedFile(file.toFile());chooser.approveSelection();return;}
                }
            });
            // Timeout cancels only dialogs owned by the test frame, never other apps.
            javax.swing.Timer deadline=new javax.swing.Timer(8000,e->{
                timer.stop();
                for(Window window:Window.getWindows())if(window instanceof JDialog d&&d.getOwner()==frame&&d.isShowing())d.dispose();
            });deadline.setRepeats(false);
            timer.start();deadline.start();
            try {button.doClick();}finally{timer.stop();deadline.stop();}
            return null;
        });
    }
    static boolean selectGroup(MainChatFrame frame,String id) {
        for(JList<?> list:components(frame,JList.class)) {
            for(int i=0;i<list.getModel().getSize();i++){
                if(list.getModel().getElementAt(i) instanceof Conversation c&&id.equals(c.id())){
                    list.setSelectedIndex(i);return true;
                }
            }
        }
        return false;
    }
    static boolean groupComplete(LiveChatService service,UUID group,UUID origin){
        return service.history().loadMessages(group,500,0).stream().anyMatch(e->e.senderId().equals(origin)&&e.transferState().equals("COMPLETED"));
    }
    static void verifyGroupRows(UUID group,List<LiveChatService> services,UUID origin,Path source)throws Exception{
        UUID common=null;
        for(var service:services){
            var rows=service.history().loadMessages(group,500,0).stream().filter(e->e.senderId().equals(origin)&&e.transferState().equals("COMPLETED")).toList();
            check(rows.size()==1,"One group media row");
            var row=rows.getFirst();
            if(common==null)common=row.messageId();else check(common.equals(row.messageId()),"Stable group ID");
            check(java.util.Arrays.equals(Files.readAllBytes(source),Files.readAllBytes(Path.of(row.localPath()))),"Group bytes");
        }
    }
    public static void main(String[] args)throws Exception{
        check(!GraphicsEnvironment.isHeadless(),"Desktop required");
        Path dir=Path.of(args[0]);Files.createDirectories(dir);
        Path png=dir.resolve("sample.png"), wav=dir.resolve("sample.wav"), video=dir.resolve("sample.mp4"), file=dir.resolve("sample.bin");
        ImageIO.write(new BufferedImage(80,40,BufferedImage.TYPE_INT_RGB),"png",png.toFile());
        try(var stream=new AudioInputStream(new ByteArrayInputStream(new byte[16000]),new AudioFormat(8000,16,1,true,false),8000)){
            AudioSystem.write(stream,AudioFileFormat.Type.WAVE,wav.toFile());
        }
        Files.write(video,new byte[]{0,0,0,20,'f','t','y','p','i','s','o','m'}); // signature fixture only
        Files.write(file,new byte[]{1,2,3,4});
        var a=LocalProfile.loadOrCreate(dir.resolve("A"));var b=LocalProfile.loadOrCreate(dir.resolve("B"));var c=LocalProfile.loadOrCreate(dir.resolve("C"));
        int port;try(var s=new ServerSocket(0)){port=s.getLocalPort();}
        var settingsOpened=new java.util.concurrent.atomic.AtomicInteger();
        try(var sa=new LiveChatService(a);var sb=new LiveChatService(b);var sc=new LiveChatService(c);
            var ca=new LanCoordinator(a,i->CompletableFuture.completedFuture(true),p->{try{sa.attach(p.session(),p.name());}catch(Exception e){throw new RuntimeException(e);}});
            var cb=new LanCoordinator(b,i->CompletableFuture.completedFuture(true),p->{try{sb.attach(p.session(),p.name());}catch(Exception e){throw new RuntimeException(e);}});
            var cc=new LanCoordinator(c,i->CompletableFuture.completedFuture(true),p->{try{sc.attach(p.session(),p.name());}catch(Exception e){throw new RuntimeException(e);}});
            var aa=new LiveChatAdapter(a,a.displayName(),sa,()->{},settingsOpened::incrementAndGet);
            var ab=new LiveChatAdapter(b,b.displayName(),sb,()->{});
            var ac=new LiveChatAdapter(c,c.displayName(),sc,()->{})){
            ca.startListening(new NetworkConfig("127.0.0.1",port));
            cb.connect(new NetworkConfig("127.0.0.1",port)).get(8,TimeUnit.SECONDS);
            cc.connect(new NetworkConfig("127.0.0.1",port)).get(8,TimeUnit.SECONDS);
            var frames=edt(()->{
                ChatStyle.install();
                var left=new MainChatFrame(aa.self(),aa);var right=new MainChatFrame(ab.self(),ab);var third=new MainChatFrame(ac.self(),ac);
                left.setLocation(10,10);right.setLocation(70,70);third.setLocation(130,130);
                left.setVisible(true);right.setVisible(true);third.setVisible(true);return List.of(left,right,third);
            });
            var left=frames.get(0);var right=frames.get(1);var third=frames.get(2);
            try{
                until(()->edt(()->tool(left,ChatIcon.Kind.IMAGE).isEnabled()&&tool(right,ChatIcon.Kind.IMAGE).isEnabled()),"online media");
                until(()->edt(()->selectGroup(left,sa.conversationId(b.deviceId()).toString())&&selectGroup(right,sb.conversationId(a.deviceId()).toString())),"Select direct chat");
                edt(()->{
                    var manage=button(left,x->"group-manage".equals(x.getName()));
                    var gear=button(left,x->"messenger-lan-settings".equals(x.getName()));
                    check(manage!=null&&!manage.isShowing(),"No group management in direct chat");
                    check(gear!=null&&gear.isShowing()&&gear.getIcon() instanceof ChatIcon icon&&icon.kind()==ChatIcon.Kind.SETTINGS,"Settings icon visible");
                    var profile=components(left,JPanel.class).stream().filter(x->"messenger-profile".equals(x.getName())).findFirst().orElseThrow();
                    check(SwingUtilities.isDescendingFrom(gear,profile),"Settings belongs to profile");
                    check(((BorderLayout)profile.getLayout()).getLayoutComponent(BorderLayout.EAST)==gear.getParent(),"Settings on right");
                    check(gear.getText()==null||gear.getText().isBlank(),"Icon only");
                    check("Cài đặt kết nối LAN".equals(gear.getAccessibleContext().getAccessibleName()),"Accessible settings name");
                    gear.doClick();
                    check(settingsOpened.get()==1,"Settings callback");
                    check(button(left,x->"Tạo nhóm mới".equals(x.getAccessibleContext().getAccessibleName())).isShowing(),"Create group still present");
                    return null;
                });
                check(edt(()->components(left,JButton.class).stream().filter(x->x.getIcon() instanceof ChatIcon i&&i.kind()==ChatIcon.Kind.MIC).count()==1),"One mic");
                for(var sample:List.of(png,video,wav,file)){
                    ChatIcon.Kind kind=sample==png?ChatIcon.Kind.IMAGE:sample==video?ChatIcon.Kind.VIDEO:sample==wav?ChatIcon.Kind.AUDIO:ChatIcon.Kind.FILE;
                    clickChooser(left,edt(()->tool(left,kind)),sample);
                    Path destination=b.mediaDir().resolve(sample.getFileName());
                    until(()->Files.isRegularFile(destination)&&edt(()->button(right,x->x.getText().equals("Mở tệp / vị trí"))!=null),"Automatic receive "+sample);
                    check(edt(()->button(right,x->x.getText().equals("Nhận / Chọn nơi lưu"))==null),"No destination picker");
                    check(java.util.Arrays.equals(Files.readAllBytes(sample),Files.readAllBytes(destination)),"Bytes "+sample);
                    if(sample==png)until(()->edt(()->button(right,x->x.isEnabled()&&x.getIcon()!=null&&x.getAccessibleContext().getAccessibleName().startsWith("Xem ảnh"))!=null),"Rendered thumbnail");
                    if(sample==wav)until(()->edt(()->button(right,x->"Phát tin thoại".equals(x.getAccessibleContext().getAccessibleName()))!=null),"WAV player");
                    System.out.println("PASS_GUI_MEDIA "+sample.getFileName());
                    // Wait for sender's FILE_RESULT before offering next file.
                    int expected=(sample==png?1:sample==video?2:sample==wav?3:4);
                    until(()->edt(()->components(left,JButton.class).stream().filter(x->x.getText().equals("Mở tệp / vị trí")).count()>=expected),"Sender completion");
                }
                javax.swing.Timer inviteTimer=edt(()->{
                    javax.swing.Timer timer=new javax.swing.Timer(100,e->{
                        for(Window w:Window.getWindows())if(w.isShowing()&&w instanceof JDialog d&&d.getTitle().equals("Lời mời nhóm IntraChat")){
                            for(JOptionPane pane:components(d,JOptionPane.class))pane.setValue(JOptionPane.YES_OPTION);
                        }
                    });timer.start();return timer;
                });
                try{
                    var group=aa.createGroup("Media smoke",List.of(b.deviceId().toString(),c.deviceId().toString())).toCompletableFuture().get(10,TimeUnit.SECONDS);
                    until(()->edt(()->selectGroup(left,group.id())&&selectGroup(right,group.id())&&selectGroup(third,group.id())),"Group visible");
                    until(()->edt(()->button(left,x->"group-manage".equals(x.getName())).isShowing()&&button(right,x->"group-manage".equals(x.getName())).isShowing()),"Group management visible in group");
                    until(()->edt(()->tool(left,ChatIcon.Kind.MIC).isEnabled()&&tool(right,ChatIcon.Kind.MIC).isEnabled()&&tool(third,ChatIcon.Kind.MIC).isEnabled()),"Group media enabled");
                    check(edt(()->components(left,JButton.class).stream().filter(x->x.getIcon() instanceof ChatIcon i&&i.kind()==ChatIcon.Kind.MIC).count()==1),"Group one mic");
                    UUID gid=UUID.fromString(group.id());
                    // Wait for all three member rosters, not just the first invite acceptance.
                    until(()->sa.history().groups().stream().anyMatch(g->g.groupId().equals(gid)&&g.memberIds().size()==3)
                        &&sb.history().groups().stream().anyMatch(g->g.groupId().equals(gid)&&g.memberIds().size()==3)
                        &&sc.history().groups().stream().anyMatch(g->g.groupId().equals(gid)&&g.memberIds().size()==3),"Group roster");
                    clickChooser(left,edt(()->tool(left,ChatIcon.Kind.IMAGE)),png);
                    until(()->groupComplete(sa,gid,a.deviceId())&&groupComplete(sb,gid,a.deviceId())&&groupComplete(sc,gid,a.deviceId()),"Owner group media");
                    until(()->edt(()->button(third,x->x.isEnabled()&&x.getAccessibleContext().getAccessibleName().startsWith("Xem ảnh"))!=null),"Group thumbnail");
                    clickChooser(right,edt(()->tool(right,ChatIcon.Kind.AUDIO)),wav);
                    until(()->groupComplete(sa,gid,b.deviceId())&&groupComplete(sb,gid,b.deviceId())&&groupComplete(sc,gid,b.deviceId()),"Member group media");
                    until(()->edt(()->button(third,x->"Phát tin thoại".equals(x.getAccessibleContext().getAccessibleName()))!=null),"Group WAV UI");
                    check(!sb.connectedPeers().containsKey(c.deviceId()),"Member relay, no B-C direct connection");
                    verifyGroupRows(gid,List.of(sa,sb,sc),a.deviceId(),png);
                    verifyGroupRows(gid,List.of(sa,sb,sc),b.deviceId(),wav);
                    System.out.println("PASS_GUI_GROUP_OWNER_AND_MEMBER_MEDIA_AUTOSAVE");
                    until(()->edt(()->selectGroup(left,sa.conversationId(b.deviceId()).toString())),"Return to direct chat");
                    until(()->edt(()->!button(left,x->"group-manage".equals(x.getName())).isShowing()),"Hide group management again");
                    System.out.println("PASS_GUI_CONTEXTUAL_GROUP_AND_PROFILE_SETTINGS");
                }finally{edt(()->{inviteTimer.stop();return null;});}
            }finally{edt(()->{left.dispose();right.dispose();third.dispose();return null;});}
        }
        System.out.println("PASS_GUI_MEDIA_END_TO_END");
    }
}
