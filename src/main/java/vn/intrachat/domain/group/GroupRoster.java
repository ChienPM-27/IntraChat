package vn.intrachat.domain.group;

import java.util.*;

/** Immutable owner-authorized roster snapshot; knowing the groupId is not membership. */
public final class GroupRoster {
    public static final int MAX_MEMBERS = 5;
    private final List<GroupMember> members;
    private final UUID ownerId;

    private GroupRoster(List<GroupMember> list) {
        Objects.requireNonNull(list, "members");
        if (list.isEmpty() || list.size() > MAX_MEMBERS)
            throw new IllegalStateException("Group roster capacity exceeded or empty");
        Set<UUID> ids = new HashSet<>();
        Set<String> fingerprints = new HashSet<>();
        UUID foundOwner = null;
        for (GroupMember member : list) {
            Objects.requireNonNull(member, "member");
            if (!ids.add(member.deviceId()) ||
                !fingerprints.add(HexFormat.of().formatHex(member.pinnedSpkiSha256())))
                throw new IllegalArgumentException("Duplicate group device identity/SPKI");
            if (member.role() == GroupMember.Role.OWNER) {
                if (foundOwner != null)
                    throw new IllegalArgumentException("Multiple owners");
                foundOwner = member.deviceId();
            }
        }
        if (foundOwner == null) throw new IllegalArgumentException("Owner missing");
        members = List.copyOf(list);
        ownerId = foundOwner;
    }

    public static GroupRoster withOwner(GroupMember owner) {
        Objects.requireNonNull(owner, "owner");
        if (owner.role() != GroupMember.Role.OWNER)
            throw new IllegalArgumentException("Initial member must be owner");
        return new GroupRoster(List.of(owner));
    }

    public GroupRoster add(GroupMember member) {
        Objects.requireNonNull(member, "member");
        if (member.role() != GroupMember.Role.MEMBER)
            throw new IllegalArgumentException("Only a member can join this roster");
        if (members.size() >= MAX_MEMBERS)
            throw new IllegalStateException("Group roster is full");
        List<GroupMember> next = new ArrayList<>(members);
        next.add(member);
        return new GroupRoster(next);
    }

    public GroupRoster remove(UUID deviceId) {
        Objects.requireNonNull(deviceId, "deviceId");
        if (ownerId.equals(deviceId)) throw new IllegalStateException("Owner needs handoff/disband");
        if (!contains(deviceId)) throw new IllegalArgumentException("Not a group member");
        return new GroupRoster(members.stream()
            .filter(m -> !m.deviceId().equals(deviceId)).toList());
    }

    public List<GroupMember> members() { return members; }
    public UUID ownerId() { return ownerId; }
    public int size() { return members.size(); }
    public boolean contains(UUID deviceId) {
        return members.stream().anyMatch(m -> m.deviceId().equals(deviceId));
    }
    public Optional<GroupMember> find(UUID deviceId) {
        return members.stream().filter(m -> m.deviceId().equals(deviceId)).findFirst();
    }
}
