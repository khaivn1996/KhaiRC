package com.khaiit.kremote.core.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

const val LOCAL_NETWORK_PERMISSION = Manifest.permission.ACCESS_LOCAL_NETWORK

fun Context.hasLocalNetworkPermission(): Boolean =
    Build.VERSION.SDK_INT < 37 || ContextCompat.checkSelfPermission(
        this, LOCAL_NETWORK_PERMISSION
    ) == PackageManager.PERMISSION_GRANTED
