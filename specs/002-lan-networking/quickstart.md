# Quickstart — IntraChat M1 Direct LAN Diagnostics (In Progress)

**Runtime tested:** Windows 11 x64, Eclipse Temurin Java 21.0.13.1+2, Maven Wrapper 3.9.16.
**State:** **Manual TCP/IPv4 diagnostics implemented and locally tested**, including a real two-process Swing loopback demonstration; **two physical LAN PCs NOT TESTED** and M1 not Verified.

## Important security warning

**M1 HAS NO DEVICE AUTHENTICATION AND NO ENCRYPTION.** A self-declared UUID/name/IP or pressing **Chấp nhận** does **not** establish identity or trust. Only synthetic diagnostic PING/PONG is allowed; do not send user chat, History, File or sensitive data. Do not expose the listener to public networks.

## Build & verification (actually run)

Run from repository root in PowerShell with actual Temurin JDK21 installed and active:

```powershell
java -version
.\mvnw.cmd clean package
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-m1.ps1 -Offline
java -jar target\intrachat-0.1.0-SNAPSHOT.jar --diagnostics
java -jar target\intrachat-0.1.0-SNAPSHOT.jar
```

Maven downloads dependencies on first build. `-Offline` works only after dependencies are cached. The **shaded** `target/intrachat-0.1.0-SNAPSHOT.jar` contains Jackson runtime dependency and is launchable with `java -jar`.

**Local test evidence:** Java21 `scripts/verify-m1.ps1 -Offline` completed **45 JUnit tests (sau khi hợp nhất FE demo), 0 failures/errors** (including M0 regression). See [test results](evidence/m1-test-results.md) and [two-process UI evidence](evidence/us2-approval-handshake.md). None of these tests imply production-grade security or two physical PCs.

## Two independent GUI processes on one Windows PC (tested)

Use separate profile locations to avoid accidentally sharing the same self-declared `deviceId`:

```powershell
java '-Dintrachat.profile=C:\Temp\intrachat-A.properties' -jar target\intrachat-0.1.0-SNAPSHOT.jar
java '-Dintrachat.profile=C:\Temp\intrachat-B.properties' -jar target\intrachat-0.1.0-SNAPSHOT.jar
```

Use a **writable existing directory** instead of `C:\Temp` if necessary. A default profile is stored as nonsecret config under `%LOCALAPPDATA%\IntraChat\config\device.properties`. The override only affects the current JVM. To run separate JVMs from one PowerShell instance, start them as separate processes/terminals; each owns its own UI.

1. In A, section 1 enter `127.0.0.1` and TCP port **47321**, click **Bắt đầu lắng nghe**.
2. In B, section 2 enter **127.0.0.1:47321**, click **Kết nối**. B must not show CONNECTED yet.
3. A receives a real **Yêu cầu kết nối LAN đến** dialog with unverified device identity and strong plaintext warning. Click **Chấp nhận** (or **Từ chối** to test the denial). An unanswered dialog expires after ~30s.
4. Only after Accept and protocol v1 HELLO negotiation do both windows show CONNECTED-for-diagnostics. Click **Ping kiểm tra** in B and A to verify synthetic PING/PONG; no chat read receipt.
5. Disconnect with **Ngắt kết nối**. To reattempt, **manually** click **Kết nối lại**; the receiver must approve every incoming connection again.
6. Close both UI windows normally. Network tasks and socket resources should terminate.

The actual local Swing test on this machine observed **A listening**, **B pending**, a **human click Accept**, both connected, and PONG in **both directions** (~6ms and ~7ms from UI). One first attempt timed out; B displayed a failure, then a **manual** retry led to new approval. The application did not auto-accept or auto-reconnect.

## Two Windows PCs on the real LAN (NOT RUN)

- On each PC select the actual private LAN IPv4 address from `ipconfig` (never `127.0.0.1` for another PC), set listener port **47321** (or agreed alternative) and type peer's IPv4:port manually.
- Private-profile firewall inbound TCP rule **only if authorized**; the app does not change Windows firewall. A Wi-Fi guest/client isolation policy may block direct connections.
- Keep LAN communication operational while testing without public Internet if safe/authorized. Require explicit inbound Accept; exchange synthetic Ping/Pong from both sides, reverse roles, then test Reject, timeout and manual retry.
- Record actual results in `evidence/us5-real-lan.md`; **AC-02-A/I and M1 Verified remain BLOCKED** until done.
- See [Windows LAN Setup Guide](../../docs/windows-lan-setup.md).

## Test/diagnostics scope

- `scripts/verify-m1.ps1` tests Java21 build, automated loopback and JAR diagnostics **on one Windows PC**. It does not connect to a second PC.
- `scripts/verify-m0.ps1` tests M0 foundation. M0 was separately verified with actual JDK21 and a JVM network-denial harness; no real LAN evidence is implied.
- No online account, relay, discovery, group, file, chat or history code is enabled by M1.
