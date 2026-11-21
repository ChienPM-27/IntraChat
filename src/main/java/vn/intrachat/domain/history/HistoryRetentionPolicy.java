package vn.intrachat.domain.history;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Pure policy for two ACTIVE confirmed limits: default 90 days AND 500 MiB.
 * Actual retention cleanup, physical file size accounting, tombstone protection
 * and disk writes are separate encrypted persistence operations.
 */
public record HistoryRetentionPolicy(Duration maxAge, long maxBytes) {
    public static final Duration DEFAULT_MAX_AGE = Duration.ofDays(90);
    public static final long DEFAULT_MAX_BYTES = 500L * 1024 * 1024;
    private static final Duration MAX_CONFIG_AGE = Duration.ofDays(3650);
    private static final long MIN_CONFIG_BYTES = 1024L * 1024;
    private static final long MAX_CONFIG_BYTES = 8L * 1024 * 1024 * 1024;

    public HistoryRetentionPolicy {
        Objects.requireNonNull(maxAge,"maxAge");
        if (maxAge.compareTo(Duration.ofDays(1)) < 0 ||
            maxAge.compareTo(MAX_CONFIG_AGE) > 0 ||
            maxBytes < MIN_CONFIG_BYTES || maxBytes > MAX_CONFIG_BYTES)
            throw new IllegalArgumentException("Retention policy is outside safe bounds");
    }
    public static HistoryRetentionPolicy defaults() {
        return new HistoryRetentionPolicy(DEFAULT_MAX_AGE,DEFAULT_MAX_BYTES);
    }
    public static HistoryRetentionPolicy custom(Duration maxAge, long maxBytes) {
        return new HistoryRetentionPolicy(maxAge,maxBytes);
    }
    public Decision evaluate(Instant committedAt, Instant localNow,
                             long totalEncryptedStoreBytes, boolean isTombstone) {
        Objects.requireNonNull(committedAt,"committedAt");
        Objects.requireNonNull(localNow,"localNow");
        if (totalEncryptedStoreBytes < 0)
            throw new IllegalArgumentException("Negative store size");
        boolean expired = !committedAt.isAfter(localNow.minus(maxAge));
        boolean overCap = totalEncryptedStoreBytes >= maxBytes;
        return new Decision(expired,overCap,isTombstone);
    }
    public record Decision(boolean expireByAge, boolean overSize, boolean protectTombstone) {
        public boolean requiresCleanup() { return expireByAge || overSize; }
        public boolean deleteThisEvent() { return expireByAge && !protectTombstone; }
    }
}
