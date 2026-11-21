# Group capacity / shared session budget — T007

**Date:** 2026-11-21. Source constants checked directly. Plan §7 values are **design targets**, not benchmark results.

- In `NetworkConfig`, MAX_ACTIVE_SESSIONS = **4**. In `LiveChatService`, MAX_ACTIVE = **4**. Confirmed CL-04-05 is **5 devices** total: owner A is **local** member; B/C/D/E use **four** verified M2 sessions to A.
- Sessions are **shared** across P2P and Group, not one extra set per Group. If A is connected to unrelated P2P peer F and already has B/C/D/E, the app MUST reject excess capacity before opening a new session; never silently drop P2P or bypass inbound human consent. Groups may share the same peer link.
- M2 per-peer queue declarations: `MAX_OUTBOUND_FRAMES=16`, `MAX_QUEUED_BYTES=2MiB`. Whether every writer enforces both simultaneously must be measured/audited; Group must not rely on an unenforced constant.
- Proposed max **8 local groups**, Group transcript **200 msgs or 1MiB text/group**, Group+P2P process-wide **8MiB**, owner accepted queue 64 messages, receipt correlations ≤128/peer, 8 pending invites/group. All require implementation and measured stress T087/T088/T113/T117; quotas are **NOT yet proven**.
- Test cases: 5 independent device IDs with owner=4 sockets; 6th member denied; P2P+Group simultaneous shared link; slow C does not block B/D; 16/2MiB writer cap; aggregate 8MiB with fair typed rejection; 2-second normal close and EDT p95 targets.

**Assessment:** 5-device product requirement is technically compatible with four owner peer sockets **by topology**, but no evidence yet proves overall transport/resource capacity.
