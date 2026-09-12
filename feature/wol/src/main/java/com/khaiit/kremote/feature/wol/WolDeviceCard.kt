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

    var showMacDialog by remember {
        mutableStateOf(false)
    }

    var macInput by remember {
        mutableStateOf(
            config.macAddress
        )
    }

    var broadcastInput by remember {
        mutableStateOf(
            config.broadcastAddress
        )
    }

    var macError by remember {
        mutableStateOf<String?>(null)
    }

    var broadcastError by remember {
        mutableStateOf<String?>(null)
    }

    var statusText by remember {
        mutableStateOf<String?>(null)
    }

    val sendWakePacket: () -> Unit = {

        coroutineScope.launch {

            statusText =
                "Đang gửi Wake-on-LAN..."

            controller
                .wake()
                .onSuccess {

                    statusText =
                        "Đã gửi tới ${config.broadcastAddress}:9"
                }
                .onFailure { error ->

                    statusText =
                        error.message
                            ?: "Không thể gửi Wake-on-LAN"
                }
        }
    }

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
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surfaceContainer
        )
    ) {

        Column(
            modifier = Modifier.padding(20.dp)
        ) {

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
                        text = config.macAddress,
                        style =
                            MaterialTheme.typography.bodyMedium,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant
                    )
                }

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
                    modifier = Modifier
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

                        broadcastInput =
                            config.broadcastAddress

                        macError =
                            null

                        broadcastError =
                            null

                        showMacDialog =
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

    if (showMacDialog) {

        AlertDialog(
            onDismissRequest = {
                showMacDialog = false
            },

            title = {
                Text("PC Wake-on-LAN")
            },

            text = {

                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(12.dp)
                ) {

                    OutlinedTextField(
                        value = macInput,

                        onValueChange = {
                            macInput = it
                            macError = null
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        singleLine = true,

                        label = {
                            Text("MAC Address")
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

                    OutlinedTextField(
                        value = broadcastInput,

                        onValueChange = {
                            broadcastInput = it
                            broadcastError = null
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        singleLine = true,

                        label = {
                            Text("Broadcast Address")
                        },

                        placeholder = {
                            Text(
                                WolSettings.DEFAULT_BROADCAST_ADDRESS
                            )
                        },

                        isError =
                            broadcastError != null,

                        supportingText = {

                            broadcastError?.let {
                                Text(it)
                            }
                        }
                    )
                }
            },

            confirmButton = {

                TextButton(
                    onClick = {
                        controller
                            .saveConfig(
                                macAddress =
                                    macInput,

                                broadcastAddress =
                                    broadcastInput
                            )
                            .onSuccess { newConfig ->

                                config =
                                    newConfig

                                showMacDialog =
                                    false

                                macError =
                                    null

                                broadcastError =
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
                                        "Broadcast",
                                        ignoreCase = true
                                    ) -> {

                                        broadcastError =
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

            dismissButton = {

                TextButton(
                    onClick = {
                        showMacDialog = false
                    }
                ) {

                    Text("CANCEL")
                }
            }
        )
    }
}