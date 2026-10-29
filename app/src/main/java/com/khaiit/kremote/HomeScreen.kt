package com.khaiit.kremote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khaiit.kremote.feature.ac.daikin.DaikinAcController
import com.khaiit.kremote.feature.ac.daikin.DaikinAcDeviceCard
import com.khaiit.kremote.feature.ac.toshiba.ToshibaAcController
import com.khaiit.kremote.feature.ac.toshiba.ToshibaAcDeviceCard
import com.khaiit.kremote.feature.fan.FanDeviceCard
import com.khaiit.kremote.feature.tuya.TuyaOc09Controller
import com.khaiit.kremote.feature.tuya.TuyaOc09DeviceCard
import com.khaiit.kremote.feature.wol.WolController
import com.khaiit.kremote.feature.wol.WolDeviceCard

private enum class HomeTab(val label: String, val subtitle: String, val icon: ImageVector) {
    ALL("Tất cả", "5 thiết bị · IR / LAN / Cloud", Icons.Filled.Apps),
    IR("Hồng ngoại", "3 thiết bị điều khiển IR", Icons.Filled.SettingsRemote),
    NETWORK("Mạng", "2 thiết bị · LAN / Cloud", Icons.Filled.Lan)
}

@Composable
fun HomeScreen(
    irAvailable: Boolean,
    onPowerHigh: () -> Unit,
    onSwing: () -> Unit,
    onFanOff: () -> Unit,
    wolController: WolController,
    daikinAcController: DaikinAcController,
    toshibaAcController: ToshibaAcController,
    tuyaOc09Controller: TuyaOc09Controller,
    modifier: Modifier = Modifier
) {
    // Tab navigation is UI-only: controllers stay in MainActivity and Tuya never
    // performs an extra Cloud refresh merely because a tab was selected.
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.ALL) }

    // Keep an independent saveable scroll position per tab.
    val allScrollState = rememberScrollState()
    val irScrollState = rememberScrollState()
    val networkScrollState = rememberScrollState()
    val currentScrollState = when (selectedTab) {
        HomeTab.ALL -> allScrollState
        HomeTab.IR -> irScrollState
        HomeTab.NETWORK -> networkScrollState
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            CompactDarkTechNavigationBar(
                selected = selectedTab,
                onSelected = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0A101C), Color(0xFF0E192A))
                    )
                )
                .verticalScroll(currentScrollState)
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .width(4.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Khai RC",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        selectedTab.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Reuse the existing full-featured cards without changing IR/WOL/Tuya logic.
            if (selectedTab == HomeTab.ALL || selectedTab == HomeTab.IR) {
                FanDeviceCard(
                    irAvailable = irAvailable,
                    onPowerHigh = onPowerHigh,
                    onSwing = onSwing,
                    onOff = onFanOff
                )
                DaikinAcDeviceCard(controller = daikinAcController)
                ToshibaAcDeviceCard(controller = toshibaAcController)
            }

            if (selectedTab == HomeTab.ALL || selectedTab == HomeTab.NETWORK) {
                WolDeviceCard(controller = wolController)
                TuyaOc09DeviceCard(controller = tuyaOc09Controller)
            }
        }
    }
}

/** 60dp content height, PLUS the actual Android system navigation inset. */
@Composable
private fun CompactDarkTechNavigationBar(
    selected: HomeTab,
    onSelected: (HomeTab) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeTab.entries.forEach { tab ->
                val active = selected == tab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            else Color.Transparent
                        )
                        .selectable(
                            selected = active,
                            role = Role.Tab,
                            onClick = { onSelected(tab) }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = tab.label,
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
