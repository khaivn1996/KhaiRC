package com.khaiit.kremote.core.vault

data class MergeResult(val toApply: VaultBundle, val merged: VaultBundle, val needsPush: Boolean) {
    override fun toString() = "MergeResult(<redacted>)"
}

object VaultMerge {
    fun merge(local: VaultBundle, cloud: VaultBundle): MergeResult {
        val normalized = local.copy(
            tuya = local.tuya?.takeUnless { it.clientId.isBlank() && it.clientSecret.isBlank() && it.deviceId.isBlank() },
            hub = local.hub?.takeUnless { it.ipv4.isBlank() && it.apiKey.isBlank() },
            https = local.https?.takeUnless { it.host.isBlank() && it.fingerprintHex.isBlank() },
            wol = local.wol?.takeUnless { it.mac.isBlank() && it.ip.isBlank() },
            labels = local.labels?.takeIf { it.isNotEmpty() }
        )
        val apply = VaultBundle(
            cloud.tuya.takeIf { normalized.tuya == null },
            cloud.hub.takeIf { normalized.hub == null },
            cloud.https.takeIf { normalized.https == null },
            cloud.wol.takeIf { normalized.wol == null },
            cloud.labels.takeIf { normalized.labels == null }
        )
        val merged = VaultBundle(
            normalized.tuya ?: cloud.tuya, normalized.hub ?: cloud.hub,
            normalized.https ?: cloud.https, normalized.wol ?: cloud.wol,
            normalized.labels ?: cloud.labels
        )
        return MergeResult(apply, merged, merged != cloud)
    }
}
