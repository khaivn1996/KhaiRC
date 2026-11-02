package com.khaiit.kremote.feature.xiaomi

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * miIO v2 UDP transport, interoperable with python-miio:
 * hello -> device id/timestamp -> AES-CBC/PKCS7 JSON + MD5 integrity.
 * One attempt per command. NEVER silently retry set_properties.
 */
internal class MiioUdpClient(
    private val port: Int = 54321,
    private val timeoutMs: Int = 3500
) {
    fun send(ip: String, tokenHex: String, method: String, params: JSONArray): JSONArray {
        val token = MiioPacket.tokenBytes(tokenHex)
        val address = InetAddress.getByName(ip)
        val requestId = SecureRandom().nextInt(8000) + 1
        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMs
            socket.connect(address, port)
            val hello = MiioPacket.hello()
            socket.send(DatagramPacket(hello, hello.size))
            val helloReply = receive(socket)
            val header = MiioPacket.parseHello(helloReply)
            val json = JSONObject()
                .put("id", requestId)
                .put("method", method)
                .put("params", params)
                .toString()
            val packet = MiioPacket.encode(
                header.deviceId, header.timestamp + 1, token, json
            )
            socket.send(DatagramPacket(packet, packet.size))
            val replyBytes = receive(socket)
            val reply = MiioPacket.decode(replyBytes, token, header.deviceId)
            val response = JSONObject(reply)
            if (response.optInt("id", -1) != requestId) {
                throw IOException("miIO trả về ID không đúng")
            }
            if (response.has("error")) {
                val error = response.getJSONObject("error")
                throw IOException("Thiết bị trả về lỗi miIO: " + error.optInt("code", -1))
            }
            return response.optJSONArray("result")
                ?: throw IOException("Thiết bị không trả về kết quả MIoT")
        }
    }

    private fun receive(socket: DatagramSocket): ByteArray {
        val buffer = ByteArray(4096)
        val packet = DatagramPacket(buffer, buffer.size)
        socket.receive(packet)
        return packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
    }
}

internal data class MiioHello(val deviceId: Int, val timestamp: Int)

internal object MiioPacket {
    private const val HEADER = 16
    private const val CHECKSUM = 16
    private const val MAGIC = 0x2131

    fun tokenBytes(hex: String): ByteArray {
        require(hex.matches(Regex("[0-9a-fA-F]{32}"))) { "Token không hợp lệ" }
        return ByteArray(16) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    fun hello(): ByteArray = byteArrayOf(0x21, 0x31, 0x00, 0x20) +
        ByteArray(28) { 0xff.toByte() }

    fun parseHello(message: ByteArray): MiioHello {
        require(message.size == 32) { "Phản hồi handshake miIO sai độ dài" }
        val b = ByteBuffer.wrap(message).order(ByteOrder.BIG_ENDIAN)
        require((b.short.toInt() and 0xffff) == MAGIC && (b.short.toInt() and 0xffff) == 32) {
            "Phản hồi handshake miIO không hợp lệ"
        }
        b.int // unknown
        val id = b.int
        val ts = b.int
        require(message.copyOfRange(16, 32).all { it == 0xff.toByte() }) {
            "Checksum handshake miIO không hợp lệ"
        }
        return MiioHello(id, ts)
    }

    fun encode(deviceId: Int, timestamp: Int, token: ByteArray, json: String): ByteArray {
        require(token.size == 16)
        // python-miio appends a single NUL byte before AES-CBC PKCS7 padding.
        val plain = json.toByteArray(Charsets.UTF_8) + byteArrayOf(0)
        val encrypted = crypt(Cipher.ENCRYPT_MODE, token, plain)
        val size = HEADER + CHECKSUM + encrypted.size
        require(size <= 4096) { "Lệnh miIO quá dài" }
        val header = ByteBuffer.allocate(HEADER).order(ByteOrder.BIG_ENDIAN)
            .putShort(MAGIC.toShort()).putShort(size.toShort())
            .putInt(0).putInt(deviceId).putInt(timestamp).array()
        val digest = md5(header + token + encrypted)
        return header + digest + encrypted
    }

    fun decode(message: ByteArray, token: ByteArray, expectedDeviceId: Int): String {
        require(token.size == 16)
        require(message.size >= 48 && message.size <= 4096) {
            "Kích thước phản hồi miIO không hợp lệ"
        }
        val header = ByteBuffer.wrap(message, 0, HEADER).order(ByteOrder.BIG_ENDIAN)
        require((header.short.toInt() and 0xffff) == MAGIC) { "Sai magic miIO" }
        require((header.short.toInt() and 0xffff) == message.size) { "Sai chiều dài miIO" }
        header.int
        require(header.int == expectedDeviceId) { "Sai miIO device id" }
        header.int
        val encrypted = message.copyOfRange(32, message.size)
        require(encrypted.isNotEmpty() && encrypted.size % 16 == 0) { "Payload miIO sai cấu trúc" }
        val expected = md5(message.copyOfRange(0, HEADER) + token + encrypted)
        require(MessageDigest.isEqual(expected, message.copyOfRange(16, 32))) {
            "Checksum miIO không hợp lệ (token sai hoặc dữ liệu bị thay đổi)"
        }
        return crypt(Cipher.DECRYPT_MODE, token, encrypted)
            .toString(Charsets.UTF_8).trimEnd('\u0000')
    }

    private fun crypt(mode: Int, token: ByteArray, value: ByteArray): ByteArray {
        val key = md5(token)
        val iv = md5(key + token)
        return Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            doFinal(value)
        }
    }

    private fun md5(data: ByteArray): ByteArray = MessageDigest.getInstance("MD5").digest(data)
}
