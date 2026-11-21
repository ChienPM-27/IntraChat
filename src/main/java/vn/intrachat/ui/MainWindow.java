package vn.intrachat.ui;

import vn.intrachat.application.AppInfo;
import vn.intrachat.domain.ConnectionState;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** IntraChat M1 window: only explicit, plaintext, unauthenticated LAN diagnostics. */
public final class MainWindow extends JFrame {
    private final NetworkPanel networkPanel;
    private final AtomicBoolean shuttingDown = new AtomicBoolean();

    public MainWindow() {
        super(AppInfo.NAME);
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("MainWindow must be constructed on Swing EDT");
        }
        try {
            networkPanel = new NetworkPanel();
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load local device profile: " + failure.getMessage(), failure);
        }
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(700, 450));
        setSize(1050, 600);
        setLocationRelativeTo(null);

        JButton exitButton = new JButton("Thoát");
        exitButton.addActionListener(event -> disposeAndExit());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(exitButton);

        setLayout(new BorderLayout());
        add(networkPanel, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                disposeAndExit();
            }
        });
    }

    public String displayedConnectionState() { return networkPanel.statusText(); }
    public ConnectionState connectionState() {
        return networkPanel.isConnected() ? ConnectionState.CONNECTED : ConnectionState.DISCONNECTED;
    }

    private void disposeAndExit() {
        if (!shuttingDown.compareAndSet(false, true)) return;
        setEnabled(false);
        Thread.ofVirtual().name("intrachat-shutdown").start(() -> {
            try { networkPanel.close(); }
            finally {
                SwingUtilities.invokeLater(() -> {
                    dispose();
                    System.exit(0);
                });
            }
        });
    }
}
