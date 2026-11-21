# IntraChat — Lộ trình triển khai chi tiết

> **Dự án:** IntraChat — Phần mềm Chat mạng LAN bằng Java  
> **Phiên bản lộ trình:** 1.0.0  
> **Ngày lập:** 21/11/2026  
> **Trạng thái:** Kế hoạch đề xuất, **chưa phải** báo cáo tiến độ đã triển khai  
> **Nguồn yêu cầu:** P2P, Group, File, Emoji, History, Image, Video, Sound, State, Time, Sender  
> **Tài liệu liên quan:** [README](README.md) · [AGENTS](AGENTS.md) · [Constitution](.specify/memory/constitution.md)

## 1. Mục tiêu và nguyên tắc triển khai

Xây dựng ứng dụng Java cho phép các máy trong **cùng mạng LAN** trao đổi tin nhắn cá nhân, tin nhắn nhóm và tệp đa phương tiện; quản lý lịch sử hội thoại; hiển thị người gửi, thời gian và trạng thái một cách chính xác.

Nguyên tắc:

1. **LAN-first:** các chức năng lõi không bắt buộc Internet hay dịch vụ cloud.
2. **Chốt giao thức trước khi mở rộng:** mọi tin nhắn và sự kiện mạng đi qua hợp đồng dữ liệu nhất quán.
3. **Làm bản chạy được sớm:** hoàn thành P2P cơ bản trước Group, File và Media.
4. **Tái sử dụng:** Image/Video/Sound sử dụng cơ chế File Transfer khi có thể.
5. **Tách trách nhiệm:** UI, networking, protocol, service/domain và persistence không phụ thuộc chéo tùy tiện.
6. **Test theo từng giai đoạn:** không đợi đến cuối mới kiểm thử.
7. **Spec-driven:** từng nhóm tính năng có spec, kế hoạch, tasks và tiêu chí nghiệm thu rõ ràng.
8. **Không thổi phồng tiến độ:** chưa được test thì không đánh dấu verified; các lựa chọn kỹ thuật chưa chốt được ghi là TBD.

### 1.1. Phạm vi tính năng và mốc dự kiến

| Tính năng | Giai đoạn chính | Mức độ ưu tiên | Kết quả mong muốn |
| --- | --- | --- | --- |
| P2P | 2–3 | P0 | Hai peer trao đổi tin nhắn trực tiếp trong LAN |
| Group | 4 | P1 | Nhiều thành viên trao đổi trong một nhóm |
| File | 6 | P2 | Gửi/nhận tệp có tiến độ và kiểm soát lỗi |
| Emoji | 3 | P0 | Soạn và hiển thị emoji Unicode |
| History | 5 | P1 | Lưu và đọc lại các hội thoại |
| Image | 7 | P2 | Gửi/nhận và xem hình ảnh |
| Video | 7 | P2 | Gửi/nhận và mở hoặc phát video |
| Sound | 7 | P2 | Gửi/nhận và mở hoặc phát âm thanh |
| State | 2–3; hoàn thiện 8 | P0 | Hiện diện/kết nối; nếu được đặc tả, trạng thái tin nhắn |
| Time | 3 | P0 | Hiển thị timestamp nhất quán |
| Sender | 3 | P0 | Mỗi tin nhắn có định danh người gửi chính xác |

**Giới hạn phạm vi:** Video và Sound ở đây là **chia sẻ/phát nội dung**, chưa bao gồm gọi video hoặc gọi thoại thời gian thực. Những phần như Internet chat, cloud account, đồng bộ đa thiết bị, gọi điện trực tiếp chỉ thực hiện khi có yêu cầu và spec riêng.

### 1.2. Các quyết định cần làm rõ, không tự giả định

- Phiên bản JDK/LTS và môi trường hệ điều hành cần hỗ trợ.
- Công cụ build (Maven/Gradle) và framework test (ví dụ: JUnit).
- UI desktop (Swing/JavaFX) và thư viện/phương án hiển thị media.
- Mô hình kết nối P2P, cơ chế tìm peer, địa chỉ/port, TCP/UDP và vấn đề firewall.
- Group chuyển tiếp qua một peer điều phối trong LAN hay phân phối theo mô hình khác.
- Peer identity, display name, cách tham gia/xác minh peer, policy chấp nhận file.
- Định dạng thông điệp, framing, phiên bản giao thức, retry/ACK/dedup.
- Vị trí lưu History, cấu trúc dữ liệu, retention/deletion và truy cập.
- Giới hạn dung lượng tin nhắn/file, loại file hỗ trợ, điều kiện phát media.
- Có hay không trạng thái `sent/delivered/read` ngoài hiện diện `online/offline`.

Các quyết định ảnh hưởng kiến trúc phải được ghi tại `plan.md` tương ứng, kèm lý do chọn và phương án thay thế.

## 2. Sơ đồ phụ thuộc và mốc phát hành

```text
01. Khởi tạo & kiến trúc
              |
02. Nền tảng kết nối LAN
              |
03. P2P + Sender + Time + State + Emoji  ---> MVP: chat cá nhân dùng được
           /           \
04. Group Chat      05. History
           \           /
              |
06. File Transfer
              |
07. Image + Video + Sound
              |
08. Integration + Testing + Release
```

Giai đoạn 4 và 5 có thể làm song song **sau khi** mô hình thông điệp và P2P ổn định. Trong tất cả giai đoạn, kiểm thử và cập nhật tài liệu được thực hiện liên tục; giai đoạn 8 là **kiểm thử tổng hợp/đóng gói**, không thay cho test của giai đoạn trước.

**Mốc bàn giao:**

- **M0 — Foundation ready:** build/run được skeleton, có protocol draft và test infrastructure.
- **M1 — LAN connected:** hai peer kết nối/trao đổi thông điệp thử nghiệm ổn định.
- **M2 — MVP:** chat P2P có Sender, Time, Emoji, State tối thiểu.
- **M3 — Core collaboration:** có Group và History đã nghiệm thu.
- **M4 — Media-capable:** File, Image, Video, Sound hoạt động theo đặc tả.
- **M5 — Release candidate:** bản đóng gói qua test tích hợp và có tài liệu sử dụng.

---

## 3. Giai đoạn 01 — Khởi tạo dự án và thiết kế kiến trúc

**Ưu tiên:** P0  
**Spec Kit feature:** `001-project-foundation`  
**Phụ thuộc:** không có  
**Mục tiêu:** tạo nền tảng phát triển Java có thể build, run và test, thống nhất các ranh giới kiến trúc.

### Công việc chi tiết

- [ ] **01.01 — Chốt môi trường:** JDK, hệ điều hành mục tiêu, IDE gợi ý, Maven/Gradle và phiên bản thư viện.
- [ ] **01.02 — Chọn UI:** phân tích Swing/JavaFX, xác định lý do, cách cập nhật UI thread và cơ chế đóng app.
- [ ] **01.03 — Phác họa kiến trúc:** chia UI / service / domain / protocol / transport / persistence; xác định dependency direction.
- [ ] **01.04 — Định nghĩa loại dữ liệu chính:** `Peer`, `Conversation`, `Message`, `Group`, `Attachment`, `Transfer`, `ConnectionState` (tên lớp cuối cùng có thể thay đổi).
- [ ] **01.05 — Thiết kế hợp đồng thông điệp bản đầu:** message ID, version, type, sender ID, destination, timestamp, payload; quy tắc validation và giới hạn kích thước.
- [ ] **01.06 — Thiết lập project skeleton:** cấu hình build, thư mục `src/main`, `src/test`, entry point, logging và cấu hình chạy.
- [ ] **01.07 — Kiểm thử ban đầu:** unit-test mẫu, lệnh test/build có thể chạy được; CI nếu có môi trường phù hợp.
- [ ] **01.08 — Cập nhật README:** ghi lệnh build/run/test thực tế sau khi đã xác minh.

### Kết quả bàn giao

- Source skeleton có thể khởi chạy giao diện/CLI tối thiểu và đóng sạch tài nguyên.
- Bản thiết kế kiến trúc và hợp đồng thông điệp sơ bộ.
- Có test framework và lệnh build/test chạy thành công trên máy phát triển.
- Mọi quyết định công nghệ đã được lưu trong kế hoạch, không chỉ truyền miệng.

### Tiêu chí nghiệm thu

- **AC-01-A:** clone dự án trên máy phù hợp, làm theo README thì build/run được.
- **AC-01-B:** unit test mẫu chạy và cho kết quả có thể lặp lại.
- **AC-01-C:** các tầng chính có ranh giới rõ, không buộc UI gọi trực tiếp socket I/O.

---

## 4. Giai đoạn 02 — Nền tảng kết nối mạng LAN

**Ưu tiên:** P0  
**Spec Kit feature:** `002-lan-networking`  
**Phụ thuộc:** giai đoạn 01  
**Mục tiêu:** thiết lập kết nối tin cậy giữa ít nhất hai tiến trình/máy trong LAN.

### Công việc chi tiết

- [ ] **02.01 — Đặc tả topology:** xác định P2P/điều phối LAN, discovery, thiết lập kết nối, port và firewall.
- [ ] **02.02 — Khám phá peer:** chọn cơ chế tìm thiết bị hoặc nhập IP thủ công (phương án fallback).
- [ ] **02.03 — Thiết lập session:** kết nối, handshake, peer ID, trạng thái connected/disconnected.
- [ ] **02.04 — Framing:** phân tách message trên luồng byte, hỗ trợ bản tin bị chia nhỏ/gộp, giới hạn size.
- [ ] **02.05 — I/O bất đồng bộ:** reader/writer workers hoặc abstraction tương đương, queue hợp lý, không chặn UI.
- [ ] **02.06 — Lifecycle:** timeout, đóng socket/stream/thread, reconnect policy và hiển thị lỗi.
- [ ] **02.07 — Kiểm tra input:** version sai, loại message lạ, malformed payload, lỗi giao thức.
- [ ] **02.08 — Test kết nối:** loopback hai process; thử hai máy thật trong LAN; ngắt đột ngột.

### Kết quả bàn giao

- Có thể tìm/nhập địa chỉ và kết nối tới một peer theo topology đã chọn.
- Có API gửi/nhận thông điệp thử nghiệm độc lập với giao diện chat.
- Có sự kiện kết nối và ngắt kết nối để UI/service sử dụng.

### Tiêu chí nghiệm thu

- **AC-02-A:** hai phiên chạy ở hai máy cùng LAN kết nối và trao đổi payload thử nghiệm thành công.
- **AC-02-B:** mất kết nối không treo UI; socket/worker được thu hồi.
- **AC-02-C:** đầu vào sai loại, quá kích thước hoặc malformed bị từ chối có kiểm soát.
- **AC-02-D:** mô tả rõ yêu cầu port/firewall, cách peer được tìm và giới hạn của discovery.

---

## 5. Giai đoạn 03 — Chat P2P (MVP)

**Ưu tiên:** P0  
**Spec Kit feature:** `003-p2p-messaging`  
**Phụ thuộc:** giai đoạn 02  
**Phạm vi tính năng:** **P2P, Sender, Time, State, Emoji**  
**Mục tiêu:** hoàn thành phiên bản chat cá nhân tối thiểu có thể sử dụng.

### Công việc chi tiết

- [ ] **03.01 — Mô hình hội thoại P2P:** ánh xạ peer ID ↔ conversation; không dùng display name làm khóa.
- [ ] **03.02 — Gửi/nhận text:** mã hóa UTF-8, message ID, dữ liệu đầu vào hợp lệ, giới hạn độ dài.
- [ ] **03.03 — UI hội thoại:** danh sách peer, khung tin nhắn, ô nhập, nút gửi, cuộn lịch sử trong phiên.
- [ ] **03.04 — Sender:** hiển thị người gửi đúng với từng message; hỗ trợ tên trùng, peer đổi tên.
- [ ] **03.05 — Time:** gắn timestamp, lưu theo quy ước chung (ưu tiên UTC), hiển thị theo giờ địa phương.
- [ ] **03.06 — State:** ít nhất hiện diện/kết nối `connected/disconnected`; nếu hiển thị sent/delivered/read phải có protocol ACK tương ứng.
- [ ] **03.07 — Emoji:** nhập, gửi/nhận và render emoji Unicode; kiểm tra text đa ngôn ngữ.
- [ ] **03.08 — Gửi thất bại:** feedback rõ khi peer offline; chính sách retry/queue nếu được đặc tả.
- [ ] **03.09 — Automated tests:** message mapping, metadata, Unicode, peer không kết nối, gửi lặp nếu có retry.

### Kết quả bàn giao

- Bản **MVP IntraChat** chat trực tiếp được giữa hai máy LAN.
- Tin nhắn hiển thị nội dung, người gửi, thời gian và trạng thái phù hợp.
- Emoji không bị mất hoặc biến dạng vì lỗi mã hóa.

### Tiêu chí nghiệm thu

- **AC-03-A:** A gửi tin nhắn cho B, B thấy đúng nội dung, sender và time.
- **AC-03-B:** B phản hồi cho A; hai bên nhìn đúng chiều gửi/nhận.
- **AC-03-C:** emoji và chữ tiếng Việt đi qua mạng mà không lỗi encoding.
- **AC-03-D:** khi B mất kết nối, A nhận trạng thái/lỗi hợp lý, UI vẫn phản hồi.
- **AC-03-E:** không hiển thị trạng thái `read/delivered` nếu hệ thống chưa quan sát/xác nhận được.

**Mốc nghiệm thu M2:** chỉ công bố MVP sau khi các kiểm thử P2P chạy thành công.

---

## 6. Giai đoạn 04 — Group Chat

**Ưu tiên:** P1  
**Spec Kit feature:** `004-group-chat`  
**Phụ thuộc:** giai đoạn 03  
**Phạm vi tính năng:** **Group**, tái sử dụng Sender/Time/State/Emoji  
**Mục tiêu:** hỗ trợ trao đổi tin nhắn giữa nhiều thành viên trong LAN.

### Công việc chi tiết

- [ ] **04.01 — Chốt mô hình Group:** group ID, người tạo, thành viên, quyền, mô hình điều phối/phân phối.
- [ ] **04.02 — Luồng tạo/join/leave nhóm:** xác định đồng bộ membership giữa các peer.
- [ ] **04.03 — Gửi tin nhóm:** xác định danh sách nhận theo membership; tránh gửi nhầm người ngoài nhóm.
- [ ] **04.04 — Hiển thị nhóm:** tên nhóm, thành viên, tên người gửi ở mỗi tin, danh sách hội thoại.
- [ ] **04.05 — Xử lý bất đồng bộ:** thành viên offline, join muộn, rời nhóm, group host/peer điều phối rời mạng.
- [ ] **04.06 — Semantics tin nhóm:** duplicate, ordering, acknowledgment, consistency được định nghĩa rõ.
- [ ] **04.07 — Test ≥3 client:** broadcast, join/leave, kết nối gián đoạn và tên trùng.

### Kết quả bàn giao

- Người dùng có thể tạo hoặc tham gia nhóm theo quy tắc được đặc tả.
- Các tin gửi vào nhóm được phân phối tới đúng thành viên.
- Group hoạt động độc lập với hội thoại P2P đang mở.

### Tiêu chí nghiệm thu

- **AC-04-A:** ba client A/B/C trong cùng nhóm đều nhận được tin nhắn từ A theo chính sách phân phối.
- **AC-04-B:** người ngoài nhóm không tự động nhận nội dung nhóm.
- **AC-04-C:** thay đổi membership được phản ánh đúng; không làm hỏng hội thoại P2P.
- **AC-04-D:** ngắt một client không khiến các client còn lại treo hoặc bị lỗi dây chuyền.

---

## 7. Giai đoạn 05 — Chat History

**Ưu tiên:** P1  
**Spec Kit feature:** `005-chat-history`  
**Phụ thuộc:** mô hình message giai đoạn 03; tích hợp group với giai đoạn 04  
**Phạm vi tính năng:** **History**, liên kết Sender/Time/State  
**Mục tiêu:** xem lại lịch sử sau khi đóng/mở ứng dụng.

### Công việc chi tiết

- [ ] **05.01 — Chọn storage:** file/DB nhúng, schema, migration, khóa hội thoại và vị trí lưu.
- [ ] **05.02 — Lưu message:** unique ID, conversation/group ID, sender ID, timestamp, loại nội dung, metadata.
- [ ] **05.03 — Load lịch sử:** phân trang/giới hạn để không tải toàn bộ dữ liệu lớn lên UI.
- [ ] **05.04 — UI lịch sử:** hiển thị theo hội thoại, thứ tự xác định rõ, phân biệt ngày và người gửi.
- [ ] **05.05 — Chính sách dữ liệu:** retention, xóa lịch sử, dữ liệu nhạy cảm, lỗi ghi và backup nếu cần.
- [ ] **05.06 — Chống dữ liệu trùng:** xử lý message ID trùng, replay/retry; tránh đổi sender/time gốc.
- [ ] **05.07 — Test persistence:** đóng/mở lại, corrupted storage, nhiều hội thoại và dữ liệu Unicode.

### Kết quả bàn giao

- Tin đã lưu xuất hiện lại sau khi khởi động ứng dụng.
- Lịch sử P2P và Group không lẫn vào nhau.
- Dữ liệu hiển thị đúng sender, timestamp và loại tin.

### Tiêu chí nghiệm thu

- **AC-05-A:** khởi động lại ứng dụng vẫn xem được lịch sử đã lưu của P2P.
- **AC-05-B:** sau khi Group tích hợp, tin nhóm xuất hiện đúng conversation/group.
- **AC-05-C:** tin lặp không làm xuất hiện bản sao ngoài chính sách đã định.
- **AC-05-D:** khi lỗi storage, app báo lỗi hợp lý và không sập/không che giấu mất dữ liệu.

---

## 8. Giai đoạn 06 — File Transfer

**Ưu tiên:** P2  
**Spec Kit feature:** `006-file-transfer`  
**Phụ thuộc:** kết nối/giao thức giai đoạn 02–03; mục tiêu tích hợp Group ở giai đoạn 04  
**Phạm vi tính năng:** **File**  
**Mục tiêu:** truyền tệp giữa các peer có tiến độ, hủy truyền và kiểm soát an toàn.

### Công việc chi tiết

- [ ] **06.01 — Đặc tả file protocol:** transfer ID, file metadata, size, streaming/chunking, ACK, lỗi/hủy.
- [ ] **06.02 — Chính sách nhận file:** vị trí lưu, yêu cầu chấp thuận, kiểm tra đường dẫn/tên, overwrite.
- [ ] **06.03 — Gửi/nhận theo luồng:** giới hạn buffer, không nạp toàn bộ tệp vào bộ nhớ.
- [ ] **06.04 — UI tiến độ:** tiến trình byte đã gửi/nhận, completed/failed/cancelled.
- [ ] **06.05 — Integrity:** đối chiếu bytes/checksum và chỉ đánh dấu complete khi hợp lệ.
- [ ] **06.06 — Cleanup:** timeout, hủy giữa chừng, xóa/đánh dấu partial file theo chính sách.
- [ ] **06.07 — Kiểm thử an toàn:** file tên lạ, đường dẫn traversal, file rỗng, file lớn/quá giới hạn.
- [ ] **06.08 — Tích hợp Group:** nếu hỗ trợ tệp nhóm, xác định rõ cơ chế gửi tới từng thành viên và trường hợp nhận thất bại một phần.

### Kết quả bàn giao

- Người dùng gửi/nhận một tệp qua LAN với trạng thái minh bạch.
- Tệp hoàn tất trùng nội dung tệp gốc.
- Không cho phép dữ liệu mạng ghi ra ngoài thư mục đích an toàn.

### Tiêu chí nghiệm thu

- **AC-06-A:** A gửi file, B nhận được file cùng số byte và checksum (nếu quy định).
- **AC-06-B:** đang truyền có thể thấy tiến độ và hủy an toàn.
- **AC-06-C:** đứt mạng giữa chừng không bị đánh dấu là đã hoàn tất.
- **AC-06-D:** đường dẫn không hợp lệ, file quá giới hạn và tên tệp độc hại bị xử lý an toàn.

---

## 9. Giai đoạn 07 — Multimedia: Image, Video, Sound

**Ưu tiên:** P2  
**Spec Kit feature:** `007-multimedia`  
**Phụ thuộc:** giai đoạn 06  
**Phạm vi tính năng:** **Image, Video, Sound**  
**Mục tiêu:** hỗ trợ nội dung media dựa trên nền truyền tệp đã có.

### Công việc chi tiết

- [ ] **07.01 — Media metadata:** MIME type, tên, kích thước, thumbnail/preview policy nếu cần.
- [ ] **07.02 — Image:** gửi/nhận ảnh, preview, kích thước UI hợp lý, xử lý ảnh lỗi.
- [ ] **07.03 — Video:** gửi/nhận file video, mở bằng trình phát phù hợp hoặc phát in-app nếu có giải pháp được chọn.
- [ ] **07.04 — Sound:** gửi/nhận file audio, phát/dừng bằng player phù hợp nếu được hỗ trợ.
- [ ] **07.05 — Giới hạn và fallback:** codec không hỗ trợ, dung lượng vượt hạn mức, thiếu player và dữ liệu không hợp lệ.
- [ ] **07.06 — UI hội thoại:** hiển thị item media với sender, time, transfer state và thao tác mở/lưu.
- [ ] **07.07 — Test:** đa dạng loại file, media lỗi, tên Unicode, file lớn, hủy và kiểm tra byte integrity.

### Kết quả bàn giao

- Người dùng gửi/nhận ảnh, video, âm thanh trong chat.
- Media được preview/phát khi nền tảng hỗ trợ; ngược lại có chỉ dẫn mở file an toàn.
- Không tự tuyên bố hỗ trợ gọi thoại/video thời gian thực.

### Tiêu chí nghiệm thu

- **AC-07-A:** gửi/nhận ảnh, video, audio theo tập định dạng hỗ trợ đã công bố.
- **AC-07-B:** file nhận không bị thay đổi nội dung; preview/playback không làm treo UI.
- **AC-07-C:** codec/định dạng không hỗ trợ được báo rõ, không crash.
- **AC-07-D:** mọi media item hiển thị đúng sender, time và trạng thái truyền.

---

## 10. Giai đoạn 08 — Tích hợp, kiểm thử và đóng gói

**Ưu tiên:** P0 (bắt buộc cho phát hành)  
**Spec Kit feature:** `008-integration-release`  
**Phụ thuộc:** các giai đoạn thuộc phạm vi bản phát hành  
**Mục tiêu:** xác nhận ứng dụng hoạt động ổn định trên LAN thực tế, hoàn thiện trải nghiệm và phát hành có thể tái lập.

### Công việc chi tiết

- [ ] **08.01 — Full regression:** chạy lại test P2P, Group, History, File và Media.
- [ ] **08.02 — LAN thực tế:** kiểm thử 2–3 máy, các địa chỉ IP khác nhau cùng LAN, policy firewall và reconnect.
- [ ] **08.03 — Stress/soak:** gửi nhiều tin, nhiều transfer, file lớn và chạy kéo dài theo budget đã thống nhất.
- [ ] **08.04 — Failure injection:** peer tắt đột ngột, packet/frame không hợp lệ, lỗi lưu, lỗi quyền thư mục, mất kết nối khi tải.
- [ ] **08.05 — Review bảo mật:** path traversal, unsafe deserialization, thiếu size limit, lộ nội dung chat trong log, ghi đè file.
- [ ] **08.06 — Review UX:** hiển thị Sender/Time/State, lỗi dễ hiểu, không chặn UI, đóng ứng dụng sạch.
- [ ] **08.07 — Đóng gói:** chọn JAR/installer phù hợp, dependency/runtime, cách chạy, yêu cầu máy đích.
- [ ] **08.08 — Tài liệu:** README cập nhật, hướng dẫn cài đặt, cấu hình LAN/firewall, screenshots (khi có), known limitations.
- [ ] **08.09 — Gắn phiên bản và phát hành:** changelog, release notes, kết quả test xác minh, tag Git khi được duyệt.

### Kết quả bàn giao

- Gói chạy ứng dụng trên máy đích theo hướng dẫn.
- Test report gồm test pass/fail, môi trường, lỗi chưa giải quyết và giới hạn đã biết.
- Tài liệu vận hành và release notes phản ánh đúng chức năng thực tế.

### Tiêu chí nghiệm thu

- **AC-08-A:** bản build được đóng gói và chạy được trong môi trường LAN mục tiêu.
- **AC-08-B:** tất cả các acceptance criteria của chức năng nằm trong phạm vi phát hành được xác nhận.
- **AC-08-C:** không còn lỗi nghiêm trọng chưa có quyết định xử lý; các giới hạn được công bố.
- **AC-08-D:** README có lệnh build, test, run, setup LAN và xử lý sự cố đã được kiểm chứng.

---

## 11. Cách tổ chức thư mục đặc tả theo GitHub Spec Kit

Thư mục dưới đây là **cấu trúc dự kiến**; chỉ tạo khi bắt đầu đặc tả từng nhóm chức năng. Không mặc định các file đã hiện diện.

```text
IntraChat/
├── README.md
├── AGENTS.md
├── ROADMAP.md
├── .specify/
│   └── memory/
│       └── constitution.md
└── specs/
    ├── 001-project-foundation/
    │   ├── spec.md
    │   ├── plan.md
    │   └── tasks.md
    ├── 002-lan-networking/
    ├── 003-p2p-messaging/
    ├── 004-group-chat/
    ├── 005-chat-history/
    ├── 006-file-transfer/
    ├── 007-multimedia/
    └── 008-integration-release/
```

Mỗi feature folder hướng tới ba tài liệu cơ bản:

1. **`spec.md` (WHAT/WHY):** mục tiêu, phạm vi, user stories, yêu cầu chức năng, edge cases, acceptance criteria; không khóa cứng framework khi không cần.
2. **`plan.md` (HOW):** công nghệ, topology, kiến trúc, data model, contract, chiến lược test, Constitution Check, trade-off.
3. **`tasks.md` (EXECUTION):** các đầu việc nhỏ, thứ tự phụ thuộc, vị trí thay đổi, bài test, trạng thái.

Tùy tính năng, có thể có `research.md`, `data-model.md`, `contracts/`, `quickstart.md` hoặc checklist được sinh theo workflow Spec Kit thực tế.

### 11.1. Quy trình cho từng giai đoạn

1. Đọc `.specify/memory/constitution.md` và `AGENTS.md`.
2. Xây dựng `spec.md` từ phạm vi, tác nhân, luồng thành công/thất bại và tiêu chí nghiệm thu trong lộ trình.
3. Làm rõ các câu hỏi TBD; tránh bỏ mặc quyết định ảnh hưởng kiến trúc.
4. Lập `plan.md`, kiểm tra tuân thủ 7 nguyên tắc Constitution, xác định rủi ro.
5. Tạo `tasks.md` với test và định nghĩa hoàn thành cho từng task.
6. Thực hiện theo tasks, ghi lại kết quả build/test thực tế.
7. Review acceptance criteria, cập nhật tài liệu và chỉ sau đó chuyển giai đoạn.

> **Lưu ý:** repository có thể chứa `constitution.md` và `AGENTS.md` nhưng chưa chắc đã cài CLI/integration của GitHub Spec Kit. Những lệnh kiểu `/speckit-specify`, `/speckit-plan` và `/speckit-tasks` chỉ hoạt động khi agent tích hợp đã được khởi tạo đúng cách. Không chạy lệnh init ghi đè mà không kiểm tra/sao lưu tài liệu hiện có.

### 11.2. Mẫu thông tin bắt buộc trong spec

- **Actors:** người gửi, người nhận, thành viên nhóm, peer mất kết nối.
- **Preconditions:** cùng LAN, peer reachable, quyền nhận tệp hoặc nhóm hợp lệ.
- **Happy paths:** gửi/nhận P2P, nhắn nhóm, mở lịch sử, truyền tệp, xem media.
- **Failure paths:** peer offline, trùng/thiếu metadata, timeout, hủy truyền, lỗi lưu, file độc hại.
- **Acceptance tests:** đánh số để có thể trace đến unit/integration test.
- **Non-functional:** độ phản hồi UI, giới hạn file, timeout, an toàn path, memory usage và bảo mật.

---

## 12. Ma trận kiểm thử xuyên suốt

| Hạng mục | Test cần có | Giai đoạn |
| --- | --- | --- |
| LAN | Hai peer kết nối; peer không tồn tại; timeout; ngắt đột ngột; malformed frame | 2, 8 |
| P2P | Gửi/nhận hai chiều; đúng conversation; Unicode; message ID trùng | 3, 8 |
| Sender | Trùng display name; sender ID không đổi; không gán nhầm người | 3–5, 8 |
| Time | Hiển thị thời gian; sai múi giờ; đồng hồ peer lệch; reload history | 3, 5, 8 |
| State | Online/offline; chuyển trạng thái khi disconnect; ACK nếu có | 2–3, 8 |
| Emoji | Unicode đa byte; tin tiếng Việt + emoji; lưu đọc lại | 3, 5 |
| Group | ≥3 client; join/leave; thành viên ngoài nhóm; member offline | 4, 8 |
| History | Restart; phân tách conversation; message lặp; lỗi storage | 5, 8 |
| File | File rỗng/lớn; checksum; hủy; disconnect; traversal; overwrite | 6, 8 |
| Image | Nhận/xem ảnh; định dạng lỗi; file lớn; sender/time | 7, 8 |
| Video | Nhận/mở; unsupported codec; hủy transfer; không block UI | 7, 8 |
| Sound | Nhận/phát; unsupported codec; lỗi playback; không block UI | 7, 8 |

**Tối thiểu cho thay đổi mạng/file:** một test luồng thành công, một test đầu vào sai, một test mất kết nối/timeout và một test dọn tài nguyên.

## 13. Rủi ro chính và biện pháp

| Rủi ro | Tác động | Hướng xử lý |
| --- | --- | --- |
| Chưa chốt topology/discovery | P2P/Group khó mở rộng | Chốt trước giai đoạn 02; tài liệu hóa quyết định |
| Framing và schema thông điệp thiếu nhất quán | Sai sender/time, mất hoặc trùng message | Contract versioned, parser test, message ID |
| UI xử lý I/O đồng bộ | Đơ khi mạng yếu hoặc truyền file | Background worker, state event, timeout |
| Group không thống nhất thành viên | Gửi sai người hoặc thiếu người | Membership contract, multi-client tests |
| History trộn hội thoại hoặc mất dữ liệu | Sai thông tin, giảm độ tin cậy | Key theo conversation/group ID; persistence tests |
| File path traversal/ghi đè | Mất dữ liệu hoặc vi phạm an toàn | Validate và chuẩn hóa đường dẫn; giới hạn quyền ghi |
| Media quá lớn/codec không hỗ trợ | Tăng RAM, treo UI hoặc lỗi phát | Streaming, giới hạn, player fallback |
| LAN không được xác thực/mã hóa | Có thể bị đọc trộm hoặc giả mạo | Đặc tả trust model; công bố hạn chế; bổ sung cơ chế bảo vệ khi được duyệt |
| Không có test trên LAN thực | Bản demo chỉ chạy loopback | Test tối thiểu trên hai máy và ghi bằng chứng |

## 14. Quy tắc theo dõi tiến độ

Trạng thái thống nhất cho từng task: **Not Started → In Progress → In Review → Verified**; thêm **Blocked** khi có phụ thuộc hoặc quyết định chưa chốt.

| Giai đoạn | Trạng thái ban đầu | Mốc |
| --- | --- | --- |
| 01 — Project foundation | Not Started | M0 |
| 02 — LAN networking | Not Started | M1 |
| 03 — P2P MVP | Not Started | M2 |
| 04 — Group chat | Not Started | M3 |
| 05 — Chat history | Not Started | M3 |
| 06 — File transfer | Not Started | M4 |
| 07 — Multimedia | Not Started | M4 |
| 08 — Integration & release | Not Started | M5 |

**Định nghĩa hoàn thành (Definition of Done) cho từng giai đoạn:**

- [ ] Acceptance criteria đã được xác minh bằng thao tác/test cụ thể.
- [ ] Build/test chạy thật và kết quả được lưu lại; không ghi `pass` nếu chưa chạy.
- [ ] Các lỗi mạng, dữ liệu không hợp lệ và cleanup phù hợp đã được xử lý.
- [ ] Không có vi phạm `MUST` của Constitution chưa được giải quyết.
- [ ] `spec.md`, `plan.md`, `tasks.md` và README được cập nhật theo phần thực sự triển khai.
- [ ] Quyết định còn mở, rủi ro và giới hạn được ghi nhận trước khi chuyển bước.

## 15. Bắt đầu từ đâu?

**Bước tiếp theo được đề xuất là Giai đoạn 01**, theo thứ tự:

1. Viết `specs/001-project-foundation/spec.md`.
2. Quyết định JDK, UI, build tool, test framework; ghi vào `plan.md`.
3. Làm `tasks.md` cho skeleton, entry point, test setup và protocol draft.
4. Chỉ khi project build/test được mới triển khai networking ở giai đoạn 02.
5. Sau khi kết nối LAN ổn định, ưu tiên hoàn thành MVP giai đoạn 03 để có bản demo hoạt động được.

**Điều chỉnh lộ trình:** nếu yêu cầu của môn học/giảng viên ưu tiên Group hoặc File sớm, có thể thay thứ tự giai đoạn 04–07 nhưng **không bỏ qua** protocol foundation, kiểm thử tối thiểu và các yêu cầu an toàn dữ liệu.

---

*Tài liệu này là kế hoạch định hướng. Trạng thái ban đầu không chứng minh tính năng nào đã hoàn thành; tiến độ cần được cập nhật khi có mã nguồn, test và bằng chứng nghiệm thu.*
