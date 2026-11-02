package com.khaiit.kremote.feature.xiaomi

data class XiaomiRemoteConfig(
    val httpsUrl: String,
    val entityId: String
) {
    fun validated(): XiaomiRemoteConfig {
        val uri = java.net.URI(httpsUrl.trim())
        require(uri.scheme.equals("https", ignoreCase = true))
        require(!uri.host.isNullOrBlank() && uri.userInfo == null)
        require(uri.rawQuery == null && uri.rawFragment == null)
        require(uri.rawPath.isNullOrBlank() || uri.rawPath == "/")
        require(entityId.matches(Regex("switch\\.[a-z0-9_]+")))
        return copy(httpsUrl = httpsUrl.trim().trimEnd('/'))
    }
}
