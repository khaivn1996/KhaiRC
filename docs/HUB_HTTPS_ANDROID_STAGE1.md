# KhaiRC Android – HTTPS Hub

## Nghiệm thu Stage A

**Đã đạt** trên Hub chạy firmware `1.0.0`, build ID `a1b86edfc0a43a94` (slot `app1`, OTA `valid`).

| Hạng mục | Mạng | Kết quả |
|---|---|---|
| HTTPS Hub | LAN, 4G | Nút **KIỂM TRA** thành công, TLS hợp lệ qua cổng `443`. |
| MiLife 10A | LAN, 4G | Bật/tắt thành công và đọc lại đúng trạng thái. |
| Toshiba | LAN | Điều khiển thành công qua HTTPS. |

## Cấu hình và kiểm tra HTTPS

- Thẻ “HTTPS Hub · Stage 2A” nằm trong tab Tất cả / Mạng. Cấu hình hostname hoặc IPv4, cổng HTTPS và SHA-256 fingerprint của chứng chỉ tự ký. MiLife và Toshiba dùng chung cấu hình HTTPS này.
- Nút **KIỂM TRA** gửi một `GET /api/v1/health` qua HTTPS và không gửi API key. App kiểm tra fingerprint, hạn chứng chỉ, hostname và các trường JSON `status=ok`, `device=KhaiRC-ESP32-Hub`, `phase=https_probe`.
- App giữ xác minh TLS mặc định cho hostname, không theo redirect và không hạ cấp sang HTTP. Xem [đặc tả API HTTPS](PROJECT_SPEC.md#62-esp32-hub-https) để biết các đường dẫn điều khiển.

## Fingerprint từ chứng chỉ có sẵn trên máy Windows

Chạy từ bất cứ thư mục nào (Python có sẵn):

```powershell
$Cert = 'D:\IoT\KhaiRC-ESP32-Hub\include\tls_certificate.pem'
python -c "import ssl,hashlib,sys; print(hashlib.sha256(ssl.PEM_cert_to_DER_cert(open(sys.argv[1], encoding='ascii').read())).hexdigest().upper())" $Cert
```

Kết quả 64 ký tự hex, chỉ là fingerprint **công khai**; không sao chép `tls_private_key.pem` hay `tls_credentials.h` vào dự án Android.

Dùng địa chỉ IPv4 LAN hoặc hostname truy cập từ xa có trong SAN của chứng chỉ. Cấu hình đã nghiệm thu dùng cổng `443`. Khi thay chứng chỉ, cập nhật fingerprint từ bản PEM tin cậy.

## Kiểm thử trên Windows / Android Studio

```powershell
.\gradlew.bat :core:network:testDebugUnitTest :app:assembleDebug
```

Nếu plugin AGP đặt tên task khác, liệt kê `gradlew.bat :core:network:tasks --all`; không sửa Gradle mò. Kỳ vọng build và test pass, sau đó mở KhaiRC Android, tab Mạng, cấu hình HTTPS và dùng nút KIỂM TRA khi Hub kết nối được. Nếu Hub hiện offline, kết quả timeout là bình thường, không chứng minh client sai.

## Hạn chế

- Trust manager chỉ chấp nhận chứng chỉ tự ký đã được pin. HTTPS dùng CA công khai cần thiết kế xác thực khác.
- Nhập fingerprint từ bản PEM trên máy tính tin cậy.
