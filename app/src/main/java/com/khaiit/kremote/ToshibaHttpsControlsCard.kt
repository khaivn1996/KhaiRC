package com.khaiit.kremote

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.khaiit.kremote.feature.ac.toshiba.ToshibaAcState
import com.khaiit.kremote.feature.ac.toshiba.ToshibaFanSpeed

/** Replaces the phone-IR Toshiba card and Stage 2B standalone FIX card. */
@Composable
fun ToshibaHttpsControlsCard(controller: ToshibaHttpsControlsController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val ui = controller.state
    val model = ui.model
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Do not automatically transmit a command after a permission dialog.
        if (!granted) { /* No permission: next manual press will still be blocked. */ }
    }
    val canSend = !ui.busy && !ui.needsManualCheck
    val send: (() -> Unit) -> Unit = { command ->
        if (Build.VERSION.SDK_INT >= 37 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_LOCAL_NETWORK
            ) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        } else command()
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Toshiba AC", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    Text("ESP32 Hub · HTTPS · IR 38 kHz", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { send(controller::togglePower) }, enabled = canSend) {
                    Icon(Icons.Filled.PowerSettingsNew, contentDescription = null)
                    Text(if (model.power) "TẮT" else "BẬT")
                }
            }
            Text("Trạng thái trên app chỉ là ước lượng, không có phản hồi nguồn từ máy lạnh.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { send(controller::decreaseTemperature) },
                    enabled = canSend && model.power && model.temperatureC > ToshibaAcState.MIN_TEMPERATURE_C) {
                    Text("−")
                }
                Text("${model.temperatureC} °C", style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f))
                Button(onClick = { send(controller::increaseTemperature) },
                    enabled = canSend && model.power && model.temperatureC < ToshibaAcState.MAX_TEMPERATURE_C) {
                    Text("+")
                }
            }
            Button(onClick = { send(controller::chooseCool) },
                enabled = canSend && model.power, modifier = Modifier.fillMaxWidth()) {
                Text("COOL")
            }
            Text("Tốc độ quạt", fontWeight = FontWeight.SemiBold)
            ToshibaFanSpeed.entries.chunked(3).forEach { group ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    group.forEach { fan ->
                        val selected = model.fanSpeed == fan
                        val click = { send { controller.setFanSpeed(fan) } }
                        if (selected) {
                            Button(onClick = click, enabled = canSend && model.power,
                                modifier = Modifier.weight(1f)) { Text(fan.label) }
                        } else {
                            OutlinedButton(onClick = click, enabled = canSend && model.power,
                                modifier = Modifier.weight(1f)) { Text(fan.label) }
                        }
                    }
                }
            }
            Text("Hướng gió", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { send(controller::fix) },
                    enabled = canSend && model.power, modifier = Modifier.weight(1f)) {
                    Text("FIX")
                }
                OutlinedButton(onClick = { send(controller::toggleSwing) },
                    enabled = canSend && model.power, modifier = Modifier.weight(1f)) {
                    Text(if (model.swing) "SWING OFF" else "SWING ON")
                }
            }
            Text(ui.message, style = MaterialTheme.typography.bodySmall,
                color = if (ui.needsManualCheck) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant)
            if (ui.needsManualCheck) {
                OutlinedButton(
                    onClick = controller::confirmObserved,
                    enabled = !ui.busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Tôi đã kiểm tra máy lạnh — tiếp tục") }
            }
        }
    }
}
