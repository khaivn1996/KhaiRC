package com.khaiit.kremote.feature.ac.toshiba

import com.khaiit.kremote.core.ir.IrTransmitter

/**
 * Independent of Daikin. The saved state is a local UI estimate:
 * infrared provides no feedback about changes made with other remotes.
 */
class ToshibaAcController(
    private val irTransmitter: IrTransmitter,
    private val stateStore: ToshibaAcStateStore,
    private val encoder: ToshibaAcEncoder = ToshibaAcEncoder()
) {
    var state: ToshibaAcState = stateStore.load()
        private set

    val isIrAvailable: Boolean
        get() = irTransmitter.isAvailable

    fun togglePower(): ToshibaAcState = if (state.power) powerOff() else powerOn()

    fun powerOn(): ToshibaAcState = sendFullState(state.copy(power = true))

    fun powerOff(): ToshibaAcState = sendFullState(state.copy(power = false))

    /** Selects COOL at the currently selected temperature and fan speed. */
    fun chooseCool(): ToshibaAcState = sendFullState(state.copy(power = true))

    fun setFanSpeed(speed: ToshibaFanSpeed): ToshibaAcState =
        sendFullState(state.copy(power = true, fanSpeed = speed))

    /** Adjust temperature by 1 C without resetting fan speed or swing state. */
    fun increaseTemperature(): ToshibaAcState = setTemperature(
        (state.temperatureC + 1).coerceAtMost(ToshibaAcState.MAX_TEMPERATURE_C)
    )

    fun decreaseTemperature(): ToshibaAcState = setTemperature(
        (state.temperatureC - 1).coerceAtLeast(ToshibaAcState.MIN_TEMPERATURE_C)
    )

    fun setTemperature(temperatureC: Int): ToshibaAcState {
        requirePowerOn()
        require(temperatureC in ToshibaAcState.MIN_TEMPERATURE_C..ToshibaAcState.MAX_TEMPERATURE_C) {
            "Toshiba temperature must be between 17 and 30 C"
        }
        if (temperatureC == state.temperatureC) return state
        return sendFullState(state.copy(temperatureC = temperatureC))
    }

    /** FIX is a single 56-bit step command; louver position isn't tracked. */
    fun fix(): ToshibaAcState {
        requirePowerOn()
        transmit(encoder.encodeFix())
        return state
    }

    fun toggleSwing(): ToshibaAcState = setSwing(!state.swing)

    fun setSwing(enabled: Boolean): ToshibaAcState {
        requirePowerOn()
        transmit(if (enabled) encoder.encodeSwingOn() else encoder.encodeSwingOff())
        val updated = state.copy(swing = enabled)
        state = updated
        stateStore.save(updated)
        return updated
    }

    fun resendCurrentState(): ToshibaAcState {
        transmit(encoder.encodePowerOrCool(state))
        return state
    }

    private fun sendFullState(next: ToshibaAcState): ToshibaAcState {
        transmit(encoder.encodePowerOrCool(next))
        state = next
        stateStore.save(next)
        return next
    }

    private fun requirePowerOn() {
        check(state.power) { "Turn the air conditioner on before adjusting the temperature or louver" }
    }

    private fun transmit(command: ToshibaAcEncoder.EncodedCommand) {
        irTransmitter.transmit(command.frequencyHz, command.pattern)
    }
}
