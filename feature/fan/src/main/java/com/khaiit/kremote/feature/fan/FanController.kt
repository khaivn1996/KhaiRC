package com.khaiit.kremote.feature.fan

import com.khaiit.kremote.core.ir.IrTransmitter

class FanController(
    private val irTransmitter: IrTransmitter
) {

    val isIrAvailable: Boolean
        get() = irTransmitter.isAvailable

    fun powerHigh() {
        irTransmitter.transmit(
            frequency = FanIrCodes.FREQUENCY,
            pattern = FanIrCodes.POWER_HIGH
        )
    }

    fun swing() {
        irTransmitter.transmit(
            frequency = FanIrCodes.FREQUENCY,
            pattern = FanIrCodes.SWING
        )
    }

    fun off() {
        irTransmitter.transmit(
            frequency = FanIrCodes.FREQUENCY,
            pattern = FanIrCodes.OFF
        )
    }
}