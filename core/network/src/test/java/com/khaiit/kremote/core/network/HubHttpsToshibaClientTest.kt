package com.khaiit.kremote.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HubHttpsToshibaClientTest {
    @Test fun httpsFixUsesOnlyTheSingleSupportedToshibaAction() {
        assertEquals("/api/v1/commands", ToshibaFixRequest.PATH)
        val payload = String(ToshibaFixRequest.body(), Charsets.UTF_8)
        assertEquals("{\"device_id\":\"toshiba_ac\",\"action\":\"fix\"}", payload)
        assertFalse(payload.contains("milife"))
        assertFalse(payload.contains("fan_ir"))
        assertFalse(payload.contains("daikin"))
    }
}
