package com.khaiit.kremote.core.vault

import org.junit.Assert.*
import org.junit.Test

class VaultPrivacyTest {
    @Test fun allModelsRedactToString() {
        val marker = "sample-private-value"
        val bundle = VaultBundle(TuyaPart(marker, marker, marker), HubPart(marker, marker),
            HttpsPart(marker, 443, marker), WolPart(marker, marker), mapOf("switch_1" to marker))
        val blob = VaultCrypto.seal(ByteArray(32), VaultCodec.encode(bundle), "uid")
        val doc = SealedDoc(1000, ByteArray(16), blob.iv, blob.ct)
        listOf(bundle, bundle.tuya, bundle.hub, bundle.https, bundle.wol, blob, doc,
            MergeResult(bundle, bundle, true), VaultKey(ByteArray(32), ByteArray(16), 1000)).forEach {
            assertTrue(it.toString().contains("<redacted>"))
            assertFalse(it.toString().contains(marker))
        }
    }

    @Test fun remoteMapContainsNoPlaintext() {
        val marker = "sample-private-value"
        val plain = VaultCodec.encode(VaultBundle(tuya = TuyaPart(marker, marker, marker)))
        val blob = VaultCrypto.seal(ByteArray(32), plain, "uid")
        val doc = SealedDoc(1000, ByteArray(16), blob.iv, blob.ct)
        assertEquals(setOf("schemaVersion", "kdfAlg", "iterations", "salt", "iv", "ct"), doc.toMap().keys)
        assertFalse(doc.toMap().toString().contains(marker))
        val parsed = SealedDoc.fromMap(doc.toMap().mapValues { (_, value) ->
            if (value is Int) value.toLong() else value
        })
        assertArrayEquals(plain, VaultCrypto.open(ByteArray(32), SealedBlob(parsed.iv, parsed.ct), "uid"))
    }

    @Test fun malformedRemoteMetadataRejected() {
        val doc = SealedDoc(1000, ByteArray(16), ByteArray(12), ByteArray(16))
        val map = doc.toMap().mapValues { (_, value) -> if (value is Int) value.toLong() else value }
        listOf(map + ("schemaVersion" to 2L), map + ("kdfAlg" to "unknown"),
            map + ("iterations" to Long.MAX_VALUE), map + ("salt" to "!invalid"),
            map + ("iv" to "")).forEach { invalid ->
            assertThrows(VaultFormatException::class.java) { SealedDoc.fromMap(invalid) }
        }
    }
}
