package com.khaiit.kremote.core.vault

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

object VaultCodec {
    fun encode(bundle: VaultBundle): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeByte(1)
            out.writeBoolean(bundle.tuya != null)
            bundle.tuya?.let { out.writeUTF(it.clientId); out.writeUTF(it.clientSecret); out.writeUTF(it.deviceId) }
            out.writeBoolean(bundle.hub != null)
            bundle.hub?.let { out.writeUTF(it.ipv4); out.writeUTF(it.apiKey) }
            out.writeBoolean(bundle.https != null)
            bundle.https?.let { out.writeUTF(it.host); out.writeInt(it.port); out.writeUTF(it.fingerprintHex) }
            out.writeBoolean(bundle.wol != null)
            bundle.wol?.let { out.writeUTF(it.mac); out.writeUTF(it.ip) }
            out.writeBoolean(bundle.labels != null)
            bundle.labels?.let { labels ->
                require(labels.size <= 6)
                out.writeInt(labels.size)
                labels.toSortedMap().forEach { (key, value) -> out.writeUTF(key); out.writeUTF(value) }
            }
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): VaultBundle = try {
        require(bytes.size <= 1_048_576)
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readUnsignedByte() == 1)
            fun present(): Boolean = when (input.readUnsignedByte()) {
                0 -> false
                1 -> true
                else -> throw VaultFormatException()
            }
            val tuya = if (present()) TuyaPart(input.readUTF(), input.readUTF(), input.readUTF()) else null
            val hub = if (present()) HubPart(input.readUTF(), input.readUTF()) else null
            val https = if (present()) HttpsPart(input.readUTF(), input.readInt(), input.readUTF()) else null
            val wol = if (present()) WolPart(input.readUTF(), input.readUTF()) else null
            val labels = if (present()) {
                val count = input.readInt()
                require(count in 0..6)
                val result = linkedMapOf<String, String>()
                repeat(count) {
                    val key = input.readUTF()
                    require(!result.containsKey(key))
                    result[key] = input.readUTF()
                }
                result
            } else null
            require(input.available() == 0)
            VaultBundle(tuya, hub, https, wol, labels)
        }
    } catch (_: Exception) {
        throw VaultFormatException()
    }
}
