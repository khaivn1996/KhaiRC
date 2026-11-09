package com.khaiit.kremote.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

data class HubEndpoint(val ipv4: String) {
    init {
        require(Ipv4Address.isValid(ipv4) && ipv4 == ipv4.trim()) { "Địa chỉ Hub phải là IPv4 LAN" }
        val parts = ipv4.split('.')
        require(parts.all { part ->
            part.all { it in '0'..'9' } && (part.length == 1 || part[0] != '0')
        }) { "IPv4 phải dùng dạng thập phân chuẩn" }
        val octets = parts.map(String::toInt)
        require(octets[0] == 10 ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168)) { "Chỉ hỗ trợ IPv4 private trong LAN" }
        // Without a subnet mask we cannot detect every subnet-directed broadcast.
        // Reject the network/broadcast endpoints of the three supported private blocks.
        require(ipv4 !in setOf("10.0.0.0", "10.255.255.255", "172.16.0.0",
            "172.31.255.255", "192.168.0.0", "192.168.255.255")) { "Cần địa chỉ unicast của Hub" }
    }

    val baseUrl: String get() = "http://$ipv4:80"
}

class HubConfig(val endpoint: HubEndpoint, val apiKey: String) {
    init {
        require(apiKey.length == 64 && apiKey.all { it in ' '..'~' }) {
            "API key phải có 64 ký tự ASCII in được"
        }
    }

    override fun toString(): String = "HubConfig(endpoint=$endpoint, apiKey=<redacted>)"
}

enum class HubCommand(val deviceId: String, val action: String) {
    FAN_HIGH("fan_ir", "power_on"),
    FAN_SWING("fan_ir", "swing"),
    FAN_OFF("fan_ir", "power_off"),
    TOSHIBA_FIX("toshiba_ac", "fix");

    // Both values belong to this fixed vocabulary, so no user input enters JSON.
    internal fun body(): ByteArray =
        "{\"device_id\":\"$deviceId\",\"action\":\"$action\"}".toByteArray(Charsets.UTF_8)
}

data class HubDevice(
    val id: String,
    val availability: String,
    val transport: String,
    val supportedActions: Set<String>
)

data class HubSnapshot(
    val firmware: String,
    val bootId: String,
    val irSend: Boolean,
    val devices: List<HubDevice>
) {
    fun supports(command: HubCommand): Boolean = irSend && devices.any {
        it.id == command.deviceId && it.availability == "ready" &&
            it.transport == "ir" && command.action in it.supportedActions
    }
}

sealed interface HubSendResult {
    data object Sent : HubSendResult
    data class Rejected(val httpStatus: Int?, val code: String) : HubSendResult
    data class Uncertain(val httpStatus: Int?, val code: String) : HubSendResult
}

interface HubApi {
    suspend fun inspect(endpoint: HubEndpoint): Result<HubSnapshot>
    suspend fun send(config: HubConfig, command: HubCommand): HubSendResult
}

/** Only a fixed, safe code crosses the client boundary; never attach raw HTTP exceptions. */
class HubClientException(val code: String) : IOException(code)

fun interface HubConnectionFactory {
    fun open(url: URL): HttpURLConnection
}

class HubClient(
    private val connectionFactory: HubConnectionFactory = HubConnectionFactory {
        it.openConnection(Proxy.NO_PROXY) as HttpURLConnection
    },
    private val requestTimeoutMillis: Long = 8_000L
) : HubApi {
    init { require(requestTimeoutMillis in 1..8_000L) }

    override suspend fun inspect(endpoint: HubEndpoint): Result<HubSnapshot> = try {
        val health = get(endpoint, "/api/v1/health")
        val capabilities = get(endpoint, "/api/v1/capabilities")
        val devices = get(endpoint, "/api/v1/devices")
        Result.success(HubContract.snapshot(health, capabilities, devices))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(safeFailure(error))
    }

    override suspend fun send(config: HubConfig, command: HubCommand): HubSendResult = try {
        val response = exchange(config.endpoint, "/api/v1/commands", command.body(), config.apiKey)
        // Redirects are never followed, and their response cannot prove a command was not sent.
        if (response.status in 300..399) {
            HubSendResult.Uncertain(response.status, "redirect_blocked")
        } else {
            val decoded = try { HubJson.decode(response.body) } catch (_: Exception) { null }
            HubContract.sendResult(response.status, command, decoded)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        HubSendResult.Uncertain(null, safeFailure(error).code)
    }

    private suspend fun get(endpoint: HubEndpoint, path: String): Map<String, Any?> {
        val response = exchange(endpoint, path)
        if (response.status != 200) throw HubClientException("http_${response.status}")
        return HubJson.decode(response.body)
    }

    /** Internal transport seam: JVM tests exercise I/O without replacing Android's JSON parser. */
    internal suspend fun exchange(
        endpoint: HubEndpoint,
        path: String,
        body: ByteArray? = null,
        apiKey: String? = null
    ): HubHttpResponse = supervisorScope {
        require(path in setOf("/api/v1/health", "/api/v1/capabilities", "/api/v1/devices", "/api/v1/commands"))
        require(if (path == "/api/v1/commands") body != null && apiKey != null else body == null && apiKey == null)
        require(body == null || body.size <= 512)
        val lifetime = RequestConnection()
        val worker = async(Dispatchers.IO) {
            val connection = connectionFactory.open(URL(endpoint.baseUrl + path))
            lifetime.attach(connection)
            try {
                connection.connectTimeout = 3_000
                connection.readTimeout = 3_000
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.requestMethod = if (body == null) "GET" else "POST"
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Accept-Encoding", "identity")
                connection.setRequestProperty("Connection", "close")
                if (body != null) {
                    connection.doOutput = true
                    connection.setFixedLengthStreamingMode(body.size)
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("X-API-Key", apiKey)
                    lifetime.ensureOpen()
                    connection.outputStream.use { it.write(body) }
                }
                lifetime.ensureOpen()
                val status = connection.responseCode
                if (status !in 100..599) throw HubClientException("invalid_response")
                lifetime.ensureOpen()
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val bytes = stream?.use { readBounded(it, lifetime::ensureOpen) } ?: byteArrayOf()
                HubHttpResponse(status, bytes)
            } finally {
                lifetime.close()
            }
        }
        try {
            withTimeoutOrNull(requestTimeoutMillis) { worker.await() }
                ?: throw HubClientException("timeout")
        } finally {
            // Cancellation of an await alone does not interrupt blocking socket I/O.
            // Disconnect off the main thread, then wait for the worker/streams to finish;
            // the coordinator cannot release its send gate while this request is still alive.
            withContext(NonCancellable + Dispatchers.IO) {
                lifetime.close()
                worker.cancelAndJoin()
            }
        }
    }

    private fun safeFailure(error: Exception): HubClientException = when (error) {
        is HubClientException -> error
        is SocketTimeoutException -> HubClientException("timeout")
        is SecurityException -> HubClientException("network_permission")
        else -> HubClientException("network_error")
    }
}

internal data class HubHttpResponse(val status: Int, val body: ByteArray)

private class RequestConnection {
    private val closed = AtomicBoolean(false)
    private val connection = AtomicReference<HttpURLConnection?>()

    fun attach(value: HttpURLConnection) {
        connection.set(value)
        if (closed.get()) {
            value.disconnect()
            throw HubClientException("cancelled")
        }
    }

    fun ensureOpen() {
        if (closed.get()) throw HubClientException("cancelled")
    }

    fun close() {
        closed.set(true)
        // Disconnect is idempotent; it may race the I/O thread's normal cleanup.
        runCatching { connection.get()?.disconnect() }
    }
}

private fun readBounded(stream: InputStream, ensureOpen: () -> Unit): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(1_024)
    while (true) {
        ensureOpen()
        val count = stream.read(buffer, 0, minOf(buffer.size, 8_193 - output.size()))
        if (count < 0) return output.toByteArray()
        if (count == 0) continue
        output.write(buffer, 0, count)
        if (output.size() > 8_192) throw HubClientException("response_too_large")
    }
}

/** Schema validation is pure Kotlin; native org.json decoding is verified on Android. */
internal object HubContract {
    fun snapshot(
        health: Map<String, Any?>,
        capabilities: Map<String, Any?>,
        registry: Map<String, Any?>
    ): HubSnapshot {
        valid(health["status"] == "ok" && health["device"] == "KhaiRC-ESP32-Hub")
        schema(capabilities)
        schema(registry)
        val flags = objectValue(capabilities["capabilities"])
        val irSend = flags["ir_send"]
        valid(irSend is Boolean)
        val entries = registry["devices"]
        valid(entries is List<*> && entries.size <= 64)
        val devices = (entries as List<*>).map { entry ->
            val device = objectValue(entry)
            val actions = device["supported_actions"]
            valid(actions is List<*> && actions.size <= 32)
            val actionNames = (actions as List<*>).map { token(it) }
            valid(actionNames.distinct().size == actionNames.size)
            HubDevice(token(device["id"]), token(device["availability"]),
                token(device["transport"]), actionNames.toSet())
        }
        valid(devices.map { it.id }.distinct().size == devices.size)
        return HubSnapshot(text(health["firmware"]), text(health["boot_id"]), irSend as Boolean, devices)
    }

    fun sendResult(status: Int, command: HubCommand, body: Map<String, Any?>?): HubSendResult {
        if (status == 200) {
            return try {
                valid(body != null)
                schema(body!!)
                valid(body["device_id"] == command.deviceId && body["action"] == command.action &&
                    body["transport"] == "ir" && body["status"] == "sent" && body["state_verified"] == false)
                HubSendResult.Sent
            } catch (_: HubClientException) {
                HubSendResult.Uncertain(status, "invalid_response")
            }
        }
        val allowed = when (status) {
            400 -> setOf("invalid_json", "invalid_command")
            401 -> setOf("unauthorized")
            404 -> setOf("device_not_found")
            405 -> setOf("method_not_allowed")
            413 -> setOf("payload_too_large")
            415 -> setOf("unsupported_media_type")
            422 -> setOf("unsupported_action")
            500 -> setOf("ir_send_failed")
            503 -> setOf("device_not_ready")
            else -> emptySet()
        }
        val code = (body?.get("error") as? Map<*, *>)?.get("code")
        if (code !is String || code !in allowed) return HubSendResult.Uncertain(status, "invalid_response")
        return if (status == 500) HubSendResult.Uncertain(status, code)
        else HubSendResult.Rejected(status, code)
    }

    private fun schema(value: Map<String, Any?>) {
        val version = value["schema_version"]
        valid(version == 1 || version == 1L)
    }

    private fun text(value: Any?): String {
        valid(value is String && value.isNotBlank() && value.length <= 128 && value.none { it < ' ' || it == '\u007f' })
        return value as String
    }

    private fun token(value: Any?): String = text(value).also {
        valid(it.length <= 64 && it.all { character -> character in 'a'..'z' || character in '0'..'9' || character == '_' })
    }

    @Suppress("UNCHECKED_CAST")
    private fun objectValue(value: Any?): Map<String, Any?> {
        valid(value is Map<*, *> && value.keys.all { it is String })
        return value as Map<String, Any?>
    }

    private fun valid(condition: Boolean) {
        if (!condition) throw HubClientException("invalid_response")
    }
}

private object HubJson {
    fun decode(bytes: ByteArray): Map<String, Any?> {
        try {
            val raw = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            if ('\u0000' in raw) throw HubClientException("invalid_response")
            limitDepth(raw)
            val tokener = JSONTokener(raw)
            val value = tokener.nextValue()
            if (value !is JSONObject || tokener.nextClean() != '\u0000') throw HubClientException("invalid_response")
            return objectValue(value)
        } catch (_: Exception) {
            throw HubClientException("invalid_response")
        }
    }

    private fun objectValue(value: JSONObject): Map<String, Any?> = value.keys().asSequence().associateWith { key ->
        convert(value.get(key))
    }

    private fun convert(value: Any?): Any? = when (value) {
        is JSONObject -> objectValue(value)
        is JSONArray -> (0 until value.length()).map { convert(value.get(it)) }
        JSONObject.NULL -> null
        else -> value
    }

    private fun limitDepth(raw: String) {
        var depth = 0
        var quoted = false
        var escaped = false
        for (character in raw) {
            if (quoted) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> quoted = false
                }
            } else when (character) {
                '"' -> quoted = true
                '{', '[' -> if (++depth > 16) throw HubClientException("invalid_response")
                '}', ']' -> if (--depth < 0) throw HubClientException("invalid_response")
            }
        }
        if (depth != 0 || quoted) throw HubClientException("invalid_response")
    }
}
