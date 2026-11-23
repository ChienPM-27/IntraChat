> **Ghi chú phạm vi đồ án (22/11/2026):** [ROADMAP hiện hành](../../ROADMAP.md) · [11 chức năng và nghiệm thu lớp học](../../docs/course-project-scope.md) · [Tích hợp Messenger](../../docs/messenger-integration-plan.md) · [Checklist Group MVP cho môn học](course-mvp.md). **Group 28/92** (core prototype), **chưa có Group Live**; ưu tiên tạo nhóm/invite/fanout 3 peer và nối `CreateGroupDialog` vào `MainChatFrame`, không để audit owner-grant/Gate0 cũ chặn phần code. **Các yêu cầu Gate/audit bảo mật chuyên sâu ở phần lịch sử bên dưới chuyển thành nâng cao, không phải điều kiện để bắt đầu code**. Giữ an toàn tối thiểu, không tắt TLS M2, không giả mạo trạng thái hoặc ghi đè file ngoài ý muốn. **Task checkbox/biên bản Gate cũ không bị đổi thành PASS**; khi có tính năng Live thật hãy ghi test và trạng thái riêng trong [ma trận 11 chức năng](../../docs/feature-matrix.md).

# Feature Specification: Group Chat qua LAN — Giai đoạn 04 (M3)

**Feature ID:** 004-group-chat
**Created:** 2026-11-22
**Version:** 0.2.0 — đã xác nhận sáu clarification
**Status (mới nhất):** **Group core prototype 28/92, chưa có Group Live transport/UI**. Ưu tiên [Group MVP Messenger](course-mvp.md); kế hoạch bảo mật phức tạp phía dưới là phạm vi kỹ thuật cũ.
**Priority:** P1 · **Milestone:** M3 — Core Collaboration (Group; History thuộc Spec 005)
**Feature branch:** Chưa tạo; tên gợi ý 004-group-chat, không khẳng định Git branch hiện tồn tại.
**Nguồn:** [ROADMAP giai đoạn 04](../../ROADMAP.md) · [PRODUCT](../../PRODUCT.md) · [Constitution](../../.specify/memory/constitution.md) · [M1 Spec](../002-lan-networking/spec.md) · [M2 Spec](../003-p2p-messaging/spec.md) · [M2 acceptance](../003-p2p-messaging/evidence/m2-acceptance-report.md)
**Tình trạng tiền đề (22/11/2026):** M0 **Verified 41/41**; M1 **In Progress 37/60**; M2 **In Progress 34/90**; kết quả regression **113/113 JUnit local PASS** nhưng **chưa** có hai máy Windows LAN vật lý và review bảo mật độc lập. Việc soạn spec 004 **không** thay đổi trạng thái nghiệm thu các mốc trước.

## 1. Tổng quan, mục tiêu và phạm vi

Người dùng IntraChat trên các thiết bị Windows được kết nối qua **mạng LAN**, không yêu cầu Internet hoặc cloud, có thể tạo cuộc trò chuyện nhóm, mời những **thiết bị đã được xác minh**, chấp nhận/từ chối tham gia, gửi/nhận **text và emoji** cho đúng thành viên, theo dõi trạng thái phân phối **theo từng người nhận**, và xem/điều chỉnh membership theo quyền được phê duyệt. UI tiếp tục dùng Messenger Live hiện có; Demo được giữ riêng.

**Mục tiêu tối thiểu có thể nghiệm thu:** **3 peer/tiến trình có định danh xác minh khác nhau** (A, B, C) trao đổi văn bản nhóm theo cả ba hướng trên các kết nối LAN được xác thực, không gửi nội dung đến D ngoài nhóm, không làm mất/đổi ngữ cảnh các cuộc chat P2P đang mở; thành viên vào/rời nhóm và mất kết nối được xử lý trung thực.

**Ranh giới kỹ thuật:** M1 v1 plaintext/unverified **chỉ dành cho diagnostics**, không được dùng chuyển bất kỳ dữ liệu nhóm. M2 v2 hiện cung cấp kênh TLS 1.3 và sender identity đã pairing cho **P2P text**; Group phải có **contract/version và authorization riêng**, không được bật chỉ bằng cách dùng lại nhãn nhóm trong Demo. Spec không tuyên bố M2 đã qua security audit.

### 1.1. Hiện trạng có trong source — không đánh đồng Demo với Group Live

| Đã tồn tại trong working tree | Hệ quả với 004 |
| --- | --- |
| model.mock.MockChatService.createGroup, ui.dialogs.CreateGroupDialog, model.Conversation, ui.frames.MainChatFrame | **Group Demo có UI**, chưa chứng minh được gửi nội dung cho peer thật. Có thể tái sử dụng giao diện sau khi mở rộng hợp đồng Live một cách có kiểm thử. |
| listener.LiveChatAdapter.createGroup trả UnsupportedOperationException; MainChatFrame ẩn nút Group ở Live | **Chưa có tạo nhóm Live**. Không được coi Demo group, seeded recipients hoặc simulated states là dữ liệu LAN thật. |
| application.LiveChatService, SecureChatCoordinator, transport.TlsChatSession, protocol.ChatV2Codec | Có nền v2 bảo vệ văn bản P2P cục bộ, **chưa có group membership protocol, fanout, group authorization hay group ACK aggregation**. |
| CL-03-01…05 tại Spec 003 | Kế thừa **so mã SAS/fingerprint, human inbound Accept, trust pin, RAM-only chat/draft, manual reconnect/retry, read theo foreground viewport, Demo/Live isolation**. |
| ROADMAP 004, 005, 006, 007 | **Group text/emoji** là 004; History bền vững & đồng bộ là 005; File là 006; Image/Video/Sound là 007. |

### 1.2. In scope

- Khởi tạo group với tên, group ID độc lập với P2P conversation ID, danh tính người tạo và danh sách thiết bị tham gia.
- Lời mời tham gia cho **peer đã verified**, người nhận tự quyết định; xem danh sách thành viên và khả năng/điều kiện gửi.
- Thêm thành viên, rời nhóm, xóa/thu hồi quyền theo cơ chế quản trị đã chốt; đồng bộ thay đổi membership **trong phiên đang kết nối**.
- Gửi/nhận văn bản UTF-8 và emoji giữa **ít nhất ba peer** thông qua các link đã xác minh; tách group và P2P rõ ràng.
- Phân phối đến **đúng tập người nhận hợp lệ**, có ACK ứng dụng và READ dựa trên sự kiện hiển thị thực **theo từng thiết bị/nhận**; xử lý partial success, offline, retry thủ công, duplicate và out-of-order.
- Các giới hạn tài nguyên, test giao thức và kiểm thử trên LAN thực không Internet; bảo mật quyền thành viên và tránh rò rỉ log/chat.

### 1.3. Out of scope

- Tự động tìm thiết bị, server/cloud, Internet routing, bắt buộc group coordinator trung tâm, auto-connect/auto-resend.
- Tải/chuyển tiếp nội dung nhóm qua M1 plaintext hoặc gửi đến device chưa verified, chưa đồng ý tham gia.
- Lưu bền vững nội dung nhóm, lịch sử đồng bộ sau restart, durable offline queue; là 005 hoặc spec riêng. **CL-04-05:** chỉ **metadata nhóm được lưu bền vững, bảo vệ bởi Windows**; chat/transcript/draft/receipt vẫn RAM-only, không History Sync.
- Nhóm gửi File/Image/Video/Sound, screen sharing, voice/video call; các mục này thuộc 006/007 hoặc spec riêng.
- Quyền quản trị phức tạp, federation/role nhiều cấp, nhóm liên mạng/VLAN xuyên Internet, group broadcast công khai.
- Không tự tuyên bố giao thức E2EE nhóm, group forward secrecy hay post-compromise security: **đã chốt fanout trực tiếp** qua từng liên kết TLS đã xác minh; không có relay trung tâm.

## 2. User Stories & Acceptance Scenarios *(mandatory)*

### US1 — Tạo nhóm và mời thành viên có xác minh (P1)

Là A, tôi muốn tạo nhóm “Đồ án” với B và C đã được tôi xác minh để biết chính xác ai được phép nhận nội dung.

**Independent test:** A có ít nhất hai verified peer; tạo nhóm, gửi invite, B/C Accept, tất cả nhận đúng group ID và membership revision; D không được thêm nếu chưa pairing/consent.

**Scenarios:**
1. **Given** A, B, C đã ghép cặp bằng cơ chế M2, **When** A tạo nhóm và mời B/C, **Then** cả hai nhận lời mời với tên nhóm, người mời và danh tính đã xác minh; sau **Accept** của từng người, nhóm hiển thị membership đúng theo revision hợp lệ.
2. **Given** C từ chối, timeout hoặc chưa verified, **When** A hoàn tất thao tác, **Then** C **không** được coi là thành viên và không nhận nội dung; UI thể hiện invite pending/rejected/expired trung thực.
3. **Given** B và C cùng display name hoặc có peer đổi alias, **When** người dùng xem membership, **Then** mỗi người vẫn phân biệt được bằng verified device identity/trust details, không trộn người.
4. **Given** group title rỗng/quá giới hạn hoặc thành viên trùng ID, **When** A nhấn Tạo, **Then** UI báo lỗi, không gửi lời mời hay tạo group partial có thể nhận tin.

### US2 — Gửi và nhận văn bản nhóm đúng người, đúng ngữ cảnh (P1)

Là thành viên, tôi muốn gửi tin đến A/B/C và nhìn rõ sender/time mà không làm lẫn hội thoại P2P.

**Independent test:** Ba JVM peer với danh tính thật, mỗi peer gửi ít nhất một tin tiếng Việt có emoji; cả các thành viên được phép nhận chính xác một item theo group ID; D ngoài nhóm không nhận.

**Scenarios:**
1. **Given** A/B/C là thành viên đã commit của revision đang hiệu lực, **When** A gửi “Họp lúc 9h 😊”, **Then** B và C đang kết nối nhận đúng Unicode, sender ID, UTC time, group ID, message ID; A không hiện tin giả từ server hoặc Demo.
2. **Given** A mở group và đồng thời có chat P2P với B, **When** B trả lời group, **Then** tin chỉ hiện ở group; không nhập nhầm vào P2P, kể cả trùng display name.
3. **Given** D biết groupId nhưng không là thành viên, **When** D gửi tin hoặc giả mạo metadata A/B, **Then** app từ chối trước khi hiển thị/ACK và không fanout dữ liệu của D.
4. **Given** một peer bị disconnect trong lúc A gửi, **When** những peer còn lại hoạt động, **Then** kết quả phân phối của từng người phản ánh đúng trạng thái; không “Đã nhận toàn nhóm” giả và không làm treo các chat khác.

### US3 — Xem và cập nhật thành viên, quyền tham gia (P1)

Là thành viên, tôi muốn biết nhóm gồm những ai, rời nhóm và thấy các thay đổi quyền tác động đúng đến việc gửi/nhận tiếp theo.

**Independent test:** Invited→Accepted; thêm D; B rời; creator/role được phép remove C; stale revision/replay bị từ chối; peer bị loại không nhận bản tin **sau thời điểm thu hồi có hiệu lực**.

**Scenarios:**
1. **Given** B là thành viên, **When** B chọn Rời nhóm, **Then** membership update được phát tới các peer hợp lệ; B không được tiếp tục gửi/nhận tin nhóm mới sau khi membership change có hiệu lực theo commit rule.
2. **Given** A có quyền quản trị theo chính sách đã phê duyệt, **When** A mời/thêm D, **Then** chỉ sau D **verified + chấp nhận** và **owner commit revision monotonic**, D mới được nhận tin; không tự đồng bộ tin nhóm trước khi D tham gia.
3. **Given** C bị thu hồi membership, **When** A/B gửi tin mới, **Then** không phân phối cho C. Việc thu hồi **không** xóa được bản sao tin mà C đã nhận từ trước; UI không cam kết xóa dữ liệu từ máy C.
4. **Given** hai cập nhật membership đồng thời hoặc đến đảo thứ tự, **When** các peer xử lý, **Then** revision/epoch không lùi, không hợp nhất sai tập nhận; phiên không thống nhất quyền phải ngăn gửi vào tập không xác định.

### US4 — Thể hiện trạng thái phân phối và lỗi trung thực (P1)

Là người gửi nhóm, tôi muốn biết người nhận nào đã nhận/xem tin và được thông báo rõ trường hợp chỉ gửi thành công một phần.

**Independent test:** B ACK nhưng chưa view, C offline; B hiển thị ở foreground rồi gửi authenticated READ; C không tự gửi lại khi online; sai ID/peer/session/revision không nâng trạng thái.

**Scenarios:**
1. **Given** A gửi cho B/C, **When** B application-ACK còn C chưa ACK, **Then** trạng thái không mặc nhiên là toàn nhóm đã nhận; hiển thị **theo người nhận** hoặc số lượng phản ánh đúng.
2. **Given** B chỉ nhận frame ở background, **When** A xem chi tiết, **Then** B không được đánh dấu READ; chỉ có read receipt đã xác thực sau hiển thị row trong foreground viewport của group mới nâng trạng thái cho B.
3. **Given** C offline hoặc ACK bị mất, **When** A xem gửi thất bại/uncertain, **Then** UI giải thích không rõ kết quả, không tự retry; cho phép thao tác **Gửi lại thủ công** khi người dùng đã reconnect và đủ quyền.
4. **Given** duplicate tin / ACK / READ, **When** peer nhận lại trong phạm vi dedup được hỗ trợ, **Then** không xuất hiện bubble nhân đôi hoặc trạng thái bị nâng sai; giới hạn exactly-once xuyên restart không được hứa.

### US5 — Nhóm hoạt động khi có disconnect hoặc coordinator/creator offline (P2)

Là thành viên, tôi muốn app không treo nếu một thiết bị hoặc người tạo nhóm mất mạng và biết chức năng nào còn dùng được theo topology đã chọn.

**Independent test:** 3 process local và peer Windows thực; force-close B / mất connection / owner offline; quan sát lỗi, socket cleanup, peer còn lại hoạt động hoặc dừng có lý do đúng với mô hình phân phối đã phê duyệt.

**Scenarios:**
1. **Given** A/B/C có nhóm đang chat, **When** B disconnect, **Then** C và A không bị treo; gửi tới B hiển thị fail/uncertain, gửi tới C theo connectivity/role còn hợp lệ.
2. **Given** owner offline, **When** B/C gửi group text hoặc muốn đổi membership, **Then** không cấp được ticket owner cho tin mới nên **Group send tạm dừng** và không thể commit membership; các chat P2P riêng vẫn hoạt động, không tự bầu owner/relay.
3. **Given** app đóng và khởi động lại, **When** mở Live Group, **Then** metadata (ID, tên, owner, roster, revision) khôi phục từ kho được bảo vệ, **không phục hồi tin/nháp/receipt**, và group chưa được phép gửi đến khi xác minh peer/roster lại.

### US6 — Demo và Live được cách ly, tài nguyên được bảo vệ (P2)

Là người dùng, tôi muốn group trong Messenger Live là hoạt động thật và không để chức năng chưa được hỗ trợ/thiết bị lạ làm hỏng app.

**Independent test:** Demo group hoạt động như cũ; Live mở group thật sau verified peer sessions; unknown group types/oversized membership frames và flood bị từ chối; không xuất hiện fake group delivery từ Demo.

**Scenarios:**
1. **Given** Demo có nhóm mẫu, **When** mở Live, **Then** không có seed/mock member, simulated ACK/READ hoặc demo group traffic; chỉ group được tạo qua Live và verified membership mới hiển thị.
2. **Given** Group/History/File/Media action chưa được cấp quyền, **When** chọn ở Live, **Then** hệ thống ẩn/vô hiệu hóa hoặc báo unsupported, không gửi packet lạ.
3. **Given** một peer gửi group event sai version/loại, quá hạn mức, group unknown hoặc mạo danh sender, **When** parser nhận, **Then** fail closed, báo lỗi không nhạy cảm, không in nội dung riêng ra log, không freeze EDT.

## 3. Clarification Decisions — CONFIRMED 2026-11-22

**Người dùng đã xác nhận toàn bộ CL-04-01…06 ngày 22/11/2026.** Các lựa chọn dưới đây là yêu cầu có hiệu lực với [technical plan](plan.md). Plan phải làm rõ HOW (giao thức, race, quota, lưu trữ, bảo mật), **không được tự đổi quyết định sản phẩm**.

| Mã | Quyết định **ĐÃ CHỐT** | Ràng buộc thiết kế |
| --- | --- | --- |
| **CL-04-01 — Topology** | **Direct P2P fanout** qua từng liên kết TLS đã verified, **không relay trung tâm** | Người gửi gửi riêng cho mỗi recipient; owner có thể làm authority roster nhưng **không chuyển tiếp nội dung chat** |
| **CL-04-02 — Roles** | **Một owner** quản lý thêm/xóa thành viên, thành viên tự leave | Không multi-admin, không tự bầu owner mới. Owner offline → không commit membership mới |
| **CL-04-03 — Membership** | Người được mời phải **Accept**; **owner commit revision tăng đơn điệu** | Chặn stale/forged change; validate authenticated owner + verified member identity; fail closed khi local roster không chắc chắn |
| **CL-04-04 — Receipts** | **Đã nhận / Đã xem theo từng người nhận** | Application ACK và foreground viewport READ có groupId/messageId/recipient/session binding; không suy ra mọi người đã xem |
| **CL-04-05 — Storage** | **Persist group metadata được bảo vệ**, nhưng **chat/draft/receipt vẫn RAM-only** | Group ID/owner/name/roster/revision lưu DPAPI CurrentUser/ACL/atomic; không restore nội dung hoặc tự cấp group authorization sau restart |
| **CL-04-06 — Capacity** | **Tối đa 5 thành viên bao gồm chính mình** — là mục tiêu, **chưa được kiểm chứng** | Max 4 peer khác mỗi group; không tự suy ra hiệu năng/interop từ giới hạn P2P hiện tại; reject member thứ 6 |

**Bất biến:** Group không bao giờ truyền trên M1 plaintext; tên/UUID/IP tự khai không tạo trust; phải có Accept của người được mời và commit owner; người đã bị revoke không được gửi/nhận nội dung mới **khi thay đổi có hiệu lực**. Revocation tức thời trên mọi peer trong network partition là vấn đề khó/không bảo đảm được nếu thiếu cơ chế đồng bộ quyền; technical plan phải chọn phương án fail-closed và ghi rõ giới hạn.

## 4. Requirements *(mandatory)*

### 4.1. Functional Requirements

**A. Identity, setup và membership**

- **FR-04-001:** Live MUST tạo group có **groupId độc lập**, display title, creator/authority, member verified identities, membership revision/epoch và scope riêng với P2P.
- **FR-04-002:** Chỉ những peer được xác minh và có **phiên bảo vệ/READY** theo M2 mới được mời và trao đổi group content. Transport CONNECTED, raw M1 Accept, IP, self-declared UUID/displayName **MUST NOT** cấp quyền.
- **FR-04-003:** Nhóm Live có luồng Create → Invite → Accept/Reject/Expire; một invite chưa được Accept **MUST NOT** biến thành membership active.
- **FR-04-004:** Có **duy nhất một owner** (authenticated creator device identity) có quyền invite/add/remove/rename; member chỉ được tự leave. Không multi-admin hay transfer ownership trong 004; sender và receiver MUST kiểm tra owner authorization, không dựa alias.
- **FR-04-005:** Người tham gia **MUST** xem được danh sách thành viên, trạng thái membership/connection và phân biệt các peer trùng tên/đổi alias bằng stable verified device ID.
- **FR-04-006:** Các thay đổi join/leave/remove **MUST** tạo sự kiện versioned, có revision/epoch, author và xác thực quyền; local group roster **MUST NOT** lùi hoặc nhận stale/forged event.
- **FR-04-007:** Thành viên đã rời/bị xóa hoặc chưa accepted **MUST NOT** gửi/nhận group text mới. Peer vừa tham gia **MUST NOT** tự động nhận bản ghi trước lúc join. Không bảo đảm xóa dữ liệu họ đã nhận trước đó.
- **FR-04-008:** Nếu membership không nhất quán hoặc authority mất kết nối, app **MUST** tuân thủ commit rule được chốt trong plan, không phát nhóm tới tập nhận không rõ, và thông báo lý do tạm ngừng/thất bại.
- **FR-04-009:** Group identity, membership và P2P conversation **MUST** không bị trộn kể cả khi tên nhóm/tên peer trùng nhau hay người tạo đổi displayName.

**B. Gửi/nhận tin và ngữ nghĩa protocol**

- **FR-04-010:** Có thể gửi và nhận **CHAT_GROUP_TEXT** (tên wire sẽ chốt ở plan) qua LAN tới đúng các thành viên active; phải hoạt động với **ít nhất ba peer** không cần Internet/server trung tâm.
- **FR-04-011:** Bản tin group MUST mang/binding với messageId, groupId, membership revision, sender authenticated device identity, recipient identity (hoặc contract rõ nếu fanout), session/context, timestamp UTC và Unicode payload; không dùng active conversation UI hoặc displayName làm source of truth.
- **FR-04-012:** Mọi group message, membership event và receipt **MUST** có version/type schema, framing/size limit, reject code cho trường hợp malformed/unknown/downgrade và parser **không** native Java object deserialization.
- **FR-04-013:** Nội dung nhóm **MUST** đi qua kênh TLS v2 verified/READY hoặc cơ chế bảo vệ tương đương được duyệt; **MUST NOT** dùng M1 v1 plaintext, chat chưa verified, downgrade hay trust-all TLS.
- **FR-04-014:** Peer nhận **MUST** kiểm tra sender session/cert binding, group membership (theo revision hợp lệ), quyền gửi, groupId và recipient trước khi nhập transcript/ACK. Mọi ID tự khai chưa đủ để bypass ACL.
- **FR-04-015:** Gửi tới một hoặc nhiều thành viên không thành công **MUST** dẫn tới trạng thái per-recipient đúng; không ghi “everyone delivered/read” nếu chưa có từng evidence.
- **FR-04-016:** Group message dedup **MUST** theo messageId kết hợp group/peer/session/epoch thích hợp; duplicate/reorder/replay không được tạo nội dung trùng hoặc thay đổi sender/time; giới hạn dedup xuyên restart phải công bố.
- **FR-04-017:** **Group ordering** MUST được định nghĩa rõ trong contract/plan: ít nhất thứ tự cục bộ theo một sender trong session, cách hiển thị khi clock skew và concurrent send; **MUST NOT** giả định global total order không có bằng chứng.
- **FR-04-018:** ACK “đã nhận” **MUST** là application acceptance tại recipient, không phải socket write; READ “đã xem” **MUST** là view event ở **foreground group conversation + visible row**, được gửi như authenticated receipt. Không tự áp dụng receipt P2P cho nhóm nếu thiếu group/peer correlation.
- **FR-04-019:** ACK/READ **MUST** chứa recipient identity, original messageId, groupId, relevant session/epoch và chống forgery/duplicate/out-of-order/cross-conversation; một người ACK/READ **MUST NOT** nâng trạng thái của người khác.
- **FR-04-020:** Manual reconnect/retry **MUST** tuân thủ CL-03-02: không auto-reconnect, auto-outbox, auto-resend; khi mất ACK UI **MUST** hiện trạng thái không chắc chắn và cảnh báo nguy cơ trùng trước khi người dùng gửi lại. **Group send mới** phải có ticket owner còn hiệu lực; owner offline thì tạm dừng Group send, không tự cấp phép từ roster cũ.

**C. UI, dữ liệu và vòng đời**

- **FR-04-021:** Live Messenger MUST cho phép tạo/nhận lời mời, xem thành viên, chọn group, gửi text/emoji, nhận tin và theo dõi per-recipient states; không hiển thị group như đang sẵn sàng khi chưa đủ quyền/kênh.
- **FR-04-022:** Live/Demo **MUST** cách ly dữ liệu và event; Demo Group chỉ mô phỏng, không sinh Live group packet, synthetic member hoặc synthetic receipt.
- **FR-04-023:** UI **MUST** hiển thị sender (identity tách mutable alias), readable local timezone cho UTC timestamp, member count/permission, trạng thái đang gửi/lỗi/partial delivery theo nguồn xác thực; tên trùng không được gây gửi nhầm.
- **FR-04-024:** **Group metadata** (groupId, title, owner/verified key binding, membership, committed monotonic revision) MUST persist qua restart bằng **Windows CurrentUser DPAPI, owner-only ACL, atomic writes, fail closed corruption**; metadata khôi phục không tự cấp VERIFIED/READY. **Chat content, drafts, delivery/read receipts và attempts vẫn chỉ RAM**; không log/Git hoặc sync/history ngoài Spec 005.
- **FR-04-025:** Thao tác group network/membership **MUST NOT** chặn Swing EDT; có cancel, timeout, cleanup cho fanout worker/pending invite/receipt khi disconnect, close, leave.
- **FR-04-026:** Mỗi group **MUST** có tối đa **5 thành viên tính cả local**, từ chối thành viên thứ 6. Giới hạn peer/group khác, pending invite, pending delivery, queued bytes, RAM, retry/expiry **MUST** nêu số cụ thể trong plan và kiểm thử; flood không được làm treo chat khác. Con số 5 **chưa được nghiệm thu**.
- **FR-04-027:** Nút Group/File/Media/History Live **MUST** chỉ mở khi transport/authorization tương ứng đã triển khai; không mở các packet ngoài phạm vi từ UI Demo.
- **FR-04-028:** Ranh giới chính sách thay đổi khóa/thu hồi trust **MUST** kế thừa M2: peer key changed/revoked bị chặn khỏi gửi/nhận group cho đến khi người dùng xác minh lại; không dùng cached group membership để vượt qua pin check.
- **FR-04-029:** Người dùng **MUST** được thông báo rõ rằng một thành viên được mời có thể sao chép/lưu tin họ đã xem; removal không xóa được nội dung đã rời khỏi thiết bị gửi.
- **FR-04-030:** Spec 004 **MUST NOT** được công bố Verified khi M1/M2 Gate 1 chưa qua hoặc thiếu kiểm thử multi-peer trên LAN thật, audit bảo mật và acceptance 004.

### 4.2. Non-Functional Requirements

- **NFR-04-001 — LAN Independence:** toàn bộ create/invite/chat/leave/receipt hoạt động trên cùng LAN **không cần public Internet**, cloud identity hoặc relay ngoài LAN.
- **NFR-04-002 — Authorization/Privacy:** mọi group payload được mã hóa trong transit theo thiết kế đã kiểm chứng và truy cập dựa trên **định danh được xác minh + membership + revision**; adversarial/unauthorized/cross-group frames bị chặn, kể cả stale invitation và replay.
- **NFR-04-003 — Resource budgets:** plan MUST ấn định và stress-test frame/group-size/active sockets/pending invites/receipt ledger/message RAM/cancellation, có fail bounded; không tự dùng con số M2 hiện tại làm bảo đảm group scale.
- **NFR-04-004 — Nonblocking UX:** stress group fanout không chặn Swing EDT; latency/user feedback thresholds phải ghi trong plan cùng phương pháp đo và hệ Windows mục tiêu.
- **NFR-04-005 — Fault tolerance:** peer crash/creator offline/partition không gây crash dây chuyền, duplicate committed member hoặc leaked worker; trạng thái partial/failure được thông báo thay vì silently drop.
- **NFR-04-006 — Unicode/metadata:** bảo toàn tiếng Việt/emoji, sender, UTC và timezone rendering; tests gồm trùng tên, nhiều sender đồng thời, same-ID/replay và clock skew.
- **NFR-04-007 — Security review:** review riêng group membership rights/epoch, recipient confidentiality, replay/role forgery, MITM/key changes, log/DPAPI boundary; **không** khẳng định E2EE group hoặc perfect membership convergence nếu chưa thiết kế/test.
- **NFR-04-008 — Accessibility:** UI mời/tham gia/rời nhóm/receipt và lỗi phải có nhãn, keyboard/focus, không phụ thuộc duy nhất màu; không đánh đồng “đã xem” với việc người dùng thật đã đọc.
- **NFR-04-009 — Regression:** không phá Demo, text P2P, v1 diagnostic whitelist, TLS pairing, ACK/READ hoặc clean close; mỗi thay đổi có test local trên Temurin Java 21 và quy trình xác nhận kết quả.
- **NFR-04-010 — Real-world acceptance:** tối thiểu **ba peer identity và ba tiến trình độc lập**; kiểm thử LAN thật ít nhất **hai thiết bị Windows vật lý** với ba tiến trình/định danh độc lập, ghi rõ host/process distribution. Nếu cần đánh giá multi-host latency/isolated failure của cả ba peer, plan bổ sung lần chạy **ba máy vật lý** thay vì ghi nhận thay thế bằng localhost.

## 5. Key Entities & State Model *(logical, not yet Java/wire schema)*

| Entity | Trách nhiệm, ranh giới |
| --- | --- |
| **VerifiedPeer** | deviceId + verified key/pin + mutable alias, trạng thái kênh/consent; group ACL không chỉ dựa vào alias |
| **GroupIdentity** | groupId độc lập P2P, title, owner/authority và lifecycle |
| **GroupMembership** | groupId, member verified deviceId, quyền, joined/left/revoked, committed revision/epoch |
| **GroupInvitation** | inviteId, groupId, inviter, intended invitee, expiry, membership version, ACCEPT/REJECT/PENDING |
| **MembershipEvent** | add/leave/remove/rename/role decision, author, groupId, prior/new revision, authenticated authorization |
| **GroupMessage** | messageId, groupId, revision/epoch, sender, text/emoji, sentAt UTC, delivery scope |
| **RecipientAttempt** | group messageId + recipientId + protected session, local write/accepted/read/failed/uncertain |
| **GroupReadReceipt** | original message + exact recipient + groupId + session/revision + foreground visibility evidence semantics |
| **GroupSession** | group participant list/transport health và RAM-only transcript, per-peer bounds/cleanup |
| **GroupPermission** | Một owner add/remove/rename; thành viên tự leave; không multi-admin hoặc auto-owner election |

**State model (hành vi được chốt; chi tiết giao thức xem plan):**
- Invitation: PENDING → ACCEPTED / REJECTED / EXPIRED / CANCELED; không hồi sinh invite hết hạn qua replay.
- Membership: INVITED → ACTIVE → LEFT/REMOVED; membership revision/epoch monotonic. Khi authority/chưa sync thì chuyển trạng thái **UNCONFIRMED**, không tự đoán ACTIVE.
- Recipient attempt: DRAFT → SENDING → LOCAL_SENT → APP_ACKNOWLEDGED → READ; nhánh FAILED/UNCERTAIN trung thực. **Không** nâng tổng nhóm lên READ nếu chưa có receipt của tất cả người nhận.
- Connection/trust: CONNECTED ≠ VERIFIED_READY ≠ GROUP_AUTHORIZED; một peer có thể online nhưng chưa được phép ở nhóm.
- Ordering: arrival/UTC không chứng minh tổng thứ tự. Chính sách giao tranh concurrent update và timestamp skew phải được plan giải quyết.

## 6. Edge Cases & Security Abuse Cases

- Tên nhóm rỗng/quá dài/Unicode combining; groupId trùng/ngẫu nhiên va chạm; duplicate member IDs hoặc self-invite.
- B và C trùng tên/đổi alias trong lúc online; người dùng chọn peer bằng nickname dẫn đến nhầm người.
- Invite trước khi trust verified; invite đến sau timeout; accept/reject gửi lặp; sender mất kết nối ngay sau accept.
- A gửi text đúng lúc owner xóa B; gói cũ đến muộn khi B vẫn có session; group epoch/ACL phải chặn tin **mới** cho peer đã revoke và phân loại in-flight cũ một cách nhất quán.
- Hai thao tác add/remove đồng thời; peer bị partition giữ revision cũ; owner mất mạng; không tự chọn owner/relay; split-brain và replay update.
- Một người rời nhóm khi đang gửi; chưa ACK của một người nhưng đã ACK người khác; receipt bị gắn sai user/group/session/epoch.
- Sender A gửi khi B offline và C online; retry thủ công sau reconnect, same-ID duplicate, no-outbox/no unread history; C không nhận trùng.
- Group/peer flood, frame quá lớn, unknown type, downgrade v1, malformed UTF-8/JSON, forged sender/group/recipient, rollback roster, token/identity mismatch.
- App đóng khi pending invite/receipt/long user consent; mất mạng rồi người dùng mở app lại; RAM-only dữ liệu mất không bị báo thành đồng bộ.
- UI chuyển group/P2P liên tục, bubble offscreen/notification/background; không báo READ khi content chưa hiện đúng group foreground.
- Group có một thành viên sau khi các thành viên khác leave; policies archived/disabled và UI phải được chốt, không tự phát chat nhóm một người.
- Thiết bị bị thu hồi pin/đổi khóa; group metadata cũ không bỏ qua kiểm tra tin cậy; nội dung đã hiển thị trước revoke không thể thu hồi lại bằng hệ thống.

## 7. Acceptance Criteria — AC-04-A…L

| ID | Tiêu chí nghiệm thu bắt buộc | US / FR liên quan |
| --- | --- | --- |
| **AC-04-A** | Ba peer A/B/C được verified/accepted cùng một group gửi tin **ba chiều**, mỗi recipient được phép thấy đúng text/emoji/sender/time/groupId; không cần Internet | US1–2; FR-001–003, 010–014 |
| **AC-04-B** | D ngoài nhóm/không verified không thể join bằng UUID, tự gửi/nhận group payload, kể cả giả mạo role, groupId và membership revision | US1–3,6; FR-002–008, 013–014 |
| **AC-04-C** | Invite Accept/Reject/Expire, add/leave/remove đúng quyền; thay đổi có revision, cập nhật đúng UI, người đã removed không được nhận tin **mới** | US1,3; FR-003–009 |
| **AC-04-D** | Một peer hoặc coordinator/owner offline không làm treo group/P2P khác; trạng thái lỗi/partial và cleanup đúng với topology được duyệt | US2,4–5; FR-008,015,025–026 |
| **AC-04-E** | Group message không trộn P2P; người gửi/time/alias trùng và emoji Unicode hiển thị đúng trong GUI Live, không có fake Demo items | US2,6; FR-009–012,021–023 |
| **AC-04-F** | Per-recipient app ACK/READ: ACK ≠ view; foreground viewport group read hợp lệ mới nâng đúng recipient, không ai khác; no premature aggregate read | US4; FR-015,018–019 |
| **AC-04-G** | Duplicate/replay/out-of-order/stale group revision/forged receipt/wrong session và full bad-input matrix bị từ chối an toàn; không trùng bubble hoặc nâng trạng thái sai | US2–4,6; FR-006,012,014,016–019 |
| **AC-04-H** | Peer offline: no auto resend/reconnect, lỗi/UNCERTAIN rõ ràng, manual retry có cảnh báo trùng; RAM-only chat mất sau restart, không giả history | US4–5; FR-020,024 |
| **AC-04-I** | Demo Group không bị phá; Live Group chỉ hiện khi có verified membership; File/Media/History Live vẫn chặn, không phá regression M0–M2 | US6; FR-021–024,027 |
| **AC-04-J** | Test ít nhất 3 peer độc lập, phiên LAN thật trên từ 2 Windows vật lý trở lên với IP/test manifest, các luồng thành công + Reject/leave/offline/cleanup, không có public Internet | US1–6; NFR-001,005,010 |
| **AC-04-K** | Automated Java21 tests, security/privacy/capacity/EDT/negative matrices và review của Constitution I–VII/Plan/Protocol, có lệnh/exit/evidence, chưa test không được đánh PASS | All; FR-012–030; NFR-002–009 |
| **AC-04-L** | M1 Gate 1 và M2 Gate 1 đã Verified trước M3 Verified; CL-04-01…06 đã được phê duyệt, doc/UI/README đúng phạm vi, có reviewer và release decision | All; FR-030 |

### Success Criteria — measurable outcomes (SC-04-001…010)

- **SC-04-001:** **3/3 distinct verified peer identities** có thể tham gia và gửi/nhận group text qua kết nối LAN không Internet; mỗi peer được định danh độc lập với displayName.
- **SC-04-002:** 100% **required group invitation outcomes** ACCEPT/REJECT/EXPIRE/CANCEL được test và không tạo member từ một invite không thành công.
- **SC-04-003:** 100% **specified negative authorization cases** (unknown member, removed member, forged identity, role, stale revision, cross-group) đều bị từ chối theo test suite.
- **SC-04-004:** 100% nhóm receiver active/authorized được kiểm thử nhận đúng nội dung, trong trường hợp mạng bình thường; **0** tin test được gửi tới người ngoài nhóm. Không hứa delivery khi một recipient offline.
- **SC-04-005:** Ít nhất 3/3 lượt gửi hai chiều giữa **ba sender A/B/C** trong group thành công trên testcase chuẩn, đúng sender/group/time/Unicode.
- **SC-04-006:** 100% bộ test ACK/READ bắt buộc chỉ nâng trạng thái đúng recipient/message/group/session; **0** false READ cho background/preview/offscreen/wrong group.
- **SC-04-007:** Ít nhất 5/5 chu trình group create→invite→send→disconnect→close/reopen (local JVM automation và GUI có ghi nhận) không treo app hoặc rò tài nguyên theo budget plan; không hiển thị history giả sau restart.
- **SC-04-008:** Chạy trên **≥2 Windows 11 physical hosts**, **≥3 independent peer identities/processes**, xác minh topology/no-Internet trên từng máy; nếu bộ test yêu cầu ba host vật lý phải ghi rõ thay vì dùng localhost làm thay thế.
- **SC-04-009:** Các ngưỡng latency/resource/queue/sockets/frames được **định lượng trong plan** và kiểm thử theo các ngưỡng đó; không đưa chỉ số benchmark tự đoán vào spec.
- **SC-04-010:** Có traceability cho toàn bộ **AC-04-A…L**, test pass/fail thực, lỗi còn mở và reviewer Gate 1; không đưa bản M3 Verified nếu bất kỳ gate an toàn bắt buộc chưa đạt.

## 8. Dependencies, Assumptions & Handoff

### 8.1. Tiền đề bắt buộc

1. [M1 Gate 1](../002-lan-networking/evidence/m1-acceptance-report.md) **và** [M2 Gate 1](../003-p2p-messaging/evidence/m2-acceptance-report.md) phải Verified trước **nghiệm thu M3**; hiện tại **đều chưa Verified**. Có thể viết spec/plan và phát triển code độc lập một phần nhưng không được tự bỏ qua gate.
2. Reuse verified P2P peer identity, TLS 1.3, SAS/fingerprint, human Accept/Reject, read-on-viewport và RAM-only behavior từ [Spec 003](../003-p2p-messaging/spec.md). Không dùng M1 v1 plaintext cho group.
3. Không tự suy ra Group transport từ `MockChatService`, `CreateGroupDialog`, `Conversation.group` hoặc `ChatActionHandler.createGroup`: đó là API/FE dùng lại được, **chưa có Group Live network semantics**.
4. Không phụ thuộc History Sync từ 005, File/Media 006/007, public Internet hay cloud identity. Kết nối máy trên LAN cần cấu hình Windows IP/port/Private Firewall theo chính sách được duyệt; không tự thay đổi Public Firewall.
5. **M3 milestone ROADMAP gồm 004 Group + 005 History.** Hoàn thành 004 riêng, kể cả sau test, **chưa** có nghĩa M3 tổng đã Verified nếu 005 còn mở.

### 8.2. Planning Handoff — không được bỏ qua trước task implementation

- **CL-04-01…06 đã xác nhận 22/11/2026.** Cụ thể hóa direct TLS P2P fanout không relay, một owner, invite Accept + owner commit revision monotonic, per-recipient ACK/READ, metadata persisted protected (chat RAM-only), max 5 total members. Phân tích revocation dưới network partition và fail-closed.
- [Plan 004](plan.md) đã được soạn với Constitution Check I–VII và các ADR design; cần reviewer chốt chính xác Group V1 capability transcript/interop, owner signature/ticket, groupId/epoch/nonce/certificate binding, threat model owner/invite/replay/removed peer trước khi code.
- Viết hợp đồng wire trong **contracts/** (group invite, membership change, text, ACK/READ, reject): fields, schema, max bytes, signature/auth binding, validation và failure codes; phân biệt group frame với P2P/diagnostics v1.
- Chốt data model/group frontend adapter: group ID tách P2P, nhóm max 5; group metadata persisted protected, chat/receipts RAM-only; verified member identity/permission và explicit Live UI support, Demo tách biệt.
- Đã tạo [tasks.md](tasks.md) với **92 task**, hiện **20/92 hoàn tất (8 task tài liệu + 12 task code/test lõi Group)** (72 mở); có RED→GREEN, negative tests, 3-process loopback, GUI Windows, 2+ physical hosts và Gate 0/1. **Chỉ check task docs sau khi tài liệu/QA thật hoàn tất; các task code/test yêu cầu RED/GREEN thực chạy**, không mượn kết quả M2.
- Tạo **quickstart.md** cho direct fanout, owner approval, IP/port, SAS, invite consent, manual retry, metadata persisted + chat RAM-only, max 5; công bố owner offline/revocation caveat và các giới hạn chưa test.
- Khi code xong, lập **evidence/** có kết quả thực tế, test command + Java version + exit code + test cases, hai Windows host manifest và constitution/reviewer signoff. Nếu thiếu máy B hoặc reviewer thì ghi **BLOCKED** và giữ M3 NOT VERIFIED.

## 9. Traceability — mục tiêu ROADMAP → Spec 004

| ROADMAP | User stories / FR | Acceptance |
| --- | --- | --- |
| **04.01** Mô hình group, creator, thành viên, quyền, topology | CL-04-01/02/03/05/06; US1,3; FR-001–009 | AC-A/B/C/K/L |
| **04.02** Tạo, join, leave nhóm | US1,3; FR-003–008,021 | AC-A/C/I |
| **04.03** Gửi tin đúng membership, không phát tán ngoài nhóm | US2; FR-010–017,028 | AC-A/B/E/G |
| **04.04** UI nhóm, thành viên, người gửi | US1–4,6; FR-005,009,021–023 | AC-C/E/F/I |
| **04.05** Offline, join muộn, owner offline (không relay) | CL-04-01/02/05; US3–5; FR-008,015,020,024–026 | AC-C/D/H/J |
| **04.06** Duplicate, ordering, ACK, consistency | CL-04-03/04; US2–4; FR-006,011–020 | AC-F/G/K |
| **04.07** Kiểm thử ≥3 client, tên trùng, join/leave, disconnect | US1–6; FR-025–030; NFR-001–010 | AC-A…L; SC-001…010 |

---

**Decision (2026-11-22):** Các CL-04-01…06 đã được **CONFIRMED**, Spec 004 chuyển sang clarified, có [plan.md](plan.md) và [tasks.md](tasks.md) **20/92 (core prototype)**; đã có [Group domain/codec/owner signature/DPAPI store và 28 JUnit mới](evidence/group-core-implementation-2026-11-22.md), **chưa có Group Live LAN/UI hoặc acceptance và independent security gate**. M1/M2 và M3 chưa Verified. M1/M2 vẫn In Progress/NOT VERIFIED; M3 Gate 1 BLOCKED.