package com.khaiit.kremote.core.vault

data class TuyaPart(val clientId: String, val clientSecret: String, val deviceId: String) {
    override fun toString() = "TuyaPart(<redacted>)"
}
data class HubPart(val ipv4: String, val apiKey: String) {
    override fun toString() = "HubPart(<redacted>)"
}
data class HttpsPart(val host: String, val port: Int, val fingerprintHex: String) {
    override fun toString() = "HttpsPart(<redacted>)"
}
data class WolPart(val mac: String, val ip: String) {
    override fun toString() = "WolPart(<redacted>)"
}
data class VaultBundle(
    val tuya: TuyaPart? = null,
    val hub: HubPart? = null,
    val https: HttpsPart? = null,
    val wol: WolPart? = null,
    val labels: Map<String, String>? = null
) {
    override fun toString() = "VaultBundle(<redacted>)"
}

class VaultFormatException : Exception("Dữ liệu sao lưu không hợp lệ")
class VaultAuthException : Exception("Không thể xác thực dữ liệu sao lưu")
class VaultNetworkException : Exception("Không thể kết nối dịch vụ sao lưu")
class VaultCancelledSignIn : Exception("Đã hủy đăng nhập")
class VaultSignInException : Exception("Không đăng nhập được Google")
class VaultStorageException : Exception("Không đọc hoặc ghi được dữ liệu sao lưu trên máy")
