package com.khaiit.kremote.feature.tuya

/** Verified mapping of the Ralli / Rang Dong OC09, NOT numeric Tuya DP order. */
data class TuyaChannel(val label: String, val code: String) {
    companion object {
        val ALL = listOf(
            TuyaChannel("Ổ cắm 1", "switch_5"),
            TuyaChannel("Ổ cắm 2", "switch_4"),
            TuyaChannel("Ổ cắm 3", "switch_3"),
            TuyaChannel("Ổ cắm 4", "switch_2"),
            TuyaChannel("Ổ cắm 5", "switch_1"),
            TuyaChannel("USB", "switch_6")
        )
        val CODES = ALL.map { it.code }.toSet()
    }
}

data class TuyaOc09State(
    val configured: Boolean = false,
    val channelValues: Map<String, Boolean> = emptyMap(),
    val verified: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null
) {
    /** Never infer OFF from missing or stale data. */
    fun channelPower(code: String): Boolean? = if (verified) channelValues[code] else null

    /** Read-only overall indicator, no master command exists in this OC09 instruction set. */
    val masterPower: Boolean?
        get() = if (verified && TuyaChannel.CODES.all(channelValues::containsKey)) {
            TuyaChannel.CODES.any { channelValues[it] == true }
        } else null
}
