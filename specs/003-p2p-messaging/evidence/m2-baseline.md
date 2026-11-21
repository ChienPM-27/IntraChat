# M2 Windows JDK21 build and regression evidence

**Date:** 2026-11-21. **Platform:** Windows 11 x64 build 26100; Temurin JDK **21.0.13.1+2**, Maven Wrapper **3.9.16**.

| Stage | Command | Actual result |
| --- | --- | --- |
| Before M2 code | `.\mvnw.cmd -q -o clean package` | **Exit 0, 45/45 existing M0/M1/FE JUnit pass** |
| Test-first RED | `.\mvnw.cmd -q -o test` after new security/domain/codec test sources but before implementing classes | Expected **nonzero**, missing `ChatText`, `P2PConversation`, security and CHAT_V2 codec classes |
| Foundation GREEN | `.\mvnw.cmd -q -o test` | **55/55 pass** on JDK21 |
| CHAT_V2 codec/state | same | **65/65 pass** |
| Actual JSSE TLS1.3 socket | same | **67/67 pass** |
| SecureChatCoordinator, real loopback pairing/Reject | same | **69/69 pass** |
| LiveChatService + real protected ACK/read | same | **70/70 pass** |
| FE viewport observer | same | **71/71 pass** |
| Windows OS foreground guard | `.\mvnw.cmd -q -o clean package` | **72/72 pass** |
| Asynchronous ACK race regression | same | **73/73 pass** |
| Changed-key MITM test | `.\mvnw.cmd -q -o test` | **74/74 pass** |
| Runnable original M1 jar | `java -jar target/intrachat-0.1.0-SNAPSHOT.jar --diagnostics` | **Exit 0** after Maven Shade signature metadata filter |
| M2 single-PC verification | `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-m2.ps1 -Offline` | **Exit 0, 74/74 JUnit pass, 0 errors/failures**, runtime Temurin21, runnable shaded JAR verified (final run after changed-key/failure cleanup fixes) |

**Important failure found and fixed:** First shaded build with Bouncy Castle retained the original signed JAR's `META-INF/*.SF,*.RSA,*.DSA`; Java launcher threw `SecurityException: Invalid signature file digest`. Maven Shade filter now removes those signature files before packaging. Verified `java -jar --diagnostics` exits 0 and `ui.LiveIntraChat` opens on real Windows.

**Not tested:** two distinct physical Windows LAN computers, end-to-end security audit, signed release build, production cryptographic hardening or exact SLA. Historical test totals are not M2 verification on 2 PCs. Evidence of real two independent GUI processes: [US1](us1-fingerprint-pairing.md), [US4](us4-delivery-read-receipt.md).

**Later regression addendum, same date:** Following [test-first hardening](m2-hardening-round2.md), `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-m2.ps1 -Offline` on actual Temurin **21.0.12.1+1** exited **0** with **93/93 JUnit tests PASS, 0 failures/errors** and runnable shaded diagnostics JAR. This supersedes the historical 74/74 suite count for current source but does not supersede any physical two-machine, independence or security-signoff gate.
