# Actual Java21 RED→GREEN execution — Phase05 pure History domain and P2P scope gate

**Date:** 2026-11-21 · **Worktree base:** `005-chat-history` at `9acfb8214650fa88c932a81ae9589aa10af7dc23`. **Environment:** Windows 11 x64 local endpoint, Temurin OpenJDK **21.0.12.1**, Maven Wrapper offline. This report records actual commands and observed outcomes; no History SQLite/TLS/Group implementation is claimed.

## 1. Baseline before new source

- `.\mvnw.cmd -o test`, exit **0**, **122 tests**, 0 failures/errors/skips, `BUILD SUCCESS`, actual Maven time **44.928 s**. Original M0–M2 + Group pure-domain tests, **0 History tests**.

## 2. T011/T013/T015/T017/T019/T021/T023 — seven RED tests

- Wrote tests **before** creating any `domain/history` source:
  - `HistoryConversationKeyTest` (3 tests)
  - `HistoryEventTest` (2)
  - `HistoryEventDeduplicatorTest` (1)
  - `HistoryConsentAndTombstoneTest` (2)
  - `GroupHistoryEligibilityTest` (2)
  - `HistoryCopyStateTest` (2)
  - `HistoryRetentionPolicyTest` (2)
- Command: `.\mvnw.cmd -o '-Dtest=HistoryConversationKeyTest,HistoryEventTest,HistoryEventDeduplicatorTest,HistoryConsentAndTombstoneTest,GroupHistoryEligibilityTest,HistoryCopyStateTest,HistoryRetentionPolicyTest' test` (PowerShell syntax; `JAVA_HOME` set to JDK21).
- Actual **RED exit 1**: Maven compiler `cannot find symbol` for missing History types in the **seven new test files**. Log contained **168** matching diagnostics across build/error summary. Expected RED compile failure, **not** a baseline product failure. Raw log held only in local TEMP, not committed.

## 3. T012/T014/T016/T018/T020/T022/T024 — seven GREEN implementations

Implemented new, isolated `src/main/java/vn/intrachat/domain/history/` classes:
- `HistoryScope` + `HistoryConversationKey` — P2P key derived from 2 verified device identities/SPKI via existing M2 `P2PConversation` and Group namespace isolation; **not a replacement for live TLS pin verification**.
- `HistoryContent` + `HistoryEvent` — Unicode/emoji, immutable source IDs/UTC, bounds and defensive copies; **originSeal bytes are untrusted until separate cryptographic verification**.
- `HistoryConflictDecision` + `HistoryEventIdentity` — eventId within exact scope; byte/hash/signer/original source equality or quarantine, no LWW.
- `HistoryConsentPolicy` + `HistoryDeletionFence` — default DISABLED, capture only after local observed opt-in, local-only tombstones and trusted local generation. **Pure policy, not durable deletion until encrypted store exists**.
- `GroupHistoryPosition` + `GroupHistoryEligibility` — owner-commit order and signed-proof precondition, post-JOIN cutoff and post-REMOVE no new access. **Not proof 004 Group runtime exists**.
- `HistoryLocalState` + `HistorySyncState` — local copy state separate from M2/Group app ACK/READ, manual and verified READY required by state policy; no network.
- `HistoryRetentionPolicy` — **both** user-confirmed default 90 days AND 500 MiB, configuration with safe bounds, protected tombstone cleanup decision; **no disk compaction or DB quota implementation**.

Focused GREEN command: same seven-class Maven selection, exit **0**, **14/14 new tests**, 0 failures/errors/skips, `BUILD SUCCESS`. Full regression afterward: `.\mvnw.cmd -o test` exit **0**, **136/136** (122 old + 14 new), 0 failures/errors/skips.

## 4. T029/T030 — exact P2P scope authorization RED→GREEN

- Wrote `src/test/java/vn/intrachat/security/history/P2PHistoryAuthorizationTest.java` first. Tests: A↔B correct verified ID/SPKI, outsider C with legitimate A↔C pin guessing A↔B scope, wrong key, Group scope confusion, missing consent/capability/verified READY.
- RED command `.\mvnw.cmd -o '-Dtest=P2PHistoryAuthorizationTest' test`, actual **exit 1**, compiler `cannot find symbol` for `VerifiedP2PHistoryAccess`.
- Implemented `security/history/VerifiedP2PHistoryAccess.java` **pure pair-scope/consent gating predicate**. It **MUST** be called only with identities/SPKIs from authenticated M2 session, not JSON claims; it does **NOT** establish TLS identity, capability or signed provenance by itself.
- GREEN command same test, actual **exit 0**, **2/2 tests**, 0 failures/errors/skips, `BUILD SUCCESS`.
- Full regression after this source: `.\mvnw.cmd -o test`, actual **exit 0**, **138/138** tests (122 baseline + 16 History), no failures/errors/skips, Maven time **40.245 s**, finished **2026-11-21T22:14:34+07:00**.

## 5. Package validation

- Earlier `.\mvnw.cmd -o -DskipTests package`: actual exit 0, shaded `BUILD SUCCESS` (4.411 s), before final P2P ACL.
- **Final** `.\mvnw.cmd -o package` **with all tests enabled**, actual **exit 0**, **138/138 JUnit PASS**, 0 failures/errors/skips, shaded artifact `BUILD SUCCESS`, Maven time **42.938 s**, finished **2026-11-21T22:16:09+07:00**.

## 6. Limits, task status and honest release conclusion

**Eligible completed task IDs (22):** T001–T003, T007–T009, T011–T024, T029–T030. T010 has a written **CONDITIONAL/BLOCKED** design assessment but its declared dependencies T004–T006 are not approved; **T010 remains unchecked**. All other tasks **unchecked**.

No JDK21 History network integration or SQLite at-rest security tests were run. The 16 new tests are pure domain/policy only. `[execution-blockers.md](execution-blockers.md)` lists independently confirmed missing SQLite/vendor/crypto reviewer, 004 runtime, and two-physical-PC acceptance evidence. **005 NOT VERIFIED; M3 NOT VERIFIED.** No private conversation, user key, PIN/SAS or database was added to the repo.
