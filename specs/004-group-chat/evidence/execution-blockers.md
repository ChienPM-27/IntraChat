# Phase 04 blocker register — stopping point after safe pure-domain implementation

**Date:** 2026-11-21 · **Status:** **BLOCKED for secure wire implementation / full Group acceptance, domain unit tests PASS.** Distinguish reviewer/physical dependencies from ordinary unfinished work.

## B1 — Independent security, dependency and cryptographic profile review unavailable (external / release blocking)

- M2 already records an independent dependency/security review as unresolved; current phase T005 requires current license/CVE/provenance checks for Jackson, Bouncy Castle 1.87, JNA-platform 5.19.1 and their transitive deps, plus signoff. Maven cache and 122 tests **do not** constitute independent approval.
- T006 requires frozen exact Group V1 canonical binary signature vectors, including origin/owner/recipient READ/handoff and a separately checked interpretation of the mutual post-TLS Group capability negotiation. Current Group contract describes a proposal, **not reviewed/frozen interoperable production bytes**.
- Without a qualified independent reviewer and final canonical vectors, **do not enable Group messages or claim origin/READ/handoff crypto secure**. The pure Group domain model can be test-run in isolation, but T019–T020 and T023–T036 secure signer/profile tasks **cannot be signed off**; Group deployment prohibited.
- **To resolve:** assign independent authorized crypto/security reviewer; produce canonical signed test vectors and validate against an independent implementation; close findings; approve dependency/SBOM/license and authenticated Group capability transcript.

## B2 — Two physical Windows LAN PCs and independent human pairing unavailable (external / acceptance blocking)

- Connected Windows tools control one accessible host only; actual peer-to-peer first-pair with **two independent Win11 physical machines** without Internet, human inbound consent and out-of-band SAS cannot be run here. An actual third independent Group client process is also required.
- Existing M1/M2 Gate 1 remains open; localhost two-JVM or Java21 JUnit **not equivalent** to physical LAN.
- Blocks T115, T121–T124 and inherited M1/M2 release ACs even if code later passes locally.
- **To resolve:** provide two authorized Win11 x64 LAN hosts, distinct client profiles and human pairing operators; run M1/M2 gates and Group LAN scenario, archive sanitized evidence.

## B3 — Cross-Windows-user DPAPI and independent storage privacy check absent

- DPAPI CurrentUser Group metadata persistence not implemented. A second Windows account/security token is required for cross-user security negative tests; no second account was used in this turn.
- Blocks full CL-04-04 acceptance T040–T048/T116 and security signoff; cannot downgrade persistent-metadata requirement or store Group chat/READ on disk.
- **To resolve:** provide an authorized alternate Windows test account and reviewer, then implement/test protected Group metadata store with ACL/atomic/restart/corrupt/rollback behavior.

## Other technical work still OPEN (not mislabeled as external blockers)

- Group member proposal→owner approve→invitee accept network workflow; voluntary owner handoff; signed B→A→C relay; per-viewer READ; FE Live; actual 5-device Group; fuzz and stress tests remain unimplemented.
- Existing M2 v2 HELLO currently requires exactly `CHAT_TLS13_V2` capability and `ChatV2Codec` has strict type allowlist; Group V1/v3 must be negotiated separately, never tunneled into plaintext v1 or M2 P2P chat.
- Owner group 5-device requires four concurrent shared verified M2 sessions; aggregate RAM/queue budgets remain design targets, not measured.

## Stop decision

Continue with **only** reviewed, isolated pure Java Group domain work; **stop at secure protocol/transport release boundary** until B1 authorizes exact cryptographic/capability implementation and B2/B3 permit required physical/privacy acceptance. Current branch should be reported as **PARTIAL IMPLEMENTATION / NOT VERIFIED**, never Group 004/M3 Verified. No tasks beyond evidence/tests actually run may be checked.
