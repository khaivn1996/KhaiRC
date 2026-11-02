package com.khaiit.kremote.feature.xiaomi

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class XiaomiPlugControllerTest {
    private val config = XiaomiPlugConfig("192.168.2.103", "00112233445566778899aabbccddeeff")

    private class FakeStore(private var config: XiaomiPlugConfig?) : XiaomiPlugConfigStore {
        override fun load(): XiaomiPlugConfig? = config
        override fun save(config: XiaomiPlugConfig) { this.config = config }
    }

    private class FakeRepo : XiaomiPlugRepository {
        var readCount = 0
        var writeCount = 0
        var value = true
        var failWrite = false
        override suspend fun readPower(config: XiaomiPlugConfig): Boolean {
            readCount++
            return value
        }
        override suspend fun setPower(config: XiaomiPlugConfig, on: Boolean) {
            writeCount++
            if (failWrite) throw IOException("VPN lost")
            value = on
        }
    }

    @Test fun constructorNeverWrites() {
        val repo = FakeRepo()
        val controller = XiaomiPlugController(FakeStore(config), repo)
        assertTrue(controller.state.configured)
        assertNull(controller.state.power)
        assertEquals(0, repo.readCount)
        assertEquals(0, repo.writeCount)
    }

    @Test fun refreshIsReadOnly() = runBlocking {
        val repo = FakeRepo()
        val controller = XiaomiPlugController(FakeStore(config), repo)
        controller.refresh()
        assertEquals(true, controller.state.power)
        assertEquals(1, repo.readCount)
        assertEquals(0, repo.writeCount)
    }

    @Test fun writeOnceAndVerifyByReading() = runBlocking {
        val repo = FakeRepo()
        val controller = XiaomiPlugController(FakeStore(config), repo)
        controller.refresh()
        controller.setPower(false)
        assertFalse(controller.state.power!!)
        assertEquals(1, repo.writeCount)
        assertEquals(2, repo.readCount)
    }

    @Test fun failedWriteLeavesPowerUnknownAndNeverRetries() = runBlocking {
        val repo = FakeRepo().apply { failWrite = true }
        val controller = XiaomiPlugController(FakeStore(config), repo)
        controller.refresh()
        controller.setPower(false)
        assertNull(controller.state.power)
        assertEquals(1, repo.writeCount)
    }

    @Test fun refusesWriteWhenPowerUnknown() = runBlocking {
        val repo = FakeRepo()
        val controller = XiaomiPlugController(FakeStore(config), repo)
        controller.setPower(false)
        assertEquals(0, repo.writeCount)
    }
}
