package com.khaiit.kremote.feature.ac

class DaikinBrcEncoder(
    private val profile: DaikinBrcProfile =
        DaikinBrcProfile.BRC4C153
) {

    data class EncodedCommand(
        val frequencyHz: Int,
        val pattern: IntArray,
        val frame: ByteArray,
        val profileName: String
    )

    fun encode(state: DaikinAcState,modeButtonPressed: Boolean = false): EncodedCommand {
        val frame = buildFrame(
            state = state,
            modeButtonPressed = modeButtonPressed
        )
        val pattern = buildPattern(frame)

        return EncodedCommand(
            frequencyHz = profile.frequencyHz,
            pattern = pattern,
            frame = frame,
            profileName = profile.name
        )
    }

    fun buildFrame(
        state: DaikinAcState,
        modeButtonPressed: Boolean = false
    ): ByteArray {
        val frame = byteArrayOf(
            0x11, 0xDA.toByte(), 0x17, 0x18, 0x04, 0x00, 0x00,
            0x11, 0xDA.toByte(), 0x17, 0x18, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x20, 0x00
        )

        frame[12] = alternateMode(state.mode).toByte()
        frame[13] =
            if (modeButtonPressed) {
                0x04
            } else {
                0x00
            }
        frame[14] = operationMode(state).toByte()
        frame[17] = encodedTemperature(state).toByte()
        frame[18] = fanAndSwing(state).toByte()

        frame[6] = checksum(frame, 0, 5).toByte()
        frame[21] = checksum(frame, 7, 20).toByte()

        return frame
    }

    private fun alternateMode(mode: DaikinMode): Int = when (mode) {
        DaikinMode.DRY -> 0x23
        DaikinMode.FAN -> 0x63
        DaikinMode.AUTO,
        DaikinMode.COOL,
        DaikinMode.HEAT -> 0x73
    }

    private fun operationMode(state: DaikinAcState): Int {

        /*
         * BRC4CXXX:
         * OFF = operation byte 0x00.
         */
        if (!state.power) {
            return 0x00
        }

        val mode = when (state.mode) {
            DaikinMode.FAN -> 0x00
            DaikinMode.HEAT -> 0x10
            DaikinMode.COOL -> 0x20
            DaikinMode.AUTO -> 0x30
            DaikinMode.DRY -> 0x70
        }

        /*
         * Bit 0 = Power ON.
         */
        return mode or 0x01
    }

    private fun encodedTemperature(state: DaikinAcState): Int {
        return when (state.mode) {
            DaikinMode.DRY,
            DaikinMode.FAN -> (17 - 9) shl 1

            else -> (state.temperatureC - 9) shl 1
        }
    }

    private fun fanAndSwing(state: DaikinAcState): Int {
        val fan = when (state.fanSpeed) {
            DaikinFanSpeed.LOW -> 0x10
            DaikinFanSpeed.HIGH -> 0x30
        }

        val swing = if (state.swing) 0x05 else 0x06

        return fan or swing
    }

    private fun checksum(
        bytes: ByteArray,
        start: Int,
        endInclusive: Int
    ): Int {
        var sum = 0

        for (index in start..endInclusive) {
            sum = (sum + bytes[index].toUByte().toInt()) and 0xFF
        }

        return sum
    }

    private fun buildPattern(frame: ByteArray): IntArray {
        require(frame.size == FRAME_SIZE) {
            "Daikin BRC frame must contain $FRAME_SIZE bytes"
        }

        val durations = ArrayList<Int>(PATTERN_CAPACITY)

        appendFrame(
            durations = durations,
            bytes = frame,
            start = 0,
            length = PREAMBLE_SIZE,
            endingSpaceUs = profile.interFrameSpaceUs
        )

        appendFrame(
            durations = durations,
            bytes = frame,
            start = PREAMBLE_SIZE,
            length = STATE_FRAME_SIZE,
            endingSpaceUs = profile.terminalSpaceUs
        )

        return durations.toIntArray()
    }

    private fun appendFrame(
        durations: MutableList<Int>,
        bytes: ByteArray,
        start: Int,
        length: Int,
        endingSpaceUs: Int
    ) {
        durations += profile.headerMarkUs
        durations += profile.headerSpaceUs

        for (index in start until start + length) {
            val value = bytes[index].toUByte().toInt()

            for (bit in 0 until 8) {
                durations += profile.bitMarkUs

                val isOne = (value and (1 shl bit)) != 0
                durations += if (isOne) {
                    profile.oneSpaceUs
                } else {
                    profile.zeroSpaceUs
                }
            }
        }

        durations += profile.bitMarkUs
        durations += endingSpaceUs
    }

    companion object {
        const val FRAME_SIZE = 22
        const val PREAMBLE_SIZE = 7
        const val STATE_FRAME_SIZE = 15
        const val PATTERN_CAPACITY = 360
    }
}
