package com.khaiit.kremote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.khaiit.kremote.feature.fan.FanDeviceCard
import com.khaiit.kremote.feature.wol.WolController
import com.khaiit.kremote.feature.wol.WolDeviceCard

@Composable
fun HomeScreen(
    irAvailable: Boolean,
    onPowerHigh: () -> Unit,
    onSwing: () -> Unit,
    onFanOff: () -> Unit,
    wolController: WolController,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        Text(
            text = "Khai RC",
            style = MaterialTheme.typography.headlineLarge
        )

        FanDeviceCard(
            irAvailable = irAvailable,
            onPowerHigh = onPowerHigh,
            onSwing = onSwing,
            onOff = onFanOff
        )

        WolDeviceCard(
            controller = wolController
        )
    }
}