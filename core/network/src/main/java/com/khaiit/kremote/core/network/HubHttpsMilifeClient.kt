package com.khaiit.kremote.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONException
import java.io.ByteArrayOutputStream
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.CharacterCodingException
import java.security.SecureRandom
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException

interface MilifePowerApi : MilifeReadApi {
    suspend fun setPower(config: HubConfig, power: Boolean): MilifePowerResult
}

sealed interface MilifePowerResult {
    data class Rejected(val code: String) : MilifePowerResult
    data class ReadBack(val power: Boolean?) : MilifePowerResult
}

/** Uses the same pinned HTTPS endpoint and API key for commands and status reads. */
class HubHttpsMilifeClient(
    private val endpointProvider: () -> HubHttpsEndpoint?
) : MilifePowerApi {
    override suspend fun readMilife(config: HubConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        val endpoint = endpointProvider()
            ?: return@withContext Result.failure(HubClientException("https_not_configured"))
        readMilife(config, endpoint)
    }

    override suspend fun setPower(config: HubConfig, power: Boolean): MilifePowerResult =
        withContext(Dispatchers.IO) {
            val endpoint = endpointProvider()
                ?: return@withContext MilifePowerResult.Rejected("https_not_configured")
            val rejection = sendPower(config, endpoint, power)
            if (rejection != null) {
                MilifePowerResult.Rejected(rejection)
            } else {
                // A POST acknowledgment and an uncertain delivery both require an independent GET.
                currentCoroutineContext().ensureActive()
                MilifePowerResult.ReadBack(readMilife(config, endpoint).getOrNull())
            }
        }

    private fun readMilife(config: HubConfig, endpoint: HubHttpsEndpoint): Result<Boolean> {
        var connection: HttpsURLConnection? = null
        return try {
            connection = open(config, endpoint, "/api/v1/devices/milife_plug_10a/state")
            connection.requestMethod = "GET"

            val status = connection.responseCode
            if (status in 300..399) throw HubClientException("redirect_blocked")
            if (status != 200) throw HubClientException("http_$status")
            val body = connection.inputStream.use(::readJson)
            if (body.opt("schema_version") != 1 ||
                body.optString("device_id") != "milife_plug_10a" ||
                body.optString("transport") != "miio_lan" ||
                body.opt("state_verified") != true || body.opt("power") !is Boolean) {
                throw HubClientException("invalid_response")
            }
            Result.success(body.getBoolean("power"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: HubClientException) {
            Result.failure(error)
        } catch (_: JSONException) {
            Result.failure(HubClientException("invalid_response"))
        } catch (_: CharacterCodingException) {
            Result.failure(HubClientException("invalid_response"))
        } catch (_: SSLException) {
            Result.failure(HubClientException("tls_validation_failed"))
        } catch (_: SocketTimeoutException) {
            Result.failure(HubClientException("timeout"))
        } catch (_: SecurityException) {
            Result.failure(HubClientException("network_permission"))
        } catch (_: Exception) {
            Result.failure(HubClientException("network_error"))
        } finally {
            connection?.disconnect()
        }
    }

    // Only a matching documented rejection skips read-back. Every other response or
    // transport failure may have delivered the write, so verify with a GET.
    private fun sendPower(config: HubConfig, endpoint: HubHttpsEndpoint, power: Boolean): String? {
        var connection: HttpsURLConnection? = null
        return try {
            connection = open(config, endpoint, "/api/v1/commands")
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            val payload = milifePowerBody(power)
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }
            val status = connection.responseCode
            if (status in 400..599) {
                val code = connection.errorStream?.use(::readJson)
                    ?.optJSONObject("error")?.opt("code") as? String
                code?.takeIf { isMilifePowerRejection(status, it) }
            } else {
                null
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun open(config: HubConfig, endpoint: HubHttpsEndpoint, path: String): HttpsURLConnection {
        val tls = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(PinnedSelfSignedTrustManager(endpoint.fingerprintHex)), SecureRandom())
        }
        return (URL(endpoint.baseUrl + path).openConnection(Proxy.NO_PROXY) as HttpsURLConnection).apply {
            sslSocketFactory = tls.socketFactory
            hostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
            connectTimeout = 3_000
            // Allow time for miIO hello (1.2 s) and the device response (3.2 s).
            readTimeout = 8_000
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Connection", "close")
            setRequestProperty("X-API-Key", config.apiKey)
        }
    }

    private fun readJson(input: java.io.InputStream): JSONObject {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(512)
        while (true) {
            val size = input.read(chunk)
            if (size == -1) break
            if (buffer.size() + size > 2_048) throw HubClientException("response_too_large")
            buffer.write(chunk, 0, size)
        }
        val decoded = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(buffer.toByteArray())).toString()
        if ('\u0000' in decoded) throw HubClientException("invalid_response")
        return JSONObject(decoded)
    }
}

internal fun milifePowerBody(power: Boolean): ByteArray {
    val action = if (power) "power_on" else "power_off"
    return """{"device_id":"milife_plug_10a","action":"$action"}""".toByteArray(Charsets.UTF_8)
}

internal fun isMilifePowerRejection(status: Int, code: String): Boolean = when (status) {
    400 -> code == "invalid_json" || code == "invalid_command"
    401 -> code == "unauthorized"
    413 -> code == "payload_too_large"
    415 -> code == "unsupported_media_type"
    422 -> code == "unsupported_action"
    502 -> code in setOf("wifi_unavailable", "miot_network_error", "miot_device_mismatch",
        "miot_protocol_error", "miot_device_rejected")
    503 -> code == "miot_not_configured" || code == "device_not_ready"
    504 -> code == "miot_hello_timeout"
    else -> false
}
