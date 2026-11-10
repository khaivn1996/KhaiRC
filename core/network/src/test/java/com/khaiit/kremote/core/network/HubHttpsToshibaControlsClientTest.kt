package com.khaiit.kremote.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HubHttpsToshibaControlsClientTest {
    @Test fun fixedVocabularyAndStateEncoding() {
        assertEquals(
            "{\"device_id\":\"toshiba_ac\",\"action\":\"state\",\"power\":true,\"temperature_c\":24,\"fan_speed\":\"auto\"}",
            ToshibaHttpsAction.State(true, 24, ToshibaHttpsFan.AUTO).body().toString(Charsets.UTF_8))
        assertEquals("{\"device_id\":\"toshiba_ac\",\"action\":\"fix\"}",
            ToshibaHttpsAction.Fix.body().toString(Charsets.UTF_8))
        assertEquals("{\"device_id\":\"toshiba_ac\",\"action\":\"swing_on\"}",
            ToshibaHttpsAction.SwingOn.body().toString(Charsets.UTF_8))
        assertEquals("{\"device_id\":\"toshiba_ac\",\"action\":\"swing_off\"}",
            ToshibaHttpsAction.SwingOff.body().toString(Charsets.UTF_8))
    }
    @Test fun stateBounds() {
        assertThrows(IllegalArgumentException::class.java) {
            ToshibaHttpsAction.State(true, 16, ToshibaHttpsFan.AUTO)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ToshibaHttpsAction.State(true, 31, ToshibaHttpsFan.AUTO)
        }
        assertEquals(6, ToshibaHttpsFan.entries.size)
    }
}
