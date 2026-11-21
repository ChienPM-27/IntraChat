package ui.frames;

import listener.LiveChatAdapter;
import ui.components.ChatStyle;
import vn.intrachat.application.*;
import vn.intrachat.domain.PeerIdentity;
import vn.intrachat.security.*;
import vn.intrachat.transport.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Explicit human-operated M2 Live launcher. Does not use the Demo login/password.
 * Live CHAT_TEXT is impossible until both sides approve and verify the same SAS.
 */
public final class LiveConnectionFrame extends JFrame {
    private final DeviceIdentity identity;
    private final String displayName;
    private final LiveChatService liveService;
    private final SecureChatCoordinator secure;
    private final ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor();
    private final Map<UUID,String> knownNames=new ConcurrentHashMap<>();
    private final JTextField localIp=new JTextField("127.0.0.1",16);
    private final JTextField localPort=new JTextField("47321",6);
    private final JTextField remoteIp=new JTextField("127.0.0.1",16);
    private final JTextField remotePort=new JTextField("47321",6);
    private final JLabel status=new JLabel("Chưa kết nối. Chỉ Chat Live khi hai thiết bị xác minh.");
    private JButton listen,stop,connect;
    private MainChatFrame chatFrame;
    private boolean closed;

    public LiveConnectionFrame(DeviceIdentity identity,PinnedPeerStore pinnedPeers){
        super("IntraChat — Live LAN / Xác minh thiết bị");
        this.identity=Objects.requireNonNull(identity);
        this.displayName="Thiết bị "+identity.deviceId().toString().substring(0,8);
        this.liveService=new LiveChatService(identity);
        this.secure=new SecureChatCoordinator(identity,displayName,
            Objects.requireNonNull(pinnedPeers),this::approveInbound,this::verifyFingerprint,
            this::acceptSession);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(670,500);
        setMinimumSize(new Dimension(610,420));
        setLocationRelativeTo(null);
        JPanel content=new JPanel(new BorderLayout(10,10));
        content.setBorder(new EmptyBorder(15,18,15,18));
        setContentPane(content);
        JLabel heading=new JLabel("IntraChat — Secure LAN Live");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD,20f));
        content.add(heading,BorderLayout.NORTH);
        JPanel center=new JPanel();
        center.setLayout(new BoxLayout(center,BoxLayout.Y_AXIS));
        JLabel security=new JLabel("<html><b>Chỉ gửi tin thật sau khi xác minh mã giữa hai máy.</b><br>"
          +"Kênh Chat Live: TLS 1.3; người nhận phải Chấp nhận từng lần kết nối.<br>"
          +"Đã xem nghĩa là tin đã hiển thị trong ứng dụng bên nhận, không chứng minh đã đọc.<br>"
          +"Tin và bản nháp chỉ trong phiên; thoát ứng dụng là mất. Không tự gửi lại.</html>");
        security.setBorder(new EmptyBorder(0,0,12,0));
        center.add(security);
        center.add(new JLabel("Thiết bị của bạn: "+displayName+" (danh tính hiển thị không phải xác thực)"));
        center.add(Box.createVerticalStrut(12));
        center.add(sectionTitle("1. Lắng nghe LAN (chủ động)"));
        JPanel own=new JPanel(new FlowLayout(FlowLayout.LEADING));
        own.add(new JLabel("IPv4"));own.add(localIp);
        own.add(new JLabel("TCP port"));own.add(localPort);
        listen=new JButton("Bắt đầu lắng nghe");
        stop=new JButton("Dừng");
        own.add(listen);own.add(stop);center.add(own);
        center.add(Box.createVerticalStrut(10));
        center.add(sectionTitle("2. Kết nối trực tiếp (IP/port thủ công)"));
        JPanel other=new JPanel(new FlowLayout(FlowLayout.LEADING));
        other.add(new JLabel("Peer IPv4"));other.add(remoteIp);
        other.add(new JLabel("Port"));other.add(remotePort);
        connect=new JButton("Kết nối & xác minh");
        other.add(connect);center.add(other);
        JButton demo=new JButton("Mở Demo Messenger riêng");
        demo.addActionListener(e->ui.IntraChat.main(new String[0]));
        JPanel demoRow=new JPanel(new FlowLayout(FlowLayout.LEADING));
        demoRow.add(demo);
        center.add(demoRow);
        content.add(center,BorderLayout.CENTER);
        status.setBorder(new EmptyBorder(12,2,0,2));
        status.getAccessibleContext().setAccessibleName("Trạng thái Live LAN");
        content.add(status,BorderLayout.SOUTH);

        listen.addActionListener(e->start());
        stop.addActionListener(e->{
            secure.stopListening();
            report("Đã dừng lắng nghe. Phiên đã có được ngắt riêng.");
        });
        connect.addActionListener(e->connect());
        addWindowListener(new WindowAdapter(){
            @Override public void windowClosed(WindowEvent e){
                if(closed)return;
                closed=true;
                workers.shutdownNow();
                if(chatFrame!=null&&chatFrame.isDisplayable())chatFrame.dispose();
                secure.close();
                liveService.close();
            }
        });
    }
    private static JLabel sectionTitle(String text){
        JLabel label=new JLabel(text);label.setFont(label.getFont().deriveFont(Font.BOLD,14f));
        return label;
    }
    private void start(){
        final NetworkConfig config;
        try{config=new NetworkConfig(localIp.getText().trim(),Integer.parseInt(localPort.getText().trim()));}
        catch(RuntimeException invalid){report("IPv4/cổng không hợp lệ: "+invalid.getMessage());return;}
        report("Đang mở listener "+config.ipv4()+":"+config.port());
        workers.submit(()->{
            try{secure.startListening(config);report("Đang lắng nghe "+config.ipv4()+":"+config.port());}
            catch(Exception failure){report("Không mở được listener: "+failure.getClass().getSimpleName());}
        });
    }
    private void connect(){
        final NetworkConfig config;
        try{config=new NetworkConfig(remoteIp.getText().trim(),Integer.parseInt(remotePort.getText().trim()));}
        catch(RuntimeException invalid){report("IPv4/cổng không hợp lệ: "+invalid.getMessage());return;}
        connect.setEnabled(false);
        report("Đang kết nối; đợi người nhận Chấp nhận và so mã fingerprint.");
        secure.connect(config).whenComplete((session,error)->SwingUtilities.invokeLater(()->{
            connect.setEnabled(true);
            if(error!=null){report("Kết nối/ghép cặp không thành công. Kiểm tra lại và thử thủ công.");return;}
            try{
                liveService.attach(session,knownNames.getOrDefault(session.remoteId(),
                        "Peer "+session.remoteId().toString().substring(0,8)));
                report("Đã xác minh TLS và hai phía READY. Chat Live sẵn sàng.");
                showMessenger();
            }catch(Exception failure){session.close();report("Không tạo phiên Chat Live: "+failure.getClass().getSimpleName());}
        }));
    }
    private CompletionStage<InboundApprovalPort.Decision> approveInbound(InboundRequest incoming){
        knownNames.put(incoming.claimedPeer().deviceId(),incoming.claimedPeer().displayName());
        CompletableFuture<InboundApprovalPort.Decision> choice=new CompletableFuture<>();
        choice.completeOnTimeout(InboundApprovalPort.Decision.REJECT,30,TimeUnit.SECONDS);
        SwingUtilities.invokeLater(()->{
            if(closed){choice.complete(InboundApprovalPort.Decision.REJECT);return;}
            String message="<html><b>Có yêu cầu Chat LAN đến</b><br>"
                +"Đầu TCP quan sát được: "+escape(incoming.remoteEndpoint())+"<br>"
                +"Tên tự khai (chưa tin): "+escape(incoming.claimedPeer().displayName())+"<br>"
                +"UUID tự khai: "+incoming.claimedPeer().deviceId()+"<br>"
                +"<b>Accept chỉ cho phép bắt đầu TLS, KHÔNG xác minh người gửi.</b><br>"
                +"Bạn sẽ phải so mã fingerprint ở bước tiếp theo.</html>";
            showDecisionDialog("Yêu cầu Chat Live — Chấp nhận kết nối?",message,
                "Chấp nhận","Từ chối",choice,InboundApprovalPort.Decision.ACCEPT,
                InboundApprovalPort.Decision.REJECT);
        });
        return choice;
    }
    private CompletionStage<Boolean> verifyFingerprint(FingerprintApprovalPort.Comparison compare){
        knownNames.put(compare.remoteId(),compare.remoteDisplayName());
        CompletableFuture<Boolean> choice=new CompletableFuture<>();
        choice.completeOnTimeout(false,60,TimeUnit.SECONDS);
        SwingUtilities.invokeLater(()->{
            if(closed){choice.complete(false);return;}
            String message="<html><b>So khớp mã dưới đây trên CẢ HAI máy qua kênh riêng:</b><br>"
                +"<h2>"+compare.sharedCode()+"</h2>"
                +"Peer: "+escape(compare.remoteDisplayName())+" · "+compare.remoteId()+"<br>"
                +"Fingerprint của bạn: "+compare.ownFullFingerprint()+"<br>"
                +"Fingerprint bên kia: "+compare.remoteFullFingerprint()+"<br>"
                +"<b>Nếu hai mã khác nhau, chọn Từ chối.</b><br>"
                +"Không chia sẻ nội dung chat trước khi cả hai xác minh.</html>";
            showDecisionDialog("XÁC MINH DANH TÍNH THIẾT BỊ",message,
                "Mã trùng — Xác minh","Từ chối",choice,true,false);
        });
        return choice;
    }
    private <T> void showDecisionDialog(String title,String message,String yes,String no,
                                        CompletableFuture<T> promise,T accepted,T rejected){
        Frame owner=chatFrame!=null&&chatFrame.isShowing()?chatFrame:this;
        JDialog dialog=new JDialog(owner,title,Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel panel=new JPanel(new BorderLayout(10,10));
        panel.setBorder(new EmptyBorder(16,18,16,18));
        JLabel text=new JLabel(message);
        panel.add(text,BorderLayout.CENTER);
        JPanel footer=new JPanel(new FlowLayout(FlowLayout.TRAILING));
        JButton deny=new JButton(no);
        JButton approve=new JButton(yes);
        approve.getAccessibleContext().setAccessibleName(yes);
        deny.getAccessibleContext().setAccessibleName(no);
        deny.addActionListener(e->{promise.complete(rejected);dialog.dispose();});
        approve.addActionListener(e->{promise.complete(accepted);dialog.dispose();});
        footer.add(deny);footer.add(approve);panel.add(footer,BorderLayout.SOUTH);
        dialog.setContentPane(panel);
        dialog.addWindowListener(new WindowAdapter(){
            @Override public void windowClosed(WindowEvent e){promise.complete(rejected);}
        });
        promise.whenComplete((v,e)->SwingUtilities.invokeLater(dialog::dispose));
        dialog.pack();
        dialog.setMinimumSize(new Dimension(500,220));
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }
    private static String escape(String source){
        return source.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
    private void acceptSession(TlsChatSession session){
        try{
            liveService.attach(session,knownNames.getOrDefault(session.remoteId(),
                "Peer "+session.remoteId().toString().substring(0,8)));
            report("Đã chấp nhận và xác minh phiên Chat Live qua TLS 1.3.");
            SwingUtilities.invokeLater(this::showMessenger);
        }catch(Exception failure){
            session.close();
            report("Không nhận được phiên Live: "+failure.getClass().getSimpleName());
        }
    }
    private void showMessenger(){
        if(closed)return;
        if(chatFrame==null||!chatFrame.isDisplayable()){
            LiveChatAdapter adapter=new LiveChatAdapter(identity,displayName,liveService,
                ()->{secure.close();SwingUtilities.invokeLater(this::dispose);});
            chatFrame=new MainChatFrame(adapter.self(),adapter);
            chatFrame.setVisible(true);
        }else{
            chatFrame.toFront();
            chatFrame.requestFocus();
        }
    }
    private void report(String message){
        SwingUtilities.invokeLater(()->{if(!closed)status.setText(message);});
    }
}
