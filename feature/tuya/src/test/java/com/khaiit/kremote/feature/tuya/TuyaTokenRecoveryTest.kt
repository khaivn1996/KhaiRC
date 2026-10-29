package com.khaiit.kremote.feature.tuya

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class TuyaTokenRecoveryTest {
    @Test fun onlyKnownCloudTokenErrorsAreRecognized() {
        for (code in listOf("1010", "1011", "1012", "1400")) {
            assertTrue(TuyaTokenRecovery.isTokenErrorCode(code))
        }
        for (code in listOf("1004", "1005", "1106", "1199", "2001", "2007", "401")) {
            assertFalse(TuyaTokenRecovery.isTokenErrorCode(code))
        }
    }

    @Test fun rejectedGetClearsTokenAndRepeatsOnceWithNewToken() = runBlocking {
        val requests = mutableListOf<String>()
        var tokenVersion = 0
        var invalidations = 0
        val value = TuyaTokenRecovery.call(
            retryReadOnly = true,
            getToken = { if (tokenVersion == 0) "old" else "fresh" },
            clearToken = { invalidations++; tokenVersion++ },
            request = { token ->
                requests += token
                if (token == "old") throw TuyaTokenRejectedException("1010", "expired")
                "latest-state"
            }
        )
        assertEquals("latest-state", value)
        assertEquals(listOf("old", "fresh"), requests)
        assertEquals(1, invalidations)
    }

    @Test fun rejectedPostClearsTokenButNeverResendsCommand() = runBlocking {
        val requests = mutableListOf<String>()
        var invalidations = 0
        try {
            TuyaTokenRecovery.call(
                retryReadOnly = false,
                getToken = { "old" },
                clearToken = { invalidations++ },
                request = { token: String ->
                    requests += token
                    throw TuyaTokenRejectedException("1011", "invalid")
                }
            )
            fail("Expected TuyaTokenRejectedException")
        } catch (expected: TuyaTokenRejectedException) {
            assertEquals(listOf("old"), requests)
            assertEquals(1, invalidations)
        }
    }

    @Test fun unrelatedErrorIsNotRetriedAndKeepsCurrentToken() = runBlocking {
        var requests = 0
        var invalidations = 0
        try {
            TuyaTokenRecovery.call(
                retryReadOnly = true,
                getToken = { "current" },
                clearToken = { invalidations++ },
                request = { _: String ->
                    requests++
                    throw IOException("Tuya 2001: device offline")
                }
            )
            fail("Expected IOException")
        } catch (expected: IOException) {
            assertEquals(1, requests)
            assertEquals(0, invalidations)
        }
    }

    @Test fun secondRejectedGetStopsAfterOneRetry() = runBlocking {
        var requests = 0
        var invalidations = 0
        try {
            TuyaTokenRecovery.call(
                retryReadOnly = true,
                getToken = { "token-$requests" },
                clearToken = { invalidations++ },
                request = { _: String ->
                    requests++
                    throw TuyaTokenRejectedException("1012", "token status invalid")
                }
            )
            fail("Expected TuyaTokenRejectedException")
        } catch (expected: TuyaTokenRejectedException) {
            assertEquals(2, requests)
            assertEquals(2, invalidations)
        }
    }
}
