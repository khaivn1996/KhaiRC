package com.khaiit.kremote.feature.ac.toshiba

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ToshibaAcEncoderTest {
    private val encoder = ToshibaAcEncoder()

    @Test
    fun powerOnCool24FanAutoMatchesOriginalRemote() {
        assertHex(
            "4F B0 C0 3F 80 0E 80 00 0E",
            encoder.buildPowerOrCoolFrame(ToshibaAcState(power = true))
        )
    }

    @Test
    fun powerOffMatchesOriginalRemoteRegardlessOfSelectedFan() {
        ToshibaFanSpeed.entries.forEach { speed ->
            assertHex(
                "4F B0 C0 3F 80 0E E0 00 6E",
                encoder.buildPowerOrCoolFrame(
                    ToshibaAcState(power = false, fanSpeed = speed)
                )
            )
        }
    }

    @Test
    fun allCapturedFanSpeedsMatch() {
        val expected = mapOf(
            ToshibaFanSpeed.AUTO to "4F B0 C0 3F 80 0E 80 00 0E",
            ToshibaFanSpeed.LEVEL_1 to "4F B0 C0 3F 80 0E 82 00 0C",
            ToshibaFanSpeed.LEVEL_2 to "4F B0 C0 3F 80 0E 86 00 08",
            ToshibaFanSpeed.LEVEL_3 to "4F B0 C0 3F 80 0E 81 00 0F",
            ToshibaFanSpeed.LEVEL_4 to "4F B0 C0 3F 80 0E 85 00 0B",
            ToshibaFanSpeed.LEVEL_5 to "4F B0 C0 3F 80 0E 83 00 0D"
        )
        expected.forEach { (speed, hex) ->
            assertHex(
                hex,
                encoder.buildPowerOrCoolFrame(
                    ToshibaAcState(power = true, fanSpeed = speed)
                )
            )
        }
    }

    @Test
    fun fixSwingOnAndSwingOffMatchOriginalRemote() {
        assertHex("4F B0 80 7F 84 00 84", encoder.buildFixFrame())
        assertHex("4F B0 80 7F 84 80 04", encoder.buildSwingOnFrame())
        assertHex("4F B0 80 7F 84 40 C4", encoder.buildSwingOffFrame())
    }

    @Test
    fun frameCountsAndTimingSizesAreCorrect() {
        val stateCommand = encoder.encodePowerOrCool(ToshibaAcState(power = true))
        assertEquals(2, stateCommand.repeatCount)
        assertEquals(296, stateCommand.pattern.size) // 148 timings * 2
        assertEquals(4_350, stateCommand.pattern[0])
        assertEquals(4_300, stateCommand.pattern[1])
        assertEquals(550, stateCommand.pattern[146]) // first stop mark
        assertEquals(7_350, stateCommand.pattern[147]) // inter-frame space
        assertEquals(4_350, stateCommand.pattern[148]) // second header
        assertEquals(550, stateCommand.pattern.last()) // trailing space

        val fix = encoder.encodeFix()
        assertEquals(1, fix.repeatCount)
        assertEquals(116, fix.pattern.size)

        val on = encoder.encodeSwingOn()
        val off = encoder.encodeSwingOff()
        assertEquals(2, on.repeatCount)
        assertEquals(232, on.pattern.size)
        assertEquals(2, off.repeatCount)
        assertEquals(232, off.pattern.size)
    }

    @Test
    fun builtPatternDecodesBackToPayload() {
        listOf(
            encoder.encodePowerOrCool(ToshibaAcState(power = true)),
            encoder.encodePowerOrCool(ToshibaAcState(power = false)),
            encoder.encodeFix(),
            encoder.encodeSwingOn(),
            encoder.encodeSwingOff()
        ).forEach { command ->
            val frameTimings = 2 + command.frame.size * 16 + 2
            repeat(command.repeatCount) { frameIndex ->
                val from = frameIndex * frameTimings
                val decoded = ByteArray(command.frame.size)
                repeat(command.frame.size) { index ->
                    var value = 0
                    repeat(8) { bit ->
                        val pos = from + 2 + index * 16 + bit * 2
                        assertEquals(ToshibaAcEncoder.BIT_MARK_US, command.pattern[pos])
                        val space = command.pattern[pos + 1]
                        if (space == ToshibaAcEncoder.ONE_SPACE_US) {
                            value = value or (1 shl bit)
                        } else {
                            assertEquals(ToshibaAcEncoder.ZERO_SPACE_US, space)
                        }
                    }
                    decoded[index] = value.toByte()
                }
                assertArrayEquals(command.frame, decoded)
            }
        }
    }

    private fun assertHex(expected: String, data: ByteArray) {
        assertEquals(
            expected,
            data.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
        )
    }
}
