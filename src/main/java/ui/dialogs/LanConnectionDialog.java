package ui.dialogs;

import ui.frames.LoginFrame;
import ui.frames.MainChatFrame;

import listener.LiveChatAdapter;
import ui.components.ChatStyle;
import static ui.components.ChatStyle.*;
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
public final class LanConnectionDialog extends JDialog {
    private final LoginFrame launcher;
    private final DeviceIdentity identity;
    private final String displayName;
    private final LiveChatService liveService;
    private final SecureChatCoordinator secure;
    private final NetworkCoordinator m1Diagnostics;
    private final SharedProtocolListener sharedListener;
    private final ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor();
    private final Map<UUID,String> knownNames=new ConcurrentHashMap<>();
    private final JTextField localIp=new JTextField("127.0.0.1",16);
    private final JTextField localPort=new JTextField("47321",6);
    private final JTextField remoteIp=new JTextField("127.0.0.1",16);
    private final JTextField remotePort=new JTextField("47321",6);
    private final JLabel status=new JLabel("Chưa kết nối. Chỉ Chat Live khi hai thiết bị xác minh.");
    private JButton listen,stop,connect,disconnect;
    private MainChatFrame chatFrame;
    private boolean closed;

    public LanConnectionDialog(LoginFrame launcher,DeviceIdentity identity,PinnedPeerStore pinnedPeers){
        super((Frame)null,"IntraChat — Kết nối LAN",Dialog.ModalityType.MODELESS);
        this.launcher=Objects.requireNonNull(launcher);
        launcher.setEnabled(false); // Demo and Live cannot be operated concurrently.
        this.identity=Objects.requireNonNull(identity);
        this.displayName="Thiết bị "+identity.deviceId().toString().substring(0,8);
        this.liveService=new LiveChatService(identity);
        this.secure=new SecureChatCoordinator(identity,displayName,
            Objects.requireNonNull(pinnedPeers),this::approveInbound,this::verifyFingerprint,
            this::acceptSession);
        this.m1Diagnostics=new NetworkCoordinator(
            new PeerIdentity(identity.deviceId(),displayName+" (diagnostics v1)"),
            this::approveM1Diagnostic,event->{
                if(event.kind()==NetworkEvents.Kind.CONNECTED)
                    report("Đã chấp nhận chẩn đoán TCP v1 KHÔNG mã hóa. Không gửi chat.");
                else if(event.kind()==NetworkEvents.Kind.DISCONNECTED)
                    report("Đã ngắt phiên chẩn đoán TCP v1.");
            });
        this.sharedListener=new SharedProtocolListener(
            m1Diagnostics::acceptSharedCandidate,secure::acceptSharedCandidate);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(850, 600);
        setMinimumSize(new Dimension(800, 560));
        setLocationRelativeTo(launcher);

        JPanel content = panel(new BorderLayout(0, 16));
        content.setBorder(new EmptyBorder(22, 24, 20, 24));
        setContentPane(content);
        JPanel header = panel(new BorderLayout(0, 6));
        header.add(label("Kết nối trong mạng LAN", 23, Font.BOLD, INK), BorderLayout.NORTH);
        JPanel subtitles = panel(new GridLayout(2, 1, 0, 3));
        subtitles.add(label("Nhắn tin với bạn bè trên cùng Wi-Fi hoặc mạng nội bộ",
            13, Font.PLAIN, MUTED));
        subtitles.add(label("Thiết bị của bạn: " + displayName
            + " · Tin nhắn hiện chỉ lưu trong phiên", 12, Font.PLAIN, MUTED));
        header.add(subtitles, BorderLayout.SOUTH);
        content.add(header, BorderLayout.NORTH);

        JPanel sections = panel(new GridLayout(2, 1, 0, 14));
        JPanel hosting = dialogCard(new BorderLayout(0, 12));
        hosting.setName("m2-listen-card");
        hosting.add(cardHeading("Chờ bạn bè kết nối",
            "Mở cổng trên máy này để người khác có thể tham gia."), BorderLayout.NORTH);
        dialogField(localIp, "IPv4 lắng nghe");
        localIp.setName("m2-listen-ip");
        dialogField(localPort, "Cổng TCP");
        localPort.setName("m2-listen-port");
        JPanel hostFields = panel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        hostFields.add(label("IPv4", 12, Font.BOLD, MUTED));
        hostFields.add(localIp);
        hostFields.add(label("Cổng", 12, Font.BOLD, MUTED));
        hostFields.add(localPort);
        hosting.add(hostFields, BorderLayout.CENTER);
        listen = primary("Bắt đầu lắng nghe");
        listen.setName("m2-listen");
        stop = secondary("Dừng");
        stop.setName("m2-stop");
        JPanel hostActions = panel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        hostActions.add(stop);
        hostActions.add(listen);
        hosting.add(hostActions, BorderLayout.SOUTH);
        sections.add(hosting);

        JPanel joining = dialogCard(new BorderLayout(0, 12));
        joining.setName("m2-connect-card");
        joining.add(cardHeading("Kết nối với bạn bè",
            "Nhập địa chỉ IP và cổng của máy đang lắng nghe."), BorderLayout.NORTH);
        dialogField(remoteIp, "IPv4 người nhận");
        remoteIp.setName("m2-connect-ip");
        dialogField(remotePort, "Cổng TCP");
        remotePort.setName("m2-connect-port");
        JPanel peerFields = panel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        peerFields.add(label("IPv4", 12, Font.BOLD, MUTED));
        peerFields.add(remoteIp);
        peerFields.add(label("Cổng", 12, Font.BOLD, MUTED));
        peerFields.add(remotePort);
        joining.add(peerFields, BorderLayout.CENTER);
        connect = primary("Kết nối & xác minh");
        connect.setName("m2-connect");
        disconnect = secondary("Ngắt kết nối");
        disconnect.setName("m2-disconnect");
        disconnect.setEnabled(false);
        disconnect.addActionListener(e -> {
            for (UUID peer : liveService.connectedPeers().keySet()) secure.disconnect(peer);
            report("Đã yêu cầu ngắt phiên Chat LAN.");
        });
        JPanel peerActions = panel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        peerActions.add(disconnect);
        peerActions.add(connect);
        joining.add(peerActions, BorderLayout.SOUTH);
        sections.add(joining);
        content.add(sections, BorderLayout.CENTER);

        JPanel footer = panel(new BorderLayout(0, 10));
        JLabel help = label("Khi kết nối, hai máy cần chấp nhận và so khớp mã xác minh.",
            12, Font.PLAIN, MUTED);
        footer.add(help, BorderLayout.NORTH);
        status.setName("m2-status");
        status.setForeground(INK);
        status.setOpaque(true);
        status.setBackground(SELECTED);
        status.setBorder(new EmptyBorder(10, 12, 10, 12));
        footer.add(status, BorderLayout.CENTER);
        JButton openM1Diagnostics = secondary("Chẩn đoán mạng (nâng cao)");
        openM1Diagnostics.setName("open-m1-diagnostics");
        openM1Diagnostics.setToolTipText(
            "Chỉ dùng PING/PONG kỹ thuật TCP v1 không mã hóa; không gửi tin nhắn.");
        openM1Diagnostics.getAccessibleContext().setAccessibleName(
            "Mở chẩn đoán TCP v1 không mã hóa và không xác thực");
        openM1Diagnostics.addActionListener(e -> new LanDiagnosticsDialog(this,
            new PeerIdentity(identity.deviceId(), "M1 technical diagnostics")).setVisible(true));
        JPanel tools = panel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        tools.add(openM1Diagnostics);
        footer.add(tools, BorderLayout.SOUTH);
        content.add(footer, BorderLayout.SOUTH);

        listen.addActionListener(e->start());
        stop.addActionListener(e->{
            sharedListener.close();
            listen.setEnabled(true);stop.setEnabled(false);
            report("Đã dừng listener chung TCP v1/v2. Phiên đã có được ngắt riêng.");
        });
        connect.addActionListener(e->connect());
        stop.setEnabled(false);
        addWindowListener(new WindowAdapter(){
            @Override public void windowClosing(WindowEvent e){
                if(chatFrame!=null&&chatFrame.isDisplayable())setVisible(false);
                else dispose();
            }
            @Override public void windowClosed(WindowEvent e){
                if(closed)return;
                closed=true;
                workers.shutdownNow();
                if(chatFrame!=null&&chatFrame.isDisplayable())chatFrame.dispose();
                sharedListener.close();
                m1Diagnostics.close();
                secure.close();
                liveService.close();
                if(launcher.isDisplayable())launcher.setEnabled(true);
            }
        });
    }
    private static JPanel cardHeading(String title, String description) {
        JPanel heading = panel(new GridLayout(2, 1, 0, 3));
        heading.add(label(title, 16, Font.BOLD, INK));
        heading.add(label(description, 12, Font.PLAIN, MUTED));
        return heading;
    }
    private void start(){
        final NetworkConfig config;
        try{config=new NetworkConfig(localIp.getText().trim(),Integer.parseInt(localPort.getText().trim()));}
        catch(RuntimeException invalid){report("IPv4/cổng không hợp lệ: "+invalid.getMessage());return;}
        report("Đang mở listener "+config.ipv4()+":"+config.port());
        workers.submit(()->{
            try{sharedListener.start(config);
                report("Đang lắng nghe "+config.ipv4()+":"+config.port()
                    +" — v1 CHỈ chẩn đoán plaintext; v2 yêu cầu TLS/SAS.");
                SwingUtilities.invokeLater(()->{listen.setEnabled(false);stop.setEnabled(true);});}
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
    /**
     * Dedicated M1 consent. Never reuse the secure Chat Live approval text:
     * plaintext diagnostic identities are self-declared, not verified.
     */
    private CompletionStage<InboundApprovalPort.Decision> approveM1Diagnostic(InboundRequest incoming){
        CompletableFuture<InboundApprovalPort.Decision> result=new CompletableFuture<>();
        result.completeOnTimeout(InboundApprovalPort.Decision.REJECT,30,TimeUnit.SECONDS);
        SwingUtilities.invokeLater(()->{
            if(closed){result.complete(InboundApprovalPort.Decision.REJECT);return;}
            String description="<html><b>Chẩn đoán TCP v1 — KHÔNG MÃ HÓA / KHÔNG XÁC THỰC</b><br>"
                +"Endpoint mạng quan sát: "+escape(incoming.remoteEndpoint())+"<br>"
                +"Tên tự khai: "+escape(incoming.claimedPeer().displayName())+"<br>"
                +"UUID tự khai: "+incoming.claimedPeer().deviceId()+"<br>"
                +"<b>Chỉ được gửi PING/PONG tổng hợp, KHÔNG gửi Chat/File/Group.</b><br>"
                +"Accept không xác minh danh tính và không tạo trust pin.</html>";
            showDecisionDialog("Cho phép chẩn đoán TCP v1 không mã hóa?",description,
                "Chấp nhận chẩn đoán","Từ chối",result,
                InboundApprovalPort.Decision.ACCEPT,InboundApprovalPort.Decision.REJECT);
        });
        return result;
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
            JPanel verification = panel(new BorderLayout(0, 12));
            JPanel codeCard = dialogCard(new BorderLayout(0, 5));
            codeCard.setName("m2-sas-code-card");
            codeCard.add(label("So khớp mã này trên cả hai thiết bị", 13,
                Font.BOLD, INK), BorderLayout.NORTH);
            JLabel code = label(compare.sharedCode(), 28, Font.BOLD, BLUE);
            code.setName("m2-sas-code");
            code.setHorizontalAlignment(SwingConstants.CENTER);
            code.setFont(new Font("Consolas", Font.BOLD, 28));
            codeCard.add(code, BorderLayout.CENTER);
            verification.add(codeCard, BorderLayout.NORTH);
            JTextArea fingerprints = new JTextArea(
                "Thiết bị: " + compare.remoteDisplayName() + " · " + compare.remoteId()
                + "\\nMã thiết bị của bạn: " + compare.ownFullFingerprint()
                + "\\nMã thiết bị bên kia: " + compare.remoteFullFingerprint());
            fingerprints.setEditable(false);
            fingerprints.setFocusable(false);
            fingerprints.setLineWrap(true);
            fingerprints.setWrapStyleWord(true);
            fingerprints.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            fingerprints.setBackground(FIELD);
            fingerprints.setForeground(INK);
            fingerprints.setBorder(new EmptyBorder(12, 14, 12, 14));
            fingerprints.getAccessibleContext().setAccessibleName("Thông tin xác minh thiết bị");
            verification.add(fingerprints, BorderLayout.CENTER);
            verification.add(label("Nếu mã trên hai máy khác nhau, hãy chọn Từ chối.",
                12, Font.BOLD, RED), BorderLayout.SOUTH);
            showDecisionDialog("XÁC MINH DANH TÍNH THIẾT BỊ", verification,
                "Mã trùng — Xác minh", "Từ chối", choice, true, false);
        });
        return choice;
    }
    private <T> void showDecisionDialog(String title, String message, String yes,
            String no, CompletableFuture<T> promise, T accepted, T rejected) {
        JLabel description = new JLabel(message);
        description.setForeground(INK);
        JPanel body = panel(new BorderLayout());
        body.add(description, BorderLayout.CENTER);
        showDecisionDialog(title, body, yes, no, promise, accepted, rejected);
    }

    /**
     * Messenger-styled approval surface. Missing focus, ESC, window close and
     * expiry keep the existing fail-closed rejection behavior.
     */
    private <T> void showDecisionDialog(String title, JComponent body, String yes,
            String no, CompletableFuture<T> promise, T accepted, T rejected) {
        Window owner = chatFrame != null && chatFrame.isShowing() ? chatFrame : this;
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.MODELESS);
        dialog.setName("m2-decision-dialog");
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel outer = panel(new BorderLayout(0, 16));
        outer.setBorder(new EmptyBorder(20, 22, 18, 22));
        JPanel head = panel(new BorderLayout(0, 6));
        head.add(label(title, 19, Font.BOLD, INK), BorderLayout.NORTH);
        head.add(label("Xem kỹ thông tin trước khi chọn tiếp tục.",
            12, Font.PLAIN, MUTED), BorderLayout.SOUTH);
        outer.add(head, BorderLayout.NORTH);
        JPanel information = dialogCard(new BorderLayout());
        information.add(body, BorderLayout.CENTER);
        outer.add(information, BorderLayout.CENTER);
        JPanel footer = panel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton deny = secondary(no);
        JButton approve = primary(yes);
        deny.setName("m2-decision-reject");
        approve.setName("m2-decision-approve");
        approve.getAccessibleContext().setAccessibleName(yes);
        deny.getAccessibleContext().setAccessibleName(no);
        deny.addActionListener(e -> { promise.complete(rejected); dialog.dispose(); });
        approve.addActionListener(e -> { promise.complete(accepted); dialog.dispose(); });
        footer.add(deny);
        footer.add(approve);
        outer.add(footer, BorderLayout.SOUTH);
        dialog.setContentPane(outer);
        dialog.getRootPane().registerKeyboardAction(
            e -> { promise.complete(rejected); dialog.dispose(); },
            KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                promise.complete(rejected);
            }
        });
        promise.whenComplete((v,e) -> SwingUtilities.invokeLater(dialog::dispose));
        dialog.pack();
        dialog.setMinimumSize(new Dimension(650, 320));
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
                ()->{secure.close();SwingUtilities.invokeLater(this::dispose);},
                ()->SwingUtilities.invokeLater(()->{
                    if(!closed){setVisible(true);toFront();}
                }));
            chatFrame=new MainChatFrame(adapter.self(),adapter);
            chatFrame.setVisible(true);
            disconnect.setEnabled(true);
            setVisible(false);
            launcher.dispose();
        }else{
            chatFrame.toFront();
            chatFrame.requestFocus();
        }
    }
    private void report(String message){
        SwingUtilities.invokeLater(()->{if(!closed)status.setText(message);});
    }
}