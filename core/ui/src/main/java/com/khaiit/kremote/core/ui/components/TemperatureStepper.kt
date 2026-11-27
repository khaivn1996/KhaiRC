package com.khaiit.kremote.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khaiit.kremote.core.ui.R

@Composable
fun TemperatureStepper(
    temperatureC: Int,
    active: Boolean,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PressFeedbackButton(onClick = onDecrease, enabled = canDecrease) {
            Text(stringResource(R.string.common_minus), fontSize = 24.sp)
        }
        Text(
            text = stringResource(R.string.common_temperature_c, temperatureC),
            style = MaterialTheme.typography.displaySmall,
            color = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        PressFeedbackButton(onClick = onIncrease, enabled = canIncrease) {
            Text(stringResource(R.string.common_plus), fontSize = 24.sp)
        }
    }
}
