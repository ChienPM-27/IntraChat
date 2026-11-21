# M2 Quickstart & Acceptance Procedure — Future Secure P2P Chat

**STATUS: PROPOSED TEST PROCEDURE ONLY.** M2 has **not** been implemented. Do not expect CHAT_V2 pairing/read receipts or a Live UI to work from this repository today.
**References:** [spec](spec.md), [plan](plan.md), [wire contract](contracts/chat-v2.md), [data model](data-model.md).
**Actual baseline:** M1 Java21 local TCP diagnostics and FE standalone Demo exist; M1 real two-PC Gate 1 remains NOT VERIFIED.

## 1. Java 21 / baseline verification (existing commands)

From the repository root on Windows 11 x64 with actual Java 21 and Maven cache:

    java -version
    .\mvnw.cmd -version
    .\mvnw.cmd clean package
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-m1.ps1 -Offline
    java -jar target\intrachat-0.1.0-SNAPSHOT.jar --diagnostics

**These are M1 baseline commands**, not proof of M2. The current jar default starts the **M1 diagnostic UI**, while:

    java -cp target\intrachat-0.1.0-SNAPSHOT.jar ui.IntraChat

launches the **FE mock Demo**. No real chat authentication exists today. A future M2 build must expose an **explicit Live mode entry/selector** and keep Demo independent and clearly labeled. Do not invent or document a Live launch CLI as working before it exists.

## 2. Planned future two-process loopback

1. Build the future M2 package and run its future verification script (planned scripts/verify-m2.ps1, **NOT CREATED**). Record JDK version, Maven/Surefire exit code and test count.
2. Launch two separate Windows JVM processes with **separate local device profile and security-store directories**, avoiding accidental reuse of a deviceId or DPAPI private key. A plain override of the existing intrachat.profile alone is **NOT sufficient** proof that future secure-store paths are separate; the M2 implementation must provide an explicit safe test profile/security-root override.
3. In A, manually start listener on 127.0.0.1:47321; in B manually Connect to A. A must see real inbound approval popup; Reject → no TLS app session.
4. Accept; initial A/B screens show **the same 64-bit SAS**, verify through independent comparison, Confirm on both. Until both local confirmations and both protected PAIR_READY messages, **Send disabled**; no plaintext user content.
5. In B Live FE send "Xin chào Việt Nam 👋" (UTF-8) to A; A sees correct sender/time/conversation. B shows LOCAL_SENT then **Đã nhận** only after A sends application ACK.
6. Without opening the conversation on A, verify B **does NOT show Đã xem**. Preview/notification/background/hidden viewport does not emit read receipt. Bring A's Live chat to foreground and visibly scroll message bubble into viewport for ≥250ms; after protected read receipt B may show **Đã xem** for that exact message. It only means app displayed the content.
7. A replies with emoji multi-code-point; verify reciprocal delivery ACK/read and matching sender/time. Switch conversation while other message arrives; message must go to correct peer identity, not selected UI.
8. Abruptly disconnect while B sends; B retains draft/uncertain attempt **only in RAM**, never background reconnect or auto resend. Explicit manual retry may warn possible duplicates.
9. Close and reopen both applications: **trust fingerprint still matches** but neither conversation text nor draft/read receipt should be restored. Human inbound approval remains mandatory on new connection.
10. Replace peer key/test changed fingerprint: must block user chat, require manual re-pair; never downgrade to raw M1 to send text.

**Record actual outcomes only when executed.** Do not add test screenshot with real secrets/messages.

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

## 5. Evidence manifest (future entries, not yet recorded)

| Evidence file (proposed) | Test outcome today |
| --- | --- |
| evidence/m2-jdk21-test-results.md | **NOT CREATED / NOT RUN** |
| evidence/us1-fingerprint-pairing.md | **NOT CREATED / NOT RUN** |
| evidence/us2-text-emoji.md | **NOT CREATED / NOT RUN** |
| evidence/us3-sender-time.md | **NOT CREATED / NOT RUN** |
| evidence/us4-delivery-read-receipt.md | **NOT CREATED / NOT RUN** |
| evidence/us5-fe-live-demo.md | **NOT CREATED / NOT RUN** |
| evidence/us6-two-windows-lan.md | **NOT CREATED / NOT RUN** |
| evidence/m2-security-review.md | **NOT CREATED / NOT RUN** |
| evidence/m2-acceptance-report.md | **NOT CREATED / NOT RUN** |

**No current M2 runtime, code, test outcome or user data is produced by this Plan-stage quickstart.**
