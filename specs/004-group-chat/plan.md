# Implementation Plan — Giai đoạn 04: Group Chat trên LAN

**Feature/branch:** `004-group-chat`
**Date:** 2026-11-21
**Status:** **PLANNED — 8/8 CL-04 user-confirmed; chưa triển khai Group, chưa chạy acceptance hoặc security review**
**Spec:** [spec.md](spec.md) · [requirements checklist](checklists/requirements.md)
**Supporting artifacts:** [Research/ADRs](research.md) · [Data model/state machine](data-model.md) · [Group v1 contract](contracts/group-v1.md) · [Test quickstart](quickstart.md)
**Governing:** [Constitution v1.0.0](../../.specify/memory/constitution.md) · [ROADMAP 04](../../ROADMAP.md) · [M2 plan](../003-p2p-messaging/plan.md) · [M2 v2 contract](../003-p2p-messaging/contracts/chat-v2.md)
**Prerequisites:** M0 Verified. M1 physical two-Windows-PC Gate 1 **NOT VERIFIED**; M2 34/90 checked on baseline, 107 committed-source JUnit PASS Java21, but two physical PCs and independent security/privacy review **NOT VERIFIED**. Current file is design, not implementation proof.

## 1. Summary — 8 quyết định binding

Group theo topology **owner relay nội bộ LAN** trên các **verified CHAT_V2 TLS 1.3 sessions** đã qua human inbound consent/pairing. Owner có quyền thay đổi membership, fan-out tối đa **5 thiết bị/nhóm**; thành viên được **đề xuất lời mời**, owner phải **duyệt**, invitee phải **chấp thuận**. Owner có thể **bàn giao chủ động** cho member đồng ý với handoff epoch/commit an toàn; owner offline chưa handoff => Group PAUSED. **Metadata** Group được **mã hóa bằng Windows DPAPI CurrentUser** qua restart; nội dung/draft/receipt/attempt chỉ RAM. Group có **per-recipient DELIVERY và READ thực**, READ chỉ sau view event trong Live foreground/viewport và authenticated signed viewer receipt; peer join muộn chỉ có tin mới; REMOVE có hiệu lực ngay khi **owner durable commit revision**, không thu hồi bytes đã phát.

| Confirmed CL | Architectural consequence |
| --- | --- |
| CL-04-01 owner relay | Một application-level coordinator/group; không có cloud hoặc B↔C active link bắt buộc khi đang chat |
| CL-04-02 member invite proposal | `GROUP_INVITE_PROPOSE` không cấp quyền; owner ký `GROUP_INVITE_OFFER` sau approval; invitee ký accept, owner commit revision |
| CL-04-03 voluntary handoff | `PREPARE→ACCEPT→DURABLE_RETIRE/COMMIT→ACTIVATE` với ownerEpoch tăng + signed continuity proof và fail closed nếu crash |
| CL-04-04 persist metadata only | `GroupMetadataStore` DPAPI/ACL/atomic user-local; không lưu transcript, draft/receipt/dedup; khởi động lại ở PAUSED |
| CL-04-05 5 devices | Owner có 4 verified M2 sessions cho 4 member; giới hạn 4 active sessions từ `NetworkConfig`, không 5 peer links ở owner |
| CL-04-06 per-member READ | Member gửi read receipt ký khóa thiết bị qua owner; sender verify signer/trust + original message + run/session correlation |
| CL-04-07 no backfill | Quyền nhận chỉ từ owner-committed join revision trở đi; không replay tin cũ theo Group/history |
| CL-04-08 remove at commit | Owner serialized durable revision commit; mọi message mới sau commit lấy **roster mới**; in-flight pre-commit có thể đã rời owner |

**Phạm vi:** Java21/Swing/Maven, Group text/emoji, memberships, persistence metadata, handoff, sender/time/state, delivery/read, bảo mật và tests. **Không:** History 005, File/Media 006/007, group E2EE crypto key, cloud/web server, auto-discovery/reconnect/failover, auto-backfill, durable outbox.

## 2. Technical Context — kiểm tra theo repository

| Layer | Hiện có ở baseline commit | Quyết định cho 004 |
| --- | --- | --- |
| Runtime | Windows 11 x64 target, Java21, Maven Wrapper 3.9.16/JUnit5, Swing FlatLaf | Reuse; không đổi UI toolkit/build module |
| Legacy LAN | Manual IPv4:port, user Accept/Reject, `JsonEnvelopeCodec` v1 strict diagnostic | Giữ v1 nguyên, không Group qua M1 |
| Protected P2P | `SecureChatCoordinator`, `TlsChatSession`, `ChatV2Codec`, `ChatV2Message` với TLS 1.3/pinning/SAS | Reuse verified transport/identity/session; thêm **negotiated Group extension**, không bỏ whitelist v2 |
| Secure identity | `DeviceIdentityStore`, `PinnedPeerStore`, `ProtectedLocalFile` dùng Windows DPAPI CurrentUser/ACL | Reuse user-local path/protection; Group metadata **một file/store riêng**. Đảm bảo kiểm tra đổi khóa/revoke |
| Chat domain | `LiveChatService` dùng RAM session, `MessageAttempt`; giới hạn 4 active peer trong `NetworkConfig` | Group service tách biệt, dùng typed verified session port; không nhét Group vào P2P conversation |
| GUI | `MainChatFrame`, `LiveChatAdapter` và FE Demo | Mở Group Live adapter mới và toolbar; Demo mock giữ nguyên |
| Existing v2 codec | `protocolVersion=2`, `destination.kind=PEER`, v2 enum allowlist CHAT_TEXT/ACK/READ/PAIR; text payload strings | Group **không** có cách gửi chỉ bằng JSON mới vào codec v2 cũ; thiết kế extension được negotiate riêng |
| Existing v2 caps/targets | Protected max 65,536 bytes, text ≤8,000 Unicode code points AND 32KiB, 4 active, receipt timeout 30s, visible viewport ≥250ms | Group v1 wire contract nhận giới hạn tương đương; các cap tổng RAM/queue M2 phải được code/test trước gate |

**Baseline accuracy:** M2 plan đặt một số quota 8MiB/16 queue/2MiB làm **targets** nhưng không chứng minh mọi quota đã được thực thi. Trước khi bật 5-device Group, implement/tests phải xác minh backpressure/concurrency và aggregate RAM. Chưa được tuyên bố performance/security đạt chỉ vì giá trị đã ghi trong plan.

### 2.1 Application protocol compatibility — không phá M2

**Chọn profile `GROUP_V1` bên trong phiên TLS CHAT_V2 verified**, không mở port/group socket mới. Trước TLS, HELLO/ACCEPT của v2 có thể mang capability **optional** `GROUP_V1`, không có Group text/roster trong preface. **Quan trọng:** prefaced capability là **untrusted** và có thể bị MITM strip; sau PAIR_READY phải xác nhận lại hai phía bằng **protected Group capabilities transcript** (gồm capability offers từ cả hai phía, sessionId, verified deviceIds, nonces và capability version). Hai phía kiểm tra cùng transcript; không đạt => **Group disabled**, không tự fallback Group sang v1, còn P2P v2 có thể tiếp tục nếu pairing/consent hợp lệ.

Trong TLS secure frame (4-byte length + JSON, max 65,536 bytes), **Group application envelopes dùng `protocolVersion=3` + `groupProtocolVersion=1`**, `destination.kind=PEER` là **đích hop hiện hành**; `groupId` nằm trong Group body/context tách khỏi P2P conversationId. Chỉ decoder/dispatcher đã **Group negotiated + READY** mới nhận type v3; `ChatV2Codec` vẫn strict `protocolVersion=2` và `destination.kind=PEER` cho P2P. Peer v2-only không nhận Group frames (không đánh lừa bằng CHAT_TEXT). v3 chỉ định profile Group, **không** tự tuyên bố toàn bộ CHAT_V3 transport đã được implement; tên wire sẽ được freeze trong contract trước code.

**Đường đi:** preface v2 → human inbound Accept → TLS1.3 mutual device certificates → pin/SAS + both READY → protected Group-capability confirmation → Group event negotiation + current roster revision → Group text/receipts. Không có Group content trước bất kỳ gate nào. Group cap negotiation không thay thế first-pair pin/human approval.

### 2.2 Threat boundaries

1. Attacker trong LAN có thể giả HELLO, displayName, deviceId, groupId, revision, invite hoặc M1 plaintext; **chỉ TLS verified/pinned peer và signed authorized owner/member events** mới được cấp quyền.
2. **Owner relay được tin về membership/routing nhưng không được phép tự mạo danh origin hoặc viewer**. Chữ ký theo thiết bị trên **canonical immutable Group text/ACK/READ**; recipient phải biết **pinned SPKI của từng sender trong roster**. Nếu thiếu pin B trên C, C phải làm quy trình xác minh fingerprint/pairing với B trước khi tin của B được gắn “verified”/được hiển thị; owner-attested unverified key một mình **không** chứng minh được B cho C.
3. Roster/snapshot chỉ được chia sẻ với verified **current members/candidate invitee cần thiết**; owner relay thấy content/roster nên **không có Group E2EE đối với owner**. Key người dùng/metadata DPAPI không chống được người có toàn quyền Windows user.
4. Hand-off crash/rollback/split brain, revoked member retry, lost owner fan-out/ACK, delayed receipt và Win11 DPAPI corrupt/account mismatch là threat được test cụ thể. Không dùng tên/IP/timestamp làm nguồn authority.

## 3. Constitution Check — Gate 0 (design-level, not release approval)

| Principle | Design status | Verification/Gate 1 cần có |
| --- | --- | --- |
| **I LAN-first/topology** | **DESIGN PASS** — owner relay LAN qua manual peer TLS, handoff chỉ user online, không cloud/auto route | Real LAN without Internet, owner disconnect/handoff, đúng 4 links |
| **II Versioned protocol/identity** | **DESIGN PASS** — Group profile v1/v3 envelope gated, group UUID/epoch/revision/origin, signed metadata + UTC | Wrong version/group/sender/epoch, 3 client compatibility, old v2 still safe |
| **III Async/bounded resources** | **DESIGN CONDITIONAL** — serial owner event processor, bounded fan-out/outbox, quota/timeout, no EDT I/O | Queue/memory/threads stress, slow C, parallel JOIN/REMOVE, shutdown |
| **IV Security/privacy** | **DESIGN CONDITIONAL** — M2 TLS pin, sender+viewer signature, DPAPI metadata, encrypted store, manual consent, no plaintext Group | Independent crypto/canonical signing/metadata review; malicious owner/key rollback, ACL, corrupt file |
| **V Verifiable tests** | **PLAN ONLY**, no Group tests executed | 3 independent JVMs, 2 physical Win11 PC prerequisite, RED/GREEN + security/regression evidence |
| **VI Honest UX** | **DESIGN PASS** — per-recipient delivery & read, no fake all-read, Group PAUSED and Demo/Live separation | Real foreground viewport, no background READ, offline/nonrecipient state, FE EDT |
| **VII Spec-driven** | **DESIGN PASS** — 8/8 confirmed CL, separate spec/research/model/contract, release gates documented | Tasks/code/test → independent review → Gate 1; no premature Verified |

**Gate 0 verdict:** **Design documented, security and resource verification OPEN**. Không có ngoại lệ Constitution được tự cấp. M1/M2 blockers vẫn phải giải quyết trước release. Group không implement và AC-04 vẫn NOT RUN.

## 4. Architecture, dependency direction và interfaces

~~~text
Swing MainChatFrame (Live Group screens)     Demo MockChatService (untouched)
             |
      GroupLiveAdapter / GroupViewEventPort — UI EDT dispatch
             |
         GroupService (user commands, roster/chat, per-recipient states)
          /      |           |           \
GroupMembership  GroupRelay  GroupReceipt  GroupMetadataStore
(authorized      (fan-out,    (ACK/READ)    (DPAPI protected
 serial events)  queues)                   non-content only)
          \      |           /
     GroupProtocolCodec + GroupSignatureVerifier + GroupCapabilities
                          |
    VerifiedSessionPort (wrapper around existing M2 TLS sessions)
                          |
        M2 SecureChatCoordinator / TlsChatSession / SessionRegistry
                          |
               manual IPv4:port / Win11 LAN
~~~

**Dependency flow:** FE only calls application async ports, application invokes pure domain/policy + secure protocol/transport + metadata store, persistence does not import Swing. All owner state changes serialized **per group** on worker executor, not on EDT; immutable roster snapshot from committed revision to fan-out. Reuse M2 authenticated session and per-peer transport writer, but **do not expose raw `SSLSocket`** to FE.

| Proposed new file/module — not yet created | Responsibility / test seam |
| --- | --- |
| `vn.intrachat.domain.group.GroupIdentity`, `GroupRoster`, `GroupMember`, `MembershipEvent` | Immutable IDs/epoch/revision/roles, 5-member cap, authorization; unit tests independent transport |
| `vn.intrachat.domain.group.GroupMessage`, `GroupRecipientStatus`, `GroupReadEvent` | Origin signature, bounded text, per-recipient state and reducer without UI |
| `vn.intrachat.application.group.GroupService` | Create/propose/approve/invite/join/leave/remove/handoff/send/markVisible; publish events |
| `vn.intrachat.application.group.GroupOwnerCoordinator` | Serialize membership durability + revision commit and owner fan-out |
| `vn.intrachat.application.group.GroupHandoffService` | Safe PREPARE/ACCEPT/COMMIT/ACTIVATE with epoch proof, timeout and recovery |
| `vn.intrachat.application.group.GroupReceiptRouter` | Verify signed ACK/READ and correlate original message/recipient/session/run |
| `vn.intrachat.protocol.group.GroupV1Codec`, `GroupV1Types`, `GroupCanonicalizer` | Strict profile v3 schema, binary canonical signature bytes, capabilities and error codes |
| `vn.intrachat.security.group.GroupSignatureService` | SHA256withECDSA key from M2 identity; verify pinned public keys and domain separation |
| `vn.intrachat.security.group.GroupMetadataStore` | DPAPI CurrentUser + user-only ACL + atomic encrypted store, journal/commit proof, fail closed |
| `vn.intrachat.transport.GroupVerifiedSessionPort` | Type-safe only-after READY/caps, message framing, bounded nonblocking queue & cleanup |
| `listener.GroupLiveAdapter`, `ui.* Group panels/dialogs` | Render real owner/member/roster/states; event from actual Live viewport; Demo untouched |
| `src/test/java/vn/intrachat/{group,protocol,security,application,...}` | Strict codec/canonical vectors, authorization & transition, crash/fuzz/stress, 3-JVM E2E |

**Suggested APIs (contract-first; exact Java methods TBD):** `GroupService.create(name)`, `proposeInvite(groupId, targetPeerId)`, `approveProposal(proposalId)`, `respondToInvite(inviteId, decision)`, `removeMember(groupId, memberId)`, `initiateHandoff(groupId, successorId)`, `sendText(groupId, text)`, `onVisibleMessage(groupId, messageId, actualViewportEvidence)`; non-blocking return `CompletionStage<Result>` or event stream. `VerifiedSessionPort` exposes `peerId+peerPinnedSpki+sessionId+READY+capabilities`, not a caller-supplied arbitrary identity.

### 4.1 Important M2 integration work before implementation

- Extend **existing v2 bootstrap capability negotiation**, never mutate old v1 diagnostic whitelist to accept Group; legacy v2 peers stay P2P-only. Update `SecureChatCoordinator/TlsChatSession` to expose a **validated Group capability** only after verified in-TLS confirm; dispatch `protocolVersion=3` to dedicated Group codec. Unknown type/version on any path fails closed and never accidentally routes to LiveChatService P2P.
- **Pinned trust and roster keys:** M2 `PinnedPeerStore` is keyed deviceId↔SHA256 SPKI; it may not expose the public key DER for an offline third-party member. Keep an authenticated, user-verified roster of **full public keys** for origin signature verification, or add identity lookup from cert previously paired directly. Never accept a new key solely from owner without separate verification if UI promises the origin is verified. Revoke/changed key blocks Group until re-pair.
- M2 currently has max 4 sessions in `NetworkConfig` and `LiveChatService`; Group must **share** the same session registry, not create a second count of 4 Group sessions on top. Owner with 4 member sockets is at ceiling; display capacity for external P2P until slots freed, not silently evict.
- M2 FE currently maps reduced view statuses; Group must have distinct typed recipient statuses (PENDING, OWNER_ACCEPTED, APP_ACKNOWLEDGED, VIEWED, UNCERTAIN, OFFLINE) and **never** map UNKNOWN to “Delivered”.
- M2 `ProtectedLocalFile` is package-private and currently stores identity and pins, not groups. Reuse protected primitives through reviewed security API without copying plaintext to logs or granting Group code direct key material.

## 5. State machines, owner authority và persistence

### 5.1 Group state (cross restart / connectivity)

~~~text
LOCAL_CREATED → INVITING → ACTIVE (owner online + verified members)
                   |          |
                   v          v
                 PAUSED ← owner offline / consent lost / stale roster
                   |          |
           manual rejoin/      | online owner requests transfer
            resync + proof     v
                   |       TRANSFERRING
                   |          | committed A→B, B activates
                   └──────→ ACTIVE (owner B, epoch +1)
                          or PAUSED (ambiguous/timeout)
ACTIVE/PAUSED → DISBANDED (owner authorized durable commit)
~~~

`GroupMetadataStore` persists Group ID, owner, epoch, membership revision and roster **but not volatile ACTIVE presence**. On restart every restored Group starts **PAUSED/needs owner resync** until actual verified sessions, owner signed current roster/run activation and human-initiated reconnect succeed. No auto rejoin, reconnect or History restore.

Member states: `INVITED` (no access), `ACTIVE` (authorized), `LEFT`, `REMOVED`; optionally `PENDING_OWNER_APPROVAL` is **proposal**, never member state. Roster at most 5 unique verified deviceIds including current owner; duplicate name allowed. Events include authoritative `eventId`, `groupId`, `epoch`, `revision`, `previousEventDigest`, `actorId`, `targetId`, UTC (display/audit only), and signature. Owner serializes and **durably commits** before considering event effective or emitting next fan-out.

### 5.2 Member invite proposal → owner approval → invitee consent → join

1. Existing active member B on authenticated Group session **proposes** D; D is not added and must not be sent roster/content merely because B supplied endpoint. `GROUP_INVITE_PROPOSE` contains proposalId, groupId, target deviceId/key reference and short TTL; no sensitive Group details to unapproved outsider.
2. Owner A validates B is active member in current epoch/revision, D is pinned/eligible and not already member, quota ≤5 and proposal bound to verified session. Owner shows UI **Duyệt/Từ chối** (no automatic approve). Timeout/reject leaves roster unchanged; repeat proposal dedup.
3. Owner approves on its own GUI and sends **signed Group invitation** on verified A↔D TLS. D sees owner authenticated device ID, group name + minimum roster identity/fingerprints and accepts/rejects independently. M1 inbound socket still prompts separately; if D cannot authenticate/pair, invite cannot deliver.
4. D's acceptance signed and correlated with invitationId/ownerId/groupId/epoch/nonce/deadline, owner validates then **atomic metadata store** commits revision r+1 adding D. If store write fails → no join confirmation, no membership access.
5. Owner distributes signed roster delta or snapshot to existing online members and D. **No Group chat to D until D knows and confirms current membership + signed roster + runId**, and required pairwise roster keys are verified; existing members must sync current revision before trusting D's signed text. Offline member cannot halt others indefinitely; after reconnect it must fetch current roster before sending Group.
6. Member **LEAVE** request signed by that member; owner commits removal revision. Owner may **REMOVE** member directly after UI confirmation. Group owner may disband (not auto) with signed durable event.

**Authority model:** member can propose invitations; owner can approve/invite/remove/rename/handoff/disband; invitee can accept/reject; signed proposal does **not** change roster; actor signature is **not sufficient** unless role+epoch/revision authorization also passes.

### 5.3 Atomic REMOVE vs concurrent SEND (CL-04-08)

- Owner has one serialized per-Group command stream. For each accepted Group text, take a **committed roster snapshot** and assign `ownerSequence` + immutable recipient set. Dispatch to these recipients. A REMOVE event is effective only after **durable commit** of revision r+1; **all messages accepted/fan-out after that commit use r+1 and exclude removed member**.
- If a previous message was already queued/sent before durable REMOVE, the application cannot erase network bytes; when practical, re-check eligibility before every **not-yet-started** write to avoid preventable leaks. A removed peer's pending queue must be cleared/cancelled; status for uncertain frames remains honest.
- Owner rejects member SEND signed against **older revision** after removal if sender no longer active, and does not silently rewrite origin's signed content. Member still online but roster outdated must refresh revision before send.
- Receiver validates current owner/epoch/revision and origin signature before display. If receiver is stale, **pause/request snapshot**; do not auto accept unknown sender or older roster to keep UI “working”.

### 5.4 Voluntary handoff with at-most-one active coordinator per normal protocol

**Inputs:** A is active owner at epoch e, target B is **online, active member, independently verified** and explicitly **accepts owner responsibility**; everyone else may be offline. No auto election. The goal is **fail closed under partial commit**, not guaranteed availability.

1. A asks GUI confirmation; **serialize group changes and pause new sends**, enter `TRANSFERRING` (bounded timeout) at epoch e, generate `transferId` and prepare e+1/newOwner B/current rosterDigest/expected revision. B verifies A's signed `HANDOFF_PREPARE` and local current owner state.
2. B explicitly accepts; signs `HANDOFF_ACCEPT` (binding transferId, A/B identities, epoch e→e+1, full roster digest) after storing `PREPARED` in protected local metadata. No acceptance, wrong key or timeout => A **may abort only before irrevocable commit**; abort signed with proof, store safely, then resume A.
3. **Irrevocable boundary:** A writes a **durable owner-retired/transfer-committed record** for epoch e+1 **before** sending its signed `HANDOFF_COMMIT`; from this point A **MUST NOT** accept or fan-out new owner traffic at epoch e. Commit proof includes A signature + B signed acceptance, groupId, old/new owner IDs, epoch, roster digest, transferId, monotonic digest.
4. B validates signatures, expected key/epoch and matching prepared transfer; **persists new active owner epoch e+1 and commit proof atomically before any fan-out**, signs `HANDOFF_ACTIVATED`; peers verify **both prior-owner A and successor B** signatures, check pinned keys and adopt B only after storing updated metadata. Until activation proof/signed current snapshot: PAUSED.
5. If A crashes after `RETIRED` but before B receives COMMIT, Group remains **PAUSED**. Upon explicit user resume, A can **retransmit same already-signed commit** (no owner authority), or users perform separately reviewed recovery; **A cannot roll back to epoch e**. If B crashes before persisting activation, it must not claim owner until restored commit validated. If peers see conflicting epoch/proofs/backup rollback, **freeze Group**, require explicit resolution/re-pair; never choose max numeric epoch from unauthenticated packet.
6. After activation, A may remain as member or leave via a **new REMOVE/LEAVE revision under owner B**. Old owner A cannot sign new owner decisions. No split-brain claim beyond protocol-correct peers with non-rollback protected state; Windows DPAPI **does not** prevent local backup rollback or compromised owner private key (documented risk).

**Transfer test injection points:** before PREPARE store, after B ACCEPT, before/after A RETIRED durable write, before B receives COMMIT, before/after B ACTIVE persist, after partial dissemination, restart each peer, stale A attempts fan-out. For every test assert: either exactly one owner has valid committed authority or Group is PAUSED, never two live owners with valid proof.

### 5.5 Protected Group metadata lifecycle

- **Path (proposed):** `%LOCALAPPDATA%\\IntraChat\\groups.v1.dpapi` (same Windows user scope as M2 identity/pins, dedicated file). Never store in Git repo, home shared profile, cloud-synced folder or routine log. File contains **version, groupId, displayName, current ownerId+pinned key hash, signed roster deviceIds+fingerprints, ownerEpoch, membershipRevision, current event digest, handoff proof, local voluntary leave/revoke/tombstone state**. **No message text, draft, timestamps of user read, receipts, invite secrets, session tokens, messageIds or replay cache.**
- Reuse Windows `Crypt32Util` DPAPI **CurrentUser**, owner-only file ACL, atomic sibling-temp write+replace, protected file size ceiling. Write immutable snapshot validated before swap; check owner and file ACL on **load**, not only write; no fallback to plaintext and no silent reset on corruption/DPAPI failure.
- On owner mutation, **durable write is the commit point**. On member receipt, verify signature+revision continuity+pin then persist new metadata before granting new rights. If storage fails or full, the affected Group becomes PAUSED/read-only until repaired; sending new Group content forbidden.
- Restart: load only metadata, mark PAUSED, user manually initiates M2 verified reconnect, obtain owner signed latest snapshot/activation; fail closed on stale revision/owner mismatch, no automatic transcript restore.
- Deleting a Group locally requires explicit confirmation and atomic deletion/tombstone; Group leave/remove/disband requires **authoritative owner event**, not just deleting a local file. If device/group locally deleted while offline, it cannot silently rejoin on the strength of an old invitation. Keep peer identity/pin store separate; Group delete does not revoke M2 paired device unless user explicitly chooses revoke.
- **Limitations:** DPAPI CurrentUser is not a hardware monotonic anti-rollback register, so manually restored old metadata cannot be conclusively detected while offline. Require fresh current owner signed state on every activation; if unavailable, stay PAUSED. Full OS-account compromise remains out of scope of device-user security.


## 6. Versioned Group wire contract & message lifecycle

[Group V1 contract](contracts/group-v1.md) định nghĩa field tables, critical fields, canonicalization, signatures, state gates, negative vectors và error codes. Phần này là tóm tắt **thiết kế bắt buộc**, không phải encoder đang chạy.

**Wire layers:** existing TCP/v2 preface (≤16KiB, no Group data) → verified M2 TLS1.3 (human Accept + pinned SAS/key and READY) → optional protected Group capability confirmation → **v3 Group app envelope** framed max **65,536 UTF-8 JSON bytes**. Hop destination là authenticated connected PEER; payload chứa groupId/ownerEpoch/revision/runId. **M2 v2 direct chat vẫn type strict và không phải container Group.**

**Mandatory Group event types** (all only after READY+caps, except optional caps-negotiation control frame at dedicated gate):

| Type | Signer / authoritative sender | Result |
| --- | --- | --- |
| `GROUP_CAPS_CONFIRM` / `GROUP_CAPS_ACK` | Each authenticated hop peer | Bind mutual Group capability/version to TLS session and both preface nonces; no Group content yet |
| `GROUP_INVITE_PROPOSE` / `GROUP_PROPOSAL_DECISION` | Current member / owner | Member proposes only, owner approve/reject; no grant |
| `GROUP_INVITE_OFFER` / `GROUP_INVITE_RESPONSE` | Owner / invitee | Invitee accepts/rejects under owner approved offer; not yet member until commit |
| `GROUP_MEMBERSHIP_COMMIT` / `GROUP_ROSTER_SYNC` | Current owner | Signed epoch/revision and roster digest; applied after protected local storage write |
| `GROUP_TEXT_SUBMIT` / `GROUP_TEXT_RELAY` | Original member signature + owner routing signature | Owner validates, assigns ownerSequence/recipient snapshot and fan-out |
| `GROUP_OWNER_ACCEPT` | Owner | Owner accepted/queued for particular sender's original message; **not delivery to all members** |
| `GROUP_DELIVERY_ACK` / `GROUP_READ_RECEIPT` | **Actual recipient device signature** | Sender verifies per-recipient delivery or view event; relay cannot assert for viewer |
| `GROUP_LEAVE_REQUEST` / `GROUP_REMOVE_COMMIT` / `GROUP_DISBAND_COMMIT` | Member leave request / owner committed event | Changes roster or closes Group; only owner commit establishes authority |
| `GROUP_HANDOFF_PREPARE` / `GROUP_HANDOFF_ACCEPT` / `GROUP_HANDOFF_COMMIT` / `GROUP_HANDOFF_ACTIVATED` | Old owner, successor, old owner, new owner | Epoch e→e+1 dual-sign proof and durable ownership transfer |
| `GROUP_ERROR` | Authenticated peer | Sanitized bounded error code; no raw private payload |

**P2P Session authenticity vs Group origin authenticity:** a v3 protected envelope's `sender.deviceId` identifies **the current TLS hop** (B→A then A→C); immutable `origin.deviceId` refers to source B. C verifies signed immutable origin bytes using independently pinned B public key; A signs relay routing decision and validates membership. Receiver checks both signatures, group/run/revision validity, connected hop identity A and original origin B. Similar signed viewer READ from C via A reaches B without A being able to forge C's signature. **If any origin/recipient pin unknown or changed: fail closed and require human M2-style verification of that member's key**; do not downgrade trust to owner self-claim.

### 6.1 Send / fan-out / ACK and READ

1. B's Live composer validates text 1–8,000 code points and ≤32 KiB UTF-8, Group ACTIVE, owner available, B is currently authorized and key pinned. Generate immutable `originalMessageId` + `originSignature` (domain-separated, groupId/ownerEpoch/revision/runId/UTC/body hash); UI marks SENDING, not DELIVERED.
2. B sends signed `GROUP_TEXT_SUBMIT` to A. A verifies session identity B, origin signature, current roster/epoch, Group capacity/duplicate and **serializes acceptance** with concurrent JOIN/REMOVE. A sends `GROUP_OWNER_ACCEPT` correlated to original ID only after acceptance into bounded RAM + recipient snapshot/sequence; still **not** delivery ACK from C.
3. A forwards same signed content (cannot edit origin payload), appends owner signed `GROUP_TEXT_RELAY` with ownerSequence, revision and snapshotDigest to recipient C/D/E who are currently allowed and online. Outbound list excludes sender B unless required by UI mapping; A includes itself as actual application recipient for member-origin message.
4. Each recipient validates A's current owner epoch, owner signature, pinned origin B key/signature, eligibility and dedup. Only after content is accepted into correct Group RAM stream may it send **signed `GROUP_DELIVERY_ACK`**. Owner verifies and forwards unchanged recipient signature to B; B correlates original message/group/epoch/revision/runId/recipient/active session. ACK from owner about its own receipt ≠ C read.
5. Read only when recipient's actual Live Group bubble enters foreground active viewport **≥250ms** (same explicit M2 rule), not preview/background/offscreen or demo. Recipient C signs `GROUP_READ_RECEIPT` with readEventId, originalMessageId, groupId, source origin ID, viewer ID, epoch/revision/runId and viewedAtUTC. Owner checks and forwards, original sender verifies C signature/key and correlation before marking **C VIEWED**. One UI view event/session yields at most one receipt (bounded cache). READ arriving before ACK is higher evidence **for C only**, late ACK cannot regress VIEWED.
6. Sender UI shows **per-recipient** state with count `đã nhận x/y` / `đã xem z/y` if helpful; denom = immutable eligible recipient snapshot for the original message (never dynamically changes when someone joins/leaves). Peer offline never contributes a positive ACK/READ without signed evidence. Read receipt doesn't prove a human eye watched the text.
7. ACK lost/partial owner fan-out: wait bounded deadline then mark affected recipients UNCERTAIN (not definitely FAILED), **no automatic resend**. User may manually retry **same originalMessageId if same Group run/epoch and bounded dedup window**, or send a **new** message after run/epoch change with duplicate warning. Never silently rewrite/merge signed original across revisions; retry cannot bypass group membership.

**Dedup/order:** owner maintains per-Group ownerSequence for an active run; display order can be ownerSequence for confirmed deliveries, with sender/time visually separate. Network preserves order per TLS hop, not a global causal order. Dedup key `(groupId,runId,originId,originalMessageId)` + canonical bodyDigest; same ID/different digest is protocol violation. Dedup is **bounded RAM**, so no exactly-once guarantee across app restart, History 005 or indefinite reconnect. Source read status is monotonic DRAFT→SENDING→OWNER_ACCEPTED→RECIPIENT_APP_ACK→RECIPIENT_READ for each recipient, with separate UNCERTAIN/OFFLINE states. No fake read from old owner or missing receipt.

### 6.2 Authenticated startup/resume, roster and limits

- On every restart Group begins PAUSED, even if metadata loaded and M2 pin valid. User initiates Connect/Reconnect by manual IPv4:port and receiver human Accept; after READY + Group caps, request owner signed `GROUP_ROSTER_SYNC` (latest epoch/revision, active runId, roster digest, owner signed event-chain head).
- Verify owner pinned key and for each sender/viewer in roster its independently pinned SPKI before marking it trusted. No Group text/receipt routed until both parties have reconciled current epoch/revision/run state; refresh cannot implicitly re-add a removed local member.
- On owner restart rotate new random group `runId` and establish a fresh activation after persistent metadata read/validation. Old in-RAM transcript, pending receipts and drafts are lost; old signed message replay from previous run is rejected. Reconnect within same owner run uses fresh session binding + recipient dedup window; no global exactly-once across lost RAM.
- Owner cannot auto-relay to 5 peers: **5 devices means owner + four member connections**, max 4 active M2 sessions in total. If owner is simultaneously engaged with other P2P peers, show `SESSION_CAPACITY` and ask user to disconnect manually; do not evict P2P.
- If an offline member misses ownerEpoch/revision, it cannot send Group text or claim READ for the new run until resynced; after removal it has no Group authorization and cannot use an old pin to regain rights. Membership group revocation does not silently revoke peer's independent P2P pin.

## 7. Bounded resources and performance budgets (design targets; NOT measured)

| Resource | Planned hard bound / behavior | Rationale / test requirement |
| --- | --- | --- |
| Members | **5 devices/group incl owner**; maximum **4 simultaneous peer sessions total** at owner | CL-04-05 + `NetworkConfig.MAX_ACTIVE_SESSIONS=4`; no extra hidden Group sockets |
| Groups per local device | **8 active/paused metadata groups**; reject overflow with visible `GROUP_CAPACITY` | Prototype target; test multiple groups and storage caps |
| Group text | **1..8,000 code points AND ≤32 KiB UTF-8**; blank/invalid surrogate rejected | Reuse M2 `ChatText` behavior without silent truncation |
| Protected frame | **≤65,536 bytes** (4-byte length inside TLS); pre-TLS ≤16,384 bytes no Group data | M2 boundaries; include Base64 signatures within frame cap |
| Group transcript RAM | **≤200 messages OR ≤1MiB UTF-8 text per Group**, global Group+P2P RAM **≤8MiB/process** | RAM-only, LRU eviction/drop with honest UI; no false delivery |
| Pending Group messages | **≤64 owner accepted/queued messages globally**; **≤128 pending status correlations per peer**, ≤512 global receipt records | Aggregate bounded future work; reject excess, no silent loss |
| Dedup | **1,024 message IDs per (group,run) or 30min**, whichever comes first; receipt IDs bounded similarly | Duplicate within window; not durable exactly once |
| Proposals/invitations | **≤8 pending/group, ≤32/device**, 60s proposal/owner approval, 60s invitee response (expire/cleanup) | Prevent idle prompts/unbounded invite map; timers not on EDT |
| Writer queue | **≤16 frames AND ≤2MiB queued bytes per verified session**, shared with P2P | Backpressure on slow C; memory caps must be implemented/tested |
| Metadata store | **≤128KiB protected group file** incl 8 groups/5 members and handoff proofs; ≤1 atomic write inflight per file | Prevent unbounded DPAPI allocation, atomically commit revisions |
| Network deadlines | Reuse v2 TCP connect 5s, preface 5s, inbound decision 30s, TLS 10s, SAS 60s, frame read 15s | Per hop, explicit timeout/cleanup, no unbounded await |
| Owner acceptance/receipt | Owner route ack deadline **10s**; per-recipient delivery ACK **30s**, READ is event-driven and may never arrive; no deadline implies read | No auto resend, show UNCERTAIN, no fake READ |
| Handoff | Prepare/consent **60s**, committed activation **30s**; ambiguous commit → PAUSED/require explicit recovery | Avoid automatic failover/dual owner |
| View event | Live Group selected + window active + bubble viewport visible **≥250ms** | Port existing M2 semantics to Group per-recipient READ |
| UI | Input/scroll p95 **≤200ms**, state update p95 **≤500ms**, bounded normal shutdown **≤2s** | Acceptance measured under 3-client stress, not claimed now |

**Quota precedence:** The process-wide 8MiB target must be a **single budget** for M2+Group, not two independent limits. Queue counters/receipt maps must be concurrency-safe/atomic. If pending work requires more than budget, reject before accepting message and show user status, rather than first saying owner accepted then dropping silently. Storage size limits must be checked before DPAPI plaintext allocation where possible. If stress shows 5 peers cannot stay responsive within target, fix/test resource implementation, **do not silently reduce confirmed 5-device Group** without user approval.

## 8. Verification matrix — test-first, no fabricated PASS

| Stage | Evidence to capture | What it proves / must fail |
| --- | --- | --- |
| **Gate 0 documents** | Spec CL-04 8/8, plan, research ADR, data model, strict contract + review | Architecture traceability, not functional implementation |
| **TDD domain RED→GREEN** | Create/5-cap, member propose → owner approve → invitee accept, leave/remove, epoch, handoff, recovery, RAM vs metadata | Failing test before implementation and passing after |
| **Protocol/security** | Canonical P-256 signatures vectors on real Java21, strict v3 codec, tampering/forgery/unknown fields/type/version, wrong SPKI/revision/run | No Group through v1/v2 and no malicious relay/viewer impersonation |
| **Protected storage** | Win11 DPAPI CurrentUser, owner ACL read+write, corrupted ciphertext, occupied parent, cross-Windows-user, atomicity, restart, delete/revoke | Metadata survives; content/receipt not stored; failure fails closed |
| **3-JVM loopback** | A owner + B/C members; member proposed D with owner approval; owner→B/C, B→A→C, ACK/READ viewport, leave/remove | Real encrypted multi-peer Group traffic, not mock |
| **Owner handoff fault injection** | A→B, A crashes at each prepare/retire/commit, B crashes pre/post activate, concurrent event, stale A relay | No dual active owner, fail-closed paused/recovery |
| **3+ client negative** | D not member, wrong display names, missed revision, key change, forged origin/read, stale epoch, duplicate conflicting message | 0 outsider content, 0 unverified viewer success |
| **Offline/DoS/EDT** | C slow/offline while A/B continue, A offline, bounded queue/bytes/timeout, UI 5+ cycles, M2/P2P unaffected | Partial recipient state truthful; no UI freeze/memory runaway |
| **Physical LAN** | ≥2 actual Windows 11 x64 PCs (inherited M1/M2 gate), ≥3 independent clients, offline Internet, manual IPv4, real consent/SAS | Actual LAN topology with host count explicit; localhost alone not sufficient |
| **Independent review** | Dependency CVE/license, TLS/pinning, canonical signature, metadata at rest, handoff/rollback, app logs/privacy | Security risk signoff, no open Constitution MUST blockers |
| **Release** | `AC-04-A…L` evidence, M1+M2 Gate 1 Verified and regressions PASS; M3 still needs History 005 | **Feature 004 Verified only after all gates; M3 not automatically Verified** |

**Examples of must-fail tests:** member B attempting direct JOIN without owner approval; forged owner signed roster; A relays invented B origin with unpinned key; A fabricates C READ; C loses focus before 250ms; owner crashes after retired but before COMMIT delivery; DPAPI metadata wrong user; B removed between acceptance and fan-out; stale Group v3 sent to a v2-only peer; 5-device Group with owner external P2P exceeding shared 4 sessions.

### 8.1 Real LAN acceptance trace (not run)

- Test rig needs **≥3 distinct JVM/client processes** A/B/C and **at least 2 physically distinct Win11 LAN PCs**; if three actual PCs are available, assign A/B/C separately. Record device hostnames anonymized, app commit/hash, `java -version`, IP profile, manually supplied endpoint and how Internet access was disabled without disrupting LAN.
- Consent: every inbound TCP Accept, first peer fingerprint/SAS per M2 and group key verification per member; no public Internet, no auto port opening, correct TLS1.3.
- For owner relay, verify B sends to A, A relays to C, C signed app-ACK/READ arrives B; D outsider 0 data. Cover invite approval, owner handoff, restarted metadata and no chat replay.
- Capture deterministic JUnit XML, sanitized console log, screenshots of Live UI status and state snapshots; **no actual chat content, private key, SAS secret or real personal metadata in evidence**.
- **No acceptance test execution in this docs-only task.** Existing 107/107 Java21 M2 tests cannot stand in for Group tests.

## 9. Risk register, ADRs và implementation sequence

| Risk | Severity | Mitigation / stop rule |
| --- | --- | --- |
| Handoff partial commit or stale owner fork | **Critical** | Durable A retire before B activation, signed dual proof, epoch monotonicity, fail-closed PAUSED and crash injection |
| Owner relays forged origin/viewer | **Critical** | Sign immutable origin/ACK/READ, pinned signer keys verified by each recipient; do not trust owner-only attestation for sender |
| Membership revocation races in-flight fan-out | **High** | Owner serial executor + durable remove commit, re-check queue before writes; no claim to unsend bytes |
| Group metadata leaks/rollback/corrupt DPAPI | **High** | DPAPI/ACL/atomic/fail-closed, limits, fresh owner snapshot after restart, no History; rollback limitation documented |
| v2 strict decoder incompatible with Group | **High** | Separate Group v1/v3 profile + mutual post-TLS capability confirmation; no v1/v2 chat whitelist relaxation |
| 4 M2 session cap vs 5 Group devices | **High** | 4 peer links + owner local; share registry and user-visible quota, load tests before enable |
| READ spoof/false positives | **High** | Exact viewport foreground ≥250ms, distinct signed viewer receipt, full group/recipient correlation and idempotent updates |
| Queue/RAM amplification and partial delivery | **High** | Group/global budgets, bounded per-recipient queues, no blocking EDT, truthful UNCERTAIN/offline |
| Pairwise trust verification overhead | **Medium/High** | Separate first-contact SAS/pinning for each origin key before “verified sender”; limit group to 5 and document UX cost |
| External security/physical LAN evidence missing | **Release blocker** | Cannot mark Group Verified; M1/M2 gate and independent review required first |

**Architecture decisions documented separately:** [research.md](research.md) covers owner relay vs mesh, handoff, signatures, persistence, compatibility, retry/READ and acceptance tradeoffs; [data-model.md](data-model.md) has entity/schema/state tables; [contracts/group-v1.md](contracts/group-v1.md) has wire fields/nonce/signatures/errors.

**Implementation task tracker:** [tasks.md](tasks.md) — **124 tasks T001–T124, 0 completed**. Sequence below maps to the task phases; all implementation and Group acceptance remain NOT RUN.

1. Verify prerequisite M2 design/runtime/security and owner 4-peer capacity; freeze Group extension version/canonical vectors and independent review.
2. RED tests + pure Group domain, ACL/roster/revision reducer, bounded typed states and member invitation proposal.
3. Protected metadata DPAPI CurrentUser store with anti-corruption, atomic write, restart and delete/revoke tests; signing and key lookup.
4. V3 Group protocol codec and negotiated Group caps within M2 TLS, strict v2 regression; signed origin/receipts.
5. Owner coordinator serialized membership/fan-out/ACK/read routing; handoff protocol/epoch crash injection.
6. FE Live Group components and true per-recipient viewport READ; capacity/status and Demo isolation.
7. 3-JVM secure tests → stress/negative → physical Win11 multi-peer LAN → independent security review → AC-04 evidence/release signoff.

## 10. Definition of Done and explicit BLOCKERS

- **Planning artifact DONE:** spec 8/8 CL, this plan and design artifacts consistent; links/references and internal counters checked, Git commit/push verified separately.
- **Implementation NOT DONE:** no new Group Java service, protocol, storage, UI or tests in this task; no plan statuses converted into feature PASS.
- **Inherited blockers:** (B1) M1/M2 acceptance on **two physical Win11 LAN machines** not available/verified; (B2) dependency/license/CVE and security/privacy peer review not signed; (B3) Group pairwise signer verification, DPAPI cross-user + handoff safety must be implemented and tested.
- **Gate 1 Group NOT VERIFIED:** must run actual multi-client Group test, negative/fault/resource checks, per-recipient READ tests, real LAN & independent security review, and keep M0/M1/M2 regressions. Constitution I–VII MUST requirements remain binding.
- **M3 NOT VERIFIED:** M3 includes History 005, which is a separate future feature and cannot be claimed by completing Group 004 alone.

**Source-of-truth precedence:** user's 8 confirmed CL-04 + Constitution → `spec.md` requirements → `plan.md` architectural decisions → protocol contract/data model → future code/tests/evidence. If a later implementation proposes different user behavior (e.g. Group READ omitted, no metadata persistence, no handoff or 4 devices max), obtain a new explicit user clarification before changing requirements.
