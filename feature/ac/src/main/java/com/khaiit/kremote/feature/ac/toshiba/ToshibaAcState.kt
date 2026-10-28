package com.khaiit.kremote.feature.ac.toshiba

/**
 * Toshiba IR state for COOL. The temperature is transmitted in the 72-bit
 * command along with the selected fan speed.
 *
 * The displayed state is only a local estimate; the air conditioner does not
 * report changes made with the original remote back to KhaiRC.
 */
data class ToshibaAcState(
    val power: Boolean = false,
    val fanSpeed: ToshibaFanSpeed = ToshibaFanSpeed.AUTO,
    val swing: Boolean = false,
    val temperatureC: Int = DEFAULT_TEMPERATURE_C
) {
    init {
        require(temperatureC in MIN_TEMPERATURE_C..MAX_TEMPERATURE_C) {
            "Toshiba temperature must be between $MIN_TEMPERATURE_C and $MAX_TEMPERATURE_C C"
        }
    }

    companion object {
        const val MIN_TEMPERATURE_C = 17
        const val MAX_TEMPERATURE_C = 30
        const val DEFAULT_TEMPERATURE_C = 24
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
