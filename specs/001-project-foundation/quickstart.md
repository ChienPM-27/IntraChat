# Quickstart — IntraChat Foundation (M0)

**Platform:** Windows 11 x64. **Java source target:** Java 21. **UI:** Swing.

## Prerequisites

- Java 21 LTS JDK recommended; Java 25 JDK can compile with `--release 21` but must not be claimed as a Java 21 runtime test.
- PowerShell, Git and an Internet connection for the **first dependency / Maven Wrapper download**, unless Maven cache or mirror is already provisioned.
- GUI desktop session for Swing; headless test servers can run unit tests but not the GUI smoke check.
- This stage does **not** connect peers or sync chat histories.

## Commands (Windows PowerShell from repository root)

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-environment.ps1
.\mvnw.cmd -version
.\mvnw.cmd clean package
.\mvnw.cmd test
java -jar target/intrachat-0.1.0-SNAPSHOT.jar
java -jar target/intrachat-0.1.0-SNAPSHOT.jar --diagnostics
```

If using system Maven during wrapper/bootstrap troubleshooting: `mvn clean package`.

Test without network **after dependencies are cached**: `.\mvnw.cmd -o test`.

## Validation

- Console diagnostics should print `IntraChat` and `Chưa kết nối` without creating a socket.
- GUI should show an IntraChat window, foundation-only message, and close normally.
- Verify five consecutive GUI open/close cycles and process exit before marking M0 fully verified.
- Do not call this release a LAN messenger yet; it is the **foundation** only.

## Verified environment and outcomes

**Originally executed 2026-11-21 on Windows 11 x64, Oracle JDK 25.0.4:** Maven Wrapper 3.9.16, `clean package` success, `test` 9/9 pass, `--diagnostics` success, `scripts/verify-m0.ps1` success. A temporary clean-source copy also built offline with cached dependencies; deliberately broken test failed as expected. Five GUI open/close cycles passed. **Subsequent M0 Gate 1 (2026-11-21):** Actual Temurin21 build/test/diagnostics and 5/5 GUI open/close inside network-denied JVM verified; Windows adapter was NOT physically disconnected. See [M0 JDK21 and offline test evidence](evidence/m0-jdk21-offline.md). Full evidence: [M0 test results](evidence/m0-test-results.md), [lifecycle](evidence/us2-lifecycle.md).
