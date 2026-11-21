# Feature Specification — Giai đoạn 05: Chat History & LAN P2P Sync

**Feature:** `005-chat-history` · **Branch:** `005-chat-history`
**Created:** 2026-11-21 · **Priority:** P1 · **Milestone:** M3 = Group 004 **và** History 005
**Status:** **PARTIALLY IMPLEMENTED — 22/136 tasks completed; 10/10 CL-05 confirmed; Java21 138/138 JUnit PASS (122 baseline + 16 new History domain/policy). Secure SQLite/wire/Group E2E BLOCKED; 005/M3 NOT VERIFIED.**
**Design:** [Confirmed Clarifications](clarifications.md) · [Implementation plan](plan.md) · [136 implementation tasks](tasks.md) · [Research](research.md) · [Data model](data-model.md) · [History V1 contract](contracts/history-v1.md) · [Test quickstart](quickstart.md)
**Sources:** [ROADMAP Giai đoạn 05](../../ROADMAP.md) · [Constitution](../../.specify/memory/constitution.md) · [Foundation CL-01-04](../001-project-foundation/spec.md) · [M2 secure P2P](../003-p2p-messaging/spec.md) · [Group 004](../004-group-chat/spec.md) · [M2 release blockers](../003-p2p-messaging/evidence/m1-blocker-at-m2.md)

## 1. Tổng quan, mục tiêu và ranh giới

**Mục tiêu sản phẩm:** Người dùng IntraChat Live có thể **lưu lịch sử** các tin nhắn văn bản/emoji của các hội thoại P2P và Group mà họ thực sự có quyền, **mở lại và xem sau khi tắt ứng dụng**, và **đối soát/đồng bộ những sự kiện còn thiếu** với các peer được phép khi kết nối LAN được tái lập. History liên kết **Sender, Time, State, conversationId/groupId, nguồn xác thực và quyền truy cập**; không cần cloud, Internet, máy chủ trung tâm hay tự khám phá/kết nối.

**Đã xác nhận từ M0 (CL-01-04):** dữ liệu History có bản sao cục bộ **tại các thiết bị được phép** và sync peer-to-peer trên LAN khi chúng kết nối; trạng thái hướng tới **eventual consistency** cho những bản sao đang có quyền và có thể liên lạc, không bảo đảm peer offline được đồng bộ tức thì. Một `deviceId` khác **không** mặc nhiên là cùng một người/tài khoản; biết ID/IP/tên không cấp quyền History.

**Các quyết định ràng buộc từ M1/M2/004:**

| Nguồn | Cam kết hiện hữu | Quy tắc History 005 |
| --- | --- | --- |
| M1 CL-02-01…04 | IPv4:port thủ công, mỗi kết nối inbound cần human Accept/Reject, không auto reconnect; raw v1 không xác thực, chỉ diagnostics | **Cấm** tải/đồng bộ History qua M1 plaintext; History sync chỉ khi kênh M2 TLS và pin đã xác minh. Sync không tự mở kết nối nền |
| M2 CL-03-01 | TLS 1.3, verified deviceId/SPKI + first-pair SAS/human acceptance | Mọi sync request, sender và source event phải gắn với **định danh đã xác minh**; peer có phiên P2P không đương nhiên có quyền vào mọi conversation |
| M2 CL-03-02/04 | Message/draft/receipt của Phase 03 RAM-only, retry thủ công; protected identity/trust riêng | **005 chủ đích mở rộng** bằng việc lưu những sự kiện được phép **kể từ khi tính năng History được bật**; không hứa phục hồi các phiên M2 đã mất, không tự gửi bản nháp/outbox |
| M2 CL-03-03 | DELIVERED từ application ACK; READ từ actual Live foreground viewport + authenticated receipt | Record lịch sử **không được tự phát sinh ACK/READ**; trạng thái lưu/sync không được suy ra từ việc xem lại nội dung |
| M2 CL-03-05 | Messenger FE Live thật, Demo mock biệt lập | History Live chỉ xuất phát từ sự kiện thật đã được verify; không đưa dữ liệu MockChatService/demo login vào lịch sử hoặc wire |
| Group CL-04-01/03 | Owner relay; chỉ owner hiện hành có quyền phân phối, có handoff epoch an toàn | Group sync phải tuân owner hiện hành + epoch/revision/proof; owner offline Group PAUSED, **không tự chọn relay History khác** |
| Group CL-04-02/05 | Invite cần owner duyệt và invitee đồng ý; tối đa 5 thiết bị gồm owner | History không cho người mới quyền xem tin cũ chỉ vì đã được owner mời; không mở một network pool riêng phá giới hạn 4 M2 sessions |
| Group CL-04-04/06 | Metadata Group được DPAPI-protect; message/draft/ACK/READ ở 004 RAM-only; READ ký bởi viewer sau viewport | 005 phải phân biệt **protected Group metadata** sẵn có với **History content store** mới; không tự tạo READ qua load/sync |
| Group **CL-04-07** | **Join muộn chỉ được nhận tin sau khi JOIN được owner commit**; không backfill trong 004 | **Ngay cả sau khi 005 hỗ trợ History, không cấp quyền backfill trước JOIN** cho member mới nếu chưa có quyết định mới được xác nhận |
| Group **CL-04-08** | REMOVE có hiệu lực với **tin mới** tại owner durable commit; không thu hồi bytes đã gửi | Sau REMOVE, ngăn **mọi yêu cầu cấp/đồng bộ thêm History** cho peer đã bị loại; không tuyên bố xóa được bản sao đã đến máy peer trước đó |

**Giới hạn thực tế ngày viết:** Branch 004 mới có pure Group domain và 15 Group unit tests, **Group wire/UI chưa triển khai**. M1/M2 chưa có two-physical-PC Gate 1 và independent security signoff. Vì thế 005 có thể được thiết kế/test từng phần, nhưng **không thể khẳng định Group History Sync, M3 hay security Verified** chỉ vì tài liệu này tồn tại.

### 1.1 Trong phạm vi

1. Lưu lịch sử văn bản/emoji ở **user-local protected store** cho P2P và Group Live theo quyền hiện hành; bảo toàn định danh source, canonical content, UTC, trạng thái nguồn và lifecycle.
2. Liệt kê hội thoại, tìm theo thời gian/phân trang, hiển thị sender/time, ranh giới phiên, thứ tự ổn định và phân biệt dữ liệu lịch sử với tin Live đang đến.
3. Bền vững qua restart: schema/version/migration, crash-safe writes, corrupted store, bounded indexes/retention/size, xóa/tombstone và chính sách quyền riêng tư.
4. **LAN P2P History Sync** giữa các thiết bị đã được xác minh **và được phép cho đúng hội thoại**, bằng summary→delta→validate→durable apply→resume (có retry/dedup, không cloud).
5. P2P history sau verified reconnect; Group History chỉ với current owner/eligible member tại epoch+membership revision hợp lệ và mốc quyền JOIN/REMOVE; signed provenance cho sender/Group events và receipt evidence khi được quyết định lưu.
6. Live UI minh bạch và test Java21 multi-peer, negative/replay/revoke, offline, storage corruption, restart, no leak, bounded resources, M1/M2/004 regressions.

### 1.2 Ngoài phạm vi

- Tự động discovery, reconnect, background socket, cloud account, Internet relay, centralized history server hoặc tự động cùng danh tính user trên thiết bị thứ hai.
- Tự đọc lại các tin đã mất do M2/004 trước ngày bật History; khôi phục dữ liệu đã xóa bền vững khi không có bản sao được phép; toàn cục exactly-once/strict clock ordering khi offline.
- Tự gửi bản nháp, tin nhắn đang chờ hoặc READ receipts sau reboot, trừ khi người dùng mở rộng scope ở Clarify (mặc định **không**).
- File attachment/image/video/sound content, thumbnail, binary/history media (006/007); tìm kiếm máy chủ/public indexing; audit log nội dung chat mặc định.
- “Remote wipe” chắc chắn nội dung mà peer khác đã sở hữu; Group E2EE đối với relay owner (không có ở 004); auto-election của coordinator; chạy History qua v1 plaintext.
- Sáp nhập Demo mock vào dữ liệu Live, tự tạo tin hoặc tự tạo bằng chứng delivered/read để “làm đầy” lịch sử.

## 2. Clarification Decisions — **USER CONFIRMED 2026-11-21 (10/10; phương án A)**

**Precedence:** Các quyết định dưới đây **ràng buộc sản phẩm và thay thế toàn bộ gợi ý A/B trong bản draft trước**. Không cần hỏi lại trước khi xây dựng tasks; các thuật toán crypto, native driver và benchmark vẫn phải được review/test độc lập. Xem [biên bản xác nhận](clarifications.md) và [tác động kiến trúc](checklists/architecture-impact-review.md).

| ID | Quyết định đã chốt | Hệ quả bắt buộc |
| --- | --- | --- |
| **CL-05-01** | **Opt-in History lần đầu, chỉ lưu tin mới** | Mặc định DISABLED, yêu cầu người dùng đồng ý trước khi ghi nội dung; không tự khôi phục M2 RAM cũ |
| **CL-05-02** | **SQLite mã hóa toàn bộ + DPAPI CurrentUser + owner-only ACL, qua review** | Cấm SQLite plaintext/kho phụ WAL/temp plaintext, không fallback thiếu mã hóa; cần xét duyệt driver/native/crypto thực tế |
| **CL-05-03** | **Retention 90 ngày hoặc tổng dung lượng 500 MiB mặc định, cho phép điều chỉnh** | Hai ngưỡng tuổi/dung lượng đều phải được giám sát; chạm một ngưỡng phải xử lý an toàn theo policy, cảnh báo, không xoá tombstone gây hồi sinh |
| **CL-05-04** | **Bấm Đồng bộ thủ công cho từng hội thoại** | Không auto reconnect/auto sync, mỗi lần yêu cầu cần M2 verified READY + History capability + exact scope ACL |
| **CL-05-05** | **P2P sync chỉ giữa hai verified device IDs trong hội thoại** | Không tự liên kết nhiều thiết bị cùng user/tên và không cấp quyền khi chỉ có pin TLS ngoài conversation |
| **CL-05-06** | **Chỉ owner Group hiện hành phục vụ History trong mốc JOIN→REMOVE** | Phải kiểm owner epoch/revision, signed roster và quyền theo owner commit, không backfill pre-JOIN, không delta mới sau REMOVE |
| **CL-05-07** | **Xóa tại máy hiện tại + tombstone chống hồi sinh** | Không gửi lệnh remote-delete, không hứa xóa bản sao ở máy khác; checkpoint/tombstone phải durable |
| **CL-05-08** | **Không lưu hoặc sync application delivery ACK / READ receipts** | Chỉ lưu content/nguồn gốc cùng trạng thái History riêng SAVED/SYNCED; không restore/giả READ/DELIVERED |
| **CL-05-09** | **Immutable events, xung đột ID khác nguồn/body thì quarantine** | Không overwrite/last-write-wins; tombstone local chống reimport |
| **CL-05-10** | **Không tự backup/export; chỉ migration schema an toàn** | Không tạo bản sao nội dung lịch sử phục vụ export/backup; migration transaction/rollback không làm mất dữ liệu bền vững |

**Bảo mật vẫn phải được thẩm định:** Xác nhận CL-05-02 **không** xác nhận bất kỳ bản native SQLCipher/JDBC hoặc review độc lập nào đã PASS. Nếu chưa có SQLite driver mã hóa an toàn và test Windows ACL/DPAPI/WAL/temp thì **bảo lưu blocker**, không chọn phương án file-store thay thế hoặc plaintext một cách âm thầm. Các target hiệu năng và schema/wire canonical tiếp tục là thiết kế cần freeze/test.

## 3. User Scenarios & Testing (mandatory)

### US1 — Bật lưu và khôi phục History P2P (P1)

**Giá trị:** Tin nhắn đã được lưu hợp lệ vẫn tồn tại sau khi ứng dụng đóng/mở mà không phải phụ thuộc peer đang online.
**Independent test:** A/B trao đổi text trên M2 verified; bật History theo chính sách được duyệt, đóng Live/reboot app, load tin và đối chiếu IDs/body/time.

1. **Given** A chưa bật History hoặc chưa được thông báo chính sách, **When** A gửi/nhận P2P, **Then** không được âm thầm ghi plaintext/tệp nội dung, lịch sử tạm M2 chỉ RAM theo phase trước.
2. **Given** A đã bật History hợp lệ và kênh B đã verified, **When** A gửi/nhận “Họp 14:00 👩🏽‍💻”, **Then** chỉ tin thuộc hội thoại P2P A↔B được ghi vào store được bảo vệ, giữ **original messageId, sender deviceId, sentAt UTC, text và provenance**.
3. **Given** nội dung đã được durable-commit và A thoát/mở lại ứng dụng trên cùng Windows user, **When** mở Live History A↔B, **Then** những tin đã commit xuất hiện **một lần**, đúng dữ liệu và không đòi B phải online.
4. **Given** người gửi/nhận vừa nhấn Send nhưng write History lỗi, **When** giao diện cập nhật, **Then** không báo “đã lưu” hoặc “đã được nhận” như một sự kiện đã bền vững; nói rõ nguy cơ mất bản sao cục bộ và cho phép người dùng xử lý.
5. **Given** B đổi alias hoặc A có peer C cùng displayName, **When** mở History, **Then** A↔B/A↔C không lẫn nhau vì khóa hội thoại dựa trên verified device IDs + pin, không dựa vào tên/IP.
6. **Given** app M2 đã từng chat trước khi History được bật nhưng tin cũ đã mất sau restart, **When** bật History, **Then** không dựng lại tin đã mất, không tự sao chép Demo/mock hay tạo “lịch sử” ảo.

### US2 — Duyệt, phân trang và hiển thị theo Sender/Time/State (P1)

**Giá trị:** Có thể xem lại hội thoại dài mà UI vẫn phản hồi và thông tin không bị diễn giải sai.
**Independent test:** Nạp tập tin/sự kiện nhiều trang ở 2 conversation cùng tên + Group khác ID, timezone khác nhau, thay đổi clock nguồn, duplicate.

1. **Given** hội thoại có nhiều tin hơn một trang, **When** người dùng cuộn xem tin cũ, **Then** load từng trang theo giới hạn định trước, không nạp toàn bộ kho History vào RAM hoặc block Swing EDT.
2. **Given** hai sự kiện có cùng `sentAt` hoặc đồng hồ B đi trước A, **When** hiển thị, **Then** hệ thống dùng tie-break ổn định và cho biết timestamp từ nguồn là dữ liệu **hiển thị** chứ không chứng minh thứ tự nhân quả toàn cục.
3. **Given** tin có dấu tiếng Việt, emoji nhiều codepoint, multiline, **When** reload và render, **Then** giữ nguyên nội dung đã được xác thực và thứ tự trong hội thoại.
4. **Given** archived event có trạng thái `SENT` nhưng chưa có recipient application ACK, **When** xem lại, **Then** UI không nâng thành DELIVERED/READ chỉ vì tin tồn tại trong History hoặc được đồng bộ.
5. **Given** tin nhận được qua sync với nguồn sender không được xác minh, **When** hiển thị, **Then** không tự gắn nhãn “Verified sender”; sự kiện bị từ chối/quarantine tùy policy đã duyệt, không làm bẩn hội thoại tin cậy.
6. **Given** người dùng mở conversation khác hoặc Demo mode, **When** paginated load đang chạy, **Then** kết quả không chèn nhầm vào Live conversation khác hoặc bất kỳ Demo session nào.

### US3 — Đồng bộ P2P bị thiếu sau thời gian offline (P1)

**Giá trị:** Hai peer thực sự thuộc một cuộc hội thoại có thể trao đổi bản ghi còn thiếu mà không tạo trùng hoặc lộ sang người khác.
**Independent test:** A/B là một pair verified; A offline trong lúc B lưu một số event hợp lệ của đúng conversation; cả hai reconnect thủ công và sync theo policy; ngắt giữa chừng và retry.

1. **Given** A/B có verified P2P conversation và History active, **When** A mất mạng rồi khôi phục verified TLS session (sau mỗi human inbound Accept), **Then** hành động Sync theo lựa chọn đã chốt chỉ chạy **trên protected ready session** và trong đúng conversation.
2. **Given** B có 10 event hợp lệ A thiếu, **When** hai đầu đối chiếu summary/delta, **Then** A chỉ nhận các event còn thiếu và commit hợp lệ; đồng bộ lặp lại không tạo bản sao.
3. **Given** A có một số event B thiếu và B có event A thiếu, **When** sync hai chiều được cho phép, **Then** mỗi phía đối chiếu đúng tập event có thể chia sẻ, không dựa solely vào max timestamp để bỏ lọt sự kiện.
4. **Given** đường truyền mất giữa chunk N và N+1, **When** user xác nhận thử lại sau verified reconnect, **Then** chuyển tiếp từ cursor/checkpoint an toàn hoặc đối soát lại, không coi việc bắt đầu sync là đã hoàn tất; không auto gửi draft/outbox.
5. **Given** C biết conversationId A↔B và có TLS đã ghim với A nhưng C **không thuộc** A↔B, **When** C hỏi summary hoặc event, **Then** A không tiết lộ count, sender list, timestamp, nội dung hay thậm chí có tồn tại conversation.
6. **Given** A/B có alias trùng với peer D, **When** sync, **Then** transcript của conversation A↔B không bị gửi sang D hoặc Group.
7. **Given** peer gửi eventId trùng với body/sender khác, **When** apply, **Then** sự kiện bị quarantine/reject và trạng thái UI báo bất thường, không overwrite nguồn gốc theo last-write-wins.

### US4 — Group History theo membership epoch và thời điểm JOIN/REMOVE (P1, phụ thuộc Group 004)

**Giá trị:** Group history chỉ được lưu/đồng bộ theo **khoảng thời gian quyền tham gia**; join muộn và member bị loại không thể đọc thêm tin trái quyền.
**Independent test:** A owner, B/C là thành viên, D là invitee mới, E outsider; owner handoff A→B; test membership revision trước/sau JOIN/REMOVE trên ≥3 process.

1. **Given** A/B/C đã joined Group G hợp lệ và các tin Group có verified origin/owner relay proof, **When** bật History và tin mới được durable-commit, **Then** dữ liệu gắn `groupId, ownerEpoch, membershipRevision, originDeviceId, originalMessageId` và các event chỉ hiển thị trong G.
2. **Given** D được owner phê duyệt và JOIN tại revision r, **When** D yêu cầu History, **Then** D không nhận tin được chấp nhận **trước JOIN commit**. Lời mời, membership proposal hoặc biết Group ID không cấp quyền truy cập tin cũ.
3. **Given** owner remove C ở revision r+1, **When** C sau đó reconnect hoặc gửi Sync, **Then** owner/Group peers từ chối mọi cấp phát History mới cho C; dữ liệu C đã có trước REMOVE **không thể được hứa thu hồi từ xa**.
4. **Given** owner A bàn giao hợp lệ cho B với ownerEpoch e+1, **When** B nhận Group Sync request, **Then** B phải chứng minh owner mới qua signed A+B transfer proof và current roster; A cũ không được tiếp tục sync bằng quyền owner e.
5. **Given** owner offline hoặc không có signed current roster state, **When** member yêu cầu Group History Sync, **Then** Group sync PAUSED/disabled theo topology 004, không tự chuyển sang peer relay khác hoặc tin một self-declared owner.
6. **Given** Group 004 network/runtime chưa đạt Gate 1, **When** người dùng thử Group History, **Then** UI không gợi ý có sync hoạt động; P2P History (khi hợp lệ) vẫn tách riêng.
7. **Given** sender B đã rời/được loại nhưng những message trước khi rời là hợp lệ đối với A, **When** A xem local History, **Then** không tự xóa sự kiện trước đây chỉ vì B offline, nhưng không coi B còn quyền yêu cầu sync sau REMOVE.

### US5 — Quản lý quyền riêng tư, retention và xóa lịch sử (P1)

**Giá trị:** Người dùng biết dữ liệu gì được lưu, có thể dừng lưu/xóa và hiểu giới hạn của việc thu hồi bản sao trên các thiết bị khác.
**Independent test:** History enable/disable, quota/retention, xóa một conversation, xóa một khoảng, restart, sync lại từ B đang giữ bản sao và kiểm tra không resurrection.

1. **Given** người dùng lần đầu sử dụng History, **When** bật tính năng theo chính sách CL-05-01 được duyệt, **Then** nhìn rõ lưu trên máy nào, dữ liệu nào, ai có thể đồng bộ, retention và cách xóa.
2. **Given** người dùng ngừng lưu, **When** tin nhắn mới tới, **Then** không tiếp tục ghi vào History; những bản ghi đã lưu được giữ hay xóa theo lựa chọn đã xác nhận, không tự chuyển thành outbox.
3. **Given** người dùng xóa một conversation hoặc dữ liệu theo khoảng, **When** thao tác xác nhận thành công, **Then** UI/History local không còn hiển thị và sync tiếp không **âm thầm nhập lại** những event đã bị local delete/tombstone.
4. **Given** B đã nhận và giữ bản sao các tin của A, **When** A xóa local History, **Then** A không thể khẳng định B cũng đã xóa trừ khi có explicit remote-delete protocol/ack/guarantee được chốt. UI phải nói rõ giới hạn.
5. **Given** retention/quota sắp hết hoặc không còn disk space, **When** Store cần ghi, **Then** xử lý trong giới hạn/đúng chính sách và thông báo người dùng; không sửa/silent drop sự kiện đã commit hoặc sinh trạng thái delivered giả.
6. **Given** attacker dùng Windows user khác, sửa DB/ciphertext/WAL hoặc copy tệp, **When** mở History store, **Then** không được lộ plaintext và không tự hạ xuống storage không mã hóa; lỗi đúng mã, fail closed theo chính sách an toàn.
7. **Given** Group member bị loại nhưng từng lưu local History hợp lệ, **When** người đó xem lại dữ liệu đã sở hữu, **Then** việc giữ/xóa bản sao local tuân chính sách retention được chốt; remote không thể đảm bảo xóa dữ liệu đã nhận.

### US6 — Lỗi store, migration, trùng sự kiện và phục hồi an toàn (P1)

**Giá trị:** Storage/Sync có lỗi không làm app sập, mất dữ liệu không được che giấu hoặc tự khôi phục sai.
**Independent test:** atomic crash before/after commit, truncated DB, unknown schema, process kill giữa sync, duplicate/malformed payload và provenance mismatch.

1. **Given** storage file/ciphertext bị hỏng, sai Windows user hoặc schema không được hỗ trợ, **When** mở Live History, **Then** không crash EDT, không âm thầm xóa/hồi sinh History và báo trạng thái an toàn với hướng khôi phục theo policy.
2. **Given** app crash đúng lúc ghi hoặc migration, **When** restart, **Then** dữ liệu trước commit còn nhất quán và bản ghi chưa commit không được hiện là đã lưu; migration có rollback hoặc lỗi rõ ràng.
3. **Given** peer gửi hàng nghìn event trùng hoặc frame quá lớn/malformed/Unicode lỗi, **When** sync, **Then** từ chối/rate-limit theo quota trước khi tốn tài nguyên quá mức, không block UI và không phá những conversation khác.
4. **Given** peer chuyển event signed hợp lệ nhưng hash khác bản local có cùng stable eventId, **When** merge, **Then** xác định conflict và không cho sự kiện remote ghi đè source ID, sentAt, content hoặc tombstone của local.
5. **Given** user tắt app trong khi đang Sync, **When** khởi động lại, **Then** History giữ trạng thái last durable checkpoint, không tự kết nối hoặc tự tuyên bố SYNCED, cho phép retry thủ công theo chính sách.
6. **Given** có message chứa text Unicode và cùng tên hiển thị giữa 2 conversation, **When** migration/reload, **Then** byte nội dung và conversation key giữ nguyên, không mất sender hoặc nhập trùng.

### US7 — Tích hợp Live History với Group/P2P và trạng thái minh bạch (P2)

**Giá trị:** Người dùng biết tin nào đang live, tin nào đã lưu/sync, có thể quản lý lỗi mà không nhầm “đã lưu” thành “đã xem”.
**Independent test:** chọn Live A↔B rồi G, sync interrupted, Demo đang mở, read receipt thật của C và local History reload.

1. **Given** B đã gửi tin và local commit xong, **When** UI xem trạng thái, **Then** nhãn `Đã lưu trên thiết bị này` phân biệt với `Đã gửi/Đã nhận/Đã xem` và `Đồng bộ xong`.
2. **Given** sync nhận 5/10 events rồi đứt, **When** xem UI, **Then** hiển thị tiến độ/trạng thái partial, peer/cụm conversation đúng; không hiện SYNCED hoặc ALL READ.
3. **Given** người dùng lướt tin lịch sử cũ, **When** bubble hiện trong viewport, **Then** **không tự tạo READ receipt mới** nếu đó không phải actual live view event được cho phép theo M2/004; nếu muốn backfill READ phải có CL riêng.
4. **Given** người dùng mở Demo và Live cùng tên hội thoại, **When** xem History, **Then** dữ liệu Demo không ghi chung kho Live; không có mock ACK/READ hoặc sync traffic.
5. **Given** sync/per-page load mất nhiều thời gian, **When** người dùng scroll/chọn hội thoại/gõ, **Then** Swing EDT vẫn phản hồi và có thể hủy thao tác; không để socket/disk I/O trên EDT.

## 4. Functional Requirements (mandatory)

### A. History identity, provenance và lifecycle

- **FR-05-001:** Hệ thống **MUST** phân biệt History `conversationId` cho P2P và `groupId` cho Group; key phải gắn với verified device identity/roster thay vì tên hiển thị, IP, cửa sổ đang chọn hoặc UUID tự khai.
- **FR-05-002:** Mỗi history event **MUST** có stable globally unique `eventId/originalMessageId`, loại sự kiện, conversation/group context, authenticated originDeviceId, UTC time, canonical content/hash, source protocol version, và nguồn/proof có thể kiểm tra.
- **FR-05-003:** Nhập dữ liệu từ mạng **MUST** verify signer/origin/pinned key, phiên đã xác minh, group owner proof nếu có, context, recipient scope và schema trước khi durable write và trước khi đưa lên Live UI.
- **FR-05-004:** History bản địa **MUST** lưu P2P text/emoji hợp lệ kể từ lúc History được bật theo chính sách đã chốt; **MUST NOT** dựng lại RAM-only M2 từ các phiên không còn tồn tại, mock Demo, hoặc private chat chưa verified.
- **FR-05-005:** Group History **MUST** bảo toàn `groupId + ownerEpoch + membershipRevision + original origin + owner relay proof` cần thiết để kiểm tra quyền theo thời gian, không tự suy ra owner chỉ từ tên hoặc highest epoch packet.
- **FR-05-006:** Mỗi History event **MUST** có trạng thái durable storage/local-sync riêng với message sending, recipient APP_ACK và viewer-signed READ; `SAVED`/**SYNCED** **MUST NOT** đồng nghĩa `DELIVERED/READ`.
- **FR-05-007:** **MUST NOT** tạo application ACK/READ mới khi import, reload, scroll History hoặc nhận sync delta; theo **CL-05-08 CONFIRMED**, **không** persist, restore hay đồng bộ ACK/READ receipts (kể cả signed receipts). Chỉ actual M2/004 foreground viewer receipt ở runtime mới có authority READ.
- **FR-05-008:** History **MUST NOT** âm thầm khôi phục/truyền bản nháp, unacknowledged send attempt, private key, SAS, session token hay offline outbox; khác biệt với lưu message đã giao nhận phải hiển thị rõ.
- **FR-05-009:** `sourceMessageId`/`sourceSentAtUTC`/verified sender và content bytes của event đã commit **MUST** bất biến; đổi alias, timezone hoặc đồng hồ hệ thống không được viết lại nguồn gốc.

### B. Local persistence, migration, load và isolation

- **FR-05-010:** History store **MUST** có versioned schema, durable commit boundary, atomic write/transaction hoặc cơ chế tương đương; app crash giữa ghi không tạo false saved record hoặc phá dữ liệu đã commit.
- **FR-05-011:** Theo **CL-05-02 CONFIRMED**, History text/sender metadata **MUST** lưu trong **SQLite được mã hóa toàn bộ** bằng giải pháp đã security-review, DB key độc lập được DPAPI CurrentUser bảo vệ với owner-only Windows ACL. Toàn bộ DB/WAL/journal/temp nhạy cảm **MUST NOT** plaintext; không fallback khi thiếu encrypted driver/key/ACL; cross-Windows-user tests là release gate.
- **FR-05-012:** History **MUST** chỉ dùng application-approved user-local path, không cho peer supply đường dẫn; xử lý symlink/reparse, file permissions và truy cập khác Windows user trong test bảo mật.
- **FR-05-013:** Bản ghi History **MUST** chỉ có nội dung/loại dữ liệu thuộc tính năng đang được duyệt (P2P/Group text+emoji); media/file binary/attachments phải được từ chối hoặc biểu diễn placeholder **không chứa nội dung file** cho đến các spec 006/007.
- **FR-05-014:** App **MUST** phục hồi các bản ghi đã commit đúng conversation và stable IDs sau restart cùng user mà **không đòi peer online**; corruption/key unavailable phải fail closed và báo lỗi có thể hiểu.
- **FR-05-015:** History **MUST** hỗ trợ paginated/cursor load có giới hạn, phân tách nhiều hội thoại/Group, không đọc toàn bộ tệp lên UI hoặc làm block Swing EDT.
- **FR-05-016:** Danh sách và message timeline **MUST** hiển thị đúng verified sender, UTC source timestamp chuyển sang timezone local, nhóm ngày và ordering/tie-break ổn định, không dùng `sentAt` để khẳng định global causal order.
- **FR-05-017:** Theo **CL-05-10 CONFIRMED**, ứng dụng **MUST** chỉ hỗ trợ migration schema an toàn với rollback/crash consistency và từ chối version mới hơn; **MUST NOT** sinh backup/export tự động hoặc đưa user export/import vào scope, không tự xóa store khi lỗi.
- **FR-05-018:** History **MUST** tách khỏi GroupMetadataStore 004, M2 DeviceIdentityStore/PinnedPeerStore và Demo mock storage; xóa History không được xóa key/pin hoặc Group roster trừ khi có lệnh riêng.
- **FR-05-019:** Theo **CL-05-03 CONFIRMED**, History **MUST** có ngưỡng mặc định 90 ngày và 500 MiB tổng, có khả năng điều chỉnh trong giới hạn an toàn được plan/test; khi một ngưỡng chạm tới phải áp dụng chính sách retention/quota, cảnh báo rõ và bảo vệ tombstones/deletion fences, không silent drop bản ghi đã commit.
- **FR-05-020:** Bật/tắt History theo explicit opt-in, hiển thị retention 90 ngày + 500 MiB mặc định có thể điều chỉnh, xem thông tin lưu trữ, xóa local + tombstone và các trạng thái lỗi/saved/sync **MUST** được hiển thị rõ trong Live UI, không nâng ACK/READ.

### C. LAN P2P sync, anti-dup và trạng thái đối soát

- **FR-05-021:** **Chỉ** peer có kênh M2 verified TLS1.3/pinned SPKI, human inbound Accept và cả hai phía ở protected READY + explicit History capability mới được trao đổi bất cứ `HISTORY_SUMMARY`/`DELTA`/`APPLY_ACK` nào.
- **FR-05-022:** History sync **MUST** dùng versioned protocol riêng với type allowlist/framing/size bounds, không được tunnel qua M1 plaintext/v2 `CHAT_TEXT` hay coi một unknown Group/History type là hợp lệ.
- **FR-05-023:** Sync **MUST** giới hạn theo đúng conversation/group đã chọn và verified membership; biết `conversationId`, `groupId`, alias hay TLS pin ngoài hội thoại không tự cấp quyền. Unauthorized requests **MUST NOT** leak event count/timestamps/roster.
- **FR-05-024:** Sync engine **MUST** hỗ trợ summary/chunked delta đối soát **event thiếu/trùng**, không dựa riêng vào max timestamp/wall clock hoặc sự kiện tới cuối cùng để kết luận hai kho bằng nhau.
- **FR-05-025:** Sync **MUST** có stable event IDs + content digest dedup, chống replay và idempotent durable apply; retry cùng event không được thêm bubble trùng hoặc tăng giả counter.
- **FR-05-026:** Hai event có cùng ổn định ID nhưng khác immutable signer/payload/digest **MUST** bị đánh dấu conflict/quarantine; không last-write-wins bằng đồng hồ/alias hoặc ghi đè event cục bộ có căn cứ.
- **FR-05-027:** Sync **MUST** hỗ trợ loss/disconnect/half-open/timeout/cancel và resume/checkpoint xác minh, người dùng phải **bấm Đồng bộ thủ công cho đúng hội thoại sau verified reconnect** (CL-05-04 CONFIRMED); **MUST NOT** tự auto-sync hoặc hiện “Sync complete” trước khi các event hợp lệ được durable commit/đối soát.
- **FR-05-028:** Sync **MUST** tách việc copy History với sending/resending chat text; không tự gửi lại draft/outbox hoặc sinh ACK/READ từ event lịch sử.
- **FR-05-029:** Khi peer offline hoặc owner Group PAUSED, History Sync **MUST** dừng/hiển thị trạng thái chờ; không tự mở socket, auto reconnect, broadcast, auto relay hoặc fallback sang M1.
- **FR-05-030:** Sync **MUST** có giới hạn frame, batch, tổng event/bytes, đồng thời/chờ, timeouts, checksum/integrity và backpressure để không làm treo UI hoặc ngốn tài nguyên; con số được chốt tại plan và kiểm thử dưới dữ liệu lớn.
- **FR-05-031:** Theo **CL-05-05 CONFIRMED**, P2P History sync **MUST** chỉ giữa **hai deviceId/SPKI đã xác minh** cấu thành chính verified P2P conversation; không tự xem hai thiết bị cùng tên là một tài khoản, không cấp quyền cho thiết bị thứ ba.
- **FR-05-032:** History sync **MUST** cho phép hai peer có bản sao được phép tiến dần tới cùng tập event khi có kết nối liên tục đủ lâu, với semantic **eventual consistency** và giới hạn rõ khi offline/quota/revocation.

### D. Group access boundaries và revoke

- **FR-05-033:** Theo **CL-05-06 CONFIRMED**, **chỉ current verified owner của Group** được cấp History, và **MUST** xác thực ownerEpoch, membershipRevision, signed owner/roster/transfer proof cùng JOIN/REMOVE eligibility trước khi trả summary/delta. Không member-to-member forwarding; owner offline/không rõ quyền → PAUSED.
- **FR-05-034:** Mốc `JOIN commit` là earliest eligible event boundary của member mới: **MUST NOT** trả về event được chấp nhận trước mốc đó, kể cả owner vẫn lưu, member đã ghim khóa hoặc chủ động xin backfill.
- **FR-05-035:** Sau `REMOVE commit`, hệ thống **MUST** từ chối mọi History request mới cho member đã mất quyền, kể cả tin nhắn cũ; **MUST NOT** hứa xóa được bytes đã truyền trước revoke.
- **FR-05-036:** Owner handoff Group **MUST** đòi signed continuity proof cũ+mới và roster epoch/revision mới trước khi cấp History; owner cũ không được tiếp tục sync với authority cũ.
- **FR-05-037:** Không có Group 004 verified wire/auth hoặc không xác nhận được signed owner/roster thì Group History **MUST** bị disable/PAUSED rõ ràng; P2P History và Demo riêng không bị tự nâng đặc quyền.

### E. Deletion, retention, privacy & faults

- **FR-05-038:** Theo **CL-05-07 CONFIRMED**, xóa History **MUST** là thao tác local explicit và commit durable tombstone/deletion generation để chống silent resurrection do sync. Không phát lệnh remote delete và không hứa xóa bản sao peer đã nhận.
- **FR-05-039:** Sau local delete, app **MUST** giải thích giới hạn thu hồi remote copies: peer có thể đã lưu nội dung trước đó; không ghi “đã xóa khỏi mọi máy” nếu thiếu protocol/bằng chứng xác nhận.
- **FR-05-040:** History **MUST** áp dụng CL-05-03 **90 ngày và 500 MiB** mặc định có thể điều chỉnh theo giới hạn an toàn, giữ tombstone chống reimport và an toàn khi crash/restart; không giấu lỗi ghi hoặc tự hạ xuống plaintext.
- **FR-05-041:** Nếu store/key không mở được, bị hỏng, migration lỗi, disk full hoặc corrupted page, app **MUST** fail safely và báo lỗi, không tự reset/overwrite store, không tuyên bố persisted/synced thành công khi chưa commit.
- **FR-05-042:** Routine logs, crash/error reports và Git evidence **MUST NOT** chứa chat content, private key, SAS, secret tokens, plaintext recipient roster hay dữ liệu cá nhân; chỉ mã lỗi, counters đã được sanitize.
- **FR-05-043:** History provenance/security **MUST** phân biệt local-authored và remote-imported events, quarantine malformed/forged/cùng-ID-khác signer/hash theo **CL-05-09 CONFIRMED**, không overwrite và không cho peer sửa timeline thuộc conversation khác.
- **FR-05-044:** HISTORY Sync và history pagination **MUST** chạy ngoài Swing EDT, có cancellation/cleanup cho I/O/timer/worker, fail closed trước frame lớn/Unicode không hợp lệ và giữ các cuộc chat khác hoạt động.
- **FR-05-045:** History **MUST** có kiểm thử Windows CurrentUser protection/ACL/other user/corrupt file, user deletion/restart, cross-conversation unauthorized peer, replay/duplicate, owner handoff/revoke, resource stress và privacy leak; unavailable physical/independent review phải được đánh dấu BLOCKED trung thực.
- **FR-05-046:** Theo **CL-05-08 CONFIRMED**, History store/sync **MUST NOT** ghi, khôi phục hay đồng bộ application ACK hoặc viewer-signed READ receipts; việc mở lại History/đồng bộ **MUST NOT** tự tạo READ hoặc nâng per-recipient delivered/read. M2/004 receipts chỉ thuộc session runtime và semantics riêng.
- **FR-05-047:** Hệ thống **MUST** công bố rõ status `DISABLED / LOCAL_ONLY / SYNC_PENDING / PARTIAL / SYNCED / ERROR` hoặc tương đương; “SYNCED” chỉ khi scope/cursor được chứng minh, không có nghĩa mọi thiết bị trên LAN đã có dữ liệu.
- **FR-05-048:** Before release, History **MUST** pass Constitution I–VII, inherited M1/M2 security/physical LAN gates, Group 004 gate cho Group History, dependency/security/privacy review độc lập và AC-05 evidence; hoàn thành 005 riêng **không** tự nhận M3 Verified nếu 004 còn BLOCKED.

## 5. Edge cases & failure semantics (must not be omitted in plan)

| Tình huống | Hành vi an toàn bắt buộc |
| --- | --- |
| Người dùng nâng M2 lên 005 nhưng không còn RAM các tin cũ | Không bịa/nhập tin cũ, không dùng Demo làm nguồn Live; History có mốc bắt đầu lưu rõ |
| Same-name devices/groups, peer IP thay đổi, key rotation | Identity là authenticated deviceId + pinned SPKI; thay khóa cần flow re-pair/migration authority rõ, không gộp tự động |
| Cùng `messageId`, khác signer/byte payload | Quarantine/conflict, không overwrite hay last-write-wins bằng timestamp |
| Duplicate/history delta tới nhiều lần hoặc sync mất ACK | Apply idempotent, không tạo bản sao/READ/DELIVERED giả; outcome PARTIAL/UNCERTAIN nếu không đủ xác nhận |
| Sự kiện đến ngoài thứ tự, timestamp sai múi giờ/clock skew | Giữ original UTC/verified origin; tie-break hiển thị ổn định theo rule, không tuyên bố causal order |
| Group member JOIN tại r nhưng owner giữ tin r-1 | Group ACL filter theo eligible interval; không auto backfill tin trước JOIN (CL-04-07) |
| Group REMOVE tại r+1 nhưng member còn ciphertext/tin cũ | Dừng cấp History mới; không thể thu hồi bytes đã nhận, cảnh báo UI trung thực |
| Owner handoff bị crash sau A retired, trước B activated | Group Sync PAUSED cho tới có dual signed proof/verified current roster; không auto election |
| P2P peer C có M2 pin nhưng không thuộc conversation A↔B | Không leak summary/delta/count/metadata, kể cả đoán được conversationId |
| Dọn local History rồi reconnect với B giữ record cũ | Không auto reimport những event đã local-delete; tombstone local theo CL-05-07 CONFIRMED, không remote delete |
| Quá quota, disk full, blocked ACL, DPAPI lỗi, file corruption | Chỉ báo SAVED sau durable commit, fail closed, báo lỗi và đưa hướng xử lý; không silent plaintext reset |
| Chạy trên Windows khác user hoặc chuyển nguyên tệp data | Không tự giải mã được khi chưa có authorized key recovery/CL-05-10, không “đăng nhập lại” để vượt DPAPI |
| WAL/temp/index chứa plaintext vô tình | Không chấp nhận coi “DB chính mã hóa” là đủ; mọi file phụ, crash dump/log phải tuân bảo mật |
| History paginated giữa lúc có new live message hoặc delete | Cursor/page ổn định, không duplicate/missing do offset dịch; không cập nhật sai conversation/EDT |
| Local Group metadata còn nhưng Group transport 004 chưa Verified | Group History disabled; không nhận owner tự khai hoặc lập sync engine riêng phá 004 topology |
| Sync đang chạy khi người dùng hủy, đóng app hoặc đổi conversation | Hủy worker, giữ durable checkpoint đã commit, không auto resend; UI thể hiện partial/paused |

## 6. Success Criteria (measurable, outcome-oriented)

- **SC-05-001:** Trên Windows cùng user, ít nhất một hội thoại P2P có tin Unicode/emoji **đã durable commit** được hiển thị đúng ID/sender/time/body sau app restart; không cần peer online.
- **SC-05-002:** Hai P2P conversations có displayName trùng và một Group khác ID đều không có message cross-contamination qua reload/pagination/sync.
- **SC-05-003:** Với N event hợp lệ và ≥2 lần sync P2P trên verified link, tập ID + canonical body cuối cùng trùng nhau trong phạm vi được phép, **0 duplicate bubbles**; một lần mất kết nối giữa sync không làm xuất hiện SYNCED giả.
- **SC-05-004:** **0** event/count/roster/private metadata bị trả về cho unauthorized peer, dù có M2 TLS pairing hoặc biết conversation/group ID.
- **SC-05-005:** New Group member nhận **0 pre-JOIN events**; removed member nhận **0 new History delta** sau REMOVE commit; existing previously acquired bytes không bị quảng cáo là đã remote-wipe.
- **SC-05-006:** Khi owner Group handoff hoặc offline, chỉ current authenticated owner với epoch/proof mới được cấp History, hoặc Group sync PAUSED; **0 dual-owner unauthorized grants** trong fault matrix.
- **SC-05-007:** **0** READ/DELIVERED nâng sai do load/scroll/sync/owner ACK; trạng thái local durable/sync và recipient receipts được phân biệt ở Live.
- **SC-05-008:** Có test restart, crash before/after durable commit, corrupt encrypted store, unsupported schema và wrong Windows user; không có silent data reset/cleartext fallback hay UI crash.
- **SC-05-009:** Test invalid UTF-8, malformed/unknown wire version, oversize chunks, replay, tampered event và disk/queue pressure cho thấy rejection bounded và EDT không bị chặn; các target định lượng được định nghĩa ở plan và **đo thực**, không tự PASS bằng design.
- **SC-05-010:** Áp dụng consent opt-in và **90 ngày / 500 MiB mặc định đều được giám sát, có thể điều chỉnh**, local-only deletion với tombstone không hồi sinh sau sync; không backup/export, không remote-delete; kết quả test/UX thể hiện giới hạn bản sao ở peer.
- **SC-05-011:** Bộ thử nghiệm Java21 tự động và ít nhất **2 peer/processes P2P**; Group History được thử với **≥3 independent JVM/client**, đồng thời có gate kế thừa **≥2 Windows 11 LAN PCs vật lý** và independent security review cho phát hành.
- **SC-05-012:** Trace đầy đủ **48/48 FR-05**, 16 AC-05-A…P và 9 hạng mục ROADMAP 05.01…05.09; release report nêu rõ Gate 1 blockers và không ghi M3 Verified khi Group 004 còn Partial.

## 7. Acceptance Criteria — 16 scenarios (AC-05-A…P)

**Tình trạng hiện tại: ALL NOT RUN.** Mỗi AC phải có actual command/test/screenshot/commit và kết quả PASS/FAIL/BLOCKED sau triển khai. A–F giữ nguyên ý định ROADMAP, G–P bổ sung để không bỏ sót quyết định an toàn.

| ID | Phép thử nghiệm thu | Expected behavior |
| --- | --- | --- |
| **AC-05-A** | A/B verified P2P lưu text/emoji; đóng toàn bộ Live, mở lại cùng Windows user khi B offline | Tin đã durable commit hiển thị lại đúng messageId/sender/time/body, một lần; không đòi online |
| **AC-05-B** | Sau khi Group 004 thực sự tích hợp, A/B/C có G và H, duplicate name, Group text verified | Tin nhóm chỉ vào đúng groupId/roster; bảo vệ provenance và membership epoch; không chèn sang P2P |
| **AC-05-C** | Import/retry cùng stable event ID, duplicate ACK/chunk, conflict ID cùng body khác/signer khác | 0 bản sao trùng; conflict không overwrite nguồn gốc, có cảnh báo/quarantine |
| **AC-05-D** | DB/ciphertext corrupt, schema unknown, disk full, crash giữa write/migration | Fail safely, lỗi hiển thị trung thực, dữ liệu đã commit không bị âm thầm mất/reset |
| **AC-05-E** | A/B hai peer được phép, A offline trong lúc B giữ event còn thiếu, verified reconnect/manual sync, giữa sync bị cắt link | Summary/delta tìm event thiếu, resume/idempotent; không cần Internet, không SYNCED sớm |
| **AC-05-F** | C pinned TLS peer nhưng ngoài conversation A↔B, biết conversationId và xin summary/delta | Không cấp History, **0 content/count/sender/time metadata** được tiết lộ |
| **AC-05-G** | Tập lịch sử lớn, nhiều trang, UTF-8/emoji ZWJ, khác timezone/clock skew, trùng tên | Phân trang đúng, UI EDT responsive, sender/time/source data và tie-break ổn định |
| **AC-05-H** | D Group join sau revision r, hỏi tin trước r; C bị remove r+1, hỏi tin sau r+1 | D nhận 0 pre-join events; C nhận 0 new deltas sau removal, dù đã ghim khóa |
| **AC-05-I** | A→B owner handoff signed, A cũ gửi Group History after e+1, B nhận genuine sync | Chỉ B mới đúng epoch/proof có quyền; stale A và spoofed handoff bị từ chối |
| **AC-05-J** | User xóa local History, restart, reconnect/sync B đang giữ bản sao cũ | Tombstoned local event không tự hồi sinh; không hứa B đã xóa dữ liệu |
| **AC-05-K** | Load/sync History trong lúc tin chưa có app ACK, C chưa xem; Demo mock chạy song song | Không nâng READ/DELIVERED giả, không đưa Demo events vào History Live |
| **AC-05-L** | Protected store trên cùng Windows user, rồi sai user/ACL/reparse/changed key, inspect side files/logs | Protection fail closed, không có plaintext History/secret trong temp/WAL/log; wrong user bị chặn |
| **AC-05-M** | Sync cancel/restart/quota, malformed version/UTF-8, replay/delta flood và peer half-open | Fail bounded, giữ last durable checkpoint, báo PARTIAL/ERROR, các conversation khác không treo |
| **AC-05-N** | Opt-in enable/disable; hai ngưỡng mặc định 90 ngày và 500 MiB, cấu hình lại, local delete/tombstone, không backup/export | Chỉ ghi tin mới sau consent; retention/quota/dọn có cảnh báo, không reimport local deleted, không remote wipe, không phát sinh backup hay receipt persisted |
| **AC-05-O** | E2E ≥3 JVM Group + ≥2 physical Win11 PCs LAN không Internet, manual inbound Accept/SAS, old M2/Group regression | Có evidence thực về verified identities, correct Group authority; không thay localhost bằng “2 PCs” |
| **AC-05-P** | Constitution I–VII/release: M1/M2 security/physical gates, Group 004, dependency/security/privacy review, toàn bộ FR/SC/AC | Chỉ đánh dấu History 005 Verified khi mọi mandatory gates có evidence; M3 chỉ Verified nếu **cả** 004 và 005 Verified |

## 8. Non-functional & privacy requirements

| Hạng mục | Ràng buộc/đích kiểm thử |
| --- | --- |
| **NFR-05-01 LAN-only** | Không phụ thuộc cloud/Internet hay máy chủ tập trung; outbound History chỉ qua manual approved verified M2 TLS |
| **NFR-05-02 Security/at rest** | Encrypted protected user-local content, least-privilege Windows ACL, no plaintext WAL/temp/log, no silent loss/rollback; cấm unauthorized sync |
| **NFR-05-03 Performance** | Paginated UI, bounded payload/memory/queue/disk/retention, cancellation và timeout rõ; chốt cụ thể/benchmark tại plan |
| **NFR-05-04 Reliability** | Atomic/durable commit trước khi “SAVED”; failure-recovery/checkpoints, no unverified optimistic SYNCED; data migration rollback safe |
| **NFR-05-05 Eventual consistency** | Đối soát dần khi cả hai peer authorized/online; không toàn cục exactly-once, không time-based last-write-wins |
| **NFR-05-06 Honesty** | Phân biệt sender original time, local saved time, copy/sync status và M2/004 ACK/READ evidence; UI nói rõ remote delete limitations |
| **NFR-05-07 Testability** | Pure store/ACL/merge + corrupt/key/authorization tests, two-JVM actual TLS P2P, ≥3-JVM Group and physical LAN Gate 1 |
| **NFR-05-08 Compliance** | Không chứa chat content trong Git/log; không backup/export tự động; sync chỉ đúng quyền, review independent dependency/security/privacy, Constitution I–VII |
| **NFR-05-09 Backward compatibility** | M1 raw v1 diagnostic unchanged; M2 CHAT_V2 strict; Group V1 owner relay/epoch; History capability negotiation có version/denial |

## 9. Conceptual data model (WHAT, không khóa DB/schema trước plan)

| Khái niệm | Dữ liệu cần nhận diện | Trạng thái lưu/đồng bộ |
| --- | --- | --- |
| **HistoryConversation** | kind P2P/GROUP, conversationId/groupId, verified participant key references, retention/consent state | Một conversation không thể tự kế thừa quyền từ Group khác hoặc displayName |
| **HistoryEvent** | eventId, originalMessageId, type=text/emoji, source UTC, authenticated senderId, canonical content/hash, provenance proof | Immutable after commit, dedup across retry; không lưu draft/outbox theo mặc định |
| **HistoryGroupEligibility** | groupId, memberId/key, join commit boundary, remove boundary, owner epoch/revision and signed proof | Để lọc event trước JOIN và chặn cấp thêm sau REMOVE |
| **HistoryLocalCommit** | eventId, durable commit marker, storedAt local, retention eligibility | Phân biệt local SAVED với sender delivered/read và SYNCED |
| **HistorySyncSession** | verified peer, conversation scope, capability version, requestId, summary, bounded cursor/chunk IDs, timeout/cancel state | Ephemeral; không tự lập kết nối hoặc tạo message ACK |
| **HistoryTombstone** | local deletion intent, target event/conversation IDs or cryptographic digest, created/committed timestamp | Phải chặn reimport; remote deletion ngoài phạm vi CL-05-07 CONFIRMED |
| **HistoryConflict** | eventId and conflicting signer/hash/version, detection result/quarantine flag | Không overwrite in-place, không log raw private content |
| **HistoryStoreProfile** | schema version, encrypted key reference, user-local ACL, quotas, migration state | Phải bảo vệ side-files và dữ liệu nhạy cảm, không nhầm với M2 pin store |

**Trust boundary:** Local History file encrypted không thay thế authorization. Peer request được kiểm tra `verified session/device + conversation-specific rights + event eligibility interval` và signed event source **trước** khi cung cấp hoặc durable apply. Không dùng số thứ tự clock một mình để quyết định quyền/đồng bộ.

## 10. ROADMAP traceability & prerequisites

| ROADMAP item | Phạm vi trong spec | Test/acceptance |
| --- | --- | --- |
| **05.01** Storage/schema/version/migration/local path | FR-05-010…020, NFR-05-02/04 | AC-A/D/L |
| **05.02** Event data stable IDs/sender/UTC/type | FR-05-001…009 | AC-A/B/C/K |
| **05.03** Paginated load/bounded UI | FR-05-014…016/044 | AC-G/M |
| **05.04** History UI/context | FR-05-006/007/016/020/047 | AC-G/K/N |
| **05.05** Retention/delete/privacy/fault/backup decision | FR-05-011…020/038…042 | AC-D/J/L/N |
| **05.06** Duplicate/replay/conflict | FR-05-024…027/043 | AC-C/E/M |
| **05.07** Persistence test/restart/corrupt/Unicode | FR-05-010…018/041/045 | AC-A/D/G/L |
| **05.08** P2P summary/delta/resume/offline | FR-05-021…032 | AC-E/F/M |
| **05.09** Auth/ACL/revoke/conflict/Group eligibility | FR-05-003/023/026/033…039/045/048 | AC-F/H/I/J/O/P |

**Dependencies/gates (not claimed complete):** M0 verified per prior evidence; M1/M2 multi-physical-Win11-LAN/security Gate 1 not yet Verified. Group 004 is currently partial domain only; Group History requires 004 verified Group messaging/owner handoff before acceptance. Full milestone **M3 requires verified 004 + 005**, neither is established by this spec. Các task/code/tests 005 đã thực thi về sau chỉ thuộc **domain/ACL**, theo [báo cáo](evidence/history-domain-red-green.md); không có secure History storage/network/Group acceptance.

**Execution:** [22/136 tasks completed](tasks.md) bằng actual RED→GREEN, full Java21 `mvnw.cmd -o package` **138/138 PASS**. [Evidence](evidence/history-domain-red-green.md) · [Verified blockers](evidence/execution-blockers.md) · [Gate 0 conditional](checklists/constitution-gate0.md). Phần thuần domain/ACL là code thật, nhưng **chưa** có encrypted SQLite, signed HISTORY_V1, authenticated sync hoặc Group History E2E. Chỉ tiếp tục các phần đó sau independent review và hoàn tất 004; không tuyên bố 005/M3 Verified.
