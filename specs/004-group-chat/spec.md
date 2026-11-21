# Feature Specification: Group Chat trên LAN — Giai đoạn 04

**Feature:** `004-group-chat` · **Branch:** `004-group-chat`
**Created:** 2026-11-21 · **Priority:** P1 · **Milestone:** M3 (Group + History; History thuộc 005)
**Status:** **DRAFT / SPEC ONLY — chưa xác nhận quyết định Group mới; chưa triển khai/kiểm thử/Verified**
**Source:** [ROADMAP `04](../../ROADMAP.md) · [Constitution](../../.specify/memory/constitution.md) · [M1 spec](../002-lan-networking/spec.md) · [M2 spec](../003-p2p-messaging/spec.md) · [M2 blockers](../003-p2p-messaging/evidence/m1-blocker-at-m2.md)

## 1. Tổng quan, mục tiêu và điều kiện đầu vào

Người dùng ở cùng LAN có thể tạo **Group**, mời peer đã xác minh, tham gia/rời/loại thành viên, gửi **tin văn bản/emoji** tới **đúng các thành viên được phép**, quan sát Sender/Time/State và ứng phó khi có người offline. Không yêu cầu cloud, Internet, server công cộng, auto-discovery hay auto-reconnect.

**Ranh giới nền tảng:** Giai đoạn 02 chỉ có kênh chẩn đoán M1 plaintext, không gửi tin Group. Giai đoạn 03 thiết kế CHAT_V2 qua TLS 1.3, danh tính thiết bị ghim SPKI và SAS xác minh hai người; mỗi kết nối inbound vẫn phải có người nhận Chấp nhận/Từ chối. **Group chỉ được dùng kênh chat đã xác minh/bảo vệ**; group ID, display name, IP hoặc lời mời tự khai không phải authentication. **M1/M2 chưa được nghiệm thu hai Windows PC vật lý và review bảo mật độc lập**; viết spec không biến các gate đó thành PASS.

### 1.1 Topology đề xuất (chưa chốt)

**Baseline đề xuất: owner-coordinated LAN relay.** Thiết bị A tạo Group và làm owner/coordinator. B, C kết nối P2P bảo vệ riêng tới A; khi B gửi Group, A xác minh quyền và chuyển tiếp tới A (local view), C và những thành viên online còn quyền. Khi A gửi, A fan-out trực tiếp. Không cần socket B↔C; không tự động bầu owner khi A offline.

~~~text
B -- TLS/P2P đã xác minh -- A (group owner/coordinator) -- TLS/P2P đã xác minh -- C
~~~

Điều này **không** phải Group end-to-end encryption độc lập với coordinator: A có thể đọc nội dung để điều phối; từng hop được bảo vệ. Owner rời mạng => Group tạm dừng, không âm thầm chuyển sang relay khác hay kết nối lại. **Người dùng cần xác nhận `CL-04-01`** hoặc chọn sender fan-out trực tiếp có membership authority riêng trước bước `plan.md`. Dù chọn topology nào, không được gửi nhầm ngoài nhóm hoặc giả mạo membership.

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

**Trong phạm vi:** Group ID/owner/name; invite/accept/reject; membership revision/permission; join/leave/remove/disband; text/emoji multi-recipient; per-member ACK/lỗi/offline; identity/sender/time; dedup, ordering rõ ràng; FE Group Live, 3-client tests và trạng thái an toàn khi coordinator offline.

**Ngoài phạm vi:** Group History/local DB/sync/backfill (005); File/Image/Video/Sound (006/007); gọi thoại/video; cloud/Internet relay; public join link, tự khám phá/kết nối; E2EE group-key độc lập, đa coordinator/owner auto-election; trạng thái Group READ nếu chưa có receipt/viewport per member được chốt.

## 2. Clarification decisions — PENDING (chưa được người dùng phê duyệt)

| Mã | Vấn đề phải chốt | **Đề xuất mặc định** — không coi là confirmed |
| --- | --- | --- |
| **CL-04-01** | Phân phối theo owner relay hay full-mesh/direct fan-out? | **Owner relay trên các kết nối TLS/P2P đã xác minh**; owner quản lý roster và fan-out |
| **CL-04-02** | Quyền invite/remove và ai được join? | Owner mời **peer đã xác minh**; invitee **chủ động Đồng ý/Từ chối**; owner được remove/disband; không có public join |
| **CL-04-03** | Owner offline/rời nhóm? | Group PAUSED khi owner offline, owner rời = disband, không tự failover/handoff |
| **CL-04-04** | Metadata Group có tồn tại sau restart? | Group ID/name/roster + message/draft/receipts **RAM-only**; mở app lại tạo/mời Group mới; pin/identity M2 còn lưu bảo vệ |
| **CL-04-05** | Giới hạn số nhóm và thành viên? | Đề xuất Group có thể tạo với **owner duy nhất**, test tối thiểu **3 client**; **giới hạn trên đề xuất 5 thiết bị/Group kể cả owner**. Plan phải kiểm tra giới hạn tối đa 4 phiên M2 active và đo tải trước khi cam kết |
| **CL-04-06** | Trạng thái Group delivery/read? | Hiển thị **từng recipient**: đang gửi, owner accepted, app-ACK, chưa rõ/thất bại/offline; **không** hiện “tất cả đã xem” trong M3 đầu |
| **CL-04-07** | Người mới join có xem tin trước đó? | **Không**; chỉ tin phát sinh khi đã được quyền, History 005 mới thiết kế backfill + ACL |
| **CL-04-08** | Remove cạnh tranh với fan-out? | Chặn tin **mới** kể từ membership revision có hiệu lực; không thể thu hồi bytes đã gửi trước khi remove |

**Definition of Ready:** cần xác nhận `CL-04-01…06` trước khi phê duyệt `plan.md`/`tasks.md`; các giới hạn timeout/frame/queue/membership đồng thuận sẽ được plan lựa chọn và kiểm thử. Đây không phải quyết định kỹ thuật đã chốt.

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
3. **Given** B không phải owner, **When** B giả `REMOVE_MEMBER`/`DISBAND`, **Then** bị từ chối; roster và Group không đổi.
4. **Given** owner disband, **When** thông báo hợp lệ tới thành viên, **Then** Group đóng, không được gửi nữa; các hội thoại P2P không bị xóa.
5. **Given** tin đã phát trước khi C bị loại, **When** bytes còn đang truyền, **Then** UI không tuyên bố có thể thu hồi tin đã gửi qua mạng.
6. **Given** C đã rời rồi tự replay INVITE/JOIN cũ, **When** xác thực, **Then** không có auto rejoin; chỉ lời mời mới theo quy trình được duyệt.

### US5 — Member/owner offline và ACK một phần (P1)

**Giá trị:** Không treo vì một peer lỗi, không hiển thị “tất cả đã nhận/xem” khi chưa có bằng chứng.
**Independent test:** A/B/C joined; ngắt C giữa fan-out; ngắt A; mất ACK; reconnect thủ công.

1. **Given** C offline và A/B online, **When** B gửi tới G, **Then** A vẫn xử lý và thông báo **C offline/chưa nhận**; B không bị block vô hạn.
2. **Given** owner A offline, **When** B bấm Gửi, **Then** Group PAUSED; không gửi qua M1 hoặc B↔C ngầm; nháp giữ RAM và người dùng có thể thử lại thủ công.
3. **Given** ACK từ A hoặc B đến nhưng C chưa ACK, **When** UI hiện kết quả, **Then** trạng thái phân biệt **từng recipient**, không “cả nhóm đã nhận”.
4. **Given** ACK bị mất sau khi tin có thể đã tới, **When** timeout, **Then** hiển thị UNCERTAIN thay vì DELIVERED/FAILED chắc chắn; không tự resend.
5. **Given** user chủ động reconnect với owner bằng kênh TLS/pin hợp lệ và membership mới được chấp thuận, **When** gửi tin mới, **Then** không phát lại ngầm tin/receipt/draft cũ.
6. **Given** nhận duplicate authenticated Group message, **When** nhận lại trong cửa sổ dedup, **Then** không hiện hai bubble; ACK lại chỉ khi bản gốc đã thực sự được nhận vào RAM.

### US6 — Live FE Group và Demo/P2P độc lập (P2)

**Giá trị:** Người dùng nhìn đúng Group, thành viên, trạng thái và sender trong giao diện Messenger Live.
**Independent test:** 3-peer Group Live, chuyển G↔H↔P2P, mở Demo độc lập, thử Group File/Media.

1. **Given** G đã tạo, **When** người dùng chọn G, **Then** thấy tên nhóm, owner, roster, trạng thái hoạt động và tin với sender/time dễ đọc.
2. **Given** B gửi G trong lúc A xem P2P hoặc H, **When** callback đến, **Then** cập nhật G, không chuyển sang chat sai.
3. **Given** G paused/chưa được phép, **When** nhập text và bấm Gửi, **Then** không phát wire message, giữ draft RAM và thông báo lý do.
4. **Given** Demo có Group/mock status, **When** chạy cùng Live, **Then** Demo không thể làm Group Live có tin/ACK/READ giả.
5. **Given** chọn File/Image/Video/Sound/History trong Group khi chưa có spec tương ứng được implement, **When** thao tác, **Then** nút bị ẩn/khóa và không sinh packet lén.

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
- Nếu người dùng đóng app, tin/draft/receipt và group metadata (theo baseline RAM-only đề xuất) không được tự phục hồi như History.

## 5. Functional Requirements (mandatory)

### 5.1 Identity, ownership và Group membership

- **FR-04-001:** Live **MUST** tạo Group có groupId UUID duy nhất, tên hiển thị, owner **verified deviceId**, trạng thái và roster tách khỏi P2P conversation.
- **FR-04-002:** Membership **MUST** gắn với deviceId + fingerprint/SPKI đã xác minh; tên/IP/Group ID tự khai **MUST NOT** tạo trust hoặc quyền.
- **FR-04-003:** Mọi Group event/content **MUST** qua authenticated encrypted peer session đã consent theo M1/M2; raw M1 diagnostic **MUST NOT** truyền chat hay roster nhạy cảm.
- **FR-04-004:** Quyền owner/member **MUST** được kiểm tra cho create/invite/rename/remove/disband, không dựa vào label FE hoặc peer-claimed role.
- **FR-04-005:** Người được mời **MUST** chủ động đồng ý/từ chối invitation **riêng**; consent Group không thay human Accept mỗi TCP inbound.
- **FR-04-006:** Invitation **MUST** có correlation ID, groupId, owner ID, invitee verified ID, thời hạn, nonce/challenge và chống replay/forward.
- **FR-04-007:** Group membership **MUST** có epoch/revision và quy tắc xác thực, commit/apply/duplicate/stale rejection rõ ràng.
- **FR-04-008:** JOIN/LEAVE/REMOVE/DISBAND **MUST** cập nhật roster và quyền nhất quán theo authority; member thường không tự điều khiển Group.
- **FR-04-009:** Peer removed/left **MUST NOT** được chọn làm recipient cho Group text **mới** sau revision có hiệu lực; không hứa thu hồi bytes đã truyền.
- **FR-04-010:** Kết thúc Group hoặc mất quyền **MUST NOT** tự thu hồi pinned peer key, xóa P2P conversation hoặc tự khóa toàn bộ transport M2.
- **FR-04-011:** Group state (created/inviting/active/paused/disbanded), member online state và delivery state **MUST** là ba khái niệm độc lập.

### 5.2 Group wire message, routing và ordering

- **FR-04-012:** Group wire envelope **MUST** versioned, mang messageId, type, groupId, membership revision, authenticated origin senderId, destination kind GROUP, sentAt UTC, payload và binding đúng transport session/hop.
- **FR-04-013:** Người phát Group **MUST** xác định recipients từ snapshot membership đã được phê duyệt ở revision hợp lệ; không lấy danh sách trực tiếp từ UI hoặc IP khả dụng.
- **FR-04-014:** Mỗi hop **MUST** xác thực identity bên gửi socket và nguồn gốc origin sender; coordinator không được tự giả mã nguồn của một member.
- **FR-04-015:** Text/emoji Unicode **MUST** bảo toàn UTF-8 (bao gồm tiếng Việt/emoji tổ hợp), có giới hạn bytes và code points, từ chối blank/invalid Unicode.
- **FR-04-016:** Trong cửa sổ dedup per session, cùng groupId/origin/messageId/content **MUST** không tạo bubble trùng; reuse ID với payload khác **MUST** bị từ chối.
- **FR-04-017:** Hệ thống **MUST** xác định order ít nhất theo thứ tự coordinator chấp nhận/fan-out hoặc một quy tắc rõ ràng được plan chốt; **MUST NOT** hứa total order xuyên session/restart.
- **FR-04-018:** Nếu membership đổi trong lúc gửi, điểm áp dụng revision và policy snapshot recipient **MUST** xác định; không phát tin mới sau remove tới peer hết quyền.
- **FR-04-019:** Peer cũ chỉ hỗ trợ P2P/v2 **MUST NOT** nhận Group data theo type P2P, cũng không có silent version downgrade; thương lượng capability rõ ràng.
- **FR-04-020:** Parser/transport **MUST** giới hạn length/nesting/queue, strict UTF-8/JSON, duplicate keys/unknown types/invalid UUID và **MUST NOT** dùng Java ObjectInputStream cho dữ liệu mạng.

### 5.3 ACK, failure, privacy và UX

- **FR-04-021:** Phân biệt local TCP/TLS write, owner accepted và recipient **application ACK**; read chỉ có nghĩa tin được hiển thị sau event thật và receipt riêng.
- **FR-04-022:** Giao nhận **MUST** gắn đúng **recipient + groupId + originalMessageId + verified peer + session/revision**; forged/late/wrong-session ACK không nâng trạng thái sai.
- **FR-04-023:** Group UI **MUST** trình bày status **per-recipient**; không “đã nhận bởi tất cả/đã xem bởi tất cả” khi chưa có chứng cứ cho từng peer; Group READ không yêu cầu cho bản đầu.
- **FR-04-024:** Peer offline **MUST** không làm các recipients online bị chờ/treo vô hạn; báo partial delivery và recipient offline.
- **FR-04-025:** Offline draft/manual retry **MUST** không trở thành auto-outbox, auto-reconnect, auto-resend; UNCERTAIN không được báo thành chắc chắn FAILED/DELIVERED.
- **FR-04-026:** Khi owner/coordinator mất liên lạc, Group **MUST** PAUSED theo topology đã phê duyệt và không chuyển sang relay khác ngầm.
- **FR-04-027:** TCP/TLS, fan-out, receipt, timeout và parse **MUST NOT** chặn Swing EDT; giới hạn group count/member count/active peers/pending invites/ACKs/writer bytes/worker threads.
- **FR-04-028:** Group **MUST** có cancellation, timeout, cleanup session và trạng thái lỗi được phản ánh rõ; không phát lại read/offline messages sau reconnect.
- **FR-04-029:** FE Live **MUST** hiển thị Group name, owner, verified members, sender/time và Group state; sender không suy từ alignment hoặc active conversation.
- **FR-04-030:** User **MUST** thấy lý do không thể join/send/invite do thiếu quyền, chưa xác minh, owner offline, quá quota hoặc incompatibility.
- **FR-04-031:** Metadata/roster/text/name gửi từ LAN **MUST** được validate và escape/sanitize trước UI; không dùng peer displayName làm khóa.
- **FR-04-032:** Chat/draft/receipt **MUST** có chính sách lưu/xóa rõ, đề xuất **RAM-only** và không triển khai History 005; metadata Group persistence phải chốt `CL-04-04`; only protected M2 identity/pin survive.
- **FR-04-033:** **MUST NOT** ghi private content, SAS, pinned private key, invite token, raw untrusted JSON vào routine log/Git.
- **FR-04-034:** Live Group **MUST** tách Demo/MockChatService, trạng thái mock/credential demo không được dùng xác thực hoặc ACK/READ.
- **FR-04-035:** Group File/Image/Video/Sound/History **MUST** bị khóa/ẩn khi chưa có triển khai thực ở 005–007.
- **FR-04-036:** Security tests **MUST** gồm giả owner/role/origin, replay invite, revoked peer, wrong revision, key changed, spoof sender và unauthorized outside member.
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
| **GroupDraft** | groupId, editorId, text, updatedAtUTC | RAM-only đề xuất, không auto-send |
| **GroupSessionView** | owner availability, connected peers, selected group, unread count, RAM transcript | UI state không cấp quyền membership |

**Bảo mật dữ liệu:** groupId không phải secret hoặc capability. **Authorization cần cả authenticated pinned device + current membership revision**, bất kể attacker biết groupId. Group roster cũng là metadata nhạy cảm, không phát cho outsider. Owner/coordinator trong topology relay đề xuất có thể xem Group text; **không quảng cáo Group E2EE**. Private key/pinned trust M2 được lưu bảo vệ; nội dung Group không tự persist ở 004. Nếu yêu cầu giữ roster qua restart thì cần security/storage policy riêng trước code.

**Ordering và delivery mặc định đề xuất:** owner tạo một thứ tự chấp nhận cục bộ cho Group trong phiên; trên mỗi hop dùng stream order đã được bảo vệ. Không bảo đảm total order giữa các client khi có disconnect/rejoin và không khẳng định exactly-once sau restart. Mỗi bản tin có recipient snapshot theo epoch; một member offline sinh kết quả thiếu/không rõ, không kéo người khác vào FAILED. **Không** giả mạo “đã xem bởi mọi người”.

## 7. Non-functional Requirements và Quality Gates

| Nhóm | Yêu cầu tối thiểu/điều cần thiết kế ở plan |
| --- | --- |
| **Platform** | Windows 11 x64, actual JDK 21, Swing Live UI, Maven offline-compatible; LAN không phụ thuộc Internet |
| **Identity/privacy** | TLS 1.3, pinned SPKI + first-pair SAS; consent inbound mỗi socket, group authorization riêng, không plaintext M1 |
| **Protocol** | Versioned Group capabilities, strict JSON/UTF-8, UUID + session/epoch validation, chống downgrade và replay; protocol document phải chốt trước code |
| **Limits** | Bounded frame/text, roster, invite timeout, active sessions, per-recipient ACKs, total RAM, write queues; plan phải dựa vào M2 quotas thực tế, không tự gán SLA |
| **Reliability** | Đảm bảo màn hình không freeze khi 1 peer chết, manual retry, no auto-reconnect, bounded failure timeout; no false all-delivered/read |
| **Persistence** | RAM chat/receipt/draft, metadata Group chờ chốt; riêng M2 identity/trust lưu bảo vệ; không tạo SQLite/History/Sync ở 004 |
| **Compatibility** | M1 diagnostics còn hoạt động nhưng chỉ plaintext probe; M2 P2P vẫn đúng quyền và Sender/Time/State; Group type không len vào v1 hoặc old v2 |
| **Accessibility** | Labels rõ cho Group/owner/member, keyboard/focus states trên Swing; lỗi invite/timeout/permission hiểu được, không tự biến đổi lựa chọn active conversation |
| **Testing** | ≥3 independent clients; unit/domain/protocol; real TLS 3-process loopback; negative attacker/fault/timeout; real LAN physical-host evidence và independent security review |

**Performance budgets to decide in plan (không coi là cam kết hiện hữu):** số Groups/client, số thành viên/Group (đề xuất 3–5), max active/pending TLS, invite timeout, max roster/event size, RAM/cumulative queue limits, fan-out concurrency, ACK deadline, UI responsiveness p95, cancel/close SLA và số chu kỳ stress. M2 đã có giới hạn văn bản dự kiến ≤8.000 codepoints và ≤32KiB; việc tái sử dụng ở Group phải được xác minh bằng test trước khi công bố.

## 8. Measurable Success Criteria

- **SC-04-001:** Trong test ≥3 client đã xác minh, **100% online authorized recipients** được owner gửi tin hợp lệ thành công nhận đúng **một** bubble mỗi message; **0** outsider nhận được text.
- **SC-04-002:** Sau membership REMOVE/LEAVE đã có hiệu lực, **0 Group message mới** được lựa chọn để gửi tới thiết bị mất quyền.
- **SC-04-003:** **100%** invitation bị Reject/expire/replay/sai owner hoặc sai key **không** cấp membership.
- **SC-04-004:** **0** Group event tác động sai P2P conversation hoặc Group khác qua kiểm thử trùng tên/nhiều hội thoại.
- **SC-04-005:** **0** app-ACK/read state giả cho recipient offline, ACK giả hoặc sai session; delivery per-recipient có thể quan sát/kiểm tra.
- **SC-04-006:** Unicode Vietnamese/emoji trong các test round-trip giữ **đúng toàn bộ nội dung** qua fan-out; lỗi protocol không khiến UI dừng phản hồi.
- **SC-04-007:** Có ít nhất 1 positive và 1 negative kiểm thử cho CREATE, INVITE, JOIN, LEAVE, REMOVE, DISBAND, GROUP_TEXT, ACK, owner offline và duplicate/revision.
- **SC-04-008:** Automated Java 21 regressions M0/M1/M2 + Group chạy **0 lỗi**; chỉ báo số test đã thực thi, không bịa PASS.
- **SC-04-009:** Bằng chứng LAN không public Internet, human Accept/SAS, real multiple process và **số PC vật lý** được ghi rõ, không coi localhost là 2-PC gate.
- **SC-04-010:** Qua kiểm tra privacy có **0** dữ liệu chat/draft/receipt/secret bị ghi vào log hoặc storage ngoài chính sách đã chốt; không trình bày E2EE nếu chỉ TLS theo từng hop.

## 9. Acceptance Criteria (AC-04-A…L)

| ID | Test end-to-end | Điều kiện để PASS |
| --- | --- | --- |
| **AC-04-A** | A/B/C đã join; B gửi text/emoji | A và C nhận đúng sender/time, đúng một tin qua mạng thật |
| **AC-04-B** | D không thuộc roster dù đang có verified P2P | D không nhận Group content/roster private |
| **AC-04-C** | Invite B Accept; C Reject; B leave; owner remove C | Membership revision và quyền đúng; P2P không bị ảnh hưởng |
| **AC-04-D** | C offline/timeout và owner A offline | Group còn đúng UI state, không treo EDT/cascade failure, không auto fallback |
| **AC-04-E** | Owner giả, invite replay, changed pin, member đã bị loại gửi lại | Bị từ chối, không cấp trust/quyền hoặc hiển thị dữ liệu trái phép |
| **AC-04-F** | Tên trùng, alias đổi, timestamp UTC/khác timezone | Group/sender đúng deviceId, không nhầm hội thoại |
| **AC-04-G** | ACK sai/trễ/duplicate, 1 recipient offline | Status per recipient, không all-delivered/read giả |
| **AC-04-H** | Concurrent JOIN/REMOVE/SEND; stale revision | Snapshot/revision có thẩm quyền, không fan-out tin mới cho removed peer |
| **AC-04-I** | FE Live Group, P2P và Demo riêng | Không lẫn state/mock data; các tính năng ngoài scope bị khóa |
| **AC-04-J** | Fuzz UTF-8/JSON/frame, half-open, queue/roster cap, close | Fail closed, bounded resources và UI phản hồi |
| **AC-04-K** | ≥3 JVM/client và LAN Windows không Internet | Log evidence thực tế topology, actual JDK21/OS, consent, TLS, test results và physical hosts |
| **AC-04-L** | Release/prerequisite/Constitution I–VII | CL-04 đã chốt, plan/threat/contract/tasks/test/evidence review, **M1/M2 Gate 1 Verified**, không vi phạm MUST |

**Actual status ngày viết:** AC-04-A…L = **NOT RUN**; Group chưa có mã nguồn triển khai/test riêng. Tạo spec không chứng minh tính năng đang hoạt động. Mốc **M3** còn cần History giai đoạn 05, nên Group 004 Verified **không** đồng nghĩa M3 Verified.

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
| **04.01** Group ID, owner, thành viên, quyền, topology | `1–2, US1, FR-04-001…004/007, AC-C/H |
| **04.02** Create/join/leave + membership sync | US1/2/4, FR-04-005…011, AC-C/E/H |
| **04.03** Đúng recipient khi gửi tin | US3, FR-04-012…020, AC-A/B/E |
| **04.04** UI nhóm, roster, sender | US1/6, FR-04-029…035, AC-F/I |
| **04.05** Offline, join muộn, owner disconnect | US2/5, CL-04-03/04/07, FR-04-024…028, AC-D/G |
| **04.06** Duplicate/ordering/ACK/consistency | US3/4/5, FR-04-016…023, AC-G/H/J |
| **04.07** ≥3 client, join/leave/timeout/duplicate name | `9.1, FR-04-037…040, AC-A…L |

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

**Bước tiếp theo:** (1) xác nhận `CL-04-01…06`; (2) viết `plan.md` với topology chính thức, Group wire contract/ACL/epoch và resource budgets; (3) tạo `tasks.md` chia RED→GREEN; (4) implement sau khi đáp ứng security/prerequisite M2; (5) nghiệm thu ≥3 clients và LAN vật lý có bằng chứng độc lập. **Không tuyên bố Group/M3 Verified khi chưa có code/test/release signoff.**
