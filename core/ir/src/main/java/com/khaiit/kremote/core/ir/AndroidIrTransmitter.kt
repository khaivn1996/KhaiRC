package com.khaiit.kremote.core.ir

import android.content.Context
import android.hardware.ConsumerIrManager

class AndroidIrTransmitter(
    context: Context
) : IrTransmitter {

    private val irManager: ConsumerIrManager? =
        context.applicationContext.getSystemService(
            Context.CONSUMER_IR_SERVICE
        ) as? ConsumerIrManager

    override val isAvailable: Boolean
        get() = irManager?.hasIrEmitter() == true

    override fun transmit(
        frequency: Int,
        pattern: IntArray
    ) {
        require(frequency > 0) {
            "IR frequency must be greater than 0"
        }

        require(pattern.isNotEmpty()) {
            "IR pattern must not be empty"
        }

        val manager = checkNotNull(irManager) {
            "Consumer IR service is unavailable"
        }

        check(manager.hasIrEmitter()) {
            "This device does not have an IR emitter"
        }

        manager.transmit(
            frequency,
            pattern
        )
    }
}