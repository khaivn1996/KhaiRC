---
spec_version: 1
project: KhaiRC
last_synced_commit: ad2827f766920939686759c5eca6b148f09af236
last_synced_at: 2026-11-13
last_full_scan_commit: b636c48de8c6ddf378b18fa4e71a66270c4ec1d6
---

# KhaiRC (Android) — Đặc tả dự án

> File này do skill `$docs` duy trì. Đọc theo mục bằng `python tools/spec.py <mục>`, không đọc cả file.
> Phạm vi: mã nguồn đã commit tại `last_synced_commit`; không gồm thay đổi chưa commit hay cấu hình trên điện thoại.

## 0. Tóm tắt nhanh

- App Android cá nhân điều khiển thiết bị trong nhà: quạt trần và Daikin qua IR của điện thoại, PC qua Wake-on-LAN, ổ Rạng Đông OC09 qua Tuya Cloud (Singapore), Toshiba AC và Milife 10A qua ESP32-S3 Hub (HTTPS, chứng chỉ tự ký được pin).
- Kotlin + Jetpack Compose + Material 3, Gradle đa module (`:app`, `:core:ir`, `:core:network`, `:feature:fan|ac|wol|tuya`). Không DI, không ViewModel: controller tạo thủ công trong `MainActivity.onCreate`.
- Nguyên tắc cốt lõi: một lần nhấn = tối đa một lệnh; không báo thành công khi chưa xác minh; secret mã hóa bằng Android Keystore và loại khỏi backup.
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
| Milife 10A | `MilifeDeviceCard` (`app`) | HTTPS → Hub → miIO LAN | Có, chỉ đọc; nút BẬT/TẮT bị khóa |
| ESP32 Hub | `HubHttpsProbeCard` (`app`) | HTTPS `GET /api/v1/health` | Probe sức khỏe/TLS |

Card Toshiba điều khiển bằng IR của điện thoại (`ToshibaAcDeviceCard`) vẫn có trong `feature/ac/toshiba` nhưng `HomeScreen` không hiển thị (§10).

Nguồn: `app/src/main/java/com/khaiit/kremote/HomeScreen.kt`, `feature/*/src/main/java/**/*DeviceCard.kt`, `README.md`.

## 2. Toolchain, build, test

- AGP 9.3.2, Kotlin 2.2.10 (plugin `org.jetbrains.kotlin.plugin.compose`), Compose BOM 2026.02.01, Java 11, minSdk 26, compileSdk/targetSdk 37 (`compileSdk { version = release(37) }`).
- Version catalog: `gradle/libs.versions.toml`. `core/network` khai báo trực tiếp `kotlinx-coroutines-core:1.10.2`, lệch với catalog `kotlinx-coroutines = 1.11.0` (§10).
- `dependencyResolutionManagement` dùng `FAIL_ON_PROJECT_REPOS` (chỉ `google()`, `mavenCentral()`).
- Build/test (PowerShell, theo `AGENTS.md` §4):
  - Lệnh kiểm chứng chuẩn (`AGENTS.md` §4): `testDebugUnitTest` ở root (mọi module) + `:app:assembleDebug`, ghi log qua `cmd /c` vào `build\gradle.log`.
  - Mọi module có `src/test` đều khai báo `testImplementation(libs.junit)`, gồm `core/ir`, `feature/fan`, `feature/wol` với `ExampleUnitTest`.
  - Một test: `.\gradlew.bat :feature:tuya:testDebugUnitTest --tests "com.khaiit.kremote.feature.tuya.TuyaOc09ControllerTest"`
  - APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Unit test: JUnit 4 + fake viết tay (không mocking library). Có ở `core/network` (`HubClientTest`, `HubHttpsProbeTest`, `HubHttpsToshibaControlsClientTest`, `MilifeStatusContractTest`), `feature/ac` (`DaikinBrcEncoderTest`, `DaikinAcControllerTest`, `ToshibaAcEncoderTest`, `ToshibaAcControllerTest`), `feature/tuya` (`TuyaOc09ControllerTest`, `TuyaRequestSignerTest`, `TuyaTokenRecoveryTest`, `TuyaCredentialPrivacyTest`, `TuyaOc09LabelRulesTest`). Các module khác chỉ có `ExampleUnitTest`.
- Instrumented: `app/src/androidTest/.../HubIntegrationTest.kt` (cần thiết bị); chỉ compile được bằng `:app:compileDebugAndroidTestKotlin`.
- Release: `optimization { enable = false }`; chưa có cấu hình ký release.

Nguồn: `gradle/libs.versions.toml`, `build.gradle.kts`, `settings.gradle.kts`, `app/build.gradle.kts`, `core/network/build.gradle.kts`, `core/ir/build.gradle.kts`, `feature/fan/build.gradle.kts`, `feature/wol/build.gradle.kts`.

## 3. Cấu trúc module & phụ thuộc

| Module | Namespace | Phụ thuộc project | Nội dung |
|---|---|---|---|
| `:app` | `com.khaiit.kremote` | tất cả module dưới | `MainActivity`, `HomeScreen`, theme, controller/card cho Hub (Toshiba HTTPS, Milife, probe), `HubSettingsStore` |
| `:core:ir` | `…core.ir` | — | `IrTransmitter`, `AndroidIrTransmitter` |
| `:core:network` | `…core.network` | — | WOL, `MacAddress`, `Ipv4Address`, mọi client Hub (HTTP LAN + HTTPS) |
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
  - Trong `setContent`, dùng `remember { }` cho `FanController`, `WolController`, `DaikinAcController`, `ToshibaAcController`.
- `networkAllowed`: `SDK_INT < 37 || checkSelfPermission(ACCESS_LOCAL_NETWORK) == GRANTED`.
- `onStart`: `lifecycleScope.launch { tuyaOc09Controller.refresh() }` và `milifeController.refresh()`. Không có polling định kỳ.
- `onDestroy`: `milifeController.close()`, `toshibaHttpsControlsController.close()` (hủy `CoroutineScope` riêng).
- `HomeScreen` có 3 tab (`HomeTab.ALL`, `IR`, `NETWORK`). Tab chỉ chọn card hiển thị, controller không phụ thuộc tab. Thứ tự hiển thị:
  - IR: Fan → Toshiba HTTPS → Daikin.
  - Mạng: WOL → Tuya OC09 → Milife → HTTPS probe.
- Theme: `ui/theme/{Color,Theme,Type}.kt`, `KhaiRCTheme`.

Nguồn: `app/src/main/java/com/khaiit/kremote/MainActivity.kt`, `app/src/main/java/com/khaiit/kremote/HomeScreen.kt`.

## 5. Module

### 5.1 IR điện thoại (`:core:ir`)

- `interface IrTransmitter { val isAvailable: Boolean; fun transmit(frequency: Int, pattern: IntArray) }`.
- `AndroidIrTransmitter(context)` bọc `ConsumerIrManager`. `isAvailable = hasIrEmitter()`. `transmit` dùng `require` để kiểm tần số > 0 và pattern không rỗng, dùng `check` để kiểm có emitter, nên ném exception khi máy không có IR. Gọi đồng bộ trên luồng gọi.
- Quyền `TRANSMIT_IR` khai báo trong Manifest của module.

Nguồn: `core/ir/src/main/java/com/khaiit/kremote/core/ir/*.kt`, `core/ir/src/main/AndroidManifest.xml`.

### 5.2 Wake-on-LAN (`:core:network` + `:feature:wol`)

- `AndroidWakeOnLanSender.wake(macAddress, targetAddress, port)` chạy trên `Dispatchers.IO`. Nó gửi magic packet unicast tới IP cấu hình và broadcast tới `192.168.2.255` (hardcode). Chỉ hỗ trợ IPv4.
- `WolController.saveConfig(mac, ip)`: trường để trống thì dùng `WolSettings.DEFAULT_MAC_ADDRESS`/`DEFAULT_TARGET_IP`. Hàm validate bằng `MacAddress`/`Ipv4Address`, rồi lưu prefs `wol_settings`.
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

Nguồn: `feature/fan/src/main/java/com/khaiit/kremote/feature/fan/*.kt`.

### 5.5 Daikin AC (`:feature:ac/daikin`)

- `DaikinAcState`: nhiệt độ 18..30, `DaikinMode` (AUTO, COOL, DRY, FAN, HEAT), `DaikinFanSpeed` (LOW, HIGH), swing.
- `DaikinAcController(irTransmitter, stateStore)`: `togglePower`, `increaseTemperature`, `decreaseTemperature`, `cycleFanSpeed`, `toggleSwing`, `setMode`, `cycleMode`, `resendCurrentState`. Mọi thao tác đi qua `updateAndTransmit` và lưu qua `DaikinAcStateStore`.
- `DaikinBrcEncoder(profile)`:
  - `encode(state, modeButtonPressed)` trả `EncodedCommand(frequencyHz, pattern)`.
  - Frame 22 byte gồm preamble 7 byte và state frame 15 byte; mỗi phần có checksum.
  - Profile ở `DaikinBrcProfile` (38 kHz).
- Prefs `daikin_ac_settings` (`DaikinAcSettings`).

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
- Dialog cấu hình dùng `SecureFlagPolicy.SecureOn` và không điền sẵn credentials.

Nguồn: `feature/tuya/src/main/java/com/khaiit/kremote/feature/tuya/*.kt`, `README.md` §2, §4.

### 5.9 Controller & card Hub trong `:app`

- `HubSettingsStore` (implement `HubConfigStore`):
  - Lưu `HubSettings(config, fanTransport, fixTransport)` dạng JSON `schemaVersion = 1`, mã hóa AES-GCM bằng khóa Keystore `khairc_hub_aes_v1`, prefs `hub_secure_v1`.
  - `load()` không tạo khóa mới và không xóa cấu hình khi đọc lỗi, mà ném `IllegalStateException` kèm thông điệp tiếng Việt.
- `HttpsProbePreferences` (trong `HubHttpsProbeCard.kt`): prefs `hub_https_probe_v1` gồm `host`, `port` (mặc định 443), `sha256`; `read()` trả `HubHttpsEndpoint?`.
- `MilifeController(api, store, networkAllowed)`:
  - `init` load settings rồi gọi `refresh()` nếu đã cấu hình và có quyền.
  - `refresh()` gửi một GET và ánh xạ `HubClientException.code` ra thông điệp. `power = null` nghĩa là chưa xác minh.
  - `saveConfiguration(address, replacementKey)`: key trống thì giữ key cũ.
  - Dialog "Kết nối ESP32 Hub" nằm trong `MilifeDeviceCard` (`SecureFlagPolicy.SecureOn`).
- `ToshibaHttpsControlsController(api, hubStore, endpointProvider, stateStore, networkAllowed)`:
  - State `ToshibaHttpsUiState(model, busy, needsManualCheck, message)`.
  - `withModel` chặn thao tác khi `busy` hoặc `needsManualCheck`. Gate `AtomicBoolean` trong `submit`.
  - Chỉ `Sent` mới gọi `stateStore.save`. `Uncertain`, exception hay cancel đều bật `needsManualCheck`; người dùng gọi `confirmObserved()` để mở khóa.
  - Thao tác (trừ `togglePower`/`chooseCool`) chỉ chạy khi `power = true`.
- `HubHttpsProbeCard()`: nhập host/port/fingerprint và nút KIỂM TRA (gọi `HubHttpsProbe.check`).

Nguồn: `app/src/main/java/com/khaiit/kremote/{HubSettingsStore,HubHttpsProbeCard,MilifeController,MilifeDeviceCard,ToshibaHttpsControlsController,ToshibaHttpsControlsCard}.kt`.

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
| `/api/v1/commands` (`device_id=toshiba_ac`) | POST | `X-API-Key` | `HubHttpsToshibaControlsClient.send` |

- TLS: chứng chỉ tự ký, pin SHA-256 DER do người dùng nhập; hostname phải có trong SAN. Cách lấy fingerprint xem `docs/HUB_HTTPS_ANDROID_STAGE1.md`.
- Hợp đồng phía firmware nằm ở repo `KhaiRC-ESP32-Hub` (`docs/PROJECT_SPEC.md` §6.5 của repo đó). Đã đối chiếu lúc tạo spec (firmware `d66cbf6`):
  - `state` có đúng 5 field: `power` bool, `temperature_c` 17–30, `fan_speed` `auto`/`1`–`5`. Các action khác có đúng 2 field.
  - `500 ir_send_failed` và lỗi đọc body (firmware đóng socket, không phản hồi) không nằm trong danh sách `Rejected` của app, nên app xếp vào `Uncertain`.

Nguồn: `core/network/.../HubHttps*.kt`, `docs/HUB_HTTPS_ANDROID_STAGE1.md`.

### 6.3 ESP32 Hub HTTP LAN (cổng 80)

- `HubClient` gọi `http://<ipv4>:80/api/v1/{health,capabilities,devices,commands,devices/milife_plug_10a/state}`.
- `network_security_config.xml` cho phép cleartext toàn cục để phục vụ client này. Hiện không có UI dùng (§10).

Nguồn: `core/network/.../HubClient.kt`, `app/src/main/res/xml/network_security_config.xml`.

### 6.4 Wake-on-LAN & IR

- WOL: UDP cổng 9, unicast + broadcast `192.168.2.255`.
- IR: `ConsumerIrManager.transmit(frequency, pattern)`, pattern tính bằng micro giây (mark/space xen kẽ).

Nguồn: `core/network/.../AndroidWakeOnLanSender.kt`, `core/ir/.../AndroidIrTransmitter.kt`.

## 7. Dữ liệu & cấu hình

| SharedPreferences | Chủ sở hữu | Nội dung | Mã hóa | Loại khỏi backup |
|---|---|---|---|---|
| `hub_secure_v1` | `HubSettingsStore` | IPv4 Hub, API key, transport | AES-GCM, alias `khairc_hub_aes_v1` | Có |
| `hub_https_probe_v1` | `HttpsProbePreferences` | host, port, fingerprint (công khai) | Không | Không |
| `tuya_oc09_secure` | `TuyaCredentialStore` | Client ID/Secret, Device ID | AES-GCM, alias `khairc_tuya_oc09_aes_v1` | Có |
| `tuya_oc09_labels` | `TuyaOc09LabelStore` | tên ổ cắm | Không | Không |
| `daikin_ac_settings` | `DaikinAcSettings` | state Daikin | Không | Không |
| `toshiba_ac_settings` | `ToshibaAcSettings` | state Toshiba (dùng chung IR/HTTPS) | Không | Không |
| `wol_settings` | `WolSettings` | MAC, IP đích | Không | Không |

- Loại trừ backup ở `app/src/main/res/xml/backup_rules.xml` (cloud backup) và `data_extraction_rules.xml` (cloud + device transfer).
- Quyền:
  - `TRANSMIT_IR` (`core/ir`).
  - `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_LOCAL_NETWORK` (`core/network`).
  - `INTERNET` (`feature/tuya`).

Nguồn: các file store ở trên, `app/src/main/AndroidManifest.xml`, `*/src/main/AndroidManifest.xml`, `app/src/main/res/xml/*.xml`.

## 8. Quy ước & pattern của codebase

- Controller có Compose state `var state by mutableStateOf(...)` + `private set`.
  - Controller async có scope riêng `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)`, implement `AutoCloseable` và được `close()` trong `MainActivity.onDestroy`.
  - Tuya dùng `suspend fun` + `Mutex`; card gọi qua `rememberCoroutineScope`.
- I/O mạng và Keystore chạy trong `withContext(Dispatchers.IO)`. Bắt `CancellationException` thì ném lại.
- API/store là interface hoặc `fun interface` (`MilifeReadApi`, `ToshibaHttpsControlsApi`, `HubConfigStore`, `TuyaConfigStore`, `TuyaOc09Repository`, `IrTransmitter`, `WakeOnLanSender`, `ToshibaAcStateStore`) để test bằng fake.
- Kết quả gửi lệnh tách 3 loại: Sent (đã phát hoặc đã xác minh), Rejected (server từ chối rõ ràng, không có gì xảy ra), Uncertain (có thể đã chạy). Chỉ GET được retry; POST/IR không bao giờ tự gửi lại.
- Trạng thái chưa xác minh biểu diễn bằng `null` (`MilifeUiState.power`, `TuyaOc09State.masterPower`) chứ không giữ giá trị cũ.
- Secret: mã hóa AES-GCM/Keystore; `toString()` redacted; dialog nhập dùng `SecureFlagPolicy.SecureOn`; trường trống thì giữ giá trị cũ; không hiển thị lại secret.
- Thông điệp lỗi tiếng Việt, không chứa nội dung exception gốc (tránh lộ dữ liệu giải mã), xem `HubSettingsStore.load`.
- Body request dựng từ từ vựng cố định (enum/sealed) nên không có input người dùng trong JSON.
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
- **Quyền mới:** khai báo ở Manifest của module dùng nó. Quyền runtime thì kiểm qua lambda giống `networkAllowed`.
- Quy trình phân vai: `/architect` → `$implement` → thử trên điện thoại → `$reviewer` → commit → `$docs` (`docs/AGENT_WORKFLOW.md`). Tiến độ nằm trong `docs/PLAN.md`, được `.gitignore` bỏ qua; mỗi vai dùng chat riêng.
- `python tools/spec.py` in mục lục; truyền số mục hoặc từ khóa tiêu đề để đọc chọn lọc. Công cụ chỉ đọc spec, cần Python 3.7+.

Nguồn: `AGENTS.md`, các module ở §5, `docs/AGENT_WORKFLOW.md`, `.gitignore`, `tools/spec.py`.

## 10. Vấn đề đã biết, TODO, cần xác nhận

- `HubClient` (HTTP LAN cổng 80) và enum `HubCommand` không còn UI nào gọi. Chỉ `HubClientTest`, `MilifeStatusContractTest` và `HubIntegrationTest` dùng. `network_security_config.xml` vẫn cho phép cleartext toàn cục. Cần quyết định giữ hay gỡ.
- `HubSettings.fanTransport`/`fixTransport` (`HubTransport.PHONE|HUB`) vẫn được lưu và đọc nhưng không còn logic nào dùng.
- `ToshibaAcController`/`ToshibaAcDeviceCard` (IR điện thoại) không hiển thị. `HomeScreen` vẫn nhận tham số `toshibaAcController` nhưng không dùng.
- `HomeTab` có subtitle đếm thiết bị hardcode ("6 thiết bị", "3 thiết bị"), dễ lệch khi thêm/bớt card.
- WOL hardcode broadcast `192.168.2.255` và mặc định MAC/IP trong `WolSettings`, nên chỉ đúng với LAN hiện tại.
- `core/network` khai báo `kotlinx-coroutines-core:1.10.2` trực tiếp, lệch với catalog `kotlinx-coroutines = 1.11.0`.
- Không có `android.util.Log`: khi chẩn đoán, bằng chứng chủ yếu là thông điệp UI và crash logcat.
- Chưa có cấu hình ký release; `optimization` đang tắt.
Nguồn: `git grep` trong `app core feature`, các file liên quan ở §5.

## 11. Nhật ký đồng bộ

- 2026-11-13 · INCREMENTAL · `b636c48..ad2827f` · Xác nhận JUnit cho core/ir, feature/fan, feature/wol; bổ sung quy trình PLAN và công cụ đọc spec.
- 2026-11-13 · FULL · `b636c48` · Tạo spec lần đầu cùng bộ skill agent (`AGENTS.md`, `.claude/skills`, `.agents/skills`).

## 12. Bản đồ file → mục

| File / thư mục | Mục |
|---|---|
| `settings.gradle.kts`, `build.gradle.kts`, `*/build.gradle.kts` | §2, §3 |
| `gradle/libs.versions.toml`, `gradle.properties` | §2 |
| `app/src/main/java/com/khaiit/kremote/MainActivity.kt` | §4 |
| `app/src/main/java/com/khaiit/kremote/HomeScreen.kt`, `ui/theme/*` | §4 |
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
| `README.md` | §0, §1 |
| `AGENTS.md`, `docs/AGENT_WORKFLOW.md` | §8, §9 |
| `.gitignore`, `tools/spec.py` | §9 |
