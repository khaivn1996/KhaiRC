package com.khaiit.kremote.feature.ac.toshiba

/**
 * Encoder for the particular Toshiba handset captured for KhaiRC.
 *
 * Captured COOL 24 C: 4F B0 C0 3F 80 0E 80 00 0E (FAN AUTO).
 * The captured bytes are LSB-first representations. Reversing the bits in
 * each byte gives the standard Toshiba AC layout F2 0D 03 FC 01 70 01 00 70.
 * Standard Toshiba AC: byte[5] high nibble = temperature - 17 (17..30 C).
 * As KhaiRC's encoder already emits LSB-first bytes, byte[5] in our frame is
 * the bit reverse of ((temperature - 17) << 4).
 *
 * This field mapping matches the 24 C capture and published Toshiba AC
 * protocol. Temperatures other than 24 C still require a real-device test.
 *
 * - Power/state: 9 bytes / 72 bits, sent twice.
 * - FIX: 7 bytes / 56 bits, sent once.
 * - SWING ON/OFF: 7 bytes / 56 bits, sent twice.
 * - All transmitted bytes are LSB-first.
 * - Checksum is XOR of all preceding bytes.
 * - 38 kHz remains a provisional carrier frequency.
 */
class ToshibaAcEncoder(
    private val frequencyHz: Int = DEFAULT_FREQUENCY_HZ
) {
    init {
        require(frequencyHz > 0) { "Frequency must be positive" }
    }

    data class EncodedCommand(
        val frequencyHz: Int,
        val pattern: IntArray,
        val frame: ByteArray,
        val repeatCount: Int
    )

    fun encodePowerOrCool(state: ToshibaAcState): EncodedCommand =
        encode(buildPowerOrCoolFrame(state), STATE_REPEATS)

    fun encodeFix(): EncodedCommand =
        encode(buildShortFrame(FIX_COMMAND), FIX_REPEATS)

    fun encodeSwingOn(): EncodedCommand =
        encode(buildShortFrame(SWING_ON_COMMAND), SWING_REPEATS)

    fun encodeSwingOff(): EncodedCommand =
        encode(buildShortFrame(SWING_OFF_COMMAND), SWING_REPEATS)

    fun buildPowerOrCoolFrame(state: ToshibaAcState): ByteArray {
        val operation = if (state.power) {
            state.fanSpeed.protocolByte
        } else {
            POWER_OFF_COMMAND
        }

        // Original capture at 24 C: 0x0E. Keep the same bit order as all
        // other KhaiRC Toshiba bytes, including POWER OFF.
        val temperatureByte = Integer.reverse(
            (state.temperatureC - ToshibaAcState.MIN_TEMPERATURE_C) shl 4
        ) ushr 24

        return withXorChecksum(
            0x4F, 0xB0, 0xC0, 0x3F, 0x80, temperatureByte, operation, 0x00
        )
    }

    fun buildFixFrame(): ByteArray = buildShortFrame(FIX_COMMAND)
    fun buildSwingOnFrame(): ByteArray = buildShortFrame(SWING_ON_COMMAND)
    fun buildSwingOffFrame(): ByteArray = buildShortFrame(SWING_OFF_COMMAND)

    private fun buildShortFrame(command: Int): ByteArray =
        withXorChecksum(0x4F, 0xB0, 0x80, 0x7F, 0x84, command)

    private fun encode(frame: ByteArray, repeatCount: Int): EncodedCommand =
        EncodedCommand(
            frequencyHz = frequencyHz,
            pattern = buildPattern(frame, repeatCount),
            frame = frame,
            repeatCount = repeatCount
        )

    /** Android ConsumerIrManager uses alternating positive MARK/SPACE microseconds. */
    fun buildPattern(frame: ByteArray, repeatCount: Int): IntArray {
        require(frame.size == STATE_FRAME_BYTES || frame.size == SHORT_FRAME_BYTES) {
            "Expected a 9-byte state frame or a 7-byte short frame"
        }
        require(repeatCount in 1..3) { "repeatCount must be between 1 and 3" }

        val entriesPerFrame = 2 + frame.size * 8 * 2 + 2
        val result = ArrayList<Int>(entriesPerFrame * repeatCount)
        repeat(repeatCount) { frameIndex ->
            result += HEADER_MARK_US
            result += HEADER_SPACE_US
            for (byte in frame) {
                val value = byte.toInt() and 0xFF
                for (bit in 0 until 8) {
                    result += BIT_MARK_US
                    result += if ((value and (1 shl bit)) != 0) {
                        ONE_SPACE_US
                    } else {
                        ZERO_SPACE_US
                    }
                }
            }
            result += BIT_MARK_US
            result += if (frameIndex < repeatCount - 1) {
                INTER_FRAME_SPACE_US
            } else {
                TERMINAL_SPACE_US
            }
        }
        return result.toIntArray()
    }

    private fun withXorChecksum(vararg payload: Int): ByteArray {
        var xor = 0
        val result = ByteArray(payload.size + 1)
        payload.forEachIndexed { index, value ->
            require(value in 0..255)
            result[index] = value.toByte()
            xor = xor xor value
        }
        result[payload.size] = xor.toByte()
        return result
    }

    companion object {
        const val DEFAULT_FREQUENCY_HZ = 38_000
        const val HEADER_MARK_US = 4_350
        const val HEADER_SPACE_US = 4_300
        const val BIT_MARK_US = 550
        const val ONE_SPACE_US = 1_600
        const val ZERO_SPACE_US = 550
        const val INTER_FRAME_SPACE_US = 7_350
        const val TERMINAL_SPACE_US = 550

        const val STATE_FRAME_BYTES = 9
        const val SHORT_FRAME_BYTES = 7
        const val STATE_REPEATS = 2
        const val FIX_REPEATS = 1
        const val SWING_REPEATS = 2

        private const val POWER_OFF_COMMAND = 0xE0
        private const val FIX_COMMAND = 0x00
        private const val SWING_ON_COMMAND = 0x80
        private const val SWING_OFF_COMMAND = 0x40
    }
}
