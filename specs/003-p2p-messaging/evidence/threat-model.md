# M2 threat model — authenticated LAN chat (T004)

**Date:** 2026-11-21 · **Scope:** `003-p2p-messaging` only · **State:** engineering threat assessment; **independent security sign-off PENDING**.
**Preliminary reviewer:** implementation maintainer (code + negative-test inspection). **Required independent reviewer for Gate 1:** security reviewer nominated by project maintainer; no approval signature received.
**Assets:** P-256 device private key, durable Windows DPAPI identity, pinned peer SPKI fingerprints, ephemeral messages/drafts/ACK/read receipts, pairing confirmation decisions. Trust boundary is **network socket → bounded untrusted preface → JSSE TLS quarantined candidate → verified pin/SAS + two-sided READY → in-RAM application**. Swing Demo is a separate, untrusted/mock-only mode.

## Attacks, severity and explicit controls

| Threat / attacker capability | Risk | Boundary / mitigation | Existing test or open attack test | Stop condition |
| --- | --- | --- | --- | --- |
| LAN MITM or relay rewriting HELLO/ACCEPT and nonces | CRITICAL | Unverified metadata only before TLS; user must compare 64-bit SAS independently; PAIR_CONFIRM transcript binds both nonces and TLS peer public keys | `SecureChatCoordinatorTest` first-pair; deterministic `FingerprintVerifierTest`; **active MITM proxy test OPEN** | Any CHAT_TEXT before bilateral confirmation |
| Bogus self-signed certificate or invalid issuer/critical X509 extension | HIGH | Validate self-issued P-256, digitalSignature, client+server EKU, time, signature, supported critical extensions; **a valid candidate is not authorization** | `DeviceCertificateValidatorTest`, including two test-first RED→GREEN fixes | Accept unsupported critical extension or unrelated issuer |
| UUID spoof, duplicate name, changed SPKI or pin replacement | CRITICAL | Exact expected UUID in X509 subject; pinned SPKI constant-time compare; no auto-update pin; name never authenticates | `TlsContextFactoryTest`, `SecureChatCoordinatorTest.changedKeyForPreviouslyPinnedDeviceIsRejected`; duplicate-ID collision test OPEN | Changed pin becomes trusted silently |
| SAS mismatch, coerced/one-sided human approval, malicious remote PAIR_CONFIRM | CRITICAL | Explicit human comparison; local approval on both devices; protected transcriptHash, request/session and PAIR_READY required | `PairingStateMachineTest` + local TLS loopback; missing/lost READY and force-wrong-code tests OPEN | One-sided or mismatched pairing reaches CHAT-ready |
| Version downgrade or v1 raw plaintext chat | CRITICAL | No CHAT_TEXT on v1, no fallback, v2 protected-only; separate listener lifecycles today | `M1TypeWhitelistTest`, `ChatV2BootstrapCodecTest`, `TlsChatSessionSecurityTest`; **shared-port dispatcher test OPEN** | Any private text sent via v1/unencrypted stream |
| Windows DPAPI corruption, ACL bypass, partial credential/trust write | HIGH | CurrentUser DPAPI; temp file restricted to owner ACL; atomic rename only; corrupt file fails closed | `DeviceIdentityStoreTest`, `PinnedPeerStoreTest`; second Windows account/ACL audit OPEN | Decryption failure silently resets identity/pins or writes plaintext |
| Forged, duplicate, delayed ACK/read, wrong conversation or stale session | HIGH | TLS verified peer + exact session, conversation, original messageId; READ only from actual foreground viewport event | `LiveChatServiceTest`, `MessageAttemptStateTest`, `LiveMessageVisibilityTest`; cross-session/replay suite OPEN | READ inferred from send/ACK or wrong peer |
| Message replay, stale session, clock skew | MEDIUM | Fresh request/nonce; sessionId, bounded per-session dedup; UTC timestamps display-only | Conversation vector + message-state tests; reconnect replay stress OPEN | Replay displays duplicate/incorrect identity or timestamp treated as trust |
| Slowloris, pending socket/worker flood, queue/frame memory exhaustion | HIGH | 16KiB preface, 64KiB protected frames, 4 pending at listener; timeouts and async workers | `ResourceLimitsTest`; **M2 total 8MiB, 16 write slots/2MiB, outbound attempts cap not fully enforced — OPEN** | Unbounded memory or GUI EDT hangs |
| Caller invokes `markViewed` without actual visible row | HIGH | Trusted FE-only call boundary; `WindowsForegroundGuard`, visibility ≥250ms; receipt still says only “displayed on peer device” | `LiveMessageVisibilityTest` and local two-GUI evidence; negative mock-caller check OPEN | Preview/background notification emits real READ |
| Malicious peer deliberately lies about read state | MEDIUM / inherent | Authenticated application observation **cannot prove human attention** | UX copy, not cryptographic proof | Claim a human definitely read message |
| Leaking secrets/chat to logs, demos or on-disk persistence | HIGH | No message/secret logging, chat/draft/attempt RAM only, separate Demo service | source inspection and `SecurityBoundaryTest`; filesystem/process restart privacy test OPEN | Raw private text/key or SAS in ordinary logs/Git |
| Transport disconnect/failure + automatic resend | MEDIUM | Manual reconnect and manual resend only, UNCERTAIN on ambiguous write | `LiveChatServiceTest` and UI smoke; ack-loss/manual-retry tests OPEN | Unannounced automatic transmission, false DELIVERED |

## Abuse-test protocol and review requirements

- **Negative gate:** capture proof that a rejected/invalid peer sends **zero application CHAT_TEXT bytes**. A listener Accept is a transport consent, never key trust. A remembered pin does not auto-accept the next incoming call.
- **SAS gate:** each person compares codes on a separate trusted channel; two independent confirmations must produce authenticated PAIR_READY before sending.
- **Data gate:** run malformed UTF-8, duplicate JSON keys, oversized 16KiB/64KiB frames, wrong identity/conversation/session, revoked-key, corrupt DPAPI and half-open sockets. Reject or close rather than truncate or reflect untrusted inputs.
- **UI gate:** validate 250ms foreground viewport and background/no-read on real Windows, distinguish “Đã nhận” and “Đã xem” from transport write, preserve UTF-8 emoji.
- **Release stop:** M1 two physical Windows LAN hosts / no Internet gate remains NOT RUN, M2 2-PC security/messaging gate remains NOT RUN, independent security source review is not signed. Any CRITICAL failure blocks M2 release even if Java tests pass.

**Review record:** prepared from repository source/test review; no outside assessor approval. `T004` documents design threats; `T083`/Constitution Gate 1 remain explicitly **BLOCKED** pending independent review and physical dual-PC evidence.
