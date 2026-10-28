package com.khaiit.kremote.feature.ac

/**
 * Encoder for the particular Toshiba handset captured for KhaiRC.
 *
 * Observed data (COOL 24 C):
 * - Power/state: 9 bytes / 72 bits, sent twice.
 * - FIX: 7 bytes / 56 bits, sent once.
 * - SWING ON/OFF: 7 bytes / 56 bits, sent twice.
 * - Each byte is transmitted least-significant bit first.
 * - Checksum is XOR of all preceding bytes, verified for these captures.
 *
 * Carrier frequency was NOT measured by the YL-55 receiver. 38 kHz is a
 * provisional test value, not a verified specification of the remote.
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

    /** The captured power-off command is fixed, including when a different fan speed was selected. */
    fun buildPowerOrCoolFrame(state: ToshibaAcState): ByteArray {
        val operation = if (state.power) {
            state.fanSpeed.protocolByte
        } else {
            POWER_OFF_COMMAND
        }
        return withXorChecksum(
            0x4F, 0xB0, 0xC0, 0x3F, 0x80, 0x0E, operation, 0x00
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

    /** Android ConsumerIrManager expects alternating positive MARK / SPACE durations in us. */
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
            result += BIT_MARK_US // stop mark
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
        // Provisional carrier frequency; check on the real AC before relying on it.
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
