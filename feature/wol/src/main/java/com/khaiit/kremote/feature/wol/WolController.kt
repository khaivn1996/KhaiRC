package com.khaiit.kremote.feature.wol

import com.khaiit.kremote.core.network.Ipv4Address
import com.khaiit.kremote.core.network.MacAddress
import com.khaiit.kremote.core.network.WakeOnLanSender

class WolController(
    private val wakeOnLanSender: WakeOnLanSender,
    private val settings: WolSettings
) {

    val config: WolConfig
        get() = settings.getConfig()

    fun saveConfig(
        macAddress: String,
        broadcastAddress: String
    ): Result<WolConfig> {

        return runCatching {

            val macCandidate =
                if (macAddress.isBlank()) {
                    WolSettings.DEFAULT_MAC_ADDRESS
                } else {
                    macAddress
                }

            val broadcastCandidate =
                if (broadcastAddress.isBlank()) {
                    WolSettings.DEFAULT_BROADCAST_ADDRESS
                } else {
                    broadcastAddress
                }

            val normalizedMac =
                MacAddress.normalize(
                    macCandidate
                )

            val normalizedBroadcast =
                broadcastCandidate.trim()

            require(
                MacAddress.isValid(
                    normalizedMac
                )
            ) {
                "MAC Address không hợp lệ"
            }

            require(
                Ipv4Address.isValid(
                    normalizedBroadcast
                )
            ) {
                "Broadcast Address không hợp lệ"
            }

            val newConfig =
                WolConfig(
                    macAddress =
                        normalizedMac,

                    broadcastAddress =
                        normalizedBroadcast
                )

            settings.saveConfig(
                newConfig
            )

            newConfig
        }
    }

    suspend fun wake(): Result<Unit> {

        return runCatching {

            val currentConfig =
                settings.getConfig()

            wakeOnLanSender.wake(
                macAddress =
                    currentConfig.macAddress,

                broadcastAddress =
                    currentConfig.broadcastAddress,

                port = 9
            )
        }
    }
}