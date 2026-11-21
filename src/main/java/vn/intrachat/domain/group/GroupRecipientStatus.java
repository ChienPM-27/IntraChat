package vn.intrachat.domain.group;

import java.util.*;

/**
 * Immutable per-recipient UI evidence projection. Only a signature-verified receipt router
 * may call recipientAck/recipientRead; this class does not claim network authentication.
 */
public final class GroupRecipientStatus {
    public enum State { PENDING, OFFLINE, UNCERTAIN, APP_ACKNOWLEDGED, VIEWED }

    private final Map<UUID, State> recipients;
    private final boolean ownerAccepted;

    public GroupRecipientStatus(Set<UUID> recipientIds) {
        Objects.requireNonNull(recipientIds, "recipientIds");
        if (recipientIds.isEmpty() || recipientIds.size() > 4 ||
            recipientIds.contains(new UUID(0L,0L)))
            throw new IllegalArgumentException("Invalid group recipient snapshot");
        LinkedHashMap<UUID, State> states = new LinkedHashMap<>();
        for (UUID id : recipientIds) states.put(Objects.requireNonNull(id, "recipient"), State.PENDING);
        this.recipients = Map.copyOf(states);
        this.ownerAccepted = false;
    }

    private GroupRecipientStatus(Map<UUID, State> states, boolean accepted) {
        recipients = Map.copyOf(states);
        ownerAccepted = accepted;
    }

    public boolean isOwnerAccepted() { return ownerAccepted; }
    public Set<UUID> recipients() { return recipients.keySet(); }
    public State statusOf(UUID recipient) {
        State state = recipients.get(recipient);
        if (state == null) throw new SecurityException("Not an original group recipient");
        return state;
    }
    public int deliveredCount() {
        return (int) recipients.values().stream()
            .filter(x -> x == State.APP_ACKNOWLEDGED || x == State.VIEWED).count();
    }
    public int readCount() {
        return (int) recipients.values().stream().filter(x -> x == State.VIEWED).count();
    }
    public GroupRecipientStatus ownerAccepted() {
        return new GroupRecipientStatus(recipients, true);
    }
    public GroupRecipientStatus recipientAck(UUID peer) { return update(peer, State.APP_ACKNOWLEDGED); }
    public GroupRecipientStatus recipientRead(UUID peer) { return update(peer, State.VIEWED); }
    public GroupRecipientStatus recipientOffline(UUID peer) { return update(peer, State.OFFLINE); }
    public GroupRecipientStatus recipientUncertain(UUID peer) { return update(peer, State.UNCERTAIN); }

    private GroupRecipientStatus update(UUID peer, State next) {
        State current = statusOf(peer);
        if (current == State.VIEWED ||
            (current == State.APP_ACKNOWLEDGED &&
                (next == State.OFFLINE || next == State.UNCERTAIN)) ||
            (next == State.APP_ACKNOWLEDGED && current == State.APP_ACKNOWLEDGED))
            return this;
        Map<UUID, State> updated = new LinkedHashMap<>(recipients);
        updated.put(peer, next);
        return new GroupRecipientStatus(updated, ownerAccepted);
    }
}
