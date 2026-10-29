# Khai RC

**Khai RC** là ứng dụng điều khiển thiết bị trong nhà dành cho Android, kết hợp **hồng ngoại (IR)**, **Wake-on-LAN (WOL)** và **Tuya Cloud** trong một giao diện thống nhất. Ứng dụng được viết bằng **Kotlin**, **Jetpack Compose** và **Material 3**, theo cấu trúc Gradle đa module.

> **Trạng thái dự án:** Các chức năng IR, WOL và điều khiển Rạng Đông OC09 đã được kiểm thử trên thiết bị thực tế. Giao diện OC09 dạng compact với tên ổ cắm tùy chỉnh đã được kiểm thử trên bản local; nếu xem `main` trước khi commit UI, giao diện có thể vẫn là phiên bản cũ.

## 1. Thiết bị và chức năng

| Thiết bị | Kết nối | Chức năng chính |
| --- | --- | --- |
| Quạt trần | IR trên điện thoại | HIGH, SWING, OFF |
| Daikin AC (`BRC4C153`) | IR trên điện thoại | Power, nhiệt độ, chế độ, tốc độ quạt, swing |
| Toshiba AC | IR trên điện thoại | Power, COOL, nhiệt độ, FAN, FIX, SWING |
| PC | Wake-on-LAN (UDP) | Gửi magic packet để đánh thức máy; không xác nhận trạng thái bật/tắt thực |
| Rạng Đông / Ralli OC09 | Tuya Cloud, Singapore | Bật/tắt 5 ổ cắm và USB, Power tổng, đọc trạng thái Cloud, đổi tên ổ cắm |

**Lưu ý về trạng thái:** Điều khiển IR là một chiều, nên trạng thái hiển thị trên ứng dụng có thể khác với thiết bị thực. Wake-on-LAN chỉ xác nhận quá trình gửi gói tin, không xác nhận PC đã bật. Riêng OC09 có thể đọc lại trạng thái qua Cloud.

## 2. Giao diện Rạng Đông OC09

Card OC09 được thiết kế tối giản theo Material 3:

- **Tiêu đề:** tên thiết bị, trạng thái kết nối và ba biểu tượng thao tác: **bút chì (đổi tên)**, **bánh răng (cấu hình Tuya)**, **Refresh (đọc lại trạng thái)**.
- **Nguồn tổng:** khu vực riêng với nút Power. Khi *ít nhất một* kênh bật, nguồn tổng hiển thị ON; khi *cả sáu* kênh tắt, hiển thị OFF.
- **Sáu kênh:** lưới **2 cột × 3 hàng**, theo thứ tự vật lý ổ cắm 1–5 và USB. Mỗi ô hiển thị tên, trạng thái và nút Power dạng biểu tượng thay cho nút chữ BẬT/TẮT.
- **Tên tùy chỉnh:** nhấn biểu tượng bút chì để đổi tên từng ổ cắm (tối đa 24 ký tự). Tên được lưu trên điện thoại và giữ nguyên khi khởi động lại ứng dụng; xóa trắng tên để trở về tên mặc định.
- **Lệnh và trạng thái:** nút điều khiển bị khóa khi đang thực hiện lệnh hoặc chưa xác minh được trạng thái. Ứng dụng đọc lại Cloud sau thao tác, không báo thành công dựa trên phản hồi POST đơn thuần.

Tên tùy chỉnh chỉ thay đổi **nhãn hiển thị**, không thay đổi mã lệnh Tuya hoặc thứ tự vật lý của các ổ cắm. Tên được lưu riêng trong SharedPreferences `tuya_oc09_labels`; nơi này **không chứa** Tuya Client ID, Client Secret, Device ID hay access token.

### Ánh xạ đã xác minh trên OC09

| Kênh vật lý | Tuya DP code |
| --- | --- |
| Ổ cắm 1 | `switch_5` |
| Ổ cắm 2 | `switch_4` |
| Ổ cắm 3 | `switch_3` |
| Ổ cắm 4 | `switch_2` |
| Ổ cắm 5 | `switch_1` |
| USB | `switch_6` |

**Power tổng:** OC09 không công bố DP tổng tên `switch` trong instruction set. Khai RC sử dụng **một request POST chứa sáu lệnh** `switch_1` đến `switch_6` để bật/tắt đồng thời các kênh; phương án này đã được thử thành công trên thiết bị thực. Việc thực thi không được giả định là nguyên tử, nên ứng dụng luôn đọc lại và xác minh trạng thái của cả sáu kênh.

**Đồng bộ trạng thái:** tự đọc khi ứng dụng mở/trở lại foreground, sau mỗi thao tác điều khiển và khi người dùng nhấn Refresh. Không chạy polling định kỳ. Khi Cloud chưa cập nhật ngay, ứng dụng có thể thử đọc lại trạng thái trong một khoảng giới hạn mà **không gửi lại lệnh POST**.

## 3. Yêu cầu và thiết lập dự án

- **Android:** `minSdk 26`, `targetSdk 37`, `compileSdk 37` (theo `app/build.gradle.kts`).
- **Công cụ:** Android Studio, Android SDK phù hợp và Gradle Wrapper có sẵn trong repository.
- **Ngôn ngữ/UI:** Kotlin, Jetpack Compose, Material 3.
- **IR:** cần điện thoại có phần cứng phát hồng ngoại (IR blaster). Các chức năng WOL/Tuya không yêu cầu IR blaster.
- **WOL:** cần cấu hình MAC Address và địa chỉ đích trong card PC; quyền truy cập mạng nội bộ có thể cần được cấp trên các phiên bản Android mới.
- **Tuya:** điện thoại và thiết bị OC09 cần có kết nối Internet để sử dụng Tuya Cloud.

Clone dự án và mở trong Android Studio:

```powershell
git clone https://github.com/khaivn1996/KhaiRC.git
cd KhaiRC
```

Build và chạy kiểm thử từ PowerShell trên Windows:

```powershell
.\gradlew.bat :feature:tuya:testDebugUnitTest
.\gradlew.bat :feature:ac:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

Sau khi build thành công, có thể cài `app/build/outputs/apk/debug/app-debug.apk` lên điện thoại. Nếu cần kiểm tra tình trạng Git trước khi commit:

```powershell
git status --short
git diff --check
```

## 4. Cấu hình Tuya Cloud cho OC09

1. Tạo hoặc sử dụng **Tuya Developer Cloud Project** tại Data Center **Singapore**, liên kết tài khoản **Smart Life** có thiết bị OC09. Cloud Project cần được cấp quyền API phù hợp.
2. Trong Khai RC, mở card **Rạng Đông OC09** và nhấn **biểu tượng bánh răng**.
3. Ở lần thiết lập đầu, nhập đủ **Client ID**, **Client Secret** và **Device ID** từ Tuya Developer Platform, rồi nhấn **LƯU**.
4. Khi chỉnh sửa cấu hình đã lưu, **để trống trường nào thì giữ nguyên giá trị trường đó**. Ứng dụng không hiển thị lại credentials đã lưu; cả ba ô được che ký tự.
5. Sau khi đồng bộ thành công, sử dụng Power của từng ổ hoặc Power tổng. Nhấn **biểu tượng Refresh** khi muốn đọc lại trạng thái thủ công.
6. Nhấn **biểu tượng bút chì** để đặt tên theo mục đích sử dụng như “Đèn bàn”, “Tivi” hoặc “Quạt”.

Khai RC gọi Tuya Cloud trực tiếp từ Android, **không cần** OpenWrt, ESP32 hay API Bridge để điều khiển OC09. Phần tích hợp Tuya hiện dùng endpoint tại Singapore.

### Token và xử lý lỗi

- Access token chỉ được cache trong RAM, không lưu xuống thiết bị.
- Khi Cloud từ chối token với mã `1010`, `1011`, `1012` hoặc `1400`, ứng dụng xóa token cũ.
- **GET trạng thái:** lấy token mới và thử lại tối đa một lần.
- **POST điều khiển:** **không tự gửi lại**, tránh nguy cơ phát lệnh bật/tắt trùng; hãy Refresh để kiểm tra kết quả thực trước khi thao tác tiếp.
- Khi Cloud hoặc mạng lỗi, ứng dụng báo trạng thái chưa xác minh, không giả định thiết bị đã bật/tắt thành công.

## 5. Bảo mật và dữ liệu người dùng

- **Không hardcode** Client ID, Client Secret, Device ID thực hay access token trong source code, Gradle, resources, README hoặc file đưa lên GitHub.
- Credentials được mã hóa bằng **AES-GCM**, với khóa được quản lý bằng **Android Keystore**, rồi lưu trong SharedPreferences riêng `tuya_oc09_secure`.
- File lưu credentials được loại trừ khỏi **cloud backup** và **device-to-device transfer** thông qua `backup_rules.xml` và `data_extraction_rules.xml`.
- Màn hình cấu hình Tuya không điền sẵn thông tin đã lưu, dùng trường nhập che ký tự và áp dụng `SecureFlagPolicy.SecureOn` để hạn chế chụp màn hình nội dung cấu hình.
- Đối tượng `TuyaCredentials` được thiết kế để không tự in giá trị thật khi gọi `toString()`.
- **Tên ổ cắm tùy chỉnh** là dữ liệu hiển thị không nhạy cảm, được lưu riêng, không dùng chung nơi lưu credentials.

> **Giới hạn bảo mật:** Do Android trực tiếp ký request để gọi Tuya Cloud, Client Secret vẫn phải được giải mã và sử dụng trong bộ nhớ lúc gửi yêu cầu. Android Keystore và việc ẩn Settings giúp giảm rủi ro, nhưng **không bảo đảm không thể trích xuất thông tin** trên điện thoại bị root, bị hooking hoặc đã bị xâm nhập. Không chia sẻ APK đã cấu hình hoặc bản sao dữ liệu ứng dụng cho người không tin cậy.

Nếu nghi ngờ credentials bị lộ, cần thu hồi/thay mới thông tin tương ứng trên Tuya Developer Platform và cập nhật lại cấu hình trong Khai RC.

## 6. Kiến trúc mã nguồn

```text
KhaiRC/
├── app/                 # MainActivity, HomeScreen, khởi tạo các controller
├── core/
│   ├── ir/              # Phát hồng ngoại bằng phần cứng Android
│   └── network/         # Gửi Wake-on-LAN UDP
├── feature/
│   ├── fan/             # Quạt trần IR
│   ├── ac/              # Điều hòa Daikin và Toshiba
│   ├── wol/             # Giao diện/cấu hình WOL
│   └── tuya/            # OC09: Cloud API, token, Keystore, trạng thái, UI, tên tùy chỉnh
├── gradle/              # Version catalog và Gradle Wrapper
└── settings.gradle.kts  # Đăng ký module
```

Luồng điều khiển OC09:

```text
TuyaOc09DeviceCard (Jetpack Compose)
            |
            v
TuyaOc09Controller (trạng thái, khóa thao tác, xác nhận lệnh)
            |
            v
TuyaCloudOc09Repository (REST API + HMAC-SHA256 + token recovery)
            |
            v
Tuya Cloud Singapore <--> Rạng Đông OC09

TuyaCredentialStore (Android Keystore) --> credentials cho repository
TuyaOc09LabelStore (SharedPreferences riêng) --> tên hiển thị trên card
```

Phần IR/WOL hoạt động độc lập với `:feature:tuya`. Mục tiêu khi thay đổi UI hoặc Cloud API là **không làm thay đổi hành vi của các module đang hoạt động ổn định**.

## 7. Kiểm thử đề xuất trước khi commit

| Nhóm | Cần xác minh |
| --- | --- |
| Build | `:feature:tuya:testDebugUnitTest`, `:feature:ac:testDebugUnitTest`, `:app:assembleDebug` thành công |
| OC09 từng kênh | Sáu nút Power hoạt động và trạng thái được cập nhật tự động |
| Power tổng | Bật/tắt đủ sáu kênh bằng một POST, trạng thái đọc lại chính xác |
| Đổi tên | Lưu tên, mở lại ứng dụng vẫn còn; để trống sẽ trở lại tên mặc định |
| Settings | Không hiển thị credentials cũ; lưu với trường trống giữ được cấu hình |
| Mất mạng | Báo lỗi, không crash; sau khi có mạng Refresh hoạt động |
| Tương thích | IR Daikin/Toshiba/quạt trần và WOL không bị ảnh hưởng |

## 8. Phạm vi hiện tại và hướng phát triển

- **Đã triển khai:** điều khiển IR trên điện thoại, WOL và Rạng Đông OC09 qua Tuya Cloud.
- **Chưa triển khai:** điều khiển IR qua ESP32-S3, hub local-first, Tuya local hoặc tích hợp các thiết bị Xiaomi Home.
- **Định hướng nghiên cứu:** ESP32-S3 làm bộ phát IR qua Wi-Fi để không phụ thuộc IR blaster của điện thoại, nhưng chưa thay đổi kiến trúc Tuya Cloud hiện hành.

---

**Repository:** [khaivn1996/KhaiRC](https://github.com/khaivn1996/KhaiRC)
**Mục đích:** dự án cá nhân, ưu tiên điều khiển ổn định, thay đổi tối thiểu và không đưa credentials thật vào mã nguồn.
