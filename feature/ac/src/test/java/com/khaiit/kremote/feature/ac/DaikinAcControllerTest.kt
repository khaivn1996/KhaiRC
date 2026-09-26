package com.khaiit.kremote.feature.ac

import com.khaiit.kremote.core.ir.IrTransmitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DaikinAcControllerTest {

    @Test
    fun requestedSequence_updatesStateAndTransmitsEveryStep() {
        val transmitter = FakeIrTransmitter()

        val store = InMemoryStateStore(
            DaikinAcState(
                power = true,
                temperatureC = 25,
                mode = DaikinMode.COOL,
                fanSpeed = DaikinFanSpeed.HIGH,
                swing = true
            )
        )

        val controller = DaikinAcController(
            irTransmitter = transmitter,
            stateStore = store,
            encoder = DaikinBrcEncoder(
                DaikinBrcProfile.BRC4C153
            )
        )

        // 25 HIGH Swing ON
        // -> TEMP+
        var state = controller.increaseTemperature()

        assertEquals(
            26,
            state.temperatureC
        )
        assertEquals(
            DaikinFanSpeed.HIGH,
            state.fanSpeed
        )
        assertTrue(state.swing)

        // 26 HIGH Swing ON
        // -> SWING
        state = controller.toggleSwing()

        assertEquals(
            26,
            state.temperatureC
        )
        assertEquals(
            DaikinFanSpeed.HIGH,
            state.fanSpeed
        )
        assertFalse(state.swing)

        // 26 HIGH Swing OFF
        // -> SPEED
        //
        // BRC4C153 chỉ có:
        // HIGH <-> LOW
        state = controller.cycleFanSpeed()

        assertEquals(
            26,
            state.temperatureC
        )
        assertEquals(
            DaikinFanSpeed.LOW,
            state.fanSpeed
        )
        assertFalse(state.swing)

        assertEquals(
            3,
            transmitter.transmitCount
        )

        assertEquals(
            38_000,
            transmitter.lastFrequency
        )

        assertEquals(
            state,
            store.load()
        )
    }

    private class InMemoryStateStore(
        initial: DaikinAcState
    ) : DaikinAcStateStore {

        private var state = initial

        override fun load(): DaikinAcState = state

        override fun save(state: DaikinAcState) {
            this.state = state
        }
    }

    private class FakeIrTransmitter : IrTransmitter {
        override val isAvailable: Boolean = true

        var transmitCount: Int = 0
        var lastFrequency: Int = 0
        var lastPattern: IntArray = intArrayOf()

        override fun transmit(
            frequency: Int,
            pattern: IntArray
        ) {
            transmitCount += 1
            lastFrequency = frequency
            lastPattern = pattern.copyOf()
        }
    }

    @Test
    fun cycleMode_movesToNextMode() {
        val transmitter = FakeIrTransmitter()

        val store = InMemoryStateStore(
            DaikinAcState(
                power = true,
                temperatureC = 25,
                mode = DaikinMode.COOL,
                fanSpeed = DaikinFanSpeed.HIGH,
                swing = true
            )
        )

        val controller = DaikinAcController(
            irTransmitter = transmitter,
            stateStore = store,
            encoder = DaikinBrcEncoder(
                DaikinBrcProfile.BRC4C153
            )
        )

        val state = controller.cycleMode()

        assertEquals(
            DaikinMode.DRY,
            state.mode
        )

        assertEquals(
            state,
            store.load()
        )

        assertEquals(
            1,
            transmitter.transmitCount
        )
    }
}
