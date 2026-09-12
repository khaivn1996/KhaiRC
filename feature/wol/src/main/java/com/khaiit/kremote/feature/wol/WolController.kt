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
        targetIp: String
    ): Result<WolConfig> {

        return runCatching {

            val macCandidate =
                if (macAddress.isBlank()) {
                    WolSettings.DEFAULT_MAC_ADDRESS
                } else {
                    macAddress
                }

            val targetIpCandidate =
                if (targetIp.isBlank()) {
                    WolSettings.DEFAULT_TARGET_IP
                } else {
                    targetIp
                }

            val normalizedMac =
                MacAddress.normalize(
                    macCandidate
                )

            val normalizedTargetIp =
                targetIpCandidate.trim()

            require(
                MacAddress.isValid(
                    normalizedMac
                )
            ) {
                "MAC Address không hợp lệ"
            }

            require(
                Ipv4Address.isValid(
                    normalizedTargetIp
                )
            ) {
                "Target IP không hợp lệ"
            }

            val newConfig =
                WolConfig(
                    macAddress =
                        normalizedMac,

                    targetIp =
                        normalizedTargetIp
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

                targetAddress =
                    currentConfig.targetIp,

                port = 9
            )
        }
    }
}