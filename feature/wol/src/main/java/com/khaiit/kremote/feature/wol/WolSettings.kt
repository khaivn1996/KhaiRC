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

        val broadcastAddress =
            preferences.getString(
                KEY_BROADCAST_ADDRESS,
                null
            )
                ?: DEFAULT_BROADCAST_ADDRESS

        return WolConfig(
            macAddress = macAddress,
            broadcastAddress = broadcastAddress
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
                KEY_BROADCAST_ADDRESS,
                config.broadcastAddress
            )
            .apply()
    }

    companion object {

        const val DEFAULT_MAC_ADDRESS =
            "D8:BB:C1:DC:2E:40"

        const val DEFAULT_BROADCAST_ADDRESS =
            "192.168.2.255"

        private const val PREFS_NAME =
            "wol_settings"

        private const val KEY_MAC_ADDRESS =
            "mac_address"

        private const val KEY_BROADCAST_ADDRESS =
            "broadcast_address"
    }
}