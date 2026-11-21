# Quickstart — Verification procedure for History 005 (DRAFT; NOT RUN)

**Feature:** 005-chat-history · **Status:** TEST PLAN ONLY; no History Java classes, encrypted SQLite database, HISTORY_V1 codec or implemented Sync tests exist on this branch. [Plan](plan.md) · [Spec](spec.md) · [CL candidates](clarifications.md) · [Contract draft](contracts/history-v1.md).

## 1. Safety and gate checks

1. Review and **confirm CL-05-01…10**. Do not treat baseline A in the plan as consent.
2. Resolve independent review and supply-chain approval for any native encrypted SQLite driver; verify DPAPI CurrentUser, ACL, DB/WAL/journal/temp encryption on Win11 x64. If no reviewed encrypted storage, **STOP** instead of plaintext fallback.
3. Confirm M1/M2 Gate 1 status and keep Group History disabled until Group 004 owner handoff, signed membership and Group wire are actually implemented and security-reviewed.
4. Use **a separate clean worktree** for 005 to preserve uncommitted changes in 003 and remaining Group 004 work. Keep real chats, PIN/SAS, keys, encrypted DB/profile, event logs with private content out of Git.

## 2. Baseline (future implementation day)

~~~powershell
# Run from a clean 005 worktree on Windows 11 x64.
# Use an actual Java 21 installation, not the system default if it is Java 25.
$env:JAVA_HOME = 'C:\Path\To\Installed\JDK21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
.\mvnw.cmd -o test
.\mvnw.cmd -o package
~~~

Record actual commit, Java version, command, exit code, JUnit XML counts; no pass is asserted here. Build source/test classes only after approved plan and RED tests. A temporary path shown above is a placeholder to replace with the installed JDK21 path.

## 3. RED→GREEN implementation schedule

| Stage | RED tests written first | Success scenario (later, after GREEN) |
| --- | --- | --- |
| Domain | Immutable event, scoped P2P/Group IDs, provenance, deletion fence/duplicate/conflict | Same eventId+digest idempotent; different content quarantined |
| Encrypted persistence | Wrong Windows user, corrupted key/DB/WAL, atomic crash, no plaintext fallback, migration and keyset pagination | Same-user restart restores committed Unicode; no plaintext artifacts |
| Source proof | Forged origin, key rotation, self-authored seal, Group owner routing proof | No authenticated sender from TLS hop alone |
| History codec | v1/v2/Group downgrade, duplicate JSON keys, frame limits, invalid UTF-8, replays | Strict independent HISTORY_V1 only after post-TLS confirmation |
| Two-peer sync | Verified M2 A↔B, missing event, interrupted chunk, repeated ID, outsider C | B sends only eligible signed events; A durable applies and resumes without duplicates |
| Group sync | A/B/C owner, late joiner D, removed C, owner handoff, ≥3 real JVM processes | No pre-JOIN history or post-REMOVE new deltas |
| Live UI | Opt-in/disable, paging, user delete/tombstone, Demo isolation and foreground READ | SAVED/SYNCED never implies DELIVERED/READ, EDT responsive |
| Release | Cross-account DPAPI, disk stress, privacy scan, two **physical** Windows LAN PCs, independent review | Actual AC-05-A…P test evidence and Gate 1 signoff |

## 4. Multi-process scenarios (future acceptance)

**P2P A↔B:** Run two distinct JVM/device profiles with separately verified M2 pins and explicit human inbound approval. User opts in to History, sends **eligible** real Live chat and stores it; kill/restart one client; view committed History offline. On manual reconnect, negotiate protected HISTORY_V1 and request missing allowed events; kill during DELTA; retry and verify idempotence, source unchanged, no SYNCED before durable ACK.

**Unauthorized C:** Establish separate verified M2 TLS A↔C, then C guesses A↔B conversation ID; request HISTORY_SUMMARY/DELTA. Expected 0 count/metadata/content disclosure and no grant despite valid TLS.

**Group A/B/C/D:** Only after 004 Group wire is actually verified. Owner A, members B/C, D joins late. D must receive **zero** events before its owner JOIN commit. Remove C and verify C receives **zero** new History frames after owner durable REMOVE. Perform actual A→B signed owner handoff; stale A fails all Group sync. At least three independent clients/JVMs, with actual peer identities and a 5-device capacity test.

**Physical LAN:** At least **two distinct Windows 11 x64 physical machines** in a private LAN with public Internet unavailable, manual IPv4:port, human Accept and SAS confirmation, secure TLS. Distinguish number of JVMs from number of PCs; one PC with three JVMs does not satisfy this gate.

**Windows data privacy:** Same-user restart recovers encrypted history; second authorized Windows account fails to decrypt/open; corrupt DB/WAL/ACL/reparse or exhaust disk and verify safe error; scan files/logs for synthetic private markers, not real chat.

## 5. Evidence report template (not a PASS report)

~~~text
Date, commit, branch: <observed>
Win11 physical PCs, distinct JVMs, Windows users: <observed counts>
Java and Maven versions: <observed>
CL-05 decisions/approved storage driver: <approval refs>
RED test command/exit/failure: <actual>
GREEN test command/exit/count: <actual>
Local restart saved event (same account): <actual>
Wrong Windows user/ACL/DB/WAL cleartext scan: <actual>
A↔B manual sync + outsider denial: <actual>
Group late JOIN/REMOVE/handoff: <actual or BLOCKED>
M0/M1/M2/004 regression: <actual or BLOCKED>
AC-05-A…P mapping: <each PASS/FAIL/BLOCKED with evidence refs>
Independent dependency/security/privacy reviewer signoff: <actual or BLOCKED>
Decision: NOT VERIFIED unless every mandatory gate passed
~~~

**Stop rule:** missing reviewer, physical Windows LAN machines, authenticated Group 004 runtime or tested encrypted store is a release BLOCKER, not permission to weaken ACL, disable encryption, auto-backfill old Group content or claim success from design. Full M3 still needs verified Group 004 and History 005.
