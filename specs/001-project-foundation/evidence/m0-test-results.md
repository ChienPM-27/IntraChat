# M0 — Executed Test Results

**Date:** 2026-11-21
**OS:** Windows 11 x64, Windows build 26100
**JDK active:** Oracle Java 25.0.4 (JDK 21 runtime **not installed/verified**)
**Compiler target:** `maven.compiler.release=21`
**Maven:** Wrapper 3.9.16 (system Maven 3.9.14 was used for red-test/bootstrap).

## Commands and outcomes

| Verification | Command / approach | Actual result |
| --- | --- | --- |
| Test-first red | `mvn -q test` before creating Java main classes | exit **1**; compilation reported missing AppInfo/PeerIdentity/MessageMetadata types |
| Wrapper bootstrap | Maven Wrapper plugin 3.3.4, `-Dmaven=3.9.16`, `-Dtype=only-script` | exit **0**; generated wrapper properties and scripts |
| Wrapper version | `.\mvnw.cmd -version` | exit **0**; Maven 3.9.16, JDK 25.0.4 |
| Build & package | `.\mvnw.cmd -q clean package` | exit **0**, JAR generated |
| JUnit after implementation | `mvn -q test` and `.\mvnw.cmd -q test` | exit **0**, **9 tests/0 failures/0 errors** |
| Command diagnostics | `java -jar target/intrachat-0.1.0-SNAPSHOT.jar --diagnostics` | exit **0**; IntraChat, disconnected |
| Automated verification | `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-m0.ps1` | exit **0**, package/test/diagnostics + count checked |
| Offline tests (cached) | `.\mvnw.cmd -q -o test` | exit **0** |
| Isolated checkout-like copy | copy `pom.xml`, wrapper, source only to temporary folder, `.\mvnw.cmd -q -o clean package` | exit **0**; no compiled classes copied |
| Isolated negative test | in temporary copy, change AppInfo assertion to `WRONG_PRODUCT`, run `.\mvnw.cmd -q -o test` | exit **1**, **9 tests/1 expected failure**, temp cleaned |
| Windows GUI lifecycle | five independently launched `javaw -jar ...` processes, Windows title lookup, normal close, process gone | **5/5 PASS**, see [US2 evidence](us2-lifecycle.md) |

## Test inventory

| Class | Tests | Failures |
| --- | ---: | ---: |
| `AppInfoTest` | 1 | 0 |
| `ConnectionStateTest` | 2 | 0 |
| `PeerIdentityTest` | 3 | 0 |
| `MessageMetadataTest` | 3 | 0 |
| **Total** | **9** | **0** |

## Limitations / NOT RUN

- JDK **21 runtime** and clean checkout on a machine with only JDK 21: NOT RUN. Target 21 compilation is not runtime verification.
- Physical disconnect of all Internet interfaces for GUI smoke: NOT RUN (M0 source doesn't open sockets).
- Startup ≤3s / exit ≤2s quantitative performance budget: NOT MEASURED.
- Peer-to-peer socket, group messaging, history synchronization, file/media: OUT OF SCOPE, no tests claimed.
- History data confidentiality/authentication: NOT IMPLEMENTED; not asserted.

**Automated sub-gate: PASS.** **Full M0 readiness: pending outstanding platform/offline/acceptance checks.**
