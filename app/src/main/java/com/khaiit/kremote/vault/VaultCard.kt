package com.khaiit.kremote.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun VaultCard(controller: VaultController) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sao lưu cloud")
            Text(controller.syncStatus)
            // Push only exists once this install is synced; otherwise offer the gate again instead of a silent no-op.
            if (controller.backupEnabled) {
                OutlinedButton(onClick = controller::pushNow) { Text("Sao lưu ngay") }
            } else {
                OutlinedButton(onClick = controller::reopenSetup) { Text("Thiết lập sao lưu") }
            }
        }
    }
}
