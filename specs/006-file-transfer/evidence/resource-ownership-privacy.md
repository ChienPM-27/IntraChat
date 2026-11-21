# Phase 06 source-level resource review, file ownership and privacy register

**Date:** 2026-11-21 · **Tasks:** T007, T008, T010. **Status:** design/source inventory reviewed locally; no independent resource security or Win11 FS approval. [Baseline](phase06-baseline.md) · [Threat model](file-threat-model.md) · [Plan](../plan.md).

## Existing source limits and proposed measurement contract (T007)

| Real source | Observed limit | File 006 implication |
| --- | --- | --- |
| `src/main/java/vn/intrachat/transport/NetworkConfig.java` | `MAX_ACTIVE_SESSIONS=4` | No additional File socket, never exceed 4 shared sessions |
| `NetworkConfig.MAX_OUTBOUND_FRAMES` | **16/peer** | File bulk must yield to chat/Group control and not reserve all 16 |
| `NetworkConfig.MAX_QUEUED_BYTES` | **2 × 1024 × 1024 bytes/peer** | Test slow receiver/backpressure and bounded worker queue |
| M2 `ChatV2Codec.LIMIT` and existing `FrameCodec` | **65,536 encoded bytes** | File profile overhead **inside** same limit; ≤32KiB proposed chunk is still UNFROZEN |
| `NetworkConfig.APPROVAL_TIMEOUT_MS` | **30,000 milliseconds** for inbound connection | Distinct from per-file offer timeout; don't conflate socket Accept with file consent |
| `NetworkConfig.ACTIVE_READ_TIMEOUT_MS` | **15,000 milliseconds** | File slower flow cannot assume infinite idle wait; stage-specific timeout plan needed |
| File user CL-06-01 A | **256 MiB inclusive**, zero permitted | Hard policy not a performance benchmark; no whole file memory allocation |

**Proposed experiment (NOT RUN):** Send synthetic 0/1/256MiB files through real verified M2 A↔B on two physical Win11 PCs, measure peak heap, per-peer queue, active session count, encoded frame max, request/cancel latency and Swing EDT frame delays. Repeat with chat in progress and a receiver throttled to slow disk/network; verify no starvation or OOM. Propose one stream/peer, raw chunks ≤32KiB and finite stage-specific Offer/idle/overall timeout only as **technical candidates**. A successful pure `FileDeadlinePolicyTest`/source prehash does **not** measure these; require T004/T005/T011 and reviewer signoff before enabling File socket/fs writes.

**Go/no-go:** File cannot ship if any encoded frame exceeds 65,536 bytes, if peer frames/queue or shared session budget unbounded, if EDT blocks on I/O, or if protected handshake/capability is absent. Exact upper concurrency and timeout numbers still require test/review T139 and T144; source inspection here is not resource performance PASS.

## Shared-file owner matrix (T008)

| Source/feature | Planned single editing owner | Independent regression |
| --- | --- | --- |
| `TlsChatSession.java` / `SessionRegistry.java` | M2 verified-session/transport owner (File profile mux only after T004/T011) | Full M2 v2 Chat + pairing tests, File strict profile isolation |
| `ChatV2BootstrapCodec.java` / `ChatV2Codec.java` / `FrameCodec.java` | Protocol owner, no File allowed inside CHAT_TEXT or v1 plaintext | v1/v2 malformed/handshake backward compatibility |
| `SecureChatCoordinator.java` | Application coordinator owner | No false APP_ACK/READ, no third-party metadata leak |
| `MainChatFrame.java` / `LiveChatAdapter.java` | Live UI owner; keep dirty `003` worktree edits untouched | Swing EDT/permission tests, Demo separation |
| `pom.xml` | Build/dependency owner; no unreviewed native Win32 package | Maven Java21 full tests + dependency license/security review |
| `domain/file/` new classes | Pure-domain File owner, parallel safe | New File JUnit + M2/004/005 regression |
| `application/file/` prehash helper | Read-only File application owner | Source mutation/read-only tests; no production File socket |
| `storage/file/` **future** | Dedicated independent Windows filesystem-reviewed owner T005 | Win11 ACL/reparse/junction/no-clobber/crash tests only after reviewer |
| `application/group/file/` **future** | Verified Group 004 relay/signature owner | Signed B original proof/owner epoch/JOIN-REMOVE and ≥3-JVM tests |

**Git rule:** do not merge or modify user's dirty `003-p2p-messaging` checkout. All 006 changes use isolated worktree, explicitly stage only 006 paths and documentation. If M2 shared APIs have outstanding unrelated changes, stop and coordinate an owned merge rather than overwriting.

## Privacy and failure review register (T010)

| Data | Permitted lifetime / disclosure | Forbidden |
| --- | --- | --- |
| File offer: basename, size, sha256 | In RAM, to **exact** approved P2P peer only over verified TLS + protected File capability | Pre-TLS/public discovery, outsider C, logs/History/server |
| Source absolute path and local filesystem identity | User-local process memory while sending; read-only inspected by tests | Network/Git/telemetry or automatic persistent source listing |
| Preauthorized Downloads root (CL-06-03 B) | Local user setting; persist only if independently reviewed protection, otherwise reauthorize per launch | Remote chosen root; persistent File transfer metadata disguised as setting |
| Per-file Accept/Reject | One-time local user decision tied to offer/session; times out | “Pin = autoAccept”, root authorization = autoAccept, third-party impersonation |
| File chunks, SHA-256/ACK | Protected in-flight RAM; only receiver's transient staging file while accepted | Whole-file heap/Base64 JSON, History binary backup, fabricated chat receipt |
| Completed user file | Only verified, durable new no-overwrite file in user-approved Downloads | Auto-run/unzip/preview or overwrite/remote wipe |
| Cancel/fault partial | Close/delete app-owned staging; guarded cleanup after crash | Arbitrary `*.part` deletion or silent resumable upload |
| Security evidence | Synthetic hashes, IDs/counts/commands/exits with secrets redacted | Private file bytes, Windows user home/full paths, real SAS, cert/key/DPAPI data |

**Independent review status:** T004 canonical transcript/signing, T005 Win11 FS/ACL/no-clobber and T144 security/privacy signoff are **NOT EVIDENCED**. This is a design register, not an attestation that current networking/filesystem is secure for File 006.
