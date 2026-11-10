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

/** Narrow, fixed Toshiba command vocabulary. No generic IR frames or MiLife writes. */
enum class ToshibaHttpsFan(val wire: String) {
    AUTO("auto"), LEVEL_1("1"), LEVEL_2("2"), LEVEL_3("3"), LEVEL_4("4"), LEVEL_5("5")
}

sealed interface ToshibaHttpsAction {
    val action: String
    fun body(): ByteArray

    data class State(val power: Boolean, val temperatureC: Int, val fan: ToshibaHttpsFan) : ToshibaHttpsAction {
        init { require(temperatureC in 17..30) }
        override val action: String get() = "state"
        override fun body(): ByteArray = (
            "{\"device_id\":\"toshiba_ac\",\"action\":\"state\",\"power\":$power," +
            "\"temperature_c\":$temperatureC,\"fan_speed\":\"${fan.wire}\"}"
        ).toByteArray(Charsets.UTF_8)
    }
    data object Fix : ToshibaHttpsAction {
        override val action = "fix"
        override fun body() = shortBody(action)
    }
    data object SwingOn : ToshibaHttpsAction {
        override val action = "swing_on"
        override fun body() = shortBody(action)
    }
    data object SwingOff : ToshibaHttpsAction {
        override val action = "swing_off"
        override fun body() = shortBody(action)
    }
}

private fun shortBody(action: String): ByteArray =
    "{\"device_id\":\"toshiba_ac\",\"action\":\"$action\"}"
        .toByteArray(Charsets.UTF_8)

sealed interface ToshibaHttpsControlResult {
    data object Sent : ToshibaHttpsControlResult
    data class Rejected(val status: Int, val code: String) : ToshibaHttpsControlResult
    data class Uncertain(val reason: String) : ToshibaHttpsControlResult
}

fun interface ToshibaHttpsControlsApi {
    suspend fun send(
        config: HubConfig, endpoint: HubHttpsEndpoint, command: ToshibaHttpsAction
    ): ToshibaHttpsControlResult
}

/** One POST per deliberate button press; no automatic resend, redirects or HTTP fallback. */
class HubHttpsToshibaControlsClient : ToshibaHttpsControlsApi {
    override suspend fun send(
        config: HubConfig, endpoint: HubHttpsEndpoint, command: ToshibaHttpsAction
    ): ToshibaHttpsControlResult = withContext(Dispatchers.IO) {
        var connection: HttpsURLConnection? = null
        try {
            val context = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(PinnedSelfSignedTrustManager(endpoint.fingerprintHex)), SecureRandom())
            }
            connection = URL(endpoint.baseUrl + "/api/v1/commands")
                .openConnection(Proxy.NO_PROXY) as HttpsURLConnection
            connection.sslSocketFactory = context.socketFactory
            connection.hostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
            connection.connectTimeout = 3_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.requestMethod = "POST"
            connection.setRequestProperty("X-API-Key", config.apiKey)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("Connection", "close")
            val body = command.body()
            require(body.size in 1..512)
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            if (status in 300..399) return@withContext ToshibaHttpsControlResult.Uncertain("redirect_blocked")
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(256)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (out.size() + n > 2_048) {
                        return@withContext ToshibaHttpsControlResult.Uncertain("response_too_large")
                    }
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            } ?: ByteArray(0)
            val responseText = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
            if ('\u0000' in responseText) {
                return@withContext ToshibaHttpsControlResult.Uncertain("invalid_response")
            }
            val response = JSONObject(responseText)
            if (status == 200) {
                if (response.optInt("schema_version", -1) == 1 &&
                    response.optString("device_id") == "toshiba_ac" &&
                    response.optString("action") == command.action &&
                    response.optString("transport") == "ir" &&
                    response.optString("status") == "sent" &&
                    response.opt("state_verified") == false) {
                    ToshibaHttpsControlResult.Sent
                } else ToshibaHttpsControlResult.Uncertain("invalid_response")
            } else {
                val code = response.optJSONObject("error")?.optString("code").orEmpty()
                if ((status == 401 && code == "unauthorized") ||
                    (status == 422 && code == "unsupported_action") ||
                    (status == 503 && code == "device_not_ready") ||
                    (status == 413 && code == "payload_too_large") ||
                    (status == 415 && code == "unsupported_media_type") ||
                    (status == 400 && code in setOf("invalid_json", "invalid_command"))) {
                    ToshibaHttpsControlResult.Rejected(status, code)
                } else ToshibaHttpsControlResult.Uncertain("http_$status")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SSLException) {
            ToshibaHttpsControlResult.Uncertain("tls_validation_failed")
        } catch (_: SocketTimeoutException) {
            ToshibaHttpsControlResult.Uncertain("timeout")
        } catch (_: SecurityException) {
            ToshibaHttpsControlResult.Uncertain("network_permission")
        } catch (_: Exception) {
            ToshibaHttpsControlResult.Uncertain("network_error")
        } finally {
            connection?.disconnect()
        }
    }
}
