package com.khaiit.kremote

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.khaiit.kremote.core.network.HubClient
import com.khaiit.kremote.core.network.HubClientException
import com.khaiit.kremote.core.network.HubCommand
import com.khaiit.kremote.core.network.HubConfig
import com.khaiit.kremote.core.network.HubConnectionFactory
import com.khaiit.kremote.core.network.HubEndpoint
import com.khaiit.kremote.core.network.HubSendResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Real Android org.json, HttpURLConnection and Keystore; all HTTP stays on loopback. */
@RunWith(AndroidJUnit4::class)
class HubIntegrationTest {
    private val endpoint = HubEndpoint("192.168.10.20")
    private val dummyKey = "integration-test-key-".padEnd(64, 'x')
    private val config get() = HubConfig(endpoint, dummyKey)

    @Test
    fun inspectReadsSchemaAndActionsWithoutSendingCredentials() = runBlocking {
        LoopbackServer { request, socket ->
            val body = when (request.path) {
                "/api/v1/health" -> HEALTH
                "/api/v1/capabilities" -> CAPABILITIES
                "/api/v1/devices" -> DEVICES
                else -> error("Unexpected test path")
            }
            socket.respond(200, body)
        }.use { server ->
            val snapshot = server.client().inspect(endpoint).getOrThrow()
            assertEquals("0.4.0-ota1", snapshot.firmware)
            assertEquals("abc123", snapshot.bootId)
            HubCommand.entries.forEach { assertTrue(snapshot.supports(it)) }
            assertEquals(
                listOf("/api/v1/health", "/api/v1/capabilities", "/api/v1/devices"),
                server.requests.map { it.path }
            )
            server.requests.forEach {
                assertEquals("GET", it.method)
                assertNull(it.headers["x-api-key"])
                assertTrue(it.body.isEmpty())
            }
        }
    }

    @Test
    fun inspectRejectsStringSchemaVersionFromNativeJson() = runBlocking {
        LoopbackServer { request, socket ->
            socket.respond(200, when (request.path) {
                "/api/v1/health" -> HEALTH
                "/api/v1/capabilities" -> CAPABILITIES.replace("\"schema_version\":1", "\"schema_version\":\"1\"")
                else -> DEVICES
            })
        }.use { server ->
            val result = server.client().inspect(endpoint)
            assertTrue(result.isFailure)
            assertEquals("invalid_response", (result.exceptionOrNull() as HubClientException).code)
            assertTrue(server.requests.all { it.method == "GET" })
        }
    }

    @Test
    fun postRequiresMatchingSuccessContractAndSendsExactlyOneBody() = runBlocking {
        val responses = listOf(
            SENT,
            SENT.replace("\"schema_version\":1", "\"schema_version\":\"1\""),
            SENT.replace("\"swing\"", "\"power_off\""),
            SENT.replace("\"fan_ir\"", "\"toshiba_ac\""),
            SENT.replace("\"ir\"", "\"cloud\""),
            SENT.replace("\"sent\"", "\"queued\""),
            SENT.replace("false", "true"),
            "{"
        )
        responses.forEachIndexed { index, response ->
            LoopbackServer { _, socket -> socket.respond(200, response) }.use { server ->
                val result = server.client().send(config, HubCommand.FAN_SWING)
                assertEquals(
                    if (index == 0) HubSendResult.Sent else HubSendResult.Uncertain(200, "invalid_response"),
                    result
                )
                assertEquals(1, server.requests.size)
                val request = server.requests.single()
                assertEquals("POST", request.method)
                assertEquals("/api/v1/commands", request.path)
                assertEquals(dummyKey, request.headers["x-api-key"])
                assertEquals("application/json", request.headers["content-type"])
                assertNull(request.headers["transfer-encoding"])
                assertEquals(request.body.size.toString(), request.headers["content-length"])
                val body = JSONObject(String(request.body, Charsets.UTF_8))
                assertEquals("fan_ir", body.getString("device_id"))
                assertEquals("swing", body.getString("action"))
                assertEquals(2, body.length())
            }
        }
    }

    @Test
    fun rejectedAndUncertainStatusesNeverRetry() = runBlocking {
        listOf(
            Triple(401, "unauthorized", HubSendResult.Rejected(401, "unauthorized")),
            Triple(500, "ir_send_failed", HubSendResult.Uncertain(500, "ir_send_failed")),
            Triple(503, "device_not_ready", HubSendResult.Rejected(503, "device_not_ready"))
        ).forEach { (status, code, expected) ->
            LoopbackServer { _, socket -> socket.respond(status, "{\"error\":{\"code\":\"$code\"}}") }.use { server ->
                assertEquals(expected, server.client().send(config, HubCommand.FAN_SWING))
                assertEquals(1, server.requests.size)
            }
        }
    }

    @Test
    fun redirectDoesNotForwardKeyOrRepeatPost() = runBlocking {
        LoopbackServer { _, socket -> socket.respond(200, SENT) }.use { destination ->
            LoopbackServer { _, socket ->
                socket.respond(302, "", mapOf("Location" to destination.url("/redirect")))
            }.use { origin ->
                assertEquals(
                    HubSendResult.Uncertain(302, "redirect_blocked"),
                    origin.client().send(config, HubCommand.FAN_SWING)
                )
                assertEquals(1, origin.requests.size)
                assertTrue("Redirect destination must receive neither request nor API key", destination.requests.isEmpty())
            }
        }
    }

    @Test
    fun lostResponseAfterCompletePostIsUncertainWithoutResend() = runBlocking {
        // The fixture closes the socket after consuming the complete request, without a response.
        LoopbackServer { _, _ -> }.use { server ->
            assertTrue(server.client().send(config, HubCommand.FAN_SWING) is HubSendResult.Uncertain)
            assertEquals(1, server.requests.size)
            assertEquals("POST", server.requests.single().method)
            assertTrue(server.requests.single().body.isNotEmpty())
        }
    }

    @Test
    fun responseOverEightKiBIsBoundedAndNotRetried() = runBlocking {
        LoopbackServer { _, socket -> socket.respond(200, " ".repeat(8_193)) }.use { server ->
            assertEquals(
                HubSendResult.Uncertain(null, "response_too_large"),
                server.client().send(config, HubCommand.FAN_SWING)
            )
            assertEquals(1, server.requests.size)
        }
    }

    @Test
    fun slowDripCannotExtendTheEightSecondOverallDeadline() = runBlocking {
        val peerClosed = CountDownLatch(1)
        LoopbackServer { _, socket ->
            try {
                socket.writeHeaders(200, 4_096)
                val until = SystemClock.elapsedRealtime() + 12_000
                while (SystemClock.elapsedRealtime() < until) {
                    socket.getOutputStream().write(' '.code)
                    socket.getOutputStream().flush()
                    Thread.sleep(150)
                }
            } catch (_: IOException) {
                peerClosed.countDown()
            }
        }.use { server ->
            val start = SystemClock.elapsedRealtime()
            val result = server.client().send(config, HubCommand.FAN_SWING)
            val elapsed = SystemClock.elapsedRealtime() - start
            assertEquals(HubSendResult.Uncertain(null, "timeout"), result)
            assertTrue("Drip response should reach the overall deadline, elapsed=$elapsed", elapsed >= 7_000)
            assertTrue("Request exceeded deadline plus scheduler tolerance, elapsed=$elapsed", elapsed < 9_500)
            assertTrue("Deadline must disconnect the socket", peerClosed.await(2, TimeUnit.SECONDS))
            assertEquals(1, server.requests.size)
        }
    }

    @Test
    fun explicitCancellationClosesTheSocketBeforeReturning() = runBlocking {
        val responseStarted = CountDownLatch(1)
        val peerClosed = CountDownLatch(1)
        LoopbackServer { _, socket ->
            socket.writeHeaders(200, 4_096)
            socket.getOutputStream().write(' '.code)
            socket.getOutputStream().flush()
            responseStarted.countDown()
            socket.soTimeout = 5_000
            try {
                if (socket.getInputStream().read() == -1) peerClosed.countDown()
            } catch (timeout: SocketTimeoutException) {
                throw timeout
            } catch (_: IOException) {
                // Closing with an unread response can reset instead of gracefully closing TCP.
                peerClosed.countDown()
            }
        }.use { server ->
            val call = async(Dispatchers.Default) { server.client().send(config, HubCommand.FAN_SWING) }
            assertTrue(responseStarted.await(3, TimeUnit.SECONDS))
            val start = SystemClock.elapsedRealtime()
            call.cancelAndJoin()
            val elapsed = SystemClock.elapsedRealtime() - start
            assertTrue(call.isCancelled)
            assertTrue("Cancellation retained a blocking request, elapsed=$elapsed", elapsed < 2_500)
            assertTrue("Cancellation must close the peer socket", peerClosed.await(1, TimeUnit.SECONDS))
            assertEquals(1, server.requests.size)
        }
    }

    @Test
    fun encryptedSettingsRoundTripAndCorruptionPreserveStoredData() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val testPrefsName = "hub_integration_" + UUID.randomUUID().toString()
        // Isolate the blob from production preferences; never read a user's Hub or Tuya values.
        val isolated = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                base.getSharedPreferences(testPrefsName, mode)
        }
        val prefs = isolated.getSharedPreferences("hub_secure_v1", Context.MODE_PRIVATE)
        try {
            val store = HubSettingsStore(isolated)
            assertNull(store.load())
            val settings = HubSettings(config, HubTransport.HUB, HubTransport.PHONE)
            store.save(settings)
            val firstBlob = prefs.getString("config", null)
            assertNotNull(firstBlob)
            assertFalse(firstBlob!!.contains(dummyKey))
            assertFalse(String(Base64.decode(firstBlob, Base64.NO_WRAP), Charsets.UTF_8).contains(dummyKey))
            assertFalse(settings.toString().contains(dummyKey))
            assertFalse(config.toString().contains(dummyKey))
            val loaded = store.load()!!
            assertEquals(endpoint, loaded.config.endpoint)
            assertEquals(dummyKey, loaded.config.apiKey)
            assertEquals(HubTransport.HUB, loaded.fanTransport)
            assertEquals(HubTransport.PHONE, loaded.fixTransport)
            store.save(settings)
            assertNotEquals("Each save needs a fresh GCM IV", firstBlob, prefs.getString("config", null))

            val bytes = Base64.decode(prefs.getString("config", null), Base64.NO_WRAP)
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            val corrupted = Base64.encodeToString(bytes, Base64.NO_WRAP)
            assertTrue(prefs.edit().putString("config", corrupted).commit())
            val error = runCatching { store.load() }.exceptionOrNull()
            assertTrue(error is IllegalStateException)
            assertNull("No parser/crypto cause should expose decrypted input", error!!.cause)
            assertFalse(error.message.orEmpty().contains(dummyKey))
            assertEquals("Loading corrupt data must not silently erase it", corrupted, prefs.getString("config", null))
            store.save(settings)
            assertEquals(dummyKey, store.load()!!.config.apiKey)
        } finally {
            base.deleteSharedPreferences(testPrefsName)
        }
    }

    private companion object {
        const val HEALTH = "{\"status\":\"ok\",\"device\":\"KhaiRC-ESP32-Hub\",\"firmware\":\"0.4.0-ota1\",\"boot_id\":\"abc123\"}"
        const val CAPABILITIES = "{\"schema_version\":1,\"capabilities\":{\"ir_send\":true,\"ota_update\":true}}"
        const val DEVICES = "{\"schema_version\":1,\"devices\":[{\"id\":\"fan_ir\",\"availability\":\"ready\",\"transport\":\"ir\",\"supported_actions\":[\"power_on\",\"swing\",\"power_off\"]},{\"id\":\"toshiba_ac\",\"availability\":\"ready\",\"transport\":\"ir\",\"supported_actions\":[\"fix\"]}]}"
        const val SENT = "{\"schema_version\":1,\"device_id\":\"fan_ir\",\"action\":\"swing\",\"transport\":\"ir\",\"status\":\"sent\",\"state_verified\":false}"
    }
}

private data class RecordedRequest(val method: String, val path: String, val headers: Map<String, String>, val body: ByteArray)

/** Fixed-size requests, bounded reads and daemon threads; close also shuts down stalled peers. */
private class LoopbackServer(private val respond: (RecordedRequest, Socket) -> Unit) : Closeable {
    private val listener = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 250 }
    private val stopped = AtomicBoolean(false)
    private val peers = ConcurrentHashMap.newKeySet<Socket>()
    private val workers = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "hub-test-peer").apply { isDaemon = true }
    }
    val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val acceptThread = Thread({
        while (!stopped.get()) {
            try {
                val peer = listener.accept().apply { soTimeout = 5_000 }
                peers.add(peer)
                workers.execute {
                    try {
                        peer.use {
                            val request = it.readRequest()
                            requests.add(request)
                            respond(request, it)
                        }
                    } catch (_: IOException) {
                        // A client disconnect is part of deadline/cancellation tests.
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    } finally {
                        peers.remove(peer)
                    }
                }
            } catch (_: SocketTimeoutException) {
                // Check stop flag again.
            } catch (_: IOException) {
                if (!stopped.get()) throw IllegalStateException("Loopback accept failed")
            }
        }
    }, "hub-test-accept").apply { isDaemon = true; start() }

    fun url(path: String): String = "http://127.0.0.1:${listener.localPort}$path"

    fun client(): HubClient = HubClient(HubConnectionFactory { requested ->
        // Production validates private IPv4; only this test seam substitutes a loopback socket.
        URL(url(requested.path)).openConnection(Proxy.NO_PROXY) as HttpURLConnection
    })

    override fun close() {
        stopped.set(true)
        listener.close()
        peers.forEach { runCatching { it.close() } }
        workers.shutdownNow()
        acceptThread.join(1_000)
        check(workers.awaitTermination(2, TimeUnit.SECONDS)) { "Loopback worker did not stop" }
    }
}

private fun Socket.readRequest(): RecordedRequest {
    val input = getInputStream()
    val start = input.readAsciiLine().split(' ')
    require(start.size == 3)
    val headers = linkedMapOf<String, String>()
    var headerBytes = 0
    while (true) {
        val line = input.readAsciiLine()
        headerBytes += line.length
        require(headerBytes <= 8_192)
        if (line.isEmpty()) break
        val colon = line.indexOf(':')
        require(colon > 0)
        headers[line.substring(0, colon).lowercase(Locale.ROOT)] = line.substring(colon + 1).trim()
    }
    val length = headers["content-length"]?.toInt() ?: 0
    require(length in 0..1_024)
    val body = ByteArray(length)
    var offset = 0
    while (offset < length) {
        val count = input.read(body, offset, length - offset)
        if (count < 0) throw EOFException()
        offset += count
    }
    return RecordedRequest(start[0], start[1], headers, body)
}

private fun InputStream.readAsciiLine(): String {
    val bytes = ByteArrayOutputStream()
    while (bytes.size() <= 8_192) {
        val next = read()
        if (next < 0) throw EOFException()
        if (next == '\n'.code) return bytes.toString("US-ASCII").removeSuffix("\r")
        bytes.write(next)
    }
    throw IOException("Test request header too large")
}

private fun Socket.writeHeaders(status: Int, length: Int, headers: Map<String, String> = emptyMap()) {
    val text = buildString {
        append("HTTP/1.1 $status Test\r\nContent-Type: application/json\r\n")
        append("Content-Length: $length\r\nConnection: close\r\n")
        headers.forEach { (name, value) -> append("$name: $value\r\n") }
        append("\r\n")
    }
    getOutputStream().write(text.toByteArray(Charsets.US_ASCII))
    getOutputStream().flush()
}

private fun Socket.respond(status: Int, body: String, headers: Map<String, String> = emptyMap()) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    writeHeaders(status, bytes.size, headers)
    getOutputStream().write(bytes)
    getOutputStream().flush()
}
