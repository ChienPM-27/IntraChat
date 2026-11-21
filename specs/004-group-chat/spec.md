# Feature Specification: Group Chat trên LAN — Giai đoạn 04

**Feature:** `004-group-chat` · **Branch:** `004-group-chat`
**Created:** 2026-11-21 · **Priority:** P1 · **Milestone:** M3 (Group + History; History thuộc 005)
**Status:** **IN PROGRESS — 18/124 tasks; pure Group domain Java21 implemented/tested (15 Group unit tests), network Group not implemented; NOT VERIFIED**
**Source:** [ROADMAP — Giai đoạn 04](../../ROADMAP.md) · [Constitution](../../.specify/memory/constitution.md) · [M1 spec](../002-lan-networking/spec.md) · [M2 spec](../003-p2p-messaging/spec.md) · [M2 blockers](../003-p2p-messaging/evidence/m1-blocker-at-m2.md)

## 1. Tổng quan, mục tiêu và điều kiện đầu vào

Người dùng ở cùng LAN có thể tạo **Group**, mời peer đã xác minh, tham gia/rời/loại thành viên, gửi **tin văn bản/emoji** tới **đúng các thành viên được phép**, quan sát Sender/Time/State và ứng phó khi có người offline. Không yêu cầu cloud, Internet, server công cộng, auto-discovery hay auto-reconnect.

**Ranh giới nền tảng:** Giai đoạn 02 chỉ có kênh chẩn đoán M1 plaintext, không gửi tin Group. Giai đoạn 03 thiết kế CHAT_V2 qua TLS 1.3, danh tính thiết bị ghim SPKI và SAS xác minh hai người; mỗi kết nối inbound vẫn phải có người nhận Chấp nhận/Từ chối. **Group chỉ được dùng kênh chat đã xác minh/bảo vệ**; group ID, display name, IP hoặc lời mời tự khai không phải authentication. **M1/M2 chưa được nghiệm thu hai Windows PC vật lý và review bảo mật độc lập**; viết spec không biến các gate đó thành PASS.

### 1.1 Topology đã xác nhận — owner relay (CL-04-01, CL-04-03)

**CONFIRMED:** Một peer làm **owner/coordinator** cho Group; thành viên gửi qua authenticated TLS/P2P tới owner; owner kiểm tra quyền và fan-out tới các recipient hợp lệ. Giới hạn **5 thiết bị kể cả owner**. Owner có thể đọc nội dung vì Group dùng TLS từng chặng, **không phải Group E2EE**. C chỉ thấy session TLS của owner A, nên bản tin B→A→C **MUST** có chữ ký origin B trên nội dung canonical; không tin nhãn `origin=B` do A khai.

~~~text
B -- verified TLS/P2P -- A (owner) -- verified TLS/P2P -- C/D/E
~~~

- Member có quyền **đề xuất người được mời**, chỉ owner được **duyệt**; người được mời phải tự **Đồng ý/Từ chối**, độc lập human Accept inbound.
- Owner offline và chưa handoff thành công: Group **PAUSED**, không tự bầu owner. Owner online có thể **chủ động bàn giao** qua owner-epoch/commit an toàn cho member đã đồng ý; failure/ambiguous transfer fail closed để tránh hai owner.
- **Metadata Group** (ID, tên, owner, roster, epoch/revision) **được lưu bảo vệ qua restart** bằng Windows user-local protected storage; content/draft/ACK/READ/dedup **RAM-only**. Sau restart user chủ động reconnect và xác nhận state hiện hành trước khi chat.
- **READ theo từng recipient** sau view event trong Live foreground/viewport và receipt có xác thực nguồn gốc người xem; không suy ra từ ACK/owner relay.
- Member mới chỉ nhận **tin sau join commit**; owner commit revision remove là điểm chặn **các bản tin mới** tới removed peer; bytes gửi trước đó không thể thu hồi.

Các quyết định hành vi này được **người dùng xác nhận** ngày 2026-11-21. Cách thiết kế event, chữ ký, storage/epoch và hạn mức được trình bày trong [plan](plan.md); không phải minh chứng Group đã triển khai.

### 1.2 Kế thừa ràng buộc đã xác nhận

| Nguồn | Quyết định trước đó | Áp dụng vào Group |
| --- | --- | --- |
| CL-01 | LAN-first, Windows 11 x64; History sync qua peer được phép thuộc 005 | Group chạy LAN không Internet; không tích hợp History trước 005 |
| CL-02-01/04 | IPv4:port thủ công, không tự khám phá/tự reconnect | Group không scan/broadcast hoặc tạo socket nền |
| CL-02-02 | Human Accept/Reject cho **mỗi** inbound TCP | Group invite/join **khác** với phê duyệt kết nối |
| CL-02-03 | M1 plaintext chỉ diagnostics | Cấm chuyển Group content/roster nhạy cảm qua M1 |
| CL-03-01 | First-pair SAS/fingerprint, pin khóa thiết bị | Quyền Group gắn với verified deviceId + SPKI |
| CL-03-02/04 | Nháp/tin/receipt trong RAM, retry thủ công, không offline outbox | Group không persist nội dung/nháp/receipt hoặc auto-send |
| CL-03-03 | P2P “Đã xem” chỉ nhờ viewport event + authenticated receipt | Không giả “cả nhóm đã xem” theo Group ACK |
| CL-03-05 | FE Live thật và Demo mock tách biệt | Group Live không dùng MockChatService/demo credentials |

**Trong phạm vi:** Group ID/owner/name; **member đề xuất lời mời và owner duyệt**; invite/accept/reject; membership revision/permission; **owner handoff**; join/leave/remove/disband; **protected metadata lưu qua restart**; text/emoji multi-recipient; **per-member ACK/READ**; identity/sender/time; dedup, ordering; FE Group Live và ≥3-client test.

**Ngoài phạm vi:** Group History/local DB/sync/backfill (005); File/Image/Video/Sound (006/007); gọi thoại/video; cloud/Internet relay; public join link, tự khám phá/kết nối; E2EE group-key độc lập, đa coordinator/owner auto-election. **Group READ per member đã trong phạm vi bắt buộc**.

## 2. Clarification Decisions — USER CONFIRMED 2026-11-21 (8/8)

| ID | Quyết định được xác nhận | Hệ quả bắt buộc |
| --- | --- | --- |
| **CL-04-01** | **Owner relay** | Peer tạo nhóm điều phối/fan-out qua TLS/P2P đã xác minh, owner phải online. Không cloud/full mesh auto. |
| **CL-04-02** | **Thành viên mời, owner duyệt** | Member chỉ **đề xuất lời mời**, owner phê duyệt, invitee chủ động đồng ý. Không tự add khi owner chưa duyệt. |
| **CL-04-03** | **Chuyển quyền chủ động** | Owner online bàn giao cho member đủ điều kiện đã đồng ý, có epoch/commit fail closed trước khi rời. Không tự bầu. |
| **CL-04-04** | **Lưu metadata** | Bảo vệ bền vững groupId/tên/owner/roster/epoch/revision; **không** lưu chat text/draft/ACK/READ/outbox. |
| **CL-04-05** | **5 thiết bị tối đa** | Owner + tối đa 4 member. Cần kiểm thử quota M2: 4 active TLS peer cho owner. |
| **CL-04-06** | **Thêm READ từng người** | READ chỉ sau Live foreground viewport + authenticated/signed viewer receipt; UI từng recipient hoặc x/y thật. |
| **CL-04-07** | **Chỉ tin mới** | Sau khi join commit mới có quyền nhận; không backfill, History sync thuộc 005. |
| **CL-04-08** | **Owner commit remove** | Ngay owner commit revision remove, không fan-out **tin mới** cho người bị loại; không thu hồi frame cũ. |

**Precedence:** Các quyết định này **thay thế toàn bộ baseline PENDING**. Member invite proposal, handoff, protected metadata và authenticated per-member READ là **bắt buộc**, không phải optional. Các chi tiết HOW do [plan](plan.md), [research](research.md), [data model](data-model.md) và [Group wire contract](contracts/group-v1.md) mô tả; không đánh dấu Group Verified nếu chưa qua test/Gate 1.

## 3. User Scenarios & Testing (mandatory)

### US1 — Tạo và nhận diện Group (P1)

**Giá trị:** Người dùng tạo nhóm riêng biệt với P2P và không nhầm tên nhóm/peer.
**Independent test:** A tạo G; B và C trùng displayName nhưng khác verifiedId; tạo hai Group trùng tên; chuyển giữa G/P2P.

1. **Given** A đang ở Live, **When** tạo “Dự án 🚀”, **Then** Group G có UUID duy nhất, owner A và trạng thái roster ban đầu; chưa có thành viên khác chỉ nhờ nhập tên.
2. **Given** B/C cùng tên nhưng khác deviceId/pinned key, **When** xem danh sách, **Then** vẫn là hai peer khác nhau.
3. **Given** hai Group G/H cùng tên, **When** chọn và gửi, **Then** groupId/roster/conversation RAM không bị gộp.
4. **Given** tên rỗng, toàn whitespace, quá dài hoặc Unicode không hợp lệ, **When** tạo/đổi tên, **Then** từ chối an toàn và giải thích rõ.
5. **Given** user đang đọc P2P với B, **When** tin Group G tới, **Then** chỉ G đổi nội dung/thông báo, không chèn vào P2P.

### US2 — Mời, đồng ý/từ chối và join an toàn (P1)

**Giá trị:** Không bị tự thêm vào Group hoặc lộ nội dung cho peer giả.
**Independent test:** A owner mời B/C trên TLS verified; B Accept, C Reject; thử D tự biết groupId và lời mời replay.

1. **Given** A có Group G, B là peer đã ghim khóa, **When** A mời B, **Then** B thấy owner/Group ID/tên/roster được phép và nút **Đồng ý/Từ chối**; B chưa là member trước khi đồng ý.
2. **Given** B đồng ý trong thời hạn, **When** owner xác nhận membership revision hợp lệ và B nhận snapshot, **Then** A/B nhìn đúng B trong roster, B mới có quyền gửi/nhận Group.
3. **Given** C từ chối, hết hạn hoặc đóng dialog, **When** xử lý, **Then** C không gia nhập/không nhận Group message; không tự retry invite.
4. **Given** X biết groupId nhưng không có verified key, hoặc mạo danh B/owner, **When** X JOIN/INVITE, **Then** không cấp quyền, không lộ content/roster.
5. **Given** invite cũ bị replay hay dùng với deviceId khác, **When** validate, **Then** không tạo member/revision trùng.
6. **Given** chưa có kênh M2 TLS đã xác minh hoặc người nhận từ chối inbound, **When** gửi lời mời, **Then** không gửi thông tin Group qua kênh M1 plaintext.
7. **Given** B đã join G, **When** B đề xuất mời D nhưng A owner chưa duyệt/từ chối, **Then** D không nhận Group invite/roster và chưa là member; khi A duyệt, D vẫn cần chủ động chấp thuận trước khi owner commit join.

### US3 — Nhắn tin Group tới đúng tập thành viên (P1)

**Giá trị:** Một thao tác gửi tới các thành viên đã được phép, đúng sender/time/emoji.
**Independent test:** 3 JVM thật A/B/C; B→A→C; D có P2P với A nhưng không thuộc Group; kiểm tra 0 frame Group tới D.

1. **Given** A/B/C joined G và online, **When** B gửi “Chào cả nhóm 👩🏽‍💻”, **Then** A/C hiển thị mỗi nơi một bubble, đúng authenticated sender B, sentAt UTC; D không nhận gì.
2. **Given** D có phiên P2P với A nhưng không thuộc G, **When** A fan-out, **Then** D nhận **0 Group text/private roster frame**.
3. **Given** B gửi khi A đang xem Group H, **When** A nhận, **Then** chỉ G cập nhật; không suy sender bằng active screen.
4. **Given** B/C trùng displayName hoặc đổi alias, **When** gửi, **Then** senderId/nhóm không đổi.
5. **Given** tin có tiếng Việt dấu tổ hợp, emoji ZWJ, **When** đi qua coordinator, **Then** text giữ nguyên Unicode và vượt max bytes/codepoints phải bị từ chối trước khi fan-out.
6. **Given** payload giả mạo groupId/revision/origin/owner, **When** nhận, **Then** từ chối, không đưa lên UI như tin thật.

### US4 — Leave/remove/disband và hiệu lực quyền (P1)

**Giá trị:** Người rời/bị loại không nhận tin mới, member thường không thể điều khiển quyền người khác.
**Independent test:** A owner, B/C joined; A loại C; B gửi; C replay request theo revision cũ; B rời; A disband.

1. **Given** C là member G, **When** C chọn “Rời nhóm” và owner xác nhận, **Then** C mất quyền gửi/nhận Group G; roster A/B phản ánh revision mới.
2. **Given** A loại C, **When** B gửi tin mới sau khi revision đó có hiệu lực, **Then** C **không** có recipient Group mới, dù P2P A↔C vẫn có thể còn.
3. **Given** B là member, **When** B gửi đề xuất mời D, **Then** owner có thể duyệt/từ chối và D chỉ được join khi owner duyệt + D đồng ý. **When** B giả `REMOVE_MEMBER`/`DISBAND`, **Then** bị từ chối.
4. **Given** owner muốn rời, **When** owner bàn giao cho member online đã đồng ý, **Then** chỉ owner mới sau commit/epoch an toàn được tiếp tục relay; owner có thể chủ động disband thay vì handoff; P2P không bị xóa.
5. **Given** tin đã phát trước khi C bị loại, **When** bytes còn đang truyền, **Then** UI không tuyên bố có thể thu hồi tin đã gửi qua mạng.
6. **Given** C đã rời rồi tự replay INVITE/JOIN cũ, **When** xác thực, **Then** không có auto rejoin; chỉ lời mời mới theo quy trình được duyệt.

### US5 — Member/owner offline và ACK một phần (P1)

**Giá trị:** Không treo vì một peer lỗi, không hiển thị “tất cả đã nhận/xem” khi chưa có bằng chứng.
**Independent test:** A/B/C joined; ngắt C giữa fan-out; ngắt A; mất ACK; reconnect thủ công.

1. **Given** C offline và A/B online, **When** B gửi tới G, **Then** A vẫn xử lý và thông báo **C offline/chưa nhận**; B không bị block vô hạn.
2. **Given** owner hiện tại A offline và chưa handoff commit, **When** B bấm Gửi, **Then** Group PAUSED; không gửi qua M1 hoặc B↔C ngầm; nháp RAM và retry thủ công. Nếu handoff A→C đã commit, C là owner hiện hành.
3. **Given** ACK từ A hoặc B đến nhưng C chưa ACK, **When** UI hiện kết quả, **Then** trạng thái phân biệt **từng recipient**, không “cả nhóm đã nhận”.
4. **Given** ACK bị mất sau khi tin có thể đã tới, **When** timeout, **Then** hiển thị UNCERTAIN thay vì DELIVERED/FAILED chắc chắn; không tự resend.
5. **Given** user chủ động reconnect với owner bằng kênh TLS/pin hợp lệ và membership mới được chấp thuận, **When** gửi tin mới, **Then** không phát lại ngầm tin/receipt/draft cũ.
6. **Given** nhận duplicate authenticated Group message, **When** nhận lại trong cửa sổ dedup, **Then** không hiện hai bubble; ACK lại chỉ khi bản gốc đã thực sự được nhận vào RAM.
7. **Given** C thực sự xem bubble từ B trong Group G ở Live foreground/viewport đủ thời gian, **When** C phát receipt có chữ ký C và owner A relay hợp lệ tới B, **Then** chỉ trạng thái **C đã xem** của đúng tin B được nâng, không nâng người khác.
8. **Given** C chỉ xem preview, ở nền hoặc chưa cuộn tới tin, **When** B chờ, **Then** không có READ; ACK của owner/thiết bị khác không thay thế bằng chứng C đã xem.
9. **Given** A độc hại tự chế READ mang nhãn C, **When** B kiểm tra chữ ký và pinned SPKI C, **Then** không thể nâng C thành VIEWED nếu thiếu chữ ký của C.

### US6 — Live FE Group và Demo/P2P độc lập (P2)

**Giá trị:** Người dùng nhìn đúng Group, thành viên, trạng thái và sender trong giao diện Messenger Live.
**Independent test:** 3-peer Group Live, chuyển G↔H↔P2P, mở Demo độc lập, thử Group File/Media.

1. **Given** G đã tạo, **When** người dùng chọn G, **Then** thấy tên nhóm, owner, roster, trạng thái hoạt động và tin với sender/time dễ đọc.
2. **Given** B gửi G trong lúc A xem P2P hoặc H, **When** callback đến, **Then** cập nhật G, không chuyển sang chat sai.
3. **Given** G paused/chưa được phép, **When** nhập text và bấm Gửi, **Then** không phát wire message, giữ draft RAM và thông báo lý do.
4. **Given** Demo có Group/mock status, **When** chạy cùng Live, **Then** Demo không thể làm Group Live có tin/ACK/READ giả.
5. **Given** chọn File/Image/Video/Sound/History trong Group khi chưa có spec tương ứng được implement, **When** thao tác, **Then** nút bị ẩn/khóa và không sinh packet lén.
6. **Given** người dùng đóng/mở lại Live sau khi đã tạo Group, **When** mở Group đã lưu metadata hợp lệ dưới cùng Windows user, **Then** groupId/tên/roster/owner vẫn có nhưng Group PAUSED cho đến khi reconnect/roster sync thủ công; tin/nháp/ACK/READ cũ không xuất hiện.
7. **Given** metadata bị hỏng/DPAPI không giải mã được hoặc ACL sai, **When** tải Group, **Then** fail closed, báo lỗi có thể hiểu và không tự tạo nhóm/owner giả hay lưu nội dung rõ trên đĩa.

## 4. Edge cases và abuse cases bắt buộc

- Hai group cùng tên/roster và hai peer cùng nickname; thay displayName/IP không đổi quyền hoặc groupId.
- Invite hết hạn, replay, người dùng từ chối, owner giả, member giả, UUID/groupId bị đoán, key pinned thay đổi/revoked.
- Concurrent JOIN/REMOVE và fan-out, revision sai/thụt lùi, member nhận snapshot cũ, owner chết giữa commit.
- Tin cùng messageId nhưng khác payload/time/origin; duplicate ACK, ACK đến sau khi sender rời nhóm, receipt từ sai peer/session/group.
- 2 hay 3 peer gửi gần như đồng thời; đồng hồ lệch giờ và timezone/DST; không hứa thứ tự nhân quả toàn cục.
- Owner offline, ngủ máy, đổi mạng/IP, C slowloris/half-open, socket đóng giữa frame; UI EDT vẫn phản hồi và tài nguyên phải được dọn.
- Frame vượt hạn, UTF-8 sai/escaped surrogate đơn, JSON duplicate/extra field, type/version lạ, group roster quá quota; từ chối trước khi cấp phát bộ nhớ không giới hạn.
- C bị remove sau khi A đã gửi một frame: không thể thu hồi dữ liệu đã phát; membership mới **ngăn các bản tin mới**.
- C mới join **không** có quyền đọc tin trước khi join; không tự backfill từ Group History khi chưa triển khai 005.
- Group state/roster/status không lẫn P2P, không tự dùng demo login/password làm authentication; không log chat text, SAS, private key hay invitation sensitive.
- M1 đang chạy diagnostics không bao giờ chấp nhận `GROUP_TEXT`; v2 legacy-only không tự “nâng” thành Group nếu không thương lượng version/capability có kiểm tra.
- Sau restart **metadata Group được giải mã và đọc lại** từ store bảo vệ, nhưng tin/draft/receipt/dedup mất (không History). Group cần user reconnect và owner-state resync thủ công trước khi gửi.

## 5. Functional Requirements (mandatory)

### 5.1 Identity, ownership và Group membership

- **FR-04-001:** Live **MUST** tạo Group có groupId UUID duy nhất, tên hiển thị, owner **verified deviceId**, trạng thái và roster tách khỏi P2P conversation.
- **FR-04-002:** Membership **MUST** gắn với deviceId + fingerprint/SPKI đã xác minh; tên/IP/Group ID tự khai **MUST NOT** tạo trust hoặc quyền.
- **FR-04-003:** Mọi Group event/content **MUST** qua authenticated encrypted peer session đã consent theo M1/M2; raw M1 diagnostic **MUST NOT** truyền chat hay roster nhạy cảm.
- **FR-04-004:** Member **MAY đề xuất lời mời**, nhưng chỉ owner hiện hành **MUST phê duyệt** lời mời; owner kiểm tra quyền rename/remove/disband/**handoff** theo pinned identity + ownerEpoch, không theo label FE.
- **FR-04-005:** Sau owner approval, người được mời **MUST** chủ động đồng ý/từ chối invitation **riêng**; consent Group không thay human Accept mỗi TCP inbound.
- **FR-04-006:** Invitation **MUST** có correlation ID, groupId, owner ID, invitee verified ID, thời hạn, nonce/challenge và chống replay/forward.
- **FR-04-007:** Group membership **MUST** có epoch/revision và quy tắc xác thực, commit/apply/duplicate/stale rejection rõ ràng.
- **FR-04-008:** JOIN/LEAVE/REMOVE/DISBAND/**HANDOFF** **MUST** cập nhật roster/ownerEpoch/quyền nhất quán; member chỉ có thể **đề xuất** invite, không tự add/remove hoặc chuyển owner.
- **FR-04-009:** Peer removed/left **MUST NOT** được chọn làm recipient cho Group text **mới** sau revision có hiệu lực; không hứa thu hồi bytes đã truyền.
- **FR-04-010:** Kết thúc Group hoặc mất quyền **MUST NOT** tự thu hồi pinned peer key, xóa P2P conversation hoặc tự khóa toàn bộ transport M2.
- **FR-04-011:** Group state (created/inviting/active/paused/transferring/disbanded), member online state và delivery/read state **MUST** độc lập; tối đa **5 thiết bị** trong một Group.

### 5.2 Group wire message, routing và ordering

- **FR-04-012:** Group wire envelope **MUST** versioned, mang messageId, type, groupId, membership revision, authenticated origin senderId, destination kind GROUP, sentAt UTC, payload và binding đúng transport session/hop.
- **FR-04-013:** Người phát Group **MUST** xác định recipients từ snapshot membership đã được phê duyệt ở revision hợp lệ; không lấy danh sách trực tiếp từ UI hoặc IP khả dụng.
- **FR-04-014:** Mỗi hop **MUST** xác minh peer TLS/pin; Group text và receipts **MUST** dùng chữ ký origin/recipient P-256 trên canonical content và xác minh dựa vào **pinned SPKI độc lập của người gửi/người xem ở từng người nhận**. Nếu chưa xác minh pin thành viên chéo, chưa được hiển thị như sender verified. Relay không được giả member/viewer.
- **FR-04-015:** Text/emoji Unicode **MUST** bảo toàn UTF-8 (bao gồm tiếng Việt/emoji tổ hợp), có giới hạn bytes và code points, từ chối blank/invalid Unicode.
- **FR-04-016:** Trong cửa sổ dedup per session, cùng groupId/origin/messageId/content **MUST** không tạo bubble trùng; reuse ID với payload khác **MUST** bị từ chối.
- **FR-04-017:** Hệ thống **MUST** xác định order ít nhất theo thứ tự coordinator chấp nhận/fan-out hoặc một quy tắc rõ ràng được plan chốt; **MUST NOT** hứa total order xuyên session/restart.
- **FR-04-018:** **Owner durable commit** là điểm hiệu lực revision; từ commit REMOVE, owner **MUST NOT** fan-out tin mới tới removed peer, kể cả khi có race; frame cũ đã phát không thể thu hồi.
- **FR-04-019:** Peer cũ chỉ hỗ trợ P2P/v2 **MUST NOT** nhận Group data theo type P2P, cũng không có silent version downgrade; thương lượng capability rõ ràng.
- **FR-04-020:** Parser/transport **MUST** giới hạn length/nesting/queue, strict UTF-8/JSON, duplicate keys/unknown types/invalid UUID và **MUST NOT** dùng Java ObjectInputStream cho dữ liệu mạng.

### 5.3 ACK, failure, privacy và UX

- **FR-04-021:** Phân biệt local write, owner accepted, recipient **application ACK và READ**; READ **MUST** dựa trên view event Live foreground viewport + receipt có chữ ký verified viewer, không suy từ ACK.
- **FR-04-022:** ACK/READ **MUST** gắn đúng **recipient + groupId + originalMessageId + verified signer + ownerEpoch/revision/runId + valid session/hop**; forged/late/wrong-session ACK/READ không nâng trạng thái sai.
- **FR-04-023:** Group UI **MUST** trình bày DELIVERED/**READ per recipient** (tùy chọn x/y dựa trên receipts thật); không ghi “tất cả đã nhận/xem” khi bất kỳ recipient nào chưa có receipt hợp lệ.
- **FR-04-024:** Peer offline **MUST** không làm các recipients online bị chờ/treo vô hạn; báo partial delivery và recipient offline.
- **FR-04-025:** Offline draft/manual retry **MUST** không trở thành auto-outbox, auto-reconnect, auto-resend; UNCERTAIN không được báo thành chắc chắn FAILED/DELIVERED.
- **FR-04-026:** Owner hiện hành offline => Group **MUST** PAUSED, trừ khi đã **handoff tự nguyện có authenticated accept và durable epoch commit**; chuyển quyền lỗi/mơ hồ fail closed, không auto election hoặc split-brain.
- **FR-04-027:** TCP/TLS, fan-out, receipt, timeout và parse **MUST NOT** chặn Swing EDT; giới hạn group count/member count/active peers/pending invites/ACKs/writer bytes/worker threads.
- **FR-04-028:** Group **MUST** có cancellation, timeout, cleanup session và trạng thái lỗi được phản ánh rõ; không phát lại read/offline messages sau reconnect.
- **FR-04-029:** FE Live **MUST** hiển thị Group name, owner, verified members, sender/time và Group state; sender không suy từ alignment hoặc active conversation.
- **FR-04-030:** User **MUST** thấy lý do không thể join/send/invite do thiếu quyền, chưa xác minh, owner offline, quá quota hoặc incompatibility.
- **FR-04-031:** Metadata/roster/text/name gửi từ LAN **MUST** được validate và escape/sanitize trước UI; không dùng peer displayName làm khóa.
- **FR-04-032:** Group metadata (groupId, tên, owner, roster, ownerEpoch/revision, handoff proof) **MUST** lưu qua restart trong user-local **DPAPI CurrentUser + owner-only ACL + atomic write**, có xóa/thu hồi, corrupted-store fail closed. Content/draft/receipt/dedup **MUST** RAM-only; không History/outbox.
- **FR-04-033:** **MUST NOT** ghi private content, SAS, pinned private key, invite token, raw untrusted JSON vào routine log/Git.
- **FR-04-034:** Live Group **MUST** tách Demo/MockChatService, trạng thái mock/credential demo không được dùng xác thực hoặc ACK/READ.
- **FR-04-035:** Group File/Image/Video/Sound/History **MUST** bị khóa/ẩn khi chưa có triển khai thực ở 005–007.
- **FR-04-036:** Security tests **MUST** có giả owner/role/origin/viewer, replay invite/READ, revoked peer, wrong epoch/revision, changed key, malicious relay, handoff crash/partial commit và corrupted metadata store.
- **FR-04-037:** Test network **MUST** có ≥3 client độc lập A/B/C với kênh TLS thực, join/leave/remove, owner/member send, D non-member và offline.
- **FR-04-038:** Test acceptance **MUST** chứng minh LAN Windows 11/JDK21 không public Internet, actual multiple peer processes và kế thừa real two-PC gate M1/M2; số máy vật lý phải được nêu.
- **FR-04-039:** Regression tests **MUST** giữ an toàn M0–M2, strict version/identity, bounded resources, timeout, disconnect, EDT, false status và Group/P2P isolation.
- **FR-04-040:** **MUST NOT** báo Group Verified khi các AC-04 bắt buộc thiếu evidence, CL-04 chưa approved, M1/M2 Gate 1 chưa Verified hoặc Constitution/security review còn blocker.

## 6. Key Entities (logical, chưa phải Java classes/DB schema)

| Entity | Fields tối thiểu | Bất biến |
| --- | --- | --- |
| **Group** | groupId UUID, displayName, ownerId, createdAtUTC, state, membershipEpoch/revision | Group ID khác P2P conversation; owner ID không phải self-claim |
| **GroupMember** | groupId, verified deviceId + pinned SPKI, role, joinedRevision, leftRevision/status | Alias/IP không phải membership key; removed member không tự rejoin |
| **GroupInvitation** | invitationId, groupId, ownerId, invitee verifiedId, nonce, deadline, decision | Ràng buộc đúng recipient, consent riêng, chống replay/timeout |
| **GroupMembershipEvent** | eventId, groupId, revision, authorizedActorId, action + targetId, atUTC | Chỉ owner/capability có quyền; no stale/duplicate grant |
| **GroupMessage** | messageId, groupId, originSenderId, revision, sentAtUTC, text UTF-8, recipient snapshot reference | Không chấp nhận giả origin; chỉ recipients đã quyền |
| **RecipientDelivery** | originalMessageId, recipientId, groupId, session/attemptId, ACK/time/status | ACK per-recipient, không chuyển nhầm sang READ |
| **GroupDraft** | groupId, editorId, text, updatedAtUTC | **RAM-only (đã xác nhận)**, không auto-send |
| **GroupSessionView** | owner availability, connected peers, selected group, unread count, RAM transcript | UI state không cấp quyền membership |

**Bảo mật dữ liệu:** groupId không phải secret/capability. Cần pinned device key + membership epoch/revision. Owner relay có thể đọc Group text, nhưng không giả được origin/viewer signatures. **Không quảng cáo Group E2EE**. Group metadata được lưu bảo vệ theo Windows user; chat/draft/ACK/READ vẫn RAM-only.

**Ordering và delivery mặc định đề xuất:** owner tạo một thứ tự chấp nhận cục bộ cho Group trong phiên; trên mỗi hop dùng stream order đã được bảo vệ. Không bảo đảm total order giữa các client khi có disconnect/rejoin và không khẳng định exactly-once sau restart. Mỗi bản tin có recipient snapshot theo epoch; một member offline sinh kết quả thiếu/không rõ, không kéo người khác vào FAILED. **Không** giả mạo “đã xem bởi mọi người”.

## 7. Non-functional Requirements và Quality Gates

| Nhóm | Yêu cầu tối thiểu/điều cần thiết kế ở plan |
| --- | --- |
| **Platform** | Windows 11 x64, actual JDK 21, Swing Live UI, Maven offline-compatible; LAN không phụ thuộc Internet |
| **Identity/privacy** | TLS 1.3, pinned SPKI + first-pair SAS; consent inbound mỗi socket, group authorization riêng, không plaintext M1 |
| **Protocol** | Versioned Group capabilities, strict JSON/UTF-8, UUID + session/epoch validation, chống downgrade và replay; protocol document phải chốt trước code |
| **Limits** | Bounded frame/text, roster, invite timeout, active sessions, per-recipient ACKs, total RAM, write queues; plan phải dựa vào M2 quotas thực tế, không tự gán SLA |
| **Reliability** | Đảm bảo màn hình không freeze khi 1 peer chết, manual retry, no auto-reconnect, bounded failure timeout; no false all-delivered/read |
| **Persistence** | Group metadata **phải lưu bảo vệ qua restart**; chat/draft/ACK/READ RAM-only; không History/Sync 005 |
| **Compatibility** | M1 diagnostics còn hoạt động nhưng chỉ plaintext probe; M2 P2P vẫn đúng quyền và Sender/Time/State; Group type không len vào v1 hoặc old v2 |
| **Accessibility** | Labels rõ cho Group/owner/member, keyboard/focus states trên Swing; lỗi invite/timeout/permission hiểu được, không tự biến đổi lựa chọn active conversation |
| **Testing** | ≥3 independent clients; unit/domain/protocol; real TLS 3-process loopback; negative attacker/fault/timeout; real LAN physical-host evidence và independent security review |

**Performance budgets đã thiết kế trong [plan mục 7](plan.md#7-bounded-resources-and-performance-budgets-design-targets-not-measured) (chưa đo):** tối đa **5 thiết bị/Group**, tối đa 4 session hoạt động ở owner, giới hạn Group count/queue/RAM/receipt và deadline, max active/pending TLS, invite timeout, max roster/event size, RAM/cumulative queue limits, fan-out concurrency, ACK deadline, UI responsiveness p95, cancel/close SLA và số chu kỳ stress. M2 đã có giới hạn văn bản dự kiến ≤8.000 codepoints và ≤32KiB; việc tái sử dụng ở Group phải được xác minh bằng test trước khi công bố.

## 8. Measurable Success Criteria

- **SC-04-001:** Trong test ≥3 client đã xác minh, **100% online authorized recipients** được owner gửi tin hợp lệ thành công nhận đúng **một** bubble mỗi message; **0** outsider nhận được text.
- **SC-04-002:** Sau membership REMOVE/LEAVE đã có hiệu lực, **0 Group message mới** được lựa chọn để gửi tới thiết bị mất quyền.
- **SC-04-003:** **100%** invitation bị Reject/expire/replay/sai owner hoặc sai key **không** cấp membership.
- **SC-04-004:** **0** Group event tác động sai P2P conversation hoặc Group khác qua kiểm thử trùng tên/nhiều hội thoại.
- **SC-04-005:** **0** app-ACK/read state giả cho recipient offline, ACK giả hoặc sai session; delivery per-recipient có thể quan sát/kiểm tra.
- **SC-04-006:** Unicode Vietnamese/emoji trong các test round-trip giữ **đúng toàn bộ nội dung** qua fan-out; lỗi protocol không khiến UI dừng phản hồi.
- **SC-04-007:** Có ít nhất 1 positive/negative kiểm thử CREATE, member INVITE_PROPOSAL/owner APPROVAL, JOIN/LEAVE/REMOVE, DISBAND/**HANDOFF**, GROUP_TEXT, per-recipient ACK/**READ**, owner offline, metadata reload/corruption và duplicate/revision.
- **SC-04-008:** Automated Java 21 regressions M0/M1/M2 + Group chạy **0 lỗi**; chỉ báo số test đã thực thi, không bịa PASS.
- **SC-04-009:** Bằng chứng LAN không public Internet, human Accept/SAS, real multiple process và **số PC vật lý** được ghi rõ, không coi localhost là 2-PC gate.
- **SC-04-010:** Qua kiểm tra privacy có **0** dữ liệu chat/draft/receipt/secret bị ghi vào log hoặc storage ngoài chính sách đã chốt; không trình bày E2EE nếu chỉ TLS theo từng hop.

## 9. Acceptance Criteria (AC-04-A…L)

| ID | Test end-to-end | Điều kiện để PASS |
| --- | --- | --- |
| **AC-04-A** | A/B/C đã join; B gửi text/emoji | A và C nhận đúng sender/time, đúng một tin qua mạng thật |
| **AC-04-B** | D không thuộc roster dù đang có verified P2P; C chưa có pin độc lập của B | D không nhận Group text/roster private; C không tin nhãn verified B nếu thiếu pin B |
| **AC-04-C** | Member propose invite → owner Approve/Reject; B Accept/C Reject; remove | Phải có cả owner approval + invitee consent trước join; quyền/revision đúng, P2P không đổi |
| **AC-04-D** | C offline/timeout; A offline; A handoff tự nguyện cho B | Paused trước transfer; B active chỉ sau secure commit, không split-brain/EDT block |
| **AC-04-E** | Owner giả, invite replay, changed pin, member đã bị loại gửi lại | Bị từ chối, không cấp trust/quyền hoặc hiển thị dữ liệu trái phép |
| **AC-04-F** | Tên trùng, alias đổi, timestamp UTC/khác timezone | Group/sender đúng deviceId, không nhầm hội thoại |
| **AC-04-G** | ACK + READ hợp lệ/sai session, foreground vs preview, 1 recipient offline | READ đúng từng recipient, relay không giả viewer; không all-read giả |
| **AC-04-H** | Concurrent JOIN/REMOVE/SEND; stale revision | Snapshot/revision có thẩm quyền, không fan-out tin mới cho removed peer |
| **AC-04-I** | FE Live Group, P2P và Demo riêng | Không lẫn state/mock data; các tính năng ngoài scope bị khóa |
| **AC-04-J** | Fuzz/queue/roster cap, restart/corrupt DPAPI metadata, handoff crash | Fail closed, dữ liệu content không persist, metadata bảo vệ, EDT không treo |
| **AC-04-K** | ≥3 JVM/client và LAN Windows không Internet | Log evidence thực tế topology, actual JDK21/OS, consent, TLS, test results và physical hosts |
| **AC-04-L** | Release/prerequisite/Constitution I–VII | CL-04 đã chốt, plan/threat/contract/tasks/test/evidence review, **M1/M2 Gate 1 Verified**, không vi phạm MUST |

**Actual status thực thi 2026-11-21:** AC-04-A…L = **NOT RUN**; 8/8 CL-04 confirmed; pure Group domain classes + 15 Group unit tests **PASS**, còn Group TLS wire, member invite/handoff, metadata DPAPI, READ UI và multi-client acceptance **NOT IMPLEMENTED/NOT RUN**. Tạo spec không chứng minh tính năng đang hoạt động. Mốc **M3** còn cần History giai đoạn 05, nên Group 004 Verified **không** đồng nghĩa M3 Verified.

### 9.1 Test matrix bắt buộc trong plan/tasks

1. **Positive:** 3 JVM A/B/C, invite/join có consent, owner→B/C và B→owner→C text Unicode/emoji, per-recipient app ACK; D outsider nhận 0.
2. **Security:** fake owner/member/origin, changed key, invite replay, wrong epoch/session, known groupId outsider, removed member, downgraded raw M1.
3. **Ordering/concurrency:** cùng gửi từ B/C, revision thay đổi khi fan-out, duplicated identical ID và conflicting payload, member JOIN muộn/LEAVE trong luồng.
4. **Offline:** C mất kết nối giữa lúc gửi, owner A offline, cancel invite, ACK loss, retry thủ công, app restart không History, không âm thầm reconnect/resend.
5. **Fault/DoS:** malformed/oversize frame, duplicate JSON, invalid UTF-8, unknown type/version, partial TCP frame, slow peers, quotas/queue flood; không block EDT.
6. **UX:** Group vs P2P/Group khác/Demo, trùng alias, timezone, foreground/preview status thật, disabled future media/history.
7. **Physical:** tại tối thiểu 2 máy Win11 vật lý (M1/M2 Gate 1) và ≥3 client độc lập, LAN không Internet công cộng, nhập IPv4/port thủ công; ghi tách rõ máy vật lý với JVM. Nếu yêu cầu 3 máy vật lý phải chốt ở plan, không giả định đã chạy.

## 10. Traceability to ROADMAP

| ROADMAP item | Yêu cầu/Story/Acceptance |
| --- | --- |
| **04.01** Group ID, owner, thành viên, quyền, topology | mục 1–2, US1, FR-04-001…004/007, AC-C/H |
| **04.02** Create/join/leave + membership sync | US1/2/4, FR-04-005…011, AC-C/E/H |
| **04.03** Đúng recipient khi gửi tin | US3, FR-04-012…020, AC-A/B/E |
| **04.04** UI nhóm, roster, sender | US1/6, FR-04-029…035, AC-F/I |
| **04.05** Offline, join muộn, owner disconnect | US2/5, CL-04-03/04/07, FR-04-024…028, AC-D/G |
| **04.06** Duplicate/ordering/ACK/consistency | US3/4/5, FR-04-016…023, AC-G/H/J |
| **04.07** ≥3 client, join/leave/timeout/duplicate name | mục 9.1, FR-04-037…040, AC-A…L |

## 11. Constitution review & Next Steps

| Nguyên tắc | Kiểm tra trước khi implementation |
| --- | --- |
| I LAN-first | Topology Group được chốt, LAN không Internet, không cloud/auto-discovery |
| II Versioned contract | Group messageId/groupId/origin/membership revision, sender/UTC, peer/session/ACK |
| III Bounded/nonblocking | Quota member/invite/socket/queue/memory/timeout, cancel/close, không block EDT |
| IV Security/privacy | Verified TLS/pinning, group ACL/replay, chống leak roster và no plaintext fallback |
| V Tests backed | 3-client real transport, negative/race/fault + 2-PC prerequisite evidence |
| VI Honest UX | Per-recipient status, không fake all-delivered/read, không mock Live |
| VII Spec-driven | CL-04 xác nhận, viết plan/contract/data-model/tasks, chỉ Verified khi AC có evidence |

**Bước tiếp theo:** (1) 8/8 CL-04 đã xác nhận; (2) xem [plan.md](plan.md), [research](research.md), [data model](data-model.md), [wire contract](contracts/group-v1.md); (3) theo dõi [tasks.md](tasks.md) với **18/124 task có evidence; secure Group wire vẫn BLOCKED**; (4) implement sau khi M1/M2 Gate 1; (5) nghiệm thu ≥3 clients và LAN vật lý có evidence. **Không tuyên bố Group/M3 Verified khi chưa có code/test/release signoff.**
