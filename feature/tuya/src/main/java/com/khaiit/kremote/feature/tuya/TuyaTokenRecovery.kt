package com.khaiit.kremote.feature.tuya

import java.io.IOException

/** A rejected Cloud access token, identified by Tuya's explicit error code. */
internal class TuyaTokenRejectedException(code: String, detail: String) :
    IOException("Tuya $code: $detail")

/**
 * A GET is safe to repeat once with a new token. A power POST is not retried:
 * on failure the controller marks device state unverified until a fresh read.
 */
internal object TuyaTokenRecovery {
    private val TOKEN_ERROR_CODES = setOf("1010", "1011", "1012", "1400")

    fun isTokenErrorCode(code: String): Boolean = code in TOKEN_ERROR_CODES

    suspend fun <T> call(
        retryReadOnly: Boolean,
        getToken: suspend () -> String,
        clearToken: () -> Unit,
        request: suspend (String) -> T
    ): T {
        return try {
            request(getToken())
        } catch (error: TuyaTokenRejectedException) {
            clearToken()
            if (!retryReadOnly) throw error
            try {
                request(getToken())
            } catch (secondError: TuyaTokenRejectedException) {
                clearToken()
                throw secondError
            }
        }
    }
}
