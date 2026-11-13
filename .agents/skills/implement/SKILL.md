---
name: implement
description: Vai Coder - thực hiện liền mạch toàn bộ các Step được giao trong docs/PLAN.md, chỉ chạy build/test Gradle và tick tiến độ một lần ở cuối. Chỉ dùng khi người dùng gọi $implement.
effort: medium
---

# Implement (code theo plan - Silent Batch Mode)

Đối số: `$implement` (toàn bộ Step còn `[ ]`) | `$implement 2` | `$implement 2-3`.
Tiến độ nằm trong `docs/PLAN.md` (ô `[ ]`/`[x]`), nên chat mới có thể làm tiếp mà không cần lịch sử cũ.

## Quy tắc Thực thi Im lặng & Tiết kiệm Token (Bắt buộc)

- **CẤM gọi subagent:** làm 100% trên luồng chính, không subagent hay background task.
- **CẤM báo cáo trung gian:** gọi nối tiếp các tool đọc/sửa cho tới khi xong mọi Step trong phạm vi. Không xuất tin nhắn kiểu "Đã xong Step 1".
- **Không đọc lặp spec:** thông tin thiết kế đã có trong `docs/PLAN.md`. Không gọi `python tools/spec.py` trừ khi plan yêu cầu rõ.
- **Vùng cấm (`AGENTS.md` §1):** không đọc secret/keystore; không quét `build/` (trừ `build/gradle.log`), `.gradle/`, `.kotlin/`, `.idea/`, `.git/`, `*.patch`, `*.apk`.

## Quy trình (1 mạch duy nhất)

1. **Đọc & kiểm tra điều kiện:**
   - Đọc `docs/PLAN.md`. Không có file thì dừng, bảo người dùng chạy `/architect` trong Claude CLI. Mục "Câu hỏi mở" còn chưa trả lời thì dừng và hỏi.
   - Xác định các Step cần làm (theo đối số hoặc mọi Step còn `[ ]`).
   - Đọc trọn một lượt các file đích (`.kt`, `.kts`, `.xml`) và test liên quan.
2. **Sửa code liền mạch:**
   - Áp dụng đúng thay đổi của mọi Step trong phạm vi.
   - Chỉ sửa file có trong plan (cộng import/test bắt buộc). Cần sửa file ngoài plan, hoặc plan mâu thuẫn với code thực tế (chữ ký/dòng không khớp) thì dừng ngay, nêu lý do và hỏi.
3. **Kiểm chứng gom lô 1 lần ở cuối (`AGENTS.md` §4):**
   - Chạy đúng lệnh ở mục 5 của plan; mặc định là lệnh kiểm chứng chuẩn ở `AGENTS.md` §4, rồi `git diff --check`. Plan chỉ sửa Markdown/YAML: chạy kiểm chứng bullet cuối §4, không Gradle.
   - Lỗi: `Select-String build\gradle.log -Pattern '^e: |FAILED|error:' | Select-Object -First 15`, sửa theo lỗi gốc. Cùng một lỗi quá 2 lần chưa xong thì dừng và báo cáo.
   - Sandbox chặn Gradle (lỗi quyền ghi `~/.gradle` hoặc mạng tải dependency) thì không lách; báo người dùng cấu hình theo `docs/AGENT_WORKFLOW.md` và ghi rõ "chưa kiểm chứng".
4. **Cập nhật `docs/PLAN.md` đúng 1 lần:** khi build/test pass, đổi các Step vừa xong từ `[ ]` thành `[x]`, ghi kết quả (số test pass, `BUILD SUCCESSFUL`) và mục "Phát hiện thêm" (nếu có) trong **một thao tác ghi file duy nhất**. Không pass thì không tick; ghi Step nào xong code nhưng chưa kiểm chứng được.

## Cấm

- Không commit, push, cài APK (`adb install`), không chạy instrumented test trên thiết bị.
- Không phá bất biến `AGENTS.md` §3: không thêm retry cho POST/IR, không nới TLS (trust-all, `HostnameVerifier`, redirect, HTTP), không đưa secret vào code/log/`toString()`, không đổi ánh xạ OC09.
- Không thêm dependency, không mở rộng phạm vi, không refactor/format lại ngoài plan. Điều đáng sửa nhưng ngoài plan ghi vào "Phát hiện thêm" ở cuối `docs/PLAN.md` trong thao tác ghi ở bước 4.

## Kết thúc

Xuất **1 báo cáo duy nhất** (tối đa 8 dòng): Step đã xong / chưa xong, kết quả build/test (số test, có chạy hay không), thao tác người dùng phải thử trên điện thoại (theo mục 7 của plan), và gợi ý chạy `$reviewer` ở chat mới.
