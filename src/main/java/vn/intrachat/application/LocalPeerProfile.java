package vn.intrachat.application;

import vn.intrachat.domain.PeerIdentity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;

/** Stores only a nonsecret local device ID and display name in user-local configuration. */
public final class LocalPeerProfile {
    private LocalPeerProfile() { }

    public static PeerIdentity loadOrCreate(Path configFile) throws IOException {
        Objects.requireNonNull(configFile);
        if (Files.isRegularFile(configFile)) {
            Properties properties = new Properties();
            try (var in = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                properties.load(in);
            }
            try {
                UUID id = UUID.fromString(properties.getProperty("deviceId", ""));
                if (id.equals(new UUID(0,0))) throw new IllegalArgumentException("Empty UUID");
                return new PeerIdentity(id, properties.getProperty("displayName", "Máy IntraChat"));
            } catch (IllegalArgumentException invalid) {
                throw new IOException("Local peer profile is corrupt; do not silently change identity", invalid);
            }
        }
        Path parent = configFile.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        PeerIdentity peer = new PeerIdentity(UUID.randomUUID(), "Máy IntraChat");
        Properties properties = new Properties();
        properties.setProperty("deviceId", peer.deviceId().toString());
        properties.setProperty("displayName", peer.displayName());
        Path temporary = Files.createTempFile(parent, "intrachat-device-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(writer, "IntraChat device profile - not an authentication credential");
            }
            try {
                Files.move(temporary, configFile, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException notAtomic) {
                Files.move(temporary, configFile);
            } catch (FileAlreadyExistsException concurrent) {
                Files.deleteIfExists(temporary);
                return loadOrCreate(configFile);
            }
            return peer;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static Path defaultPath() {
        String explicit = System.getProperty("intrachat.profile", "");
        if (!explicit.isBlank()) return Path.of(explicit).toAbsolutePath();
        String appData = System.getenv("LOCALAPPDATA");
        if (appData == null || appData.isBlank()) {
            appData = System.getProperty("user.home");
        }
        return Path.of(appData, "IntraChat", "config", "device.properties");
    }
}
