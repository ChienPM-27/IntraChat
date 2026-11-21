# Research and Architecture Decision Records — Phase 06 File Transfer

**Feature:** `006-file-transfer` · **Status:** 10/10 product CL confirmed (9 A, 06-03 B); **technical proposals only**, no independently approved File security/wire/Windows implementation. [Plan](plan.md) · [Spec](spec.md) · [CL log](clarifications.md).

## ADR-06-01 — FILE_V1 as an authenticated profile on existing M2 TLS

**Decision from user:** CL-06-08 A, not a new socket. **Proposed design:** optional data-free untrusted FILE_V1 preface token + **protected post-PAIR_READY mutual capability transcript** + strict FILE_V1 codec/profile. Candidate application version 5 distinct from M1 v1, M2 v2, Group v3, History v4; **not frozen**. Transcript must bind requestId, peer identity/SPKI, ordered preface raw offer sets, both nonces and active M2 session. Existing M2 v2-only peers remain able to chat; no FILE if not both confirmed. **Gate:** independent canonical vector/security review and source codec compatibility tests **OPEN**.

## ADR-06-02 — Per-file Accept + preauthorized Downloads

**User:** CL-06-02 A and **CL-06-03 B**, distinct approvals. Local user chooses/authorizes and can revoke/change Downloads root in settings; each FILE_OFFER still individually Accept/Reject, timeout rejects. If root unapproved, revoked or unsafe, receiver may not send FILE_ACCEPT. Selected destination root is **never** sent by peer. Persisting user-only settings (if needed) must use reviewed protected settings or require authorization again each launch; **transfer states remain RAM-only**. **Gate:** Win11 shell known-folder/ACL, reparse points, current user settings handling review **OPEN**.

## ADR-06-03 — 256 MiB cap, zero-byte first-class

**User:** CL-06-01 A. 0..268435456 bytes inclusive; reject 268435457. Empty file SHA-256 constant `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`, still Offer→Accept→END→durable create→COMMIT_OK with no chunks. Existing Demo `model.Attachment` requires positive size and must not be reused as File network validation. **Gate:** 0/1/max/max+1, signed-int overflow, disk quota and binary metadata vectors pending.

## ADR-06-04 — No overwrite and root-preserving filesystem commit

**User:** CL-06-04 A. Choose safe collision-free basename; never `REPLACE_EXISTING`. Proposed same-root private staging + SHA256/size + fsync/close + **Win11 proven atomic no-clobber** create/move. Windows atomic move flags and reparse/junction races must be verified with actual Windows filesystem handles; a Java lexical normalized-prefix check alone is unsafe. If no safe audited API/path behavior, **BLOCKED**, not partial overwrite. Guarded orphan sweeping may delete only app-created partials. **Gate:** independent Win32/JDK21 no-clobber/ACL/reparse/junction/security review and race tests **BLOCKED/OPEN**.

## ADR-06-05 — No resume, no transfer metadata persistence

**User:** CL-06-05 A and CL-06-09 A. Attempt state exists in RAM; explicit retry is new transferId. On cancel/error close streams and delete temp; power loss can strand temp, safe next-start cleanup; no persisted resume index/outbox, no automatic resend or remote wipe. User saved final file remains. History 005 must not automatically ingest File bytes or transfer metadata. **Gate:** crash boundary/orphan attribution tests pending; no assertion of instant deletion on power loss.

## ADR-06-06 — Source mutation fails, end-to-end digest mandatory

**User:** CL-06-06 A. Suggested prehash before Offer then bounded streaming from source with second SHA-256, check file metadata/file identity before END; fail when digest/identity/length changes. Receiver hashes exact byte sequence independently and only sends COMMIT_OK after durable finalization. No source snapshot/temporary plaintext source copy. **Gate:** concurrent same-size mutation/sparse file/locked file tests pending.

## ADR-06-07 — Bounded streaming shares M2 resources

**User:** CL-06-08 A. M2 secure max frame 65,536; existing source 4 sessions, 16 outbound frames and 2MiB bytes queued/peer. Proposed ≤32 KiB binary chunk and 1 transfer/peer are engineering **targets** only. Need one TLS reader and profile mux, queue fairness to text/Group, fixed buffer and off-Swing-EDT hash/disk; terminal counters distinguish sender queued/accepted/verified/committed. **Gate:** exact binary wire bytes, queue test/heap/timing benchmark and independent protocol review **OPEN**.

## ADR-06-08 — Group owner fanout gated by actual 004

**User:** CL-06-07 A. Only signed current Group owner relays per-recipient transfers; original author signature separately verified; JOIN cutoff and REMOVE stop new data issuance/flush queued frames. Owner offline/handoff uncertain PAUSED. No member mesh or universal Group success; each recipient locally accepts. Group 004 baseline 18/124 pure-domain only, no verified owner relay/Group runtime, therefore Group route **BLOCKED**. No speculative signatures derived solely from owner A↔C TLS hop.

## ADR-06-09 — Any binary file allowed, never auto-executed

**User:** CL-06-10 A. Metadata MIME/extensions untrusted; warn about dangerous/unknown formats and manually OS Open only on explicit action after completion. No decompression, player, antivirus trust inference or automatic execution. Multimedia 007 controls later previews/codec/players and needs its own spec/test. **Gate:** type-warning UI spoof/double extension tests and Windows SmartScreen/MOTW review **OPEN**.

## Open independent reviewers and stop criteria

1. **File wire/security** — freeze FILE_V1 app version/frame encoding, exact binary canonicalization/post-TLS capability transcript, replay/downgrade negative golden vectors; review inherited M2 pin/SAS boundary.
2. **Windows filesystem** — user-authorized Downloads root settings, Win11 path and reparse/junction/ADS/ACL, safe no-clobber atomic finalize and crash orphan cleanup, no real user file damage.
3. **Resource/privacy** — memory/queue/EDT/2-PC rates at 256 MiB, no transfer metadata persistence, safe logs/warnings, per-recipient consent.
4. **Group 004** — real signed original member proof/owner epoch/roster/commit/handoff runtime first, then per-recipient Group File tests.

**Not verified:** 27 isolated File domain/read-only Java21 JUnit tests now PASS, but **no real File network/Win11 Downloads writer**, native dependency/Windows/protocol independent review, two-PC File transfer or AC acceptance. ADR proposals remain unapproved for production.
