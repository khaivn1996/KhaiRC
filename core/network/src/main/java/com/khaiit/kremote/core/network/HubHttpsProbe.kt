package com.khaiit.kremote.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.X509TrustManager

/** An explicit, read-only HTTPS target; independent of the existing LAN HubEndpoint. */
data class HubHttpsEndpoint(
    val host: String,
    val port: Int,
    val sha256Fingerprint: String
) {
    init {
        require(port in 1..65535) { "Cổng HTTPS không hợp lệ" }
        require(host.length in 4..253 && host == host.trim() &&
            host == host.lowercase(Locale.ROOT)) { "Tên máy chủ phải ở dạng chữ thường" }
        val privateIp = runCatching { HubEndpoint(host) }.isSuccess
        val labels = host.split('.')
        val validDomain = labels.size >= 2 && labels.all { label ->
            label.length in 1..63 &&
                (label.first() in 'a'..'z' || label.first() in '0'..'9') &&
                (label.last() in 'a'..'z' || label.last() in '0'..'9') &&
                label.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }
        } && labels.last().length >= 2 && labels.last().all { it in 'a'..'z' }
        require(privateIp || validDomain) { "Chỉ dùng IPv4 LAN hoặc hostname DNS hợp lệ" }
        val normalized = sha256Fingerprint.replace(":", "").uppercase(Locale.ROOT)
        require(Regex("[0-9A-F]{64}").matches(normalized)) {
            "SHA-256 fingerprint cần đủ 64 ký tự hex"
        }
    }

    val baseUrl: String get() = "https://$host:$port"
    val fingerprintHex: String get() = sha256Fingerprint.replace(":", "").uppercase(Locale.ROOT)

    override fun toString(): String = "HubHttpsEndpoint($host:$port, pin=<configured>)"
}

sealed interface HubHttpsProbeResult {
    data class Verified(val uptimeSeconds: Long, val freeHeap: Long) : HubHttpsProbeResult
    data class Failed(val code: String) : HubHttpsProbeResult
}

/**
 * Only probes GET /api/v1/health. It sends NO API key and performs NO writes.
 * TLS trusts only the *exact* user-pinned self-signed leaf certificate.
 * HttpsURLConnection retains its default hostname verifier (DNS/IP SAN required).
 */
class HubHttpsProbe {
    suspend fun check(endpoint: HubHttpsEndpoint): HubHttpsProbeResult = withContext(Dispatchers.IO) {
        var connection: HttpsURLConnection? = null
        try {
            val context = SSLContext.getInstance("TLS")
            context.init(null, arrayOf(PinnedSelfSignedTrustManager(endpoint.fingerprintHex)), SecureRandom())
            val url = URL(endpoint.baseUrl + "/api/v1/health")
            connection = url.openConnection(Proxy.NO_PROXY) as HttpsURLConnection
            connection.sslSocketFactory = context.socketFactory
            // Never override hostname verification with a permissive verifier.
            connection.hostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
            connection.connectTimeout = 3_000
            connection.readTimeout = 3_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("Connection", "close")

            val status = connection.responseCode
            if (status in 300..399) return@withContext HubHttpsProbeResult.Failed("redirect_blocked")
            if (status != 200) return@withContext HubHttpsProbeResult.Failed("http_$status")
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(512)
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    if (output.size() + count > 2_048) {
                        return@withContext HubHttpsProbeResult.Failed("response_too_large")
                    }
                    output.write(buffer, 0, count)
                }
            }
            val body = JSONObject(output.toString(Charsets.UTF_8.name()))
            if (body.optString("status") != "ok" ||
                body.optString("device") != "KhaiRC-ESP32-Hub" ||
                body.optString("phase") != "https_probe" ||
                !body.has("uptime_sec") || !body.has("free_heap")) {
                return@withContext HubHttpsProbeResult.Failed("invalid_response")
            }
            val uptime = body.optLong("uptime_sec", -1)
            val heap = body.optLong("free_heap", -1)
            if (uptime < 0 || heap < 0) {
                HubHttpsProbeResult.Failed("invalid_response")
            } else {
                HubHttpsProbeResult.Verified(uptime, heap)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SSLException) {
            HubHttpsProbeResult.Failed("tls_validation_failed")
        } catch (_: UnknownHostException) {
            HubHttpsProbeResult.Failed("dns_error")
        } catch (_: SocketTimeoutException) {
            HubHttpsProbeResult.Failed("timeout")
        } catch (_: JSONException) {
            HubHttpsProbeResult.Failed("invalid_response")
        } catch (_: SecurityException) {
            HubHttpsProbeResult.Failed("network_permission")
        } catch (_: IOException) {
            HubHttpsProbeResult.Failed("network_error")
        } catch (_: Exception) {
            HubHttpsProbeResult.Failed("tls_error")
        } finally {
            connection?.disconnect()
        }
    }
}

/** NOT a trust-all manager. Cert is valid only when its SHA-256 DER hash matches. */
internal class PinnedSelfSignedTrustManager(fingerprintHex: String) : X509TrustManager {
    private val pinned = hexToBytes(fingerprintHex)

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("No server certificate")
        if (authType.isNullOrBlank()) throw CertificateException("Missing authType")
        leaf.checkValidity()
        val observed = MessageDigest.getInstance("SHA-256").digest(leaf.encoded)
        if (!MessageDigest.isEqual(pinned, observed)) {
            throw CertificateException("Pinned certificate mismatch")
        }
        // Stage 1 uses a self-signed certificate generated locally for the ESP32.
        leaf.verify(leaf.publicKey)
        // Hostname/SAN checking is separately enforced by HttpsURLConnection.
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("Client certificates are not supported")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    private fun hexToBytes(value: String): ByteArray {
        require(Regex("[0-9A-F]{64}").matches(value))
        return ByteArray(32) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
