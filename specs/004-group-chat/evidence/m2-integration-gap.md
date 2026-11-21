# Phase 04 M2 integration gap — T003

**Observed committed source at ff6aa96, 2026-11-21**

| Source | Observed behavior | Gap and required task |
| --- | --- | --- |
| `transport/NetworkConfig.java` | `MAX_ACTIVE_SESSIONS=4`, `MAX_PENDING_APPROVALS=4`, frame queue constants 16/2MiB | Group max 5 **devices** works only as owner local + 4 peer connections; share session registry, do not create additional connection pool (T007/T087/T088) |
| `application/LiveChatService.java` | `MAX_ACTIVE=4`, per-peer RAM cache 500 msgs/2MiB and pending receipt max 128 | Group+P2P aggregate 8MiB is a **plan target**, not verified enforced global limiter; T087/T088 |
| `protocol/ChatV2BootstrapCodec.java` | HELLO requires **exactly one** `CHAT_TLS13_V2` capability | Optional `GROUP_V1` requires explicit negotiated preface change and **protected post-TLS** dual confirm, old-v2 regression (T031–T034) |
| `protocol/ChatV2Codec.java` | Strict `protocolVersion=2`, `destination.kind=PEER`, CHAT/P2P type allowlist | Group `protocolVersion=3` must go through separate strict Group codec, not v2 parser and not M1 raw plaintext (T027–T036) |
| `transport/TlsChatSession.java` | `canChat()` gates M2 READY and protected chat; no Group capability path | Extend typed verified transport dispatch only after caps and authenticated peer binding |
| `security/ProtectedLocalFile.java` | DPAPI/Windows ACL/atomic temp write; no Group metadata file | Dedicated GroupMetadataStore, load-time owner/ACL/reparse validation and wrong-user tests required (T037–T048) |
| `application/SecureChatCoordinator.java` | Pairing state machine/READY per peer; shared v1/v2 dispatcher not release-ready | First-pair and per-inbound consent MUST remain, M1/M2 release blockers persist |
| `listener/LiveChatAdapter.java`, `ui/frames/MainChatFrame.java` | M2 Live frontend and separate Demo; Group service/real read not present | Group Live view port, per-recipient receipts and thread confinement (T101–T110) |

**Blockers:** only one accessible PC; two physical Win11 LAN endpoints cannot be tested here. Dependency/CVE/peer cryptographic review and cross-Windows-user DPAPI test require independent reviewer/other logon. No Group code was presumed operational by this audit.
