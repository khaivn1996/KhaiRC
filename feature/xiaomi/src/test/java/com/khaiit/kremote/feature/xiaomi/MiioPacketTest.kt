package com.khaiit.kremote.feature.xiaomi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiioPacketTest {
    private val token = MiioPacket.tokenBytes("00112233445566778899aabbccddeeff")

    @Test fun handshakeParsesDeviceIdAndTimestamp() {
        val reply = byteArrayOf(
            0x21, 0x31, 0x00, 0x20, 0, 0, 0, 0,
            0x7a, 0x2e, 0xd0.toByte(), 0x6e, 0, 9, 0x3d, 0x67
        ) + ByteArray(16) { 0xff.toByte() }
        val parsed = MiioPacket.parseHello(reply)
        assertEquals(0x7a2ed06e, parsed.deviceId)
        assertEquals(0x00093d67, parsed.timestamp)
    }

    @Test fun payloadRoundTrip() {
        val json = """{"id":24,"method":"get_properties","params":[{"did":"2-1","siid":2,"piid":1}]}"""
        val packet = MiioPacket.encode(0x7a2ed06e, 12345, token, json)
        assertEquals(json, MiioPacket.decode(packet, token, 0x7a2ed06e))
        assertTrue(packet.size > 32)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTamperedChecksum() {
        val packet = MiioPacket.encode(0x7a2ed06e, 12345, token, """{"id":1}""")
        packet[16] = (packet[16].toInt() xor 1).toByte()
        MiioPacket.decode(packet, token, 0x7a2ed06e)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidTokenLength() {
        MiioPacket.tokenBytes("123")
    }
}
