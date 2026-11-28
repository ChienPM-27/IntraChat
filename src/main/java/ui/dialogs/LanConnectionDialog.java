package ui.dialogs;

import ui.frames.MainChatFrame;
import ui.components.AvatarIcon;
import ui.components.ChatIcon;
import com.formdev.flatlaf.FlatClientProperties;
import static ui.components.ChatStyle.*;
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
    private final JLabel listenStatus=label("Đang chuẩn bị Listen...",12,Font.PLAIN,MUTED);
    private final JLabel status=label("Chưa kết nối",12,Font.PLAIN,MUTED);
    private final JButton start=primary("Bắt đầu"), stop=secondary("Dừng");
    private final JButton connect=primary("Kết nối"), disconnect=secondary("Ngắt kết nối");
    private final JLabel peerCount=label("0 thiết bị đang kết nối",12,Font.BOLD,MUTED);
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
            ()->SwingUtilities.invokeLater(()->{if(!closed){refreshPeerCount();setLocationRelativeTo(frame);setVisible(true);toFront();}}));
        live.addListener(event->{
            if(event.kind()==LiveChatService.EventKind.DISCONNECTED){
                coordinator.disconnect(event.peerId());
                report("Đã ngắt kết nối: "+event.peerId());
                SwingUtilities.invokeLater(()->{disconnect.setEnabled(true);refreshPeerCount();});
            }
        });
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setIconImage(ChatIcon.appIconImage());
        JPanel root=panel(new BorderLayout(0,16));
        root.setBorder(new EmptyBorder(22,24,20,24));
        setContentPane(root);
        JPanel header=panel(new BorderLayout(12,0));
        JLabel gear=new JLabel(new ChatIcon(ChatIcon.Kind.SETTINGS,BLUE,30));
        header.add(gear,BorderLayout.WEST);
        JPanel titles=panel(new GridLayout(2,1,0,3));
        titles.add(label("Cài đặt kết nối LAN",22,Font.BOLD,INK));
        titles.add(label("Quản lý thiết bị và kết nối trong mạng nội bộ",13,Font.PLAIN,MUTED));
        header.add(titles);
        root.add(header,BorderLayout.NORTH);

        listenIp.setEditable(true);
        listenIp.setPrototypeDisplayValue("255.255.255.255");
        listenIp.putClientProperty(FlatClientProperties.STYLE,"arc: 12; borderWidth: 0");
        listenIp.setBackground(FIELD);
        listenIp.setPreferredSize(new Dimension(226,40));
        listenIp.getAccessibleContext().setAccessibleName("IPv4 của thiết bị này");
        for(String address:localAddresses)listenIp.addItem(address);
        if(listenIp.getItemCount()==0)listenIp.addItem("127.0.0.1");
        NetworkConfig previous=live.history().localListen();
        if(previous!=null){
            listenPort.setText(Integer.toString(previous.port()));
            if(localAddresses.contains(previous.ipv4()))listenIp.setSelectedItem(previous.ipv4());
        }
        var known=live.history().peerEndpoints().values().stream().reduce((first,last)->last).orElse(null);
        if(known!=null){connectIp.setText(known.ipv4());connectPort.setText(Integer.toString(known.port()));}
        dialogField(listenPort,"Cổng Listen");
        dialogField(connectIp,"IPv4 thiết bị khác");
        dialogField(connectPort,"Cổng thiết bị khác");
        start.setName("lan-listen");stop.setName("lan-stop");
        connect.setName("lan-connect");disconnect.setName("lan-disconnect");
        listenStatus.setName("lan-listen-status");status.setName("lan-session-status");
        peerCount.setName("lan-peer-count");
        JPanel sections=panel(new GridLayout(2,1,0,12));
        sections.add(connectionCard("Thiết bị của bạn","Lắng nghe kết nối đến · tự bật khi khởi động",
            listenIp,listenPort,start,stop,listenStatus));
        sections.add(connectionCard("Kết nối thiết bị khác","Nhập IPv4 và cổng Listen của thiết bị cần kết nối",
            connectIp,connectPort,connect,disconnect,status));
        root.add(sections,BorderLayout.CENTER);

        JPanel footer=panel(new BorderLayout(0,12));
        JPanel identity=dialogCard(new BorderLayout(10,0));
        identity.add(new JLabel(new AvatarIcon(profile.displayName(),profile.deviceId().toString(),36,false,false)),BorderLayout.WEST);
        JPanel details=panel(new GridLayout(2,1,0,3));
        JLabel name=label(profile.displayName(),14,Font.BOLD,INK);
        name.setToolTipText(profile.deviceId().toString());
        details.add(name);details.add(peerCount);
        identity.add(details,BorderLayout.CENTER);
        JLabel protocol=label("TCP LAN",12,Font.BOLD,BLUE);
        identity.add(protocol,BorderLayout.EAST);
        footer.add(identity,BorderLayout.NORTH);
        JPanel bottom=panel(new BorderLayout(10,0));
        bottom.add(label("Không mã hóa · Chỉ sử dụng trong LAN tin cậy",12,Font.PLAIN,MUTED));
        JButton close=secondary("Đóng");
        close.setName("lan-close");
        close.addActionListener(e->dispatchEvent(new WindowEvent(this,WindowEvent.WINDOW_CLOSING)));
        bottom.add(close,BorderLayout.EAST);
        footer.add(bottom,BorderLayout.SOUTH);
        root.add(footer,BorderLayout.SOUTH);
        getRootPane().setDefaultButton(connect);
        getRootPane().registerKeyboardAction(e->close.doClick(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        pack();
        setMinimumSize(new Dimension(Math.max(650,getWidth()),getHeight()));
        setSize(Math.max(690,getWidth()),getHeight());
        setLocationRelativeTo(null);
        stop.setEnabled(false);
        disconnect.setEnabled(!live.history().peerEndpoints().isEmpty());
        start.addActionListener(e->startListening());
        stop.addActionListener(e->{coordinator.stopListening();stop.setEnabled(false);
            start.setEnabled(true);listenIp.setEnabled(true);listenPort.setEnabled(true);
            setState(listenStatus,"Đã dừng lắng nghe.",MUTED);});
        connect.addActionListener(e->connect());
        disconnect.addActionListener(e->{
            reconnect.pause();
            for(UUID peer:live.connectedPeers().keySet()){
                live.disconnect(peer);coordinator.disconnect(peer);
            }
            disconnect.setEnabled(false);
            report("Đã ngắt kết nối; tạm dừng tự kết nối lại. Nhấn Kết nối để tiếp tục.");
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
    private JPanel connectionCard(String title,String description,JComponent ip,JTextField port,
                                  JButton primaryAction,JButton secondaryAction,JLabel state){
        JPanel card=dialogCard(new BorderLayout(0,10));
        JPanel heading=panel(new GridLayout(2,1,0,3));
        heading.add(label(title,15,Font.BOLD,INK));
        heading.add(label(description,12,Font.PLAIN,MUTED));
        card.add(heading,BorderLayout.NORTH);
        JPanel fields=panel(new BorderLayout(12,0));
        JPanel address=panel(new BorderLayout(0,5));
        JLabel ipLabel=label("Địa chỉ IPv4",12,Font.BOLD,INK);ipLabel.setLabelFor(ip);
        address.add(ipLabel,BorderLayout.NORTH);address.add(ip);
        JPanel portBox=panel(new BorderLayout(0,5));
        JLabel portLabel=label("Cổng",12,Font.BOLD,INK);portLabel.setLabelFor(port);
        portBox.add(portLabel,BorderLayout.NORTH);portBox.add(port);
        JPanel inputs=panel(new BorderLayout(10,0));
        inputs.add(address);inputs.add(portBox,BorderLayout.EAST);
        fields.add(inputs);
        JPanel buttons=panel(new FlowLayout(FlowLayout.TRAILING,6,0));
        for(JButton button:java.util.List.of(primaryAction,secondaryAction)){
            button.setPreferredSize(new Dimension(Math.max(96,button.getPreferredSize().width+10),40));
            buttons.add(button);
        }
        JPanel actionSlot=panel(new BorderLayout());
        actionSlot.add(buttons,BorderLayout.SOUTH);
        fields.add(actionSlot,BorderLayout.EAST);
        card.add(fields);
        state.setPreferredSize(new Dimension(0,22));
        card.add(state,BorderLayout.SOUTH);
        return card;
    }
    private static void setState(JLabel label,String text,Color color){
        label.setText(text);label.setToolTipText(text);label.setForeground(color);
    }
    private void refreshPeerCount(){
        int count=live.connectedPeers().size();
        setState(peerCount,count+" thiết bị đang kết nối",count>0?GREEN:MUTED);
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
        catch(RuntimeException ex){setState(listenStatus,"IPv4/cổng không hợp lệ: "+ex.getMessage(),RED);return;}
        start.setEnabled(false);listenIp.setEnabled(false);listenPort.setEnabled(false);
        setState(listenStatus,"Đang mở lắng nghe tại "+cfg.ipv4()+":"+cfg.port()+"…",BLUE);
        workers.submit(()->{
            try{
                coordinator.startListening(cfg);
                live.history().saveLocalListen(cfg);
                SwingUtilities.invokeLater(()->{
                    if(closed)return;
                    setState(listenStatus,"Đang lắng nghe · "+cfg.ipv4()+":"+cfg.port()+
                        (cfg.ipv4().startsWith("127.")?" (chỉ máy này)":""),GREEN);
                    stop.setEnabled(true);
                });
            }catch(Exception error){
                SwingUtilities.invokeLater(()->{
                    if(closed)return;
                    setState(listenStatus,"Không thể lắng nghe: "+error.getMessage(),RED);
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
            dialog.setIconImage(ChatIcon.appIconImage());
            JPanel box=panel(new BorderLayout(0,16));
            box.setBorder(new EmptyBorder(20,22,20,22));
            JPanel heading=panel(new BorderLayout(12,0));
            heading.add(new JLabel(new AvatarIcon(incoming.displayName(),incoming.deviceId().toString(),44,false,false)),BorderLayout.WEST);
            JPanel identity=panel(new GridLayout(2,1,0,3));
            identity.add(label("Yêu cầu kết nối LAN",18,Font.BOLD,INK));
            identity.add(label(incoming.displayName(),14,Font.BOLD,INK));
            heading.add(identity);
            box.add(heading,BorderLayout.NORTH);
            JPanel details=dialogCard(new GridLayout(0,1,0,6));
            details.add(label("Địa chỉ: "+incoming.endpoint(),13,Font.PLAIN,INK));
            details.add(label("UUID: "+incoming.deviceId(),12,Font.PLAIN,MUTED));
            details.add(label("Danh tính tự khai · Kết nối không mã hóa",12,Font.PLAIN,MUTED));
            box.add(details,BorderLayout.CENTER);
            JPanel actions=panel(new FlowLayout(FlowLayout.TRAILING,8,0));
            JButton reject=secondary("Từ chối"),accept=primary("Chấp nhận");
            reject.setName("lan-reject");accept.setName("lan-accept");
            reject.addActionListener(e->{decision.complete(false);dialog.dispose();});
            accept.addActionListener(e->{decision.complete(true);dialog.dispose();});
            actions.add(reject);actions.add(accept);
            box.add(actions,BorderLayout.SOUTH);
            dialog.setContentPane(box);dialog.pack();dialog.setMinimumSize(new Dimension(Math.max(500,dialog.getWidth()),dialog.getHeight()));
            dialog.getRootPane().registerKeyboardAction(e->reject.doClick(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
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
        refreshPeerCount();
    }
    private void report(String message){SwingUtilities.invokeLater(()->{
        if(!closed){
            Color color=message.contains("lỗi")||message.startsWith("Không thể")||message.contains("không hợp lệ")?RED:
                message.startsWith("Đã kết nối")||message.startsWith("Chat TCP đã kết nối")||message.startsWith("Đã tự kết nối")?GREEN:
                message.startsWith("Đang")?BLUE:MUTED;
            setState(status,message,color);refreshPeerCount();
        }
    });}
}
