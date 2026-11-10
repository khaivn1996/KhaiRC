package com.khaiit.kremote

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun ToshibaHttpsFixCard(controller: ToshibaHttpsFixController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state = controller.state
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) controller.sendOnce() else controller.permissionDenied()
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Toshiba · FIX qua Hub HTTPS", style = MaterialTheme.typography.titleMedium)
            Text("Chỉ điều chỉnh cánh gió bằng lệnh FIX. Không điều khiển nguồn hay nhiệt độ.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                enabled = !state.busy,
                onClick = {
                    if (Build.VERSION.SDK_INT >= 37 &&
                        ContextCompat.checkSelfPermission(context,
                            Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {
                        permissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                    } else controller.sendOnce()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (state.busy) "ĐANG GỬI…" else "GỬI TOSHIBA FIX (HTTPS)") }
            Text(state.message, style = MaterialTheme.typography.bodySmall)
        }
    }
}
