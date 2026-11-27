package com.khaiit.kremote.vault

import androidx.compose.ui.res.stringResource
import com.khaiit.kremote.core.ui.text.asString
import com.khaiit.kremote.core.ui.components.DeviceCard
import com.khaiit.kremote.R
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun VaultCard(controller: VaultController) {
    DeviceCard(verticalSpacing = 8.dp) {
              Text(stringResource(R.string.vault_title))
              Text(controller.syncStatus.asString())
              // Push only exists once this install is synced; otherwise offer the gate again instead of a silent no-op.
              if (controller.backupEnabled) {
                  OutlinedButton(onClick = controller::pushNow) { Text(stringResource(R.string.vault_push_now)) }
              } else {
                  OutlinedButton(onClick = controller::reopenSetup) { Text(stringResource(R.string.vault_setup)) }
              }
    }
}
