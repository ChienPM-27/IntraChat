package model;

import java.nio.file.Path;
import java.util.Objects;

public record Attachment(Path path, String fileName, long sizeBytes, long durationMillis, int width, int height) {
    public Attachment {
        path = Objects.requireNonNull(path).toAbsolutePath().normalize();
        Objects.requireNonNull(fileName);
        if (fileName.isBlank() || sizeBytes <= 0 || durationMillis < 0 || width < 0 || height < 0) {
            throw new IllegalArgumentException("Thông tin tệp đính kèm không hợp lệ.");
        }
    }
}
