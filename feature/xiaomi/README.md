# Xiaomi MIoT LAN – Khai RC

Target: sixwgh.plug.v7icw1 / firmware 1.0.3_0004 / BL602.

- `get_properties` for SIID 2, PIID 1 read-only was verified using python-miio through VPN.
- `set_properties` is **not yet verified on the physical device**. Never test OFF remotely while this plug powers the router/VPN.
- Enter IP and 32-character hexadecimal device token in the settings dialog. Never commit tokens.
- Android Keystore AES-GCM encrypts token data; preferences excluded from backup and transfer.
- OFF requires explicit confirmation. One command only; never auto-retry writes when the response is unknown.
- Read actual state after a write; on errors set UI status UNKNOWN. No background polling, auto-off, or bridge service.
- The phone must route UDP 54321 to the plug, over Wi-Fi or VPN.

Build and test:
```powershell
.\gradlew.bat :feature:xiaomi:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```
At-home acceptance: unplug critical loads, ensure independent router power, verify OFF -> false and ON -> true. Test VPN loss and other features (IR, WOL, Tuya OC09) separately.
