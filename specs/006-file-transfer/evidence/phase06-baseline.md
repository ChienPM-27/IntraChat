# Phase 06 execution baseline — observed source and environment

**Date:** 2026-11-21 · **Branch:** `006-file-transfer` · **Initial SHA:** `d01fcc0086562e9f07c6201dde522fb007c989cb`.
**Scope:** T001, T006 and dependency inventory. **Measured on one Windows environment; not a 2-PC validation.**

| Source or environment | Observed evidence | Release implication |
| --- | --- | --- |
| JDK/compiler | Temurin `openjdk 21.0.12.1+1`, Maven wrapper offline | Java21 target available |
| OS | Windows NT build `10.0.26100.0` (Windows 11 platform family) | One environment observed, not two physical devices |
| `.\mvnw.cmd -o test` before File changes | **138/138 JUnit, Maven exit 0**, `BUILD SUCCESS` | Baseline only, not a File feature result |
| Existing M2 transport | `src/main/java/vn/intrachat/transport/NetworkConfig.java` declares 4 active sessions, 16 outbound frames/peer, 2MiB queued bytes/peer | File bulk must share and cannot assume separate connection |
| Existing secure session | `TlsChatSession.java` exposes `sessionId`, `remoteFingerprint`, `canChat`, controlled CHAT_V2 `send`/`receive` | M2 verified transport exists; **no File message dispatcher is implemented** |
| Existing File source at initial SHA | No `src/main/java/vn/intrachat/protocol/file/`, `security/file/`, `storage/file/` or `application/group/file/` implementation | No deployed File wire, auth, Windows receiving or Group File runtime |
| Group 004 | `specs/004-group-chat/tasks.md` describes **18/124** pure-domain tasks at baseline; no network Group relay/signed owner proof | T123–T136 external 004 Gate blocked |
| History 005 | `specs/005-chat-history/tasks.md` describes **22/136** partial domain/ACL tasks | 006 cannot use History 005 for persisted file metadata/resume |
| FILE_V1 technical contract | `specs/006-file-transfer/contracts/file-v1.md` explicitly **DRAFT**, `protocolVersion=5` is candidate | Independent canonical bytes and capability transcript review still open |
| Physical LAN/security | One Windows machine observed during this execution; no second *different physical* Windows LAN peer and no independent security signoff evidenced | Physical release Gate 1 NOT RUN/NOT EVIDENCED; not proof no other peer exists |

**Git safety:** user main checkout `003-p2p-messaging` was already dirty before execution, with UI/M2 edits. No checkout/reset/clean/stage performed there. All 006 work was done in isolated temporary worktree. Source references are repository-relative. No secrets, PINs, SAS, private Windows paths or user payloads included in evidence.

**Execution gate:** baseline and source inventory are observational PASS, not File 006/M4 approval. Independent protocol and Windows FS review missing; stop only security-sensitive production implementation that requires their signoff, continue pure Java/domain/read-only fixtures where possible.
