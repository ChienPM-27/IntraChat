# P5 — Image, Video và Sound Live (23/11/2026)

## A. Trạng thái nghiệm thu

- **P5 IMPLEMENTED:** YES — ba loại `IMAGE`, `VIDEO`, `SOUND` được nối vào Composer/Adapter/bubble, dùng chung FileTransferService.
- **P5 BACKEND VERIFIED:** YES **ở mức truyền byte, metadata và restart** qua hai JVM TCP độc lập; smoke chưa kiểm tra file MP4 có thể phát được.
- **P5 GUI VERIFIED:** **NOT RUN** (chưa thực hiện chuỗi chọn/gửi/nhận/xem/phát trên hai cửa sổ Messenger thực tế).
- **P5 LAN VERIFIED:** **NOT RUN** (chưa thử trên hai máy vật lý).
- **Kết luận: P5 PARTIAL ACCEPTANCE**. Không tuyên bố nghiệm thu F06/F07/F08 đầu-cuối trên GUI/LAN.

F06/F07/F08: implemented trong code và backend transfer verified; image preview ở mức component; Video Desktop.open và WAV Clip chỉ được nối logic, chưa xác nhận mở/phát thành công trong desktop thực.

## B. Thay đổi mã nguồn

Tạo:
- `scripts/P5MediaProcessSmoke.java`, `scripts/verify-p5.ps1`.
- `src/test/java/vn/intrachat/application/MediaP5Test.java`, `src/test/java/ui/components/MediaFilesP5Test.java`.
- `docs/p5-report.md`.

Sửa:
- `FileTransferService`: thêm `MessageType` trong `FILE_OFFER` và `Transfer`, overload `offer(peer,conversation,path,type)`, vẫn dùng duy nhất FILE_CHUNK/CRC32/FILE_RESULT; FILE cũ mặc định FILE; cap IMAGE 16 MiB, SOUND 8 MiB, VIDEO/FILE 128 MiB.
- `LiveChatService`: history persist đúng loại từ transfer.
- `HistoryRepository`: chấp nhận FILE/IMAGE/VIDEO/SOUND, cập nhật state/restore của mọi media; JSONL vẫn chỉ chứa metadata.
- `LiveChatAdapter`: gửi media chung; restore đúng type/bubble từ JSONL, giữ transfer ID/sender/time, không chuyển media thành TEXT.
- `ComposerPanel`: hiển thị công cụ ảnh/video/file/âm thanh/ghi WAV sẵn có ở Live; chọn WAV/MP3/M4A; kiểm tra file ngoài EDT.
- `MainChatFrame`: chỉ bật công cụ media ở hội thoại P2P; không bật Group media chưa có backend.
- `MessageBubblePanel`: media đã hoàn thành render qua `MessageMedia`; trước khi hoàn thành hiện tên/kích thước/tiến độ/nhận/từ chối/hủy; FILE giữ bubble cũ.
- `MediaFiles`: chấp nhận metadata âm thanh MP3/M4A để mở ngoài; WAV PCM tiếp tục dùng Java Sound.
- `MessageMedia`: giới hạn ảnh phóng lớn 1600 px, fallback ảnh hỏng/mất, nút Dừng và mở âm thanh bằng OS, đóng Clip khi kết thúc.
- Các tài liệu: README, architecture, feature-matrix, ROADMAP, cleanup-inventory.

Không xóa file nào trong P5. Không tạo Media Transfer Service, media listener hoặc TCP socket mới. Không commit/reset/stash/clean.

## C. Kết quả kiểm thử thực tế

| Hạng mục | Kết quả và mức chứng cứ |
| --- | --- |
| JUnit | **PASS 95/95**, không fail/error/skip (gồm 8 test mới) |
| Maven clean test package | **PASS** JDK 21, offline |
| P1 regression | **PASS**, 2 JVM TCP, text/emoji/ACK/READ |
| P2 regression | **PASS ×3**, 3 JVM mỗi lượt, Group Owner Relay |
| P3 regression | **PASS**, 2 JVM file, cancel/reject/disconnect/text |
| P4 regression | **PASS**, 2/3 JVM restart/history, 3 lượt Group bổ sung |
| P5 media hai JVM | **PASS** truyền byte và type qua TCP thật |
| Image PNG/JPEG | **PASS** byte/type process smoke + decode/resize component; smoke dùng payload byte thử, không có giải mã ảnh trên receiver GUI |
| Image preview | **PASS component** ImageIO (giữ tỷ lệ, ảnh hỏng/mất); **GUI NOT RUN** |
| Video transfer | **PASS** multi-chunk/CRC32/SHA256 smoke, metadata VIDEO; payload smoke chưa xác thực codec MP4 |
| Video Open | **NOT RUN** Desktop.open trên GUI thực |
| Audio WAV transfer | **PASS** byte/type smoke + xử lý WAV thật trong unit; playback chưa kiểm chứng trên thiết bị âm thanh |
| Audio Playback | **NOT RUN** Clip đầu ra âm thanh thực, đã có cơ chế Play/Stop và fallback Desktop |
| Media Integrity | **PASS** CRC32/FileResult P3 + SHA256 P5 |
| Media Cancel | **PASS regression P3** qua cùng service; **chưa chạy kịch bản cancel với MessageType VIDEO** |
| Media History Restart | **PASS** JSONL + restart hai JVM, đúng 4 loại và state |
| Media Type Isolation | **PASS** FILE/IMAGE/VIDEO/SOUND qua TCP/restore |
| Unicode filename | **PASS** tên tiếng Việt trong P5 hai JVM |
| Reject | **PASS** P5 VIDEO reject, không tạo file đích |
| Disconnect / text concurrent | **PASS regression P3**, **chưa chạy riêng P5 VIDEO** |
| Duplicate media | **PASS unit history**; **chưa inject duplicate FILE_OFFER qua TCP trong P5** |
| Messenger GUI | **NOT RUN** |
| LAN vật lý | **NOT RUN** |

Bằng chứng P5: `scripts/verify-p5.ps1` chạy `P5MediaProcessSmoke.java`, tạo profile A/B riêng trong `%TEMP%\intrachat-p5-<id>`. Lượt cuối PASS: `PASS_TWO_JVM_SHA256`, `PASS_RESTORE_A`, `PASS_RESTORE_B`, `PASS_P5_PROCESS`; evidence ở `C:\Users\XuanHoai\AppData\Local\Temp\intrachat-p5-e1167d2771a7422d817bf5462b9a815c`.

## D. Giới hạn

- Ảnh PNG/JPEG, tối đa 16 MiB và 32 megapixel; thumbnail ImageIO tạo ngoài EDT, ảnh lớn hiển thị co tỷ lệ.
- Video MP4/AVI/MKV, tối đa 128 MiB, mở bằng ứng dụng hệ điều hành khi người dùng bấm; codec/player phụ thuộc Windows. Không thumbnail/video player nhúng.
- Âm thanh WAV PCM tối đa 8 MiB phát bằng Java Sound; MP3/M4A chỉ mở ngoài bằng ứng dụng mặc định. Play/Stop/WAV recording hiện dựa component đã có, không streaming.
- Giới hạn một transfer hoạt động / peer; mỗi chunk 32 KiB và frame TCP tối đa 1 MiB, CRC32 phát hiện hỏng byte nhưng không xác thực mật mã.
- Không Group media, không cloud, không realtime call, không TLS hoặc mã hóa TCP/history; chỉ LAN thử nghiệm tin cậy.
- Không tự động tải lại media mất khỏi disk sau restart. Sender path chỉ tồn tại trong profile sender; receiver dùng đường lưu tự chọn.
- Preview ảnh, Desktop.open và audio hardware chưa kiểm chứng qua thao tác GUI. Chưa thử phát MP4 hợp lệ trong ứng dụng thực, chưa thử hai máy vật lý.
- P5 process smoke xác minh byte/type/metadata/restart nhưng **không chứng minh payload .mp4 có codec phát được**; cần fixture MP4 hợp lệ trong nghiệm thu GUI.

## E. Việc còn lại và mức sẵn sàng P6

1. Nghiệm thu thực tế hai Messenger: chọn ảnh hợp lệ, preview, nhận, mở; video MP4 hợp lệ bằng player mặc định; WAV Play/Stop, MP3 fallback, ghi âm WAV.
2. Bổ sung P5 scenario riêng cho VIDEO cancel/disconnect/concurrent text/duplicate offer qua TCP, codec fixture MP4 hợp lệ và UI event latency.
3. Thử hai PC cùng LAN; kiểm tra permission, thiết bị audio, desktop mở file; kiểm thử GUI không treo.
4. Quan sát RAM/EDT/timer và audio cleanup với lịch sử dài; regression sau khi hoàn thành các mục trên.

**Có thể bắt đầu lập kế hoạch P6** khi ghi nhận P5 partial acceptance và các công việc chưa nghiệm thu; **chưa được xem 11/11 chức năng đã nghiệm thu hoàn toàn**. Giữ nguyên toàn bộ dữ liệu P0–P4 và LocalAppData thật.

## Backup/Git

Branch `feat/messenger-live-integration`, HEAD `ef539c125aa08b96b65774317d032acf636441af`. Backup trước thay đổi ở `D:\Documents\Lap_trinh_ung_dung_mang\Tuan08\IntraChat-P5-backup-20261123`; chứa untracked, working tree và Git diff; kiểm tra SHA-256 README khớp. `git diff --check` PASS (chỉ có cảnh báo CRLF từ Git); không commit.
