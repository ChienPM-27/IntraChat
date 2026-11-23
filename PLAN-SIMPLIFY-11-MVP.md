# KẾ HOẠCH TINH GỌN INTRACHAT — 11 CHỨC NĂNG MVP

Ngày rà soát: 23/11/2026. Phạm vi: `D:\Documents\Lap_trinh_ung_dung_mang\Tuan08\IntraChat`.
**TRẠNG THÁI: CHỈ LẬP KẾ HOẠCH VÀ KIỂM TRA TĨNH. Chưa xóa, chỉnh sửa mã nguồn hoặc xác nhận chạy test mới.**

## 0. Quyết định và nguyên tắc

- Mục tiêu duy nhất: một ứng dụng IntraChat Java 21 + Swing/FlatLaf hoạt động qua LAN, gồm đúng 11 tính năng **F01 P2P, F02 Group, F03 File, F04 Emoji, F05 History, F06 Image, F07 Video, F08 Sound, F09 State, F10 Time, F11 Sender**.
- Mọi tính năng phải hoạt động bằng **dữ liệu thật trên 2–3 tiến trình/máy**, không tính mô phỏng UI, mock ACK hay bằng chứng lịch sử.
- Đơn giản hóa thành **một giao diện Messenger, một stack TCP, một mô hình tin nhắn, một cơ chế truyền file**. Không làm cloud, account/login, media call, internet relay, đồng bộ lịch sử nâng cao, audit/penetration test, hệ thống chứng chỉ, TLS, mã hóa dữ liệu hay chữ ký chủ nhóm.
- Việc bỏ TLS/crypto khiến dữ liệu **không được mã hóa và peer UUID không được xác thực bằng mật mã**. Chỉ sử dụng trong LAN thử nghiệm tin cậy, không sử dụng cho dữ liệu nhạy cảm hoặc mở cổng ra Internet; không quảng bá ứng dụng là “an toàn/mã hóa”.
- Giữ các điều kiện để **chức năng chạy đúng và không gây lỗi máy**: giới hạn kích thước frame/tệp, timeout, đóng tài nguyên, xác định đúng người nhận theo kết nối, xác nhận trước khi lưu file, chống ghi đè/path traversal, kiểm tra bytes/CRC32, xử lý I/O ngoài Swing EDT. Đây là điều kiện chất lượng chức năng, không phải thêm hệ bảo mật.

## 1. Kết quả rà soát repository

- 106 Java production files; 99 Java test files; 111 tệp Markdown (97 tệp trong `specs`, 47 tài liệu `evidence/`); source đang có cả Demo + Live, M1 diagnostics + M2 TLS, Group prototype đã ký + nhiều giao thức không bật.
- Git branch: `feat/messenger-live-integration`. **Có thay đổi chưa commit**, phải giữ nguyên:
  `specs/003-p2p-messaging/{plan.md,quickstart.md,spec.md,tasks.md}` (4 modified),
  `specs/003-p2p-messaging/evidence/m2-mvp-acceptance.md` (untracked).
- Căn cứ đọc: `PRODUCT.md`, `docs/feature-matrix.md`, `docs/course-project-scope.md`, `docs/architecture.md`, specs 003–007, `pom.xml`, UI/adapter/service/transport/security/Group code và các script verify. Báo cáo 246 JUnit PASS **là thông tin lịch sử trong tài liệu**, không được coi là kết quả test mới hoặc nghiệm thu 11/11.
- F01 P2P, F04 Emoji, F09 State, F10 Time, F11 Sender: có đường chạy Live cục bộ qua TLS hiện tại; **phải kiểm thử lại** sau thay stack.
- F02 Group: có core nhưng không có fanout/UI Live; `LiveChatAdapter.createGroup` trả unsupported, `MainChatFrame` giấu nút tạo nhóm.
- F03 File, F06 Image, F07 Video, F08 Sound: có UI Demo nhưng `LiveChatAdapter.sendMessage` từ chối mọi `MessageType` ngoài TEXT; `ComposerPanel.setTextOnly(true)` giấu công cụ trong Live.
- F05 History: `LiveChatService` lưu transcript RAM; `LiveChatAdapter.loadHistory` không đọc disk; `MainChatFrame.onDirectoryChanged` xóa timeline Live khi peer offline (phải sửa để giữ lịch sử sau ngắt/restart).

## 2. Kiến trúc đích tối giản

```text
ui.IntraChat (một main, không Demo)
   └── MainChatFrame + LanConnectionDialog + CreateGroupDialog
       └── LiveChatAdapter (ChatActionHandler / ChatClientListener)
           ├── LiveChatService (text/emoji, ACK/READ, sender/time)
           ├── GroupService (nhóm 3 người, owner relay/fanout)
           ├── FileTransferService (một đường truyền cho File/Image/Video/Sound)
           └── HistoryRepository (JSON Lines + metadata local)
                      │
            LanCoordinator → LanSession → TCP Socket
                      │
          FrameCodec + WireCodec (giao thức gọn, không crypto)
```

- Dùng `TcpListener`, `TcpConnector`, `NetworkConfig`, `FrameCodec`, Jackson; thay `TlsChatSession` bằng `LanSession` dùng socket thường với frame có size cap và worker read/write. Một listener cho chat, không multiplex v1 diagnostics/v2 TLS/v3 Group.
- Handshake đơn giản: `HELLO {deviceId,displayName}`, `WELCOME` hoặc `REJECT`; một popup accept có thể giữ nếu hữu ích. Không chứng chỉ, fingerprint, SAS, pin, nonce, owner signature hoặc pairing 2 bước.
- Tạo hồ sơ `{deviceId: UUID,displayName}` tại `%LOCALAPPDATA%\IntraChat\data\profile.json`; cho phép override bằng `-Dintrachat.dataDir=...` để chạy 2–3 JVM cùng máy. Tên không dùng để định tuyến.
- ID hội thoại P2P bền vững từ **cặp UUID đã sắp xếp** dưới dạng String (ví dụ `dm:<uuid-min>:<uuid-max>`), hoặc ánh xạ lưu local. **Không tái sử dụng `P2PConversation.idFor` dựa vào SHA-256 fingerprint**. Các lời gọi `UUID.fromString(conversationId)` trong adapter phải được sửa theo kiểu ID đã chọn. ID nhóm là UUID ngẫu nhiên.
- Giao thức chỉ giữ loại thực sự cần: `HELLO/WELCOME/REJECT`, `TEXT`, `ACK/READ`, `GROUP_CREATE/INVITE/JOIN/LEAVE/TEXT`, `FILE_OFFER/ACCEPT/CHUNK/DONE/CANCEL`, `ERROR`. Mỗi tin có `messageId`, `conversationId`, `senderId`, `sentAtUtc`, `type`, `payload`; file chunk tách payload nhị phân để tránh đưa cả tệp lên heap/JSON.
- Group MVP: owner mở kết nối với B và C, gửi lời mời; B/C chấp nhận; người tham gia gửi tới owner, owner **relay** sang các thành viên khác và tự hiển thị. Owner cần online; **không bắt buộc full mesh**. Kiểm tra group ID/roster hiện tại ở owner; có leave/remove; không dùng ticket/chữ ký. Không tính delivery của tất cả khi mới nhận ACK từ một người.
- History MVP: local JSONL per-conversation + `contacts/groups.json` hoặc repository file đơn giản; ghi trên worker; đọc sau restart cả khi peer offline. Lưu text + sender/time/state và metadata tệp/media (không nhét binary vào JSON). Merge theo message ID, giữ timeline offline, cho xóa một hội thoại. Không cần SQLite hay đồng bộ History P2P.
- FileTransfer: offer → accept → chunks có giới hạn → hoàn thành; tiến độ theo byte nhận thực tế, CRC32/byte-length để phát hiện file hỏng; receiver chọn thư mục lưu, dùng file tạm và rename khi thành công; cancel/error cleanup. F06/F07/F08 chỉ là dạng nội dung của cùng F03, không viết ba giao thức riêng.
- State tối thiểu: Online/Offline và `SENDING/SENT/DELIVERED/SEEN/FAILED`; ACK khi ứng dụng đầu nhận đã xử lý, READ chỉ khi UI thực sự hiện tin. Không phát state theo mock timer. Nếu retry không đủ chắc chắn, cho gửi lại thủ công/hiển thị lỗi, không tự tuyên bố `Đã nhận`.
- Tất cả I/O chạy worker/virtual thread, chỉ cập nhật Swing qua EDT.

## 3. Danh mục tệp/mã cần can thiệp, theo phụ thuộc

### 3A. GIỮ và sửa ít nhất có thể

- `ui/IntraChat.java` (entrypoint), `ui/frames/MainChatFrame.java` (Messenger), `ui/dialogs/LanConnectionDialog.java` (Listen/Connect/Disconnect), `ui/dialogs/CreateGroupDialog.java`, `ui/components/{ComposerPanel,MessageBubblePanel,MessageMedia,MediaFiles,VoiceRecorder,LiveMessageVisibilityObserver,ChatStyle,...}.java`.
- `listener/{ChatActionHandler,ChatClientListener,LiveChatAdapter}.java`; `model/{Attachment,ChatMessage,ChatSnapshot,ChatTimeline,ChatUser,Conversation,DeliveryState,MessageContent,MessageType}.java`.
- `vn/intrachat/domain/{PeerIdentity,ChatText,MessageAttempt}.java`; `vn/intrachat/transport/{TcpConnector,TcpListener,NetworkConfig}.java`; `vn/intrachat/protocol/{FrameCodec,ProtocolException}.java`.
- Duy trì `pom.xml`, `.mvn/`, `mvnw`, `mvnw.cmd`, `.gitignore` và Maven shade/manifest khi cần. `MessageType.SOUND` hiện có, không tạo loại AUDIO thứ hai nếu không cần.

### 3B. THAY VIẾT/GỠ PHỤ THUỘC TRƯỚC KHI XÓA

- `ui/IntraChat.java`: bỏ `DeviceIdentityStore`, `PinnedPeerStore`, `intrachat.secureRoot`; load `LocalProfile` đơn giản và khởi động Live.
- `ui/dialogs/LanConnectionDialog.java`: thay `SecureChatCoordinator`, `SharedProtocolListener`, `NetworkCoordinator` bằng `LanCoordinator`; bỏ fingerprint/SAS/M1 diagnostics; giữ nhập IP:port, Accept, báo lỗi, Disconnect.
- `listener/LiveChatAdapter.java`: đổi `DeviceIdentity` thành UUID/profile; map conversation string, group, file/media, history; loại thông điệp “verified TLS”; giữ dispatch UI EDT.
- `application/LiveChatService.java`: phụ thuộc `TlsChatSession`, `DeviceIdentity`, `P2PConversation`, `ChatV2Message`, ACK ledger theo session TLS; refactor nhận `LanSession` và simple wire event; giữ đúng nội dung/ACK/READ.
- `domain/P2PConversation.java`: bỏ SHA-256 fingerprint, dùng ID theo hai UUID ổn định; kiểm tra mọi vị trí đang giả định conversation ID là UUID.
- `domain/{GroupIdentity,GroupMember,GroupRoster,GroupMetadata,GroupGrant}.java`: prototype hiện chứa SPKI, owner signature, roster hash, grant. **Không bê nguyên** vào Group MVP; thay Group/Member đơn giản `groupId,title,ownerId,memberIds`.
- `protocol/{ChatV2Codec,ChatV2Message,ChatV2Type,GroupV1Codec,GroupV1Message,GroupV1Type}.java`: tái sử dụng logic encode/validate kích thước khi có ích, nhưng hợp nhất thành **một** `WireCodec/WireMessage` không ràng buộc v2/v3/TLS/authorization.
- `ui/frames/MainChatFrame.java`: bỏ xóa lịch sử khi peer offline, bật Group/File/Media khi service thật chạy; xóa text “verified key”, thay title “Live LAN”. `ui/frames/LoginFrame.java` nếu bỏ Demo phải chuyển icon/assets được dùng sang `ChatStyle`/icon helper.

### 3C. XÓA HOÀN TOÀN SAU KHI CÓ BẢN THAY THẾ CHẠY ĐƯỢC

- **12 class** trong `src/main/java/vn/intrachat/security/`: `DeviceCertificateFactory`, `DeviceCertificateValidator`, `DeviceIdentity`, `DeviceIdentityStore`, `FingerprintVerifier`, `GroupCanonicalBytes`, `GroupOwnerSignature`, `GroupRestoreGuard`, `PinnedPeerStore`, `ProtectedGroupStore`, `ProtectedLocalFile`, `TlsContextFactory`.
- Lõi xác minh: `application/{PeerTrustService,SecureChatCoordinator,PairingStateMachine,FingerprintApprovalPort,GroupOwnerGrantService}.java`; chỉ giữ `InboundApprovalPort`/`InboundRequest` nếu cần popup accept và đã bỏ các semantics xác thực.
- Giao thức riêng bảo mật: `protocol/{ChatV2BootstrapCodec,GroupV1AuthorizationEnvelope,GroupV3BootstrapCodec,GroupV3Transcript}.java`; gỡ `transport/TlsChatSession.java`, `transport/{ProtocolDispatch,SharedProtocolListener}.java`.
- Group prototype dựa owner signed grant: `application/{GroupInboundMailbox,GroupRecipientLedger}.java` chỉ xóa nếu đã thay bằng `GroupService`+ACK đơn giản; không nhầm là đã có Group Live.
- Không phải 11 tính năng: có thể bỏ `ui/dialogs/LanDiagnosticsDialog.java`, `application/{NetworkCoordinator,DiagnosticService,NetworkEvents}.java`, `transport/{TcpPeerSession,DiagnosticHeartbeat,SessionRegistry}.java`, `protocol/{JsonEnvelopeCodec,HandshakeValidator,NetworkEnvelope,Destination,DestinationKind,...}.java` **sau khi kiểm tra reference lần cuối**. `OutboundBudget` là giới hạn tài nguyên, giữ hoặc thay bằng bound tương đương.
- Nếu chọn chỉ Live: bỏ `model/mock/{DemoMedia,MockChatService}.java`, `ui/frames/LoginFrame.java` (sau refactor entrypoint) và `src/main/resources/demo/`. Không xóa giao diện `MainChatFrame`; chuyển test Demo sang test Live hoặc bỏ khi đã hết đường gọi.
- `pom.xml`: gỡ `org.bouncycastle:bcpkix-jdk18on`, `net.java.dev.jna:jna-platform` **khi không còn import/dùng**. Giữ FlatLaf/Jackson/JUnit. Kiểm tra `mvn dependency:tree` và nội dung JAR cuối; bỏ comment M2/TLS và các assertion build đã lỗi thời.

### 3D. TEST và SCRIPT phải dọn cùng code

- `src/test/java/vn/intrachat/security/` (12 test): xóa cùng các lớp security sau khi thay test mới.
- Rà soát toàn bộ test trong `application/`, `protocol/`, `transport/` đang giả định TLS/SAS/cert/pin/owner signature: `SecureChatCoordinatorTest`, `PeerTrustServiceTest`, `PairingStateMachineTest`, `LiveChatAuthorizationTest`, `SecurityBoundaryTest`, `SecureChatLoopbackTest`, `TlsChatHandshakeTest`, `TlsChatSessionSecurityTest`, `SecureSessionResourceTest`, `GroupGrantValidationTest`, `GroupV1AuthorizationEnvelopeTest` và các test/harness tương tự. Xóa/sửa test đi đôi với hành vi không còn; KHÔNG giữ test compile hỏng.
- Giữ/sửa test FE/timeline/model/Unicode/ACK/reconnect có ích, thêm test `LanSessionTest`, `P2PLoopbackTest`, `GroupThreePeerTest`, `FileTransferTest`, `HistoryRestartTest`, `MediaIntegrationTest`, `StateSenderTimeTest`.
- `scripts/verify-m2.ps1` hiện assert `TlsContextFactory.class` phải tồn tại và xét M0/M1/M2 gates; thay bằng `scripts/verify-mvp.ps1` kiểm tra JDK21, `mvnw.cmd clean test package`, một main JAR, các class chính, test kết nối 2–3 JVM và không có class security. Gỡ `verify-m0.ps1`, `verify-m1.ps1` nếu không dùng; `OfflineLaunchHarness.java` và helper JDK chỉ giữ khi hỗ trợ demo.
- Không đặt mục tiêu giữ nguyên số lượng 246 test cũ; mục tiêu mới là **toàn bộ test liên quan 11 chức năng PASS**, không có skipped được diễn giải là PASS.

## 4. Tài liệu: tinh gọn và bỏ legacy/evidence khỏi working tree

**Sau khi trích xuất mọi yêu cầu vẫn cần thiết**, chỉ giữ hệ tài liệu hiện hành:
1. `README.md` — giới thiệu, JDK21/build/chạy hai máy LAN, kịch bản demo ngắn.
2. `docs/feature-matrix.md` — **nguồn sự thật duy nhất** cho 11 chức năng, tiêu chí PASS/NOT RUN và checklist nghiệm thu.
3. `docs/architecture.md` — sơ đồ TCP/UI/storage + message protocol một trang ngắn.
4. `ROADMAP.md` — thứ tự phát triển + trạng thái mỗi chặng; gộp kế hoạch này vào ROADMAP khi bắt đầu thực thi.
5. `DESIGN.md` — chỉ giữ token giao diện thực sự dùng, nếu cần.
6. `AGENTS.md` — nếu tiếp tục dùng agent thì giữ hướng dẫn Java/EDT/testing tối giản, không trích dẫn gate cũ.

**Ứng viên xóa sau khi hợp nhất:**
- `ROADMAP-LEGACY-SECURITY-2026-11-22.md`, `PRODUCT.md`, `docs/course-project-scope.md`, `docs/messenger-integration-plan.md`, `docs/messenger-ui-style-update-2026-11-22.md`, `docs/ui-migration-2026-11-21.md`, `docs/windows-lan-setup.md` (đưa bước kết nối vào README).
- **Toàn bộ** `specs/001-desktop-frontend`, `001-project-foundation`, `002-lan-networking`, `003-p2p-messaging`, `004-group-chat`, `005-chat-history`, `006-file-transfer`, `007-multimedia` **chỉ khi** 11 tiêu chí của 005/006/007 và các phần có ích trong 003/004 đã gộp vào ma trận. Bao gồm 47 `evidence/*.md`, các `checklists/`, `contracts/`, `research.md`, `data-model.md`, `plan.md`, `tasks.md`, `quickstart.md`.
- `.specify/` và constitution có security “NON-NEGOTIABLE”: loại bỏ nếu không còn dùng Spec Kit. Nếu vẫn dùng agent/spec workflow, phải thay bằng quy tắc MVP thống nhất, không duy trì hai bộ quy định trái nhau.
- `target/` là build output, có thể `mvnw.cmd clean`; **không** xóa `.mvn/`, Maven wrapper, `.git/` hoặc dữ liệu người dùng trong `%LOCALAPPDATA%` theo chiến dịch dọn docs.
- **Phân biệt working tree và Git history:** xóa file cũ khỏi branch vẫn giữ các commit lịch sử có thể khôi phục. Nếu thực sự muốn xóa nội dung khỏi **lịch sử Git**, đó là đợt riêng dùng lịch sử viết lại/force push sau backup và phối hợp với cộng tác viên; **không** tự ý làm trong MVP. Tuyệt đối không mất 5 thay đổi chưa commit đã nêu.

## 5. Kế hoạch triển khai có cổng nghiệm thu

### P0 — Đóng băng yêu cầu, bảo vệ thay đổi hiện có
- [ ] Lưu `git status`, commit/patch hoặc snapshot riêng cho 5 thay đổi đang mở; không `reset --hard` / `clean -fd`.
- [ ] Chốt F01–F11 trong ma trận có test thủ công, đơn giản hóa State/History/Group như mục 2.
- [ ] Chốt một file/tính năng chủ sở hữu, bỏ yêu cầu “production security gate”; đánh dấu kết quả test lịch sử là *historical*.

### P1 — Thay đường chạy P2P, bỏ lớp mã hóa
- [ ] Viết `LocalProfile`, `WireMessage/WireCodec`, `LanSession`, `LanCoordinator`.
- [ ] Refactor `IntraChat` → `LanConnectionDialog` → `LiveChatService` → `LiveChatAdapter` → `MainChatFrame`.
- [ ] Dùng hai JVM/profile khác nhau: Listen, Connect/Reject, text hai chiều, Unicode/emoji, sender/time, ACK/READ, offline/reconnect; 2 máy LAN thật khi có điều kiện.
- [ ] Chỉ **sau P2P smoke PASS**, xóa TLS/DPAPI/pin/fingerprint classes + tests + dependencies, xóa M1 diagnostics và xử lý chuỗi user-facing; `mvn clean test package` PASS.

### P2 — Group (F02)
- [ ] Tạo `GroupService` + simple roster owner/member, 3 peer A/B/C, invite/accept/leave/remove.
- [ ] Owner relay tin tới thành viên, phân biệt direct/group, sender/time và ACK theo người; bật `CreateGroupDialog` trong Live.
- [ ] Test 3 JVM: A gửi, B gửi, C gửi; từng người nhìn thấy đúng; D ngoài nhóm không thấy; một thành viên offline không nhận ACK giả.

### P3 — File (F03)
- [ ] Xây `FileTransferService`: offer/accept/chunk/done/cancel, tiến độ, file tạm, save-as.
- [ ] Test file nhị phân/tên tiếng Việt nhiều chunks, so sánh bytes/CRC32, cancel/lỗi mạng, không khóa EDT, không ghi ra ngoài đích chọn.

### P4 — History (F05)
- [ ] Xây `HistoryRepository` lưu theo conversation + metadata contact/group/file.
- [ ] Sửa `LiveChatAdapter.loadHistory`, `MainChatFrame.onDirectoryChanged`; history không mất khi disconnect.
- [ ] Test A/B gửi rồi **đóng và khởi động lại 2 JVM**: lịch sử direct/group đúng sender/time, không duplicate, xóa riêng một hội thoại.

### P5 — Media (F06–F08)
- [ ] Bật Image/Video/Mic/File trong `ComposerPanel` Live, gửi qua **chung** FileTransfer.
- [ ] PNG/JPEG: preview và save; MP4: save và mở default player; WAV: record hoặc chọn file, gửi/nhận/phát. Tất cả bubble có sender/time/state.

### P6 — Dọn triệt để và bàn giao
- [ ] Dọn code/tests/scripts/dependencies còn treo, xóa Demo/mock nếu đã chốt Live-only; đồng bộ UI strings.
- [ ] Gộp tài liệu còn ~4–6 file nguồn sự thật, loại bỏ specs/evidence/legacy, sửa mọi link/anchor chết.
- [ ] `git grep` trên tracked src/tests/scripts/docs/pom để không còn import/usage không cần của `security`, `TLS`, `SSL`, `DPAPI`, `certificate`, `fingerprint`, `SAS`, `crypto`, `bouncycastle`, `jna-platform`, `GroupV3`, `M1/M2 gate`, trừ **lưu ý công khai rằng bản MVP không mã hóa**.
- [ ] Maven clean test package PASS, JAR mở được; test 2–3 JVM + LAN thật nếu có, 11/11 trường hợp được ghi `PASS/FAIL/NOT RUN`; `NOT RUN` không tính PASS.
- [ ] Kiểm tra `git diff --check`, `git status`, tracked files, class trong JAR, không còn code security sống, không còn test giả, không còn dữ liệu mock đi vào Live, không để trống chức năng Live.

## 6. Bảng nghiệm thu 11/11 tối thiểu

| ID | Bài kiểm tra bắt buộc | PASS khi |
|---|---|---|
| F01 P2P | A/B hai JVM connect, text hai chiều, reconnect | Nội dung thật tới đúng peer |
| F02 Group | A tạo nhóm A/B/C, invite, cả ba gửi/nhận, leave | Fanout đúng roster, không có người ngoài |
| F03 File | A gửi file binary nhiều chunk, B save/cancel | Kết quả byte đúng, progress/cancel thật |
| F04 Emoji | Unicode tiếng Việt, emoji/ZWJ qua P2P và Group | Không lỗi dấu/emoji |
| F05 History | Gửi text + group/file metadata, restart cả hai | Đúng hội thoại/sender/time, không mất/trùng |
| F06 Image | PNG/JPG qua LAN tới B | Preview và lưu file nhận đúng |
| F07 Video | MP4 qua LAN tới B | Tệp đúng, mở/saving có fallback |
| F08 Sound | Chọn/ghi WAV rồi truyền tới B | Phát tệp đã nhận, không giả voice call |
| F09 State | Gửi, ACK, READ thật, disconnect/error | Online/offline và trạng thái không giả |
| F10 Time | Tin P2P/Group/Media, restart | UTC lưu, local hiển thị nhất quán |
| F11 Sender | Hai peer trùng displayName, tin nhóm | Phân biệt UUID, hiện đúng người |

## 7. Các bẫy dễ bỏ sót / kiểm tra chéo cuối

- **Tách dữ liệu test:** nhiều JVM cùng PC phải dùng profile/dataDir khác nhau; không xóa credential/data người dùng cũ trong LocalAppData.
- **ID hội thoại:** `ChatViewMapper`, `LiveChatAdapter`, `LiveChatService`, `P2PConversation`, `Conversation`, `MainChatFrame`, DB/history, receipts đều dùng cùng dạng ID. Không bỏ fingerprint mà quên thay logic sinh ID.
- **UI offline:** hàm đang xóa timeline khi mất TLS phải bỏ, nếu không F05 thất bại.
- **UI controls:** `setTextOnly(true)` và `newGroup.setVisible(false)`/ `newGroup.setEnabled(!actions.liveMode()...)` phải được đổi khi F02/F03/F06–08 chạy.
- **Không reuse Group prototype giả:** các `GroupOwnerGrantService`, `GroupInboundMailbox`, `GroupV1AuthorizationEnvelope` chưa nối Live; xóa chúng không phải hoàn thành F02.
- **Script fail ẩn:** `verify-m2.ps1` yêu cầu `TlsContextFactory.class`; `pom.xml` mô tả M2 TLS; class tests import security, cần rà trước build.
- **History sau restart:** local RAM aliases không bền, cần lưu cả liên hệ/nhóm và giữ messenger directory ngay khi offline.
- **File/media:** không gửi đường dẫn local của A thay cho bytes tới B; media renderer chỉ đọc file đã nhận đủ.
- **Chạy thật:** 246 test cũ PASS không chứng minh File/History/Group/Media Live; không dùng mock/timer thay dữ liệu mạng.
- **Resource:** socket timeout, CRC/length, block/file cap, số peer, worker shutdown, file tạm và EDT là yêu cầu chạy đúng tối thiểu.
- **Không xóa Git history nhầm:** `specs/003` có thay đổi chưa commit; Git filtering/force push ngoài phạm vi thực thi mặc định.

**Định nghĩa hoàn tất:** chỉ một launcher + Messenger, 11/11 chức năng hoạt động với dữ liệu LAN thật theo bảng kiểm, tài liệu gọn và nhất quán, không còn lớp crypto/TLS/DPAPI/security nâng cao trong source/build, không còn test và script dựa vào kiến trúc bảo mật cũ. Việc chạy tests/demos phải có kết quả mới, được ghi thành PASS/FAIL/NOT RUN trung thực.
