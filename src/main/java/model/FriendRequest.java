package model;

import java.time.Instant;
import java.util.Objects;

public record FriendRequest(String id, ChatUser peer, String ipAddress, Direction direction,
                            Status status, Instant sentAt) {
    public enum Direction { INCOMING, OUTGOING }

    public enum Status { PENDING, ACCEPTED, DECLINED }

    public FriendRequest {
        Objects.requireNonNull(id);
        Objects.requireNonNull(peer);
        Objects.requireNonNull(ipAddress);
        Objects.requireNonNull(direction);
        Objects.requireNonNull(status);
        Objects.requireNonNull(sentAt);
    }

    public FriendRequest withStatus(Status next) {
        return new FriendRequest(id, peer, ipAddress, direction, next, sentAt);
    }
}
