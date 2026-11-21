package vn.intrachat.ui;

import vn.intrachat.application.*;
import vn.intrachat.domain.PeerIdentity;
import vn.intrachat.transport.NetworkConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Windows M1 UI — explicit, unauthenticated P2P diagnostics only.
 * Every socket operation is submitted to a background worker.
 */
public final class NetworkPanel extends JPanel implements AutoCloseable {
    private final NetworkCoordinator coordinator;
    private final JTextField localIp = new JTextField("127.0.0.1", 14);
    private final JSpinner localPort = new JSpinner(new SpinnerNumberModel(NetworkConfig.DEFAULT_PORT, 1, 65535, 1));
    private final JTextField remoteIp = new JTextField("127.0.0.1", 14);
    private final JSpinner remotePort = new JSpinner(new SpinnerNumberModel(NetworkConfig.DEFAULT_PORT, 1, 65535, 1));
    private final JLabel status = new JLabel("Chưa kết nối — Chat chưa sẵn sàng");
    private final JLabel localLabel;
    private final JButton listen = new JButton("Bắt đầu lắng nghe");
    private final JButton stopListen = new JButton("Dừng lắng nghe");
    private final JButton connect = new JButton("Kết nối");
    private final JButton cancel = new JButton("Hủy");
    private final JButton disconnect = new JButton("Ngắt kết nối");
    private final JButton reconnect = new JButton("Kết nối lại");
    private final JButton ping = new JButton("Ping kiểm tra");
    private volatile UUID connectedRemote;
    private volatile boolean connecting;
    private volatile boolean closed;

    public NetworkPanel() throws IOException {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("UI must be created on EDT");
        setLayout(new BorderLayout(0, 16));
        setBorder(new EmptyBorder(16, 20, 16, 20));
        PeerIdentity me = LocalPeerProfile.loadOrCreate(LocalPeerProfile.defaultPath());
        coordinator = new NetworkCoordinator(me, request -> InboundRequestDialog.ask(this, request), this::onEvent);

        localLabel = new JLabel("ID máy này: " + me.deviceId() + " (không phải danh tính xác thực)");
        JLabel warning = new JLabel("<html><b>THỬ NGHIỆM M1: CHƯA XÁC THỰC, CHƯA MÃ HÓA.</b>"
            + " Chỉ dùng dữ liệu chẩn đoán. Không gửi Chat, History, File hoặc dữ liệu nhạy cảm.</html>");
        warning.setForeground(new Color(146, 46, 26));

        JPanel heading = new JPanel(new GridLayout(0, 1, 0, 9));
        JLabel name = new JLabel("IntraChat — Kết nối P2P LAN (M1)");
        name.setFont(name.getFont().deriveFont(Font.BOLD, 22f));
        heading.add(name);
        heading.add(warning);
        heading.add(localLabel);

        JPanel form = new JPanel(new GridLayout(0, 1, 0, 12));
        form.add(section("1. Máy của bạn — chọn IP mạng LAN và cổng lắng nghe", localIp, localPort, listen, stopListen));
        form.add(section("2. Kết nối trực tiếp — nhập IP và cổng của máy bên kia", remoteIp, remotePort,
            connect, cancel, disconnect, reconnect));
        JPanel diagnostics = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        diagnostics.add(new JLabel("3. Sau khi người nhận bấm Chấp nhận:"));
        diagnostics.add(ping);
        form.add(diagnostics);

        status.setFont(status.getFont().deriveFont(Font.BOLD, 14f));
        status.setBorder(new EmptyBorder(10, 2, 10, 2));

        add(heading, BorderLayout.NORTH);
        add(form, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        localPort.setEditor(new JSpinner.NumberEditor(localPort, "#####"));
        remotePort.setEditor(new JSpinner.NumberEditor(remotePort, "#####"));
        stopListen.setEnabled(false);
        cancel.setEnabled(false);
        disconnect.setEnabled(false);
        reconnect.setEnabled(false);
        ping.setEnabled(false);

        listen.addActionListener(e -> {
            NetworkConfig config = validate(localIp, localPort);
            if (config == null) return;
            listen.setEnabled(false);
            CompletableFuture.runAsync(() -> {
                try { coordinator.startListening(config); }
                catch (IOException failure) { throw new RuntimeException(failure); }
            }).whenComplete((ok, failure) -> SwingUtilities.invokeLater(() -> {
                if (failure != null) showError("Không mở được listener: " + message(failure));
                listen.setEnabled(!coordinator.isListening());
                stopListen.setEnabled(coordinator.isListening());
            }));
        });
        stopListen.addActionListener(e -> CompletableFuture.runAsync(coordinator::stopListening)
            .whenComplete((ok, failure) -> SwingUtilities.invokeLater(() -> {
                stopListen.setEnabled(false);
                listen.setEnabled(true);
            })));

        connect.addActionListener(e -> connectTo(true));
        reconnect.addActionListener(e -> {
            if (connecting) return;
            connecting = true;
            setPendingButtons();
            coordinator.reconnect().whenComplete((remote, error) ->
                SwingUtilities.invokeLater(() -> settleConnection(remote, error)));
        });
        cancel.addActionListener(e -> coordinator.cancelPendingConnect());
        disconnect.addActionListener(e -> {
            UUID peer = connectedRemote;
            if (peer != null) CompletableFuture.runAsync(() -> coordinator.disconnect(peer));
        });
        ping.addActionListener(e -> {
            UUID peer = connectedRemote;
            if (peer == null) return;
            ping.setEnabled(false);
            coordinator.ping(peer).whenComplete((duration, error) -> SwingUtilities.invokeLater(() -> {
                ping.setEnabled(connectedRemote != null);
                if (error != null) showError("Ping thất bại: " + message(error));
                else status.setText("PONG chẩn đoán: " + duration.toMillis() + " ms — chưa xác thực/chưa mã hóa");
            }));
        });
    }

    private JPanel section(String title, JTextField host, JSpinner port, JButton... buttons) {
        JPanel wrapper = new JPanel(new BorderLayout(0, 7));
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        wrapper.add(label, BorderLayout.NORTH);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        row.add(new JLabel("IPv4:"));
        row.add(host);
        row.add(new JLabel("Cổng:"));
        row.add(port);
        for (JButton button : buttons) row.add(button);
        wrapper.add(row, BorderLayout.CENTER);
        return wrapper;
    }

    private void connectTo(boolean fromFields) {
        if (connecting) return;
        NetworkConfig config = validate(remoteIp, remotePort);
        if (config == null) return;
        connecting = true;
        setPendingButtons();
        coordinator.connect(config).whenComplete((remote, error) ->
            SwingUtilities.invokeLater(() -> settleConnection(remote, error)));
    }

    private void settleConnection(PeerIdentity remote, Throwable error) {
        connecting = false;
        cancel.setEnabled(false);
        connect.setEnabled(true);
        reconnect.setEnabled(true);
        if (error != null) {
            showError("Kết nối thất bại hoặc bị từ chối: " + message(error));
        } else {
            connectedRemote = remote.deviceId();
            disconnect.setEnabled(true);
            ping.setEnabled(true);
        }
    }

    private void setPendingButtons() {
        connect.setEnabled(false);
        reconnect.setEnabled(false);
        cancel.setEnabled(true);
        status.setText("Đang kết nối... chờ người nhận Chấp nhận/Từ chối");
    }

    private static NetworkConfig validate(JTextField ipField, JSpinner portField) {
        try { return new NetworkConfig(ipField.getText().strip(), (Integer) portField.getValue()); }
        catch (RuntimeException invalid) {
            JOptionPane.showMessageDialog(ipField, "IP hoặc cổng IPv4 không hợp lệ: " + invalid.getMessage(),
                "Lỗi địa chỉ", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    private void onEvent(NetworkEvents.Event event) {
        SwingUtilities.invokeLater(() -> {
            if (closed) return;
            status.setText(event.text());
            if (event.kind() == NetworkEvents.Kind.CONNECTED) {
                connectedRemote = event.remoteDeviceId();
                disconnect.setEnabled(true);
                ping.setEnabled(true);
                cancel.setEnabled(false);
            } else if (event.kind() == NetworkEvents.Kind.DISCONNECTED) {
                if (event.remoteDeviceId() == null || event.remoteDeviceId().equals(connectedRemote)) {
                    connectedRemote = null;
                    ping.setEnabled(false);
                    disconnect.setEnabled(false);
                    reconnect.setEnabled(true);
                }
            }
        });
    }

    private void showError(String text) {
        status.setText(text);
    }

    private static String message(Throwable cause) {
        Throwable cursor = cause;
        while (cursor.getCause() != null) cursor = cursor.getCause();
        return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
    }

    public String statusText() { return status.getText(); }
    public boolean isConnected() { return coordinator.connectedCount() > 0; }

    @Override public void close() {
        closed = true;
        coordinator.close();
    }
}
