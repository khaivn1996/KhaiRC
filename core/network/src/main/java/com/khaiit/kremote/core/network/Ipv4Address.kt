package com.khaiit.kremote.core.network

object Ipv4Address {

    fun isValid(value: String): Boolean {

        val parts = value
            .trim()
            .split(".")

        if (parts.size != 4) {
            return false
        }

        return parts.all { part ->

            if (part.isEmpty()) {
                return@all false
            }

            if (part.length > 3) {
                return@all false
            }

            if (!part.all { it.isDigit() }) {
                return@all false
            }

            val number =
                part.toIntOrNull()
                    ?: return@all false

            number in 0..255
        }
    }
}