# Group 004 implementation boundaries — T008

**Date:** 2026-11-21. Isolated 004 worktree created from committed branch; original 003 worktree contains uncommitted work that MUST NOT be edited/reverted.

| Single-owner area | Proposed files | Verification |
| --- | --- | --- |
| Pure Group domain | `src/main/java/vn/intrachat/domain/group/*` and matching JUnit | ID/roster/ACL/member states; Java21 RED→GREEN |
| Group wire/protocol | `src/main/java/vn/intrachat/protocol/group/*` | Synthetic canonical vectors, strict JSON v3; no v1/v2 downgrade |
| Group crypto/storage | `src/main/java/vn/intrachat/security/group/*` | independent signer pins, DPAPI/ACL, crash rollback |
| Group application | `src/main/java/vn/intrachat/application/group/*` | owner serialized commits, handoff, relay, signed ACK/READ |
| Integration transport | existing `TlsChatSession`, `SecureChatCoordinator`, `ChatV2BootstrapCodec` | **one owner at a time** and full M0–M2 regression |
| Frontend | `listener/GroupLiveAdapter`, `ui/frames/MainChatFrame`, dialogs | one owner at a time, EDT, real viewport, Demo/P2P separation |
| Build/release | `pom.xml`, scripts, ROADMAP/README, evidence | dependency/security approval, testing, release gating |

Initial execution changes restricted to isolated new Group paths/docs, and tests. Do not merge other working tree dirty changes automatically. A future transport/FE task requires explicit review and isolated integration commit; no Group v3 enabled until signer/capability security gates.
