# Constitution Check — 004 Group Chat — Gate 0 / Gate 1

**Prepared:** 2026-11-22 · **Basis:** [Constitution 1.0.0](../../../.specify/memory/constitution.md), [Spec 004](../spec.md), [Plan 004](../plan.md), [tasks](../tasks.md), [research](../research.md), [contract](../contracts/group-v1.md) and [source audit](../evidence/prerequisite-status.md).

**Gate 0 disposition: NOT INDEPENDENTLY SIGNED OFF (T004/T010 open).** Người dùng đã xác nhận cho tiếp tục code và test **prototype isolated** ngày 22/11/2026; phép triển khai hạn chế này **không** thay thế qualified reviewer hoặc cho phép mở Group Live network. Đã có [28 Group JUnit/core code evidence](../evidence/group-core-implementation-2026-11-22.md); Group transport/UI vẫn disabled. Gate 1 NOT VERIFIED.

## 1. Principle-by-principle design assessment

| Constitution principle | Evidence/design assessment | Status before independent Gate 0 | Gate 1 proof needed |
| --- | --- | --- | --- |
| **I — LAN-first and explicit topology** | Direct verified TLS fanout, maximum five peers, no Internet or content relay. A single owner signs control-plane roster/grants and must be reachable for new sends. Manual IP/port and inbound consent maintained. | DESIGN CONSISTENT; not tested | ≥3 independent peers, ≥2 actual Windows 11 physical hosts without public Internet, partition/owner-offline cases |
| **II — versioned message and identity** | Strict v1 diagnostics/v2 P2P separated; proposed CHAT_V3_GROUP capability negotiation and GROUP_V1 types with signed owner roster/grant and authenticated sender binding. See contract. | **CONDITIONAL**: actual v3 pairing/capability transcript test vectors and legacy interop are not signed off | Positive/negative protocol tests, no downgrade, sender/time/conversation identity |
| **III — bounded nonblocking reliable** | Existing 4 remote session cap; planned group 2–5, quotas for RAM, queue, invitations, ACK and worker timeout; Swing EDT work off-thread. | **CONDITIONAL**: target limits unmeasured, Group service absent | 5-member mesh, flood/malformed/half-open, five GUI cycles and p95 EDT measurements |
| **IV — security and privacy (MUST)** | Mutual TLS verified/pinned, owner-signed canonical roster and per-message grant, scoped DPAPI metadata, RAM-only chat, no M1 plaintext Group. Clear cross-user/rollback/revocation limitations in threat model. | **BLOCKED**: independent crypto/canonical/signature/partition/DPAPI/SBOM/CVE/license review and second-user DPAPI test absent | Independent signed review with real malformed/replay/MITM/partition/disk/log negative evidence |
| **V — automated test-backed delivery (MUST)** | 92 RED→GREEN tasks plan; baseline M2 regression 113/113 Java21 PASS (not Group). | **PLANNED, Group NOT RUN** | Actual Group focused tests and full Java21 regression, three-peer plus real multi-host acceptance, command/exit/junit/evidence |
| **VI — user-visible truth** | Design per-recipient ACK/READ (foreground viewport), owner PAUSED, explicit stale roster, separate Demo vs Live. | DESIGN CONSISTENT; not implemented | Real UI focus/keyboard/accessibility/read-vs-delivered/partial fail tests; screenshots/observations |
| **VII — spec-driven honest delivery** | Confirmed CL-04-01…06; plan with ADRs, contract/data-model/research, 92-task tracker and independent gates. | DOCUMENTED; implementation not started | Approved contracts, checked task evidence, M1/M2 gate, release docs and reviewer decision |

## 2. Gate 0 blockers requiring named independent reviewer

- **G0-B1 (security review unavailable):** Reviewer for owner ECDSA canonical bytes, self-claimed IDs vs pinned owner SPKI, grant expiration, signature/nonce replay and test vectors **not assigned**. Local author cannot self-approve. A wrong signature scheme can leak Group messages to nonmembers.
- **G0-B2 (profile safety):** Strict old v2 bootstrap accepts only exactly CHAT_TLS13_V2; the proposed CHAT_V3_GROUP capability transcript, preface negotiation, downgrade prevention, v1/v2 dispatch and pairing binding need byte-exact review, test vectors and interop validation before code.
- **G0-B3 (membership confidentiality):** Owner-signed short-lived ticket can precede a removal; in-flight data may arrive after commit. Under partitions, old metadata may appear valid. Reviewer must accept/remediate realistic revocation semantics without violating confirmed direct P2P fanout and one-owner requirements.
- **G0-B4 (dependency/provenance and storage):** Local Maven tree and cached license/hash inventory gathered; independent CVE/SBOM/license/NOTICE/signature/provenance review not finished; DPAPI CurrentUser cannot be cross-user-tested without separate authorized Windows logon.
- **G0-B5 (resource/availability):** Proposal that each new message requires owner control round-trip makes Group unavailable while owner offline. This trade-off is documented and does not change user-confirmed product clarifications, but latency/failure budgets need security/UX review.

**Required Gate 0 resolution:** A named, qualified reviewer must reproduce the security/protocol/storage findings, attach dated approval or actionable remediation for each MUST concern, and formally change Gate 0 status. **User confirmation of CLs is NOT independent security approval.** Until then T004 and T010 remain unchecked and Group security source tasks T011+ cannot enter GREEN under task dependency policy.

## 3. Gate 1: unverified criteria

- [ ] M1 Gate 1 verified (currently M1 37/60 In Progress).
- [ ] M2 Gate 1 verified (currently M2 34/90 In Progress).
- [ ] GROUP_V1 code/tests actually exist and all 004 essential RED→GREEN DoDs satisfied.
- [ ] Per-recipient ACK/READ proven with real foreground group display, 0 false READ.
- [ ] Windows DPAPI metadata stays, content/draft/receipts RAM-only; separate Windows user deny verified.
- [ ] Group 2–5 bound, flood/cleanup/EDT/5-cycle tests pass with measurements.
- [ ] ≥2 Windows 11 **physical** hosts, ≥3 independent verified peer identities, LAN no public Internet.
- [ ] Independent security/dependency/privacy review, license/NOTICE/CVE triage and code review signed.
- [ ] All AC-04-A…L have real evidence, no unmitigated Constitution MUST failures.
- [ ] Current product docs/ROADMAP updated to actual tested scope; Group 004 and History 005 correctly distinguished before claiming M3.

**Reviewer:** unassigned.
**Review date/signature:** not available.
**Gate 0:** BLOCKED/NOT APPROVED.
**Gate 1:** BLOCKED/NOT VERIFIED.
**Evidence:** [M2 blockers](../../003-p2p-messaging/evidence/execution-blockers-2026-11-22.md), [local dependency inventory](../evidence/dependency-security-review.md).
