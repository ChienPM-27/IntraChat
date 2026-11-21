package vn.intrachat.protocol;

import java.util.Objects;
import java.util.UUID;

/** Stable identifier for the intended peer or conversation group. */
public record Destination(DestinationKind kind, UUID id) {
    public Destination {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
    }
}
