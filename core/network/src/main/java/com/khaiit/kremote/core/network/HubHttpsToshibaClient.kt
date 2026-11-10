package com.khaiit.kremote.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.SecureRandom
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException

/** A single, explicit Toshiba FIX action. No generic command or write-MiLife API. */
interface ToshibaHttpsFixApi {
    suspend fun sendFix(config: HubConfig, endpoint: HubHttpsEndpoint): ToshibaFixResult
}

sealed interface ToshibaFixResult {
    data object Sent : ToshibaFixResult
    data class Rejected(val status: Int, val code: String) : ToshibaFixResult
    data class Uncertain(val code: String) : ToshibaFixResult
}

/**
 * One user action => at most one POST, with no redirect, retry or HTTP fallback.
 * Any ambiguous transport failure is UNCONFIRMED (must not trigger another send).
 */
internal object ToshibaFixRequest {
    const val PATH = "/api/v1/commands"
    fun body(): ByteArray = "{\"device_id\":\"toshiba_ac\",\"action\":\"fix\"}"
        .toByteArray(Charsets.UTF_8)
}

class HubHttpsToshibaClient : ToshibaHttpsFixApi {
    override suspend fun sendFix(config: HubConfig, endpoint: HubHttpsEndpoint): ToshibaFixResult =
        withContext(Dispatchers.IO) {
            var connection: HttpsURLConnection? = null
            try {
                // The same per-Hub self-signed cert pin used in Stage 1/2A.
                val tls = SSLContext.getInstance("TLS").apply {
                    init(null, arrayOf(PinnedSelfSignedTrustManager(endpoint.fingerprintHex)), SecureRandom())
                }
                connection = URL(endpoint.baseUrl + ToshibaFixRequest.PATH)
                    .openConnection(Proxy.NO_PROXY) as HttpsURLConnection
                connection.sslSocketFactory = tls.socketFactory
                connection.hostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
                connection.connectTimeout = 3_000
                connection.readTimeout = 5_000
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.requestMethod = "POST"
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Accept-Encoding", "identity")
                connection.setRequestProperty("Connection", "close")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("X-API-Key", config.apiKey)
                // Constant, never derived from UI input. Streaming avoids buffered POST replay.
                val body = ToshibaFixRequest.body()
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }

                val status = connection.responseCode
                if (status in 300..399) return@withContext ToshibaFixResult.Uncertain("redirect_blocked")
                val bytes = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(256)
                        while (true) {
                            val n = input.read(buffer)
                            if (n == -1) break
                            if (output.size() + n > 2_048) {
                                return@withContext ToshibaFixResult.Uncertain("response_too_large")
                            }
                            output.write(buffer, 0, n)
                        }
                        output.toByteArray()
                    } ?: ByteArray(0)
                val decoded = Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString()
                if ('\u0000' in decoded) return@withContext ToshibaFixResult.Uncertain("invalid_response")
                val json = JSONObject(decoded)
                if (status == 200) {
                    if (json.optInt("schema_version", -1) == 1 &&
                        json.optString("device_id") == "toshiba_ac" &&
                        json.optString("action") == "fix" &&
                        json.optString("transport") == "ir" &&
                        json.optString("status") == "sent" &&
                        json.opt("state_verified") == false) {
                        ToshibaFixResult.Sent
                    } else {
                        ToshibaFixResult.Uncertain("invalid_response")
                    }
                } else {
                    val code = json.optJSONObject("error")?.optString("code").orEmpty()
                    if ((status == 401 && code == "unauthorized") ||
                        (status == 422 && code == "unsupported_action") ||
                        (status == 503 && code == "device_not_ready") ||
                        (status == 413 && code == "payload_too_large") ||
                        (status == 415 && code == "unsupported_media_type") ||
                        (status == 400 && code in setOf("invalid_json", "invalid_command"))) {
                        ToshibaFixResult.Rejected(status, code)
                    } else {
                        ToshibaFixResult.Uncertain("http_$status")
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SSLException) {
                ToshibaFixResult.Uncertain("tls_validation_failed")
            } catch (_: SocketTimeoutException) {
                ToshibaFixResult.Uncertain("timeout")
            } catch (_: SecurityException) {
                ToshibaFixResult.Uncertain("network_permission")
            } catch (_: Exception) {
                ToshibaFixResult.Uncertain("network_error")
            } finally {
                connection?.disconnect()
            }
        }

}
