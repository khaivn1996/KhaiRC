# KhaiRC (Android) — Agent Directives

<!-- docs-skill:start -->
## Đặc tả dự án
Trước khi thêm hoặc sửa tính năng, đọc các mục liên quan của `docs/PROJECT_SPEC.md` (cách đọc theo mục: §5) để nắm kiến trúc, module, giao tiếp ngoài và quy ước.
Sau khi hoàn thành và commit một tính năng, chạy `$docs` để đồng bộ đặc tả.
<!-- docs-skill:end -->

## 1. Bảo mật & Giới hạn quét file
- **TUYỆT ĐỐI KHÔNG** đọc, in hoặc đưa vào ngữ cảnh: `*.jks`, `*.keystore`, `keystore.properties`, `.env`, giá trị thật của Tuya Client ID/Secret/Device ID, `HUB_API_KEY`.
- **KHÔNG** quét/tìm kiếm trong `build/`, `**/build/`, `.gradle/`, `.kotlin/`, `.idea/`, `.git/`, `*.patch`, `*.apk` (ngoại lệ: đọc đúng `build/gradle.log` theo §4).
- Đọc chéo repo Hub `D:\IoT\KhaiRC-ESP32-Hub` (nếu có trên máy) chỉ qua `python D:\IoT\KhaiRC-ESP32-Hub\tools\spec.py <mục>`; cấm đọc `include/secrets.h`, `include/ota_password.h`, `include/tls_private_key.pem`, `include/tls_credentials.h`, `include/miot_config.h` của repo đó.
- Trước khi sửa một file `.kt`, đọc trọn file (trạng thái `private`/`internal` và companion constant nằm rải rác; file lớn nhất là `core/network/.../HubClient.kt` ~420 dòng).

## 2. Ràng buộc nền tảng
- minSdk 26, compileSdk/targetSdk 37, Java 11, AGP 9.3.2, Kotlin 2.2.10, Compose BOM (`gradle/libs.versions.toml`, `app/build.gradle.kts`).
- Không DI framework: mọi controller được khởi tạo thủ công trong `MainActivity.onCreate` (`app/.../MainActivity.kt`). Không thêm dependency/thư viện mới khi chưa hỏi người dùng.
- Chuỗi UI tiếng Việt, viết trực tiếp trong Compose; giữ nguyên ngôn ngữ đó.
- Môi trường: Windows + PowerShell. Không có `adb`/thiết bị trong vòng lặp agent.

## 3. Bất biến điều khiển & bảo mật
- **Một lần nhấn = tối đa một lệnh.** Không auto-retry POST/IR. Chỉ GET đọc trạng thái được retry: `TuyaTokenRecovery.call(retryReadOnly = true, …)` thử lại một lần khi mã token `1010/1011/1012/1400`.
- **Không báo thành công từ POST.** Tuya đọc lại qua `TuyaOc09Controller.readStatusUntilConfirmed`/`readAllStatusUntilConfirmed` (chỉ đọc, không gửi lại lệnh). Trạng thái IR và Toshiba-qua-Hub là *ước lượng*; kết quả `Uncertain` bật `needsManualCheck` trong `ToshibaHttpsControlsController.submit` và chặn lệnh tới khi `confirmObserved()`. Chỉ lưu state cục bộ sau ack hợp lệ.
- **TLS Hub:** chỉ tin leaf đã pin qua `PinnedSelfSignedTrustManager.checkServerTrusted` (`HubHttpsProbe.kt`); giữ `HttpsURLConnection.getDefaultHostnameVerifier()`, `instanceFollowRedirects = false`, `Proxy.NO_PROXY`, không fallback HTTP, giới hạn body phản hồi 2048 B. Cấm trust-all/`HostnameVerifier` dễ dãi.
- Body JSON gửi Hub chỉ dựng từ từ vựng cố định (`ToshibaHttpsAction`, `HubCommand`); không đưa input người dùng vào body.
- Ánh xạ OC09 cố định: ổ 1–5 = `switch_5`..`switch_1`, USB = `switch_6` (`TuyaChannel.ALL`); Power tổng = một POST sáu lệnh.
- Secret không hardcode, không điền sẵn vào dialog, che trong `toString()` (`TuyaCredentials`, `HubConfig`, `HubSettings`, `HubHttpsEndpoint`). Thêm SharedPreferences chứa secret thì mã hóa AES-GCM/Android Keystore và thêm vào cả `backup_rules.xml` lẫn `data_extraction_rules.xml`.
- Gọi mạng LAN phải qua lambda `networkAllowed` (kiểm `ACCESS_LOCAL_NETWORK` khi SDK ≥ 37).
- Coroutine: bắt `CancellationException` thì ném lại; I/O mạng trong `withContext(Dispatchers.IO)`; Compose state chỉ ghi trên Main.
- Không bao giờ commit file `*.patch`; gặp thì xóa.

## 4. Toolchain & Kiểm chứng (PowerShell)
- **Lệnh kiểm chứng chuẩn (build + unit test mọi module):** `cmd /c ".\gradlew.bat --console=plain testDebugUnitTest :app:assembleDebug > build\gradle.log 2>&1"; "exit=$LASTEXITCODE"; Get-Content build\gradle.log -Tail 25`. Không in cả log.
  - Ghi log qua `cmd /c … > … 2>&1`, không dùng `*>` của PowerShell 5.1: `*>` ghi UTF-16 và ngắt dòng lỗi Kotlin.
  - Mọi module có `src/test` phải khai báo `testImplementation(libs.junit)`, nếu không `testDebugUnitTest` ở root sẽ fail biên dịch.
- Lỗi: `Select-String build\gradle.log -Pattern '^e: |FAILED|error:' | Select-Object -First 15`.
- Một test: trong lệnh chuẩn, thay `testDebugUnitTest :app:assembleDebug` bằng `:feature:tuya:testDebugUnitTest --tests "com.khaiit.kremote.feature.tuya.TuyaOc09ControllerTest"`.
- Instrumented test (`app/src/androidTest`) cần thiết bị: không tự chạy, chỉ compile bằng `:app:compileDebugAndroidTestKotlin`.
- Trước khi báo xong: `git diff --check`.
- Cùng một lỗi build/test sửa quá 2 lần chưa xong thì dừng, đọc lại lỗi gốc rồi báo cáo; không sửa mò.
- **Task chỉ đổi cấu hình agent/tài liệu (Markdown/YAML):** không chạy Gradle. Kiểm chứng: `git diff --stat`; quét ký tự ẩn `Select-String -Path CLAUDE.md,AGENTS.md,docs\*.md,.claude\skills\*\SKILL.md,.agents\skills\*\SKILL.md,.agents\skills\*\agents\openai.yaml -Pattern '[\u200B-\u200F\u00A0\uFEFF\u2060\u202A-\u202E\u00AD]'` (mong đợi: không có kết quả); mỗi file `.md` có số dòng bắt đầu bằng ``` hoặc `~~~` là số chẵn; nếu sửa `docs/PROJECT_SPEC.md` thì `python tools/spec.py` phải in mục lục bình thường.

## 5. Quy trình phân vai (mỗi vai một chat riêng)
| Vai | Công cụ | Lệnh | Kết quả |
|---|---|---|---|
| Plan | Claude CLI | `/architect <yêu cầu>` | Chỉ ghi `docs/PLAN.md` (Zero-Discovery, bị Git bỏ qua) |
| Code | Codex | `$implement [phạm vi]` | Sửa liền mạch các Step, build/test và tick `[x]` một lần ở cuối |
| Review | Codex | `$reviewer` | Chỉ đọc diff, chạy kiểm chứng, báo Blocker/Major/Minor |
| Docs | Codex | `$docs` | Sau commit, đồng bộ `docs/PROJECT_SPEC.md` |
| Debug | Claude CLI | `/debugger` | Chỉ chẩn đoán từ bằng chứng, chưa sửa |

- **Đọc `docs/PROJECT_SPEC.md` theo mục, không đọc cả file:** `python tools/spec.py` (mục lục), `python tools/spec.py 0 8 9 5.8`. Luôn đọc §0 và §8; thêm §9 khi thêm tính năng; thêm mục module/§6 liên quan (tra bản đồ file → mục ở §12). `$implement` không gọi lại `spec.py` (dùng thông tin trong `PLAN.md`).
- Không tự `git commit`/`git push`/cài APK. Xong việc = build sạch + unit test liên quan pass + danh sách thao tác người dùng phải thử trên điện thoại. Ngoại lệ: task chỉ đổi Markdown/YAML kiểm chứng theo §4 (bullet cuối).

## 6. Context7 MCP — Tra cứu tài liệu kỹ thuật
- Chỉ gọi Context7 khi cần tra API/cú pháp thư viện chưa rõ sau khi đã kiểm tra `gradle/libs.versions.toml` và source cục bộ.
- Ưu tiên tài liệu đúng phiên bản đang dùng; ràng buộc trong `AGENTS.md` và `docs/PROJECT_SPEC.md` luôn ưu tiên hơn tài liệu mẫu.
- Không gửi secret, token, API key hay nội dung file nhạy cảm vào truy vấn.

## 7. Chế độ thực thi im lặng & Đơn luồng
- **CẤM gọi subagent:** làm 100% trên luồng chính; không subagent, background task hay explore agent dưới mọi hình thức.
- **CẤM báo cáo trung gian:** trong một lượt, gọi nối tiếp các tool đọc/sửa/kiểm tra tới khi xong toàn bộ yêu cầu; không xuất tin nhắn tiến độ giữa các bước. Chỉ dừng giữa chừng khi thiếu thông tin bắt buộc, phát hiện mâu thuẫn với mã nguồn, hoặc lỗi build/test lặp quá 2 lần (§4).
- **Trung thực bằng chứng:** đối chiếu mã nguồn và kết quả build/test thực tế trước khi kết luận. Phân biệt rõ **đã xác minh**, **chưa xác minh**, **giả định**; không nhận đã pass khi chưa chạy lệnh. Điểm chưa hiểu hoặc giả định chưa chứng minh phải nêu ra để làm rõ, không tự lấp.
- **Báo cáo cuối (một lần):** đang chạy skill thì theo mẫu của skill. Làm tự do thì gồm: việc đã xong, việc chưa xong, kết quả kiểm chứng, rủi ro/tồn đọng, thao tác người dùng cần làm.
