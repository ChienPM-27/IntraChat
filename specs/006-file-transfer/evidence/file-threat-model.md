# File 006 security threat model and negative-test ownership

**Date:** 2026-11-21 · **Task:** T003 · **Status:** **Documented threat→mitigation→test mapping, NOT external approval.** [Plan](../plan.md) · [FILE_V1 draft](../contracts/file-v1.md) · [Blockers](execution-blockers.md).

| Adversary / fault | Boundary and risk | Required mitigation + independent gate | Task/test IDs |
| --- | --- | --- | --- |
| Clear pre-TLS feature stripping | M1/M2 bootstrap untrusted → File capability | Bind exact raw offer transcript/nonces/deviceId/SPKIs in protected post-`PAIR_READY` confirmation; no downgrade or metadata before both confirm. **Independent File protocol review T004** | T033–T036, T049–T050, T101–T102 |
| Pinned outsider C guesses A↔B transferId | Authenticated TLS C ≠ authorized conversation A↔B | Exact two independently verified deviceId/SPKI current session and scope; zero offer name/count/size disclosure | T013–T014, T041–T042, T099–T100, AC-F |
| Fake app/File receipts | TLS peer sends invalid or stale COMMIT_OK | Authenticated current run/transfer/count/SHA256, receiver **durably** finalized before declaring complete, never M2 READ | T045–T048, T097–T098, AC-C/H/L |
| 256MiB payload OOM/EDT | Network frame/data → queue/heap/UI | 65,536 frame max, bounded binary buffers, same 4 sessions/16 frames/2MiB per-peer queue, strict backpressure and off-EDT work | T037–T040, T081–T088, AC-K |
| Malicious remote filename | Untrusted metadata → Windows Downloads filesystem | User-approved Downloads root **CL-06-03 B**, per-file Accept **A**, path/ADS/UNC/drive/device-name validation, reparse/junction/TOCTOU-safe real handle | T051–T056, T067–T068, AC-D/G |
| Target collision/TOCTOU | Two processes/file races → destructive overwrite | Atomic **no-clobber** commit/owner ACL/verified root, fail closed if unproven; external Windows FS review **T005** | T057–T064, AC-G/L |
| Crash/fault orphan | Receiver staging .part → user files | Only recognize app-owned staging in authorized directory; delete on fail, guarded orphan sweep next start, NEVER generic `*.part` sweep or automatic resume | T065–T070, AC-C/L |
| Source changes after hash | Source file → emitted bytes different from offer | Prehash, emitted-stream digest and identity/mtime/size after stream, receiver recomputes; fail/cancel if changed, no source snapshot | T073–T076, AC-H |
| Fake Group author B via owner A hop | Owner TLS identity ≠ B original signer | Independently verify B signature plus current owner/epoch, durable JOIN/REMOVE; 004 signed runtime first (**BLOCKED**) | T123–T136, AC-M |
| Transfer state leaks or auto-opens | Runtime offer/path → persistent DB/OS shell | RAM-only transfer metadata, only user-approved final file persists; warnings, no automatic preview/run/READ or History 005 binary entry | T031–T032, T119–T122, AC-N |
| Single-host E2E mistaken for release | Test harness → physical LAN security claim | ≥2 different physical Win11 PCs without Internet, manual inbound Accept/SAS and independent security/privacy signoff | T143–T144, AC-O/P |

**Owner boundaries:** protocol/security reviewer T004; Windows filesystem reviewer T005; M2 transport/perf owner T007/T008; 004 owner signed-runtime reviewer T006; privacy/security independence T144. The assistant's own threat model is **not** that reviewer signoff. No raw remote-supplied pathname, private full source path, file bytes, SAS or device keys go into logs/Git.

**Risk gate:** domain identity/metadata policies are *necessary predicates only*. They **do not** create a TLS capability, origin cryptographic signature, Win11 no-overwrite guarantee or actual Group current-owner authority.
