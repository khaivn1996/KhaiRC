package com.khaiit.kremote.feature.ac

import android.content.Context

class DaikinAcSettings(
    context: Context
) : DaikinAcStateStore {

    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    override fun load(): DaikinAcState {
        val defaultState = DaikinAcState()

        val mode = runCatching {
            DaikinMode.valueOf(
                preferences.getString(KEY_MODE, defaultState.mode.name)
                    ?: defaultState.mode.name
            )
        }.getOrDefault(defaultState.mode)

        val fanSpeed = runCatching {
            DaikinFanSpeed.valueOf(
                preferences.getString(KEY_FAN, defaultState.fanSpeed.name)
                    ?: defaultState.fanSpeed.name
            )
        }.getOrDefault(defaultState.fanSpeed)

        val temperature = preferences
            .getInt(KEY_TEMPERATURE, defaultState.temperatureC)
            .coerceIn(
                DaikinAcState.MIN_TEMPERATURE_C,
                DaikinAcState.MAX_TEMPERATURE_C
            )

        return DaikinAcState(
            power = preferences.getBoolean(KEY_POWER, defaultState.power),
            temperatureC = temperature,
            mode = mode,
            fanSpeed = fanSpeed,
            swing = preferences.getBoolean(KEY_SWING, defaultState.swing)
        )
    }

    override fun save(state: DaikinAcState) {
        preferences.edit()
            .putBoolean(KEY_POWER, state.power)
            .putInt(KEY_TEMPERATURE, state.temperatureC)
            .putString(KEY_MODE, state.mode.name)
            .putString(KEY_FAN, state.fanSpeed.name)
            .putBoolean(KEY_SWING, state.swing)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "daikin_ac_settings"
        private const val KEY_POWER = "power"
        private const val KEY_TEMPERATURE = "temperature_c"
        private const val KEY_MODE = "mode"
        private const val KEY_FAN = "fan_speed"
        private const val KEY_SWING = "swing"
    }
}
