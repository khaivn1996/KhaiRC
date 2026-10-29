package com.khaiit.kremote.feature.tuya

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TuyaCredentialPrivacyTest {
    private val sample = TuyaCredentials("client-abc", "secret-top-private", "device-id-123")

    @Test fun toStringNeverRevealsSensitiveValues() {
        val printed = sample.toString()
        assertFalse(printed.contains(sample.clientId))
        assertFalse(printed.contains(sample.clientSecret))
        assertFalse(printed.contains(sample.deviceId))
    }

    @Test fun blankSettingsFieldsPreserveExistingCredentials() = runBlocking {
        val store = MemoryStore(sample)
        val repo = FakeRepository()
        val controller = TuyaOc09Controller(store, repo)
        assertTrue(controller.saveConfiguration("", "", ""))
        assertEquals(sample, store.load())
        assertTrue(controller.state.verified)
        assertEquals(1, repo.sessionClears)
    }

    @Test fun changingOnlyOneFieldPreservesOtherTwo() = runBlocking {
        val store = MemoryStore(sample)
        val controller = TuyaOc09Controller(store, FakeRepository())
        assertTrue(controller.saveConfiguration("", "", "new-device-456"))
        assertEquals(sample.copy(deviceId = "new-device-456"), store.load())
        assertTrue(controller.saveConfiguration("new-client", "", ""))
        assertEquals(sample.copy(clientId = "new-client", deviceId = "new-device-456"), store.load())
        assertTrue(controller.saveConfiguration("", "new-secret", ""))
        assertEquals(TuyaCredentials("new-client", "new-secret", "new-device-456"), store.load())
    }

    @Test fun firstTimeSetupRequiresAllThreeFields() = runBlocking {
        val attempts = listOf(
            Triple("", "", ""),
            Triple("", "secret-one", "device-123"),
            Triple("client-one", "", "device-123"),
            Triple("client-one", "secret-one", "")
        )
        for ((clientId, secret, deviceId) in attempts) {
            val store = MemoryStore(null)
            val controller = TuyaOc09Controller(store, FakeRepository())
            assertFalse(controller.saveConfiguration(clientId, secret, deviceId))
            assertNull(store.load())
            assertFalse(controller.state.configured)
        }
    }

    private class MemoryStore(private var data: TuyaCredentials?) : TuyaConfigStore {
        override fun load(): TuyaCredentials? = data
        override fun save(credentials: TuyaCredentials) { data = credentials }
    }

    private class FakeRepository : TuyaOc09Repository {
        var sessionClears = 0
        override suspend fun readStatus(credentials: TuyaCredentials): Map<String, Boolean> =
            TuyaChannel.CODES.associateWith { false }
        override suspend fun setPower(credentials: TuyaCredentials, code: String, enabled: Boolean) = Unit
        override suspend fun setAllPower(credentials: TuyaCredentials, enabled: Boolean) = Unit
        override fun clearSession() { sessionClears++ }
    }
}
