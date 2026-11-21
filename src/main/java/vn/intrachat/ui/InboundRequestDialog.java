package vn.intrachat.ui;

import vn.intrachat.application.InboundApprovalPort;
import vn.intrachat.application.InboundRequest;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Non-modal explicit consent for every inbound LAN candidate.
 * Production never auto-accepts and never records a trusted device.
 */
public final class InboundRequestDialog {
    private InboundRequestDialog() {}

    public static CompletableFuture<InboundApprovalPort.Decision> ask(Component parent, InboundRequest request) {
        CompletableFuture<InboundApprovalPort.Decision> response = new CompletableFuture<>();
        SwingUtilities.invokeLater(() -> {
            if (response.isDone()) return;
            Window owner = SwingUtilities.getWindowAncestor(parent);
            JDialog dialog = new JDialog(owner, "Yêu cầu kết nối LAN đến", Dialog.ModalityType.MODELESS);
            dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            JPanel content = new JPanel(new BorderLayout(0, 14));
            content.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
            JPanel description = new JPanel(new GridLayout(0, 1, 0, 8));
            description.add(new JLabel("Địa chỉ mạng thực: " + request.remoteEndpoint()));
            description.add(new JLabel("Tên tự khai: " + request.claimedPeer().displayName()));
            description.add(new JLabel("ID tự khai: " + request.claimedPeer().deviceId()));
            JLabel warning = new JLabel("<html><b>CHƯA XÁC THỰC & CHƯA MÃ HÓA.</b>"
                + " Chỉ cho phép dữ liệu thử nghiệm PING/PONG."
                + " Chấp nhận không có nghĩa đã tin cậy thiết bị hoặc cấp quyền History/File.</html>");
            warning.setForeground(new Color(146, 46, 26));
            JLabel remaining = new JLabel();
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton reject = new JButton("Từ chối");
            JButton accept = new JButton("Chấp nhận");
            buttons.add(reject);
            buttons.add(accept);
            JPanel center = new JPanel(new BorderLayout(0, 10));
            center.add(description, BorderLayout.NORTH);
            center.add(warning, BorderLayout.CENTER);
            center.add(remaining, BorderLayout.SOUTH);
            content.add(center, BorderLayout.CENTER);
            content.add(buttons, BorderLayout.SOUTH);
            dialog.setContentPane(content);
            dialog.setMinimumSize(new Dimension(550, 270));
            dialog.pack();
            dialog.setLocationRelativeTo(parent);
            final Timer[] timerHolder = new Timer[1];
            Runnable rejectAndClose = () -> response.complete(InboundApprovalPort.Decision.REJECT);
            reject.addActionListener(event -> rejectAndClose.run());
            accept.addActionListener(event -> {
                if (Instant.now().isBefore(request.deadline())) {
                    response.complete(InboundApprovalPort.Decision.ACCEPT);
                } else {
                    rejectAndClose.run();
                }
            });
            dialog.addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent event) { rejectAndClose.run(); }
                @Override public void windowClosed(WindowEvent event) { rejectAndClose.run(); }
            });
            Timer ticker = new Timer(500, event -> {
                long millis = Duration.between(Instant.now(), request.deadline()).toMillis();
                remaining.setText("Tự từ chối sau " + Math.max(0, (millis + 999) / 1000) + " giây");
                if (millis <= 0) rejectAndClose.run();
            });
            timerHolder[0] = ticker;
            response.whenComplete((decision, failure) -> SwingUtilities.invokeLater(() -> {
                ticker.stop();
                if (dialog.isDisplayable()) dialog.dispose();
            }));
            ticker.start();
            dialog.setVisible(true); // modeless; EDT remains responsive.
        });
        return response;
    }
}
