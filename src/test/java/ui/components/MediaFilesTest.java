package ui.components;

import model.MessageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MediaFilesTest {
    @TempDir Path directory;

    @Test
    void savesExactBytesWithoutOverwriting() throws Exception {
        byte[] content = {0, 1, 2, -1};
        Path source = Files.write(directory.resolve("mẫu.bin"), content);
        var attachment = MediaFiles.inspect(source, MessageType.FILE);
        Path destination = directory.resolve("bản sao.bin");
        MediaFiles.save(attachment, destination);
        assertArrayEquals(content, Files.readAllBytes(destination));
        Files.write(destination, new byte[]{99});
        assertThrows(IllegalArgumentException.class, () -> MediaFiles.save(attachment, destination));
        assertArrayEquals(new byte[]{99}, Files.readAllBytes(destination));
    }

    @Test
    void rejectsMalformedMediaUnsafeNamesAndOversize() throws Exception {
        Path image = Files.writeString(directory.resolve("bad.png"), "not an image");
        assertThrows(IllegalArgumentException.class, () -> MediaFiles.inspect(image, MessageType.IMAGE));
        Path video = Files.writeString(directory.resolve("bad.mp4"), "not a video");
        assertThrows(IllegalArgumentException.class, () -> MediaFiles.inspect(video, MessageType.VIDEO));
        for (String name : new String[]{"../file.txt", "C:\\file.txt", "NUL.txt", "file.", ".."}) {
            assertThrows(IllegalArgumentException.class, () -> MediaFiles.safeName(name));
        }
        try (var file = new RandomAccessFile(image.toFile(), "rw")) {
            file.setLength(16L * 1024 * 1024 + 1);
        }
        assertThrows(IllegalArgumentException.class, () -> MediaFiles.inspect(image, MessageType.IMAGE));
    }

    @Test
    void cancelledCopyLeavesNoPartialFile() throws Exception {
        Path source = Files.writeString(directory.resolve("data.txt"), "IntraChat");
        var attachment = MediaFiles.inspect(source, MessageType.FILE);
        Path destination = directory.resolve("copy.txt");
        Thread.currentThread().interrupt();
        try {
            assertThrows(java.io.IOException.class, () -> MediaFiles.save(attachment, destination));
        } finally {
            Thread.interrupted();
        }
        assertFalse(Files.exists(destination));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }
}
