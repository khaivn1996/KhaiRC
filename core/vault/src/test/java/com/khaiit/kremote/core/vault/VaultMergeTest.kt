package com.khaiit.kremote.core.vault

import org.junit.Assert.*
import org.junit.Test

class VaultMergeTest {
    private fun bundle(mask: Int, prefix: String) = VaultBundle(
        if (mask and 1 != 0) TuyaPart(prefix, prefix, prefix) else null,
        if (mask and 2 != 0) HubPart(prefix, prefix) else null,
        if (mask and 4 != 0) HttpsPart(prefix, 443, prefix) else null,
        if (mask and 8 != 0) WolPart(prefix, prefix) else null,
        if (mask and 16 != 0) mapOf("switch_1" to prefix) else null)

    @Test fun allLocalAndCloudCombinations() {
        repeat(32) { localMask ->
            repeat(32) { cloudMask ->
                val local = bundle(localMask, "local")
                val cloud = bundle(cloudMask, "cloud")
                val result = VaultMerge.merge(local, cloud)
                val expectedApply = bundle(cloudMask and localMask.inv(), "cloud")
                val expectedMerged = VaultBundle(local.tuya ?: cloud.tuya, local.hub ?: cloud.hub,
                    local.https ?: cloud.https, local.wol ?: cloud.wol, local.labels ?: cloud.labels)
                assertEquals(expectedApply, result.toApply)
                assertEquals(expectedMerged, result.merged)
                assertEquals(expectedMerged != cloud, result.needsPush)
            }
        }
    }

    @Test fun blankLocalGroupsUseCloud() {
        val empty = VaultBundle(TuyaPart("", "", ""), HubPart("", ""),
            HttpsPart("", 0, ""), WolPart("", ""), emptyMap())
        val cloud = bundle(31, "cloud")
        val result = VaultMerge.merge(empty, cloud)
        assertEquals(cloud, result.toApply)
        assertEquals(cloud, result.merged)
        assertFalse(result.needsPush)
    }

    @Test fun equalLocalDoesNotPush() {
        val cloud = bundle(31, "same")
        val result = VaultMerge.merge(cloud, cloud)
        assertEquals(VaultBundle(), result.toApply)
        assertFalse(result.needsPush)
    }
}
