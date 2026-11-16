package com.khaiit.kremote

import org.junit.Assert.*
import org.junit.Test

class HttpsStoredEndpointTest {
    private val pin = "AB".repeat(32)

    @Test fun nothingSavedIsEmptyGroup() {
        assertNull(parseStoredHttpsEndpoint(host = null, port = 443, pin = null))
    }

    @Test fun validSavedEndpointIsRead() {
        val endpoint = parseStoredHttpsEndpoint("hub.example.net", 8443, pin)
        assertEquals("hub.example.net", endpoint?.host)
        assertEquals(8443, endpoint?.port)
        assertEquals(pin, endpoint?.fingerprintHex)
    }

    // Each case must throw (unreadable) instead of returning null (empty), or a strict backup
    // push would drop the HTTPS group from a good cloud copy.
    @Test fun partialEntryIsUnreadableNotEmpty() {
        assertThrows(IllegalArgumentException::class.java) { parseStoredHttpsEndpoint("hub.example.net", 443, null) }
        assertThrows(IllegalArgumentException::class.java) { parseStoredHttpsEndpoint(null, 443, pin) }
    }

    @Test fun invalidEntryIsUnreadableNotEmpty() {
        assertThrows(IllegalArgumentException::class.java) { parseStoredHttpsEndpoint("hub.example.net", 443, "1234") }
        assertThrows(IllegalArgumentException::class.java) { parseStoredHttpsEndpoint("hub.example.net", 0, pin) }
        assertThrows(IllegalArgumentException::class.java) { parseStoredHttpsEndpoint("Not A Host", 443, pin) }
    }
}
