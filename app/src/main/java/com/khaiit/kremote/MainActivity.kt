package com.khaiit.kremote

import com.khaiit.kremote.core.network.hasLocalNetworkPermission
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.os.Bundle
import android.view.WindowManager
import androidx.compose.runtime.SideEffect
import com.khaiit.kremote.core.vault.FirestoreVaultRemote
import com.khaiit.kremote.core.vault.GoogleVaultAuth
import com.khaiit.kremote.core.vault.VaultKeyStore
import com.khaiit.kremote.core.vault.VaultStateStore
import com.khaiit.kremote.vault.LocalVaultSources
import com.khaiit.kremote.vault.VaultAutoPush
import com.khaiit.kremote.vault.VaultController
import com.khaiit.kremote.vault.VaultGateScreen
import com.khaiit.kremote.vault.VaultUiState
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import com.khaiit.kremote.core.ir.AndroidIrTransmitter
import com.khaiit.kremote.core.network.AndroidWakeOnLanSender
import com.khaiit.kremote.core.network.HubHttpsMilifeClient
import com.khaiit.kremote.core.network.HubHttpsToshibaControlsClient
import com.khaiit.kremote.feature.ac.daikin.DaikinAcController
import com.khaiit.kremote.feature.ac.daikin.DaikinAcSettings
import com.khaiit.kremote.feature.ac.toshiba.ToshibaAcSettings
import com.khaiit.kremote.feature.fan.FanController
import com.khaiit.kremote.feature.wol.WolController
import com.khaiit.kremote.feature.wol.WolSettings
import com.khaiit.kremote.feature.tuya.TuyaOc09Controller
import com.khaiit.kremote.feature.tuya.TuyaCredentialStore
import com.khaiit.kremote.feature.tuya.TuyaCloudOc09Repository
import com.khaiit.kremote.core.ui.theme.KhaiRCTheme

class MainActivity : ComponentActivity() {
    private lateinit var vaultController: VaultController
    private lateinit var vaultAutoPush: VaultAutoPush
    private lateinit var tuyaOc09Controller: TuyaOc09Controller
    private lateinit var milifeController: MilifeController
    private lateinit var toshibaHttpsControlsController: ToshibaHttpsControlsController

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
        val networkAllowed: () -> Boolean = { hasLocalNetworkPermission() }
        milifeController = MilifeController(
            api = HubHttpsMilifeClient { HttpsProbePreferences(applicationContext).read() },
            store = HubSettingsStore(applicationContext),
            networkAllowed = networkAllowed
        )

        // Independent of the MiLife card.
        toshibaHttpsControlsController = ToshibaHttpsControlsController(
            api = HubHttpsToshibaControlsClient(),
            hubStore = HubSettingsStore(applicationContext),
            stateStore = toshibaAcSettings,
            endpointProvider = { HttpsProbePreferences(applicationContext).read() },
            networkAllowed = networkAllowed
        )

        vaultController = VaultController(
            sources = LocalVaultSources(applicationContext),
            auth = GoogleVaultAuth(getString(R.string.default_web_client_id)),
            remote = FirestoreVaultRemote(),
            keys = VaultKeyStore(applicationContext),
            markers = VaultStateStore(applicationContext),
            onRestored = { recreate() }
        )
        vaultAutoPush = VaultAutoPush(applicationContext, vaultController)
        // Secure from the first frame only when the gate (secret inputs) is shown.
        if (vaultController.state != VaultUiState.Ready) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false

        setContent {
            KhaiRCTheme {
                val vaultReady = vaultController.state == VaultUiState.Ready
                SideEffect {
                    if (vaultReady) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                if (!vaultReady) {
                    VaultGateScreen(vaultController)
                    return@KhaiRCTheme
                }
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
                HomeScreen(
                    irAvailable = fanController.isIrAvailable,
                    onPowerHigh = fanController::powerHigh,
                    onSwing = fanController::swing,
                    onFanOff = fanController::off,
                    wolController = wolController,
                    daikinAcController = daikinAcController,
                    toshibaHttpsControlsController = toshibaHttpsControlsController,
                    tuyaOc09Controller = tuyaOc09Controller,
                    milifeController = milifeController,
                    vaultController = vaultController
                )
            }
        }
    }

    override fun onDestroy() {
        vaultAutoPush.close()
        vaultController.close()
        milifeController.close()
        toshibaHttpsControlsController.close()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        vaultController.start()
        lifecycleScope.launch { tuyaOc09Controller.refresh() }
        milifeController.refresh()
    }
}
