package com.khaiit.kremote.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import kotlin.time.Duration.Companion.milliseconds

class AndroidWakeOnLanSender : WakeOnLanSender {

    override suspend fun wake(
        macAddress: String,
        targetAddress: String,
        port: Int
    ) = withContext(Dispatchers.IO) {

        require(port in 1..65535) {
            "UDP port không hợp lệ"
        }

        require(
            Ipv4Address.isValid(targetAddress)
        ) {
            "Target IP không hợp lệ"
        }

        val macBytes =
            MacAddress.parse(macAddress)

        val destination =
            InetAddress.getByName(
                targetAddress.trim()
            )

        require(destination is Inet4Address) {
            "Wake-on-LAN hiện chỉ hỗ trợ IPv4"
        }

        val magicPacket =
            createMagicPacket(macBytes)

        DatagramSocket().use { socket ->

            val packet =
                DatagramPacket(
                    magicPacket,
                    magicPacket.size,
                    destination,
                    port
                )

            /*
             * Gửi 3 lần để tăng độ tin cậy.
             *
             * Magic Packet:
             * 6 byte FF
             * +
             * MAC Address lặp 16 lần
             *
             * Tổng cộng 102 byte.
             */
            repeat(3) { index ->

                socket.send(packet)

                if (index < 2) {
                    delay(80.milliseconds)
                }
            }
        }
    }

    private fun createMagicPacket(
        macAddress: ByteArray
    ): ByteArray {

        require(macAddress.size == 6) {
            "MAC Address phải có 6 byte"
        }

        val packet =
            ByteArray(
                6 + 16 * macAddress.size
            )

        // 6 byte FF đầu tiên
        for (i in 0 until 6) {
            packet[i] =
                0xFF.toByte()
        }

        // MAC Address lặp 16 lần
        for (i in 0 until 16) {

            System.arraycopy(
                macAddress,
                0,
                packet,
                6 + i * macAddress.size,
                macAddress.size
            )
        }

        return packet
    }
}