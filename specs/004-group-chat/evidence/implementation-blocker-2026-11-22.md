# Historical pre-approval stop report — 004 Group Chat, 2026-11-22

> **Superseded for development authorization:** Người dùng sau đó nói **“Phê duyệt, tiếp tục thực hiện”**, nên đã thực hiện 12 RED→GREEN tasks Group core, thêm 28 Group tests, full Java21 141/141 PASS và cập nhật [implementation evidence](group-core-implementation-2026-11-22.md). Phần bên dưới là **ảnh chụp blocker tại thời điểm trước phê duyệt**, không còn mô tả trạng thái triển khai mới nhất. Independent reviewer, 2 physical PCs và second Windows user vẫn là blockers cho Gate 1.**

**Instruction:** Implement Spec 004 tasks and stop only when goal reached or a real blocker is identified.
**Disposition:** **STOP AT BLOCKER / M3 NOT VERIFIED**. Implementation cannot lawfully advance past the security Gate 0 prerequisite without an independent review; the user-confirmed six product clarifications are not a security signoff.

## Actual verified progress

- Authored Phase 1 design/evidence: prerequisite audit, six-CL review, threat/feasibility/revocation research, deterministic Group V1 contract/v3 transcript, data model, requirements/Constitution checklists and marked-NOT-RUN quickstart.
- Ran **actual Temurin JDK 21.0.12.1+1** M2 baseline via `scripts/verify-m2.ps1 -Offline`: exit **0**, **113/113** existing JUnit, package/JAR diagnostics PASS. Group JUnit tests: **none**, no Group Live implementation exists.
- Offline Maven dependency tree **exit 0**; cached POM license info and SHA256 hashes for FlatLaf, Jackson, BC 1.87 and JNA 5.19.1 captured in [dependency inventory](dependency-security-review.md).
- Contract-only canonical digest reference fixtures: roster 260 bytes SHA256 `FE5AA540B53CE02A64457F0A95BEAEE6274828EC743ACD3598F310C085DF915D`; grant 210 bytes SHA256 `0C31739C8BD77B2E44B21BF5054FB343116C48449EAA1BAD3D533B78323BA869`; v3 pairing transcript 250 bytes SHA256 `3B4952931FF836E84EA4F21BF167BFC4DB78B8077BC114F931D3632C51388959`. **No Group code/tests were run** to assert implementations agree with these draft vectors.
- Existing source/dirty worktree preserved. **No** package changes, Java source changes, firewall changes, credentials touched, reset/stash or Git commit.

## Precisely identified blockers

| Blocker | Affected tasks | Why current work cannot close it | Required resolution |
| --- | --- | --- | --- |
| **B-G0-1: independent crypto & privacy/security approval** | **T004/T010**, which gate **all T011–T092** | Owner-signed roster and short-lived per-message grant, revocation/in-flight handling, profile v3 pairing transcript and DPAPI group store are new security-sensitive design. Local author cannot be their own independent reviewer; no reviewer name, signed approval, dated finding disposition or current authoritative CVE/SBOM/NOTICE provenance is available. | Assign qualified independent crypto/protocol/security and dependency/license reviewer, check [constitution](../checklists/constitution-check.md), reproduced test vectors, issue/approve remediation. Mark T004/T010 only on real evidence. |
| **B-G0-2: existing v1/v2 dispatcher and new Group capability compatibility** | T010 Gate 0 review, T023–T032 implementation after approval | Existing `ChatV2BootstrapCodec` accepts only v2 singleton capability, `SecureChatCoordinator` is M2-only listener, M2 shared dispatcher T025/T026 still open. Cannot send Group packets via legacy v2 or M1 plaintext and claim backward compatibility. | Approve explicit v3 protocol/capabilities and secure dispatcher design, then perform RED→GREEN legacy interop, no downgrade. |
| **B-LAN: two physical Windows PCs** | T087–T089/T092 and upstream M1/M2 Gate 1 | Only one connected real physical Windows workstation; 3 local processes cannot satisfy two-PC LAN acceptance without Internet. | Authorized second physical Win11 test host, same Private LAN, separately verified identities and human consent. |
| **B-DPAPI: second Windows logon and reviewer** | T035/T085/T090 | No independent authorized second Windows user token for cross-user DPAPI failure test. | Approved secondary test account, no real user credential export, independent security findings. |
| **B-UPSTREAM: M1 and M2 not Verified** | T092 | M1 37/60, M2 34/90. Their Gate 1 and physical/security acceptance pending. | Satisfy respective specs' acceptance and reviewer approvals before 004 Verified. M3 still additionally requires Spec 005 History. |

**Why not force source changes now:** [tasks.md](../tasks.md) explicitly makes **T010 Gate 0 a prerequisite of T011+**. Constitution IV/V/VII forbid pretending design review/security approval or RED→GREEN happened. Starting crypto/group-message code that bypasses this gate risks revealing group content to a stale/revoked peer. **An external independent review is a real execution blocker, not an ordinary code task that can be completed by an additional local test.**

## Remaining work and exact resumption

Once qualified reviewer signs Gate 0, begin T011 RED domain tests and T012 GREEN implementation, then continue phases. Before marking Verified require complete new Group test suite, real TLS 3-peer, >=2 physical Win11 PCs, other Windows user DPAPI, independent security/privacy signoff and M1/M2 Gate 1. If any review requirement fails, fix/approve ADR before writing sensitive code.

**Status of Spec 004:** Phase 1 documentation authoring materially advanced; **Group Live source NOT IMPLEMENTED**, Gate 0 **BLOCKED**, Gate 1 **NOT VERIFIED**. Never confuse baseline JUnit 113/113 with implementation of Group 004.