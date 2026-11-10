package com.khaiit.kremote.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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

/**
 * Stage 2A: exactly one HTTPS GET for MiLife status, with the Stage 1 pinned certificate.
 * Never falls back to HTTP and never sends power commands.
 * The API key is supplied from the existing Android-Keystore-protected HubConfigStore.
 */
class HubHttpsMilifeClient(
    private val endpointProvider: () -> HubHttpsEndpoint?
) : MilifeReadApi {
    override suspend fun readMilife(config: HubConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        var connection: HttpsURLConnection? = null
        try {
            val endpoint = endpointProvider() ?: throw HubClientException("https_not_configured")
            val tls = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(PinnedSelfSignedTrustManager(endpoint.fingerprintHex)), SecureRandom())
            }
            connection = URL(endpoint.baseUrl + "/api/v1/devices/milife_plug_10a/state")
                .openConnection(Proxy.NO_PROXY) as HttpsURLConnection
            connection.sslSocketFactory = tls.socketFactory
            // Also check the DNS/IP Subject Alternative Name. Never install a trust-all verifier.
            connection.hostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
            connection.connectTimeout = 3_000
            // Hub's miIO read can use hello(1.2s) + response(3.2s) before TLS/network overhead.
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("Connection", "close")
            connection.setRequestProperty("X-API-Key", config.apiKey)

            val status = connection.responseCode
            if (status in 300..399) throw HubClientException("redirect_blocked")
            if (status != 200) throw HubClientException("http_$status")
            val buffer = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val chunk = ByteArray(512)
                while (true) {
                    val n = input.read(chunk)
                    if (n == -1) break
                    if (buffer.size() + n > 2_048) throw HubClientException("response_too_large")
                    buffer.write(chunk, 0, n)
                }
            }
            val decoded = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(buffer.toByteArray())).toString()
            if ('\u0000' in decoded) throw HubClientException("invalid_response")
            val body = JSONObject(decoded)
            if (body.optInt("schema_version", -1) != 1 ||
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
}
