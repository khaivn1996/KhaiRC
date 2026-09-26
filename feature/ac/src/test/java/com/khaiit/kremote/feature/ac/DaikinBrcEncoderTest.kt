package com.khaiit.kremote.feature.ac

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DaikinBrcEncoderTest {

    private val encoder = DaikinBrcEncoder(
        DaikinBrcProfile.BRC4C153
    )

    @Test
    fun defaultCool25MediumSwingOn_matchesKnownBaseFrame() {
        val frame = encoder.buildFrame(
            DaikinAcState(
                power = true,
                temperatureC = 25,
                mode = DaikinMode.COOL,
                fanSpeed = DaikinFanSpeed.HIGH,
                swing = true
            )
        )

        val expected = intArrayOf(
            0x11, 0xDA, 0x17, 0x18, 0x04, 0x00, 0x1E,
            0x11, 0xDA, 0x17, 0x18, 0x00, 0x73, 0x00, 0x21,
            0x00, 0x00, 0x20, 0x35, 0x00, 0x20, 0x23
        )

        assertArrayEquals(
            expected,
            frame.map { it.toUByte().toInt() }.toIntArray()
        )
    }

    @Test
    fun temp26_changesTemperatureByteAndChecksum() {
        val frame = encoder.buildFrame(
            DaikinAcState(
                power = true,
                temperatureC = 26,
                mode = DaikinMode.COOL,
                fanSpeed = DaikinFanSpeed.HIGH,
                swing = true
            )
        )

        assertEquals(0x22, frame[17].toUByte().toInt())
        assertEquals(0x25, frame[21].toUByte().toInt())
    }

    @Test
    fun swingOff_changesLowNibbleAndChecksum() {
        val frame = encoder.buildFrame(
            DaikinAcState(
                power = true,
                temperatureC = 26,
                mode = DaikinMode.COOL,
                fanSpeed = DaikinFanSpeed.HIGH,
                swing = false
            )
        )

        assertEquals(0x36, frame[18].toUByte().toInt())
        assertEquals(0x26, frame[21].toUByte().toInt())
    }

    @Test
    fun highFanSwingOff_usesBrc4c153Mapping() {
        val frame = encoder.buildFrame(
            DaikinAcState(
                power = true,
                temperatureC = 26,
                mode = DaikinMode.COOL,
                fanSpeed = DaikinFanSpeed.HIGH,
                swing = false
            )
        )

        assertEquals(
            0x36,
            frame[18].toUByte().toInt()
        )

        assertEquals(
            0x26,
            frame[21].toUByte().toInt()
        )
    }

    @Test
    fun powerOff_clearsOperationByte() {
        val frame = encoder.buildFrame(
            DaikinAcState(
                power = false,
                temperatureC = 25,
                mode = DaikinMode.COOL,
                fanSpeed = DaikinFanSpeed.HIGH,
                swing = true
            )
        )

        /*
         * BRC4CXXX OFF:
         * operation byte phải bằng 0x00.
         */
        assertEquals(
            0x00,
            frame[14].toUByte().toInt()
        )

        assertEquals(
            0x02,
            frame[21].toUByte().toInt()
        )
    }

    @Test
    fun normalCommand_doesNotSetModeButtonByte() {
        val frame = encoder.buildFrame(
            DaikinAcState(
                power = true,
                temperatureC = 25,
                mode = DaikinMode.COOL,
                fanSpeed = DaikinFanSpeed.HIGH,
                swing = true
            )
        )

        assertEquals(
            0x00,
            frame[13].toUByte().toInt()
        )
    }

    @Test
    fun modeButton_setsModeButtonByte() {
        val frame = encoder.buildFrame(
            state = DaikinAcState(
                power = true,
                temperatureC = 25,
                mode = DaikinMode.DRY,
                fanSpeed = DaikinFanSpeed.HIGH,
                swing = true
            ),
            modeButtonPressed = true
        )

        assertEquals(
            0x04,
            frame[13].toUByte().toInt()
        )

        assertEquals(
            0x23,
            frame[12].toUByte().toInt()
        )

        assertEquals(
            0x71,
            frame[14].toUByte().toInt()
        )
    }

    @Test
    fun pattern_isLsbFirst_andContainsInterFrameGap() {
        val command = encoder.encode(
            DaikinAcState(
                power = true,
                temperatureC = 25,
                mode = DaikinMode.COOL,
                fanSpeed = DaikinFanSpeed.HIGH,
                swing = true
            )
        )

        assertEquals(360, command.pattern.size)
        assertEquals(5_070, command.pattern[0])
        assertEquals(2_140, command.pattern[1])

        // First byte = 0x11.
        // LSB đầu tiên là 1.
        assertEquals(370, command.pattern[2])
        assertEquals(1_780, command.pattern[3])

        // Bit kế tiếp là 0.
        assertEquals(370, command.pattern[4])
        assertEquals(710, command.pattern[5])

        // Kết thúc preamble rồi đến khoảng cách giữa 2 frame.
        assertEquals(370, command.pattern[114])
        assertEquals(29_410, command.pattern[115])

        // Header của frame thứ hai.
        assertEquals(5_070, command.pattern[116])
        assertEquals(2_140, command.pattern[117])
    }
}
