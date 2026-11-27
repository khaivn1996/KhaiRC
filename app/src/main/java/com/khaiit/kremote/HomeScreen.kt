package com.khaiit.kremote

import androidx.compose.ui.res.stringResource
import com.khaiit.kremote.core.ui.components.pressScale
import androidx.annotation.StringRes
import com.khaiit.kremote.core.ui.theme.BackgroundGradientTop
import com.khaiit.kremote.core.ui.theme.BackgroundGradientBottom
import com.khaiit.kremote.core.ui.theme.KhaiRCDimens
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.khaiit.kremote.feature.ac.daikin.DaikinAcController
import com.khaiit.kremote.feature.ac.daikin.DaikinAcDeviceCard
import com.khaiit.kremote.feature.fan.FanDeviceCard
import com.khaiit.kremote.feature.tuya.TuyaOc09Controller
import com.khaiit.kremote.feature.tuya.TuyaOc09DeviceCard
import com.khaiit.kremote.feature.wol.WolController
import com.khaiit.kremote.feature.wol.WolDeviceCard
import com.khaiit.kremote.vault.VaultController
import com.khaiit.kremote.vault.VaultCard

private enum class HomeTab(
    @StringRes val labelRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: ImageVector
) {
    ALL(R.string.home_all_label, R.string.home_all_subtitle, Icons.Filled.Apps),
    IR(R.string.home_ir_label, R.string.home_ir_subtitle, Icons.Filled.SettingsRemote),
    NETWORK(R.string.home_network_label, R.string.home_network_subtitle, Icons.Filled.Lan)
}

@Composable
fun HomeScreen(
    irAvailable: Boolean,
    onPowerHigh: () -> Unit,
    onSwing: () -> Unit,
    onFanOff: () -> Unit,
    wolController: WolController,
    daikinAcController: DaikinAcController,
    toshibaHttpsControlsController: ToshibaHttpsControlsController,
    tuyaOc09Controller: TuyaOc09Controller,
    milifeController: MilifeController,
    vaultController: VaultController,
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
                        listOf(BackgroundGradientTop, BackgroundGradientBottom)
                    )
                )
        ) {
            // Header section - Fixed (Sticky)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = KhaiRCDimens.ScreenPadding, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(selectedTab.subtitleRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Scrollable Content area
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(currentScrollState)
                    .padding(horizontal = KhaiRCDimens.ScreenPadding)
                    .padding(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(KhaiRCDimens.CardSpacing)
            ) {
                // Reuse the existing full-featured cards without changing IR/WOL/Tuya logic.
                if (selectedTab == HomeTab.ALL || selectedTab == HomeTab.IR) {
                    FanDeviceCard(
                        irAvailable = irAvailable,
                        onPowerHigh = onPowerHigh,
                        onSwing = onSwing,
                        onOff = onFanOff
                    )
                    ToshibaHttpsControlsCard(controller = toshibaHttpsControlsController)
                    DaikinAcDeviceCard(controller = daikinAcController)
                }

                if (selectedTab == HomeTab.ALL || selectedTab == HomeTab.NETWORK) {
                    WolDeviceCard(controller = wolController)
                    TuyaOc09DeviceCard(controller = tuyaOc09Controller)
                    MilifeDeviceCard(controller = milifeController)
                    HubHttpsProbeCard()
                    VaultCard(vaultController)
                }
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
                val tabInteraction = remember(tab) { MutableInteractionSource() }

                val tabBackground by animateColorAsState(
                    targetValue = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        else Color.Transparent,
                    animationSpec = tween(durationMillis = 180),
                    label = "nav_background_${tab.name}"
                )
                val tabIconTint by animateColorAsState(
                    targetValue = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(durationMillis = 180),
                    label = "nav_icon_${tab.name}"
                )
                val tabTextTint by animateColorAsState(
                    targetValue = if (active) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(durationMillis = 180),
                    label = "nav_text_${tab.name}"
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(vertical = 5.dp)
                        .pressScale(tabInteraction, enabled = true)
                        .clip(MaterialTheme.shapes.medium)
                        .background(tabBackground)
                        .selectable(
                            selected = active,
                            interactionSource = tabInteraction,
                            indication = ripple(bounded = true),
                            role = Role.Tab,
                            onClick = { if (!active) onSelected(tab) }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = tabIconTint
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(tab.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = tabTextTint,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
