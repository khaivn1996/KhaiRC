package com.khaiit.kremote.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Test

class HubHttpsProbeTest {
    private val fingerprint = "AA:".repeat(31) + "AA"

    @Test fun acceptsHttpsDomainOrPrivateIpWithAnExplicitPin() {
        val domain = HubHttpsEndpoint("openwrt96.ddns.net", 18443, fingerprint)
        assertEquals("https://openwrt96.ddns.net:18443", domain.baseUrl)
        assertEquals("AA".repeat(32), domain.fingerprintHex)
        assertFalse(domain.toString().contains(domain.fingerprintHex))
        assertEquals("https://192.168.2.200:443",
            HubHttpsEndpoint("192.168.2.200", 443, fingerprint).baseUrl)
    }

    @Test fun rejectsProtocolDowngradeMalformedUrlsAndMissingPins() {
        listOf("http://openwrt96.ddns.net", "https://openwrt96.ddns.net", "openwrt96.ddns.net/path",
            "user@openwrt96.ddns.net", "openwrt96.ddns.net:443", "localhost", "8.8.8.8",
            "192.168.002.200", "OPENWRT96.DDNS.NET", "evil..net", "127.0.0.1").forEach {
            assertThrows(IllegalArgumentException::class.java) {
                HubHttpsEndpoint(it, 443, fingerprint)
            }
        }
        listOf(0, -1, 65536).forEach {
            assertThrows(IllegalArgumentException::class.java) {
                HubHttpsEndpoint("openwrt96.ddns.net", it, fingerprint)
            }
        }
        listOf("", "AA", "0".repeat(63), "G".repeat(64)).forEach {
            assertThrows(IllegalArgumentException::class.java) {
                HubHttpsEndpoint("openwrt96.ddns.net", 443, it)
            }
        }
    }

    @Test fun rejectsMissingServerCertificate() {
        val manager = PinnedSelfSignedTrustManager("AA".repeat(32))
        assertThrows(java.security.cert.CertificateException::class.java) {
            manager.checkServerTrusted(emptyArray(), "ECDHE_ECDSA")
        }
    }
}
