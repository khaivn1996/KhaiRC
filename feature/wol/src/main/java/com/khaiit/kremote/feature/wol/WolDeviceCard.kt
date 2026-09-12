package com.khaiit.kremote.feature.wol

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

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
        mutableStateOf<String?>(null)
    }

    var targetIpError by remember {
        mutableStateOf<String?>(null)
    }

    var statusText by remember {
        mutableStateOf<String?>(null)
    }

    /*
     * Gửi Wake-on-LAN.
     */
    val sendWakePacket: () -> Unit = {

        coroutineScope.launch {

            statusText =
                "Đang gửi Wake-on-LAN..."

            controller
                .wake()
                .onSuccess {

                    statusText =
                        "Đã gửi tới ${config.targetIp}:9"
                }
                .onFailure { error ->

                    statusText =
                        error.message
                            ?: "Không thể gửi Wake-on-LAN"
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
                    "Cần quyền Local network để Wake-on-LAN"
            }
        }

    Card(
        modifier =
            modifier.fillMaxWidth(),

        shape =
            RoundedCornerShape(24.dp),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme.colorScheme.surfaceContainer
            )
    ) {

        Column(
            modifier =
                Modifier.padding(20.dp)
        ) {

            /*
             * Header:
             *
             * PC                          POWER
             * D8:BB:C1:DC:2E:41
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

                    Text(
                        text = "PC",

                        style =
                            MaterialTheme.typography.titleLarge,

                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        text =
                            config.macAddress,

                        style =
                            MaterialTheme.typography.bodyMedium,

                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant
                    )
                }

                /*
                 * WAKE / ON
                 *
                 * Primary color vì đây là hành động bật máy.
                 * Khác với nút OFF quạt dùng error/red.
                 */
                IconButton(
                    onClick = {

                        val needsPermission =
                            Build.VERSION.SDK_INT >= 37 &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.ACCESS_LOCAL_NETWORK
                                    ) != PackageManager.PERMISSION_GRANTED

                        if (needsPermission) {

                            permissionLauncher.launch(
                                Manifest.permission.ACCESS_LOCAL_NETWORK
                            )

                        } else {

                            sendWakePacket()
                        }
                    },

                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.primary
                            )
                ) {

                    Icon(
                        imageVector =
                            Icons.Filled.PowerSettingsNew,

                        contentDescription =
                            "Bật PC bằng Wake-on-LAN",

                        tint =
                            MaterialTheme.colorScheme.onPrimary,

                        modifier =
                            Modifier.size(24.dp)
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.size(16.dp)
            )

            /*
             * CHANGE
             */
            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.End
            ) {

                OutlinedButton(
                    onClick = {

                        macInput =
                            config.macAddress

                        targetIpInput =
                            config.targetIp

                        macError =
                            null

                        targetIpError =
                            null

                        showConfigDialog =
                            true
                    }
                ) {

                    Icon(
                        imageVector =
                            Icons.Filled.Edit,

                        contentDescription =
                            null,

                        modifier =
                            Modifier.size(18.dp)
                    )

                    Spacer(
                        modifier =
                            Modifier.width(8.dp)
                    )

                    Text("CHANGE")
                }
            }

            /*
             * Status / debug.
             *
             * Trong giai đoạn test giữ IP + port để dễ debug.
             */
            statusText?.let { status ->

                Spacer(
                    modifier =
                        Modifier.size(12.dp)
                )

                Text(
                    text = status,

                    style =
                        MaterialTheme.typography.bodySmall,

                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )
            }
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
                    "PC Wake-on-LAN"
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
                                "MAC Address"
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

                                Text(it)
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
                                "Target IP"
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

                                Text(it)
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

                                val message =
                                    error.message
                                        ?: "Thông tin Wake-on-LAN không hợp lệ"

                                when {

                                    message.contains(
                                        "MAC",
                                        ignoreCase = true
                                    ) -> {

                                        macError =
                                            message
                                    }

                                    message.contains(
                                        "Target IP",
                                        ignoreCase = true
                                    ) -> {

                                        targetIpError =
                                            message
                                    }

                                    else -> {

                                        macError =
                                            message
                                    }
                                }
                            }
                    }
                ) {

                    Text("SET")
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

                    Text("CANCEL")
                }
            }
        )
    }
}