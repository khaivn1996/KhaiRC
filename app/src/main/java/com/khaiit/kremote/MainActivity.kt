package com.khaiit.kremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import com.khaiit.kremote.core.ir.AndroidIrTransmitter
import com.khaiit.kremote.core.network.AndroidWakeOnLanSender
import com.khaiit.kremote.feature.ac.daikin.DaikinAcController
import com.khaiit.kremote.feature.ac.daikin.DaikinAcSettings
import com.khaiit.kremote.feature.ac.toshiba.ToshibaAcController
import com.khaiit.kremote.feature.ac.toshiba.ToshibaAcSettings
import com.khaiit.kremote.feature.fan.FanController
import com.khaiit.kremote.feature.wol.WolController
import com.khaiit.kremote.feature.wol.WolSettings
import com.khaiit.kremote.feature.tuya.TuyaOc09Controller
import com.khaiit.kremote.feature.tuya.TuyaCredentialStore
import com.khaiit.kremote.feature.tuya.TuyaCloudOc09Repository
import com.khaiit.kremote.feature.xiaomi.AndroidXiaomiPlugConfigStore
import com.khaiit.kremote.feature.xiaomi.XiaomiLanPlugRepository
import com.khaiit.kremote.feature.xiaomi.XiaomiPlugController
import com.khaiit.kremote.ui.theme.KhaiRCTheme

class MainActivity : ComponentActivity() {
    private lateinit var tuyaOc09Controller: TuyaOc09Controller
    private lateinit var xiaomiPlugController: XiaomiPlugController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* Core implementations */
        val irTransmitter = AndroidIrTransmitter(applicationContext)
        val wakeOnLanSender = AndroidWakeOnLanSender()

        /* Persistent settings */
        val wolSettings = WolSettings(applicationContext)
        val daikinAcSettings = DaikinAcSettings(applicationContext)
        val toshibaAcSettings = ToshibaAcSettings(applicationContext)
        tuyaOc09Controller = TuyaOc09Controller(
            TuyaCredentialStore(applicationContext),
            TuyaCloudOc09Repository()
        )

        xiaomiPlugController = XiaomiPlugController(
            AndroidXiaomiPlugConfigStore(applicationContext),
            XiaomiLanPlugRepository()
        )

        setContent {
            KhaiRCTheme {
                /* Feature controllers */
                val fanController = remember { FanController(irTransmitter) }
                val wolController = remember {
                    WolController(
                        wakeOnLanSender = wakeOnLanSender,
                        settings = wolSettings
                    )
                }
                val daikinAcController = remember {
                    DaikinAcController(
                        irTransmitter = irTransmitter,
                        stateStore = daikinAcSettings
                    )
                }
                val toshibaAcController = remember {
                    ToshibaAcController(
                        irTransmitter = irTransmitter,
                        stateStore = toshibaAcSettings
                    )
                }

                HomeScreen(
                    irAvailable = fanController.isIrAvailable,
                    onPowerHigh = fanController::powerHigh,
                    onSwing = fanController::swing,
                    onFanOff = fanController::off,
                    wolController = wolController,
                    daikinAcController = daikinAcController,
                    toshibaAcController = toshibaAcController,
                    tuyaOc09Controller = tuyaOc09Controller,
                    xiaomiPlugController = xiaomiPlugController
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { tuyaOc09Controller.refresh() }
        lifecycleScope.launch { xiaomiPlugController.refresh() }
    }
}
