# Quy trình dùng agent cho KhaiRC Android (Claude CLI + Codex)

Tài liệu này dành cho người dùng, không phải cho agent. Quy tắc cho agent nằm ở `AGENTS.md`, `CLAUDE.md`, `.claude/skills/` và `.agents/skills/`.

## 1. Nguyên tắc

- Ngữ cảnh là tài nguyên khan hiếm. Mỗi lượt agent gửi lại toàn bộ ngữ cảnh, nên phần luôn được nạp (`AGENTS.md`) phải nhỏ, phần lớn (`docs/PROJECT_SPEC.md`) phải đọc theo mục.
- Mỗi sự thật nằm ở một nơi: `AGENTS.md` giữ bất biến, `docs/PROJECT_SPEC.md` giữ hiện trạng code, skill giữ quy trình, `docs/PLAN.md` giữ việc đang làm (không commit).
- Agent làm im lặng và báo một lần khi xong; nói rõ phần đã xong, chưa xong, đã kiểm chứng, giả định.
- Mỗi bước có kiểm chứng chạy được. Cùng một lỗi sửa quá 2 lần thì dừng.

## 2. Các vai (mỗi vai một chat mới)

| Bước | Công cụ | Gõ | Kết quả |
|---|---|---|---|
| 1. Plan | Claude CLI (chế độ thường, không plan mode) | `/architect <yêu cầu>` | `docs/PLAN.md` |
| 2. Code | Codex | `$implement` (hoặc `$implement 2`, `$implement 2-3`) | Code, build/test, Step được tick `[x]` |
| 3. Thử | Người dùng | Làm theo mục 7 của `docs/PLAN.md` trên điện thoại | Quan sát thiết bị thật |
| 4. Review | Codex | `$reviewer` | Báo cáo Blocker/Major/Minor |
| 5. Commit | Người dùng | | |
| 6. Docs | Codex | `$docs` (`$docs check` để xem trước) | Spec đồng bộ HEAD |
| Lỗi lạ | Claude CLI | `/debugger` + thông điệp trên card / logcat | Chẩn đoán, chưa sửa |

Model mạnh nhất với effort cao dành cho Plan và Debug. Code và Review dùng effort vừa. Docs dùng model rẻ.

## 3. Chạy một tính năng từ đầu

1. Mở Claude CLI ở gốc repo, gõ ví dụ `/architect Thêm nút đọc lại trạng thái Toshiba qua Hub`.
2. `/architect` tạo `docs/PLAN.md` rồi dừng, kèm câu hỏi mở. Đọc plan, sửa nếu cần, trả lời câu hỏi (ghi vào file hoặc nói trong chat).
3. Mở chat Codex mới, gõ `$implement`. Codex làm hết các Step, chạy lệnh kiểm chứng chuẩn ở `AGENTS.md` §4 một lần, tick `[x]` và báo cáo. Nó dừng khi plan mâu thuẫn với code, cần sửa file ngoài plan, hoặc lỗi lặp quá 2 lần.
4. Cài `app/build/outputs/apk/debug/app-debug.apk` và thử theo mục 7 của plan (agent không cài APK).
5. Mở chat Codex mới, gõ `$reviewer`. Sửa Blocker/Major (bảo `$implement` hoặc sửa tay).
6. Tự commit, rồi mở chat Codex mới chạy `$docs`.

Chat bị cắt giữa chừng thì mở chat Codex mới và gõ `$implement`, vì tiến độ đã nằm trong `docs/PLAN.md`.

## 4. Tiết kiệm token

- Đọc spec theo mục:
  - `python tools/spec.py` in mục lục kèm dung lượng.
  - `python tools/spec.py 0 8 9 5.8` đọc theo số mục.
  - `python tools/spec.py Tuya` đọc theo từ khóa.
- Gradle ghi ra `build/gradle.log`, agent chỉ đọc 25 dòng cuối hoặc dòng lỗi.
- Một việc một chat. Chat dài thì dùng `/compact`.
- Gom các commit nhỏ của một tính năng rồi mới chạy `$docs` một lần.

## 5. Cấu hình

- Codex: `%USERPROFILE%\.codex\config.toml`. Gợi ý:
  - `model_reasoning_effort = "medium"`
  - `plan_mode_reasoning_effort = "high"`
  - `tool_output_token_limit = 10000`
  - Khóa toàn cục phải đứng trước mọi dòng `[bảng]`.
- Nếu sandbox Codex chặn Gradle (lỗi ghi `%USERPROFILE%\.gradle` hoặc tải dependency), thêm thư mục đó vào `writable_roots` trong `[sandbox_workspace_write]` và cho phép mạng, rồi chạy lại. Điều này chưa được thử trên máy này.
- `.gitignore` có `/docs/PLAN.md` và `*.patch`. Nếu bộ chọn `@` không thấy `PLAN.md`, gõ thẳng đường dẫn.

## 6. Xử lý sự cố nhanh

| Hiện tượng | Cách xử lý |
|---|---|
| Codex gõ `$` không thấy skill | Khởi động lại Codex; kiểm tra `.agents/skills/<tên>/SKILL.md` có `name` trùng tên thư mục |
| Claude gõ `/architect` không thấy | Mở Claude CLI ở gốc repo; kiểm tra `.claude/skills/architect/SKILL.md` |
| `$implement` báo không có PLAN.md | Chạy `/architect` trong Claude CLI trước |
| `python tools/spec.py` lỗi | Chạy từ gốc repo; cần Python 3.7+ |
| Build lỗi `SDK location not found` | Tạo `local.properties` với `sdk.dir` (file cục bộ, không commit) |
| Card báo `tls_validation_failed` | Fingerprint/hostname sai; xem `docs/HUB_HTTPS_ANDROID_STAGE1.md`, hoặc chạy `/debugger` |
