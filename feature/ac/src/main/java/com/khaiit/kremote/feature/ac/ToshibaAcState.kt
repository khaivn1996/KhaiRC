package com.khaiit.kremote.feature.ac

/**
 * The current IR samples establish COOL at 24 C only. Temperature is intentionally
 * fixed until captures from other temperatures are independently validated.
 */
data class ToshibaAcState(
    val power: Boolean = false,
    val fanSpeed: ToshibaFanSpeed = ToshibaFanSpeed.AUTO,
    val swing: Boolean = false
) {
    companion object {
        const val TEMPERATURE_C: Int = 24
    }
}

enum class ToshibaFanSpeed(val label: String, val protocolByte: Int) {
    AUTO("AUTO", 0x80),
    LEVEL_1("1", 0x82),
    LEVEL_2("2", 0x86),
    LEVEL_3("3", 0x81),
    LEVEL_4("4", 0x85),
    LEVEL_5("5", 0x83)
}

interface ToshibaAcStateStore {
    fun load(): ToshibaAcState
    fun save(state: ToshibaAcState)
}
