package com.khaiit.kremote.core.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

class MilifeStatusContractTest {
    private val response = mapOf<String, Any?>(
        "schema_version" to 1,
        "device_id" to "milife_plug_10a",
        "transport" to "miio_lan",
        "power" to true,
        "state_verified" to true
    )

    @Test fun acceptsOnlyVerifiedBooleanFromExactMilifeDevice() {
        assertTrue(HubContract.milifePower(response))
        assertFalse(HubContract.milifePower(response + ("power" to false)))
        listOf(
            "schema_version" to "1", "device_id" to "fan_ir",
            "transport" to "cloud", "power" to "true",
            "state_verified" to false, "state_verified" to "true"
        ).forEach { (field, value) ->
            assertThrows(HubClientException::class.java) {
                HubContract.milifePower(response + (field to value))
            }
        }
        assertThrows(HubClientException::class.java) {
            HubContract.milifePower(response - "power")
        }
    }

    @Test fun sendsOnlyOneAuthenticatedGetAndNeverFollowsRedirect() = runBlocking {
        val calls = AtomicInteger()
        val url = URL("http://192.168.2.200:80/api/v1/devices/milife_plug_10a/state")
        val conn = object : HttpURLConnection(url) {
            override fun connect() = Unit
            override fun disconnect() = Unit
            override fun usingProxy() = false
            override fun getResponseCode() = 302
            override fun getErrorStream(): java.io.InputStream? = null
        }
        val key = "k".repeat(64)
        val client = HubClient({ calls.incrementAndGet(); conn })
        val result = client.readMilife(HubConfig(HubEndpoint("192.168.2.200"), key))
        assertTrue(result.isFailure)
        assertEquals("http_302", (result.exceptionOrNull() as HubClientException).code)
        assertEquals(1, calls.get())
        assertEquals("GET", conn.requestMethod)
        assertEquals(key, conn.getRequestProperty("X-API-Key"))
        assertFalse(conn.instanceFollowRedirects)
        assertFalse(conn.useCaches)
        assertFalse(conn.doOutput)
    }
}
