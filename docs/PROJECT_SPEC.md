---
spec_version: 1
project: KhaiRC
last_synced_commit: 8a8dc8665831f882c7a4e30f50a70f86a574b469
last_synced_at: 2026-11-30
last_full_scan_commit: b636c48de8c6ddf378b18fa4e71a66270c4ec1d6
---

# KhaiRC (Android) — Đặc tả dự án

> File này do skill `$docs` duy trì. Đọc theo mục bằng `python tools/spec.py <mục>`, không đọc cả file.
> Phạm vi: mã nguồn đã commit tại `last_synced_commit`; không gồm thay đổi chưa commit hay cấu hình trên điện thoại.

## 0. Tóm tắt nhanh

- App Android cá nhân điều khiển thiết bị trong nhà: quạt trần và Daikin qua IR của điện thoại, PC qua Wake-on-LAN, ổ Rạng Đông OC09 qua Tuya Cloud (Singapore), Toshiba AC và Milife 10A qua ESP32-S3 Hub (HTTPS, chứng chỉ tự ký được pin).
- Kotlin + Jetpack Compose + Material 3, Gradle đa module (`:app`, `:core:ir`, `:core:network`, `:core:vault`, `:feature:fan|ac|wol|tuya`). Không DI, không ViewModel: controller tạo thủ công trong `MainActivity.onCreate`.
- Nguyên tắc cốt lõi: một lần nhấn = tối đa một lệnh; không báo thành công khi chưa xác minh; secret mã hóa bằng Android Keystore và loại khỏi backup.
- Sao lưu cấu hình Tuya/Hub/HTTPS/WOL/nhãn OC09 lên Firestore bằng blob AES-GCM, mở bằng mật khẩu chủ sau đăng nhập Google (§5.10).
- UI và thông điệp lỗi viết tiếng Việt, hardcode trong Compose.

Nguồn: `README.md`, `settings.gradle.kts`, `app/src/main/java/com/khaiit/kremote/MainActivity.kt`.

## 1. Thiết bị & kênh điều khiển

| Thiết bị | Card (file) | Kênh | Phản hồi trạng thái |
|---|---|---|---|
| Quạt trần | `FanDeviceCard` (`feature/fan`) | IR điện thoại, 38028 Hz, 3 pattern cố định | Không (một chiều) |
| Daikin AC (BRC4C) | `DaikinAcDeviceCard` (`feature/ac/daikin`) | IR điện thoại, 38 kHz | Không; state lưu cục bộ |
| Toshiba AC | `ToshibaHttpsControlsCard` (`app`) | HTTPS → ESP32 Hub → IR | Không; Hub chỉ báo `sent`, `state_verified=false` |
| PC | `WolDeviceCard` (`feature/wol`) | UDP magic packet cổng 9 | Không; chỉ xác nhận đã gửi |
| Rạng Đông OC09 | `TuyaOc09DeviceCard` (`feature/tuya`) | Tuya Cloud REST (Singapore) | Có: đọc lại status Cloud |
| Milife 10A | `MilifeDeviceCard` (`app`) | HTTPS → Hub → miIO LAN | Có: bật/tắt và đọc lại trạng thái nguồn qua Hub |
| ESP32 Hub | `HubHttpsProbeCard` (`app`) | HTTPS `GET /api/v1/health` | Probe sức khỏe/TLS |

Card Toshiba điều khiển bằng IR của điện thoại (`ToshibaAcDeviceCard`) vẫn có trong `feature/ac/toshiba` nhưng `HomeScreen` không hiển thị (§10).

Nguồn: `app/src/main/java/com/khaiit/kremote/HomeScreen.kt`, `feature/*/src/main/java/**/*DeviceCard.kt`, `README.md`.

## 2. Toolchain, build, test

- AGP 9.3.2, Kotlin 2.2.10 (plugin `org.jetbrains.kotlin.plugin.compose`), Compose BOM 2026.02.01, Java 11, minSdk 26, compileSdk/targetSdk 37 (`compileSdk { version = release(37) }`).
- Version catalog: `gradle/libs.versions.toml`. `core/network` khai báo trực tiếp `kotlinx-coroutines-core:1.10.2`, lệch với catalog `kotlinx-coroutines = 1.11.0` (§10).
- Firebase BoM 34.4.0 (Auth/Firestore), Credentials 1.3.0 (kèm Play Services Auth), googleid 1.1.1; plugin google-services 4.4.4 khai ở root và áp dụng trong `:app`. Build cần `app/google-services.json` local (Git bỏ qua); plugin sinh `default_web_client_id`.
- `dependencyResolutionManagement` dùng `FAIL_ON_PROJECT_REPOS` (chỉ `google()`, `mavenCentral()`).
- Build/test (PowerShell, theo `AGENTS.md` §4):
  - Lệnh kiểm chứng chuẩn (`AGENTS.md` §4): `testDebugUnitTest` ở root (mọi module) + `:app:assembleDebug`, ghi log qua `cmd /c` vào `build\gradle.log`.
  - Mọi module có `src/test` đều khai báo `testImplementation(libs.junit)`, gồm `core/ir`, `feature/fan`, `feature/wol` với `ExampleUnitTest`.
  - Một test: `.\gradlew.bat :feature:tuya:testDebugUnitTest --tests "com.khaiit.kremote.feature.tuya.TuyaOc09ControllerTest"`
  - APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Unit test: JUnit 4 + fake viết tay (không mocking library). Có ở `core/network` (`HubClientTest`, `HubHttpsProbeTest`, `HubHttpsToshibaControlsClientTest`, `MilifeStatusContractTest`), `feature/ac` (`DaikinBrcEncoderTest`, `DaikinAcControllerTest`, `ToshibaAcEncoderTest`, `ToshibaAcControllerTest`), `feature/tuya` (`TuyaOc09ControllerTest`, `TuyaRequestSignerTest`, `TuyaTokenRecoveryTest`, `TuyaCredentialPrivacyTest`, `TuyaOc09LabelRulesTest`). Các module khác chỉ có `ExampleUnitTest`.
- Vault có 25 test JVM: `VaultCryptoTest`, `VaultCodecTest`, `VaultMergeTest`, `VaultPrivacyTest`, `VaultPreconditionTest`; `app` có 4 test `HttpsStoredEndpointTest`. Các test này không xác minh Firebase thực tế, rules đã deploy hoặc phần cứng.
- Instrumented: `app/src/androidTest/.../HubIntegrationTest.kt` (cần thiết bị); chỉ compile được bằng `:app:compileDebugAndroidTestKotlin`.
- Release: `optimization { enable = false }`; chưa có cấu hình ký release.

Nguồn: `gradle/libs.versions.toml`, `build.gradle.kts`, `settings.gradle.kts`, `app/build.gradle.kts`, `core/network/build.gradle.kts`, `core/ir/build.gradle.kts`, `feature/fan/build.gradle.kts`, `feature/wol/build.gradle.kts`, `core/vault/build.gradle.kts`, `core/vault/src/test/`, `app/src/test/java/com/khaiit/kremote/HttpsStoredEndpointTest.kt`.

## 3. Cấu trúc module & phụ thuộc

| Module | Namespace | Phụ thuộc project | Nội dung |
|---|---|---|---|
| `:app` | `com.khaiit.kremote` | tất cả module dưới | `MainActivity`, `HomeScreen`, theme, controller/card cho Hub (Toshiba HTTPS, Milife, probe), `HubSettingsStore` |
| `:core:ir` | `…core.ir` | — | `IrTransmitter`, `AndroidIrTransmitter` |
| `:core:network` | `…core.network` | — | WOL, `MacAddress`, `Ipv4Address`, mọi client Hub (HTTP LAN + HTTPS) |
| `:core:vault` | `…core.vault` | — | Codec/crypto/merge, Keystore, marker, Google Auth, Firestore; không phụ thuộc feature hay app |
| `:feature:fan` | `…feature.fan` | `:core:ir` | `FanController`, `FanIrCodes`, `FanDeviceCard` |
| `:feature:ac` | `…feature.ac` | `:core:ir` | Daikin (`daikin/`), Toshiba IR (`toshiba/`), `ui/PressFeedbackControls.kt` |
| `:feature:wol` | `…feature.wol` | `:core:network` | `WolController`, `WolSettings`, `WolConfig`, `WolDeviceCard` |
| `:feature:tuya` | `…feature.tuya` | — | OC09: repository, auth, signer, credential/label store, controller, card |

- `:app` dùng `ToshibaAcState`/`ToshibaAcStateStore`/`ToshibaFanSpeed` của `:feature:ac` cho card Toshiba qua Hub.
- Các module feature không phụ thuộc nhau; đổi một transport không được làm đổi hành vi module khác.

Nguồn: `settings.gradle.kts`, `*/build.gradle.kts`, `app/build.gradle.kts`.

## 4. Vòng đời app & màn hình

- `MainActivity.onCreate`:
  - Tạo `AndroidIrTransmitter`, `AndroidWakeOnLanSender`, các settings store (`WolSettings`, `DaikinAcSettings`, `ToshibaAcSettings`).
  - Tạo `TuyaOc09Controller(TuyaCredentialStore, TuyaCloudOc09Repository)`.
  - Tạo `MilifeController(api = HubHttpsMilifeClient { HttpsProbePreferences(...).read() }, store = HubSettingsStore, networkAllowed)`.
  - Tạo `ToshibaHttpsControlsController(api = HubHttpsToshibaControlsClient(), hubStore, stateStore = ToshibaAcSettings, endpointProvider, networkAllowed)`.
  - Tạo `VaultController(LocalVaultSources, GoogleVaultAuth, FirestoreVaultRemote, VaultKeyStore, VaultStateStore)` và `VaultAutoPush`; callback khôi phục/setup xong gọi `recreate()` để các controller đọc lại cấu hình.
  - `setContent` hiện `VaultGateScreen` khi vault chưa `Ready`, bật `FLAG_SECURE`; khi `Ready` hiện `HomeScreen` và gỡ flag. Install đã đồng bộ mở thẳng `Ready`.
  - Trong `setContent`, dùng `remember { }` cho `FanController`, `WolController`, `DaikinAcController`, `ToshibaAcController`.
- `networkAllowed`: `SDK_INT < 37 || checkSelfPermission(ACCESS_LOCAL_NETWORK) == GRANTED`.
- `onStart`: `vaultController.start()`, `lifecycleScope.launch { tuyaOc09Controller.refresh() }` và `milifeController.refresh()`. Không có polling định kỳ.
- `onDestroy`: `vaultAutoPush.close()` gỡ listener, `vaultController.close()` hủy scope; `milifeController.close()`, `toshibaHttpsControlsController.close()` (hủy `CoroutineScope` riêng).
- `HomeScreen` có 3 tab (`HomeTab.ALL`, `IR`, `NETWORK`). Tab chỉ chọn card hiển thị, controller không phụ thuộc tab. Thứ tự hiển thị:
  - IR: Fan → Toshiba HTTPS → Daikin.
  - Mạng: WOL → Tuya OC09 → Milife → HTTPS probe → `VaultCard` (sao lưu ngay hoặc mở lại thiết lập sau khi bỏ qua).
- Theme: `ui/theme/{Color,Theme,Type}.kt`, `KhaiRCTheme`.

Nguồn: `app/src/main/java/com/khaiit/kremote/MainActivity.kt`, `app/src/main/java/com/khaiit/kremote/HomeScreen.kt`, `app/src/main/java/com/khaiit/kremote/vault/`.

## 5. Module

### 5.1 IR điện thoại (`:core:ir`)

- `interface IrTransmitter { val isAvailable: Boolean; fun transmit(frequency: Int, pattern: IntArray) }`.
- `AndroidIrTransmitter(context)` bọc `ConsumerIrManager`. `isAvailable = hasIrEmitter()`. `transmit` dùng `require` để kiểm tần số > 0 và pattern không rỗng, dùng `check` để kiểm có emitter, nên ném exception khi máy không có IR. Gọi đồng bộ trên luồng gọi.
- Quyền `TRANSMIT_IR` khai báo trong Manifest của module.

Nguồn: `core/ir/src/main/java/com/khaiit/kremote/core/ir/*.kt`, `core/ir/src/main/AndroidManifest.xml`.

### 5.2 Wake-on-LAN (`:core:network` + `:feature:wol`)

- `AndroidWakeOnLanSender.wake(macAddress, targetAddress, port)` chạy trên `Dispatchers.IO`. Nó gửi magic packet unicast tới IP cấu hình và broadcast tới `192.168.2.255` (hardcode). Chỉ hỗ trợ IPv4.
- `WolController.saveConfig(mac, ip)`: trường để trống thì dùng `WolSettings.DEFAULT_MAC_ADDRESS`/`DEFAULT_TARGET_IP`. Hàm validate bằng `MacAddress`/`Ipv4Address`, rồi lưu prefs `wol_settings`.
- `WolSettings.hasSavedConfig()` kiểm tra tồn tại key `mac_address`; backup không coi giá trị mặc định chưa lưu là cấu hình local.
- `WolController.wake()` dùng cổng 9 và trả `Result<Unit>`.

Nguồn: `core/network/.../AndroidWakeOnLanSender.kt`, `MacAddress.kt`, `Ipv4Address.kt`, `feature/wol/src/main/java/com/khaiit/kremote/feature/wol/*.kt`.

### 5.3 Client ESP32 Hub (`:core:network`)

- **Kiểu chung** (`HubClient.kt`):
  - `HubEndpoint(ipv4)`: chỉ nhận IPv4 private dạng thập phân chuẩn, loại địa chỉ network/broadcast của 3 dải private.
  - `HubConfig(endpoint, apiKey)`: key gồm 64 ký tự ASCII in được; `toString` redacted.
  - `HubClientException(code)`.
  - `interface MilifeReadApi { suspend fun readMilife(config): Result<Boolean> }`.
- **`HubHttpsEndpoint(host, port, sha256Fingerprint)`** (`HubHttpsProbe.kt`):
  - `host` là IPv4 LAN hoặc hostname DNS chữ thường.
  - Fingerprint gồm 64 hex (cho phép có `:`).
  - `baseUrl = https://host:port`; `toString` ẩn pin.
- **`PinnedSelfSignedTrustManager`**: kiểm `checkValidity`, so SHA-256 của DER leaf bằng `MessageDigest.isEqual`, rồi `leaf.verify(publicKey)`. Không hỗ trợ client cert.
- **`HubHttpsProbe.check(endpoint)`**:
  - Gửi một `GET /api/v1/health`, không có API key, timeout 3 s/3 s, body ≤ 2048 B.
  - Hợp lệ khi `status=ok`, `device=KhaiRC-ESP32-Hub`, `phase=https_probe`, có `uptime_sec`/`free_heap`.
  - Trả `HubHttpsProbeResult.Verified|Failed(code)`.
- **`HubHttpsMilifeClient(endpointProvider).readMilife(config)`**:
  - Gửi một `GET /api/v1/devices/milife_plug_10a/state` kèm `X-API-Key`, readTimeout 8 s.
  - Cần `schema_version=1`, `device_id=milife_plug_10a`, `transport=miio_lan`, `state_verified=true`, `power` kiểu Boolean.
  - Lỗi trả `HubClientException` với các code: `https_not_configured`, `redirect_blocked`, `http_<n>`, `response_too_large`, `invalid_response`, `tls_validation_failed`, `timeout`, `network_permission`, `network_error`.
- **`HubHttpsMilifeClient.setPower(config, power)`** (`MilifePowerApi`):
  - Dùng cùng endpoint HTTPS đã lưu và API key cho POST và GET xác nhận. Gửi một POST, body cố định gồm `device_id` và `action`.
  - Chỉ trả `MilifePowerResult.Rejected(code)` khi thiếu cấu hình HTTPS hoặc nhận đúng cặp HTTP status và mã lỗi từ chối đã biết.
  - Sau HTTP 200, lỗi ghi chưa rõ hoặc mất phản hồi, thực hiện một GET và trả `ReadBack(power)`. `power=null` nếu GET không xác minh được. Không gửi lại POST.
  - `MilifeStatusContractTest` kiểm tra body bật/tắt và phân loại lỗi cần đọc lại.
- **`HubHttpsToshibaControlsClient.send(config, endpoint, command)`**:
  - Gửi một `POST /api/v1/commands`.
  - `ToshibaHttpsAction`: `State(power, temperatureC 17..30, fan)`, `Fix`, `SwingOn`, `SwingOff`. Body dựng từ chuỗi cố định.
  - 200 hợp lệ (`schema_version=1`, đúng `action`, `transport=ir`, `status=sent`, `state_verified=false`) → `Sent`.
  - Các lỗi đã biết → `Rejected(status, code)`: 401 `unauthorized`, 422 `unsupported_action`, 503 `device_not_ready`, 413, 415, 400 `invalid_json|invalid_command`.
  - Còn lại → `Uncertain(reason)`.
- **`HubClient`**: client HTTP LAN `http://<ipv4>:80`.
  - `inspect` đọc `/api/v1/health`, `/capabilities`, `/devices`.
  - `send(HubCommand)` với `FAN_HIGH`, `FAN_SWING`, `FAN_OFF`, `TOSHIBA_FIX`; `readMilife`.
  - Chỉ test dùng (§10).
- Mọi client HTTPS dùng `Proxy.NO_PROXY`, `instanceFollowRedirects=false`, `useCaches=false`, `Accept-Encoding: identity`, `Connection: close`, giải mã UTF-8 nghiêm ngặt (Milife, Toshiba) và chặn ký tự NUL.

Nguồn: `core/network/src/main/java/com/khaiit/kremote/core/network/{HubClient,HubHttpsProbe,HubHttpsMilifeClient,HubHttpsToshibaControlsClient}.kt`.

### 5.4 Quạt trần (`:feature:fan`)

- `FanController(irTransmitter)`: `powerHigh()`, `swing()`, `off()` phát pattern cố định trong `FanIrCodes` (`FREQUENCY = 38028`). `isIrAvailable` lấy từ transmitter.
- Quạt trần TAIZ dùng IR tích hợp trên điện thoại. Điều khiển TAIZ qua ESP32 Hub HTTPS nằm ngoài phạm vi dự án.

Nguồn: `feature/fan/src/main/java/com/khaiit/kremote/feature/fan/*.kt`.

### 5.5 Daikin AC (`:feature:ac/daikin`)

- `DaikinAcState`: nhiệt độ 18..30, `DaikinMode` (AUTO, COOL, DRY, FAN, HEAT), `DaikinFanSpeed` (LOW, HIGH), swing.
- `DaikinAcController(irTransmitter, stateStore)`: `togglePower`, `increaseTemperature`, `decreaseTemperature`, `cycleFanSpeed`, `toggleSwing`, `setMode`, `cycleMode`, `resendCurrentState`. Mọi thao tác đi qua `updateAndTransmit` và lưu qua `DaikinAcStateStore`.
- `DaikinBrcEncoder(profile)`:
  - `encode(state, modeButtonPressed)` trả `EncodedCommand(frequencyHz, pattern)`.
  - Frame 22 byte gồm preamble 7 byte và state frame 15 byte; mỗi phần có checksum.
  - Profile ở `DaikinBrcProfile` (38 kHz).
- Prefs `daikin_ac_settings` (`DaikinAcSettings`).
- Điều khiển Daikin AC qua ESP32 Hub HTTPS nằm ngoài phạm vi dự án.

Nguồn: `feature/ac/src/main/java/com/khaiit/kremote/feature/ac/daikin/*.kt`.

### 5.6 Toshiba AC IR (`:feature:ac/toshiba`)

- `ToshibaAcState`: power, nhiệt độ 17..30 (mặc định 24), `ToshibaFanSpeed` (AUTO, LEVEL_1..5), swing. `ToshibaAcStateStore` có `load`/`save`, được implement bởi `ToshibaAcSettings` (prefs `toshiba_ac_settings`).
- `ToshibaAcEncoder`:
  - 38 kHz. State frame 9 byte phát 2 lần; FIX dùng frame ngắn 7 byte phát 1 lần; swing phát 2 lần. Checksum XOR.
  - Các hàm `encodePowerOrCool`, `encodeFix`, `encodeSwingOn`, `encodeSwingOff`, `buildPattern`.
- `ToshibaAcController(irTransmitter, stateStore)` phát qua IR điện thoại. Không được wire vào UI (§10). Card Toshiba đang dùng chung `ToshibaAcSettings` làm state store.

Nguồn: `feature/ac/src/main/java/com/khaiit/kremote/feature/ac/toshiba/*.kt`.

### 5.7 UI dùng chung `:feature:ac`

- `ui/PressFeedbackControls.kt`: các control có phản hồi khi nhấn, dùng trong card AC.

Nguồn: `feature/ac/src/main/java/com/khaiit/kremote/feature/ac/ui/PressFeedbackControls.kt`.

### 5.8 Tuya OC09 (`:feature:tuya`)

- Luồng: `TuyaOc09DeviceCard` (gọi qua `rememberCoroutineScope`) → `TuyaOc09Controller` → `TuyaOc09Repository` (`TuyaCloudOc09Repository`) → Tuya Cloud.
- `TuyaOc09Controller(configStore, repository)` dùng `Mutex` để tuần tự hóa thao tác.
  - `saveConfiguration(clientId, secretInput, deviceId)`: trường trống thì giữ giá trị cũ; lưu xong thì `clearSession()`.
  - `refresh()`.
  - `toggle(code)`: POST một lệnh, rồi gọi `readStatusUntilConfirmed`.
  - `toggleMaster()`: một POST 6 lệnh, rồi gọi `readAllStatusUntilConfirmed`.
  - Đọc xác nhận tối đa `CONFIRM_READ_ATTEMPTS = 4` lần, chờ `CONFIRM_DELAY_MS = 500L × lần`, chỉ GET.
- `TuyaOc09State`: `channelValues`, `verified`, `busy`, `message`. `masterPower` = OR của 6 kênh, hoặc null khi chưa xác minh. `TuyaChannel.ALL` giữ ánh xạ vật lý (§6.1).
- `TuyaAuthManager.token(...)`: token chỉ cache trong RAM theo `elapsedRealtime`; `invalidate()` xóa token.
- `TuyaTokenRecovery.call(retryReadOnly, getToken, clearToken, request)`: khi gặp `TuyaTokenRejectedException` (mã `1010/1011/1012/1400`) thì xóa token; GET thử lại 1 lần, POST thì không.
- `TuyaRequestSigner.sign(...)`: HMAC-SHA256 trên `clientId + accessToken + t + nonce + stringToSign`, hex viết hoa.
- `TuyaCredentialStore`: AES-GCM, khóa trong Android Keystore. `TuyaCredentials.toString()` redacted. `TuyaOc09LabelStore` lưu tên ổ cắm (`MAX_LENGTH = 24`) ở prefs riêng.
- `TuyaOc09LabelBackup` là API public cho vault: `loadCustom()` chỉ trả nhãn tùy chỉnh; `saveCustom(map)` kiểm key thuộc `TuyaChannel.CODES` và chuẩn hóa qua `TuyaOc09LabelRules`. Store/rules gốc vẫn `internal`.
- Dialog cấu hình dùng `SecureFlagPolicy.SecureOn` và không điền sẵn credentials.

Nguồn: `feature/tuya/src/main/java/com/khaiit/kremote/feature/tuya/*.kt`, `README.md` §2, §4.

### 5.9 Controller & card Hub trong `:app`

- `HubSettingsStore` (implement `HubConfigStore`):
  - Lưu `HubSettings(config, fanTransport, fixTransport)` dạng JSON `schemaVersion = 1`, mã hóa AES-GCM bằng khóa Keystore `khairc_hub_aes_v1`, prefs `hub_secure_v1`.
  - `load()` không tạo khóa mới và không xóa cấu hình khi đọc lỗi, mà ném `IllegalStateException` kèm thông điệp tiếng Việt.
- `HttpsProbePreferences` (trong `HubHttpsProbeCard.kt`): prefs `hub_https_probe_v1` gồm `host`, `port` (mặc định 443), `sha256`; `read()` trả `HubHttpsEndpoint?` và nuốt lỗi để card hoạt động như trước. `readStored()` dùng `parseStoredHttpsEndpoint`: host/pin đều chưa có thì null, bản ghi thiếu/hỏng thì ném lỗi để vault strict chặn push.
- `MilifeController(api, store, networkAllowed)`:
  - `init` load settings rồi gọi `refresh()` nếu đã cấu hình và có quyền.
  - `refresh()` gửi một GET và ánh xạ `HubClientException.code` ra thông điệp. `power = null` nghĩa là chưa xác minh.
  - `setPower(power)` kiểm tra quyền mạng, chặn lệnh khi `busy`, khi chưa xác minh nguồn hoặc khi ổ đã ở trạng thái yêu cầu. Chỉ báo thành công khi GET xác nhận đúng trạng thái đích.
  - Thẻ chỉ bật nút tương ứng với hành động có thể thực hiện. Khi chưa xác minh được trạng thái, người dùng có thể bấm **Đọc lại trạng thái**.
  - `saveConfiguration(address, replacementKey)`: key trống thì giữ key cũ.
  - Dialog "Kết nối ESP32 Hub" nằm trong `MilifeDeviceCard` (`SecureFlagPolicy.SecureOn`).
- `ToshibaHttpsControlsController(api, hubStore, endpointProvider, stateStore, networkAllowed)`:
  - State `ToshibaHttpsUiState(model, busy, needsManualCheck, message)`.
  - `withModel` chặn thao tác khi `busy` hoặc `needsManualCheck`. Gate `AtomicBoolean` trong `submit`.
  - Chỉ `Sent` mới gọi `stateStore.save`. `Uncertain`, exception hay cancel đều bật `needsManualCheck`; người dùng gọi `confirmObserved()` để mở khóa.
  - Thao tác (trừ `togglePower`/`chooseCool`) chỉ chạy khi `power = true`.
- `HubHttpsProbeCard()`: nhập host/port/fingerprint và nút KIỂM TRA (gọi `HubHttpsProbe.check`). Fingerprint không điền sẵn, che ký tự; ô trống giữ pin cũ; dialog dùng `SecureFlagPolicy.SecureOn`.

Nguồn: `app/src/main/java/com/khaiit/kremote/{HubSettingsStore,HubHttpsProbeCard,MilifeController,MilifeDeviceCard,ToshibaHttpsControlsController,ToshibaHttpsControlsCard}.kt`.

### 5.10 Sao lưu cloud (`:core:vault` + `:app/vault`)

- `VaultBundle` gồm `TuyaPart(clientId, clientSecret, deviceId)`, `HubPart(ipv4, apiKey)`, `HttpsPart(host, port, fingerprintHex)`, `WolPart(mac, ip)`, `labels`; nhóm có thể null. Model chứa dữ liệu/khóa có `toString()` redacted.
- `VaultCodec.encode/decode` dùng Data streams, byte version 1, cờ có/không mỗi nhóm và `writeUTF`; nhãn được sắp key để digest ổn định. Version lạ, cờ sai, dữ liệu thiếu/thừa hoặc nhãn trùng bị từ chối.
- `VaultCrypto.deriveKek`: PBKDF2WithHmacSHA256, 256 bit, mặc định 600.000 vòng, salt 16 byte; xóa bản sao mật khẩu trong `PBEKeySpec`. Mật khẩu setup ít nhất 12 ký tự. `seal/open` dùng AES-GCM, IV mới 12 byte, tag 128 bit, AAD `khairc-vault-v1|<uid>`; sai tag thành `VaultAuthException`. PBKDF2/crypto chạy trên `Dispatchers.Default`.
- `VaultMerge.merge(local, cloud)`: nhóm local null/rỗng lấy cloud, nhóm có giữ local; trả `toApply`, `merged`, `needsPush = merged != cloud`. Không gộp từng kênh trong nhóm LABELS.
- `LocalVaultSources.readAll()` coi nhóm đọc lỗi là null cho restore/setup; `readAllStrict()` chặn push nếu có nhóm không đọc được (`VaultLocalReadException`). `apply()` validate mọi nhóm trước khi ghi qua store cũ; Hub giữ transport cũ, hoặc PHONE nếu chưa có; định dạng prefs cũ không đổi.
- `VaultController` dùng scope Main.immediate + SupervisorJob, gate AtomicBoolean. State: Ready, Checking, NeedSignIn, NeedPassword, NeedSetup, Working, Error; thêm `syncStatus`, `backupEnabled`.
- `start()`: marker `syncedForInstall` thì Ready, không fetch; dirty thì push. Install chưa đồng bộ: cần đăng nhập Google; fetch có doc thì NeedPassword, chưa có doc thì NeedSetup.
- `unlock()`: derive/open/decode, merge local/cloud, apply nhóm thiếu, wrap/lưu KEK, push bản gộp nếu cần, lưu marker/digest rồi recreate. Sai mật khẩu giữ màn nhập và không apply. Setup giữ giá trị local khi ô trống, ghi local, tạo salt/KEK mới rồi put; chỉ lưu KEK/marker hoàn tất sau ack cloud.
- `VaultAutoPush` giữ listener mạnh trên 5 prefs cấu hình (§7), mark dirty và debounce 1.500 ms; bỏ sự kiện trong lúc controller apply. Push strict dùng digest SHA-256 plaintext để bỏ bản không đổi; revision giữ dirty nếu local đổi trong lúc push.
- Put dùng `VaultPrecondition` trong transaction (§6.5). Khi push gặp `VaultKeyChangedException`, `resetSync()` giữ dirty, bỏ marker/digest, tắt backup; người dùng mở thiết lập để nhập mật khẩu mới.
- Gate che mật khẩu/secret/pin, không điền giá trị lưu sẵn; form giữ dữ liệu vừa nhập khi lỗi validation. `forgotPassword()` mở setup thay thế, UI xác nhận trước ghi đè; dữ liệu chỉ ở cloud cũ không thể lấy lại nếu quên mật khẩu.
- `skipThisSession()` chỉ bỏ gate trong phiên, không đánh dấu install đã đồng bộ; `reopenSetup()` mở lại từ card. Không có đổi mật khẩu chủ, đăng xuất/nhiều tài khoản; không backup state Daikin/Toshiba.
- Lỗi UI là chuỗi cố định tiếng Việt; không in exception gốc hay Log. Các lỗi riêng: `VaultFormatException`, `VaultAuthException`, `VaultNetworkException`, `VaultCancelledSignIn`, `VaultSignInException`, `VaultStorageException`, `VaultKeyChangedException`.

Nguồn: `core/vault/src/main/java/com/khaiit/kremote/core/vault/`, `app/src/main/java/com/khaiit/kremote/vault/`, `feature/tuya/.../TuyaOc09LabelStore.kt`, `feature/wol/.../WolSettings.kt`.

## 6. Giao tiếp ngoài

### 6.1 Tuya Cloud

- Base URL `https://openapi-sg.iotbing.com` (`TuyaCloudOc09Repository.BASE_URL`).
- Các endpoint:
  - `GET /v1.0/token?grant_type=1`
  - `GET /v1.0/iot-03/devices/{deviceId}/status`
  - `POST /v1.0/iot-03/devices/{deviceId}/commands`
- Ánh xạ kênh đã xác minh trên thiết bị: Ổ 1 `switch_5`, Ổ 2 `switch_4`, Ổ 3 `switch_3`, Ổ 4 `switch_2`, Ổ 5 `switch_1`, USB `switch_6`. Không có DP tổng `switch`.

Nguồn: `feature/tuya/.../TuyaOc09Repository.kt`, `TuyaAuthManager.kt`, `TuyaOc09State.kt`.

### 6.2 ESP32 Hub HTTPS

| Route | Method | Auth | Client Android |
|---|---|---|---|
| `/api/v1/health` | GET | không | `HubHttpsProbe.check` |
| `/api/v1/devices/milife_plug_10a/state` | GET | `X-API-Key` | `HubHttpsMilifeClient.readMilife` |
| `/api/v1/commands` (`device_id=milife_plug_10a`) | POST | `X-API-Key` | `HubHttpsMilifeClient.setPower` |
| `/api/v1/commands` (`device_id=toshiba_ac`) | POST | `X-API-Key` | `HubHttpsToshibaControlsClient.send` |

- TLS: chứng chỉ tự ký, pin SHA-256 DER do người dùng nhập; hostname phải có trong SAN. Cách lấy fingerprint xem `docs/HUB_HTTPS_ANDROID_STAGE1.md`.
- MiLife gửi JSON với đúng hai trường chuỗi: `device_id=milife_plug_10a` và `action=power_on` hoặc `power_off`. App xác nhận nguồn qua GET, kể cả khi POST trả HTTP 200.
  - `422 unsupported_action`, `504 miot_hello_timeout` và `502 miot_device_rejected` là các ví dụ từ chối rõ ràng. `504 miot_write_timeout` và `502 miot_write_unconfirmed` cần GET kiểm tra.
- Hợp đồng phía firmware nằm ở repo `KhaiRC-ESP32-Hub` (`docs/PROJECT_SPEC.md` §6.5 của repo đó). Phần Toshiba đã đối chiếu lúc tạo spec (firmware `d66cbf6`):
  - `state` có đúng 5 field: `power` bool, `temperature_c` 17–30, `fan_speed` `auto`/`1`–`5`. Các action khác có đúng 2 field.
  - `500 ir_send_failed` và lỗi đọc body (firmware đóng socket, không phản hồi) không nằm trong danh sách `Rejected` của app, nên app xếp vào `Uncertain`.

Nguồn: `core/network/.../HubHttps*.kt`, `docs/HUB_HTTPS_ANDROID_STAGE1.md`.

### 6.3 ESP32 Hub HTTP LAN (cổng 80)

- `HubClient` gọi `http://<ipv4>:80/api/v1/{health,capabilities,devices,commands,devices/milife_plug_10a/state}`.
- Chính sách mạng mặc định chặn cleartext. Bản debug chỉ cho phép `127.0.0.1` để `HubIntegrationTest` chạy với server loopback; luồng điều khiển Hub trong app dùng HTTPS (§6.2).

Nguồn: `core/network/.../HubClient.kt`, `app/src/{main,debug}/res/xml/network_security_config.xml`, `app/src/androidTest/.../HubIntegrationTest.kt`.

### 6.4 Wake-on-LAN & IR

- WOL: UDP cổng 9, unicast + broadcast `192.168.2.255`.
- IR: `ConsumerIrManager.transmit(frequency, pattern)`, pattern tính bằng micro giây (mark/space xen kẽ).

Nguồn: `core/network/.../AndroidWakeOnLanSender.kt`, `core/ir/.../AndroidIrTransmitter.kt`.

### 6.5 Firebase Auth & Firestore

- `GoogleVaultAuth` dùng Credential Manager + `GetGoogleIdOption(filterByAuthorizedAccounts=false)`, server client ID từ resource `default_web_client_id`, đổi Google ID token qua `FirebaseAuth.signInWithCredential`.
- `FirestoreVaultRemote.fetch(uid)` đọc server bằng `Source.SERVER`; document cố định `users/{uid}/vault/main`. Firestore dùng `MemoryCacheSettings`, không dùng disk cache mặc định; thao tác remote giới hạn chờ 20 giây.
- `SealedDoc`: `schemaVersion=1`, `kdfAlg=PBKDF2-SHA256`, `iterations`, `salt`, `iv`, `ct` (ba mảng byte dạng Base64); put thêm `updatedAt = FieldValue.serverTimestamp()`. Không ghi plaintext cấu hình lên document.
- Put đọc doc và kiểm `VaultPrecondition.allows` trong cùng transaction: `MustNotExist` cho setup lần đầu; `SameKey` yêu cầu salt khớp hoặc doc chưa có; `Overwrite` cho thay thế đã xác nhận. Từ chối điều kiện thì không ghi và ném `VaultKeyChangedException`. Transaction ghi cloud có thể retry, độc lập bất biến không retry lệnh thiết bị POST/IR.
- `Task.await()` tự nối bằng `suspendCancellableCoroutine`. `docs/firebase/firestore.rules` chỉ cho người đã đăng nhập đọc/ghi `users/{uid}/vault/{doc}` của chính UID, chặn đường dẫn khác; file trong repo không chứng minh rules đã deploy.

Nguồn: `core/vault/.../VaultAuth.kt`, `VaultRemote.kt`, `SealedDoc.kt`, `VaultPrecondition.kt`, `docs/firebase/firestore.rules`, `app/.../MainActivity.kt`.

## 7. Dữ liệu & cấu hình

| SharedPreferences | Chủ sở hữu | Nội dung | Mã hóa | Loại khỏi backup |
|---|---|---|---|---|
| `vault_secure_v1` | `VaultKeyStore` | `kek` (IV + ciphertext KEK), `salt`, `iterations` | KEK wrap AES-GCM, alias `khairc_vault_aes_v1`; metadata không mã hóa | Có |
| `vault_state_v1` | `VaultStateStore` | `syncedForInstall`, `dirty`, `lastPushDigest`, `lastSyncAt` | Không | Có |
| `hub_secure_v1` | `HubSettingsStore` | IPv4 Hub, API key, transport | AES-GCM, alias `khairc_hub_aes_v1` | Có |
| `hub_https_probe_v1` | `HttpsProbePreferences` | host, port, fingerprint (công khai) | Không | Không |
| `tuya_oc09_secure` | `TuyaCredentialStore` | Client ID/Secret, Device ID | AES-GCM, alias `khairc_tuya_oc09_aes_v1` | Có |
| `tuya_oc09_labels` | `TuyaOc09LabelStore` / `TuyaOc09LabelBackup` | tên ổ cắm | Không | Không |
| `daikin_ac_settings` | `DaikinAcSettings` | state Daikin | Không | Không |
| `toshiba_ac_settings` | `ToshibaAcSettings` | state Toshiba (dùng chung IR/HTTPS) | Không | Không |
| `wol_settings` | `WolSettings` | MAC, IP đích | Không | Không |

- Cloud vault khác Android Auto Backup: 5 nhóm Tuya/Hub/HTTPS/WOL/LABELS nằm trong blob mã hóa; hai prefs vault đều bị loại khỏi Android cloud backup và device transfer.
- Auto-push theo dõi `tuya_oc09_secure`, `hub_secure_v1`, `hub_https_probe_v1`, `wol_settings`, `tuya_oc09_labels`; không theo dõi state điều khiển AC.
- Loại trừ backup ở `app/src/main/res/xml/backup_rules.xml` (cloud backup) và `data_extraction_rules.xml` (cloud + device transfer).
- Quyền:
  - `TRANSMIT_IR` (`core/ir`).
  - `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_LOCAL_NETWORK` (`core/network`).
  - `INTERNET` (`feature/tuya`, `core/vault`).

Nguồn: các file store ở trên, `core/vault/.../VaultKeyStore.kt`, `VaultStateStore.kt`, `app/.../vault/VaultAutoPush.kt`, `app/src/main/AndroidManifest.xml`, `core/vault/src/main/AndroidManifest.xml`, `*/src/main/AndroidManifest.xml`, `app/src/main/res/xml/*.xml`.

## 8. Quy ước & pattern của codebase

- Thứ tự nguồn sự thật: code tại HEAD > `AGENTS.md` (quy tắc bắt buộc) > `docs/PROJECT_SPEC.md` (hiện trạng kỹ thuật) > `README.md` (hướng dẫn người dùng). Không lấy chi tiết kỹ thuật từ README; tài liệu mâu thuẫn code thì tin code và sửa hoặc báo tài liệu sai.
- Controller có Compose state `var state by mutableStateOf(...)` + `private set`.
  - Controller async có scope riêng `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)`, implement `AutoCloseable` và được `close()` trong `MainActivity.onDestroy`.
  - Tuya dùng `suspend fun` + `Mutex`; card gọi qua `rememberCoroutineScope`.
- I/O mạng và Keystore chạy trong `withContext(Dispatchers.IO)`. Bắt `CancellationException` thì ném lại.
- API/store là interface hoặc `fun interface` (`MilifeReadApi`, `ToshibaHttpsControlsApi`, `HubConfigStore`, `TuyaConfigStore`, `TuyaOc09Repository`, `IrTransmitter`, `WakeOnLanSender`, `ToshibaAcStateStore`) để test bằng fake.
- Kết quả gửi lệnh tách 3 loại: Sent (đã phát hoặc đã xác minh), Rejected (server từ chối rõ ràng, không có gì xảy ra), Uncertain (có thể đã chạy). Chỉ GET được retry; POST/IR không bao giờ tự gửi lại.
- Trạng thái chưa xác minh biểu diễn bằng `null` (`MilifeUiState.power`, `TuyaOc09State.masterPower`) chứ không giữ giá trị cũ.
- Secret: mã hóa AES-GCM/Keystore; `toString()` redacted; dialog nhập dùng `SecureFlagPolicy.SecureOn`; trường trống thì giữ giá trị cũ; không hiển thị lại secret.
- Thông điệp lỗi tiếng Việt, không chứa nội dung exception gốc (tránh lộ dữ liệu giải mã), xem `HubSettingsStore.load`.
- Body request điều khiển Hub dựng từ từ vựng cố định (enum/sealed) nên không có input người dùng trong JSON.
- Quy định agent: `AGENTS.md` (vùng cấm đọc, bất biến §3, toolchain §4, phân vai §5, chế độ im lặng/đơn luồng §7).

Nguồn: các controller ở §5, `AGENTS.md`.

## 9. Cách thêm tính năng mới

- **Thiết bị IR mới (điện thoại):**
  - Encoder thuần (không phụ thuộc Android) kiểu `ToshibaAcEncoder`/`DaikinBrcEncoder` trả `EncodedCommand(frequencyHz, pattern)`, có unit test so frame/pattern.
  - Controller nhận `IrTransmitter` và state store dạng interface.
  - Card Compose; wire trong `MainActivity` + `HomeScreen` (đúng tab).
- **Lệnh mới qua ESP32 Hub:**
  - Thêm vào từ vựng cố định (`ToshibaHttpsAction` hoặc sealed type mới), kiểm response chặt như `HubHttpsToshibaControlsClient.send`, phân loại Sent/Rejected/Uncertain.
  - Đối chiếu route và mã lỗi với spec firmware.
  - Controller giữ gate chống bấm chồng và `needsManualCheck`.
  - Thêm test trong `core/network/src/test`.
- **Đọc trạng thái thiết bị mới qua Hub:** theo mẫu `HubHttpsMilifeClient` (một GET, `state_verified=true` mới tin), ánh xạ mã lỗi ra thông điệp như `MilifeController.refresh`.
- **Tuya DP mới:** xác minh DP trên thiết bị thật trước, thêm vào `TuyaChannel`. Sau POST phải đọc lại để xác minh, không gửi lại lệnh.
- **Lưu secret mới:** dùng store AES-GCM/Keystore với alias riêng, thêm prefs vào cả hai file backup rules, `toString` redacted, test kiểu `TuyaCredentialPrivacyTest`.
- **Nhóm cấu hình cần backup:** cập nhật model/codec/merge, nguồn đọc/validate/apply local, listener và UI gate; thêm test roundtrip/privacy/merge. Push cloud dùng transaction có điều kiện, không gửi lệnh thiết bị.
- **Mở rộng vault:** khi thêm nhóm cấu hình, cập nhật `VaultBundle`/`VaultCodec`/`VaultMerge`, validate/read/apply trong `LocalVaultSources`, listener và gate; thêm test roundtrip/merge/privacy. Khi đổi format phải xử lý version; không đẩy snapshot thiếu nhóm do đọc lỗi và không bỏ precondition transaction.
- **Quyền mới:** khai báo ở Manifest của module dùng nó. Quyền runtime thì kiểm qua lambda giống `networkAllowed`.
- Quy trình phân vai: `/architect` → `$implement` → thử trên điện thoại → `$reviewer` → commit → `$docs` (`docs/AGENT_WORKFLOW.md`). Tiến độ nằm trong `docs/PLAN.md`, được `.gitignore` bỏ qua; mỗi vai dùng chat riêng.
- `python tools/spec.py` in mục lục; truyền số mục hoặc từ khóa tiêu đề để đọc chọn lọc. Công cụ chỉ đọc spec, cần Python 3.7+.

Nguồn: `AGENTS.md`, các module ở §5, `docs/AGENT_WORKFLOW.md`, `.gitignore`, `tools/spec.py`.

## 10. Vấn đề đã biết, TODO, cần xác nhận

- `HubClient` (HTTP LAN cổng 80) và enum `HubCommand` chỉ được gọi từ `HubClientTest`, `MilifeStatusContractTest` và `HubIntegrationTest`. App chặn cleartext tới Hub LAN (§6.3).
- `HubSettings.fanTransport`/`fixTransport` (`HubTransport.PHONE|HUB`) vẫn được lưu và đọc nhưng không còn logic nào dùng.
- `ToshibaAcController`/`ToshibaAcDeviceCard` (IR điện thoại) không hiển thị. `HomeScreen` vẫn nhận tham số `toshibaAcController` nhưng không dùng.
- `HomeTab` có subtitle đếm thiết bị hardcode ("6 thiết bị", "3 thiết bị"), dễ lệch khi thêm/bớt card.
- WOL hardcode broadcast `192.168.2.255` và mặc định MAC/IP trong `WolSettings`, nên chỉ đúng với LAN hiện tại.
- `core/network` khai báo `kotlinx-coroutines-core:1.10.2` trực tiếp, lệch với catalog `kotlinx-coroutines = 1.11.0`.
- README lệch: §6 thiếu module `:core:vault` trong danh sách kiến trúc.
- README lệch: §4a gọi card probe là “HTTPS Hub · Stage 1”, trong code tiêu đề là “HTTPS Hub · Stage 2A”.
- README lệch: §1 và §8 còn ghi MiLife chỉ đọc hoặc chưa điều khiển nguồn. Code đã hỗ trợ bật/tắt qua HTTPS và xác nhận trạng thái bằng GET.
- Cần xác nhận cấu hình Firebase Google provider/SHA-1, rules đã deploy và luồng hai máy đổi thế hệ khóa; unit test JVM chỉ kiểm crypto/codec/merge/precondition/parser, không xác minh dịch vụ thực tế.
- Không có `android.util.Log`: khi chẩn đoán, bằng chứng chủ yếu là thông điệp UI và crash logcat.
- Chưa có cấu hình ký release; `optimization` đang tắt.
Nguồn: `README.md` §1, §4a, §6, §8, `settings.gradle.kts`, `app/.../HubHttpsProbeCard.kt`, `core/vault/src/test/`, `app/src/test/`, các file liên quan ở §5.

## 11. Nhật ký đồng bộ

- 2026-11-30 · INCREMENTAL · `2be4cb8..8a8dc86` · Cập nhật bật/tắt MiLife qua HTTPS, đọc lại trạng thái và kiểm tra hợp đồng. Ghi nhận mục README còn mô tả MiLife chỉ đọc.
- 2026-11-17 · INCREMENTAL · `ad2827f..651f1e7` · Bổ sung thứ tự nguồn sự thật ở §8; đối chiếu README sau khi thống nhất lệnh kiểm chứng và chuyển chi tiết kiến trúc sang spec.
- 2026-11-16 · INCREMENTAL · `ad2827f..2be4cb8` · Thêm vault mã hóa, Google Auth/Firestore transaction, gate/auto-push, prefs/backup rules và test; đối chiếu README.
- 2026-11-13 · INCREMENTAL · `b636c48..ad2827f` · Xác nhận JUnit cho core/ir, feature/fan, feature/wol; bổ sung quy trình PLAN và công cụ đọc spec.
- 2026-11-13 · FULL · `b636c48` · Tạo spec lần đầu cùng bộ skill agent (`AGENTS.md`, `.claude/skills`, `.agents/skills`).

Nguồn: lịch sử commit Git, `AGENTS.md`, `README.md`, front matter của `docs/PROJECT_SPEC.md`.

## 12. Bản đồ file → mục

| File / thư mục | Mục |
|---|---|
| `settings.gradle.kts`, `build.gradle.kts`, `*/build.gradle.kts` | §2, §3 |
| `gradle/libs.versions.toml`, `gradle.properties` | §2 |
| `app/src/main/java/com/khaiit/kremote/MainActivity.kt` | §4 |
| `app/src/main/java/com/khaiit/kremote/HomeScreen.kt`, `ui/theme/*` | §4 |
| `app/src/main/java/com/khaiit/kremote/vault/**` | §4, §5.10, §6.5, §7 |
| `core/vault/**` | §2, §3, §5.10, §6.5, §7 |
| `docs/firebase/firestore.rules` | §6.5 |
| `app/.../HubSettingsStore.kt`, `HubHttpsProbeCard.kt` | §5.9, §7 |
| `app/.../MilifeController.kt`, `MilifeDeviceCard.kt` | §5.9 |
| `app/.../ToshibaHttpsControlsController.kt`, `ToshibaHttpsControlsCard.kt` | §5.9 |
| `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/*` | §6.3, §7 |
| `app/src/androidTest/**`, `app/src/test/**` | §2 |
| `core/ir/**` | §5.1, §6.4 |
| `core/network/.../AndroidWakeOnLanSender.kt`, `WakeOnLanSender.kt`, `MacAddress.kt`, `Ipv4Address.kt` | §5.2, §6.4 |
| `core/network/.../HubClient.kt` | §5.3, §6.3 |
| `core/network/.../HubHttpsProbe.kt`, `HubHttpsMilifeClient.kt`, `HubHttpsToshibaControlsClient.kt` | §5.3, §6.2 |
| `core/network/src/test/**` | §2, §5.3 |
| `feature/fan/**` | §5.4 |
| `feature/ac/.../daikin/**` | §5.5 |
| `feature/ac/.../toshiba/**` | §5.6 |
| `feature/ac/.../ui/**` | §5.7 |
| `feature/wol/**` | §5.2 |
| `feature/tuya/**` | §5.8, §6.1, §7 |
| `docs/HUB_HTTPS_ANDROID_STAGE1.md` | §6.2 |
| `README.md` | §0, §1, §5.10, §10 |
| `AGENTS.md`, `docs/AGENT_WORKFLOW.md` | §8, §9 |
| `.gitignore`, `tools/spec.py` | §9 |

Nguồn: `git ls-files`, `settings.gradle.kts`, các file trong bảng.
