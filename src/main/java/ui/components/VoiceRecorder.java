package ui.components;

import model.Attachment;
import model.MessageType;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

final class VoiceRecorder implements AutoCloseable {
    private static final AudioFormat FORMAT = new AudioFormat(16_000, 16, 1, true, false);
    private static final int LIMIT = 120 * 16_000 * 2;
    private volatile boolean stopped;
    private volatile boolean cancelled;

    CompletableFuture<Attachment> record(Executor executor, Consumer<Long> onElapsed) {
        return CompletableFuture.supplyAsync(() -> {
            if (stopped) {
                return null;
            }
            try (TargetDataLine line = AudioSystem.getTargetDataLine(FORMAT);
                 ByteArrayOutputStream data = new ByteArrayOutputStream()) {
                line.open(FORMAT);
                line.start();
                byte[] buffer = new byte[3200];
                while (!stopped && data.size() < LIMIT) {
                    int count = line.read(buffer, 0, Math.min(buffer.length, LIMIT - data.size()));
                    if (count <= 0) {
                        break;
                    }
                    data.write(buffer, 0, count);
                    onElapsed.accept(data.size() / 32L);
                }
                if (cancelled) {
                    return null;
                }
                if (data.size() == 0) {
                    throw new IllegalArgumentException("Chưa thu được âm thanh. Hãy thử lại.");
                }
                return write(data.toByteArray());
            } catch (IOException | LineUnavailableException | IllegalArgumentException | SecurityException failure) {
                throw new CompletionException(new IllegalArgumentException(
                        "Không thể ghi âm. Hãy kiểm tra microphone và quyền truy cập.", failure));
            }
        }, executor);
    }

    private Attachment write(byte[] data) throws IOException {
        Path path = Files.createTempFile("TinNhanThoai-", ".wav");
        path.toFile().deleteOnExit();
        try (AudioInputStream audio = new AudioInputStream(new ByteArrayInputStream(data), FORMAT, data.length / 2)) {
            AudioSystem.write(audio, AudioFileFormat.Type.WAVE, path.toFile());
            return MediaFiles.inspect(path, MessageType.SOUND);
        } catch (IOException | RuntimeException failure) {
            Files.deleteIfExists(path);
            throw failure;
        }
    }

    void stop() {
        stopped = true;
    }

    @Override
    public void close() {
        cancelled = true;
        stop();
    }
}
