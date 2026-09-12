package com.khaiit.kremote.feature.wol

import android.content.Context

class WolSettings(
    context: Context
) {

    private val preferences =
        context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

    fun getConfig(): WolConfig {

        val macAddress =
            preferences.getString(
                KEY_MAC_ADDRESS,
                null
            )
                ?: DEFAULT_MAC_ADDRESS

        val targetIp =
            preferences.getString(
                KEY_TARGET_IP,
                null
            )
                ?: DEFAULT_TARGET_IP

        return WolConfig(
            macAddress = macAddress,
            targetIp = targetIp
        )
    }

    fun saveConfig(
        config: WolConfig
    ) {

        preferences
            .edit()
            .putString(
                KEY_MAC_ADDRESS,
                config.macAddress
            )
            .putString(
                KEY_TARGET_IP,
                config.targetIp
            )
            .apply()
    }

    companion object {

        const val DEFAULT_MAC_ADDRESS =
            "D8:BB:C1:DC:2E:40"

        const val DEFAULT_TARGET_IP =
            "192.168.2.19"

        private const val PREFS_NAME =
            "wol_settings"

        private const val KEY_MAC_ADDRESS =
            "mac_address"

        private const val KEY_TARGET_IP =
            "target_ip"
    }
}