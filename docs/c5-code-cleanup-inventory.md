# C5 — Code Cleanup Inventory (23/11/2026)

> **Ghi chú 28/11/2026:** Đây là snapshot bằng chứng lịch sử tại ngày ghi trên báo cáo; số file, commit, kết quả PASS/NOT RUN và phạm vi audit bên dưới không phải hiện trạng mới nhất. Xem [kiểm chứng hiện hành](current-verification.md), [ma trận](feature-matrix.md) và [ROADMAP](../ROADMAP.md) cho các thay đổi sau C6 (Group Media, auto-save, auto reconnect và GUI Settings).

Repository branch main; baseline HEAD f9b415af9dad8e312cac4a9d7c099411f7d50390.

## Production Java — 46 files

| Class/File | Hành động | Bằng chứng | Rủi ro | Kết quả |
|---|---|---|---|---|
| src/main/java/listener/ChatActionHandler.java | KEEP | 2 reference files (test: 0); e.g. LiveChatAdapter.java, MainChatFrame.java | MEDIUM | PASS |
| src/main/java/listener/ChatClientListener.java | KEEP | 4 reference files (test: 1); e.g. ChatActionHandler.java, LiveChatAdapter.java | MEDIUM | PASS |
| src/main/java/listener/LiveChatAdapter.java | KEEP | 2 reference files (test: 1); e.g. LanConnectionDialog.java, LiveChatAdapterP1Test.java | MEDIUM | PASS |
| src/main/java/model/Attachment.java | KEEP | 6 reference files (test: 1); e.g. LiveChatAdapter.java, MessageContent.java | MEDIUM | PASS |
| src/main/java/model/ChatMessage.java | KEEP | 12 reference files (test: 5); e.g. ChatActionHandler.java, ChatClientListener.java | MEDIUM | PASS |
| src/main/java/model/ChatSnapshot.java | KEEP | 4 reference files (test: 1); e.g. ChatClientListener.java, LiveChatAdapter.java | MEDIUM | PASS |
| src/main/java/model/ChatTimeline.java | KEEP | 3 reference files (test: 2); e.g. MainChatFrame.java, ChatTimelineTest.java | MEDIUM | PASS |
| src/main/java/model/ChatUser.java | KEEP | 4 reference files (test: 0); e.g. LiveChatAdapter.java, ChatSnapshot.java | MEDIUM | PASS |
| src/main/java/model/Conversation.java | KEEP | 7 reference files (test: 0); e.g. ChatActionHandler.java, LiveChatAdapter.java | MEDIUM | PASS |
| src/main/java/model/DeliveryState.java | KEEP | 11 reference files (test: 5); e.g. ChatClientListener.java, LiveChatAdapter.java | MEDIUM | PASS |
| src/main/java/model/MessageContent.java | KEEP | 12 reference files (test: 6); e.g. ChatActionHandler.java, LiveChatAdapter.java | MEDIUM | PASS |
| src/main/java/model/MessageType.java | KEEP | 14 reference files (test: 4); e.g. LiveChatAdapter.java, MessageContent.java | MEDIUM | PASS |
| src/main/java/ui/IntraChat.java | KEEP | 11 reference files (test: 3); e.g. LiveChatAdapter.java, LanConnectionDialog.java | MEDIUM | PASS |
| src/main/java/ui/components/AvatarIcon.java | KEEP | 2 reference files (test: 0); e.g. ConversationRenderer.java, MainChatFrame.java | MEDIUM | PASS |
| src/main/java/ui/components/ChatIcon.java | KEEP | 5 reference files (test: 0); e.g. ChatStyle.java, ComposerPanel.java | MEDIUM | PASS |
| src/main/java/ui/components/ChatStyle.java | KEEP | 9 reference files (test: 0); e.g. IntraChat.java, AvatarIcon.java | MEDIUM | PASS |
| src/main/java/ui/components/ComposerPanel.java | KEEP | 2 reference files (test: 1); e.g. MainChatFrame.java, LiveComposerTest.java | MEDIUM | PASS |
| src/main/java/ui/components/ConversationRenderer.java | KEEP | 1 reference files (test: 0); e.g. MainChatFrame.java | MEDIUM | PASS |
| src/main/java/ui/components/LiveMessageVisibilityObserver.java | KEEP | 2 reference files (test: 1); e.g. MainChatFrame.java, LiveMessageVisibilityTest.java | MEDIUM | PASS |
| src/main/java/ui/components/MediaFiles.java | KEEP | 6 reference files (test: 2); e.g. ComposerPanel.java, MessageBubblePanel.java | MEDIUM | PASS |
| src/main/java/ui/components/MessageBubblePanel.java | KEEP | 3 reference files (test: 2); e.g. MainChatFrame.java, LiveDeliveryStateRenderingTest.java | MEDIUM | PASS |
| src/main/java/ui/components/MessageMedia.java | KEEP | 3 reference files (test: 1); e.g. MessageBubblePanel.java, MainChatFrame.java | HIGH | PASS |
| src/main/java/ui/components/VoiceRecorder.java | KEEP | 1 reference files (test: 0); e.g. ComposerPanel.java | MEDIUM | PASS |
| src/main/java/ui/components/WindowsForegroundGuard.java | KEEP | 2 reference files (test: 1); e.g. MainChatFrame.java, WindowsForegroundGuardTest.java | MEDIUM | PASS |
| src/main/java/ui/dialogs/CreateGroupDialog.java | KEEP | 1 reference files (test: 0); e.g. MainChatFrame.java | MEDIUM | PASS |
| src/main/java/ui/dialogs/LanConnectionDialog.java | KEEP | 2 reference files (test: 1); e.g. IntraChat.java, SingleFrontendEntrypointTest.java | MEDIUM | PASS |
| src/main/java/ui/frames/MainChatFrame.java | KEEP | 2 reference files (test: 1); e.g. LanConnectionDialog.java, LiveReceiptVisibilityTest.java | HIGH | PASS |
| src/main/java/vn/intrachat/application/AppInfo.java | KEEP | 2 reference files (test: 1); e.g. IntraChat.java, AppInfoTest.java | MEDIUM | PASS |
| src/main/java/vn/intrachat/application/ChatViewMapper.java | KEEP | 1 reference files (test: 0); e.g. LiveChatAdapter.java | MEDIUM | PASS |
| src/main/java/vn/intrachat/application/ConversationRamBudget.java | KEEP | 2 reference files (test: 1); e.g. LiveChatService.java, ConversationRamBudgetTest.java | MEDIUM | PASS |
| src/main/java/vn/intrachat/application/FileTransferService.java | KEEP | 11 reference files (test: 3); e.g. ChatClientListener.java, LiveChatAdapter.java | HIGH | PASS |
| src/main/java/vn/intrachat/application/GroupService.java | KEEP | 4 reference files (test: 1); e.g. LiveChatAdapter.java, GroupInvitationTimeoutTest.java | HIGH | PASS |
| src/main/java/vn/intrachat/application/LanCoordinator.java | KEEP | 10 reference files (test: 3); e.g. LanConnectionDialog.java, LiveChatAdapterP1Test.java | HIGH | PASS |
| src/main/java/vn/intrachat/application/LiveChatService.java | KEEP | 15 reference files (test: 4); e.g. LiveChatAdapter.java, IntraChat.java | HIGH | PASS |
| src/main/java/vn/intrachat/application/LocalProfile.java | KEEP | 16 reference files (test: 5); e.g. LiveChatAdapter.java, IntraChat.java | MEDIUM | PASS |
| src/main/java/vn/intrachat/domain/ChatGroup.java | KEEP | 5 reference files (test: 2); e.g. LiveChatAdapter.java, GroupService.java | MEDIUM | PASS |
| src/main/java/vn/intrachat/domain/ChatText.java | KEEP | 4 reference files (test: 1); e.g. MessageContent.java, GroupService.java | MEDIUM | PASS |
| src/main/java/vn/intrachat/domain/MessageAttempt.java | KEEP | 6 reference files (test: 2); e.g. LiveChatAdapter.java, LiveChatService.java | MEDIUM | PASS |
| src/main/java/vn/intrachat/domain/P2PConversation.java | KEEP | 3 reference files (test: 2); e.g. LiveChatService.java, P1TcpLoopbackTest.java | MEDIUM | PASS |
| src/main/java/vn/intrachat/persistence/HistoryRepository.java | KEEP | 7 reference files (test: 2); e.g. LiveChatAdapter.java, GroupService.java | HIGH | PASS |
| src/main/java/vn/intrachat/protocol/FrameCodec.java | KEEP | 2 reference files (test: 1); e.g. WireCodec.java, FrameCodecTest.java | MEDIUM | PASS |
| src/main/java/vn/intrachat/protocol/ProtocolException.java | KEEP | 2 reference files (test: 1); e.g. FrameCodec.java, FrameCodecTest.java | MEDIUM | PASS |
| src/main/java/vn/intrachat/protocol/WireCodec.java | KEEP | 7 reference files (test: 4); e.g. LanCoordinator.java, LanSession.java | HIGH | PASS |
| src/main/java/vn/intrachat/protocol/WireMessage.java | KEEP | 15 reference files (test: 6); e.g. FileTransferService.java, GroupService.java | MEDIUM | PASS |
| src/main/java/vn/intrachat/transport/LanSession.java | KEEP | 7 reference files (test: 2); e.g. FileTransferService.java, LanCoordinator.java | HIGH | PASS |
| src/main/java/vn/intrachat/transport/NetworkConfig.java | KEEP | 12 reference files (test: 4); e.g. LanConnectionDialog.java, LanCoordinator.java | MEDIUM | PASS |

Reference count is a static candidate filter, not full call-graph proof. Callback-only and UI classes must not be deleted on reference counts alone.

## Tests / Scripts / Resources / Maven

| Thành phần | Hành động | Bằng chứng | Rủi ro | Kết quả |
|---|---|---|---|---|
| 30 Java test files | KEEP | 96/96 JUnit tests pass; behavior coverage for F01–F11 | LOW | PASS |
| 13 scripts (8 PS1, 5 Java smoke) | KEEP | verify-all invokes P1–P5; environment/JDK setup scripts remain utility paths | MEDIUM | PASS |
| src/main/resources, src/test/resources | KEEP (empty) | 0 files in both after C4; no live assets deleted by C5 | LOW | PASS |
| flatlaf 3.6.2 | KEEP | Swing Look and Feel | MEDIUM | PASS |
| jackson-databind 2.22.4 | KEEP | JSON protocol/history | HIGH | PASS |
| junit-jupiter 5.14.4 (test) | KEEP | 96 regression tests | LOW | PASS |

## C5 changes and pending cases

- SIMPLIFY: remove 4 unused imports (ChatActionHandler, LanConnectionDialog, LanCoordinator, HistoryRepository). Confirmed by source token inspection and passing Maven/JUnit regression.
- No whole-class deletion: all 46 production Java class names have external references, including runtime or tests; this does not prove all methods are live.
- Candidate callback methods with one textual declaration/call occurrence: LiveChatAdapter.onFileEvent, LanConnectionDialog.approveIncoming/onConnected, MainChatFrame.send/manuallyRetry/fileAction, FileTransferService.expire, GroupService.receive. They are method references/lambdas or listener endpoints; retain pending stronger call graph evidence.
- No C5 test, script, or resource deletions. Historical C4 deletions are not attributed to C5.
- Maven Shade overlap warnings: multi-release module-info plus dependency META-INF NOTICE/LICENSE/MANIFEST; build/JAR diagnostics pass. Do not suppress warnings blindly.
- GUI connection Accept timeout needs interactive verification C6; physical LAN and real-device playback untested.
- No production TLS security code, mock service, login screen, DemoMedia or demo binary assets found among current production files.
- Static inspection is not a runtime proof of absence of leaks, races, or dynamically loaded code.
