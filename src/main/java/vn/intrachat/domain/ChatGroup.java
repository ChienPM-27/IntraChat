package vn.intrachat.domain;

import java.util.*;

/** In-memory roster; peer IDs are identities for routing, not cryptographic authentication. */
public record ChatGroup(UUID groupId, String groupName, UUID ownerId, List<UUID> memberIds, long revision) {
    public ChatGroup {
        Objects.requireNonNull(groupId);
        Objects.requireNonNull(ownerId);
        if (groupName == null || groupName.isBlank() || groupName.length() > 60)
            throw new IllegalArgumentException("Invalid group name");
        memberIds = List.copyOf(memberIds);
        if (revision < 0 || memberIds.size() > 5 || new HashSet<>(memberIds).size() != memberIds.size()
                || !memberIds.contains(ownerId))
            throw new IllegalArgumentException("Invalid group membership");
    }
    public boolean contains(UUID id) { return memberIds.contains(id); }
    public ChatGroup withMembers(List<UUID> members) {
        return new ChatGroup(groupId, groupName, ownerId, members, revision + 1);
    }
}
