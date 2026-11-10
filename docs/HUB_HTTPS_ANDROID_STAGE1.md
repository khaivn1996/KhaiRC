# KhaiRC Android – HTTPS Stage 1 (đọc trạng thái TLS, không điều khiển)

- **Độc lập với MiLife và IR hiện có.** Card “HTTPS Hub · Stage 1” nằm trong tab Tất cả / Mạng.
- Khai báo **hostname DDNS hoặc IPv4 LAN/VPN**, **cổng HTTPS** và **SHA-256 fingerprint của DER certificate tự ký**. Không nhập API key hoặc private key. Cấu hình probe được giữ trong SharedPreferences riêng và không dùng làm cấu hình MiLife/IR.
- Khi nhấn KIỂM TRA, ứng dụng thực hiện đúng **một GET** `https://host:port/api/v1/health`, yêu cầu chứng chỉ self-signed đúng fingerprint và còn hạn, sau đó xác minh hostname qua TLS mặc định và JSON `status=ok`, `device=KhaiRC-ESP32-Hub`, `phase=https_probe`.
- Không bỏ qua TLS (`HostnameVerifier`, `TrustManager` không trust-all); không chấp nhận HTTP; không theo redirect; không tự retry; không tự hạ cấp sang HTTP.
- **Chỉ đúng với HTTPS Stage 1 trên ESP32**. Hiện firmware chỉ có `GET /api/v1/health` trên 443; muốn dùng MiLife/IR từ xa, sau này phải bổ sung server-side endpoints và kiểm thử độc lập.
- Chưa mở WAN:999 hoặc :18080 hoặc port HTTPS mới, không kiểm thử HTTPS qua Internet đến khi Hub ổn định và phần bảo mật hoàn tất.

## Fingerprint từ chứng chỉ có sẵn trên máy Windows

Chạy từ bất cứ thư mục nào (Python có sẵn):

```powershell
$Cert = 'D:\IoT\KhaiRC-ESP32-Hub\include\tls_certificate.pem'
python -c "import ssl,hashlib,sys; print(hashlib.sha256(ssl.PEM_cert_to_DER_cert(open(sys.argv[1], encoding='ascii').read())).hexdigest().upper())" $Cert
```

Kết quả 64 ký tự hex, chỉ là fingerprint **công khai**; không sao chép `tls_private_key.pem` hay `tls_credentials.h` vào dự án Android.

Nếu test qua VPN/LAN, nhập `192.168.2.200`, cổng `443` (chứng chỉ hiện tại có SAN IP này). Nếu test Internet sau khi an toàn, nhập `openwrt96.ddns.net` và cổng WAN HTTPS được forward tới ESP32:443 (ví dụ 18443, **chưa mở**). Chứng chỉ hiện tại cũng có SAN DNS này. Khi thay chứng chỉ, cần cập nhật fingerprint.

## Kiểm thử trên Windows / Android Studio

```powershell
.\gradlew.bat :core:network:testDebugUnitTest :app:assembleDebug
```

Nếu plugin AGP đặt tên task khác, liệt kê `gradlew.bat :core:network:tasks --all`; không sửa Gradle mò. Kỳ vọng build và test pass, sau đó mở KhaiRC Android, tab Mạng, cấu hình HTTPS và dùng nút KIỂM TRA khi Hub kết nối được. Nếu Hub hiện offline, kết quả timeout là bình thường, không chứng minh client sai.

## Hạn chế

- Chưa kiểm thử bằng Android SDK hoặc máy thật trong môi trường tạo bản vá này.
- Trust manager chấp nhận **duy nhất** chứng chỉ tự ký đã được pin; HTTPS có CA công khai sẽ cần thiết kế xác thực khác.
- Fingerprint được nhập thủ công: phải so khớp với bản PEM ở máy tính tin cậy; không lấy fingerprint bằng cách tự động chấp nhận một máy chủ chưa xác thực.
- Hiện chưa kết nối cấu hình remote với UI Milife/IR; điều này là chủ đích để không kích hoạt chức năng chưa có trên ESP32 Stage 1.
