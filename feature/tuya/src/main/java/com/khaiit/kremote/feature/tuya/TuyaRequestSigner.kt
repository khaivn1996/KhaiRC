package com.khaiit.kremote.feature.tuya

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Tuya Cloud Authorization (not App Authorization) signature scheme. */
internal object TuyaRequestSigner {
    fun bodySha256(body: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(body), false)

    /**
     * optionalSignatureKey is empty in KhaiRC: no custom Signature-Headers are sent.
     * If populated for a documented test vector, its trailing newline is part of the data.
     */
    fun sign(
        clientId: String,
        clientSecret: String,
        method: String,
        pathAndQuery: String,
        body: ByteArray,
        timestamp: String,
        nonce: String,
        accessToken: String? = null,
        optionalSignatureKey: String = ""
    ): String {
        require(method in setOf("GET", "POST"))
        require(pathAndQuery.startsWith("/"))
        val toSign = "$method\n${bodySha256(body)}\n$optionalSignatureKey\n$pathAndQuery"
        val material = clientId + (accessToken ?: "") + timestamp + nonce + toSign
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(clientSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return hex(mac.doFinal(material.toByteArray(Charsets.UTF_8)), true)
    }

    private fun hex(bytes: ByteArray, upper: Boolean): String {
        val digits = if (upper) "0123456789ABCDEF" else "0123456789abcdef"
        val out = CharArray(bytes.size * 2)
        bytes.forEachIndexed { i, b ->
            val value = b.toInt() and 0xff
            out[2 * i] = digits[value ushr 4]
            out[2 * i + 1] = digits[value and 15]
        }
        return String(out)
    }
}
