# M0 Acceptance Report — IntraChat Foundation

**Date:** 2026-11-21
**Status:** **VERIFIED — Gate 1 passed on Windows 11 x64 + Temurin JDK21**
**Required target:** Windows 11 x64, Java 21 actual runtime, offline/no-peer GUI, reproducible build/test and test-backed design.
**Executed evidence:** [Temurin21 & isolated-network GUI](m0-jdk21-offline.md) · [original test results on JDK25](m0-test-results.md) · [US1](us1-reproducibility.md) · [US2](us2-lifecycle.md) · [US3](us3-contract-review.md).

## Verification summary (new JDK21 evidence)

- Official Eclipse Adoptium **Temurin 21.0.13.1+2** ZIP (SHA-256 checked), installed under user-local `%LOCALAPPDATA%` **without global JAVA_HOME/PATH changes**.
- Maven Wrapper **3.9.16**, `verify-m0.ps1` package + tests + diagnostics → **exit 0**, **9 JUnit tests / 0 failures** running on **actual Java 21 runtime**.
- Temporary clean-source-only checkout with wrapper/Maven cache ran `mvnw.cmd -q -o clean package` → **exit 0**, `java -jar ... --diagnostics` → **exit 0**, **9 tests**. Temp source copy removed.
- JVM-specific **network-denied test harness** `scripts/OfflineLaunchHarness.java` prevented *all standard Java Socket connect/listen/accept/multicast operations* and proved the denial in a control call (`OFFLINE_DENIAL_CONFIRMED`). Unmodified M0 application launched successfully in this restricted JVM on actual Windows desktop. **5/5 open+close** passes, **0 TCP/UDP endpoints** observed for each process. Startup handles appeared in **627–816 ms** over the sampled run; second five-run sample was **568–664 ms** startup and **202–252 ms** exit.
- Actual Windows adapter was **not disconnected** to avoid disrupting unrelated processes/user connections. This is **per-process denial of Java network access**, explicitly documented, not a claim of physical adapter shutdown. It verifies the M0 app can start with Java network unavailable; M0 production code has no network API use.

## Acceptance Criteria

| AC | Observed result | Status |
| --- | --- | --- |
| **AC-01-A** | Clean-source-only offline/cached Maven build and diagnostics on actual Temurin21 | **PASS** |
| **AC-01-B** | 9/9 JUnit plus expected failure when isolated temporary test intentionally broken | **PASS** |
| **AC-01-C** | Swing UI/application/domain/protocol layers separated; no production `java.net` sockets, SQL or native deserialization | **PASS** |
| **AC-01-D** | No-peer and JVM network-denied GUI launch, truthful disconnected status by source/CLI, 5/5 normal close with no process leftovers | **PASS** (controlled process-level offline) |
| **AC-01-E** | Data model/protocol contracts documented and reviewed, not falsely claimed implemented | **PASS — design** |
| **AC-01-F** | 11/11 requested features mapped to phases in feature matrix | **PASS — design** |
| **AC-01-G** | README and quickstart contain build/run/test commands, actual Java21 evidence | **PASS** |
| **AC-01-H** | Constitution I–VII review, JUnit and GUI evidence plus test harness, no outstanding M0 MUST violation | **PASS** |
| **AC-01-I** | Four confirmed product clarifications + offline/duplicate/unauthorized History sync **design only** | **PASS — design** |

## SC-001…SC-009

| Criterion | Result |
| --- | --- |
| SC-001 | **PASS:** clean-source copy built/tested offline using Maven cache on Temurin JDK21 |
| SC-002 | **PASS:** 5/5 Java21 GUI opens/closes, 0 leftover processes |
| SC-003 | **PASS:** per-JVM networking denied, 0 TCP/UDP sockets, app starts with no peer; physical adapter stayed on (explicit test limitation) |
| SC-004 | **PASS:** 9/9 JUnit; deliberately broken assertion detected on isolated copy |
| SC-005 | **PASS — design:** all 11 capabilities mapped |
| SC-006 | **PASS — design:** versioned draft and malformed protocol cases |
| SC-007 | **PASS:** reproducible README commands and evidence |
| SC-008 | **PASS — M0 scope:** Constitution review, no socket/chat/history claims; actual Java21 and network-denied GUI test |
| SC-009 | **PASS — design:** CL-01…04 and multi-device sync situations documented |

## Constitution Gate 1 — result

| Principle | Final M0 review |
| --- | --- |
| I LAN-first | **PASS:** app foundation has no public Internet dependency, no sockets |
| II Identity/version | **PASS:** typed immutable IDs and protocol metadata tests; actual wire parsing deferred 002 |
| III EDT/bounds | **PASS:** Swing UI on EDT, no socket/file/db workers in M0; 5 GUI exit tests |
| IV Security/privacy | **PASS:** no inbound untrusted network data, private History/File not enabled, logs contain no chat secrets |
| V Automated evidence | **PASS:** 9 JUnit, red→green, negative deliberately broken test, Java21 actual build+offline GUI |
| VI Truthful UX | **PASS:** disconnected status, no false chat/read/synced states |
| VII Spec-driven | **PASS:** spec/plan/tasks/contract/evidence and clear future-stage boundaries |

**Decision:** **M0 VERIFIED within approved M0 scope.** No approved Constitution MUST exception. The offline test uses per-process Java network denial rather than OS-level adapter disable; this difference is recorded honestly. The Java21-only SecurityManager harness is **test-only and deprecated**, not a product security feature.

## Handoff to M1 — LAN Networking

- Direct P2P without central server; users only enter IP/port manually (CL-02-01).
- Incoming connections require human Accept/Reject (CL-02-02).
- M1 transports only technical diagnostic payloads; **no authentication or TLS** and prominent warning (CL-02-03); do not send private chat/file/history.
- Reconnect only by user action (CL-02-04).
- TCP framed JSON v1 and bounded sessions, async Swing, untrusted inbound bytes, malformed input tests; build by `specs/002-lan-networking/tasks.md`.
- Two real Windows machines, no Internet, consent and bidirectional PING/PONG are **future M1 acceptance**, not completed by this report.
- Authenticated peer identity and conversation ACL mandatory before History sync in phase 005.

**M1 state:** This M0 decision authorizes starting test-first M1 implementation; it does **not** mark any M1 networking task complete.
