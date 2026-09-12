package com.khaiit.kremote.core.ir

interface IrTransmitter {
    val isAvailable: Boolean

    fun transmit(
        frequency: Int,
        pattern: IntArray
    )
}