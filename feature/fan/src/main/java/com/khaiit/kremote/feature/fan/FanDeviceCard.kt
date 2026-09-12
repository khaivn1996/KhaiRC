package com.khaiit.kremote.feature.fan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun FanDeviceCard(
    irAvailable: Boolean,
    onPowerHigh: () -> Unit,
    onSwing: () -> Unit,
    onOff: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {

                    Text(
                        text = "Quạt trần",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Text(
                        text = if (irAvailable) {
                            "IR sẵn sàng"
                        } else {
                            "IR emitter not found"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // OFF
                IconButton(
                    onClick = onOff,
                    enabled = irAvailable,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = "Tắt quạt",
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // HIGH & SWING
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                Button(
                    onClick = onPowerHigh,
                    enabled = irAvailable,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("HIGH")
                }

                Button(
                    onClick = onSwing,
                    enabled = irAvailable,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("SWING")
                }
            }
        }
    }
}