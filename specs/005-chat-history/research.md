# Technical Research / ADR candidates — History 005

**Status:** PROPOSED / UNVERIFIED; **10 CL-05 PENDING** · **Date:** 2026-11-21
**Sources:** [spec](spec.md) · [CL choices](clarifications.md) · [plan](plan.md) · [004 plan](../004-group-chat/plan.md) · [Constitution](../../.specify/memory/constitution.md).

## ADR-05-01 — Storage engine and encryption

**Decision candidate:** encrypted SQLite with transactional local schema on Windows user profile. A new random independent database key protected by CurrentUser DPAPI; ACL owner-only. Native encrypted SQLite/SQLCipher-compatible JDBC provider only **after dependency license/CVE/integrity review and Windows x64 demonstration**. Existing `ProtectedLocalFile` only protects a small credential blob and cannot encrypt a normal SQLite database by itself.

**Alternatives:** plain JDBC SQLite **REJECTED** as content-at-rest exposure; per-event AES-GCM encrypted append-only pages **candidate B**, but would require custom indexing, compaction, crash safety and audit; unrestricted file JSON **REJECTED** due leakage/bounds.

**Open verification:** legal/licensing compatibility, signed native library build, KDF/cipher choice, full-page encryption and encrypted WAL/temp/shm, wrong-user check, safe upgrades, ability to never create plaintext side-files. No actual vendor artifact selected/installed in this planning step. If unavailable or failing any review, block persistence rather than fallback to plaintext.

## ADR-05-02 — Local consent & retention

**Candidate A:** user opt-in before first persistent content write, opt-out stops capture; 90d/500MiB quota is a **suggestion**, not a confirmed requirement. User sees disk/per-device sharing and delete impacts. No automatic export/backup; no hidden persistence of mock/old M2 RAM.

**Alternative:** default-on, keep-until-deleted, or account-wide sync changes privacy/security UX and must be explicitly selected.

## ADR-05-03 — Event representation and P2P provenance

**Candidate:** immutable origin-bound sealed events, each with stable eventId and source messageId, signer key, scope, UTF-8 content hash, UTC timestamp and signed canonical bytes. Existing M2 TLS authenticates a **hop** but current M2 CHAT_V2 does not automatically supply a durable detached signature. Therefore new History events may require a separate protected signed source seal correlated with the actual Live message; **legacy unsigned recipient copies cannot be re-labelled with another author's signature**.

For direct P2P, each verified device can offer **its own authored and eligible signed events**, not other-party data merely because it has a copy. Offline drafts and never-delivered attempted texts are not History sync messages. Group may offer signed original Group events only after 004 integration.

## ADR-05-04 — History application protocol isolation

**Candidate:** `HISTORY_V1` optional negotiated profile inside verified TLS M2 after PAIR_READY; strict separate app protocol `protocolVersion=4, historyProtocolVersion=1` and codec. Existing M2 preface currently requires exactly `CHAT_TLS13_V2`; adding an optional token requires v2-only regression and **protected confirmation of both raw offers** to detect MITM stripping. Never broaden M2 ChatV2Codec allowlist or send History on v1 diagnostics.

**Alternative:** new port/socket **REJECTED** due manual consent/shared M2 cap and broadened attack surface.

## ADR-05-05 — Two-party anti-entropy

**Candidate:** authorization first, then bounded stable-ID/hash manifest and HAVE/NEED, signed DELTA, transaction/apply ACK, repeat to convergence; no max timestamp heuristic. Manual Sync after user initiated M2 reconnect. Re-run scoped summary after disconnect instead of trusting stale checkpoint.

**Alternatives:** last-timestamp-only algorithm **REJECTED** (clock skew/deleted gaps); auto sync after verified session is a user-choice B that changes consent.

## ADR-05-06 — Group ACL and owner handoff

**Candidate:** Only 004 current signed owner serves a Group History request. Each event must meet member JOIN commitment time window, original signer proof and owner revision/sequence; no pre-JOIN backfill. On durable REMOVE, forbid new delta, recheck before each frame. Handoff dual signed epoch+activation proof; old owner cannot serve.

**Alternatives:** eligible member-to-member replication requires independent security ACL and owner-offline authority redesign; not silently enabled.

## ADR-05-07 — Deletion, tombstones and conflict

**Candidate:** local-only delete with durable per-event tombstones / conversation generation cutoff. Same eventId + same signer/digest is idempotent; different signer/hash quarantined. No last-write-wins based on source timestamp, no guarantee remote wipe. Local tombstone garbage collection must preserve a lasting deletion fence or block reimport under quota.

**Alternative:** signed remote deletion or mutable editing needs new event types/protocol, consent, causal merge/retention policy and tests.

## ADR-05-08 — Receipt and Live UI semantics

**Candidate:** no persisted Group/P2P READ or delivery receipts by default; local SAVED, SYNC_PENDING, PARTIAL and SYNCED are **history-copy states**. M2/004 live view-based READ exists only when an actual authenticated viewer receipt is received; loading old history/scrolling offline cannot produce receipt events. Demo remains isolated.

## ADR-05-09 — Availability, limits and external prerequisites

- Manual endpoints and inbound consent every time; offline peer provides no instantaneous sync.
- Existing M2 shared 4 active sessions, owner+4 Group cap; no extra History pool.
- Framing ≤65,536 bytes; target batch ≤20 events/48KiB, 50 events/page, additional History RAM ≤4MiB and 90d/500MiB candidate storage. All unmeasured.
- M1/M2 have no independent physical Win11 LAN acceptance/security signoff; 004 currently pure domain only. Group sync cannot be implemented/tested end-to-end yet.
- Alternative device identity linking, export, remote wipe and Group member mesh cannot be “solved” by silently changing the spec.

**Research conclusion:** A candidate plan can be drafted now without committing to a specific native dependency or claiming an implemented History feature. Before secure implementation, resolve user CLs and obtain independently reviewed exact wire/signature and encrypted storage/ACL design.
