package com.khaiit.kremote.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class HubClientTest {
    private val endpoint = HubEndpoint("192.168.2.10")
    private val key = "K".repeat(64)

    @Test fun endpointOnlyAcceptsCanonicalPrivateIpv4() {
        listOf("10.2.3.4", "172.16.0.1", "172.31.9.1", "192.168.2.10").forEach {
            assertEquals("http://$it:80", HubEndpoint(it).baseUrl)
        }
        listOf("127.0.0.1", "8.8.8.8", "172.15.0.1", "172.32.0.1", "169.254.1.2",
            "224.0.0.1", "10.0.0.0", "10.255.255.255", "192.168.255.255", "hub.local",
            "http://192.168.2.10", "192.168.2.10:80", "192.168.002.10", " 192.168.2.10",
            "192.168.2.10\n", "192.168.2.256", "::1", "192.168.2.١").forEach {
            assertThrows(IllegalArgumentException::class.java) { HubEndpoint(it) }
        }
    }

    @Test fun configRejectsHeaderInjectionAndNeverPrintsKey() {
        assertFalse(HubConfig(endpoint, key).toString().contains(key))
        assertEquals("!".repeat(64), HubConfig(endpoint, "!".repeat(64)).apiKey)
        listOf("", "K".repeat(63), "K".repeat(65), "K".repeat(63) + "\r",
            "K".repeat(63) + "\n", "K".repeat(63) + "\u0000", "K".repeat(63) + "é").forEach {
            assertThrows(IllegalArgumentException::class.java) { HubConfig(endpoint, it) }
        }
    }

    @Test fun fixedCommandVocabularyHasExactlyTheFourApprovedMappings() {
        val mappings = mapOf(HubCommand.FAN_HIGH to ("fan_ir" to "power_on"),
            HubCommand.FAN_SWING to ("fan_ir" to "swing"),
            HubCommand.FAN_OFF to ("fan_ir" to "power_off"),
            HubCommand.TOSHIBA_FIX to ("toshiba_ac" to "fix"))
        assertEquals(mappings.keys, HubCommand.entries.toSet())
        mappings.forEach { (command, values) ->
            assertEquals(values.first, command.deviceId)
            assertEquals(values.second, command.action)
            assertEquals("{\"device_id\":\"${values.first}\",\"action\":\"${values.second}\"}",
                command.body().toString(Charsets.UTF_8))
            assertTrue(command.body().size <= 512)
        }
    }

    @Test fun readinessRequiresBothCapabilityAndTheSpecificReadyIrAction() {
        val ready = HubContract.snapshot(health(), capabilities(), registry())
        assertTrue(ready.supports(HubCommand.FAN_HIGH))
        assertTrue(ready.supports(HubCommand.FAN_SWING))
        assertFalse(ready.supports(HubCommand.TOSHIBA_FIX))
        assertFalse(ready.copy(irSend = false).supports(HubCommand.FAN_HIGH))
        assertFalse(ready.copy(devices = ready.devices.map { it.copy(availability = "not_ready") })
            .supports(HubCommand.FAN_HIGH))
        assertFalse(ready.copy(devices = ready.devices.map { it.copy(transport = "cloud") })
            .supports(HubCommand.FAN_HIGH))
        assertFalse(ready.copy(devices = ready.devices.map { it.copy(supportedActions = setOf("swing")) })
            .supports(HubCommand.FAN_HIGH))
    }

    @Test fun schemaValidationRejectsCoercionsMissingFieldsAndDuplicateDeviceIds() {
        listOf("1", 1.0, 2, null).forEach { version ->
            assertThrows(HubClientException::class.java) {
                HubContract.snapshot(health(), capabilities() + ("schema_version" to version), registry())
            }
        }
        assertThrows(HubClientException::class.java) {
            HubContract.snapshot(health() + ("device" to "another-device"), capabilities(), registry())
        }
        assertThrows(HubClientException::class.java) {
            HubContract.snapshot(health(), capabilities() + ("capabilities" to mapOf("ir_send" to "true")), registry())
        }
        assertThrows(HubClientException::class.java) {
            HubContract.snapshot(health(), capabilities(), registry() + ("devices" to listOf(device(), device())))
        }
        assertThrows(HubClientException::class.java) {
            HubContract.snapshot(health(), capabilities(), registry() + ("devices" to listOf(device() - "supported_actions")))
        }
        // Forward-compatible extra fields do not change known schema semantics.
        assertTrue(HubContract.snapshot(health() + ("future" to 123), capabilities(), registry())
            .supports(HubCommand.FAN_OFF))
    }

    @Test fun sentRequiresExactIdentityTransportStatusAndUnverifiedState() {
        assertEquals(HubSendResult.Sent, HubContract.sendResult(200, HubCommand.FAN_SWING, sent()))
        listOf("schema_version" to "1", "schema_version" to 1.0, "device_id" to "toshiba_ac",
            "action" to "power_on", "transport" to "cloud", "status" to "accepted",
            "state_verified" to true, "state_verified" to "false").forEach { invalid ->
            assertEquals(HubSendResult.Uncertain(200, "invalid_response"),
                HubContract.sendResult(200, HubCommand.FAN_SWING, sent() + invalid))
        }
        assertEquals(HubSendResult.Uncertain(200, "invalid_response"),
            HubContract.sendResult(200, HubCommand.FAN_SWING, null))
    }

    @Test fun knownRejectionsAreSafeButSendFailureAndMalformedResponsesStayUncertain() {
        mapOf(400 to "invalid_command", 401 to "unauthorized", 404 to "device_not_found",
            405 to "method_not_allowed", 413 to "payload_too_large", 415 to "unsupported_media_type",
            422 to "unsupported_action", 503 to "device_not_ready").forEach { (status, code) ->
            assertEquals(HubSendResult.Rejected(status, code),
                HubContract.sendResult(status, HubCommand.FAN_SWING, mapOf("error" to mapOf("code" to code))))
        }
        assertEquals(HubSendResult.Uncertain(500, "ir_send_failed"),
            HubContract.sendResult(500, HubCommand.FAN_SWING, mapOf("error" to mapOf("code" to "ir_send_failed"))))
        assertEquals(HubSendResult.Uncertain(401, "invalid_response"),
            HubContract.sendResult(401, HubCommand.FAN_SWING, mapOf("error" to mapOf("code" to key))))
    }

    @Test fun postIsStreamedOnceWithRedirectsAndCachingDisabled() = runBlocking {
        val calls = AtomicInteger()
        val connection = FakeConnection(URL(endpoint.baseUrl + "/api/v1/commands"), 302)
        val client = HubClient(HubConnectionFactory { calls.incrementAndGet(); connection })
        val result = client.send(HubConfig(endpoint, key), HubCommand.FAN_SWING)
        assertEquals(HubSendResult.Uncertain(302, "redirect_blocked"), result)
        assertEquals(1, calls.get())
        assertEquals("POST", connection.requestMethod)
        assertFalse(connection.instanceFollowRedirects)
        assertFalse(connection.useCaches)
        assertEquals(3_000, connection.connectTimeout)
        assertEquals(3_000, connection.readTimeout)
        assertEquals(key, connection.getRequestProperty("X-API-Key"))
        assertEquals("application/json", connection.getRequestProperty("Content-Type"))
        assertEquals(connection.output.size(), connection.fixedLength)
        assertArrayEquals(HubCommand.FAN_SWING.body(), connection.output.toByteArray())
        assertTrue(connection.outputClosed)
        assertTrue(connection.disconnects.get() > 0)
    }

    @Test fun getHasNoKeyAndEightKiBResponseLimitIsEnforced() = runBlocking {
        val okay = FakeConnection(URL(endpoint.baseUrl), 200, ByteArray(8_192))
        val client = HubClient(HubConnectionFactory { okay })
        assertEquals(8_192, client.exchange(endpoint, "/api/v1/health").body.size)
        assertEquals("GET", okay.requestMethod)
        assertNull(okay.getRequestProperty("X-API-Key"))
        assertEquals(0, okay.output.size())
        val tooLarge = FakeConnection(URL(endpoint.baseUrl), 200, ByteArray(8_193))
        val failure = runCatching { HubClient(HubConnectionFactory { tooLarge }).exchange(endpoint, "/api/v1/health") }
        assertEquals("response_too_large", (failure.exceptionOrNull() as HubClientException).code)
        assertTrue(tooLarge.inputClosed)
        assertTrue(tooLarge.disconnects.get() > 0)
    }

    @Test fun lostPostResponseNeverCreatesASecondConnection() = runBlocking {
        val calls = AtomicInteger()
        val connection = FakeConnection(URL(endpoint.baseUrl), failRead = true)
        val result = HubClient(HubConnectionFactory { calls.incrementAndGet(); connection })
            .send(HubConfig(endpoint, key), HubCommand.FAN_SWING)
        assertEquals(HubSendResult.Uncertain(null, "network_error"), result)
        assertEquals(1, calls.get())
        assertArrayEquals(HubCommand.FAN_SWING.body(), connection.output.toByteArray())
        assertTrue(connection.inputClosed)
    }

    @Test fun totalDeadlineDisconnectsBlockedReadAndWaitsForCleanup() = runBlocking {
        val connection = FakeConnection(URL(endpoint.baseUrl), blockRead = true)
        val result = withTimeout(3_000) {
            HubClient(HubConnectionFactory { connection }, requestTimeoutMillis = 1_000)
                .send(HubConfig(endpoint, key), HubCommand.FAN_SWING)
        }
        assertEquals(HubSendResult.Uncertain(null, "timeout"), result)
        assertTrue(connection.disconnects.get() > 0)
        assertTrue(connection.inputClosed)
        assertTrue(connection.readFinished)
    }

    @Test fun cancellationClosesConnectionAndFinishesWorkerBeforeReturning() = runBlocking {
        val connection = FakeConnection(URL(endpoint.baseUrl), blockRead = true)
        val operation = async { HubClient(HubConnectionFactory { connection }).send(HubConfig(endpoint, key), HubCommand.FAN_SWING) }
        assertTrue(withContext(Dispatchers.IO) { connection.readStarted.await(2, TimeUnit.SECONDS) })
        withTimeout(3_000) { operation.cancelAndJoin() }
        assertTrue(operation.isCancelled)
        assertTrue(connection.disconnects.get() > 0)
        assertTrue(connection.inputClosed)
        assertTrue(connection.readFinished)
    }

    private fun health(): Map<String, Any?> = mapOf("status" to "ok", "device" to "KhaiRC-ESP32-Hub",
        "firmware" to "0.4.0-ota1", "boot_id" to "12ab")
    private fun capabilities(): Map<String, Any?> = mapOf("schema_version" to 1, "capabilities" to mapOf("ir_send" to true))
    private fun registry(): Map<String, Any?> = mapOf("schema_version" to 1, "devices" to listOf(device()))
    private fun device(): Map<String, Any?> = mapOf("id" to "fan_ir", "availability" to "ready", "transport" to "ir",
        "supported_actions" to listOf("power_on", "swing", "power_off"))
    private fun sent(): Map<String, Any?> = mapOf("schema_version" to 1, "device_id" to "fan_ir", "action" to "swing",
        "transport" to "ir", "status" to "sent", "state_verified" to false)

    private class FakeConnection(
        url: URL,
        private val status: Int = 200,
        private val payload: ByteArray = byteArrayOf(),
        private val blockRead: Boolean = false,
        private val failRead: Boolean = false
    ) : HttpURLConnection(url) {
        val disconnects = AtomicInteger()
        val readStarted = CountDownLatch(1)
        private val disconnected = CountDownLatch(1)
        @Volatile var inputClosed = false
        @Volatile var outputClosed = false
        @Volatile var readFinished = false
        val fixedLength: Int get() = fixedContentLength
        val output = object : ByteArrayOutputStream() {
            override fun close() { outputClosed = true; super.close() }
        }
        override fun connect() = Unit
        override fun usingProxy() = false
        override fun disconnect() { disconnects.incrementAndGet(); disconnected.countDown() }
        override fun getResponseCode() = status
        override fun getOutputStream() = output
        override fun getInputStream(): InputStream = object : ByteArrayInputStream(payload) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                readStarted.countDown()
                if (blockRead) {
                    try {
                        check(disconnected.await(3, TimeUnit.SECONDS)) { "Client did not disconnect" }
                        throw IOException("Disconnected")
                    } finally { readFinished = true }
                }
                if (failRead) throw IOException("Response lost")
                return super.read(buffer, offset, length)
            }
            override fun close() { inputClosed = true; super.close() }
        }
        override fun getErrorStream(): InputStream = inputStream
    }
}
