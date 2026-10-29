package com.khaiit.kremote.feature.tuya

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TuyaOc09ControllerTest {
    private val credentials = TuyaCredentials("sample-client", "sample-secret", "abcdefgh123456")

    @Test fun mappingsAndMasterIndicatorAreCorrect() {
        assertEquals(listOf("switch_5", "switch_4", "switch_3", "switch_2", "switch_1", "switch_6"),
            TuyaChannel.ALL.map { it.code })
        val zeros = TuyaChannel.CODES.associateWith { false }
        assertNull(TuyaOc09State(channelValues = zeros, verified = false).masterPower)
        assertEquals(false, TuyaOc09State(channelValues = zeros, verified = true).masterPower)
        assertEquals(true, TuyaOc09State(channelValues = zeros + ("switch_6" to true), verified = true).masterPower)
        assertNull(TuyaOc09State(channelValues = mapOf("switch_5" to true), verified = true).masterPower)
    }

    @Test fun refreshAndToggleConfirmFromCloud() = runBlocking {
        val store = MemoryStore(credentials)
        val fake = FakeRepository()
        val controller = TuyaOc09Controller(store, fake)
        controller.refresh()
        assertTrue(controller.state.verified)
        assertEquals(false, controller.state.masterPower)
        controller.toggle("switch_5")
        assertTrue(controller.state.verified)
        assertEquals(true, controller.state.channelPower("switch_5"))
        assertEquals(true, controller.state.masterPower)
        assertEquals(listOf("switch_5" to true), fake.writes)
    }

    @Test fun toggleRefreshesAutomaticallyWhenCloudStatusLags() = runBlocking {
        val fake = FakeRepository(staleReadsAfterWrite = 2)
        val controller = TuyaOc09Controller(MemoryStore(credentials), fake)
        controller.refresh()
        controller.toggle("switch_5")
        assertTrue(controller.state.verified)
        assertEquals(true, controller.state.channelPower("switch_5"))
        assertEquals(1, fake.writes.size) // GET retries must never duplicate POST.
        assertEquals(3, fake.readsAfterWrite)
    }

    @Test fun unconfirmedCloudWriteNeverShowsSuccess() = runBlocking {
        val controller = TuyaOc09Controller(MemoryStore(credentials), FakeRepository(dropWrites = true))
        controller.refresh()
        controller.toggle("switch_1")
        assertFalse(controller.state.verified)
        assertNull(controller.state.channelPower("switch_1"))
        assertTrue(controller.state.message!!.contains("Chưa xác minh"))
    }

    @Test fun masterUsesOneBatchRequestForAllChannels() = runBlocking {
        val fake = FakeRepository()
        val controller = TuyaOc09Controller(MemoryStore(credentials), fake)
        controller.refresh()
        controller.toggleMaster()
        assertEquals(1, fake.batchWrites.size)
        assertEquals(true, fake.batchWrites.single())
        assertTrue(fake.writes.isEmpty())
        assertTrue(controller.state.verified)
        assertEquals(true, controller.state.masterPower)
        assertTrue(TuyaChannel.CODES.all { controller.state.channelPower(it) == true })
        controller.toggleMaster()
        assertEquals(listOf(true, false), fake.batchWrites)
        assertEquals(false, controller.state.masterPower)
        assertTrue(TuyaChannel.CODES.all { controller.state.channelPower(it) == false })
    }

    @Test fun masterPartialResultIsNotReportedAsSuccess() = runBlocking {
        val fake = FakeRepository(partialBatchWrite = true)
        val controller = TuyaOc09Controller(MemoryStore(credentials), fake)
        controller.refresh()
        controller.toggleMaster()
        assertEquals(listOf(true), fake.batchWrites) // never retry POST
        assertFalse(controller.state.verified)
        assertNull(controller.state.masterPower)
        assertTrue(controller.state.message!!.contains("Power tổng chưa xác minh"))
    }

    @Test fun masterDoesNotSendIfStateIsUnknown() = runBlocking {
        val fake = FakeRepository()
        val controller = TuyaOc09Controller(MemoryStore(credentials), fake)
        controller.toggleMaster()
        assertTrue(fake.batchWrites.isEmpty())
        assertNull(controller.state.masterPower)
    }

    @Test fun blankSecretOnSettingsKeepsExisting() = runBlocking {
        val store = MemoryStore(credentials)
        val fake = FakeRepository()
        val controller = TuyaOc09Controller(store, fake)
        assertTrue(controller.saveConfiguration("newclient", "", "anotherdevice123"))
        assertEquals("sample-secret", store.load()!!.clientSecret)
        assertTrue(controller.state.configured)
        assertTrue(controller.state.verified)
        assertEquals(1, fake.sessionClears)
    }

    private class MemoryStore(private var value: TuyaCredentials?) : TuyaConfigStore {
        override fun load(): TuyaCredentials? = value
        override fun save(credentials: TuyaCredentials) { value = credentials }
    }

    private class FakeRepository(
        private val dropWrites: Boolean = false,
        private val staleReadsAfterWrite: Int = 0,
        private val partialBatchWrite: Boolean = false
    ) : TuyaOc09Repository {
        private val values = TuyaChannel.CODES.associateWith { false }.toMutableMap()
        val writes = mutableListOf<Pair<String, Boolean>>()
        val batchWrites = mutableListOf<Boolean>()
        var sessionClears = 0
        var readsAfterWrite = 0
        private var staleReadsRemaining = 0
        private var pendingWrite: Pair<String, Boolean>? = null
        override suspend fun readStatus(credentials: TuyaCredentials): Map<String, Boolean> {
            if (pendingWrite != null) {
                readsAfterWrite++
                if (staleReadsRemaining-- <= 0) {
                    val (code, enabled) = pendingWrite!!
                    values[code] = enabled
                    pendingWrite = null
                }
            }
            return values.toMap()
        }
        override suspend fun setPower(credentials: TuyaCredentials, code: String, enabled: Boolean) {
            writes += code to enabled
            if (!dropWrites) {
                if (staleReadsAfterWrite > 0) {
                    pendingWrite = code to enabled
                    staleReadsRemaining = staleReadsAfterWrite
                } else {
                    values[code] = enabled
                }
            }
        }
        override suspend fun setAllPower(credentials: TuyaCredentials, enabled: Boolean) {
            batchWrites += enabled
            if (!dropWrites) {
                val codes = if (partialBatchWrite) listOf("switch_5") else TuyaChannel.CODES
                codes.forEach { values[it] = enabled }
            }
        }
        override fun clearSession() { sessionClears++ }
    }
}
