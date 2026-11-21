package model.mock;

import model.Attachment;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class DemoMedia {
    private static final int WIDTH = 960;
    private static final int HEIGHT = 540;

    private DemoMedia() {
    }

    static DemoMediaFiles create() throws IOException {
        Path temp = Files.createTempDirectory("intrachat-demo-");
        temp.toFile().deleteOnExit();
        Path image = tempFile(temp, "BoCucHoiThoai.png");
        ImageIO.write(drawDesign(), "png", image.toFile());
        Path checklist = tempFile(temp, "ChecklistSprint.txt");
        Files.writeString(checklist, """
                IntraChat — Chuẩn bị chạy thử

                1. Mở đoạn chat cá nhân và kiểm tra thứ tự lịch sử.
                2. Thử gửi một đoạn văn bản có emoji và chọn ảnh từ máy.
                3. Tạo nhóm, phát đoạn ghi âm mẫu và lưu tệp đính kèm.
                """, StandardCharsets.UTF_8);
        Path video = copyResource(temp, "XemTruoc.mp4");
        Path voice = copyResource(temp, "GhiChuThoai.wav");
        long voiceDuration;
        try (var audio = javax.sound.sampled.AudioSystem.getAudioInputStream(voice.toFile())) {
            voiceDuration = Math.round(audio.getFrameLength() * 1000.0 / audio.getFormat().getFrameRate());
        } catch (javax.sound.sampled.UnsupportedAudioFileException error) {
            throw new IOException("Không đọc được đoạn ghi âm mẫu.", error);
        }
        return new DemoMediaFiles(attachment(image, 0, WIDTH, HEIGHT), attachment(checklist, 0, 0, 0),
                attachment(video, 0, 0, 0), attachment(voice, voiceDuration, 0, 0));
    }

    private static Path tempFile(Path directory, String name) {
        Path path = directory.resolve(name);
        path.toFile().deleteOnExit();
        return path;
    }

    private static Path copyResource(Path directory, String name) throws IOException {
        Path path = tempFile(directory, name);
        try (var input = DemoMedia.class.getResourceAsStream("/demo/" + name)) {
            if (input == null) {
                throw new IOException("Thiếu tài nguyên mẫu: " + name);
            }
            Files.copy(input, path);
        }
        return path;
    }

    private static Attachment attachment(Path path, long duration, int width, int height) throws IOException {
        return new Attachment(path, path.getFileName().toString(), Files.size(path), duration, width, height);
    }

    private static BufferedImage drawDesign() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setPaint(new GradientPaint(0, 0, new Color(0xDCEBFF), WIDTH, HEIGHT, new Color(0xE9DDFF)));
        graphics.fillRect(0, 0, WIDTH, HEIGHT);
        graphics.setColor(new Color(0x263454));
        graphics.setFont(new Font("Segoe UI", Font.BOLD, 60));
        graphics.drawString("Bố cục", 60, 200);
        graphics.drawString("hội thoại", 60, 273);
        graphics.setFont(new Font("Segoe UI", Font.PLAIN, 26));
        graphics.drawString("IntraChat · Phác thảo UI", 62, 332);
        shape(graphics, 451, 93, 458, 350, 30, 0xC8CDEF);
        shape(graphics, 442, 81, 458, 350, 30, 0xFFFFFF);
        shape(graphics, 462, 106, 113, 300, 16, 0xF0F4FD);
        shape(graphics, 476, 128, 85, 14, 8, 0xCAD8F0);
        for (int index = 0; index < 4; index++) {
            int top = 161 + index * 46;
            shape(graphics, 476, top, 28, 28, 28, index == 0 ? 0x87B9FF : 0xD9DEFA);
            shape(graphics, 512, top + 5, 47, 7, 5, 0xBDCBE3);
            shape(graphics, 512, top + 18, 35, 5, 4, 0xD5E0F1);
        }
        shape(graphics, 595, 109, 31, 31, 31, 0x87B9FF);
        shape(graphics, 638, 117, 85, 11, 8, 0x9FAFCB);
        shape(graphics, 595, 163, 139, 40, 20, 0xE6EDFA);
        shape(graphics, 678, 219, 195, 42, 21, 0x0084FF);
        shape(graphics, 595, 277, 162, 47, 21, 0xE8E0FF);
        shape(graphics, 595, 365, 231, 37, 36, 0xEDF2FC);
        shape(graphics, 840, 365, 37, 37, 18, 0x0084FF);
        graphics.dispose();
        return image;
    }

    private static void shape(Graphics2D graphics, int left, int top, int width, int height, int arc, int rgb) {
        graphics.setColor(new Color(rgb));
        graphics.fill(new RoundRectangle2D.Double(left, top, width, height, arc, arc));
    }

    record DemoMediaFiles(Attachment image, Attachment file, Attachment video, Attachment voice) {
    }
}
