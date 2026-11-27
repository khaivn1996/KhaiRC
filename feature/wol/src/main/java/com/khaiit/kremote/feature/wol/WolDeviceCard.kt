package com.khaiit.kremote.feature.wol

import androidx.compose.ui.res.stringResource
import com.khaiit.kremote.core.ui.text.asString
import com.khaiit.kremote.core.ui.text.UiText
import com.khaiit.kremote.core.ui.text.uiText
import com.khaiit.kremote.core.ui.components.DeviceCard
import com.khaiit.kremote.core.ui.components.PowerIconButton
import com.khaiit.kremote.core.ui.components.PowerTone
import com.khaiit.kremote.core.ui.theme.KhaiRCDimens
import com.khaiit.kremote.core.network.LOCAL_NETWORK_PERMISSION
import com.khaiit.kremote.core.network.hasLocalNetworkPermission
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable

@Composable
fun WolDeviceCard(
    controller: WolController,
    modifier: Modifier = Modifier
) {

    val context =
        LocalContext.current

    val coroutineScope =
        rememberCoroutineScope()

    var config by remember {
        mutableStateOf(
            controller.config
        )
    }

    var showConfigDialog by remember {
        mutableStateOf(false)
    }

    var macInput by remember {
        mutableStateOf(
            config.macAddress
        )
    }

    var targetIpInput by remember {
        mutableStateOf(
            config.targetIp
        )
    }

    var macError by remember {
        mutableStateOf<UiText?>(null)
    }

    var targetIpError by remember {
        mutableStateOf<UiText?>(null)
    }

    var statusText by remember {
        mutableStateOf<UiText?>(null)
    }

    /*
     * Gửi Wake-on-LAN.
     */
    val sendWakePacket: () -> Unit = {

        coroutineScope.launch {

            statusText =
                uiText(R.string.wol_sending)

            controller
                .wake()
                .onSuccess {

                    statusText =
                        uiText(R.string.wol_wake_sent, config.targetIp)
                }
                .onFailure { error ->

                    statusText =
                        error.message?.let(UiText::Raw) ?: uiText(R.string.wol_send_failed)
                }
        }
    }

    /*
     * Android 17 / API 37:
     * truy cập thiết bị trong local network cần
     * ACCESS_LOCAL_NETWORK runtime permission.
     */
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {

                sendWakePacket()

            } else {

                statusText =
                    uiText(R.string.wol_lan_permission)
            }
        }

    val openConfigDialog: () -> Unit = {
        macInput = config.macAddress
        targetIpInput = config.targetIp
        macError = null
        targetIpError = null
        showConfigDialog = true
    }

    DeviceCard(modifier) {
        /*
         * Header:
         *
         * PC                          POWER
         * D8:BB:C1:DC:2E:40
         */
        Row(
            modifier =
                Modifier.fillMaxWidth(),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),

                    verticalAlignment =
                        Alignment.CenterVertically
                ){
                    Text(
                        text = stringResource(R.string.wol_title),

                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = openConfigDialog),

                        style =
                            MaterialTheme.typography.titleLarge,

                        fontWeight =
                            FontWeight.Bold


                    )
                }

                Text(
                    text =
                        config.macAddress,

                    style =
                        MaterialTheme.typography.bodyMedium,

                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.wol_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            /*
             * WAKE / ON
             *
             * Nút Power màu đỏ đồng bộ với quạt trần.
             * Wake-on-LAN chỉ gửi lệnh bật, không xác định trạng thái PC.
             */
            IconButton(
                onClick = openConfigDialog,
                modifier = Modifier.size(KhaiRCDimens.HeaderIconButtonSize)
            ) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.wol_edit_config))
            }
            PowerIconButton(
                onClick = {
                    if (!context.hasLocalNetworkPermission()) {
                        permissionLauncher.launch(LOCAL_NETWORK_PERMISSION)
                    } else {
                        sendWakePacket()
                    }
                },
                contentDescription = stringResource(R.string.wol_wake),
                tone = PowerTone.Danger,
                iconSize = 24.dp
            )
        }



        /*
         * Status / debug.
         *
         * Trong giai đoạn test giữ IP + port để dễ debug.
         */
        statusText?.let { status ->


            Text(
                text = status.asString(),
                modifier = Modifier.padding(top = 8.dp),

                style =
                    MaterialTheme.typography.bodySmall,

                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }
    }

    /*
     * Config dialog
     */
    if (showConfigDialog) {

        AlertDialog(
            onDismissRequest = {

                showConfigDialog =
                    false
            },

            title = {

                Text(
                    stringResource(R.string.wol_config_title)
                )
            },

            text = {

                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(12.dp)
                ) {

                    /*
                     * MAC ADDRESS
                     */
                    OutlinedTextField(
                        value =
                            macInput,

                        onValueChange = {

                            macInput =
                                it

                            macError =
                                null
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        singleLine =
                            true,

                        label = {

                            Text(
                                stringResource(R.string.wol_mac_address)
                            )
                        },

                        placeholder = {

                            Text(
                                WolSettings.DEFAULT_MAC_ADDRESS
                            )
                        },

                        isError =
                            macError != null,

                        supportingText = {

                            macError?.let {

                                Text(it.asString())
                            }
                        }
                    )

                    /*
                     * TARGET IP
                     */
                    OutlinedTextField(
                        value =
                            targetIpInput,

                        onValueChange = {

                            targetIpInput =
                                it

                            targetIpError =
                                null
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        singleLine =
                            true,

                        label = {

                            Text(
                                stringResource(R.string.wol_target_ip)
                            )
                        },

                        placeholder = {

                            Text(
                                WolSettings.DEFAULT_TARGET_IP
                            )
                        },

                        isError =
                            targetIpError != null,

                        supportingText = {

                            targetIpError?.let {

                                Text(it.asString())
                            }
                        }
                    )
                }
            },

            /*
             * SET
             */
            confirmButton = {

                TextButton(
                    onClick = {

                        controller
                            .saveConfig(
                                macAddress =
                                    macInput,

                                targetIp =
                                    targetIpInput
                            )
                            .onSuccess { newConfig ->

                                config =
                                    newConfig

                                showConfigDialog =
                                    false

                                macError =
                                    null

                                targetIpError =
                                    null
                            }
                            .onFailure { error ->

                                val message = error.message
                                when {
                                    message == null -> macError = uiText(R.string.wol_invalid_config)
                                    message.contains("MAC", ignoreCase = true) -> macError = UiText.Raw(message)
                                    message.contains("Target IP", ignoreCase = true) -> targetIpError = UiText.Raw(message)
                                    else -> macError = UiText.Raw(message)
                                }
                            }
                    }
                ) {

                    Text(stringResource(R.string.wol_set))
                }
            },

            /*
             * CANCEL
             */
            dismissButton = {

                TextButton(
                    onClick = {

                        showConfigDialog =
                            false
                    }
                ) {

                    Text(stringResource(R.string.wol_cancel))
                }
            }
        )
    }
}