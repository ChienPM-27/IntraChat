# Research and engineering decisions — 004 Group Chat

**Date:** 2026-11-22 · **State:** Design investigation; **not** security approval or implementation.
**Inputs:** [Spec 004](spec.md), [Plan 004](plan.md), [Task tracker](tasks.md), [Constitution](../../.specify/memory/constitution.md), [M2 code audit](evidence/prerequisite-status.md).

## 1. Trust boundaries and assets (T003)

**Assets:** (1) group text visible to exact committed recipients; (2) verified peer identity and owner public-key binding; (3) membership invite Accept and owner roster history; (4) short-lived per-message grant; (5) locally encrypted group metadata; (6) per-recipient receipt integrity and availability; (7) Windows user private-key/DPAPI store.

**Boundaries:** untrusted TCP preface → mutually authenticated TLS 1.3 + human SAS/Accept → READY peer session → negotiated group-v1 capability → owner-signed roster/grant → sender fanout to each recipient → authenticated application ACK/real foreground READ. Group membership is an **extra authorization gate**, not implied by TLS or seeing the same groupId. Demo is outside Live and cannot grant trust.

**Attacker models:** LAN observer/MITM, unverified host claiming victim deviceId, authenticated but unauthorized outsider, removed/stale group member, malicious previously authorized member, a compromised owner or same-user Windows malware, truncated/rolled-back DPAPI blob, application flood. Claim boundary: pinned TLS protects an uncompromised link, not against malicious authorized recipients copying messages or malware running as the same Windows account.

| Attack / failure | Asset, impact, severity | Proposed control / stop rule | Required test/reviewer |
| --- | --- | --- | --- |
| Plaintext group data on M1 v1 or bad version fallback | Content exposed, **critical** | New explicit profile/capability, no user payload before TLS READY; group not routed to v1 | M1/v2 legacy interop, packet inspection; independent protocol reviewer |
| Forged peer displayName/deviceId or changed SPKI | Recipient spoof, **critical** | Pin authenticated session cert, human Accept/SAS, verify own and remote IDs against TLS, never trust alias | changed-key/fake-id/nonce tests |
| Unknown outsider joins by guessed groupId | Leakage, **critical** | Invite to known verified peer + explicit recipient Accept + signed owner roster commit + per-message ticket | unauthorized outsider D, wrong dest/role |
| Member forges owner roster/grant or flips revision | Unauthorized add/remove, **critical** | Owner SHA256withECDSA signature over canonical domain-separated bytes, verify owner SPKI against pinned trust; monotonic revision/prevHash | fixed vectors, wrong owner/group/hash/revision tests, reviewer |
| Owner offline or network partition leaves stale roster | Post-revoke send, **critical** | New sends require owner-signed short-lived message grant; no owner ⇒ PAUSED; reject old revisions/expired tickets | removal/grant race and signed ticket replay matrix |
| Owner signs grant before removal, packet arrives afterwards | In-flight content may reach recipient, **residual HIGH** | No new grants after removal commit; short ticket life, local reject when newer rev known; **cannot promise remote immediate purge** | partition/time-of-check review; disclose limit |
| Replay of invitation, grant, content or receipt | Duplicate bubble/false delivery, **high** | Per-peer/group/message/revision/session binding, nonce ledger, TTL, monotonic state; bounded dedup, no cross-restart exactly-once claim | duplicate/reorder/cross-session vectors |
| Wrong peer sends READ while row offscreen | False “Đã xem”, **high** | Actual group foreground viewport observer; exact group/message/recipient/session authenticated receipt | UI viewport/background/own-bubble tests |
| Corrupt/rollback/cross-user store | Unauthorized stale restore, **high** | CurrentUser DPAPI + user ACL + atomic replace, signature verify, restart STALE; never authorize solely from disk | corruption/other-account and restore-old-backup cases |
| Flood of group frames/invites/tickets | Hang/OOM/EDT freeze, **high** | Pre-parse frame length cap; bounded workers/socket queues; TTL invites; group/attempt/dedup/global RAM cap | stress/soak/cleanup + p95 EDT |
| Text or SAS leaked in logs/traces | Privacy, **high** | Only safe error code/metadata, do not log chat payload, raw private key, signed ticket or SAS | grep/log/disk inspection and review |
| Modified Demo data used as Live group | False security/social engineering, **medium** | Separate Live adapter and capability gate; no fake mock receipt or member | Demo/Live frontend tests |

**Reviewer:** Independent security reviewer **not assigned**; authoring this threat model is **not approval**. Track unresolved critical findings in evidence and keep Constitution Gate 0 CONDITIONAL until signed review.

## 2. Source feasibility and quotas (T005)

Observed actual code in working tree:

- `NetworkConfig.MAX_ACTIVE_SESSIONS=4`, `MAX_PENDING_APPROVALS=4`, `MAX_OUTBOUND_FRAMES=16`, `MAX_QUEUED_BYTES=2*1024*1024`, connect 5000ms, HELLO 5000ms, approval 30000ms, read 15000ms.
- `LiveChatService.MAX_ACTIVE=4`, `MAX_PENDING=128`, per-P2P peer messages=500 / 2MiB, dedup seen=1024 per session. Worker executor creates virtual thread per task; **does not itself bound task submission**.
- `ChatText` caps 8,000 code points AND 32KiB UTF-8; `ChatV2Codec.LIMIT=65536`, root type fixed. Proposed Group V1 must have its own schema.
- `TlsChatSession.send/receive` synchronizes per-send and checks paired READY but knows no group capabilities; `SecureChatCoordinator` currently has **M2-only listener**, not unified v1/v2 dispatcher. M2 T025/T026 remain open.
- `ProtectedLocalFile` implements native DPAPI CurrentUser and owner ACL atomic replace, but is package-private and is **not** a complete GroupMetadataStore with schema/signatures; `DeviceIdentityStore` holds ECDSA private key.
- `MainChatFrame` is a shared FE, yet `LiveChatAdapter.createGroup` throws UnsupportedOperationException. Group Demo mocks do not send traffic.
- A **five-member group requires up to four distinct verified remotes per device**, fitting M2 active-peer *count* only if those slots are free. Other P2P peers compete for exactly the same four slots. **No measured throughput or resource headroom follows from this count.**

**Design capacity targets (NOT VERIFIED):** 2–5/group; at most 8 active/16 stored groups; up to 4 pending invites/group and 8 total; 65,536B max group frame; 32KiB user text; 500 messages/2MiB per group and 8MiB global user-text RAM; 128 pending recipient attempts global; 1,024 seen IDs/group; 16 queued frames/2MiB per TLS remote socket. Enforce admission **before** enqueuing async work. A 5th external verified socket should reject with explicit capacity message rather than silently opening resources. Benchmark p95 EDT ≤200ms and close/cancel ≤2s are **targets only**. See [plan §8](plan.md).

## 3. Signed owner authorization and monotonic roster (T006 research)

A TLS link authenticates only its immediate peer. A malicious member could claim in JSON to be the owner; therefore authoritative ROSTER and GRANT require owner **ECDSA P-256 signatures** with JCA `SHA256withECDSA` over deterministic canonical bytes. Owner's SPKI SHA-256 pin is bound at group creation, not to nickname or current IP. Use **distinct ASCII domain prefixes**, fixed big-endian primitive encoding and UUID raw 16B, not ordinary map insertion order. Exact byte fields, title NFC validation, sorted members/recipients and signature length are specified in [contracts/group-v1.md](contracts/group-v1.md).

**Monotonic commit:** one owner actor serializes changes; revision 0 is an uncommitted proposal; committed revisions start at 1 and increase by exactly one (reject gaps, duplicates-with-different-hash, overflow). Owner persist signed metadata **before** broadcasting. Receiver requires signature, previousHash continuity and DPAPI atomic persist before acknowledging applied roster. Offline member status STALE, never inferred ACTIVE from a saved roster.

**Grant:** Sender asks owner, through verified link, for a per-message signed ticket containing exact group, revision, sender, message UUID, ordered active recipients, short issue/expiry, random nonce; **never text**. Owner serializes grant issue relative to remove/commit. Recipient checks actual authenticated TLS sender/recipient and local committed roster and grant signature before RAM acceptance; no owner, no ticket ⇒ Group send PAUSED. Direct sender fanout remains the only path for content.

**Race caveat:** If a grant was issued before a remove commit, a message sent in flight **may** reach former member after the commit. ≤10s ticket TTL and fail-closed newer roster limit exposure; receiver on a stale revision cannot prove global freshness without owner contact. For strictly stronger real-time revocation, receiver would need fresh owner authorization on every accept (additional control round trip), or remove would wait for distributed ACK/quorum; **neither stronger guarantee is approved/implemented**. Security reviewer must resolve the exact accept-vs-revoke boundary before Gate 0 signoff. Never promise immediate global revocation under partitions.

**Time/replay:** Remote-supplied wall clock is untrusted; ticket expiry interpreted with bounded local clock skew, using injected clocks in tests. Group grant nonces/message IDs are unique in bounded live ledgers; no durable receipt/grant ledgers → no exact-once guarantee across app restarts. RESTORED metadata always STALE until online owner revalidates.

## 4. Interoperability profile (T007 research)

Strict `ChatV2BootstrapCodec.decodeHello` accepts only `prefaceVersion=2` and capabilities **exactly** [`CHAT_TLS13_V2`]. Thus simply adding a `GROUP_V1` item to that array is **NOT** backward-compatible. `ChatV2Codec` also rejects new group types. `SecureChatCoordinator.startListening` cannot share the same TCP bind with v1 `NetworkCoordinator` today.

**Chosen design:** a new `CHAT_V3_GROUP` connection profile with `prefaceVersion=3` and explicit `GROUP_V1` capability. New version dispatcher chooses v1 diagnostics, strict legacy v2 or new v3 (with equivalent human Accept + TLS1.3 + mutual SAS/pin/READY) **before any user payload**. New GROUP_V1 application frames are distinguishable from P2P `CHAT_V2` and allowed only on a confirmed v3 Group-capable link. Old v2-only peers receive **no unsolicited Group frames**; an explicit user-directed P2P v2 connection can still work. Unknown versions fail closed, not silently retry plaintext.

**Security detail:** Supported capabilities, chosen profile, nonces and local/remote cert SPKI must be bound into the **pairing confirmation transcript**, otherwise active downgrade or cross-profile confusion can occur. New profile needs independent cryptographic review, not a shortcut by modifying the old v2 decoder to accept unknown fields. Byte-level draft in [contract](contracts/group-v1.md); no new listener/codec is implemented in this task.

## 5. Storage lifecycle and recovery

Metadata only lives at proposed `%LOCALAPPDATA%\IntraChat\security\groups.dpapi`: schemaVersion/group ID/owner key+ID/title/last signed committed membership and revision; no chat/draft/ACK/READ/outbox. CurrentUser DPAPI plus Windows owner-only file ACL and atomic move; missing store first run → empty; corrupt/decrypt failure → BLOCKED, no regeneration as trusted. A valid **old** encrypted backup is not detected solely by DPAPI or local high-water stored in the same rollback-able file: load always STALE and require owner online to reauthorize. Owner key loss/change cannot silently transfer ownership.

## 6. Decision record and handoff

| ADR | Decision/alternative | Status |
| --- | --- | --- |
| ADR-04-01/02 | Direct content fanout; owner-only membership authority, no relay or election | **User confirmed** |
| ADR-04-03 | Per-message owner control ticket to handle stale roster; owner offline pauses Group send | **Proposed engineering ADR, review required** |
| ADR-04-04/05/06 | Per-recipient receipts; DPAPI metadata vs RAM chat; max five group members | **User confirmed** |
| ADR-04-07 | Explicit v3 profile with Group V1 after negotiation; old v2 behavior retained | **Design proposed, exact protocol review required** |
| ADR-04-08/09/10 | One Messenger, owner key binding, fail-closed on stale owner | **Plan proposed, review required** |

**Next:** complete strict [group contract](contracts/group-v1.md), [data model](data-model.md), Gate 0 independent review and RED→GREEN tasks. No approval or performance results claimed here.
