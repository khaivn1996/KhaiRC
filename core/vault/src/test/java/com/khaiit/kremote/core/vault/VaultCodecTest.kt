package com.khaiit.kremote.core.vault

import org.junit.Assert.*
import org.junit.Test

class VaultCodecTest {
    @Test fun roundtripAllGroups() {
        val bundle = VaultBundle(TuyaPart("client", "secret", "device"),
            HubPart("192.168.1.2", "key"), HttpsPart("hub.example", 443, "pin"),
            WolPart("AA:BB:CC:DD:EE:FF", "192.168.1.255"), mapOf("switch_5" to "Đèn"))
        assertEquals(bundle, VaultCodec.decode(VaultCodec.encode(bundle)))
    }

    @Test fun roundtripEverySubset() {
        repeat(32) { mask ->
            val bundle = VaultBundle(
                if (mask and 1 != 0) TuyaPart("a", "b", "c") else null,
                if (mask and 2 != 0) HubPart("d", "e") else null,
                if (mask and 4 != 0) HttpsPart("f", 443, "g") else null,
                if (mask and 8 != 0) WolPart("h", "i") else null,
                if (mask and 16 != 0) mapOf("switch_1" to "j") else null)
            assertEquals(bundle, VaultCodec.decode(VaultCodec.encode(bundle)))
        }
    }

    @Test fun unknownVersion() {
        assertThrows(VaultFormatException::class.java) { VaultCodec.decode(byteArrayOf(2)) }
    }

    @Test fun trailingBytes() {
        assertThrows(VaultFormatException::class.java) {
            VaultCodec.decode(VaultCodec.encode(VaultBundle()) + byteArrayOf(0))
        }
    }

    @Test fun truncatedData() {
        val bytes = VaultCodec.encode(VaultBundle(tuya = TuyaPart("a", "b", "c")))
        for (length in bytes.indices) {
            assertThrows(VaultFormatException::class.java) { VaultCodec.decode(bytes.copyOf(length)) }
        }
    }

    @Test fun invalidPresenceFlag() {
        val bytes = VaultCodec.encode(VaultBundle())
        bytes[1] = 2
        assertThrows(VaultFormatException::class.java) { VaultCodec.decode(bytes) }
    }

    @Test fun deterministicLabelOrder() {
        assertArrayEquals(
            VaultCodec.encode(VaultBundle(labels = linkedMapOf("b" to "2", "a" to "1"))),
            VaultCodec.encode(VaultBundle(labels = linkedMapOf("a" to "1", "b" to "2"))))
    }
}
