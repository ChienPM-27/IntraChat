# US2 — Actual two-JVM TLS CHAT_TEXT, Vietnamese and emoji (local), 2026-11-22

**Executed on:** one Windows 11 host with **two distinct Java 21 OS processes**; NOT two physical Windows LAN PCs. Test-only scripted explicit inbound decision and first-pair SAS comparison; neither is production auto-consent or human GUI verification.

**Command (Temurin21, offline):**

```powershell
$env:JAVA_HOME='C:\Users\XuanHoai\.jdks\jdk-21.0.12.1+1'
$env:PATH=$env:JAVA_HOME+'\bin;'+$env:PATH
.\mvnw.cmd -o -q '-Dtest=SecureChatLoopbackTest' test
```

**Result:** exit 0; `SecureChatLoopbackTest` **1/1 JUnit PASS** with three separate real process phases: first-pair, pinned identity restart, changed-key rejection. `SecurePeerProcessHarness` creates two separate Windows CurrentUser DPAPI-protected `DeviceIdentityStore` files and distinct `PinnedPeerStore` records.

- Outgoing TLS1.3 `CHAT_TEXT` from Client to Server contains UTF-8 Vietnamese/emoji, original messageId/conversation UUID/verified sender UTC instant.
- Server handles message in RAM and sends genuine application `DELIVERY_ACK`; Client verifies `MessageAttempt.APP_ACKNOWLEDGED`, not a socket-write or fake READ.
- Server responds through independently protected TLS `CHAT_TEXT` with Vietnamese/emoji; Client verifies exact protected sender, timestamp and text. Both sides emit only success markers to test logs (no text/SAS/private key output).
- Restart both JVMs with original protected keys/pins; no unnecessary first-pair SAS prompt. A changed private/public key for the same claimed UUID fails to establish a verified session and cannot deliver user text.
- Group, File, History and Media are NOT transmitted via M2. M1 plaintext diagnostics remain separate and message-free.

**NOT RUN / blocking final AC:** independent physical Win11 PC LAN in public-Internet-off scenario, operator-confirmed SAS through two actual Windows GUI processes in this new test, independent security review, different-Windows-user DPAPI and final Gate1.

[Full code/test evidence](m2-local-code-2026-11-22.md) · [Pairing evidence](us1-fingerprint-pairing.md).