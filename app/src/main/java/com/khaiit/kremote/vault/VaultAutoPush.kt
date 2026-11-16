package com.khaiit.kremote.vault

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class VaultAutoPush(context: Context, private val controller: VaultController) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pending: Job? = null
    private val preferences = listOf("tuya_oc09_secure", "hub_secure_v1", "hub_https_probe_v1",
        "wol_settings", "tuya_oc09_labels").map {
        context.applicationContext.getSharedPreferences(it, Context.MODE_PRIVATE)
    }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (!controller.applying) {
            controller.markDirty()
            pending?.cancel()
            pending = scope.launch {
                delay(1500)
                controller.pushNow()
            }
        }
    }

    init { preferences.forEach { it.registerOnSharedPreferenceChangeListener(listener) } }

    override fun close() {
        preferences.forEach { it.unregisterOnSharedPreferenceChangeListener(listener) }
        scope.cancel()
    }
}
