package com.khaiit.kremote.feature.tuya

import android.os.SystemClock
import org.json.JSONObject

/** Short-lived in-memory token only. No refresh token or access token is persisted. */
internal class TuyaAuthManager {
    private var cached: CachedToken? = null
    private data class CachedToken(val value: String, val expiresAtElapsedMs: Long)

    fun invalidate() { cached = null }

    suspend fun token(credentials: TuyaCredentials, request: suspend (String) -> JSONObject): String {
        cached?.takeIf { SystemClock.elapsedRealtime() < it.expiresAtElapsedMs }?.let { return it.value }
        val result = request("/v1.0/token?grant_type=1").getJSONObject("result")
        val token = result.getString("access_token").takeIf(String::isNotBlank)
            ?: error("Tuya trả về access token rỗng")
        val expiresSeconds = result.getLong("expire_time")
        require(expiresSeconds > 0L) { "Thời hạn token Tuya không hợp lệ" }
        // Allow a margin for clock skew / requests near expiry.
        val usableMs = (expiresSeconds - 60L).coerceAtLeast(1L) * 1000L
        cached = CachedToken(token, SystemClock.elapsedRealtime() + usableMs)
        return token
    }
}
