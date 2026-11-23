# P0 — Cleanup inventory (snapshot lịch sử; cập nhật P1)

> **P1 update (23/11/2026):** Bảng bên dưới là kế hoạch P0, không phải source đang chạy. P1 đã xóa các package TLS/security/M1 diagnostics/signed Group và tests tương ứng sau khi smoke TCP 2 JVM PASS. Xem [báo cáo P1](p1-report.md) và [kiến trúc](architecture.md) để biết trạng thái mới.

Rà soát 23/11/2026. Phân loại dự kiến, **không phải lệnh xóa**. Mọi DELETE LATER chỉ thực hiện sau khi thay thế, kiểm tra reference/build/test và xác nhận dữ liệu người dùng không bị tác động. Đường dẫn tương đối từ root. [ROADMAP](../ROADMAP.md) · [Ma trận](feature-matrix.md).

| Nhóm/đường dẫn cụ thể | Phân loại | Lý do/hành động | Giai đoạn |
| --- | --- | --- | --- |
| `src/main/java/ui/IntraChat.java`, `ui/frames/MainChatFrame.java`, `ui/components/{ComposerPanel,MessageBubblePanel,MessageMedia,MediaFiles,VoiceRecorder,LiveMessageVisibilityObserver,ChatStyle}.java` | KEEP/MODIFY | Một entrypoint/Messenger; mở chức năng Live khi backend thật; giữ EDT | P1–P5 |
| `src/main/java/ui/dialogs/{LanConnectionDialog,CreateGroupDialog}.java` | MODIFY | Chuyển kết nối sang TCP, bật nhóm 3 peer | P1–P2 |
| `src/main/java/listener/{ChatActionHandler,ChatClientListener,LiveChatAdapter}.java` | MODIFY | Map identity/conversation, file/media/history/group, không mock | P1–P5 |
| `src/main/java/model/{Attachment,ChatMessage,ChatSnapshot,ChatTimeline,ChatUser,Conversation,DeliveryState,MessageContent,MessageType}.java` | KEEP/MODIFY | Tái dùng bubble/timeline/type SOUND và metadata | P1–P5 |
| `src/main/java/vn/intrachat/transport/{TcpConnector,TcpListener,NetworkConfig,OutboundBudget}.java`, `src/main/java/vn/intrachat/protocol/{FrameCodec,ProtocolException}.java` | KEEP/MODIFY | Tái dùng TCP/framing/giới hạn tài nguyên | P1 |
| `src/main/java/vn/intrachat/application/{LiveChatService,AckDeadline,RecentMessageIds,ConversationRamBudget,ChatViewMapper}.java` | MODIFY | Đổi TLS session, cập nhật ACK/READ, persistence và mapping | P1,P4 |
| `src/main/java/vn/intrachat/domain/{PeerIdentity,ChatText,MessageAttempt,P2PConversation,DeliveryReceipt,ReadReceipt}.java` | MODIFY | Giữ validation, bỏ fingerprint conversation ID, đổi UUID | P1 |
| `src/main/java/vn/intrachat/application/{SecureChatCoordinator,PeerTrustService,PairingStateMachine,FingerprintApprovalPort,GroupOwnerGrantService}.java` | REPLACE → DELETE LATER | LanCoordinator/GroupService trước khi loại crypto/pairing | P1–P2 |
| `src/main/java/vn/intrachat/transport/{TlsChatSession,ProtocolDispatch,SharedProtocolListener}.java` | REPLACE → DELETE LATER | LanSession, một TCP protocol trước khi bỏ mux/TLS | P1 |
| `src/main/java/vn/intrachat/protocol/{ChatV2Codec,ChatV2Message,ChatV2Type,ChatV2BootstrapCodec,GroupV1Codec,GroupV1Message,GroupV1Type,GroupV1AuthorizationEnvelope,GroupV3BootstrapCodec,GroupV3Transcript}.java` | REPLACE → DELETE LATER | WireCodec/WireMessage; không để protocol phụ thuộc owner signature | P1–P2 |
| `src/main/java/vn/intrachat/domain/{GroupIdentity,GroupMember,GroupRoster,GroupMetadata,GroupGrant}.java`, `src/main/java/vn/intrachat/application/{GroupInboundMailbox,GroupRecipientLedger}.java` | REPLACE → DELETE LATER | Owner relay simple roster, dedup/ACK per recipient | P2 |
| `src/main/java/vn/intrachat/security/*.java` (12 classes: DeviceCertificateFactory, DeviceCertificateValidator, DeviceIdentity, DeviceIdentityStore, FingerprintVerifier, GroupCanonicalBytes, GroupOwnerSignature, GroupRestoreGuard, PinnedPeerStore, ProtectedGroupStore, ProtectedLocalFile, TlsContextFactory) | DELETE LATER | Sau P1/P2 đã thay và dỡ mọi import/callers | P1–P2 |
| `src/main/java/vn/intrachat/application/{NetworkCoordinator,DiagnosticService,NetworkEvents}.java`, `src/main/java/vn/intrachat/transport/{TcpPeerSession,DiagnosticHeartbeat,SessionRegistry}.java`, `src/main/java/ui/dialogs/LanDiagnosticsDialog.java` | DELETE LATER | M1 diagnostics không thuộc 11 tính năng; kiểm reference trước | P1/P6 |
| `src/main/java/vn/intrachat/protocol/{JsonEnvelopeCodec,HandshakeValidator,NetworkEnvelope,Destination,DestinationKind}.java` | REPLACE → DELETE LATER | Chỉ gỡ khi WireCodec thay đủ và build/test pass | P1/P6 |
| `src/main/java/model/mock/{DemoMedia,MockChatService}.java`, `src/main/java/ui/frames/LoginFrame.java`, `src/main/resources/demo/` | DELETE LATER | Đưa Live thành launcher duy nhất, chuyển asset có dùng trước | P6 |
| `src/test/java/model/ChatTimelineTest.java`, `src/test/java/ui/{LiveComposerTest,LiveDeliveryStateRenderingTest,LiveMessageVisibilityTest,LivePeerDirectoryTest,SingleFrontendEntrypointTest}.java`, `src/test/java/ui/components/MediaFilesTest.java` | KEEP/MODIFY | Regression FE/metadata có giá trị; cập nhật Live | P1–P6 |
| `src/test/java/vn/intrachat/security/*.java` (12 tests) | DELETE LATER | Test TLS/DPAPI/cert/sign thay bằng TCP/Group test mới | P1–P2 |
| `src/test/java/vn/intrachat/application/{SecureChatCoordinatorTest,PeerTrustServiceTest,PairingStateMachineTest,LiveChatAuthorizationTest,SecurityBoundaryTest,GroupGrantValidationTest,GroupOwnerGrantServiceTest}.java` | REPLACE → DELETE LATER | Test phụ thuộc security/Group grant | P1–P2 |
| `src/test/java/vn/intrachat/protocol/{ChatV2CodecTest,ChatV2NegativeTest,GroupV1AuthorizationEnvelopeTest,GroupV1CodecTest,GroupV3BootstrapCodecTest,GroupV3TranscriptTest}.java` | REPLACE → DELETE LATER | Test mới cho WireCodec/frame/group | P1–P2 |
| `src/test/java/vn/intrachat/transport/{SecureChatLoopbackTest,SecureSessionResourceTest,TlsChatHandshakeTest,TlsChatSessionSecurityTest,SharedProtocolListenerTest,ProtocolDispatchTest,SecurePeerProcessHarness}.java` | REPLACE → DELETE LATER | Test mới cho LanSession/2–3 JVM | P1–P2 |
| `src/test/java/ui/{DemoIsolationTest,LiveFutureFeatureIsolationTest,LiveModeEntryTest}.java`, `src/test/java/ui/frames/DesktopUiTest.java`, `src/test/java/ui/dialogs/LanDiagnosticsDialogTest.java` | MODIFY/DELETE LATER | Đổi contract khi Live-only; không giữ assert Demo như tính năng hoàn thành | P5–P6 |
| `src/test/java/vn/intrachat/{application,domain,protocol,transport}/` (các test còn lại) | KEEP/MODIFY | Rà từng class theo dependency, giữ test size, lifecycle, ACK, Unicode, replay và fault có ích | P1–P6 |
| **Test mới** `LanSessionTest`, `P2PLoopbackTest`, `GroupThreePeerTest`, `FileTransferTest`, `HistoryRestartTest`, `MediaIntegrationTest`, `StateSenderTimeTest` | MODIFY (TO CREATE) | Chuyển tiêu chí nghiệm thu sang chức năng Live, không dùng regression cũ thay | P1–P6 |
| `pom.xml` | MODIFY | Bỏ `org.bouncycastle:bcpkix-jdk18on`, `net.java.dev.jna:jna-platform` chỉ sau hết import; giữ Jackson, FlatLaf, JUnit, shade main | P1/P6 |
| `scripts/{verify-m0.ps1,verify-m1.ps1,verify-m2.ps1,OfflineLaunchHarness.java,check-environment.ps1,get-jdk21-portable.ps1}` | MODIFY/DELETE LATER | `verify-m2` assert TLS/M2 gates, thay `verify-mvp.ps1`; giữ helper hữu ích cho build | P1/P6 |
| `src/main/java/ui/{frames/MainChatFrame.java,dialogs/LanConnectionDialog.java}` và `src/main/resources/` | MODIFY | Thay chuỗi TLS/verified/fingerprint/SAS, bỏ mock badge khi có chức năng Live | P1/P6 |
| `README.md`, `ROADMAP.md`, `AGENTS.md`, `docs/{feature-matrix,architecture,cleanup-inventory,p0-report}.md`, `.specify/memory/constitution.md` | KEEP/MODIFY | Nguồn MVP hiệu lực, P0 | P0–P6 |
| `PRODUCT.md`, `DESIGN.md`, `docs/{course-project-scope,messenger-integration-plan,messenger-ui-style-update-2026-11-22,ui-migration-2026-11-21,windows-lan-setup}.md`, `ROADMAP-LEGACY-SECURITY-2026-11-22.md` | KEEP (legacy) → DELETE LATER | Chuyển nhu cầu thực sang README/matrix rồi sửa link chéo | P6 |
| `specs/001-desktop-frontend/`, `specs/001-project-foundation/`, `specs/002-lan-networking/`, `specs/003-p2p-messaging/`, `specs/004-group-chat/`, `specs/005-chat-history/`, `specs/006-file-transfer/`, `specs/007-multimedia/` (gồm contracts/checklists/evidence/plan/tasks) | KEEP (legacy) → DELETE LATER | Chưa xóa, đặc biệt **5 thay đổi spec 003 của người dùng**; đối chiếu/merge trước cleanup | P6 |
| `.specify/` templates/workflow (ngoại trừ constitution đang đồng bộ) | KEEP → DELETE LATER | Chỉ xóa nếu P6 dừng dùng Spec Kit và không còn link | P6 |
| `.git/`, `.mvn/`, `mvnw`, `mvnw.cmd`, `.gitignore` | KEEP | Không viết history; wrapper cần cho build | Luôn giữ |
| `target/` | KEEP (build output) | Không clean thủ công trong P0; tool Maven có thể thay output khi build | P6 |

**Link/spec policy:** `docs/feature-matrix.md` quyết định trạng thái MVP; specs/contracts/evidence cũ là hồ sơ lịch sử nếu đòi TLS/DPAPI/owner signature/Gate hoặc History sync. Kiểm tra link một lần nữa trước khi xóa bất kỳ thư mục nào ở P6.

## Cập nhật P2 (23/11/2026)

- Các prototype `GroupV1*`, `GroupV3*`, `GroupOwnerGrantService`, `GroupInboundMailbox`, `GroupRecipientLedger`, signed grant/security đã được **gỡ ở P1**, không phục hồi trong P2.
- P2 thêm **duy nhất** `ChatGroup` domain và `GroupService` application, tái sử dụng `WireMessage/WireCodec`, `LanSession`, `LanCoordinator`, `LiveChatService`, `LiveChatAdapter` và `MainChatFrame`.
- Đã bật `CreateGroupDialog` ở Live, bổ sung menu quản lý nhóm nhỏ vào header Messenger. Không có GroupFrame, Group TCP listener, group mock trên Live hoặc Group security dependency mới.
- Đã thêm `ChatGroupTest`, `P2GroupProcessSmoke.java`, `verify-p2.ps1`; giữ `verify-mvp.ps1` và P1 regression. Không xóa thêm mã/tài liệu lịch sử trong P2; các mục Demo/mock và cleanup docs chuyển P6.

## Cập nhật P3 (23/11/2026)

- **CREATE:** `FileTransferService.java`, `FileTransferServiceTest.java`, `WireFileCodecTest.java`, `FileBubbleP3Test.java`, `scripts/P3FileProcessSmoke.java`, `scripts/verify-p3.ps1`, `docs/p3-report.md`.
- **MODIFY / REUSE:** `WireMessage` bổ sung FILE_REJECT/RESULT/ERROR; `LiveChatService` định tuyến FILE_* qua TCP P1; `LiveChatAdapter`, `ChatActionHandler`, `ChatClientListener` dùng transfer events; `ComposerPanel` bật chỉ File khi Live; `MessageBubblePanel`, `MainChatFrame` cập nhật tiến độ/Accept/Reject/Cancel/Open; `Attachment` cho phép metadata file rỗng. `LanCoordinator`, `LanSession`, `FrameCodec`, `MediaFiles`, `MessageContent` tiếp tục được tái sử dụng **không chỉnh sửa** trong P3.
- **DELETE: 0.** Không xóa thêm code/security legacy, evidence hoặc dữ liệu người dùng; không thêm listener, service ảnh/video/audio riêng hoặc thư viện mới. Các mục P4/P5/P6 giữ nguyên.


## Cập nhật chất lượng P2 (23/11/2026)

- Không thêm TCP listener, Group security, Group coordinator hoặc giao thức phụ. Tái sử dụng `GroupService` và `WireMessage.Type.GROUP_REMOVE` có sẵn để hết hạn lời mời chưa phản hồi sau 30 giây.
- Thay đổi tích hợp hiển thị ACK: `ChatActionHandler`, `ChatClientListener`, `LiveChatAdapter`, `MainChatFrame`, `MessageBubblePanel`. Thêm `GroupInvitationTimeoutTest`, mở rộng `LiveDeliveryStateRenderingTest`.
- Không xóa code cũ hoặc tài liệu bằng bulk cleanup; các mục Demo/mock/docs legacy giữ nguyên cho P6. Source F03/P3 không bị thay đổi trong đợt rà soát này.
## Cập nhật P4 — 23/11/2026

- **KEEP/CREATE:** `src/main/java/vn/intrachat/persistence/HistoryRepository.java` (JSONL append, bounded writer, group/peer metadata, dedup, state updates, startup restore), `src/test/java/vn/intrachat/persistence/HistoryRepositoryTest.java` (21 repository tests), `scripts/P4HistoryProcessSmoke.java`, `scripts/verify-p4.ps1`, `docs/p4-report.md`.
- **KEEP/MODIFY:** `LiveChatService` ghi P2P/ACK/READ/FILE, `GroupService` ghi roster và tin owner relay, `LiveChatAdapter` tải history vào Messenger và offline sidebar, `MainChatFrame` giữ timeline; model file metadata tái sử dụng từ P3.
- **DELETE: 0** trong lượt P4 này. Không khôi phục TLS, DPAPI, signed group hoặc thêm database khác. `specs/`, mock/demo và legacy evidence giữ nguyên đến P6; mọi thay đổi chưa commit P0–P3 được bảo vệ.
## Bổ sung inventory P5 (23/11/2026)

**Giữ/tái sử dụng:** `FileTransferService`, `LiveChatService`, `LiveChatAdapter`, `HistoryRepository`, `MessageType.SOUND`, `Attachment`, `MessageContent`, `ComposerPanel`, `MessageBubblePanel`, `MessageMedia`, `MediaFiles`, `VoiceRecorder`, `MainChatFrame`, `LanSession`, `WireCodec`. Chỉ mở rộng metadata type của file/chế độ render; không khôi phục TLS/DPAPI/codec nặng.

**Tạo P5:** `scripts/P5MediaProcessSmoke.java`, `scripts/verify-p5.ps1`, `src/test/java/vn/intrachat/application/MediaP5Test.java`, `src/test/java/ui/components/MediaFilesP5Test.java`, `docs/p5-report.md`. Không xóa file ở P5. Các docs/demo/legacy vẫn thuộc kiểm kê P6, không xóa trong P5. Các kết quả backend GUI/LAN phân biệt tại [p5-report.md](p5-report.md).


## P6 — Kiểm kê và hành động thực tế (23/11/2026)

| Path / nhóm | Phân loại | Lý do / dependency | Hành động P6 | Xác minh |
|---|---|---|---|---|
| `src/main/java` (49 Java) | KEEP / REVIEW | Code sản phẩm cần cho Live | Không xóa; rà soát từ khóa security và JAR | P1–P5 PASS, kiểm toán dead code chưa đầy đủ |
| `src/test/java` (30 Java) | KEEP | Regression có giá trị | Giữ toàn bộ | 95 PASS |
| `pom.xml` / `.mvn` | KEEP | Java 21/Shade | Giữ | Maven PASS, manifest JAR PASS |
| `scripts/verify-mvp.ps1` và `verify-p2..p5.ps1` | KEEP | Process smokes 2–3 JVM | Giữ | Từng script PASS khi log nằm ngoài target |
| `scripts/verify-all.ps1` | CREATE | Điều phối P1–P5 và git diff | Đã tạo | P2–P5 + diff PASS ở lượt cuối với SkipBuild; build/P1 PASS lượt trước |
| `README.md`, `ROADMAP.md`, `AGENTS.md`, `docs/feature-matrix.md` | MODIFY | Nội dung cũ mâu thuẫn P4/P5 | Đã đồng bộ | Link check tài liệu chính |
| `docs/architecture.md` | KEEP / REVIEW | Diễn giải kiến trúc hiện hành | Giữ | Cần kiểm toán line-by-line source để xác nhận sâu |
| `PRODUCT.md`, `DESIGN.md` | KEEP / REVIEW | Nội dung độc lập có giá trị ngắn | Giữ, chưa hợp nhất/xóa | Đã xem heading/nội dung |
| `docs/p0-report.md`..`docs/p5-report.md` | PRESERVE OUTSIDE REPO (candidate) | Historical evidence, có link | Đã sao lưu P6; chưa xóa | SHA backup mẫu; chờ hợp nhất/link |
| `specs/001..`..`specs/007..` / `evidence/` | PRESERVE OUTSIDE REPO (candidate) | Chứa legacy TLS/signed Group, một số file modified/untracked | Đã backup; **chưa xóa** | Cần đối chiếu từng file, refs/contracts/links |
| `.specify/memory/constitution.md` | KEEP / REVIEW | Modified trước P6 | Giữ và backup | Không mất dữ liệu |
| `src/main/java/vn/intrachat/security` và code legacy Git D | ALREADY DELETED PRE-P6 | Source legacy từ P0–P5 | Không thay Git trạng thái | P1 JAR scan PASS |

**Không có file bị xóa trong riêng P6.** Backup: `D:\Documents\Lap_trinh_ung_dung_mang\Tuan08\IntraChat-P6-backup-20261123`. Không coi mục candidate là quyết định xóa. Xem [báo cáo P6](p6-final-report.md).
