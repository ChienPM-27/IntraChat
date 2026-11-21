# Research & Architecture Decision Records — 004 Group Chat

**Date:** 2026-11-21 · **Status:** DESIGN DECISIONS / NOT IMPLEMENTED
**Inputs:** [user-confirmed CL-04-01…08](spec.md#2-clarification-decisions--user-confirmed-2026-11-21-88), [plan](plan.md), [M2 plan](../003-p2p-messaging/plan.md), [M2 chat contract](../003-p2p-messaging/contracts/chat-v2.md), [Constitution](../../.specify/memory/constitution.md).
**Evidence scope:** source inspected from branch `004-group-chat` based on committed `b8a8fd5`. No new Group build/test performed.

## R-01 Topology: owner relay in LAN — **BINDING CL-04-01**

- **Decision:** Every Group has one active owner/coordinator; owner acts as authenticated application-level relay over M2 P2P TLS sessions, maximum owner plus four members.
- **Rejected alternatives:** direct sender full-mesh fan-out (membership snapshots diverge and many concurrent sockets) or auto-election/central public relay (contrary to explicit decisions).
- **Consequence:** owner is online dependency and a plaintext observer at the relay. Group **does not claim end-to-end secrecy from owner**. Use per-origin signatures for sender authenticity and per-recipient signed read/delivery receipts. Owner rate limits/fan-out queue.
- **Security limit:** a malicious owner can **withhold/delay** messages and can see their contents. Signatures prevent forging **an independently pinned member** but not availability attacks or dishonest membership statements by an owner that clients blindly trust.

## R-02 Invitation ACL — **BINDING CL-04-02**

- **Decision:** Member can propose invite; owner reviews and approves; invitee independently opts in; owner commits authoritative revision after signed acceptance. Consent Group distinct from M1 inbound Accept.
- **Rejected:** member adds directly, public group join URL or auto trust by groupId.
- **Consequence:** proposal is never member event. Duplicate/expired proposals, false approvals and replay must not alter roster; invitee sees only necessary Group metadata before consenting.

## R-03 Handoff instead of automatic election — **BINDING CL-04-03**

- **Decision:** A can hand over to authenticated willing B; two-sided signed proof epoch e→e+1. A durably retires before B can become active; B persists commit before relay; other members require A+B proof before owner change.
- **Rejected:** first-online-wins, lowest-UUID election, unconditional timeout takeover or unsafe owner rollback.
- **Tradeoff:** liveness can be lost if A crashes **after durable retirement but before delivering COMMIT**. Fail-closed PAUSED with explicit recovery/retransmission is preferable to two owners. DPAPI user file does not provide hardware anti-rollback; old local backup restoration must force fresh signed owner state and may need manual resolution.
- **Owner leaves:** may transfer then issue member leave under B, or explicitly disband; no unilateral auto successor.

## R-04 Protected metadata, not History — **BINDING CL-04-04**

- **Decision:** user-local `%LOCALAPPDATA%\\IntraChat\\groups.v1.dpapi` stores small metadata: groupId/name/owner SPKI hash/roster SPKI hashes/revisions/epoch/owner proof, tombstones; DPAPI CurrentUser/owner-only ACL/atomic replacement, max size 128KiB.
- **Rejected:** SQL history, plaintext JSON in repo, RAM-only Group identity, cloud sync.
- **Consequences:** app restart loads metadata PAUSED and user reconnects to current owner to sync; **no chats/drafts/receipts/dedup recover**. Owner mutation is not committed until disk persist succeeds. Corrupt/inaccessible store fails closed; do not silently overwrite or regenerate old Group.
- **Risk:** M2 `ProtectedLocalFile` has write-time ACL restriction; plan additionally requires load-time ACL/owner verification and Windows alternate-user negative tests.

## R-05 Five devices and total M2 connection budget — **BINDING CL-04-05**

- **Decision:** owner + 4 members, no extra hidden relay process. One verified link per member reused with M2 shared registry; `NetworkConfig.MAX_ACTIVE_SESSIONS=4` is compatible **only if owner uses all four for its Group**.
- **Rejected:** silently raising peer cap or creating a separate uncapped Group pool. For 5-device Group, owner cannot hold an additional unrelated P2P link while at quota without a reviewed shared capacity design.
- **Open implementation:** M2 stated 8MiB global/queue limits require actual enforcement and concurrency tests before committing performance claims.

## R-06 Origin proof, per-member READ — **BINDING CL-04-06**

- **Problem:** TLS authenticates B→A and A→C, but C cannot infer B from A's claimed `originId`. A malicious A could report C READ without C ever seeing a bubble if receipts are owner-authored.
- **Decision:** each origin B signs Group text with M2 P-256 device key; each recipient C signs ACK and READ with its own device key. Canonical signature includes groupId, ownerEpoch, rosterRevision, runId, originalMessageId, origin/viewerId, content digest and appropriate event correlation. Domain separation per type, strict deterministic encoding, SHA256withECDSA.
- **Trust:** C validates B against a **locally verified pinned B SPKI**, not an owner-supplied replacement. If no B pin, user must verify/pair B's identity (can be done sequentially, no always-active B↔C link needed). A does likewise for C. With up to 5 peers, first-contact cross-member verification may involve **up to 10 unique pairs**; this UX cost is explicit, not elided.
- **Read rule:** actual Live Group foreground viewport bubble visible ≥250ms, receipt signed by viewer, bound to source and original ID; previews/background/owner ACK don't count. READ is monotonic per recipient and no human-eye proof.
- **Rejected:** owner boolean “seen”, immediate READ on network receipt, UI mock read, new group encryption key/MLS without approved scope.

## R-07 Join cutoff and remove race — **BINDING CL-04-07/08**

- **Decision:** no history backfill; joiner starts after owner durable join commit and current signed roster sync. Remove takes effect at owner **durable commit revision**; owner serializes membership changes and new message acceptance.
- **Rejected:** non-atomic in-memory roster replacement followed by async disk commit, optimistic join, replay on reconnect, “remote delete” of already transmitted text.
- **Residual:** if a frame left owner **before** remove commit, recipients may receive it later. Re-check not-yet-sent queues before dispatch and cancel pending removed recipients, but do not claim to revoke network bytes.

## R-08 Transport/version selection: optional Group v1 on M2 TLS

- **Existing reality:** v1 parser strictly diagnostics; current `ChatV2Codec` only accepts `protocolVersion=2`/`destination.kind=PEER` and typed v2 message allowlist. Sending a Group frame straight into it fails.
- **Decision:** optional preface capability plus **mutually confirmed protected transcript** after TLS READY, then separate Group v1 app decoder with `protocolVersion=3` and `groupProtocolVersion=1`. Protected frames share 4-byte length and 65,536-byte cap, but strict isolated schema. If either peer is v2-only/declines capability, keep Group disabled and P2P v2 usable. No silent plaintext fallback/relaxation of v1/v2 allowlists.
- **Risk:** pre-TLS capabilities can be modified by an active MITM; post-TLS mutual confirmation must bind exact offered capabilities and prevent spoofed Group enabling. Older v2 peer might reject unknown optional flag; negotiation compatibility must be tested before implementing extension.

## R-09 Group RAM, ordering, ACK and safety budgets

- **Decision:** one serialized owner acceptance stream with per-run `ownerSequence`. Identity/content immutable signed by origin, recipients selected from committed revision. ACK/READ signed by **actual** recipient, status per immutable recipient snapshot; manual retry only on UNCERTAIN. Dedup at most 1,024 IDs per run or 30min; no exactly-once guarantee past RAM/window.
- **Limits:** 8 Group metadata entries/client; 200 transcript messages or 1MiB text/group; Group + P2P ≤8MiB global, 64 pending owner fan-out messages, M2 queue ≤16 frames/2MiB per peer, 128 pending receipt correlation/peer, 8 invitations/group. All targets require test under real 5-device load.
- **Alternative rejected:** coordinator cumulative “delivered” when only owner ACKed; Group read = all read; offline durable outbox (005).
- **Time:** UTC for display only, owner acceptance sequence for within-run order; no global total order across epochs/sessions.

## Deferred decisions and explicit stop rules

1. **M1/M2 gates:** Physical Windows LAN (two distinct PCs), independent dependency/CVE/crypto/privacy review incomplete — release blocked.
2. **Canonical proof vectors:** Before Java implementation, produce checked deterministic test vectors for all signed entity types and cross-language/Java21 serialization, including Unicode and Base64 DER ECDSA signatures. The Group contract is not a running interoperability profile.
3. **DPAPI metadata availability:** Cross-Windows-user test, ACL load check, atomic-write failure and restore/rollback behavior need actual implementation.
4. **Owner dead after irreversible handoff commit:** No automatic election or unsafe owner reactivation; manual recovery and explicit PAUSED semantics remain even if availability suffers.
5. **Pairwise pins:** Additional human SAS verifications may be required between Group members; do not silently treat owner attestations as independent identity verification.
6. **Non-goals:** Group History sync, file/media, group E2EE independent of owner, Internet relay, durable group outbox, auto reconnection/election.

**Decisions are technical proposals under the confirmed product contract, not claims of functional verification.** Any departure from the eight CL-04 choices needs explicit user clarification.
