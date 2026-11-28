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
    private final PeerReconnect reconnect;
    private final LiveChatAdapter adapter;
    private final ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor();
    private final JComboBox<String> listenIp=new JComboBox<>();
    private final JTextField listenPort=new JTextField(String.valueOf(NetworkConfig.DEFAULT_PORT),6);
    private final JTextField connectIp=new JTextField("127.0.0.1",15);
    private final JTextField connectPort=new JTextField(String.valueOf(NetworkConfig.DEFAULT_PORT),6);
    private final JLabel listenStatus=new JLabel("Đang chuẩn bị Listen...");
    private final JLabel status=new JLabel("Chưa kết nối. TCP không mã hóa; chỉ dùng trong LAN tin cậy.");
    private final JButton start=new JButton("Listen"), stop=new JButton("Stop");
    private final JButton connect=new JButton("Connect"), disconnect=new JButton("Disconnect");
    private MainChatFrame frame;
    private volatile boolean closed;

    public LanConnectionDialog(LocalProfile profile,LiveChatService service,java.util.List<String> localAddresses){
        super((Frame)null,"IntraChat — Kết nối LAN",ModalityType.MODELESS);
        this.profile=Objects.requireNonNull(profile);
        this.live=Objects.requireNonNull(service);
        this.coordinator=new LanCoordinator(profile,this::approveIncoming,this::onConnected);
        this.reconnect=new PeerReconnect(profile.deviceId(),live.history(),coordinator,this::report);
        this.adapter=new LiveChatAdapter(profile,profile.displayName(),live,
            ()->{reconnect.close();coordinator.close();workers.shutdownNow();SwingUtilities.invokeLater(this::dispose);},
            ()->SwingUtilities.invokeLater(()->{if(!closed){setVisible(true);toFront();}}));
        live.addListener(event->{
            if(event.kind()==LiveChatService.EventKind.DISCONNECTED){
                coordinator.disconnect(event.peerId());
                report("Đã ngắt kết nối: "+event.peerId());
                SwingUtilities.invokeLater(()->disconnect.setEnabled(true));
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
        listenIp.setEditable(true);
        listenIp.setPrototypeDisplayValue("255.255.255.255");
        for(String address:localAddresses)listenIp.addItem(address);
        if(listenIp.getItemCount()==0)listenIp.addItem("127.0.0.1");
        NetworkConfig previous=live.history().localListen();
        if(previous!=null){
            listenPort.setText(Integer.toString(previous.port()));
            if(localAddresses.contains(previous.ipv4()))listenIp.setSelectedItem(previous.ipv4());
        }
        var known=live.history().peerEndpoints().values().stream().reduce((first,last)->last).orElse(null);
        if(known!=null){connectIp.setText(known.ipv4());connectPort.setText(Integer.toString(known.port()));}
        hosting.add(new JLabel("IPv4 của bạn"));hosting.add(listenIp);
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
        JPanel states=new JPanel(new GridLayout(2,1,0,4));
        states.add(listenStatus);states.add(status);
        footer.add(states,BorderLayout.SOUTH);
        root.add(footer,BorderLayout.SOUTH);
        stop.setEnabled(false);
        disconnect.setEnabled(!live.history().peerEndpoints().isEmpty());
        start.addActionListener(e->startListening());
        stop.addActionListener(e->{coordinator.stopListening();stop.setEnabled(false);
            start.setEnabled(true);listenIp.setEnabled(true);listenPort.setEnabled(true);
            listenStatus.setText("Đã dừng Listen.");});
        connect.addActionListener(e->connect());
        disconnect.addActionListener(e->{
            reconnect.pause();
            for(UUID peer:live.connectedPeers().keySet()){
                live.disconnect(peer);coordinator.disconnect(peer);
            }
            disconnect.setEnabled(false);
            report("Đã ngắt kết nối; tạm dừng tự kết nối lại. Nhấn Connect để tiếp tục.");
        });
        addWindowListener(new WindowAdapter(){
            @Override public void windowClosing(WindowEvent e){
                if(frame!=null&&frame.isDisplayable())setVisible(false);else dispose();
            }
            @Override public void windowClosed(WindowEvent e){
                if(closed)return;
                closed=true;
                reconnect.close();coordinator.close();adapter.close();workers.shutdownNow();
                if(frame!=null&&frame.isDisplayable())frame.dispose();
            }
        });
    }
    public void start(boolean returning){
        if(returning)showMessenger();else setVisible(true);
        startListening();
        reconnect.start();
        if(returning&&live.history().peerEndpoints().isEmpty()&&!live.history().peers().isEmpty()){
            String notice="Lịch sử cũ chưa lưu IP/port. Mở Cài đặt kết nối LAN (bánh răng cạnh profile) và kết nối một lần để ghi nhớ.";
            report(notice);frame.onConnectionError(notice);
        }
    }
    public void startListening(){
        if(closed||!start.isEnabled())return;
        final NetworkConfig cfg;
        try {cfg=new NetworkConfig(String.valueOf(listenIp.getEditor().getItem()).trim(),Integer.parseInt(listenPort.getText().trim()));}
        catch(RuntimeException ex){listenStatus.setText("IPv4/port không hợp lệ: "+ex.getMessage());return;}
        start.setEnabled(false);listenIp.setEnabled(false);listenPort.setEnabled(false);
        listenStatus.setText("Đang mở Listen trên "+cfg.ipv4()+":"+cfg.port()+"...");
        workers.submit(()->{
            try{
                coordinator.startListening(cfg);
                live.history().saveLocalListen(cfg);
                SwingUtilities.invokeLater(()->{
                    if(closed)return;
                    listenStatus.setText("Đang Listen trên "+cfg.ipv4()+":"+cfg.port()+
                        (cfg.ipv4().startsWith("127.")?" (chỉ máy này)":" — chia sẻ IP và port này cho peer"));
                    stop.setEnabled(true);
                });
            }catch(Exception error){
                SwingUtilities.invokeLater(()->{
                    if(closed)return;
                    listenStatus.setText("Không thể Listen: "+error.getMessage()+" — đổi IP/port và thử lại.");
                    start.setEnabled(true);listenIp.setEnabled(true);listenPort.setEnabled(true);
                });
            }
        });
    }
    private void connect(){
        final NetworkConfig cfg;
        try{cfg=new NetworkConfig(connectIp.getText().trim(),Integer.parseInt(connectPort.getText().trim()));}
        catch(RuntimeException ex){report("IPv4/port không hợp lệ: "+ex.getMessage());return;}
        reconnect.resume();
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
        if(incoming.reconnect())return CompletableFuture.completedFuture(reconnect.approveKnown(incoming));
        CompletableFuture<Boolean> decision=new CompletableFuture<>();
        decision.completeOnTimeout(false,30,TimeUnit.SECONDS);
        SwingUtilities.invokeLater(()->{
            if(closed||decision.isDone()){decision.complete(false);return;}
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
            reconnect.connected(peer);
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
            frame=new MainChatFrame(adapter.self(),adapter);
            frame.setVisible(true);
            setVisible(false);
        }
        disconnect.setEnabled(!live.connectedPeers().isEmpty()||!live.history().peerEndpoints().isEmpty());
    }
    private void report(String message){SwingUtilities.invokeLater(()->{
        if(!closed)status.setText(message);
    });}
    private static String escape(String text){
        return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
}
