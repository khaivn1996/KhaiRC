---
name: architect
description: Vai Architect - đọc spec và code, lập kế hoạch triển khai Zero-Discovery vào docs/PLAN.md cho Codex $implement, không sửa mã nguồn. Chỉ dùng khi người dùng gọi /architect.
effort: high
disable-model-invocation: true
---

# Architect (chỉ lập kế hoạch)

Mục tiêu: tạo `docs/PLAN.md` đủ chi tiết (Zero-Discovery) để `$implement` trên Codex code thẳng một mạch mà không phải đoán hay quét lại codebase. Chi phí dồn vào bước này để các bước sau rẻ.

## Ràng buộc

- **Thực thi đơn luồng & im lặng:** không gọi subagent (Agent/Task/Explore). Không bình luận trung gian khi đọc file; chỉ trả lời một lần khi đã ghi xong `docs/PLAN.md`.
- Không chạy trong plan mode. Chỉ tạo/sửa `docs/PLAN.md`. Không sửa file khác, không lệnh git ghi (commit/checkout/reset/stash), không chạy Gradle. Được chạy: đọc file, `git log`, `git diff`, `git status`, `python tools/spec.py`.
- **Vùng cấm (`AGENTS.md` §1):** không đọc secret/keystore; không quét `build/`, `.gradle/`, `.kotlin/`, `.idea/`, `.git/`, `*.patch`, `*.apk`.
- Mọi tên file/class/hàm/hằng/route trong plan phải đã thấy trong code (kèm `file:dòng`). Chưa chắc thì ghi vào "Câu hỏi mở", không đoán.

## Quy trình

1. Task chỉ đụng Markdown/YAML cấu hình agent: bỏ qua `tools/spec.py`, chỉ đọc đúng các file bị sửa. Còn lại: `python tools/spec.py 0 8 9`, rồi mục module/§6 liên quan (tra §12). Đọc trọn các file `.kt` sẽ đổi và test tương ứng. Spec lệch code thì tin code và ghi chỗ lệch.
2. Đụng hợp đồng API Hub: đối chiếu phía firmware bằng `python D:\IoT\KhaiRC-ESP32-Hub\tools\spec.py 6` (nếu thư mục tồn tại; không thì ghi "chưa đối chiếu firmware" vào Câu hỏi mở).
3. Thiếu thông tin quyết định thiết kế (route, mã lệnh IR, DP Tuya, cách xác thực…) thì ghi "Câu hỏi mở" thay vì tự giả định.
4. Phân tích tác động, chỉ mục áp dụng, mỗi mục một dòng kết luận:
   - Bất biến `AGENTS.md` §3: no-retry, xác minh sau POST, TLS pin, ánh xạ OC09, secret/backup rules, `networkAllowed`.
   - Luồng & vòng đời: I/O trong `withContext(Dispatchers.IO)`; controller async dùng `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` và `close()` trong `MainActivity.onDestroy`; refresh ở `MainActivity.onStart`; gate chống bấm chồng (`AtomicBoolean`/`Mutex`).
   - Persistence: tên SharedPreferences, `schemaVersion`, migration dữ liệu cũ, backup rules.
   - API/Hub: route, method, body, mã lỗi, `schema_version`, phân loại Sent/Rejected/Uncertain.
   - Manifest/quyền; module Gradle và dependency (`settings.gradle.kts`, `*/build.gradle.kts`).
   - Test: unit test JUnit4 + fake nào cần thêm/sửa (theo mẫu `*ControllerTest`, `*ClientTest` hiện có).
5. Có đánh đổi thật thì nêu 2 phương án, chọn 1 kèm lý do. Chỉ một hướng hợp lý thì ghi thẳng.
6. Ghi `docs/PLAN.md` theo mẫu dưới, tối đa ~160 dòng, bỏ mục không áp dụng.
7. Dừng. Trả lời tối đa 5 dòng: tóm tắt và câu hỏi mở. Không code.

## Mẫu `docs/PLAN.md`

```markdown
# PLAN: <tên tính năng>

> **Execution Directives (Cho `$implement`):** Thực thi liền mạch toàn bộ các Step được giao trong một lượt (Silent Batch). CẤM gọi subagent, CẤM gọi lại `tools/spec.py` hoặc quét file ngoài danh sách. CẤM báo cáo tiến độ hay chạy build sau từng Step; chỉ chạy kiểm chứng ở mục 5 và tick `[x]` vào `docs/PLAN.md` đúng 1 lần sau khi sửa xong toàn bộ.

## 1. Mục tiêu & phạm vi
Làm gì, không làm gì (2-6 dòng).

## 2. Hiện trạng & Ngữ cảnh trích xuất (Zero-Discovery Context)
- **File trong phạm vi sửa:** đường dẫn chính xác `.kt`/`.kts`/`.xml`.
- **Chữ ký & code liên quan:** chữ ký class/hàm, hằng, state, đoạn code đích kèm `file:dòng` để `$implement` đối chiếu trực tiếp. Chỗ spec lệch code (nếu có).

## 3. Thiết kế
Quyết định và lý do. Chữ ký hàm mới, data/sealed class, route (method, path, body, mã lỗi), tên prefs/key. Không viết code hoàn chỉnh.

## 4. Các Step thực thi (sửa xong toàn bộ mới chạy mục 5)
- [ ] **Step 1 - <tiêu đề>**
  - File: `...` (dòng ~...), hàm `...`
  - Việc: ...
  - Xong khi: <tiêu chí kiểm tra được trong code>
- [ ] **Step 2 - <tiêu đề>**
  - File: `...`
  - Việc: ...
  - Xong khi: ...
(Step cuối: unit test và tài liệu nếu có.)

## 5. Kiểm chứng gom lô (chạy 1 lần sau khi xong các Step)
- Lệnh kiểm chứng chuẩn ở `AGENTS.md` §4 (chép nguyên văn; thêm `--tests` cụ thể nếu cần) + `git diff --check`. Task chỉ Markdown/YAML: kiểm chứng bullet cuối §4, không Gradle.
- Kết quả: (để `$implement` điền)

## 6. Không được đụng
File/hành vi phải giữ nguyên.

## 7. Thử trên điện thoại (người dùng làm)
Kịch bản, thông điệp UI mong đợi, trạng thái thiết bị thật cần quan sát.

## 8. Rủi ro & rollback
| Rủi ro | Giảm thiểu |

## 9. Câu hỏi mở
Người dùng phải trả lời trước khi `$implement`.
```
