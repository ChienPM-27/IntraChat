package ui.dialogs;

import vn.intrachat.application.*;
import vn.intrachat.domain.PeerIdentity;
import vn.intrachat.transport.NetworkConfig;
import ui.components.ChatStyle;
import static ui.components.ChatStyle.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Explicitly opt-in, UNENCRYPTED, UNAUTHENTICATED M1 technical diagnostics
 * inside the single Messenger launcher. Not a second app entrypoint.
 * This dialog does NOT expose any Chat/Group/File/History composer or payload.
 */
public final class LanDiagnosticsDialog extends JDialog {
    private final NetworkCoordinator network;
    private final DiagnosticService diagnostics;
    private final ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor();
    private final JTextField localIp=field("m1-local-ip","127.0.0.1",12);
    private final JTextField localPort=field("m1-local-port","47322",6);
    private final JTextField remoteIp=field("m1-remote-ip","127.0.0.1",12);
    private final JTextField remotePort=field("m1-remote-port","47322",6);
    private final JLabel status=new JLabel("Chưa kết nối chẩn đoán. Không gửi dữ liệu riêng tư.");
    private final JButton listen=button("m1-listen","Bắt đầu lắng nghe");
    private final JButton stop=button("m1-stop","Dừng lắng nghe");
    private final JButton connect=button("m1-connect","Kết nối kỹ thuật");
    private final JButton cancel=button("m1-cancel","Hủy kết nối");
    private final JButton reconnect=button("m1-reconnect","Kết nối lại thủ công");
    private final JButton disconnect=button("m1-disconnect","Ngắt kết nối");
    private final JButton ping=button("m1-ping","Ping kiểm tra");
    private volatile boolean closed;
    private UUID selectedPeer;

    public LanDiagnosticsDialog(Window owner, PeerIdentity localIdentity){
        super(owner,"IntraChat — Chẩn đoán TCP v1 (KHÔNG MÃ HÓA)",
            Dialog.ModalityType.MODELESS);
        Objects.requireNonNull(localIdentity,"localIdentity");
        network=new NetworkCoordinator(localIdentity,this::approveInbound,this::onNetworkEvent);
        diagnostics=new DiagnosticService(network);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(890, 680);
        setMinimumSize(new Dimension(800, 610));
        setLocationRelativeTo(owner);

        JPanel root = panel(new BorderLayout(0, 14));
        root.setBorder(new EmptyBorder(20, 22, 18, 22));
        setContentPane(root);
        JPanel header = panel(new GridLayout(3, 1, 0, 4));
        header.add(label("Chẩn đoán kết nối LAN", 22, Font.BOLD, INK));
        header.add(label("Công cụ kiểm tra mạng · Không dùng để gửi tin nhắn",
            13, Font.PLAIN, MUTED));
        JLabel warning = label("KHÔNG MÃ HÓA / KHÔNG XÁC THỰC — chỉ thử PING/PONG",
            13, Font.BOLD, RED);
        warning.setName("m1-untrusted-warning");
        warning.getAccessibleContext().setAccessibleName(
            "Cảnh báo chẩn đoán TCP v1 không mã hóa và không xác thực");
        header.add(warning);
        root.add(header, BorderLayout.NORTH);

        JPanel center = panel(new GridLayout(3, 1, 0, 12));
        JPanel host = dialogCard(new BorderLayout(0, 8));
        host.setName("m1-listen-card");
        host.add(label("Lắng nghe TCP (kỹ thuật)", 15, Font.BOLD, INK),
            BorderLayout.NORTH);
        JPanel hostInputs = panel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        hostInputs.add(label("IPv4", 12, Font.BOLD, MUTED));
        hostInputs.add(localIp);
        hostInputs.add(label("Cổng", 12, Font.BOLD, MUTED));
        hostInputs.add(localPort);
        host.add(hostInputs, BorderLayout.CENTER);
        JPanel hostButtons = panel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        hostButtons.add(stop);
        hostButtons.add(listen);
        host.add(hostButtons, BorderLayout.SOUTH);
        center.add(host);

        JPanel join = dialogCard(new BorderLayout(0, 8));
        join.setName("m1-connect-card");
        join.add(label("Kết nối để thử PING/PONG", 15, Font.BOLD, INK),
            BorderLayout.NORTH);
        JPanel peerInputs = panel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        peerInputs.add(label("Peer IPv4", 12, Font.BOLD, MUTED));
        peerInputs.add(remoteIp);
        peerInputs.add(label("Cổng", 12, Font.BOLD, MUTED));
        peerInputs.add(remotePort);
        join.add(peerInputs, BorderLayout.CENTER);
        JPanel connectButtons = panel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        connectButtons.add(cancel);
        connectButtons.add(connect);
        join.add(connectButtons, BorderLayout.SOUTH);
        center.add(join);

        JPanel operations = dialogCard(new BorderLayout(0, 10));
        operations.setName("m1-actions-card");
        operations.add(label("Phiên và thử nghiệm", 15, Font.BOLD, INK),
            BorderLayout.NORTH);
        JPanel actions = panel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.add(reconnect);
        actions.add(disconnect);
        actions.add(ping);
        operations.add(actions, BorderLayout.CENTER);
        operations.add(label("TCP v1 không mã hóa; chỉ gửi PING/PONG kỹ thuật.",
            12, Font.PLAIN, RED), BorderLayout.SOUTH);
        center.add(operations);
        root.add(center, BorderLayout.CENTER);

        status.setName("m1-status");
        status.getAccessibleContext().setAccessibleName("Trạng thái chẩn đoán TCP v1");
        status.setForeground(INK);
        status.setOpaque(true);
        status.setBackground(SELECTED);
        status.setBorder(new EmptyBorder(10, 12, 10, 12));
        root.add(status, BorderLayout.SOUTH);

        stop.setEnabled(false);
        cancel.setEnabled(false);
        reconnect.setEnabled(false);
        disconnect.setEnabled(false);
        ping.setEnabled(false);
        listen.addActionListener(e->startListener());
        stop.addActionListener(e->workers.submit(()->{
            network.stopListening();
            ui(()->{listen.setEnabled(true);stop.setEnabled(false);
                setStatus("Đã dừng cổng chẩn đoán");});
        }));
        connect.addActionListener(e->connect(false));
        reconnect.addActionListener(e->connect(true));
        cancel.addActionListener(e->workers.submit(network::cancelPendingConnect));
        disconnect.addActionListener(e->workers.submit(()->{
            network.disconnectAll();
            ui(()->{selectedPeer=null;disconnect.setEnabled(false);
                ping.setEnabled(false);setStatus("Đã ngắt kết nối M1; chỉ thử lại thủ công.");});
        }));
        ping.addActionListener(e->sendProbe());
        addWindowListener(new WindowAdapter(){
            @Override public void windowClosed(WindowEvent ignored){shutdown();}
        });
    }

    /** Resource close is synchronous even when Swing posts windowClosed later. */
    @Override public void dispose(){
        shutdown();
        super.dispose();
    }
    private synchronized void shutdown(){
        if(closed)return;
        closed=true;
        network.close();
        workers.shutdownNow();
    }

    private static JTextField field(String name, String defaultValue, int columns) {
        JTextField result = new JTextField(defaultValue, columns);
        dialogField(result, name.contains("port") ? "Cổng TCP" : "Địa chỉ IPv4");
        result.setName(name);
        result.getAccessibleContext().setAccessibleName(name);
        return result;
    }

    private static JButton button(String name, String caption) {
        JButton result = "m1-listen".equals(name) || "m1-connect".equals(name)
            ? primary(caption) : secondary(caption);
        result.setName(name);
        result.getAccessibleContext().setAccessibleName(caption);
        return result;
    }
    private NetworkConfig config(JTextField ip,JTextField port){
        return new NetworkConfig(ip.getText().trim(),Integer.parseInt(port.getText().trim()));
    }
    private void startListener(){
        final NetworkConfig target;
        try{target=config(localIp,localPort);}
        catch(RuntimeException invalid){setStatus("IPv4/cổng không hợp lệ");return;}
        listen.setEnabled(false);
        setStatus("Đang mở listener kỹ thuật...");
        workers.submit(()->{
            try{
                network.startListening(target);
                ui(()->{stop.setEnabled(true);setStatus("Đang lắng nghe chẩn đoán "
                    +target.ipv4()+":"+target.port());});
            }catch(IOException|RuntimeException failure){
                ui(()->{listen.setEnabled(true);
                    setStatus("Không mở được cổng kỹ thuật: "+failure.getClass().getSimpleName());});
            }
        });
    }
    private void connect(boolean manualRetry){
        final NetworkConfig target;
        try{target=manualRetry?null:config(remoteIp,remotePort);}
        catch(RuntimeException invalid){setStatus("Peer IPv4/cổng không hợp lệ");return;}
        connect.setEnabled(false);
        reconnect.setEnabled(false);
        cancel.setEnabled(true);
        ping.setEnabled(false);
        setStatus("Đang kết nối kỹ thuật; đợi Chấp nhận thủ công...");
        CompletableFuture<PeerIdentity> pending=manualRetry?network.reconnect():network.connect(target);
        pending.whenComplete((peer,failure)->ui(()->{
            connect.setEnabled(true);cancel.setEnabled(false);
            reconnect.setEnabled(true);
            if(failure!=null){
                setStatus("Kết nối chẩn đoán thất bại hoặc bị từ chối; thử thủ công.");
            }else{
                selectedPeer=peer.deviceId();
                disconnect.setEnabled(true);
                ping.setEnabled(network.isConnectedTo(selectedPeer));
                setStatus("READY kỹ thuật với "+peer.displayName()
                    +" — KHÔNG XÁC THỰC / KHÔNG MÃ HÓA.");
            }
        }));
    }
    private void sendProbe(){
        UUID peer=selectedPeer;
        if(peer==null||!network.isConnectedTo(peer)){
            ping.setEnabled(false);
            setStatus("Chưa có phiên READY để gửi Ping.");return;
        }
        ping.setEnabled(false);
        diagnostics.ping(peer).whenComplete((duration,failure)->ui(()->{
            ping.setEnabled(network.isConnectedTo(peer));
            if(failure!=null)setStatus("Ping thất bại: kết nối kỹ thuật không sẵn sàng.");
            else setStatus("PONG chẩn đoán "+duration.toMillis()+" ms — KHÔNG PHẢI CHAT/READ.");
        }));
    }
    private CompletionStage<InboundApprovalPort.Decision> approveInbound(InboundRequest request){
        CompletableFuture<InboundApprovalPort.Decision> decision=new CompletableFuture<>();
        decision.completeOnTimeout(InboundApprovalPort.Decision.REJECT,30,TimeUnit.SECONDS);
        ui(()->{
            if(closed){decision.complete(InboundApprovalPort.Decision.REJECT);return;}
            JDialog prompt = new JDialog(this, "Chấp nhận chẩn đoán KHÔNG MÃ HÓA?",
                Dialog.ModalityType.MODELESS);
            prompt.setName("m1-approval-dialog");
            JPanel body = panel(new BorderLayout(0, 14));
            body.setBorder(new EmptyBorder(18, 20, 18, 20));
            body.add(label("Xác nhận kết nối chẩn đoán", 18, Font.BOLD, INK),
                BorderLayout.NORTH);
            JPanel details = dialogCard(new BorderLayout(0, 8));
            JLabel explanation = new JLabel(
                "<html><b>TCP v1 chưa xác thực và không mã hóa.</b><br>"
                + "Tên thiết bị tự khai: " + escape(request.claimedPeer().displayName()) + "<br>"
                + "Device ID tự khai: " + request.claimedPeer().deviceId() + "<br>"
                + "Chỉ cho phép PING/PONG để kiểm tra mạng, không gửi chat/file.</html>");
            details.add(explanation, BorderLayout.CENTER);
            body.add(details, BorderLayout.CENTER);
            JPanel buttons = panel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            JButton deny = secondary("Từ chối");
            JButton accept = primary("Chấp nhận kỹ thuật");
            deny.setName("m1-reject");
            accept.setName("m1-approve");
            deny.addActionListener(e -> {
                decision.complete(InboundApprovalPort.Decision.REJECT);
                prompt.dispose();
            });
            accept.addActionListener(e -> {
                decision.complete(InboundApprovalPort.Decision.ACCEPT);
                prompt.dispose();
            });
            buttons.add(deny);
            buttons.add(accept);
            body.add(buttons, BorderLayout.SOUTH);
            prompt.setContentPane(body);
            prompt.getRootPane().registerKeyboardAction(
                e -> { decision.complete(InboundApprovalPort.Decision.REJECT); prompt.dispose(); },
                KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
            prompt.addWindowListener(new WindowAdapter(){
                @Override public void windowClosed(WindowEvent ignored){
                    decision.complete(InboundApprovalPort.Decision.REJECT);
                }
            });
            decision.whenComplete((result,error)->ui(prompt::dispose));
            prompt.pack();
            prompt.setMinimumSize(new Dimension(620, 280));
            prompt.setLocationRelativeTo(this);
            prompt.setVisible(true);
        });
        return decision;
    }
    private void onNetworkEvent(NetworkEvents.Event event){
        ui(()->{
            switch(event.kind()){
                case CONNECTED -> {
                    // Inbound consent reaches READY without an outbound connect future.
                    selectedPeer=event.remoteDeviceId();
                    disconnect.setEnabled(selectedPeer!=null);
                    ping.setEnabled(selectedPeer!=null&&network.isConnectedTo(selectedPeer));
                    setStatus("READY kỹ thuật — peer chỉ tự khai, KHÔNG MÃ HÓA.");
                }
                case DISCONNECTED -> {
                    if(event.remoteDeviceId()!=null
                        && event.remoteDeviceId().equals(selectedPeer)){
                        selectedPeer=null;ping.setEnabled(false);disconnect.setEnabled(false);
                    }
                    setStatus("Phiên kỹ thuật đã ngắt; không tự kết nối lại.");
                }
                case FAILED -> setStatus("Chẩn đoán M1 lỗi: "+safe(event.text()));
                case WAITING_REMOTE_APPROVAL -> setStatus("Đang chờ người nhận Chấp nhận — chưa READY.");
                case LISTENER_STOPPED -> {listen.setEnabled(true);stop.setEnabled(false);}
                case DIAGNOSTIC -> setStatus("PONG kỹ thuật, không phải tin nhắn.");
                default -> { /* status updated by the initiating action */ }
            }
        });
    }
    private static String safe(String text){
        if(text==null)return "lỗi kết nối";
        return text.length()>180?text.substring(0,180):text;
    }
    private static String escape(String text){
        return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
    private void setStatus(String message){status.setText(message);}
    private void ui(Runnable change){
        SwingUtilities.invokeLater(()->{if(!closed)change.run();});
    }
}