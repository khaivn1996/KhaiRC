package com.khaiit.kremote.core.network

import org.junit.Assert.*
import org.junit.Test

class MilifeStatusContractTest {
    @Test fun encodesExplicitPowerTargets() {
        assertEquals("""{"device_id":"milife_plug_10a","action":"power_on"}""",
            milifePowerBody(true).decodeToString())
        assertEquals("""{"device_id":"milife_plug_10a","action":"power_off"}""",
            milifePowerBody(false).decodeToString())
    }

    @Test fun onlyKnownRejectionsSkipReadBack() {
        assertTrue(isMilifePowerRejection(504, "miot_hello_timeout"))
        assertTrue(isMilifePowerRejection(502, "miot_device_rejected"))
        assertTrue(isMilifePowerRejection(422, "unsupported_action"))
        assertFalse(isMilifePowerRejection(504, "miot_write_timeout"))
        assertFalse(isMilifePowerRejection(502, "miot_write_unconfirmed"))
        assertFalse(isMilifePowerRejection(502, "miot_hello_timeout"))
        assertFalse(isMilifePowerRejection(200, "sent"))
    }
}
