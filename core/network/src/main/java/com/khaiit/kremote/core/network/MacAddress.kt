package com.khaiit.kremote.core.network

object MacAddress {

    fun normalize(value: String): String {
        return value
            .trim()
            .replace("-", ":")
            .uppercase()
    }

    fun isValid(value: String): Boolean {
        return try {
            parse(value)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    fun parse(value: String): ByteArray {
        val normalized = normalize(value)

        val parts = normalized.split(":")

        require(parts.size == 6) {
            "MAC Address không hợp lệ"
        }

        return ByteArray(6) { index ->

            val part = parts[index]

            require(part.length == 2) {
                "MAC Address không hợp lệ"
            }

            val number = part.toIntOrNull(16)
                ?: throw IllegalArgumentException(
                    "MAC Address không hợp lệ"
                )

            number.toByte()
        }
    }
}