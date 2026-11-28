package ui.dialogs;

import ui.components.AvatarIcon;
import vn.intrachat.application.CallService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;

/** Live P2P Voice and Video Call window with ringing controls, PiP preview, mute/camera/screen-share. */
public final class CallDialog extends JDialog {
    private static final Color BG_DARK = new Color(17, 24, 39);
    private static final Color BAR_DARK = new Color(31, 41, 55);
    private static final Color TEXT_LIGHT = new Color(243, 244, 246);
    private static final Color TEXT_MUTED = new Color(156, 163, 175);
    private static final Color GREEN_ACCEPT = new Color(22, 163, 74);
    private static final Color RED_END = new Color(220, 38, 38);
    private static final Color BTN_NEUTRAL = new Color(55, 65, 81);
    private static final Color BTN_ACTIVE = new Color(37, 99, 235);

    private final Consumer<String> onAccept;
    private final Consumer<String> onReject;
    private final Consumer<String> onEnd;
    private final Consumer<Boolean> onMute;
    private final Consumer<Boolean> onCamera;
    private final Consumer<Boolean> onScreenShare;

    private final JLabel peerTitle = new JLabel("", SwingConstants.LEFT);
    private final JLabel statusLabel = new JLabel("", SwingConstants.LEFT);
    private final JLabel remoteBadge = new JLabel("", SwingConstants.RIGHT);
    private final VideoStage stage = new VideoStage();
    private final JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 10));
    private final javax.swing.Timer clockTimer;

    private CallService.CallSession session;
    private BufferedImage localFrame;
    private BufferedImage remoteFrame;
    private boolean closingByProgram;

    public CallDialog(Frame owner,
                      Consumer<String> onAccept,
                      Consumer<String> onReject,
                      Consumer<String> onEnd,
                      Consumer<Boolean> onMute,
                      Consumer<Boolean> onCamera,
                      Consumer<Boolean> onScreenShare) {
        super(owner, "IntraChat — Cuộc gọi P2P", ModalityType.MODELESS);
        this.onAccept = Objects.requireNonNull(onAccept);
        this.onReject = Objects.requireNonNull(onReject);
        this.onEnd = Objects.requireNonNull(onEnd);
        this.onMute = Objects.requireNonNull(onMute);
        this.onCamera = Objects.requireNonNull(onCamera);
        this.onScreenShare = Objects.requireNonNull(onScreenShare);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(760, 560);
        setMinimumSize(new Dimension(520, 420));
        setLocationRelativeTo(owner);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG_DARK);
        setContentPane(root);

        JPanel topBar = new JPanel(new BorderLayout(12, 2));
        topBar.setBackground(BAR_DARK);
        topBar.setBorder(new EmptyBorder(12, 18, 12, 18));
        JPanel titleCol = new JPanel(new GridLayout(2, 1, 0, 2));
        titleCol.setOpaque(false);
        peerTitle.setForeground(TEXT_LIGHT);
        peerTitle.setFont(peerTitle.getFont().deriveFont(Font.BOLD, 16f));
        statusLabel.setForeground(TEXT_MUTED);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12.5f));
        titleCol.add(peerTitle);
        titleCol.add(statusLabel);
        remoteBadge.setForeground(new Color(251, 191, 36));
        remoteBadge.setFont(remoteBadge.getFont().deriveFont(Font.PLAIN, 12f));
        topBar.add(titleCol, BorderLayout.CENTER);
        topBar.add(remoteBadge, BorderLayout.EAST);
        root.add(topBar, BorderLayout.NORTH);

        root.add(stage, BorderLayout.CENTER);

        actionBar.setBackground(BAR_DARK);
        actionBar.setBorder(new EmptyBorder(6, 12, 8, 12));
        root.add(actionBar, BorderLayout.SOUTH);

        clockTimer = new javax.swing.Timer(1000, e -> updateStatusText());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (closingByProgram) return;
                CallService.CallSession current = session;
                if (current != null && current.state() != CallService.State.ENDED) {
                    if (current.state() == CallService.State.INCOMING_RINGING) {
                        onReject.accept(current.callId().toString());
                    } else {
                        onEnd.accept(current.callId().toString());
                    }
                } else {
                    closeSilently();
                }
            }
        });
    }

    public void updateSession(CallService.CallSession next) {
        this.session = next;
        if (next == null) return;

        String modeText = next.mode() == CallService.Mode.VIDEO ? "Cuộc gọi video" : "Cuộc gọi thoại";
        setTitle("IntraChat — " + modeText + " · " + next.peerName());
        peerTitle.setText(next.peerName() + " (" + modeText + ")");

        if (next.state() == CallService.State.CONNECTED) {
            if (!clockTimer.isRunning()) clockTimer.start();
        } else {
            clockTimer.stop();
        }

        if (next.state() == CallService.State.ENDED) {
            localFrame = null;
            remoteFrame = null;
        }
        updateStatusText();
        updateRemoteBadge();
        rebuildButtons(next);
        stage.repaint();

        if (next.state() == CallService.State.ENDED) {
            closeSilently();
        } else if (!isVisible()) {
            setVisible(true);
            toFront();
        }
    }

    public void setLocalVideoFrame(BufferedImage frame) {
        this.localFrame = frame;
        stage.repaint();
    }

    public void setRemoteVideoFrame(BufferedImage frame) {
        this.remoteFrame = frame;
        stage.repaint();
    }

    public void closeSilently() {
        closingByProgram = true;
        clockTimer.stop();
        localFrame = null;
        remoteFrame = null;
        if (isDisplayable()) dispose();
        closingByProgram = false;
    }

    private void updateStatusText() {
        CallService.CallSession current = session;
        if (current == null) return;
        String text = switch (current.state()) {
            case OUTGOING_RINGING -> "Đang đổ chuông...";
            case INCOMING_RINGING -> current.mode() == CallService.Mode.VIDEO
                ? "Cuộc gọi video đến — bấm Nghe máy để tham gia"
                : "Cuộc gọi thoại đến — bấm Nghe máy để tham gia";
            case CONNECTED -> "Đã kết nối · " + formatElapsed(current.connectedAt());
            case ENDED -> current.reason() == null || current.reason().isBlank() ? "Đã kết thúc" : current.reason();
        };
        statusLabel.setText(text);
    }

    private void updateRemoteBadge() {
        CallService.CallSession current = session;
        if (current == null || current.state() != CallService.State.CONNECTED) {
            remoteBadge.setText("");
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (current.remoteMuted()) sb.append("Đối phương tắt mic");
        if (current.mode() == CallService.Mode.VIDEO && !current.remoteCameraOn()) {
            if (!sb.isEmpty()) sb.append(" · ");
            sb.append("Đối phương tắt camera");
        }
        remoteBadge.setText(sb.toString());
    }

    private void rebuildButtons(CallService.CallSession current) {
        actionBar.removeAll();
        String callId = current.callId().toString();
        switch (current.state()) {
            case INCOMING_RINGING -> {
                actionBar.add(actionButton("Nghe máy", GREEN_ACCEPT, () -> onAccept.accept(callId)));
                actionBar.add(actionButton("Từ chối", RED_END, () -> onReject.accept(callId)));
            }
            case OUTGOING_RINGING -> {
                actionBar.add(actionButton("Hủy cuộc gọi", RED_END, () -> onEnd.accept(callId)));
            }
            case CONNECTED -> {
                actionBar.add(actionButton(
                    current.muted() ? "Bật mic" : "Tắt mic",
                    current.muted() ? BTN_ACTIVE : BTN_NEUTRAL,
                    () -> onMute.accept(!current.muted())));
                if (current.mode() == CallService.Mode.VIDEO) {
                    actionBar.add(actionButton(
                        current.cameraOn() ? "Tắt cam" : "Bật cam",
                        !current.cameraOn() ? BTN_ACTIVE : BTN_NEUTRAL,
                        () -> onCamera.accept(!current.cameraOn())));
                    actionBar.add(actionButton(
                        current.screenShare() ? "Dùng webcam" : "Chia sẻ màn hình",
                        current.screenShare() ? BTN_ACTIVE : BTN_NEUTRAL,
                        () -> onScreenShare.accept(!current.screenShare())));
                }
                actionBar.add(actionButton("Kết thúc", RED_END, () -> onEnd.accept(callId)));
            }
            case ENDED -> {}
        }
        actionBar.revalidate();
        actionBar.repaint();
    }

    private static JButton actionButton(String text, Color bg, Runnable handler) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(10, 18, 10, 18));
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 13f));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> handler.run());
        return btn;
    }

    private static String formatElapsed(Instant start) {
        if (start == null) return "00:00";
        long secs = Math.max(0, Duration.between(start, Instant.now()).getSeconds());
        long mins = secs / 60;
        long rem = secs % 60;
        return String.format("%02d:%02d", mins, rem);
    }

    private final class VideoStage extends JPanel {
        VideoStage() {
            setBackground(BG_DARK);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            int w = getWidth();
            int h = getHeight();
            CallService.CallSession current = session;

            boolean hasRemoteVideo = current != null
                && current.mode() == CallService.Mode.VIDEO
                && current.state() == CallService.State.CONNECTED
                && current.remoteCameraOn()
                && remoteFrame != null;

            if (hasRemoteVideo) {
                drawAspectFit(g2, remoteFrame, 12, 12, w - 24, h - 24);
            } else {
                drawAvatarPlaceholder(g2, w, h, current);
            }

            // Picture-in-Picture local video preview
            if (current != null && current.mode() == CallService.Mode.VIDEO
                    && current.state() == CallService.State.CONNECTED
                    && current.cameraOn() && localFrame != null) {
                int pipW = Math.min(200, Math.max(120, w / 4));
                int pipH = (int) Math.round(pipW * ((double) localFrame.getHeight() / Math.max(1, localFrame.getWidth())));
                int pipX = w - pipW - 18;
                int pipY = h - pipH - 18;
                Shape clip = new RoundRectangle2D.Double(pipX, pipY, pipW, pipH, 12, 12);
                g2.setColor(new Color(0, 0, 0, 140));
                g2.fill(new RoundRectangle2D.Double(pipX - 2, pipY - 2, pipW + 4, pipH + 4, 14, 14));
                Shape oldClip = g2.getClip();
                g2.setClip(clip);
                g2.drawImage(localFrame, pipX, pipY, pipW, pipH, null);
                g2.setClip(oldClip);
                g2.setColor(new Color(255, 255, 255, 160));
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(clip);
            }

            g2.dispose();
        }

        private void drawAspectFit(Graphics2D g2, BufferedImage img, int x, int y, int maxW, int maxH) {
            if (maxW <= 0 || maxH <= 0) return;
            double scale = Math.min((double) maxW / img.getWidth(), (double) maxH / img.getHeight());
            int dw = Math.max(1, (int) Math.round(img.getWidth() * scale));
            int dh = Math.max(1, (int) Math.round(img.getHeight() * scale));
            int dx = x + (maxW - dw) / 2;
            int dy = y + (maxH - dh) / 2;
            g2.drawImage(img, dx, dy, dw, dh, null);
        }

        private void drawAvatarPlaceholder(Graphics2D g2, int w, int h, CallService.CallSession current) {
            String name = current == null ? "IntraChat" : current.peerName();
            String id = current == null ? "call" : current.peerId().toString();
            AvatarIcon avatar = new AvatarIcon(name, id, 96, current != null && current.state() == CallService.State.CONNECTED, false);
            int ax = (w - avatar.getIconWidth()) / 2;
            int ay = (h - avatar.getIconHeight()) / 2 - 22;
            avatar.paintIcon(this, g2, ax, ay);

            g2.setColor(TEXT_LIGHT);
            g2.setFont(getFont().deriveFont(Font.BOLD, 20f));
            FontMetrics fm = g2.getFontMetrics();
            int nx = (w - fm.stringWidth(name)) / 2;
            g2.drawString(name, nx, ay + avatar.getIconHeight() + 34);

            String sub = "";
            if (current != null) {
                sub = switch (current.state()) {
                    case OUTGOING_RINGING -> "Đang chờ đối phương nghe máy...";
                    case INCOMING_RINGING -> current.mode() == CallService.Mode.VIDEO ? "Cuộc gọi video đến..." : "Cuộc gọi thoại đến...";
                    case CONNECTED -> current.mode() == CallService.Mode.VIDEO
                        ? "Đang chờ hình ảnh từ đối phương..."
                        : "Đang trò chuyện bằng giọng nói (UDP LAN)";
                    case ENDED -> "Cuộc gọi đã kết thúc";
                };
            }
            g2.setColor(TEXT_MUTED);
            g2.setFont(getFont().deriveFont(Font.PLAIN, 13.5f));
            FontMetrics sfm = g2.getFontMetrics();
            int sx = (w - sfm.stringWidth(sub)) / 2;
            g2.drawString(sub, sx, ay + avatar.getIconHeight() + 58);
        }
    }
}
