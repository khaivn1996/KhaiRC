package com.khaiit.kremote.core.vault

import java.util.Base64

data class SealedDoc(
    val iterations: Int,
    val salt: ByteArray,
    val iv: ByteArray,
    val ct: ByteArray,
    val schemaVersion: Int = 1,
    val kdfAlg: String = "PBKDF2-SHA256"
) {
    init {
        require(schemaVersion == 1 && kdfAlg == "PBKDF2-SHA256")
        require(iterations in 1..2_000_000 && salt.size == 16 && iv.size == 12)
        require(ct.size in 16..1_048_576)
    }

    fun toMap(): Map<String, Any> = mapOf(
        "schemaVersion" to schemaVersion,
        "kdfAlg" to kdfAlg,
        "iterations" to iterations,
        "salt" to Base64.getEncoder().encodeToString(salt),
        "iv" to Base64.getEncoder().encodeToString(iv),
        "ct" to Base64.getEncoder().encodeToString(ct)
    )

    override fun toString() = "SealedDoc(<redacted>)"

    companion object {
        fun fromMap(map: Map<String, Any>): SealedDoc = try {
            fun number(key: String): Int {
                val value = map[key] as Long
                require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
                return value.toInt()
            }
            fun bytes(key: String): ByteArray {
                val value = map[key] as String
                require(value.length <= 1_398_104)
                return Base64.getDecoder().decode(value)
            }
            SealedDoc(
                number("iterations"), bytes("salt"), bytes("iv"), bytes("ct"),
                number("schemaVersion"), map["kdfAlg"] as String
            )
        } catch (_: Exception) {
            throw VaultFormatException()
        }
    }
}
