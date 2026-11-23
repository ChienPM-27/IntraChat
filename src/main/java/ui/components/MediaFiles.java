package ui.components;

import model.Attachment;
import model.MessageType;

import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;
import java.util.Set;

public final class MediaFiles {
    private static final long MIB = 1024 * 1024;

    private MediaFiles() {
    }

    public static Attachment inspect(Path path, MessageType type) throws IOException {
        long size = checkedSize(path, type);
        String name = path.getFileName().toString();
        safeName(name);
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        int width = 0;
        int height = 0;
        long duration = 0;
        switch (type) {
            case IMAGE -> {
                if (!Set.of("png", "jpg", "jpeg").contains(extension)) {
                    throw new IllegalArgumentException("Chỉ hỗ trợ ảnh PNG hoặc JPEG.");
                }
                Dimension dimensions = new Dimension();
                image(path, 240, dimensions);
                width = dimensions.width;
                height = dimensions.height;
            }
            case VIDEO -> {
                if (!Set.of("mp4", "avi", "mkv").contains(extension)) {
                    throw new IllegalArgumentException("Chỉ hỗ trợ video MP4, AVI hoặc MKV.");
                }
                try (var input = Files.newInputStream(path)) {
                    byte[] header = input.readNBytes(12);
                    String signature = new String(header, java.nio.charset.StandardCharsets.ISO_8859_1);
                    boolean valid = switch (extension) {
                        case "mp4" -> signature.startsWith("ftyp", 4);
                        case "avi" -> signature.startsWith("RIFF") && signature.startsWith("AVI ", 8);
                        default -> signature.startsWith("\u001A\u0045\u00DF\u00A3");
                    };
                    if (!valid) {
                        throw new IllegalArgumentException("Nội dung không khớp định dạng video.");
                    }
                }
            }
            case SOUND -> {
                if(!Set.of("wav","mp3","m4a").contains(extension))
                    throw new IllegalArgumentException("Chỉ hỗ trợ gửi WAV, MP3 hoặc M4A.");
                if(extension.equals("wav")) {
                    try (AudioInputStream audio = audio(path)) {
                        duration = Math.round(1000.0 * audio.getFrameLength() / audio.getFormat().getFrameRate());
                    }
                }
            }
            default -> {
            }
        }
        return new Attachment(path, name, size, duration, width, height);
    }

    static long checkedSize(Path path, MessageType type) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Không tìm thấy tệp hoặc đường dẫn không phải tệp.");
        }
        long size = Files.size(path);
        long limit = switch (type) {
            case IMAGE -> 16 * MIB;
            case SOUND -> 8 * MIB;
            default -> 128 * MIB;
        };
        if (size < 0 || (size == 0 && type != MessageType.FILE) || size > limit) {
            throw new IllegalArgumentException("Tệp phải có nội dung và không vượt quá " + limit / MIB + " MiB.");
        }
        return size;
    }

    public static BufferedImage image(Path path, int maxWidth) throws IOException {
        return image(path, maxWidth, new Dimension());
    }

    private static BufferedImage image(Path path, int maxWidth, Dimension dimensions) throws IOException {
        checkedSize(path, MessageType.IMAGE);
        try (var input = ImageIO.createImageInputStream(path.toFile())) {
            if (input != null) {
                var readers = ImageIO.getImageReaders(input);
                if (readers.hasNext()) {
                    var reader = readers.next();
                    try {
                        reader.setInput(input, true, true);
                        int width = reader.getWidth(0);
                        int height = reader.getHeight(0);
                        dimensions.setSize(width, height);
                        if (!Set.of("png", "jpeg").contains(reader.getFormatName().toLowerCase(Locale.ROOT))
                                || (long) width * height > 32_000_000) {
                            throw new IllegalArgumentException("Chỉ hỗ trợ ảnh PNG/JPEG tối đa 32 megapixel.");
                        }
                        int factor = Math.max(1, Math.max(width, height) / maxWidth);
                        var parameters = reader.getDefaultReadParam();
                        parameters.setSourceSubsampling(factor, factor, 0, 0);
                        return reader.read(0, parameters);
                    } finally {
                        reader.dispose();
                    }
                }
            }
        }
        throw new IllegalArgumentException("Không đọc được ảnh PNG/JPEG.");
    }

    static AudioInputStream audio(Path path) throws IOException {
        checkedSize(path, MessageType.SOUND);
        try {
            if (!AudioSystem.getAudioFileFormat(path.toFile()).getType().equals(AudioFileFormat.Type.WAVE)) {
                throw new IllegalArgumentException("Chỉ hỗ trợ tin thoại WAV PCM.");
            }
            AudioInputStream audio = AudioSystem.getAudioInputStream(path.toFile());
            var format = audio.getFormat();
            long frames = audio.getFrameLength();
            if ((!format.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED)
                    && !format.getEncoding().equals(AudioFormat.Encoding.PCM_UNSIGNED))
                    || frames <= 0 || format.getFrameSize() <= 0 || frames > 8 * MIB / format.getFrameSize()
                    || !(format.getFrameRate() > 0)) {
                audio.close();
                throw new IllegalArgumentException("Chỉ hỗ trợ tin thoại WAV PCM tối đa 8 MiB.");
            }
            return audio;
        } catch (UnsupportedAudioFileException failure) {
            throw new IllegalArgumentException("Không đọc được tin thoại WAV.", failure);
        }
    }

    public static String safeName(String name) {
        String base = name.split("\\.", 2)[0].stripTrailing().toUpperCase(Locale.ROOT);
        if (name.isBlank() || name.length() > 240 || name.endsWith(".") || name.endsWith(" ")
                || name.chars().anyMatch(value -> value < 32 || "/\\:<>|\"?*".indexOf(value) >= 0)
                || base.matches("CON|PRN|AUX|NUL|COM[1-9¹²³]|LPT[1-9¹²³]")) {
            throw new IllegalArgumentException("Tên tệp không hợp lệ.");
        }
        return name;
    }

    public static void save(Attachment attachment, Path target) throws IOException {
        Path destination = target.toAbsolutePath().normalize();
        safeName(destination.getFileName().toString());
        long expected = checkedSize(attachment.path(), MessageType.FILE);
        if (expected != attachment.sizeBytes()) {
            throw new IllegalArgumentException("Tệp nguồn đã thay đổi. Không thể lưu.");
        }
        Path staged = Files.createTempFile(destination.getParent(), ".intrachat-", ".part");
        try {
            try (var input = Files.newInputStream(attachment.path());
                 var output = Files.newOutputStream(staged)) {
                byte[] buffer = new byte[8192];
                long copied = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    copied += count;
                    if (copied > expected || Thread.currentThread().isInterrupted()) {
                        throw new IOException("Thao tác sao chép bị gián đoạn.");
                    }
                    output.write(buffer, 0, count);
                }
                if (copied != expected) {
                    throw new IOException("Tệp nguồn đã thay đổi.");
                }
            }
            try {
                Files.move(staged, destination);
            } catch (FileAlreadyExistsException failure) {
                throw new IllegalArgumentException("Tệp đích đã tồn tại. Hãy chọn tên khác.", failure);
            }
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    public static String size(long bytes) {
        return bytes >= MIB ? String.format(Locale.ROOT, "%.1f MB", bytes / (double) MIB)
                : bytes >= 1024 ? String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0) : bytes + " B";
    }

    public static String duration(long milliseconds) {
        long seconds = milliseconds / 1000;
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }
}
