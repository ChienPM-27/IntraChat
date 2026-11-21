package model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record CallSession(String id, String conversationId, ChatUser peer, Mode mode, Direction direction,
                          Status status, Instant connectedAt, Instant endedAt) {
    public enum Mode {
        VOICE("Gọi thoại"), VIDEO("Gọi video");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Direction { OUTGOING, INCOMING }

    public enum Status {
        RINGING, CONNECTED, CANCELLED, DECLINED, MISSED, ENDED;

        public boolean ongoing() {
            return this == RINGING || this == CONNECTED;
        }
    }

    public CallSession {
        Objects.requireNonNull(id);
        Objects.requireNonNull(conversationId);
        Objects.requireNonNull(peer);
        Objects.requireNonNull(mode);
        Objects.requireNonNull(direction);
        Objects.requireNonNull(status);
        boolean wasConnected = status == Status.CONNECTED || status == Status.ENDED;
        boolean finished = !status.ongoing();
        if ((connectedAt != null) != wasConnected || (endedAt != null) != finished) {
            throw new IllegalArgumentException("Mốc thời gian cuộc gọi không khớp trạng thái.");
        }
    }

    public long durationMillis(Instant now) {
        if (connectedAt == null) {
            return 0;
        }
        return Math.max(0, Duration.between(connectedAt, endedAt == null ? now : endedAt).toMillis());
    }
}
