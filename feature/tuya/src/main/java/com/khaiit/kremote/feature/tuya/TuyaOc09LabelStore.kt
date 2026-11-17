package com.khaiit.kremote.feature.tuya

import android.content.Context

/** Names are UI-only metadata, keyed by the verified DP code, never by grid position. */
internal object TuyaOc09LabelRules {
    const val MAX_LENGTH = 24

    fun normalize(input: String): String {
        val value = input.trim()
        require(value.length <= MAX_LENGTH) { "Tên ổ cắm tối đa $MAX_LENGTH ký tự" }
        require(value.none(Char::isISOControl)) { "Tên ổ cắm chứa ký tự không hợp lệ" }
        return value
    }

    fun displayName(channel: TuyaChannel, custom: String?): String =
        custom?.trim()?.takeIf { it.isNotEmpty() } ?: channel.label
}

/**
 * Cloud-backup access to the same `tuya_oc09_labels` prefs: only custom names (defaults omitted),
 * keyed by verified DP code. Never touches Tuya credentials or DP mappings.
 */
class TuyaOc09LabelBackup(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("tuya_oc09_labels", Context.MODE_PRIVATE)

    fun loadCustom(): Map<String, String> = TuyaChannel.ALL.mapNotNull { channel ->
        val label = prefs.getString(channel.code, null)?.let(TuyaOc09LabelRules::normalize)
        label?.takeIf { it.isNotEmpty() && it != channel.label }?.let { channel.code to it }
    }.toMap()

    fun saveCustom(map: Map<String, String>) {
        require(map.keys.all { it in TuyaChannel.CODES })
        val normalized = map.mapValues { TuyaOc09LabelRules.normalize(it.value) }
        val editor = prefs.edit()
        TuyaChannel.ALL.forEach { channel ->
            val label = normalized[channel.code]
            if (label.isNullOrEmpty() || label == channel.label) editor.remove(channel.code)
            else editor.putString(channel.code, label)
        }
        check(editor.commit()) { "Không thể lưu tên ổ cắm" }
    }
}

/**
 * Only custom display names are stored here: no Tuya IDs, credentials, or tokens.
 * Empty names reset to the default. Tuya DP mappings are never edited.
 */
internal class TuyaOc09LabelStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("tuya_oc09_labels", Context.MODE_PRIVATE)

    fun load(): Map<String, String> = TuyaChannel.ALL.associate { channel ->
        channel.code to TuyaOc09LabelRules.displayName(channel, prefs.getString(channel.code, null))
    }

    fun save(names: Map<String, String>) {
        require(names.keys == TuyaChannel.CODES) { "Thiếu nhãn ổ cắm" }
        val editor = prefs.edit()
        TuyaChannel.ALL.forEach { channel ->
            val label = TuyaOc09LabelRules.normalize(names.getValue(channel.code))
            if (label.isEmpty() || label == channel.label) editor.remove(channel.code)
            else editor.putString(channel.code, label)
        }
        check(editor.commit()) { "Không thể lưu tên ổ cắm" }
    }
}
