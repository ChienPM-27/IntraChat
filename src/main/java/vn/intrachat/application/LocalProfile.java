package vn.intrachat.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;
import java.util.Objects;
import java.util.UUID;

/** Local, self-declared device identity; this is not cryptographic authentication. */
public record LocalProfile(UUID deviceId, String displayName, Path dataDir) {
    private static final ObjectMapper JSON = new ObjectMapper();
    private record Stored(UUID deviceId, String displayName) {}

    public LocalProfile {
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(dataDir, "dataDir");
        if (displayName.isBlank() || displayName.length() > 80)
            throw new IllegalArgumentException("Display name must be 1..80 characters");
        dataDir = dataDir.toAbsolutePath().normalize();
    }

    public static Path defaultDataDir() {
        String override = System.getProperty("intrachat.dataDir");
        if (override != null && !override.isBlank()) return Path.of(override);
        String local = System.getenv("LOCALAPPDATA");
        if (local == null || local.isBlank())
            return Path.of(System.getProperty("user.home"), ".intrachat", "data");
        return Path.of(local, "IntraChat", "data");
    }

    public static LocalProfile loadOrCreate() throws IOException {
        return loadOrCreate(defaultDataDir());
    }

    public static LocalProfile loadOrCreate(Path directory) throws IOException {
        Path dir = directory.toAbsolutePath().normalize();
        Files.createDirectories(dir);
        Path file = dir.resolve("profile.json");
        if (Files.exists(file)) {
            try {
                Stored item = JSON.readValue(file.toFile(), Stored.class);
                if (item == null || item.deviceId() == null || item.displayName() == null)
                    throw new IllegalArgumentException("Missing required profile fields");
                return new LocalProfile(item.deviceId(), item.displayName(), dir);
            } catch (Exception invalid) {
                throw new IOException("Invalid local profile at " + file + ". Repair it manually; no identity was overwritten.", invalid);
            }
        }
        LocalProfile profile = new LocalProfile(UUID.randomUUID(),
            "Thiết bị " + UUID.randomUUID().toString().substring(0, 8), dir);
        Path temp = Files.createTempFile(dir, "profile-", ".tmp");
        try {
            JSON.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(),
                new Stored(profile.deviceId(), profile.displayName()));
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, file);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
        return profile;
    }
}
