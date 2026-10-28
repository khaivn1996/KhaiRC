package com.khaiit.kremote.feature.ac.toshiba

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
        assertEquals(24, controller.state.temperatureC)
        assertEquals(296, ir.lastPattern.size)

        controller.setFanSpeed(ToshibaFanSpeed.LEVEL_5)
        assertEquals(ToshibaFanSpeed.LEVEL_5, controller.state.fanSpeed)
        assertEquals(2, store.saves)
        assertEquals(2, ir.calls)
    }

    @Test
    fun temperatureButtonsTransmitOnceAndKeepFanSwingAndPower() {
        val ir = FakeIr()
        val store = FakeStore(ToshibaAcState(
            power = true,
            fanSpeed = ToshibaFanSpeed.LEVEL_3,
            swing = true
        ))
        val controller = ToshibaAcController(ir, store)

        controller.increaseTemperature()
        assertEquals(25, controller.state.temperatureC)
        assertEquals(ToshibaFanSpeed.LEVEL_3, controller.state.fanSpeed)
        assertTrue(controller.state.swing)
        assertTrue(controller.state.power)
        assertEquals(1, ir.calls)
        assertEquals(1, store.saves)

        controller.decreaseTemperature()
        assertEquals(24, controller.state.temperatureC)
        assertEquals(2, ir.calls)
        assertEquals(2, store.saves)
    }

    @Test
    fun temperatureLimitsDoNotTransmitOrSave() {
        val ir = FakeIr()
        val store = FakeStore(ToshibaAcState(power = true, temperatureC = 17))
        val controller = ToshibaAcController(ir, store)
        controller.decreaseTemperature()
        assertEquals(17, controller.state.temperatureC)
        assertEquals(0, ir.calls)
        assertEquals(0, store.saves)

        controller.setTemperature(30)
        assertEquals(30, controller.state.temperatureC)
        controller.increaseTemperature()
        assertEquals(1, ir.calls)
        assertEquals(1, store.saves)
    }

    @Test
    fun temperatureCannotBeAdjustedWhenOff() {
        val ir = FakeIr()
        val store = FakeStore()
        val controller = ToshibaAcController(ir, store)
        if (runCatching { controller.increaseTemperature() }.isSuccess) {
            throw AssertionError("Expected power-off guard")
        }
        assertEquals(24, controller.state.temperatureC)
        assertEquals(0, ir.calls)
        assertEquals(0, store.saves)
    }

    @Test
    fun choosingCoolRetainsPreviouslySelectedTemperature() {
        val ir = FakeIr()
        val store = FakeStore(ToshibaAcState(temperatureC = 26))
        val controller = ToshibaAcController(ir, store)
        controller.chooseCool()
        assertTrue(controller.state.power)
        assertEquals(26, controller.state.temperatureC)
        assertEquals(1, ir.calls)
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
        val store = FakeStore(ToshibaAcState(power = true))
        val ir = FakeIr(fail = true)
        val controller = ToshibaAcController(ir, store)
        if (runCatching { controller.increaseTemperature() }.isSuccess) {
            throw AssertionError("Expected IR transmit to fail")
        }
        assertEquals(24, controller.state.temperatureC)
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
