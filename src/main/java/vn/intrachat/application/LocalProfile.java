package vn.intrachat.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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

    public Path mediaDir() {
        return dataDir.resolve("media");
    }

    public static Path defaultDataRoot() {
        String override = System.getProperty("intrachat.dataDir");
        if (override != null && !override.isBlank()) return Path.of(override).toAbsolutePath().normalize();
        return Path.of("data").toAbsolutePath().normalize();
    }

    public static Path defaultDataDir() {
        String override = System.getProperty("intrachat.dataDir");
        if (override != null && !override.isBlank()) return Path.of(override).toAbsolutePath().normalize();
        String user = System.getProperty("intrachat.user");
        if (user != null && !user.isBlank()) return userDataDir(user);
        return defaultDataRoot();
    }

    public static Path userDataDir(String userName) {
        return defaultDataRoot().resolve(safeFolderName(userName));
    }

    public static String safeFolderName(String userName) {
        if (userName == null || userName.isBlank()) {
            throw new IllegalArgumentException("User name must not be blank");
        }
        String trimmed = userName.trim();
        if (trimmed.length() > 80) {
            throw new IllegalArgumentException("User name must be 1..80 characters");
        }
        StringBuilder clean = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c < 32 || "<>:\"/\\|?*".indexOf(c) >= 0) {
                clean.append('_');
            } else {
                clean.append(c);
            }
        }
        String result = clean.toString().replaceAll("[. ]+$", "");
        if (result.isBlank() || result.equals(".") || result.equals("..")
                || result.toUpperCase(Locale.ROOT).matches("(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])([.].*)?")) {
            result = "user-" + (result.isBlank() ? "default" : result);
        }
        return result;
    }

    /** Reuse the last explicitly chosen profile; -User and -DataDir still override. */
    public static String lastUser(){
        Path root=defaultDataRoot();
        try{
            Path marker=root.resolve("last-user.txt");
            if(Files.isRegularFile(marker)&&Files.size(marker)<=1024){
                String user=Files.readString(marker).strip();
                if(!user.isBlank()&&Files.isRegularFile(userDataDir(user).resolve("profile.json")))return user;
            }
        }catch(IOException|IllegalArgumentException ignored){}
        List<String> users=listExistingUsers(root);
        return users.size()==1?users.getFirst():null;
    }
    public static void rememberUser(String user){
        try{
            Path root=defaultDataRoot();Files.createDirectories(root);
            Path temp=Files.createTempFile(root,"last-user-",".tmp");
            try{
                Files.writeString(temp,user);
                Files.move(temp,root.resolve("last-user.txt"),StandardCopyOption.REPLACE_EXISTING);
            }finally{Files.deleteIfExists(temp);}
        }catch(IOException ignored){/* profile is still usable */}
    }

    public static List<String> listExistingUsers(Path dataRoot) {
        if (dataRoot == null || !Files.isDirectory(dataRoot)) return List.of();
        List<String> users = new ArrayList<>();
        try (var stream = Files.list(dataRoot)) {
            for (Path dir : stream.toList()) {
                if (!Files.isDirectory(dir)) continue;
                Path profileFile = dir.resolve("profile.json");
                if (!Files.isRegularFile(profileFile)) continue;
                try {
                    Stored item = JSON.readValue(profileFile.toFile(), Stored.class);
                    if (item != null && item.displayName() != null && !item.displayName().isBlank()) {
                        users.add(item.displayName().trim());
                    }
                } catch (Exception ignored) {
                    users.add(dir.getFileName().toString());
                }
            }
        } catch (IOException ignored) {
            return List.of();
        }
        Collections.sort(users, String.CASE_INSENSITIVE_ORDER);
        return users;
    }

    public static LocalProfile loadOrCreate() throws IOException {
        String user = System.getProperty("intrachat.user");
        return loadOrCreate(defaultDataDir(), user);
    }

    public static LocalProfile loadOrCreate(Path directory) throws IOException {
        return loadOrCreate(directory, null);
    }

    public static LocalProfile loadOrCreate(Path directory, String preferredDisplayName) throws IOException {
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
        String displayName = (preferredDisplayName != null && !preferredDisplayName.isBlank())
            ? preferredDisplayName.trim()
            : "Thiết bị " + UUID.randomUUID().toString().substring(0, 8);
        LocalProfile profile = new LocalProfile(UUID.randomUUID(), displayName, dir);
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
