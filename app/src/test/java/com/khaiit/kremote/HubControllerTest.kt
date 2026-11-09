package com.khaiit.kremote

import com.khaiit.kremote.core.network.HubApi
import com.khaiit.kremote.core.network.HubCommand
import com.khaiit.kremote.core.network.HubConfig
import com.khaiit.kremote.core.network.HubDevice
import com.khaiit.kremote.core.network.HubEndpoint
import com.khaiit.kremote.core.network.HubSendResult
import com.khaiit.kremote.core.network.HubSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class HubControllerTest {
    private val config = HubConfig(HubEndpoint("192.168.2.200"), "k".repeat(64))
    private val ready = HubSnapshot("0.4.0-ota1", "test-boot", true, listOf(
        HubDevice("fan_ir", "ready", "ir", setOf("power_on", "swing", "power_off")),
        HubDevice("toshiba_ac", "ready", "ir", setOf("fix"))
    ))

    @Test fun phoneRemainsDefaultWithoutHubConfiguration() {
        val api = FakeApi()
        controller(api, MemoryStore(null)).use { hub ->
            var sends = 0
            assertTrue(hub.state.value.canSendFan(true))
            assertFalse(hub.state.value.canSendFan(false))
            assertTrue(hub.submit(HubCommand.FAN_HIGH) { sends++ })
            assertEquals(1, sends)
            assertTrue(api.commands.isEmpty())
            assertEquals(0, api.inspections)
        }
    }

    @Test fun inspectionEnablesHubWithoutPhoneIrAndDoesNotSend() {
        val api = FakeApi()
        controller(api).use { hub ->
            assertFalse(hub.state.value.canSendFan(false))
            assertFalse(hub.submit(HubCommand.FAN_HIGH) { fail("No fallback") })
            assertTrue(hub.checkHub())
            assertEquals(1, api.inspections)
            assertTrue(api.commands.isEmpty())
            assertTrue(hub.state.value.canSendFan(false))
            assertFalse(hub.state.value.toString().contains(config.apiKey))
            assertTrue(hub.submit(HubCommand.FAN_HIGH) { fail("No phone IR on Hub route") })
            assertEquals(listOf(HubCommand.FAN_HIGH), api.commands)
            assertTrue(hub.state.value.message.contains("Chưa xác minh"))
        }
    }

    @Test fun duplicatePressAndSettingsChangeAreRejectedWithoutQueue() = runBlocking {
        val api = FakeApi().apply { sendWait = CompletableDeferred() }
        controller(api).use { hub ->
            hub.checkHub()
            assertTrue(hub.submit(HubCommand.FAN_SWING) { fail("No fallback") })
            assertTrue(hub.state.value.busy)
            assertFalse(hub.submit(HubCommand.FAN_SWING) { fail("No fallback") })
            assertFalse(hub.submit(HubCommand.TOSHIBA_FIX) { fail("Shared gate") })
            assertFalse(hub.checkHub())
            assertFalse(hub.saveConfiguration("192.168.2.201", null, HubTransport.PHONE))
            api.sendWait!!.complete(Unit)
            yield()
            assertFalse(hub.state.value.busy)
            assertEquals(listOf(HubCommand.FAN_SWING), api.commands)
        }
    }

    @Test fun rejectedOrUncertainCommandsClearReadinessWithoutRetryOrFallback() {
        listOf(HubSendResult.Rejected(401, "unauthorized"),
            HubSendResult.Rejected(503, "device_not_ready"),
            HubSendResult.Uncertain(null, "timeout"),
            HubSendResult.Uncertain(500, "ir_send_failed")).forEach { result ->
            val api = FakeApi().apply { sendResult = result }
            controller(api).use { hub ->
                hub.checkHub()
                hub.submit(HubCommand.FAN_SWING) { fail("No fallback") }
                assertNull(hub.state.value.snapshot)
                assertEquals(1, api.commands.size)
                assertFalse(hub.submit(HubCommand.FAN_SWING) { fail("No fallback") })
                assertEquals(1, api.commands.size)
                if (result is HubSendResult.Uncertain) {
                    assertTrue(hub.state.value.message.contains("Chưa xác định"))
                }
            }
        }
    }

    @Test fun permissionRevocationBlocksInspectionAndPost() {
        var allowed = true
        val api = FakeApi()
        controller(api, allowed = { allowed }).use { hub ->
            hub.checkHub()
            allowed = false
            assertFalse(hub.submit(HubCommand.FAN_OFF) { fail("No fallback") })
            assertFalse(hub.checkHub())
            assertNull(hub.state.value.snapshot)
            assertTrue(api.commands.isEmpty())
            assertEquals(1, api.inspections)
        }
    }

    @Test fun savingPreservesBlankKeyAndRequiresFreshInspection() {
        val store = MemoryStore(HubSettings(config, HubTransport.HUB))
        val api = FakeApi()
        controller(api, store).use { hub ->
            hub.checkHub()
            assertTrue(hub.saveConfiguration("192.168.2.201", null, HubTransport.HUB))
            assertEquals(config.apiKey, store.value!!.config.apiKey)
            assertEquals("192.168.2.201", hub.state.value.address)
            assertNull(hub.state.value.snapshot)
            assertEquals(1, hub.state.value.settingsRevision)
            assertEquals(1, api.inspections) // save must not implicitly use the network
            assertTrue(api.commands.isEmpty())
        }
    }

    @Test fun failedSaveDoesNotReplaceSettingsOrExposeException() {
        val store = MemoryStore(HubSettings(config, HubTransport.HUB)).apply { failSave = true }
        val api = FakeApi()
        controller(api, store).use { hub ->
            hub.saveConfiguration("192.168.2.201", "n".repeat(64), HubTransport.HUB)
            assertEquals("192.168.2.200", hub.state.value.address)
            assertNotNull(hub.state.value.settingsError)
            assertFalse(hub.state.value.toString().contains(config.apiKey))
            hub.checkHub()
            assertEquals(config.endpoint, api.lastEndpoint)
        }
    }

    @Test fun invalidInputDoesNotWriteStorageOrSend() {
        val api = FakeApi()
        val store = MemoryStore(null)
        controller(api, store).use { hub ->
            assertFalse(hub.saveConfiguration("8.8.8.8", config.apiKey, HubTransport.HUB))
            assertFalse(hub.saveConfiguration(config.endpoint.ipv4, null, HubTransport.HUB))
            assertNull(store.value)
            assertEquals(0, api.inspections)
            assertTrue(api.commands.isEmpty())
        }
    }

    @Test fun cancellationDoesNotReplayAndCloseRejectsFurtherActions() = runBlocking {
        val api = FakeApi().apply { sendWait = CompletableDeferred() }
        val hub = controller(api)
        hub.checkHub()
        hub.submit(HubCommand.FAN_SWING) { fail("No fallback") }
        hub.cancelOperation()
        yield()
        assertFalse(hub.state.value.busy)
        assertNull(hub.state.value.snapshot)
        assertTrue(hub.state.value.message.contains("Chưa xác định"))
        assertEquals(1, api.commands.size)
        hub.close()
        assertFalse(hub.checkHub())
        assertFalse(hub.submit(HubCommand.FAN_SWING) {})
        assertEquals(1, api.commands.size)
    }

    private fun controller(
        api: FakeApi,
        store: MemoryStore = MemoryStore(HubSettings(config, HubTransport.HUB)),
        allowed: () -> Boolean = { true }
    ) = HubController(api, store, allowed, Dispatchers.Unconfined)

    private inner class FakeApi : HubApi {
        var inspections = 0
        var lastEndpoint: HubEndpoint? = null
        val commands = mutableListOf<HubCommand>()
        var sendWait: CompletableDeferred<Unit>? = null
        var sendResult: HubSendResult = HubSendResult.Sent
        override suspend fun inspect(endpoint: HubEndpoint): Result<HubSnapshot> {
            inspections++
            lastEndpoint = endpoint
            return Result.success(ready)
        }
        override suspend fun send(config: HubConfig, command: HubCommand): HubSendResult {
            commands += command
            sendWait?.await()
            return sendResult
        }
    }

    private inner class MemoryStore(var value: HubSettings?) : HubConfigStore {
        var failSave = false
        override suspend fun load() = value
        override suspend fun save(settings: HubSettings) {
            if (failSave) error(config.apiKey) // must never cross into public state
            value = settings
        }
    }
}
