package com.khaiit.kremote.feature.ac.toshiba

import android.content.Context

class ToshibaAcSettings(context: Context) : ToshibaAcStateStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    override fun load(): ToshibaAcState {
        val defaults = ToshibaAcState()
        val storedFan = preferences.getString(KEY_FAN, defaults.fanSpeed.name)
        val fan = runCatching {
            ToshibaFanSpeed.valueOf(storedFan ?: defaults.fanSpeed.name)
        }.getOrDefault(defaults.fanSpeed)
        return ToshibaAcState(
            power = preferences.getBoolean(KEY_POWER, defaults.power),
            fanSpeed = fan,
            swing = preferences.getBoolean(KEY_SWING, defaults.swing)
        )
    }

    override fun save(state: ToshibaAcState) {
        preferences.edit()
            .putBoolean(KEY_POWER, state.power)
            .putString(KEY_FAN, state.fanSpeed.name)
            .putBoolean(KEY_SWING, state.swing)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "toshiba_ac_settings"
        const val KEY_POWER = "power"
        const val KEY_FAN = "fan_speed"
        const val KEY_SWING = "swing"
    }
}
