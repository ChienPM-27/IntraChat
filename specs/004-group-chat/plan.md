> **Ghi chú phạm vi đồ án (22/11/2026):** [ROADMAP hiện hành](../../ROADMAP.md) · [11 chức năng và nghiệm thu lớp học](../../docs/course-project-scope.md) · [Tích hợp Messenger](../../docs/messenger-integration-plan.md) · [Checklist Group MVP cho môn học](course-mvp.md). **Group 28/92** (core prototype), **chưa có Group Live**; ưu tiên tạo nhóm/invite/fanout 3 peer và nối `CreateGroupDialog` vào `MainChatFrame`, không để audit owner-grant/Gate0 cũ chặn phần code. **Các yêu cầu Gate/audit bảo mật chuyên sâu ở phần lịch sử bên dưới chuyển thành nâng cao, không phải điều kiện để bắt đầu code**. Giữ an toàn tối thiểu, không tắt TLS M2, không giả mạo trạng thái hoặc ghi đè file ngoài ý muốn. **Task checkbox/biên bản Gate cũ không bị đổi thành PASS**; khi có tính năng Live thật hãy ghi test và trạng thái riêng trong [ma trận 11 chức năng](../../docs/feature-matrix.md).

# Implementation Plan — 004 Group Chat Live (M3)

**Ngày lập:** 2026-11-22 · **Phiên bản kế hoạch kỹ thuật lịch sử:** 0.1.0 · **Trạng thái hiện tại:** Group core 28/92 đã có code/test, **Group Live chưa được nối**. Kịch bản đồ án ưu tiên [Group MVP](course-mvp.md); không biến Gate0 reviewer độc lập thành điều kiện bắt đầu code.

**Đầu vào:** [Spec 004 đã clarify](spec.md) · [Constitution v1.0.0](../../.specify/memory/constitution.md) · [ROADMAP](../../ROADMAP.md) · [Spec M2](../003-p2p-messaging/spec.md) · [M2 Plan](../003-p2p-messaging/plan.md) · [Blockers M2](../003-p2p-messaging/evidence/execution-blockers-2026-11-22.md) · [M1 Acceptance](../002-lan-networking/evidence/m1-acceptance-report.md)

**Baseline kiểm chứng trước khi lập plan:** M0 41/41 Verified; M1 37/60 In Progress; M2 34/90 In Progress. Ngày 22/11/2026 đã có lần chạy offline Java Temurin 21 với 113/113 JUnit PASS, build/shaded JAR diagnostics exit 0. Đây là **kiểm chứng M0/M1/M2 local trước đó**, hoàn toàn **không phải test Group 004**; hai Windows LAN vật lý, Gate 1 M1/M2 và independent security review vẫn chưa đạt.

## 1. Summary và quyết định đã được duyệt

Xây dựng Group Chat Live giữa **2–5 thiết bị được xác minh, gồm cả chính mình**, qua LAN không cần Internet. Các thành viên được mời phải Accept; **một owner duy nhất** (device identity của người tạo) quyết định thêm/xóa/đổi tên và ký bản membership revision tăng đơn điệu; thành viên thường được tự leave. Người gửi fanout **trực tiếp** từng bản tin text/emoji qua phiên TLS 1.3 đã verified đến từng người nhận, **không có relay/group server cho nội dung**. Trạng thái Đã nhận/Đã xem được theo dõi **riêng từng recipient**.

Group ID, tên nhóm, owner ID/khóa đã xác minh, roster và revision được **lưu bền vững ở Windows user profile với DPAPI + ACL + ghi atomic**. **Message body, draft, attempt, ACK/READ, transcript không được persist**, không tự sync History sau restart. Metadata khôi phục phải ở trạng thái STALE chờ revalidate owner/peer/roster, không tự biến thành quyền gửi.

**Chính sách an toàn được chọn trong plan:** Để tránh sender đang dùng roster cũ gửi nội dung mới tới người đã bị owner loại trong một LAN partition, **mỗi group message mới cần ticket phân phối ngắn hạn, ký bởi owner, gắn đúng messageId/revision/tập recipient**. Owner là **control authority**, không được xem/relay phần text. Khi không liên hệ được owner, **gửi Group mới bị tạm dừng**, không tự retry; P2P độc lập vẫn hoạt động. Đây là đánh đổi availability được ghi rõ, chưa phải tính năng đã triển khai.

### 1.1. Binding Clarifications CL-04-01…06 (CONFIRMED 2026-11-22)

| CL | Quyết định sản phẩm đã xác nhận | Hệ quả kỹ thuật |
| --- | --- | --- |
| 04-01 | Direct verified TLS P2P fanout, không content relay | Gửi từng peer, tối đa 4 recipients khác; không gửi group qua M1 v1 |
| 04-02 | Đúng một owner quản lý, member tự leave | Owner ký/commit roster, không auto owner election/multi-admin |
| 04-03 | Invitee phải Accept, owner commit revision monotonic | Snapshot có signature và hash chain, stale/replay bị từ chối |
| 04-04 | Đã nhận/Đã xem theo từng người nhận | ACK và viewport-based READ là hai loại receipt authenticated khác nhau |
| 04-05 | Protected group metadata persist; chat/draft/receipts RAM-only | Kho group DPAPI user scope; restart không có History |
| 04-06 | Tối đa 5 thành viên cả local, chưa nghiệm thu | Reject member thứ 6; stress trên 4 remote sessions |

**Không tùy ý thay đổi CL đã chốt** khi viết tasks/code; nếu các ADR bên dưới cần sửa do review thì ghi thay đổi và xin duyệt trước.

## 2. Source audit — hiện trạng và khoảng trống

| Source trong working tree | Trạng thái thật | Công việc dự kiến |
| --- | --- | --- |
| ui/IntraChat.java, ui/dialogs/LanConnectionDialog.java, ui/frames/MainChatFrame.java | Một Swing/FlatLaf Messenger cho Demo và Live, manual TLS pairing | Thêm Group Live vào cùng Messenger, không tạo launcher thứ hai |
| model/mock/MockChatService.java, ui/dialogs/CreateGroupDialog.java, model/Conversation.java | Group chỉ là mô phỏng Demo/FE, không truyền mạng | Tái dùng UI có kiểm tra quyền, dữ liệu Live phải lấy từ group service thật |
| listener/ChatActionHandler.java, listener/LiveChatAdapter.java | Live createGroup trả lỗi UnsupportedOperationException; chỉ hỗ trợ P2P text | Tạo GroupActionPort/adapter và snapshot group/live receipts; giữ MockChatService tách |
| application/SecureChatCoordinator.java, transport/TlsChatSession.java | M2 TLS1.3 verified pairing, nhưng chưa có group capability/dispatcher | Dùng lại authenticated session, version negotiation, share peer socket quotas |
| application/LiveChatService.java | P2P RAM text/ACK/READ; MAX_ACTIVE=4, MAX_PENDING=128, 500 tin và 2 MiB mỗi peer | GroupChatService có per-recipient ledger, global limits; không trộn P2P conversation |
| protocol/ChatV2Type.java, protocol/ChatV2Codec.java | v2 codec fixed enum, destination PEER và string payload, giới hạn 65,536 bytes | Thiết kế Group V1 phiên bản/capability riêng, không reinterpret old v2 |
| security/DeviceIdentityStore.java, security/PinnedPeerStore.java, security/ProtectedLocalFile.java | Windows CurrentUser DPAPI, owner-only ACL, atomic writes, store được bảo vệ | ProtectedGroupStore dạng hẹp, owner signature+revision, schema strict, fail closed |
| transport/NetworkConfig.java | 4 active sessions, 4 pending approval, manual IPv4:port; queue targets | Max group 5 đúng bằng 4 remotes nếu không còn session cho peer khác; giới hạn global |

**Chưa có:** Live Group service/protocol, persistent group roster, owner authorization, fanout, group receipts, Group Live UI, test nhiều peer. Group Demo **không** phải Live. M1 v1 plaintext diagnostics không được gửi bất kỳ group bytes nào.

## 3. Technical Context — target architecture

- **JDK/OS/build:** Windows 11 x64, Java 21, Maven Wrapper, JUnit 5, Swing+FlatLaf, Jackson; JSSE TLS1.3, Bouncy Castle X.509, JNA/DPAPI như M2. Không thêm thư viện mặc định nếu chưa có review CVE/license.
- **Addressing:** Manual IPv4 và TCP port, mỗi peer có thể listen/connect; inbound Accept/Reject vẫn bắt buộc; **không auto discovery/reconnect** hoặc tự sửa Public Firewall.
- **Network topology:** direct mesh fanout; sender gửi B,C,D,E riêng qua authenticated TLS, owner chỉ cấp quyền control. TCP sender ↔ recipient thực, không relay nội dung qua owner.
- **Trust:** Certificate/deviceId/SPKI pin đã verified; kết nối CONNECTED không đồng nghĩa ready/authorized; group roster và grant phải owner-signed, kiểm tra theo pinned owner key.
- **Threading:** tác vụ codec/crypto/file/network trên worker (bounded executor/queue), Swing EDT chỉ update UI; cancellation/close là first-class operation.
- **Persistent data:** user-scope DPAPI, owner-only ACL, atomic replace, no plaintext fallback, owner-signed metadata và failure recovery; chat/receipt/draft RAM-only.
- **Group ordering:** per-TCP connection byte order không là global order; UTC/locale chỉ để hiển thị; clock skew không quyết định quyền.
- **Backward compatibility:** old M2 v2 chỉ hiểu CHAT_TEXT peer; Group phải được **explicitly negotiated**, không gửi loại lạ cho legacy v2 và không downgrade về M1 plaintext.
- **Dependencies:** Spec 004 code có thể thử local từng phần trước, nhưng **M3 nghiệm thu phụ thuộc M1 và M2 Gate 1**. Milestone M3 tổng còn bao gồm Spec 005 History.

## 4. Component architecture và luồng dữ liệu

**Layers (logical):**

1. **FE:** MainChatFrame / CreateGroupDialog / GroupMembersDialog / GroupInviteDialog; xác minh nhìn thấy tin trong foreground group viewport, hiển thị owner/roster/partial status. Demo vẫn dùng MockChatService.
2. **Adapter:** GroupActionPort + LiveChatAdapter ánh xạ group snapshot/command sang Swing; không chuyển trực tiếp network events vào EDT, không dùng displayName làm key.
3. **Application:** GroupChatService tạo, mời, fanout, RAM timeline, receipt per recipient; GroupAuthority actor do owner quản lý commit revision/grant; SessionRouter dùng cùng verified transport P2P khi capability đã negotiated.
4. **Domain:** GroupId, GroupMetadata, GroupMembership, GroupInvitation, GroupText, RecipientAttempt, GroupReceipt; immutable typed event và monotonic revision.
5. **Protocol:** GroupV1Codec/typed envelopes, canonical signing bytes, group capability/strict validation, owner-signed roster+grant, groupId không phải P2P conversationId.
6. **Security/Transport:** reuse M2 mutual TLS/paired peer, pin, consent, protected store; group metadata DPAPI; no M1 v1 user traffic.

### 4.1. Create / invite / join / revision commit

1. Owner tạo random groupId UUID không phải P2P ID; đề xuất title, owner verified identity, revision 0, trạng thái PENDING, không ngay lập tức cho phép gửi group.
2. Owner mời các **verified** peer qua TLS READY; payload chứa groupId, owner key binding, title, inviteId, expiry, proposal/revision digest. Recipient xác minh sender thật từ TLS và yêu cầu human **Accept**; Reject/Expire không thành member.
3. Owner nhận Accept có correlation, khóa mọi cập nhật membership **theo thứ tự một actor/lock**. Không được chọn hai owner hoặc tự thêm peer nếu họ chỉ biết UUID/tên.
4. Owner tạo **canonical committed snapshot** có groupId/ownerId/owner SPKI/revision N+1/previous digest/unique members (2–5)/eventId/createdAtUTC; ký bằng **owner ECDSA P-256** từ device identity đã ghép cặp. Không ký JSON tùy tiện không canonical.
5. Owner **atomic persist trước** khi phân phối commit; ghi lỗi → trạng thái UNCOMMITTED, không phát quyền mới. Sau đó gửi signed roster tới active members; receiver validate owner cert/pin, signature, hash chain, revision, unique peers, chính danh mình và commit ACL, persist atomic rồi ACK applied.
6. Thành viên offline chưa apply revision mới ở trạng thái STALE/PENDING_SYNC; không dùng roster cũ làm phép gửi. Member muốn rời → tự khóa gửi ngay và gửi LEAVE_REQUEST authenticated đến owner; owner commit revision sau đó. Owner offline → membership change chờ, không tự bầu owner.
7. Đổi owner key/thu hồi trust → không được tự di chuyển ownership; yêu cầu xử lý khôi phục có phê duyệt/new group. Removal không xóa được dữ liệu đã nhận từ trước.

**Tính nhất quán:** Owner là serial authority **không phải consensus server/relay**; các peer có thể áp dụng revision khác thời điểm, vì vậy phải ngăn gửi khi roster/grant không đủ bằng chứng. Kết nối owner còn hoạt động chỉ để kiểm tra quyền, không chuyển tiếp nội dung nhóm.

### 4.2. Fanout và ticket owner (control-only)

1. Sender S sinh messageId; muốn gửi thì hỏi owner qua verified TLS: groupId, messageId, senderId, sender-known revision, exact recipient IDs **không gồm nội dung text**.
2. Owner kiểm tra roster vừa commit, sender là member active, recipients đúng tập active ngoại trừ sender, không có update pending, group size ≤5. Owner ký ticket canonical gồm groupId, messageId, ownerKeyId, revision, senderId, sorted recipients, unique nonce, issuedAtUTC, expiresAtUTC (mục tiêu hiệu lực ≤10s).
3. Sender kiểm tra chữ ký ticket rồi trực tiếp gửi mỗi người 1 bản GROUP_TEXT qua ready TLS có negotiated group capability, kèm ticket. Từng recipient được gắn Attempt trong RAM với status riêng.
4. Receiver kiểm tra **TLS sender binding** = sender ticket, chính mình trong danh sách recipients, chữ ký owner/pin, groupId/revision, ticket chưa hết hạn và chưa replay, roster local đúng revision. Nếu local còn stale thì phải đồng bộ owner-signed roster trước khi nhận, không hiện bubble/ACK sớm.
5. Chỉ sau khi nhận và ghi RAM hợp lệ, receiver phát GROUP_DELIVERY_ACK đúng messageId/groupId/sender/session/revision. READ chỉ khi **row của đúng group thật sự hiện trong viewport foreground**, receiver gửi GROUP_READ_RECEIPT riêng. “Đã xem” không khẳng định mắt người đã đọc.
6. Kết quả gửi một phần không roll back các peer đã nhận; UI hiển thị theo từng người: gửi cục bộ, đã nhận, đã xem, lỗi, chưa rõ. Không auto resend/outbox. Manual retry cần ticket mới, correlation và duplicate warning.

**Fail-closed / race:** owner offline → không cấp ticket mới → **Group send PAUSED**, P2P vẫn dùng được. Owner không cấp ticket bao gồm người bị xóa **sau khi revision remove đã commit**. Tin đã được ủy quyền và truyền trước commit có thể đến muộn; không được hứa thu hồi/xóa tức thì nội dung đang truyền, đặc biệt trong LAN partition. Reviewer phải kiểm tra race commit/grant/ACK trước Gate 0.

### 4.3. Restart và data lifecycle

- Khi tắt app: đóng Group sessions/threads, xóa GroupMessage/transcript, pending invite, drafts, grant, receipt/attempt RAM; giữ metadata nhóm và device trust trong kho Windows bảo vệ.
- Khi mở lại: metadata decode đúng schema/version/signature/size; hiển thị nhóm ở STALE, không có transcript cũ; re-pair/revalidate protected peer/session và owner revision trước READY; không tự reconnect và không auto sync tin.
- Không dùng metadata persisted để bỏ qua Accept của kết nối đến, fingerprint hoặc owner ticket mới. Bảo vệ local không đồng nghĩa mã hóa E2EE nhóm end-to-end khi member bị compromise.

## 5. Versioned wire protocol — Design Gate 0

**Hiện tại:** ChatV2Codec chỉ chấp nhận protocolVersion=2, destination.kind=PEER, enum CHAT_TEXT/DELIVERY_ACK/READ_RECEIPT/PAIR_*, string-only payload; unknown types fail closed. Không có shared v1/v2 dispatcher được nghiệm thu.

**Quyết định kiến trúc:** định nghĩa namespace **GROUP_V1** mới trên **TLS1.3 đã verified**, nhưng **chỉ sau explicit bilateral capability negotiation**. Mở rộng bootstrap/READY bằng một phiên bản/profile mới (ví dụ CHAT_V3 với capabilities) qua contract riêng; legacy M2 v2 phải hoạt động P2P như cũ và **không bao giờ nhận unsolicited GROUP_V1**. Nếu peer không hỗ trợ, Group disabled; P2P v2 chỉ dùng qua phiên đã thương lượng rõ hoặc **người dùng chủ động** kết nối lại, không tự downgrade vào M1 v1. Trước code phải chốt chi tiết bytes, transcript và test vectors trong contracts/group-v1.md.

| Wire type dự kiến | Ý nghĩa và field bắt buộc ngoài version/type/msgId/actual TLS sender/session/timestamp |
| --- | --- |
| GROUP_CAPABILITIES | Supported group-v1, sender/recipient identities, authenticated handshake binding |
| GROUP_INVITE | inviteId, groupId, owner ID/SPKI, title, expiryUTC, proposal digest |
| GROUP_INVITE_DECISION | inviteId, groupId, Accept/Reject, verified invitee, session |
| GROUP_ROSTER_COMMIT | groupId, owner, revision+previousHash, exact 2–5 members+SPKI, signed canonical bytes |
| GROUP_ROSTER_ACK | groupId, revision, hash, recipient verified identity, applied/rejected |
| GROUP_LEAVE_REQUEST | groupId, member ID, revision, unique event ID |
| GROUP_GRANT_REQUEST | groupId, messageId, senderId, revision, recipients; **no text** |
| GROUP_SEND_GRANT | groupId, revision, messageId, sender, sorted recipients, nonce, expiry, owner signature |
| GROUP_TEXT | groupId, messageId, sender, one specific recipient, revision, UTF-8 text, signed grant |
| GROUP_DELIVERY_ACK | original messageId, groupId, sender, exact recipient, session, revision, acceptedAtUTC |
| GROUP_READ_RECEIPT | original messageId, groupId, exact recipient, session, revision, readEventId, viewedAtUTC |
| GROUP_ERROR | Bounded safe errorCode and correlation ID only, no original private content |

**Validation MUST:** strict UTF-8/JSON, unique keys, canonical UUID/timestamp, exact allowlists/type/schema/size, recipient actual TLS binding, signed owner identity and monotonic rev; reject malformed/downgrade/replay/cross-group/cross-session before state changes. Do not use native Java deserialization. Canonical bytes must include **domain separator** distinct for ROSTER vs GRANT, groupId, owner key, revision and sorted field ordering; publish known-good signed test vectors before implementation.

**Owner-signing security:** TLS only proves the *direct peer*; a group member could otherwise forge a claim from owner. Every authoritative roster/ticket therefore requires **owner digital signature validated against pinned owner SPKI**. Do not trust unsigned ownerId/name or an event relayed from another member. Keep signing key in protected DeviceIdentityStore; Group service must not expose/export private key.

## 6. State model & invariants

| Machine | Allowed transitions | Mandatory stop |
| --- | --- | --- |
| TLS/trust | DISCONNECTED → CONNECTING → VERIFIED_READY / FAILED | Group unavailable if no TLS READY or no group capability |
| Invite | PENDING → ACCEPTED / REJECTED / EXPIRED / CANCELED | Not Active before owner signed commit |
| Group UI | CREATING → PENDING_COMMIT → ACTIVE / FAILED; ACTIVE → STALE / REMOVED | Restore from disk is STALE, never READY |
| Membership | INVITED → ACTIVE → LEAVE_PENDING / LEFT / REMOVED | Only owner signs committed revision; no rollback |
| Authorization | DRAFT → WAIT_OWNER_GRANT → GRANTED → SENDING | No fresh signed grant: never emit group text |
| Per recipient | SENDING → LOCAL_SENT → APP_ACKNOWLEDGED → READ; or FAILED/UNCERTAIN | Wrong group/peer/session/rev receipt never elevates status |
| Store | ABSENT → EMPTY; VALID → LOADED_STALE; CORRUPT/ACL_FAIL → BLOCKED | No plaintext fallback, no auto-repair that elevates trust |

**Invariants:** groupId independent P2P ID; unique 2–5 members, exactly one owner, only verified pinned identities; revision monotonic (64-bit overflow checked); no group content to unverified/unauthorized/nonmember; owner ticket explicit; sender sends no content to owner in grant request; 1 receipt cannot mark every member READ; RAM chat never restored after restart.

## 7. Protected metadata design

**Path được đề xuất (không code hiện tại):** %LOCALAPPDATA%/IntraChat/security/groups.dpapi, riêng với identity/trust stores. ProtectedGroupStore mới dùng cơ chế Windows CurrentUser DPAPI/owner ACL/atomic write từ security/ProtectedLocalFile qua API hẹp trong cùng security package, **không** expose decrypt/signing material cho UI.

**Persist đúng các trường:** schemaVersion; groupId; title; ownerDeviceId và pinned ownerSpkiFingerprint; unique member IDs/fingerprints/roles; committed revision N; previous snapshot hash; owner signature; local revision high-water; last sync timestamp và stale flag. **Không persist:** chat body, draft, pending attempts, grants, raw SAS, private keys trong group store, receipts, replayed payload, invite content.

**Trình tự commit:** validate + sign canonical bytes → write owner-only encrypted temp file → atomic replace → publish newly committed revision; if write fails, retain old valid state, no partial membership permission. Receiver phải verify owner signature/previousHash/revision before atomic save/ACK.

**Corruption & rollback:** DPAPI cross-user decrypt failure, owner-only ACL, missing/invalid schema, wrong owner binding, truncated cipher, bad signature, duplicate IDs, over 5 must fail closed. Khi file vắng ở first run → empty groups, không silent regenerate trên corrupt old group data. **DPAPI và atomic write không ngăn restored old valid ciphertext từ backup**; vì vậy restored roster luôn STALE và phải xác thực lại với owner. Không tuyên bố local high-water là rollback-proof nếu chính file high-water cũng rollback được.

**Retention/deletion:** user có thể leave và xóa local group metadata có xác nhận; remote recipients có thể đã lưu/copy tin cũ, không có remote secure erase. Group metadata là thông tin tương đối nhạy cảm (danh sách thành viên), chỉ dùng đúng mục đích, không vào routine log/Git. Windows other-user DPAPI/ACL test cần account khác được cấp phép.

## 8. Resource budgets — proposed, chưa đo nghiệm thu

| Budget | Target | Nguồn/ghi chú |
| --- | --- | --- |
| Thành viên nhóm | **2–5**, gồm self, tối đa 4 remote | CL-04-06 binding |
| Active TLS peers/process | ≤4 verified remotes, ≤4 pending approval | NetworkConfig hiện tại; shared group/P2P |
| Active/stored groups | ≤8 active, ≤16 metadata groups | Target cho planning, chưa có trong code |
| Pending invites | ≤4/group, ≤8/process | Target cần test flood |
| Group title | 1–60 Unicode code points, ≤240 UTF-8 bytes | UI Demo hiện kiểm 60 Java chars; phải nâng thành code point |
| Text | ≤8,000 code points AND ≤32 KiB UTF-8 | ChatText hiện có |
| Group envelope | ≤65,536 bytes serialized | ChatV2Codec.LIMIT hiện có; group codec riêng |
| Outbound queue | ≤16 frames AND ≤2 MiB/remote session, shared P2P + Group | NetworkConfig; chưa chứng minh toàn M2 writer |
| Group transcript | ≤500 messages hoặc ≤2 MiB/group, tổng ≤8 MiB toàn app | Target; phải enforce cả global |
| Pending attempts | ≤128 recipient attempts toàn process | Hiện có M2 cap; group fanout nhân theo recipient |
| Dedup | ≤1,024 message IDs/group, global cap kiểm thử thêm | Target; bounded session only |
| Protected metadata store | ≤1 MiB ciphertext và ≤16 nhóm | ProtectedLocalFile hiện read cap 1,048,576 |
| Connect/HELLO/inbound | 5s / 5s / 30s | NetworkConfig hiện có |
| TLS/SAS | 10s TLS, 60s SAS human verification | M2 code target, không thay bằng scripted production Accept |
| Invite/roster ACK | 30s / 10s | Proposed explicit timeout |
| Owner grant TTL | ≤10s, signed per messageId + recipients | Có clock-skew handling và replay cache |
| Delivery uncertainty | ACK timeout 30s (UI ghi UNCERTAIN) | M2 behavior được kế thừa |
| UI/cleanup | p95 EDT event ≤200ms; close/cancel mục tiêu ≤2s | Chỉ target cần đo trên Windows |

**Ngân sách không phải thực tế đã đạt.** Virtual threads không tự giới hạn queue; giới hạn 4 remotes không bảo đảm đồng thời thêm peer ngoài nhóm. Nếu user muốn thêm peer, UI báo capacity, không tự nâng cap.

## 9. Threat model & Security Review

| Threat | Mitigation cần triển khai/test | Giới hạn |
| --- | --- | --- |
| M1 plaintext/downgrade/legacy v2 | explicit secure group version capability; no plaintext/unknown packet | M1/M2 security gates chưa Verified |
| Peer giả mạo/changed-key | TLS1.3 mutual authenticated session + SAS/pin; no unverified grant | Fingerprint không xác minh danh tính ngoài đời |
| Forged roster/owner impersonation | owner P-256 signature, canonical domain-separated bytes + pinned owner SPKI | Owner device bị compromise ngoài scope bảo đảm |
| Removed member/partition | owner serial revision, per-message signed ticket, fail closed owner offline | In-flight pre-removal messages có thể đến sau commit |
| GroupId guessing/forged membership | verified sender + signed roster + explicit invite Accept | ID không phải authentication |
| Replay/duplicate grant/receipt | unique IDs, revision/session/recipient binding, bounded replay cache | No cross-restart exactly-once do RAM-only |
| Background FALSE READ | foreground group viewport visibility observer + authenticated per-recipient receipt | READ = displayed, không phải người đã đọc |
| Protected store attack | user DPAPI, ACL, atomic, signed metadata, restore STALE | DPAPI không bảo vệ trước malware cùng user, rollback backup |
| Oversized/flood/half-open | limits frame/queue/session/invite/dedup, timeout and cleanup | Need stress/soak numbers before PASS |
| Private data leakage | no chat/receipt/draft to logs/disk/Git, inspection after restart | Java process memory/dump and recipient copy possible |
| Owner disconnect | Group send paused, P2P independent; no shadow relay/auto-election | Availability trade-off đã nêu rõ trong UI |

**Security claim:** mỗi liên kết P2P được TLS bảo vệ sau pairing, **không phải** được chứng nhận group E2EE end-to-end theo giao thức khóa nhóm. No Internet dependency; peer đã nhận content có thể sao chép/chụp màn hình. Independent review là blocker bắt buộc.

## 10. ADRs & trade-offs

| ADR | Lựa chọn kiến trúc | Đánh đổi / rejected alternative |
| --- | --- | --- |
| ADR-04-01 | Direct P2P fanout tối đa 4 recipients | Không relay; O(n) frame per sender, nhưng fit group 5 |
| ADR-04-02 | One owner + signed monotonic membership | Không multi-admin/auto takeover; owner offline membership changes blocked |
| ADR-04-03 | Owner ticket cho **mỗi** group message | Không gửi khi owner offline; tránh cached roster authorization mơ hồ |
| ADR-04-04 | Per-recipient application ACK và foreground READ | Không fake global delivery/read; tăng RAM status bound |
| ADR-04-05 | Persist metadata bằng DPAPI + atomic; chỉ RAM messages | Không SQLite/History Sync; khôi phục group STALE |
| ADR-04-06 | Hard cap 5 including local | Phù hợp 4 remote M2 count trong case cơ bản; cần test |
| ADR-04-07 | Versioned Group V1/capability negotiation | Legacy P2P v2 không nhận unknown frames; no silent downgrade |
| ADR-04-08 | Reuse one Messenger/LiveChatAdapter | Không tạo UI thứ hai; Demo isolation giữ nguyên |
| ADR-04-09 | Owner key binding và explicit recovery | Không tự chuyển owner khi key đổi/mất |
| ADR-04-10 | Fail closed when roster/grant not verifiable | Sacrifice Group availability for access control under partitions |

**Cần review kỹ thuật trước code:** exact capability negotiation transcript với legacy M2; canonical signature test vectors; owner ticket round-trip latency và grant-vs-removal race; dual-host trust/DPAPI cross-user; quotas và UX khi owner offline. Đây là design questions trong Plan, **không phải 6 CL còn mở**.

## 11. Constitution Check (Principles I–VII)

| Nguyên tắc | Gate 0 — chỉ đánh giá design | Gate 1 cần bằng chứng |
| --- | --- | --- |
| I. LAN-first | **PASS design:** direct mesh sender fanout, manual IPv4, no relay/content Internet; owner control | ≥3 identities, ≥2 physical Windows hosts không Internet; owner/peer offline |
| II. Version/Identity | **CONDITIONAL:** separate Group V1/capabilities, owner-signed roster/grant; exact transcript chưa frozen | Wire contract, legacy interop, metadata/alias tests |
| III. Bounded & nonblocking | **CONDITIONAL:** quotas/TTL/cleanup/EDT targets đã đặt | Stress, 5 GUI cycles, latency p95, worker/socket cleanup |
| IV. Security/privacy MUST | **CONDITIONAL:** TLS pin, owner-signed ACL, DPAPI metadata, RAM chat | Independent security/code review, cross-user DPAPI, MITM/replay, packet/log audit |
| V. Test-backed MUST | **PLANNED ONLY:** tests cho success, negative, timeout, three peer, multi-host | Actual JDK21 commands, exits, reports, two-PC evidence |
| VI. Truthful UI | **PASS design:** per-recipient status, owner offline, no Demo events in Live | GUI manual accessibility/focus/viewport checks |
| VII. Spec-driven | **PASS design:** Spec CL confirmed and this Plan has ADRs, gates, roadmap mapping | Approved contracts/tasks, reviewers, honest README/roadmap/acceptance |

**Gate 0 verdict: CONDITIONAL / NOT INDEPENDENTLY SIGNED OFF.** Người dùng đã phê duyệt tiếp tục **code/test prototype** ngày 22/11; điều này **không** thay thế security review T004/T010. Đã implement domain, signing, strict group codec, protected metadata và standalone V3 preface/test vectors nhưng **không enable Group Live user-data network**. Independent reviewer cần duyệt version negotiation, signature/owner ticket race, DPAPI và budgets trước release.

**Gate 1 verdict: BLOCKED / NOT VERIFIED.** M1/M2 còn Gate 1; đã có Group core prototype/test local nhưng **chưa có end-to-end Group Live, 3-peer LAN, independent audit, full quota/GUI test hay reviewer**. Mốc M3 tổng còn cần **Spec 005 History** theo ROADMAP.

## 12. Implementation Layout — proposed only

**Tình trạng thực thi (2026-11-22):** [tasks.md](tasks.md) **20/92 (8 docs + 12 code/test Group lõi; 72 mở)**. Đã có [research](research.md), [data model](data-model.md), [Group V1 contract](contracts/group-v1.md), [Constitution check](checklists/constitution-check.md), [requirements](checklists/requirements.md), [quickstart NOT RUN](quickstart.md) và [local evidence/blocker report](evidence/implementation-blocker-2026-11-22.md). **Independent security Gate 0 T004/T010 vẫn OPEN**; người dùng phê duyệt code prototype có giới hạn, đã có [28 Group JUnit PASS và full 141/141 Java21 regression](evidence/group-core-implementation-2026-11-22.md), **chưa có Live Group network/UI và acceptance**.

**Source classes dự kiến (CHƯA TẠO):** domain/GroupIdentity, GroupMembership, GroupText; application/GroupAuthority, GroupChatService; protocol/GroupV1Codec, GroupV1Message; security/ProtectedGroupStore; transport/SecureSessionRouter (hoặc tương đương); listener/GroupActionPort và mở rộng LiveChatAdapter; ui/dialogs/GroupInviteDialog, GroupMembersDialog; MainChatFrame cập nhật. Thư mục nằm dưới src/main/java/vn/intrachat (trừ UI/adapter theo package hiện có).

**Test classes dự kiến (CHƯA TẠO):** GroupAuthorityTest, GroupMembershipRevisionTest, GroupCapabilityNegotiationTest, GroupV1CodecTest, ProtectedGroupStoreTest, GroupFanoutIntegrationTest, GroupRecipientReceiptTest, GroupOwnerOfflineTest, LiveGroupDemoIsolationTest, GroupForegroundReadTest. Những tên trên là gợi ý theo trách nhiệm, không cam kết class đã có.

## 13. Test strategy & acceptance matrix

**Test-first sequence tương lai:**

1. Baseline Java21 và current Git working tree, ghi lệnh/exit/regression số lượng test chính xác. Không xóa/cam kết các thay đổi uncommitted đang có.
2. RED protocol/capability: M1 v1 vẫn chặn group, legacy M2 v2 không nhận unsolicited group, malformed, unknown version, over limit, wrong UTF8/JSON, bad owner signatures. Implement new versioned dispatch only after RED evidence.
3. RED owner/membership: invite Accept/Reject/Expire, owner only, member leave, monotonic revision, old hash/duplicate/wrong SPKI, sixth member reject, simultaneous add/remove, owner outage. GREEN owner authority.
4. RED storage: metadata persist across restart under Windows DPAPI, private messages/receipts/drafts remain RAM; corrupt file/ACL/partial write fail closed; second Windows account test with authorized profile. GREEN ProtectedGroupStore.
5. RED 3-peer real TLS loopback: A→B,C; B→A,C; C→A,B with Vietnamese/emoji, nonmember D, changed key, owner-signed grant, group/P2P isolation. GREEN group service.
6. RED per-recipient ACK/READ: B ACK, C offline, B foreground visible row READ, wrong group/session/sender receipt rejected; manual retry/UNCERTAIN, no auto resend. GREEN UI states.
7. Resource/fault: 5-member group (4 links), 16 groups metadata, 128 attempts, 8MiB RAM and 65KiB frame caps, timed invite, owner offline, 5 repeated GUI cycles, EDT p95 metrics, cleanup, Demo regressions.
8. Physical LAN ≥2 **distinct** Win11 x64 PCs and ≥3 independent identities/processes, Internet unavailable but LAN reachable, consent/SAS owner/members, two-way group send, membership remove, disconnect, read and failures; log host count and actual environment.
9. Independent security/supply-chain review and M1/M2 Gate 1; sign Constitution I–VII only with evidence, update acceptance/docs; do not mark Verified earlier.

| Spec 004 AC | Evidence needed | Status 2026-11-22 |
| --- | --- | --- |
| AC-04-A | Three verified peers, group text A/B/C, correct sender/time/Unicode | NOT RUN |
| AC-04-B | Outsider/nonmember/forged identity and revocation blocked | NOT RUN |
| AC-04-C | Invite accept/reject/expire, owner revision, leave/remove, stale updates | NOT RUN |
| AC-04-D | Owner/peer disconnect, partial send/cleanup and continued P2P | NOT RUN |
| AC-04-E | Group vs P2P isolation, alias collision and Demo separation | NOT RUN |
| AC-04-F | Per-recipient ACK/foreground READ on actual Live group | NOT RUN |
| AC-04-G | Replay/duplicate/forged grant/receipt/version negative matrix | NOT RUN |
| AC-04-H | RAM-only content, metadata persist, manual retry, no auto-outbox | NOT RUN |
| AC-04-I | Group UI works without changing Demo/M1/M2 regressions | NOT RUN |
| AC-04-J | ≥3 peer identities on ≥2 real Windows LAN physical hosts, no Internet | BLOCKED — needs second host |
| AC-04-K | Java21 tests, resource/DPAPI/privacy/security review | NOT RUN / external reviewer needed |
| AC-04-L | M1+M2 Gate 1 and Group gate signed; CL confirmed and docs accurate | BLOCKED — M1/M2 not Verified |

**Future evidence paths only (không tạo PASS giả):** evidence/group-wire-tests.md, group-owner-security.md, group-dpapi-privacy.md, group-3peer-loopback.md, group-2host-lan.md, group-receipts-ui.md, group-resource-stress.md, group-acceptance-report.md. Mỗi report phải ghi command, JDK, exit, actual hosts, test totals, date, reviewer và failures.

## 14. Implementation phases / handoff

- **P0 Gate 0:** review ADR-04-03 owner ticket/availability + phiên bản protocol/capability, owner signatures/canonical bytes, DPAPI protected store, budgets; chốt contracts/group-v1.md và data-model.md.
- **P1 RED→GREEN core:** GroupIdentity/Authority/Invitation/Revision, owner-protected metadata, compatibility and negative tests; không enable Group Live cho người dùng khi chưa có security tests.
- **P2 fanout & receipts:** GroupChatService direct verified TLS sends, per-recipient ledger, foreground group read; stress/failure tests, no auto retry.
- **P3 UI integration:** LiveGroup actions vào MainChatFrame/LiveChatAdapter; giữ MockChatService Demo, no group packets in v1; test keyboard/focus and group/P2P context.
- **P4 acceptance:** three distinct JVM peers, ≥2 Windows LAN physical devices, no public Internet, adversarial/soak/privacy tests, peer key changes, owner offline and changed membership; independent Gate 1 review, M1/M2 prerequisite verification, then documentation/handoff.

**Exit/stop rules:** bất kỳ group bytes qua M1 plaintext, group send trước authorization, forged owner update được nhận, người bị remove nhận **tin mới sau owner commit và grant** trong case cần chặn, false READ, dữ liệu chat ghi disk, unbounded queue hoặc security reviewer phát hiện critical → STOP/FIX/REVIEW; không tuyên bố release.

**Blockers đã biết:** cần máy Windows thứ hai thật cho acceptance M1/M2/004; cần reviewer độc lập cho crypto/dependency/privacy; cần Windows account thứ hai để test DPAPI cross-user; thêm backlog kỹ thuật shared v1/v2 dispatch, M2 retries/UI/resource tests. [Blocker report 22/11](../003-p2p-messaging/evidence/execution-blockers-2026-11-22.md).

**Kết luận:** Spec 004 đã **CONFIRMED CL-04-01…06**; tài liệu này là **technical plan design**, không phải nguồn chứng minh Group chạy được. Không chạy JUnit Group, không chạm source, không commit, không đánh dấu M1/M2/M3 Verified trong công việc lập plan.