package ui.dialogs;

import ui.frames.MainChatFrame;
import listener.LiveChatAdapter;
import vn.intrachat.application.*;
import vn.intrachat.transport.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.concurrent.*;

/** Single LAN connection UI with human approval of self-declared UUIDs. */
public final class LanConnectionDialog extends JDialog {
    private final LocalProfile profile;
    private final LiveChatService live;
    private final LanCoordinator coordinator;
    private final ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor();
    private final JTextField listenIp=new JTextField("127.0.0.1",15);
    private final JTextField listenPort=new JTextField("47321",6);
    private final JTextField connectIp=new JTextField("127.0.0.1",15);
    private final JTextField connectPort=new JTextField("47321",6);
    private final JLabel status=new JLabel("Chưa kết nối. TCP không mã hóa; chỉ dùng trong LAN tin cậy.");
    private final JButton start=new JButton("Listen"), stop=new JButton("Stop");
    private final JButton connect=new JButton("Connect"), disconnect=new JButton("Disconnect");
    private MainChatFrame frame;
    private boolean closed;

    public LanConnectionDialog(LocalProfile profile,LiveChatService service){
        super((Frame)null,"IntraChat — Kết nối LAN",ModalityType.MODELESS);
        this.profile=Objects.requireNonNull(profile);
        this.live=Objects.requireNonNull(service);
        this.coordinator=new LanCoordinator(profile,this::approveIncoming,this::onConnected);
        live.addListener(event->{
            if(event.kind()==LiveChatService.EventKind.DISCONNECTED){
                coordinator.disconnect(event.peerId());
                report("Đã ngắt kết nối: "+event.peerId());
            }
        });
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(760,490);
        setMinimumSize(new Dimension(680,430));
        setLocationRelativeTo(null);
        JPanel root=new JPanel(new BorderLayout(0,15));
        root.setBorder(new EmptyBorder(22,24,20,24));
        setContentPane(root);
        JLabel title=new JLabel("Kết nối TCP trong mạng LAN");
        title.setFont(title.getFont().deriveFont(Font.BOLD,22f));
        root.add(title,BorderLayout.NORTH);
        JPanel sections=new JPanel(new GridLayout(2,1,0,14));
        JPanel hosting=new JPanel(new FlowLayout(FlowLayout.LEFT,8,12));
        hosting.setBorder(BorderFactory.createTitledBorder("Chờ kết nối"));
        hosting.add(new JLabel("IPv4"));hosting.add(listenIp);
        hosting.add(new JLabel("Port"));hosting.add(listenPort);
        hosting.add(start);hosting.add(stop);
        JPanel joining=new JPanel(new FlowLayout(FlowLayout.LEFT,8,12));
        joining.setBorder(BorderFactory.createTitledBorder("Kết nối đến peer"));
        joining.add(new JLabel("IPv4"));joining.add(connectIp);
        joining.add(new JLabel("Port"));joining.add(connectPort);
        joining.add(connect);joining.add(disconnect);
        sections.add(hosting);sections.add(joining);
        root.add(sections,BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout(0,10));
        footer.add(new JLabel("Thiết bị: "+profile.displayName()+" · "+profile.deviceId()+
            " · Dữ liệu không được mã hóa"),BorderLayout.NORTH);
        footer.add(status,BorderLayout.SOUTH);
        root.add(footer,BorderLayout.SOUTH);
        stop.setEnabled(false);
        disconnect.setEnabled(false);
        start.addActionListener(e->startListening());
        stop.addActionListener(e->{coordinator.stopListening();stop.setEnabled(false);
            start.setEnabled(true);report("Đã dừng Listen.");});
        connect.addActionListener(e->connect());
        disconnect.addActionListener(e->{
            for(UUID peer:live.connectedPeers().keySet()){
                live.disconnect(peer);coordinator.disconnect(peer);
            }
            disconnect.setEnabled(false);
            report("Đã ngắt kết nối.");
        });
        addWindowListener(new WindowAdapter(){
            @Override public void windowClosing(WindowEvent e){
                if(frame!=null&&frame.isDisplayable())setVisible(false);else dispose();
            }
            @Override public void windowClosed(WindowEvent e){
                if(closed)return;
                closed=true;
                coordinator.close();live.close();workers.shutdownNow();
                if(frame!=null&&frame.isDisplayable())frame.dispose();
            }
        });
    }
    private void startListening(){
        final NetworkConfig cfg;
        try {cfg=new NetworkConfig(listenIp.getText().trim(),Integer.parseInt(listenPort.getText().trim()));}
        catch(RuntimeException ex){report("IPv4/port không hợp lệ: "+ex.getMessage());return;}
        start.setEnabled(false);
        workers.submit(()->{
            try{
                coordinator.startListening(cfg);
                report("Đang Listen trên "+cfg.ipv4()+":"+cfg.port());
                SwingUtilities.invokeLater(()->stop.setEnabled(true));
            }catch(Exception error){
                report("Không thể Listen: "+error.getMessage());
                SwingUtilities.invokeLater(()->start.setEnabled(true));
            }
        });
    }
    private void connect(){
        final NetworkConfig cfg;
        try{cfg=new NetworkConfig(connectIp.getText().trim(),Integer.parseInt(connectPort.getText().trim()));}
        catch(RuntimeException ex){report("IPv4/port không hợp lệ: "+ex.getMessage());return;}
        connect.setEnabled(false);
        report("Đang chờ phía nhận chấp nhận kết nối...");
        coordinator.connect(cfg).whenComplete((peer,error)->SwingUtilities.invokeLater(()->{
            connect.setEnabled(true);
            if(error!=null){report("Kết nối lỗi: "+error.getMessage());return;}
            report("Đã kết nối "+peer.name()+" ("+peer.session().remoteId()+")");
            showMessenger();
        }));
    }
    private CompletionStage<Boolean> approveIncoming(LanCoordinator.Incoming incoming){
        CompletableFuture<Boolean> decision=new CompletableFuture<>();
        decision.completeOnTimeout(false,30,TimeUnit.SECONDS);
        SwingUtilities.invokeLater(()->{
            if(closed){decision.complete(false);return;}
            Window owner=frame!=null&&frame.isShowing()?frame:this;
            JDialog dialog=new JDialog(owner,"Chấp nhận kết nối TCP?",ModalityType.MODELESS);
            JPanel box=new JPanel(new BorderLayout(10,12));
            box.setBorder(new EmptyBorder(16,18,16,18));
            box.add(new JLabel("<html>Yêu cầu từ: "+escape(incoming.displayName())+
                "<br>UUID tự khai: "+incoming.deviceId()+
                "<br>Endpoint: "+escape(incoming.endpoint())+
                "<br>Không có xác thực mật mã hoặc mã hóa.</html>"),BorderLayout.CENTER);
            JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton reject=new JButton("Reject"),accept=new JButton("Accept");
            reject.addActionListener(e->{decision.complete(false);dialog.dispose();});
            accept.addActionListener(e->{decision.complete(true);dialog.dispose();});
            actions.add(reject);actions.add(accept);
            box.add(actions,BorderLayout.SOUTH);
            dialog.setContentPane(box);dialog.pack();dialog.setMinimumSize(new Dimension(500,220));
            dialog.setLocationRelativeTo(owner);
            dialog.addWindowListener(new WindowAdapter(){
                @Override public void windowClosed(WindowEvent e){decision.complete(false);}
            });
            decision.whenComplete((value,error)->SwingUtilities.invokeLater(dialog::dispose));
            dialog.setVisible(true);
        });
        return decision;
    }
    private void onConnected(LanCoordinator.Peer peer){
        try {
            live.attach(peer.session(),peer.name());
            report("Chat TCP đã kết nối: "+peer.name()+" ("+peer.session().remoteId()+")");
            SwingUtilities.invokeLater(this::showMessenger);
        }catch(Exception error){
            peer.session().close();
            report("Không thể mở Live session: "+error.getMessage());
            throw new IllegalStateException(error);
        }
    }
    private void showMessenger(){
        if(closed)return;
        if(frame==null||!frame.isDisplayable()){
            LiveChatAdapter adapter=new LiveChatAdapter(profile,profile.displayName(),live,
                ()->{coordinator.close();SwingUtilities.invokeLater(this::dispose);},
                ()->SwingUtilities.invokeLater(()->{if(!closed){setVisible(true);toFront();}}));
            frame=new MainChatFrame(adapter.self(),adapter);
            frame.setVisible(true);
            setVisible(false);
        }else{frame.toFront();frame.requestFocus();}
        disconnect.setEnabled(!live.connectedPeers().isEmpty());
    }
    private void report(String message){SwingUtilities.invokeLater(()->{
        if(!closed)status.setText(message);
    });}
    private static String escape(String text){
        return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
}
