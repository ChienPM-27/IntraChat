> **Lưu ý thay đổi UI (21/11/2026):** M1 diagnostic UI và cửa sổ Live độc lập đã được gỡ. Chạy `java -jar target/intrachat-0.1.0-SNAPSHOT.jar` để mở Messenger; chọn **Chat trong LAN (TLS 1.3)** tại trang đăng nhập để kết nối, xác minh fingerprint. Các đoạn hướng dẫn cũ bên dưới (nếu có) cần hiểu theo entrypoint thống nhất.

# M2 Quickstart & Acceptance Procedure — Local Secure P2P Prototype

**STATUS (22/11/2026): IN PROGRESS — 65/90 checked, full Java21 223/223 JUnit PASS; NOT security certified or M2 Verified.** Unified Messenger uses a strict shared-port **v1 diagnostics-only / v2 mutual TLS** dispatcher, which rejects invalid/downgraded frames. First-pair SAS/pin, protected text/ACK/foreground-view READ and **explicit UNCERTAIN manual retry** now have local source/tests; two separate Java processes on **one physical Windows PC** have passed protected two-way emoji tests. Independent security review, full secure queue/EDT stress and **two physical Windows LAN PCs** remain outstanding. [Latest pairing/TLS/Live UI RED→GREEN evidence](evidence/m2-continued-code-2026-11-22.md) · [Earlier local evidence](evidence/m2-local-code-2026-11-22.md).
**References:** [spec](spec.md), [plan](plan.md), [wire contract](contracts/chat-v2.md), [data model](data-model.md).
**Actual baseline:** M0 Verified (41/41); M1 local **53/60**, including M1 dialog in Messenger, but real two-PC M1 Gate 1 remains NOT VERIFIED. M2 **65/90**, M2 Gate 1 NOT VERIFIED.

## 1. Java 21 / baseline verification (existing commands)

From the repository root on Windows 11 x64 with actual Java 21 and Maven cache:

    java -version
    .\mvnw.cmd -version
    .\mvnw.cmd clean package
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-m2.ps1 -Offline
    java -jar target\intrachat-0.1.0-SNAPSHOT.jar --diagnostics

**Unified entrypoint (updated 2026-11-22):** `java -jar target/intrachat-0.1.0-SNAPSHOT.jar` launches **one Messenger frontend**; select **Chat trong LAN** for M2 TLS user chat or **Demo** for mock-only data. Its manually started listener on the selected TCP port routes **M1 v1 synthetic Ping/Pong** (requires separate plaintext-warning consent) versus **M2 v2 TLS** (requires human consent and first-time SAS). M3 Group packets remain rejected; no v1 chat downgrade. The M2 script runs Java21 regression but **cannot** verify two physical PCs, human pairing or independent security review.

## 2. Actual two-process Windows loopback (selected steps tested; remaining are acceptance targets)

1. Build the current M2 prototype using `.\mvnw.cmd -o clean package` and `scripts/verify-m2.ps1 -Offline` (requires cached dependencies and actual JDK21). The original M2 baseline recorded **74 JUnit tests**; newer security test additions must be counted from **current Surefire XML**, not that historical number. See [baseline](evidence/m2-baseline.md) and [security hardening evidence](evidence/certificate-validation-jdk21.md).
2. Launch **two separate terminals/JVMs** with unique `-Dintrachat.secureRoot` paths and the unified Messenger entrypoint (replace directory paths with writable directories that will be cleaned after test):

       java '-Dintrachat.secureRoot=C:\Temp\intrachat-live-A' -jar target\intrachat-0.1.0-SNAPSHOT.jar
       java '-Dintrachat.secureRoot=C:\Temp\intrachat-live-B' -jar target\intrachat-0.1.0-SNAPSHOT.jar

   The standard live launch without an override uses `%LOCALAPPDATA%\IntraChat\security\`. **Do not use the same secureRoot on two JVMs**, since that would share a private identity; avoid leaving synthetic test credentials in shared folders. `C:\Temp` is an example, use a writable location.
3. In both Messenger login windows, click **Chat trong LAN (TLS 1.3)**. In A, manually start listener on 127.0.0.1:47321; in B manually Connect to A. A must see real inbound approval popup; Reject → no TLS app session.
4. Accept; initial A/B screens show **the same 64-bit SAS**, verify through independent comparison, Confirm on both. Until both local confirmations and both protected PAIR_READY messages, **Send disabled**; no plaintext user content.
5. In B Live FE send "Xin chào Việt Nam 👋" (UTF-8) to A; A sees correct sender/time/conversation. B shows LOCAL_SENT then **Đã nhận** only after A sends application ACK.
6. Without opening the conversation on A, verify B **does NOT show Đã xem**. Preview/notification/background/hidden viewport does not emit read receipt. Bring A's Live chat to foreground and visibly scroll message bubble into viewport for ≥250ms; after protected read receipt B may show **Đã xem** for that exact message. It only means app displayed the content.
7. A replies with emoji multi-code-point; verify reciprocal delivery ACK/read and matching sender/time. Switch conversation while other message arrives; message must go to correct peer identity, not selected UI.
8. Abruptly disconnect while B sends; B retains draft/uncertain attempt **only in RAM**, never background reconnect or auto resend. Explicit manual retry may warn possible duplicates.
9. Close and reopen both applications: **trust fingerprint still matches** but neither conversation text nor draft/read receipt should be restored. Human inbound approval remains mandatory on new connection.
10. Replace peer key/test changed fingerprint: must block user chat, require manual re-pair; never downgrade to raw M1 to send text.

**Actually observed on this Windows PC:** Historical steps 2–6 for initial human GUI pairing and view-based READ are documented in [US1](evidence/us1-fingerprint-pairing.md), [US4](evidence/us4-delivery-read-receipt.md), [US5](evidence/us5-fe-live-demo.md). Newer **automated** 22/11 evidence includes [two genuine JVM processes with first-pair SAS callback, pinned restart, changed-key denial and encrypted two-way text/emoji](evidence/us2-text-emoji.md), [sender UTC/timezone mapping](evidence/us3-sender-time.md), [manual UNCERTAIN retry with fake clock](evidence/m2-local-code-2026-11-22.md) and [partial secure RAM/peer resource tests](evidence/m2-resource-tests.md). **Production manual-retry GUI under real two-host LAN, complete secure queue/EDT stress, and physical dual-PC acceptance are NOT FULLY RUN**; don't confuse test callback approval with human GUI approval. Do not upload real secrets/messages.

## 3. Mandatory two-physical-Windows-PC acceptance (NOT RUN)

- **Machine A and B** must be distinct Windows 11 x64 hosts on a permitted LAN, with actual private IPv4 addresses checked by ipconfig. **127.0.0.1 is not valid** as the other physical machine's address.
- Keep local LAN available while removing public Internet access safely/with authorization; do not disrupt unrelated work or change Firewall policy without approval.
- Start the user-selected IPv4:port listener on A (default 47321/TCP) and manually enter A's IPv4:port on B. If inbound traffic blocked, have an authorized administrator configure only an appropriate **Private** profile rule, not a silent Public-profile exception. See [Windows LAN guide](../../docs/windows-lan-setup.md).
- Verify explicit per-connection inbound Accept; both users compare code/fingerprints **out-of-band**. Confirm protected session and correct peer identity, send text/emoji B→A and A→B.
- Verify **Đã nhận** from real app ACK; then verify **Đã xem only after recipient Live foreground viewport** and protected read receipt, not earlier. Test preview, inactive window, message offscreen, and another conversation.
- Verify failed/stale ACK and read receipt do not elevate states, spoofed key/UUID/replaced certificate fails, manual reconnect, no automatic offline outbox and no History after restart. On reconnect known peer must still have inbound Accept prompt.
- Validate correct shutdown, thread/socket quotas and no raw user text logged. Include a sanctioned encrypted-traffic packet capture to confirm no plaintext CHAT_TEXT (do not capture real private user content).
- Record OS build, actual JDK21, app commit/build hash, two sanitized host identifiers, network topology, IP:port (mask if needed), test date, screenshots/observed statuses, manual consent, result of no-Internet condition and test failures **from both machines**.

**Mandatory blocker:** The M1 Gate 1 real dual-device verification is still pending. A two-process single-machine result cannot make M1 or M2 Verified. Likewise M2 cannot be marked Verified until AC-03-A…L and Constitution I–VII have actual evidence.

## 4. Negative test cases that future test suite MUST cover

| Area | Required rejection / expected behavior |
| --- | --- |
| v1 vs v2 | v1 diagnostic still works, raw v1 CHAT_TEXT rejected, v1-only peer does not trigger plaintext fallback |
| Pair | Unknown key without SAS approval never chat; wrong codes, one-sided approval, cancelled consent, changed/revoked pin after restart block |
| TLS | Invalid/expired cert, unapproved self-signed identity, key-algorithm mismatch, attempted downgrade fail closed |
| Identity | Reused self-declared UUID with different key, duplicate displayName, wrong destination/peer, session mismatch |
| Text | Empty/whitespace, invalid UTF-8, Unicode surrogate error, >8,000 code points/32 KiB, malformed JSON/prefix |
| ACK | Wrong sender/session/message/conversation, replay, duplicate and no-ACK after network failure never falsely delivered |
| Read | Preview, background, offscreen, false UI event, missing message, forged/duplicate/wrong-session read receipt never falsely READ |
| Concurrency | Pending approval flood, 4 active and 4 pending quota, 128 receipt cap, 64 KiB frame boundary and 2 MiB queue limit |
| Offline and restart | Draft/manual retry only, no auto reconnect/resend/read queue, no chat or receipt persisted; trust data persists independently |
| FE modes | Live gets actual network events, Demo contains only mock data and seeded read flags, no demo password used for identity |

## 5. Evidence manifest — factual results

| Evidence file (proposed) | Test outcome today |
| --- | --- |
| [evidence/m2-baseline.md](evidence/m2-baseline.md) | **74/74 JUnit tests PASS on actual JDK21 in final `verify-m2.ps1 -Offline` run** |
| [evidence/us1-fingerprint-pairing.md](evidence/us1-fingerprint-pairing.md) | **Local two-GUI first pair + restart + inbound approval PASS** |
| [evidence/us2-text-emoji.md](evidence/us2-text-emoji.md) | **New 22/11: two real Java 21 JVM processes on one PC, verified TLS text/emoji both directions, app ACK, pinned restart and key change rejection PASS** |
| [evidence/us3-sender-time.md](evidence/us3-sender-time.md) | **New 22/11: 3/3 JUnit UTC/timezone/alias/verified UUID tests PASS; human cross-PC GUI acceptance NOT RUN** |
| [evidence/m2-local-code-2026-11-22.md](evidence/m2-local-code-2026-11-22.md) | **Earlier 22/11 local slice:** shared v1/v2 listener, manual uncertain retry, typed receipts, Live EDT/GUI tests |
| [evidence/m2-continued-code-2026-11-22.md](evidence/m2-continued-code-2026-11-22.md) | **Latest 22/11: 65/90; 223/223 PASS; actual TLS peer certificate binding, trust-state revocation/READY, Live multiline draft vs original Demo composer, UUID-only offline peers** |
| [evidence/m2-resource-tests.md](evidence/m2-resource-tests.md) | **New 22/11: real TLS 4 active, 128 pending; 500/2MiB/1024 dedup caps, T078 still PARTIAL/OPEN** |
| [evidence/us4-delivery-read-receipt.md](evidence/us4-delivery-read-receipt.md) | **Local real GUI ACK vs READ foreground/background PASS** |
| [evidence/us5-fe-live-demo.md](evidence/us5-fe-live-demo.md) | **Live real LAN loopback and Demo separate GUI launch PASS** |
| Two distinct physical Win11 LAN PCs | **NOT RUN — release BLOCKED** |
| Independent security review | **NOT DONE — security release BLOCKED** |
| [evidence/m2-acceptance-report.md](evidence/m2-acceptance-report.md) | **PARTIAL, NOT VERIFIED** |

**Local prototype works; M2 remains IN PROGRESS / NOT VERIFIED.** Never confuse two Java processes on one machine with two physical Windows LAN computers or a security certification.

## 6. Current security/compatibility review checklist

- [x] Protocol-specific Java21 negative tests and documented canonical [SAS/UUID vectors](contracts/chat-v2.md); synthetic only, no genuine keys.
- [x] [Threat model](evidence/threat-model.md) documents MITM/impersonation/pinning/downgrade/DPAPI/ACK/read/replay/DoS stop rules.
- [ ] Formal dependency CVE/SBOM/signature/legal approval ([dependency evidence](evidence/dependency-review.md)).
- [ ] Complete M2 TLS **4-pending / 16-frame+2MiB actual TLS queued writer / half-open / EDT** resource stress and real two-host human UI accessibility. **Local 4-active/128-pending/500-message/2MiB+1024-dedup and manual uncertain retry have passing tests**, but T078/GUI release acceptance are still OPEN.
- [ ] Both separate physical Win11 LAN computers and independent security reviewer/sign-off.

See [Windows M1/M2 LAN/firewall guide](../../docs/windows-lan-setup.md) for a practical **Private** firewall-only, manual IPv4:port and two-machine evidence procedure.