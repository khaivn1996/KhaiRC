package com.khaiit.kremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import com.khaiit.kremote.core.ir.AndroidIrTransmitter
import com.khaiit.kremote.core.network.AndroidWakeOnLanSender
import com.khaiit.kremote.feature.fan.FanController
import com.khaiit.kremote.feature.wol.WolController
import com.khaiit.kremote.feature.wol.WolSettings
import com.khaiit.kremote.ui.theme.KhaiRCTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*
         * Core implementations
         */
        val irTransmitter =
            AndroidIrTransmitter(
                applicationContext
            )

        val wakeOnLanSender = AndroidWakeOnLanSender()

        /*
         * Persistent settings
         */
        val wolSettings =
            WolSettings(
                applicationContext
            )

        setContent {

            KhaiRCTheme {

                /*
                 * Feature controllers
                 */
                val fanController = remember {
                    FanController(
                        irTransmitter
                    )
                }

                val wolController = remember {
                    WolController(
                        wakeOnLanSender = wakeOnLanSender,
                        settings = wolSettings
                    )
                }

                /*
                 * Dashboard
                 */
                HomeScreen(
                    irAvailable =
                        fanController.isIrAvailable,

                    onPowerHigh =
                        fanController::powerHigh,

                    onSwing =
                        fanController::swing,

                    onFanOff =
                        fanController::off,

                    wolController =
                        wolController
                )
            }
        }
    }
}