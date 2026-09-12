package com.khaiit.kremote.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress

class AndroidWakeOnLanSender : WakeOnLanSender {

    override suspend fun wake(
        macAddress: String,
        broadcastAddress: String,
        port: Int
    ) = withContext(Dispatchers.IO) {

        require(port in 1..65535) {
            "UDP port không hợp lệ"
        }

        require(
            Ipv4Address.isValid(broadcastAddress)
        ) {
            "Broadcast Address không hợp lệ"
        }

        val macBytes =
            MacAddress.parse(macAddress)

        val destination =
            InetAddress.getByName(
                broadcastAddress.trim()
            )

        require(destination is Inet4Address) {
            "Wake-on-LAN hiện chỉ hỗ trợ IPv4"
        }

        val magicPacket =
            createMagicPacket(macBytes)

        DatagramSocket().use { socket ->

            socket.broadcast = true

            val packet =
                DatagramPacket(
                    magicPacket,
                    magicPacket.size,
                    destination,
                    port
                )

            repeat(3) { index ->

                socket.send(packet)

                if (index < 2) {
                    delay(80)
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

        // MAC Address lặp lại 16 lần
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