# Integrating the separate FE Messenger demo with M1 diagnostics

**Date:** 2026-11-21
**Git context:** FE team pull request #1 (merge `a611dba`) landed on remote `main` while M1 development was in progress. To avoid overwriting the FE work, M1 was implemented on branch `feat/m1-lan-networking`; the FE merge was brought **into that branch** (merge commit `8af774d`). No forced push to `main`.

## Collision and resolution

- The FE branch introduced `ui.IntraChat`, Swing/FlatLaf Messenger-style standalone demo, mock conversations/messages, demo resources and its own JUnit tests.
- FE merge also changed `pom.xml` to Java 17/JUnit 5.12.2/FlatLaf and prepended a second `<project>` block around the M0 pom. The resulting remote POM is **not valid XML**.
- The feature branch now has **one valid Maven POM** preserving the FE FlatLaf 3.6.2 dependency, FE source/resources/tests and its mock GUI; it also keeps Java21/Maven Wrapper 3.9.16/JUnit5.14.4, Jackson2.22.4 for M1, shade plugin and the M1 diagnostic entrypoint.
- `README.md` merge conflict was resolved preserving FE demo instructions, explicit mock-data warning, and a separate M1 manual TCP diagnostic launch command.
- **Two executable entrypoints coexist in one shaded JAR**:
  - `java -jar target/intrachat-0.1.0-SNAPSHOT.jar` → `vn.intrachat.IntraChatApplication`, technical M1 TCP diagnostics UI.
  - `java -cp target/intrachat-0.1.0-SNAPSHOT.jar ui.IntraChat` → FE Messenger-style standalone mock UI. **This does not send real LAN messages**.
- This is **code coexistence**, NOT a unified chat application. Feature 003 must connect FE's UI to authorized application/network/domain interfaces; no fake chat states should be sent over the unauthenticated M1 diagnostic transport.

## Actual verification

- On actual Temurin Java21 after merging FE, `.\mvnw.cmd -q clean package` → **exit 0**; merged Maven/Jackson/FlatLaf shaded JAR ~**3,764,968 bytes**.
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-m1.ps1 -Offline` → **exit 0**, **45 JUnit tests**, 0 failures/errors (original M0, M1 local TCP and FE tests).
- `javaw -cp <shaded-jar> ui.IntraChat` launched the FE login screen **IntraChat — Đăng nhập — Bản demo · Không kết nối máy chủ** in real Windows UI and then closed normally.
- M1 JAR GUI separately launched and tested with manual inbound user consent and synthetic PING/PONG in two independent Java Windows processes.
- No human chat content was sent over TCP; the FE mock service remains separate.

## Remaining

- **Two physical Windows LAN PCs** and real offline-LAN acceptance: NOT RUN.
- FE model/UI integration with real authenticated messaging: future 003, not M1.
- FE demo login/password is not real account authentication. Do not use actual passwords or private files in mock UI.
