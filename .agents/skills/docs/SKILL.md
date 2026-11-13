---
name: docs
description: Tạo và đồng bộ file đặc tả dự án (docs/PROJECT_SPEC.md) cho repo Android KhaiRC (Kotlin, Compose, IR, WOL, Tuya Cloud, ESP32 Hub HTTPS). Lần chạy đầu quét toàn bộ repo; các lần sau chỉ đọc commit mới kể từ lần đồng bộ trước. Chỉ dùng khi người dùng gọi $docs.
effort: medium
---

# docs — Đặc tả dự án tự đồng bộ

Skill này duy trì MỘT file đặc tả: `docs/PROJECT_SPEC.md`. Mục tiêu: trước khi làm tính năng mới chỉ cần đọc các mục liên quan (qua `python tools/spec.py`) là nắm kiến trúc (module, vòng đời app, giao tiếp ngoài, dữ liệu, quy ước, cách thêm tính năng).

- Chưa có spec: **FULL SCAN** toàn bộ repo.
- Các lần sau: **INCREMENTAL**, chỉ xem các commit mới kể từ `last_synced_commit` trong front matter.

Đối số tùy chọn:
- `$docs full`: ép quét lại toàn bộ.
- `$docs check`: chỉ báo sẽ làm gì (chế độ, file ảnh hưởng), KHÔNG ghi file.

## Nguyên tắc bắt buộc

1. **Thực thi đơn luồng & im lặng:** không gọi subagent; làm trọn một mạch, chỉ báo cáo 1 lần ở cuối.
2. Chỉ ghi điều xác minh được từ repo (code, Gradle, Manifest, resources, README, test). Không suy đoán. Chưa chắc → §10 "Cần xác nhận". README mâu thuẫn code → tin code, ghi mâu thuẫn vào §10.
3. **Bảo mật (`AGENTS.md` §1):** không đọc keystore/secret; không ghi giá trị API key, Client ID/Secret, Device ID, fingerprint thật hay token vào spec. Chỉ ghi tên prefs/key/alias.
4. Chỉ được sửa: (a) `docs/PROJECT_SPEC.md`, (b) khối marker trong `AGENTS.md` (nếu chưa có). Không sửa code, không build, không tự commit.
5. Viết tiếng Việt; giữ nguyên identifier, đường dẫn, tên lệnh.
6. Spec mô tả hiện trạng (thì hiện tại). Lịch sử chỉ ở §11. Mỗi mục kết thúc bằng dòng `Nguồn: ...` liệt kê file chính.
7. Không dán code dài: chỉ chữ ký public, bảng, hoặc ví dụ ≤ 10 dòng khi thật cần.
8. Spec gọn (≤ ~600 dòng), giữ cấu trúc đánh số (`## 0.`, `## 1.`, `### 5.1`…) để `python tools/spec.py` parse được.

## Bước 0 — Xác định chế độ

~~~powershell
git rev-parse --show-toplevel
git rev-parse HEAD
git status --porcelain
~~~

Không phải git repo → báo rồi dừng. Đọc front matter của `docs/PROJECT_SPEC.md` (nếu có) lấy `last_synced_commit`, rồi chọn:

| Điều kiện | Chế độ |
|---|---|
| Spec chưa có, thiếu front matter / `last_synced_commit`, hoặc gọi `full` | FULL |
| `git rev-parse --verify --quiet "<last>^{commit}"` thất bại (rebase/force-push) | FULL, nêu lý do |
| Sau khi lọc không còn file thay đổi | UP-TO-DATE: báo "spec đã khớp HEAD", KHÔNG ghi gì |
| Thay đổi > 60 file, hoặc đụng > một nửa số module trong §12 | FULL |
| Còn lại | INCREMENTAL |

~~~powershell
git log --no-merges --format="%h %s" <last>..HEAD
git diff --name-status -M <last> HEAD
~~~

Nếu `git merge-base --is-ancestor <last> HEAD` thất bại vẫn dùng `git diff <last> HEAD` nhưng ghi chú trong báo cáo.

**Bộ lọc — bỏ qua:** `docs/PROJECT_SPEC.md`, `build/`, `**/build/`, `.gradle/`, `.kotlin/`, `.idea/`, `.git/`, `.agents/`, `.claude/`, `CLAUDE.md`, `*.patch`, `*.apk`, `gradle/wrapper/*.jar`, `local.properties`, `res/mipmap*/`, `res/drawable*/` (ảnh), file nhị phân.

Có thay đổi chưa commit thì KHÔNG đưa vào spec (spec phản ánh HEAD); nhắc người dùng commit rồi chạy lại. Với `$docs check`: in chế độ, khoảng commit, danh sách file ảnh hưởng, rồi dừng.

## Chế độ FULL SCAN

1. **Kiểm kê:** `git ls-files` (trừ vùng lọc).
2. **Đọc theo thứ tự:**
   1. `AGENTS.md`, `README.md`, `docs/*.md` (trừ spec) — ý định thiết kế, ràng buộc.
   2. `settings.gradle.kts`, `build.gradle.kts`, `*/build.gradle.kts`, `*/*/build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`.
   3. `app/src/main/AndroidManifest.xml` và Manifest từng module; `app/src/main/res/xml/*.xml`.
   4. `app/.../MainActivity.kt` (composition root), `HomeScreen.kt`, rồi đi theo từng controller tới module `core/*`, `feature/*`.
   5. Test (`*/src/test`, `app/src/androidTest`).
   6. `git grep -n -E "TODO|FIXME|HACK|XXX" -- app core feature` → §10.
3. **Viết spec** theo cấu trúc §0–§12 hiện có; điền §12 (bản đồ file → mục) đầy đủ.
4. Làm "Bước cuối".

## Chế độ INCREMENTAL

1. Có danh sách commit và `--name-status` đã lọc.
2. `python tools/spec.py 12` xem bản đồ file → mục, rồi chỉ đọc mục bị ảnh hưởng bằng `python tools/spec.py <mục>`:
   - **A:** xác định module; module mới → thêm tiểu mục §5 và dòng §12.
   - **M:** đọc `git diff <last> HEAD -- <file>` và phần liên quan của file hiện tại, cập nhật đúng mục.
   - **D:** gỡ tham chiếu (kể cả §12, dòng `Nguồn:`), cập nhật §10 nếu liên quan.
   - **R:** sửa đường dẫn ở mọi nơi.
3. Tín hiệu liên mục:
   - `libs.versions.toml`, `*/build.gradle.kts`, lệnh build/test → §2; `settings.gradle.kts`, module mới → §3
   - `MainActivity`, `HomeScreen`, vòng đời/tab → §4
   - code trong `core/*`, `feature/*`, controller/card trong `app` → §5.x
   - client HTTP Hub, Tuya REST, WOL, IR protocol, mã lỗi → §6
   - SharedPreferences, Keystore alias, `backup_rules.xml`/`data_extraction_rules.xml`, quyền Manifest → §7
   - quy ước mới → §8 và checklist §9
   - commit sửa vấn đề trong §10 → xóa khỏi §10; TODO mới → thêm vào §10
4. Chỉ sửa mục bị ảnh hưởng, giữ nguyên phần còn lại; KHÔNG viết lại cả file.
5. Thêm một dòng vào §11 (giữ tối đa ~30 dòng gần nhất).

## Bước cuối (cả hai chế độ)

1. Front matter: `last_synced_commit` = `git rev-parse HEAD` (đủ 40 ký tự), `last_synced_at` = hôm nay (`YYYY-MM-DD`). Sau FULL đặt thêm `last_full_scan_commit` cùng giá trị.
2. Đảm bảo `AGENTS.md` có cặp marker `<!-- docs-skill:start -->` … `<!-- docs-skill:end -->`. Đã có thì **không đụng**; chưa có mới chèn:

~~~markdown
<!-- docs-skill:start -->
## Đặc tả dự án
Trước khi thêm hoặc sửa tính năng, đọc các mục liên quan của `docs/PROJECT_SPEC.md` (cách đọc theo mục: §5) để nắm kiến trúc, module, giao tiếp ngoài và quy ước.
Sau khi hoàn thành và commit một tính năng, chạy `$docs` để đồng bộ đặc tả.
<!-- docs-skill:end -->
~~~

3. **Tự kiểm tra:** `python tools/spec.py` in mục lục bình thường; mọi identifier mới ghi vào spec tìm thấy bằng `git grep -n "<tên>" -- app core feature`; không có secret thật; front matter khớp HEAD.
4. **Đối chiếu README (chỉ đọc, không sửa):** README là hướng dẫn người dùng; spec là nguồn sự thật kỹ thuật (`AGENTS.md` §0). Đọc các phần README có lệnh build/test, tên module, route, prefs, ánh xạ DP. Chỗ nào lệch với spec/code thì ghi một dòng `README lệch: <mục README> — <đúng là gì>` vào §10 và nêu trong báo cáo. Khi README đã sửa khớp thì gỡ dòng đó khỏi §10.
5. **Báo cáo** (≤ ~10 dòng): chế độ và lý do, khoảng commit (`<last7>..<head7>`), mục đã sửa, mục mới ở §10 cần xác nhận (gồm "README lệch"), nhắc commit file spec.
