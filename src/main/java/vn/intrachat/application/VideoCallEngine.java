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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Realtime JPEG video engine for P2P LAN calls.
 * Captures from Windows DirectShow webcam (via videoInput) or optional screen-share mode,
 * gracefully falling back if no webcam is attached or the webcam is in use by another app.
 */
public final class VideoCallEngine implements AutoCloseable {
    private static final int TARGET_W = 480;
    private static final int TARGET_H = 360;
    private static final int SCREEN_W = 640;
    private static final int SCREEN_H = 360;
    private static final float JPEG_QUALITY = 0.65f;
    private static final long FRAME_INTERVAL_MS = 66; // ~15 FPS

    private final CallMediaTransport transport;
    private final Consumer<BufferedImage> onLocalFrame;
    private final Consumer<BufferedImage> onRemoteFrame;
    private final Consumer<String> onNotice;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean cameraOn = new AtomicBoolean(true);
    private final AtomicBoolean screenShare = new AtomicBoolean(false);
    private final AtomicReference<byte[]> latestRemoteJpeg = new AtomicReference<>();
    private final Object decodeSignal = new Object();

    private Thread captureThread;
    private Thread decodeThread;

    public VideoCallEngine(CallMediaTransport transport,
                           Consumer<BufferedImage> onLocalFrame,
                           Consumer<BufferedImage> onRemoteFrame,
                           Consumer<String> onNotice) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.onLocalFrame = onLocalFrame == null ? f -> {} : onLocalFrame;
        this.onRemoteFrame = onRemoteFrame == null ? f -> {} : onRemoteFrame;
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
        if (!running.get() || jpeg == null || jpeg.length == 0) return;
        latestRemoteJpeg.set(jpeg);
        synchronized (decodeSignal) {
            decodeSignal.notify();
        }
    }

    private void startDecodeLoop() {
        decodeThread = Thread.ofVirtual().name("intrachat-call-vdec").start(() -> {
            while (running.get()) {
                byte[] jpeg = latestRemoteJpeg.getAndSet(null);
                if (jpeg == null) {
                    synchronized (decodeSignal) {
                        if (latestRemoteJpeg.get() == null && running.get()) {
                            try { decodeSignal.wait(150); } catch (InterruptedException ignored) {}
                        }
                    }
                    continue;
                }
                try {
                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpeg));
                    if (img != null) {
                        onRemoteFrame.accept(img);
                    }
                } catch (Exception ignored) {
                    // Drop corrupted UDP frame silently
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
                                if (count > 0 && vi.setupDevice(0, TARGET_W, TARGET_H)) {
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
                                frame = (camW > TARGET_W || camH > TARGET_H)
                                    ? scaleImage(raw, TARGET_W, TARGET_H)
                                    : raw;
                            }
                        }
                    }

                    if (frame != null) {
                        onLocalFrame.accept(frame);
                        try {
                            byte[] jpeg = encodeJpeg(frame, JPEG_QUALITY);
                            if (jpeg.length <= CallMediaTransport.MAX_VIDEO_FRAME_BYTES) {
                                transport.sendVideoJpeg(jpeg);
                            }
                        } catch (IOException ignored) {
                            // Transient UDP send failure; next frame will retry
                        }
                    }

                    long elapsed = System.currentTimeMillis() - startMs;
                    long wait = FRAME_INTERVAL_MS - elapsed;
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
    }
}
