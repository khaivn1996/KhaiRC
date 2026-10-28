package com.khaiit.kremote.feature.ac.toshiba

import com.khaiit.kremote.core.ir.IrTransmitter

/**
 * Independent of Daikin; only transmits the state combinations actually captured.
 * The persisted state is a local UI estimate: IR provides no feedback from the AC.
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

    /** Directly selects COOL, 24 C, using the selected fan speed. */
    fun chooseCool(): ToshibaAcState = sendFullState(state.copy(power = true))

    /** The captured 72-bit messages work for FAN AUTO and levels 1..5 in COOL 24 C. */
    fun setFanSpeed(speed: ToshibaFanSpeed): ToshibaAcState =
        sendFullState(state.copy(power = true, fanSpeed = speed))

    /** FIX is a single 56-bit step command; do not guess the physical louver angle. */
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
        check(state.power) { "Turn the air conditioner on before adjusting the louver" }
    }

    private fun transmit(command: ToshibaAcEncoder.EncodedCommand) {
        irTransmitter.transmit(command.frequencyHz, command.pattern)
    }
}
