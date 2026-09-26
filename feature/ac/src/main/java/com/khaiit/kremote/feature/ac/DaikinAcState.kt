package com.khaiit.kremote.feature.ac

data class DaikinAcState(
    val power: Boolean = false,
    val temperatureC: Int = 25,
    val mode: DaikinMode = DaikinMode.COOL,
    val fanSpeed: DaikinFanSpeed = DaikinFanSpeed.HIGH,
    val swing: Boolean = true
) {
    init {
        require(temperatureC in MIN_TEMPERATURE_C..MAX_TEMPERATURE_C) {
            "Temperature must be between $MIN_TEMPERATURE_C and $MAX_TEMPERATURE_C C"
        }
    }

    companion object {
        const val MIN_TEMPERATURE_C = 18
        const val MAX_TEMPERATURE_C = 30
    }
}

enum class DaikinMode {
    AUTO,
    COOL,
    DRY,
    FAN,
    HEAT;

    fun next(): DaikinMode = when (this) {
        AUTO -> COOL
        COOL -> DRY
        DRY -> FAN
        FAN -> HEAT
        HEAT -> AUTO
    }
}

enum class DaikinFanSpeed {
    LOW,
    HIGH;

    fun next(): DaikinFanSpeed = when (this) {
        LOW -> HIGH
        HIGH -> LOW
    }
}
