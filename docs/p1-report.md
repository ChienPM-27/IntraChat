# Báo cáo P1 — TCP plaintext và gỡ security legacy — 23/11/2026

## Kết quả điều kiện đầu vào

- Branch `feat/messenger-live-integration`, HEAD xác minh trước P1: `ef539c125aa08b96b65774317d032acf636441af`.
- P0 backup xác nhận tồn tại: `D:\Documents\Lap_trinh_ung_dung_mang\Tuan08\IntraChat-P0-backup-20261123`.
- Trước khi sửa, `git status --short` có thay đổi P0/docs/spec 003 và untracked. Không dùng reset/clean/stash/commit, không đụng thay đổi người dùng trong `specs/003-p2p-messaging/`.
- Backup P1 độc lập ngoài repo: `D:\Documents\Lap_trinh_ung_dung_mang\Tuan08\IntraChat-P1-backup-20261123`, gồm `working.diff`, `staged.diff` (nếu staged), `status.txt`, `head.txt`, `branch.txt` và thư mục `repository/` snapshot 332 file tracked+untracked không gồm `.git`/`target`. Đây là snapshot **trước khi bắt đầu sửa code P1**, dùng để phục hồi file riêng theo đường dẫn.
- Baseline được chạy lại **trước khi sửa code**: `mvnw.cmd -o test` trên Java mặc định 25, **249 tests, 0 fail, 0 error, 0 skipped, BUILD SUCCESS**. Sau đó tìm và sử dụng Temurin **JDK 21.0.12.1** cho build cuối.
- Tài liệu P0 đã đối chiếu cùng source; khi có khác biệt, ưu tiên source và MVP.

## Thực hiện

- **LocalProfile**: `%LOCALAPPDATA%\IntraChat\data\profile.json`, UUID bền vững, tên, override `-Dintrachat.dataDir`, báo lỗi profile hỏng mà không tự ghi đè.
- **WireMessage/WireCodec/FrameCodec**: TCP int32 length frame tối đa 1 MiB, JSON UTF-8/Unicode, schema validate, không deserialization Java. HELLO/WELCOME/REJECT/TEXT/ACK/READ; enum dự trù Group/File, chưa xử lý.
- **LanSession/LanCoordinator**: TCP real sockets, virtual-thread readers/workers, write lock, timeout, giới hạn 4 peer và 4 ứng viên, Accept/Reject UI, đóng idempotent, sender/recipient session binding, UUID trùng bị từ chối, manual reconnect.
- **P2PConversation**: deterministic UUID từ cặp UUID device sắp xếp; không dùng fingerprint, alias hay certificate. Adapter giữ String biểu diễn UUID conversation, không tạo song song ID khác.
- **LiveChatService/Adapter**: text/emoji, UTC, sender ID, message ID, ACK chỉ sau nhận TEXT, READ chỉ từ UI foreground visibility callback; no fake ACK/READ, trạng thái bất định khi đứt, retry thủ công chỉ trên session gốc. Transcript RAM giữ khi offline nhưng **không bền sau restart** (P4). Ledger bounded để không tích lũy vô hạn.
- **UI**: một `MainChatFrame` Live hiện có, entrypoint `ui.IntraChat`, `LanConnectionDialog` Listen/Connect/Accept/Reject/Disconnect, bỏ xác minh TLS/SAS; Demo vẫn riêng ở launcher, Live chỉ text/emoji. AWT foreground detection không cần JNA.
- **Cleanup sau khi 2-JVM TCP smoke PASS**: xóa package `vn.intrachat.security`, signed Group prototype, TLS/ChatV2/Group codecs, M1 diagnostics, coordinator cũ và tests không còn khớp. Giữ các test hành vi tổng quát; thêm LocalProfile, WireCodec, TCP loopback, multi-peer, LanSession. Bỏ BouncyCastle/JNA; scripts M0–M2 được thay bằng `verify-mvp.ps1` + harness hai JVM.

## Kiểm thử P1: phân biệt bằng chứng / không chạy

| Ca | Kết quả | Bằng chứng / giới hạn |
| --- | --- | --- |
| P1-01 Startup | **PARTIAL** | JDK21 executable JAR khởi chạy hai `javaw` process và Windows ghi nhận hai cửa sổ; GUI desktop khóa, chưa kiểm tra tự thao tác trong launcher |
| P1-02 Listen / Connect / Accept | **PASS backend / NOT RUN GUI** | 2 JVM riêng dùng real TCP listener, chấp nhận callback của test harness; Swing Accept/Reject popup chưa thao tác thủ công |
| P1-03 Bidirectional text | **PASS socket** | Hai JVM riêng truyền và trả lời UTF-8 A↔B qua socket, không dùng mock; GUI NOT RUN |
| P1-04 Unicode / emoji | **PASS socket / NOT RUN font GUI** | `Xin chào 👨‍👩‍👧 🇻🇳`, phản hồi `Đã nhận 🧑‍💻`; WireCodec/JUnit roundtrip |
| P1-05 Sender / Time | **PASS backend / NOT RUN GUI** | Hai JVM assert sender UUID và timestamp, P2P ID tách UUID khác nhau; duplicate displayName hiển thị thực chưa thử |
| P1-06 State | **PASS backend / NOT RUN GUI** | ACK→DELIVERED/READ→SEEN sau `markViewed` ở phía nhận; Swing visibility observer có regression unit nhưng manual foreground chưa thử |
| P1-07 Disconnect / Reconnect | **PASS local socket test** | JUnit đóng/reconnect, gửi tin mới cùng conversation ID; chưa thao tác GUI / 2 JVM reconnect |
| P1-08 Error handling | **PASS selected tests / PARTIAL** | REJECT, malformed/oversized frame, closed socket, sender mismatch, input port/IP kiểm tra; test ngắt Wi-Fi vật lý NOT RUN |
| P1-09 Conversation Identity | **PASS** | UUID stable/order-independent, khác peer; profile persisted, corrupted fail explicit |
| P1-10 UI Responsiveness | **NOT RUN manual GUI** | I/O lập trình ngoài EDT, callback UI qua Swing invokeLater, socket writer concurrency PASS; chưa đo gửi dồn bằng GUI |
| P1-11 Demo Isolation | **PASS static/regression / NOT RUN dual-mode manual** | Demo mock và Live TCP đi qua adapter riêng; không có ACK/READ giả trong service Live |
| P1-12 Multiple Peers | **PASS socket in one JVM / NOT RUN 3 JVM** | Test A đồng thời nhận B/C với session riêng, conversation/routing tách; 3 JVM độc lập chưa thử |

### Build / package

Đã chạy thực trên Temurin JDK 21.0.12.1:

```powershell
.\mvnw.cmd -o clean test package
powershell.exe -ExecutionPolicy Bypass -File .\scripts\verify-mvp.ps1 -JavaHome 'C:\Users\XuanHoai\.jdks\jdk-21.0.12.1+1'
```

- **44 JUnit tests PASS, failures 0, errors 0, skipped 0, BUILD SUCCESS** sau cleanup. Thay đổi số test từ 249 → 44 vì loại test bảo mật/protocol không còn áp dụng; không vô hiệu hóa test để giữ số 249.
- `target/intrachat-0.1.0-SNAPSHOT.jar` đã tạo; manifest `Main-Class: ui.IntraChat`; có lớp `LocalProfile`, `WireCodec`, `WireMessage`, `LanSession`, `LanCoordinator`, `LiveChatService`.
- JAR không chứa `vn/intrachat/security/`, `TlsChatSession.class`, `SecureChatCoordinator.class`, `org/bouncycastle/`, `com/sun/jna/`.
- Script verify **PASS** hai tiến trình Java 21, hai profile UUID khác nhau, TCP Unicode hai chiều, ACK, READ.
- Maven Shade **vẫn cảnh báo overlaps**: `META-INF.versions.9.module-info` giữa Jackson core/databind và các resources `META-INF/NOTICE`, `META-INF/LICENSE`, `META-INF/MANIFEST.MF` của Jackson/FlatLaf/project. Không phát hiện trùng application classes; warnings không bị tắt. BouncyCastle/JNA không còn trong dependency graph package.

## Còn thiếu / cần nghiệm thu trước khi đánh dấu P1 hoàn toàn ACCEPTED

1. Desktop hiện ở lock screen, GUI manual Listen→Connect→Accept/Reject, gửi/nhận, bubble sender/time/state, check READ đúng foreground và UI không treo **NOT RUN**. Hai process GUI đã được dừng sau khi xác minh cửa sổ tồn tại.
2. Hai PC LAN vật lý, xử lý Windows Firewall / mạng chập chờn **NOT RUN**.
3. Stress nhiều giờ, fault injection vật lý, frame fuzz nâng cao **NOT RUN**; có unit/integration fault checks, chưa phải chứng cứ kiểm thử hệ thống đầy đủ.
4. Group/File/History bền vững/Image/Video/Sound thuộc **P2–P5**, không triển khai trong P1.

**Kết luận:** P1 đã triển khai code TCP, dọn bỏ security legacy, build/test/package thành công và có bằng chứng trao đổi dữ liệu thật trên 2 JVM. **P1 chưa được tuyên bố nghiệm thu GUI/LAN vật lý hoàn tất.** Không tự động chuyển P2–P6, không commit, không thay Git history.
