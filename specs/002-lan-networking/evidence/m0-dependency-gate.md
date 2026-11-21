# M1 T001 — M0 dependency gate resolution

**Date:** 2026-11-21
**Decision:** **M0 Gate 1 verified**, enabling M1 implementation **without claiming M1 works yet**.

The previous 3 open M0 tasks were T025 (offline/no-peer GUI), T040 (Constitution Gate 1 + actual Java 21 runtime), T041 (handoff). Actual tests on Windows 11 x64:

- Official Temurin JDK **21.0.13.1+2**, SHA-256 checked, user-local; `mvnw.cmd -version` ran with Java21.
- `verify-m0.ps1` under Java21: 9/9 JUnit passed, package and diagnostics passed.
- Source-only temporary checkout `mvnw.cmd -o clean package`: pass with cached dependencies, 9/9 tests.
- Dedicated JVM network-denied harness compiled under JDK21, proving Socket connect/listen/accept/multicast fail while IntraChat GUI still launches; 5/5 GUI windows open/normal close, 0 TCP/UDP endpoints. Second sample: startup 568–664ms, normal exit 202–252ms.
- Network adapter **not physically disconnected** (avoid disruption); controlled **process-level network denial**, with no app-owned sockets, is the documented substitute. Full details: [M0 Java21+offline evidence](../../001-project-foundation/evidence/m0-jdk21-offline.md).
- Source scan: production 10 Java classes contain no `java.net`/Socket/SQL/ObjectInputStream references. Test harness is separate from production JAR.

**Gate evidence:** [M0 acceptance report](../../001-project-foundation/evidence/m0-acceptance-report.md), [M0 tasks](../../001-project-foundation/tasks.md), [Constitution review](../../001-project-foundation/checklists/constitution-check.md).

**Constraint for implementation:** M1 unverified; user-approved CL-02-01…04 cannot be relaxed. No History/File/Group/Chat over plaintext/unverified link. M1 Gate 1 requires actual 2-Windows-device LAN test and negative cases, not just loopback.
