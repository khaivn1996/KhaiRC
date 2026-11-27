package com.khaiit.kremote.core.network

import org.junit.Assert.*
import org.junit.Test

class HubConfigTest {
    private val endpoint = HubEndpoint("192.168.2.10")
    private val key = "K".repeat(64)

    @Test fun endpointOnlyAcceptsCanonicalPrivateIpv4() {
        listOf("10.2.3.4", "172.16.0.1", "172.31.9.1", "192.168.2.10").forEach {
            assertEquals(it, HubEndpoint(it).ipv4)
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
}
