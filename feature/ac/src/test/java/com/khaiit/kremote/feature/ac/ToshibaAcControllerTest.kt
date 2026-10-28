package com.khaiit.kremote.feature.ac

import com.khaiit.kremote.core.ir.IrTransmitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToshibaAcControllerTest {
    @Test
    fun choosingCoolAndFanTransmitsAndPersistsState() {
        val ir = FakeIr()
        val store = FakeStore()
        val controller = ToshibaAcController(ir, store)

        controller.chooseCool()
        assertTrue(controller.state.power)
        assertEquals(ToshibaFanSpeed.AUTO, controller.state.fanSpeed)
        assertEquals(296, ir.lastPattern.size)

        controller.setFanSpeed(ToshibaFanSpeed.LEVEL_5)
        assertEquals(ToshibaFanSpeed.LEVEL_5, controller.state.fanSpeed)
        assertEquals(2, store.saves)
        assertEquals(2, ir.calls)
    }

    @Test
    fun fixDoesNotMutateStateAndSwingUsesShortFrames() {
        val ir = FakeIr()
        val store = FakeStore(ToshibaAcState(power = true))
        val controller = ToshibaAcController(ir, store)

        controller.fix()
        assertEquals(116, ir.lastPattern.size)
        assertEquals(0, store.saves)

        controller.setSwing(true)
        assertTrue(controller.state.swing)
        assertEquals(232, ir.lastPattern.size)

        controller.setSwing(false)
        assertFalse(controller.state.swing)
        assertEquals(232, ir.lastPattern.size)
        assertEquals(2, store.saves)
    }

    @Test
    fun failedTransmissionDoesNotPersistFictitiousState() {
        val store = FakeStore()
        val ir = FakeIr(fail = true)
        val controller = ToshibaAcController(ir, store)
        runCatching { controller.chooseCool() }
            .onSuccess { throw AssertionError("Expected IR transmit to fail") }
        assertFalse(controller.state.power)
        assertEquals(0, store.saves)
    }

    private class FakeIr(private val fail: Boolean = false) : IrTransmitter {
        override val isAvailable = true
        var calls = 0
        var lastPattern = intArrayOf()
        override fun transmit(frequency: Int, pattern: IntArray) {
            if (fail) error("Emitter failed")
            calls++
            lastPattern = pattern
        }
    }

    private class FakeStore(initial: ToshibaAcState = ToshibaAcState()) : ToshibaAcStateStore {
        var stored = initial
        var saves = 0
        override fun load(): ToshibaAcState = stored
        override fun save(state: ToshibaAcState) {
            stored = state
            saves++
        }
    }
}
