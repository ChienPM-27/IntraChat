package vn.intrachat.application;

import org.bytedeco.videoinput.videoInput;
import vn.intrachat.transport.CallMediaTransport;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Realtime JPEG video engine for P2P and Group LAN calls.
 * Captures from Windows DirectShow webcam (via videoInput) or optional screen-share mode,
 * and decodes incoming video frames per remote participant.
 */
public final class VideoCallEngine implements AutoCloseable {
    private static final int P2P_W = 480;
    private static final int P2P_H = 360;
    private static final int GROUP_W = 360;
    private static final int GROUP_H = 270;
    private static final int SCREEN_W = 640;
    private static final int SCREEN_H = 360;
    private static final UUID P2P_SENDER = new UUID(0L, 0L);

    private final CallMediaTransport transport;
    private final UUID groupSenderId;
    private final int targetW;
    private final int targetH;
    private final float jpegQuality;
    private final long frameIntervalMs;
    private final Consumer<BufferedImage> onLocalFrame;
    private final BiConsumer<UUID, BufferedImage> onRemoteFrame;
    private final Consumer<String> onNotice;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean cameraOn = new AtomicBoolean(true);
    private final AtomicBoolean screenShare = new AtomicBoolean(false);
    private final ConcurrentHashMap<UUID, byte[]> latestRemoteJpegs = new ConcurrentHashMap<>();
    private final Object decodeSignal = new Object();

    private Thread captureThread;
    private Thread decodeThread;

    public VideoCallEngine(CallMediaTransport transport,
                           Consumer<BufferedImage> onLocalFrame,
                           Consumer<BufferedImage> onRemoteFrame,
                           Consumer<String> onNotice) {
        this(transport, null, onLocalFrame,
            onRemoteFrame == null ? (id, img) -> {} : (id, img) -> onRemoteFrame.accept(img),
            onNotice);
    }

    public VideoCallEngine(CallMediaTransport transport,
                           UUID groupSenderId,
                           Consumer<BufferedImage> onLocalFrame,
                           BiConsumer<UUID, BufferedImage> onRemoteFrame,
                           Consumer<String> onNotice) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.groupSenderId = groupSenderId;
        boolean group = groupSenderId != null;
        this.targetW = group ? GROUP_W : P2P_W;
        this.targetH = group ? GROUP_H : P2P_H;
        this.jpegQuality = group ? 0.58f : 0.65f;
        this.frameIntervalMs = group ? 83 : 66; // ~12 FPS for group, ~15 FPS for P2P
        this.onLocalFrame = onLocalFrame == null ? f -> {} : onLocalFrame;
        this.onRemoteFrame = onRemoteFrame == null ? (id, f) -> {} : onRemoteFrame;
        this.onNotice = onNotice == null ? s -> {} : onNotice;
    }

    public void start(boolean initiallyCameraOn) {
        this.cameraOn.set(initiallyCameraOn);
        if (!running.compareAndSet(false, true)) return;
        startCaptureLoop();
        startDecodeLoop();
    }

    public void setCameraOn(boolean on) {
        this.cameraOn.set(on);
        if (!on) {
            this.screenShare.set(false);
            onLocalFrame.accept(null);
        }
    }

    public boolean isCameraOn() {
        return cameraOn.get();
    }

    public void setScreenShare(boolean enabled) {
        this.screenShare.set(enabled);
        if (enabled) {
            this.cameraOn.set(true);
        }
    }

    public boolean isScreenShare() {
        return screenShare.get();
    }

    public void enqueueRemoteJpeg(byte[] jpeg) {
        enqueueRemoteJpeg(P2P_SENDER, jpeg);
    }

    public void enqueueRemoteJpeg(UUID senderId, byte[] jpeg) {
        if (!running.get() || jpeg == null || jpeg.length == 0) return;
        UUID key = senderId == null ? P2P_SENDER : senderId;
        latestRemoteJpegs.put(key, jpeg);
        synchronized (decodeSignal) {
            decodeSignal.notify();
        }
    }

    private void startDecodeLoop() {
        decodeThread = Thread.ofVirtual().name("intrachat-call-vdec").start(() -> {
            while (running.get()) {
                if (latestRemoteJpegs.isEmpty()) {
                    synchronized (decodeSignal) {
                        if (latestRemoteJpegs.isEmpty() && running.get()) {
                            try { decodeSignal.wait(150); } catch (InterruptedException ignored) {}
                        }
                    }
                    continue;
                }
                for (UUID senderId : latestRemoteJpegs.keySet()) {
                    byte[] jpeg = latestRemoteJpegs.remove(senderId);
                    if (jpeg == null) continue;
                    try {
                        BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpeg));
                        if (img != null) {
                            onRemoteFrame.accept(senderId, img);
                        }
                    } catch (Exception ignored) {
                        // Drop corrupted UDP frame silently
                    }
                }
            }
        });
    }

    private void startCaptureLoop() {
        captureThread = Thread.ofVirtual().name("intrachat-call-vcap").start(() -> {
            videoInput webcam = null;
            int webcamDevice = -1;
            byte[] webcamBuf = null;
            int camW = 0, camH = 0;
            boolean webcamTriedAndFailed = false;
            Robot robot = null;
            Rectangle screenBounds = null;

            try {
                while (running.get()) {
                    long startMs = System.currentTimeMillis();
                    if (!cameraOn.get()) {
                        if (webcam != null && webcamDevice >= 0) {
                            stopWebcam(webcam, webcamDevice);
                            webcam = null;
                            webcamDevice = -1;
                            webcamBuf = null;
                        }
                        webcamTriedAndFailed = false;
                        sleepQuiet(120);
                        continue;
                    }

                    BufferedImage frame = null;
                    if (screenShare.get()) {
                        if (webcam != null && webcamDevice >= 0) {
                            stopWebcam(webcam, webcamDevice);
                            webcam = null;
                            webcamDevice = -1;
                            webcamBuf = null;
                        }
                        if (robot == null && !GraphicsEnvironment.isHeadless()) {
                            try {
                                robot = new Robot();
                                screenBounds = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
                            } catch (Exception ex) {
                                onNotice.accept("Không thể chụp màn hình: " + ex.getMessage());
                                screenShare.set(false);
                            }
                        }
                        if (robot != null && screenBounds != null) {
                            BufferedImage raw = robot.createScreenCapture(screenBounds);
                            frame = scaleImage(raw, SCREEN_W, SCREEN_H);
                        }
                    } else {
                        if (webcam == null && !webcamTriedAndFailed) {
                            try {
                                videoInput vi = new videoInput();
                                int count = videoInput.listDevices(true);
                                if (count > 0 && vi.setupDevice(0, targetW, targetH)) {
                                    webcam = vi;
                                    webcamDevice = 0;
                                    camW = vi.getWidth(0);
                                    camH = vi.getHeight(0);
                                    int size = vi.getSize(0);
                                    if (camW > 0 && camH > 0 && size >= camW * camH * 3) {
                                        webcamBuf = new byte[size];
                                    } else {
                                        stopWebcam(vi, 0);
                                        webcam = null;
                                        webcamDevice = -1;
                                        webcamTriedAndFailed = true;
                                        onNotice.accept("Webcam trả về kích thước không hợp lệ — hãy thử nút Chia sẻ màn hình.");
                                    }
                                } else {
                                    webcamTriedAndFailed = true;
                                    onNotice.accept("Không mở được webcam (chưa gắn hoặc đang bận) — có thể bấm Chia sẻ màn hình.");
                                }
                            } catch (Throwable ex) {
                                webcamTriedAndFailed = true;
                                onNotice.accept("Webcam không khả dụng (" + ex.getMessage() + ") — có thể bấm Chia sẻ màn hình.");
                            }
                        }
                        if (webcam != null && webcamDevice >= 0 && webcamBuf != null) {
                            if (webcam.isFrameNew(webcamDevice)) {
                                webcam.getPixels(webcamDevice, webcamBuf, false, true);
                                BufferedImage raw = new BufferedImage(camW, camH, BufferedImage.TYPE_3BYTE_BGR);
                                byte[] dst = ((DataBufferByte) raw.getRaster().getDataBuffer()).getData();
                                System.arraycopy(webcamBuf, 0, dst, 0, Math.min(dst.length, webcamBuf.length));
                                frame = (camW > targetW || camH > targetH)
                                    ? scaleImage(raw, targetW, targetH)
                                    : raw;
                            }
                        }
                    }

                    if (frame != null) {
                        onLocalFrame.accept(frame);
                        try {
                            byte[] jpeg = encodeJpeg(frame, jpegQuality);
                            if (jpeg.length <= CallMediaTransport.MAX_VIDEO_FRAME_BYTES) {
                                if (groupSenderId == null) {
                                    transport.sendVideoJpeg(jpeg);
                                } else {
                                    transport.sendGroupVideoJpeg(groupSenderId, jpeg);
                                }
                            }
                        } catch (IOException ignored) {
                            // Transient UDP send failure; next frame will retry
                        }
                    }

                    long elapsed = System.currentTimeMillis() - startMs;
                    long wait = frameIntervalMs - elapsed;
                    if (wait > 0) sleepQuiet(wait);
                }
            } finally {
                if (webcam != null && webcamDevice >= 0) {
                    stopWebcam(webcam, webcamDevice);
                }
            }
        });
    }

    private static void stopWebcam(videoInput vi, int deviceId) {
        try {
            if (vi.isDeviceSetup(deviceId)) {
                vi.stopDevice(deviceId);
            }
        } catch (Throwable ignored) {}
    }

    public static BufferedImage scaleImage(BufferedImage source, int maxW, int maxH) {
        int sw = source.getWidth();
        int sh = source.getHeight();
        double scale = Math.min((double) maxW / sw, (double) maxH / sh);
        int dw = Math.max(1, (int) Math.round(sw * scale));
        int dh = Math.max(1, (int) Math.round(sh * scale));
        BufferedImage out = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, dw, dh, null);
        g.dispose();
        return out;
    }

    public static byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        BufferedImage rgb = image;
        if (image.getType() != BufferedImage.TYPE_INT_RGB && image.getType() != BufferedImage.TYPE_3BYTE_BGR) {
            rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.drawImage(image, 0, 0, null);
            g.dispose();
        }
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) throw new IOException("No JPEG writer available");
        ImageWriter writer = writers.next();
        ByteArrayOutputStream baos = new ByteArrayOutputStream(16 * 1024);
        try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(baos)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }

    private static void sleepQuiet(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) return;
        synchronized (decodeSignal) {
            decodeSignal.notifyAll();
        }
        if (captureThread != null) captureThread.interrupt();
        if (decodeThread != null) decodeThread.interrupt();
        latestRemoteJpegs.clear();
    }
}
