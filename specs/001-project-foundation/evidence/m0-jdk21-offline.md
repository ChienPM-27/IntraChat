# M0 — Java 21, network-denied runtime, and fresh checkout evidence

**Executed:** 2026-11-21 on Windows 11 x64, build 26100.
**JDK:** Eclipse Temurin OpenJDK `21.0.13.1+2`, downloaded as vendor ZIP from Eclipse Adoptium official API, **SHA-256 verified**: `bbebea1469996b276683c9299c227b294f85c847039c82633218186a4ad4fbf2`.
**JDK location:** User-local `%LOCALAPPDATA%\IntraChat\toolchains\jdk-21.0.13.1+2` (not committed and no global JAVA_HOME/PATH change).
**Maven:** Wrapper 3.9.16.
**Source scope:** M0 Java/Swing application; no actual LAN transport source or History sync at moment of these tests.

## 1. Actual JDK21 build/test/diagnostics

In a **command-scoped environment** with `JAVA_HOME` set to user-local Temurin JDK21 and `PATH` prefixed with its `bin`:

- `java -version` → OpenJDK 21.0.13.1 Temurin.
- `.\mvnw.cmd -version` → Apache Maven 3.9.16, Java 21.0.13.1.
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-m0.ps1` → **exit 0**; Maven package + tests + diagnostics pass, **9 tests/0 failures/errors**.
- Diagnostics printed `IntraChat 0.1.0-SNAPSHOT — Chưa kết nối`.
- A separate, clean-source-only temporary directory containing `pom.xml`, wrapper files and `src/` (no `target/` copied) ran `.\mvnw.cmd -q -o clean package` → **exit 0**, `java -jar target/intrachat-0.1.0-SNAPSHOT.jar --diagnostics` → **exit 0**, 9 test cases; temp folder removed.

This resolves the previous **Java 21 actual runtime** blocker (not just `--release 21` compilation).

## 2. Windows GUI with network calls denied at JVM boundary

A separate **Java 21-only manual test harness**, `scripts/OfflineLaunchHarness.java`, was compiled outside the application JAR into `target/m0-harness`. It installs a deprecated Java SecurityManager that denies **all `java.net` connect/listen/accept/multicast attempts** and performs a control attempt to create a localhost socket, which raises the expected `SecurityException` (`OFFLINE_DENIAL_CONFIRMED`). It then calls the unmodified `IntraChatApplication.main`. Diagnostic run with network denied → **exit 0** and truthful `Chưa kết nối` output.

On the **actual Windows desktop**, `javaw.exe` from Temurin 21 ran the harness with `-Djava.security.manager=allow`. Five independent opens/normal window closes produced:

| Cycle | PID | Window observed | Startup to window | TCP endpoints | UDP endpoints | Process exited normally |
| ---: | ---: | --- | ---: | ---: | ---: | --- |
| 1 | 388 | IntraChat | 816 ms | 0 | 0 | Yes |
| 2 | 22292 | IntraChat | 640 ms | 0 | 0 | Yes |
| 3 | 20340 | IntraChat | 688 ms | 0 | 0 | Yes |
| 4 | 7572 | IntraChat | 627 ms | 0 | 0 | Yes |
| 5 | 18744 | IntraChat | 673 ms | 0 | 0 | Yes |

**Result:** **5/5**, GUI launched with network APIs denied, zero observed TCP/UDP endpoints, all closed normally. No window was killed by force. The title was observed through Windows window handles, while body text was checked against source and CLI diagnostics. The reported startup values include JVM launch to first nonzero window handle, not a pixel-paint or accessibility readiness benchmark.

**Important limitation:** The **Windows machine's physical network adapter/Internet was not disabled** because that would disrupt unrelated user work/connections. The test instead denies all standard Java networking from **this JVM**; this directly verifies the app's offline independence without changing global Firewall/adapter settings. This is a controlled network-isolation test, not a claim that all OS networking was physically disconnected. The harness uses deprecated SecurityManager available in JDK21 and must **never** be treated as a production sandbox or used for security enforcement.

## 3. Review and handoff

- M0 production source does not open sockets, run media transfer or access History DB. The test-only `OfflineLaunchHarness.java` intentionally attempts a blocked socket **outside the application JAR**; this is not a production network capability.
- No secret, private chat or file data were used/logged. App status `Chưa kết nối` is truthful. `CONNECTED` cannot be displayed in M0 because networking has not been implemented.
- Physical full-machine offline, cross-JVM SecurityManager removal in JDK25, and external two-machine LAN testing are **not** claimed.
- With JDK21 actual runtime verification, per-process no-network GUI test, 9 tests and source/build checks, M0 Gate 1 may be reviewed against Constitution. Two-machine LAN proof belongs to **M1**.

**This evidence is actual execution; M1 network handshake/diagnostics have NOT yet been tested.**
