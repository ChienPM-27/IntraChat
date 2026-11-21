package vn.intrachat.ui;

import vn.intrachat.application.AppInfo;
import vn.intrachat.domain.ConnectionState;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * M0 shell: renders only known, truthful state. No networking or persistence.
 * Must be created and accessed on Swing's Event Dispatch Thread.
 */
public final class MainWindow extends JFrame {
    private final JLabel connectionLabel = new JLabel(AppInfo.INITIAL_CONNECTION_LABEL);

    public MainWindow() {
        super(AppInfo.NAME);
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("MainWindow must be constructed on the Swing EDT");
        }
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(440, 260));
        setSize(640, 400);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel(new BorderLayout(0, 20));
        panel.setBorder(new EmptyBorder(28, 32, 28, 32));
        JPanel heading = new JPanel(new GridLayout(0, 1, 0, 10));
        JLabel title = new JLabel(AppInfo.NAME);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 28f));
        JLabel subheading = new JLabel("Nền tảng ứng dụng Chat LAN — Giai đoạn 01");
        subheading.setForeground(new Color(70, 75, 85));
        heading.add(title);
        heading.add(subheading);

        JPanel statusPanel = new JPanel(new GridLayout(0, 1, 0, 12));
        connectionLabel.setFont(connectionLabel.getFont().deriveFont(Font.BOLD, 16f));
        statusPanel.add(connectionLabel);
        statusPanel.add(new JLabel("Chưa có kết nối mạng, tin nhắn hoặc đồng bộ lịch sử."));
        statusPanel.add(new JLabel("M0: khung ứng dụng; tính năng P2P sẽ được bổ sung sau."));

        JButton exitButton = new JButton("Thoát");
        exitButton.addActionListener(event -> disposeAndExit());

        panel.add(heading, BorderLayout.NORTH);
        panel.add(statusPanel, BorderLayout.CENTER);
        panel.add(exitButton, BorderLayout.SOUTH);
        setContentPane(panel);
    }

    public String displayedConnectionState() {
        return connectionLabel.getText();
    }

    public ConnectionState connectionState() {
        return ConnectionState.initial();
    }

    private void disposeAndExit() {
        dispose();
        // No background workers in M0; ordinary frame close uses EXIT_ON_CLOSE.
        System.exit(0);
    }
}
