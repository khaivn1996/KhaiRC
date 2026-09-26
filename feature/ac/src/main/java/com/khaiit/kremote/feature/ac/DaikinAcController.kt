package com.khaiit.kremote.feature.ac

import com.khaiit.kremote.core.ir.IrTransmitter

class DaikinAcController(
    private val irTransmitter: IrTransmitter,
    private val stateStore: DaikinAcStateStore,
    private val encoder: DaikinBrcEncoder = DaikinBrcEncoder()
) {

    var state: DaikinAcState = stateStore.load()
        private set

    val isIrAvailable: Boolean
        get() = irTransmitter.isAvailable

    val protocolName: String
        get() = encoder.encode(state).profileName

    fun togglePower(): DaikinAcState = updateAndTransmit {
        copy(power = !power)
    }

    fun increaseTemperature(): DaikinAcState = updateAndTransmit {
        copy(
            temperatureC =
                (temperatureC + 1).coerceAtMost(
                    DaikinAcState.MAX_TEMPERATURE_C
                )
        )
    }

    fun decreaseTemperature(): DaikinAcState = updateAndTransmit {
        copy(
            temperatureC =
                (temperatureC - 1).coerceAtLeast(
                    DaikinAcState.MIN_TEMPERATURE_C
                )
        )
    }

    fun cycleFanSpeed(): DaikinAcState = updateAndTransmit {
        copy(fanSpeed = fanSpeed.next())
    }

    fun toggleSwing(): DaikinAcState = updateAndTransmit {
        copy(swing = !swing)
    }

    fun setMode(mode: DaikinMode): DaikinAcState = updateAndTransmit(modeButtonPressed = true) {
            copy(mode = mode)
    }

    fun cycleMode(): DaikinAcState {
        return setMode(
            state.mode.next()
        )
    }

    fun resendCurrentState(): DaikinAcState {
        transmit(state)
        return state
    }

    private fun updateAndTransmit(
        modeButtonPressed: Boolean = false,
        transform: DaikinAcState.() -> DaikinAcState
    ): DaikinAcState {
        val nextState = state.transform()

        transmit(
            state = nextState,
            modeButtonPressed = modeButtonPressed
        )

        state = nextState
        stateStore.save(nextState)

        return nextState
    }

    private fun transmit(
        state: DaikinAcState,
        modeButtonPressed: Boolean = false
    ) {
        val command = encoder.encode(
            state = state,
            modeButtonPressed = modeButtonPressed
        )

        irTransmitter.transmit(
            frequency = command.frequencyHz,
            pattern = command.pattern
        )
    }
}
