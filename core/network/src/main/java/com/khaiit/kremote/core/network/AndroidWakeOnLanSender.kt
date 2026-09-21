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

        val unicastDestination =
            InetAddress.getByName(
                targetAddress.trim()
            )

        require(unicastDestination is Inet4Address) {
            "Wake-on-LAN hiện chỉ hỗ trợ IPv4"
        }

        /*
         * LAN hiện tại:
         *
         * 192.168.2.0/24
         *
         * nên broadcast address là:
         *
         * 192.168.2.255
         */
        val broadcastDestination =
            InetAddress.getByName(
                "192.168.2.255"
            )

        val magicPacket =
            createMagicPacket(macBytes)

        var unicastSuccess = false
        var broadcastSuccess = false

        /*
         * =========================================================
         * 1. UNICAST WOL
         *
         * Dùng chủ yếu khi đi qua WireGuard -> OpenWrt.
         *
         * OpenWrt có permanent neighbor:
         *
         * 192.168.2.19 -> D8:BB:C1:DC:2E:41
         *
         * nên vẫn gửi Ethernet frame tới PC được khi PC đang OFF.
         * =========================================================
         */
        runCatching {

            sendMagicPackets(
                magicPacket = magicPacket,
                destination = unicastDestination,
                port = port,
                broadcast = false
            )

        }.onSuccess {

            unicastSuccess = true
        }

        /*
         * =========================================================
         * 2. BROADCAST WOL
         *
         * Dùng khi điện thoại đang ở trực tiếp trong LAN nhà.
         *
         * Không cần OpenWrt giữ ARP / permanent neighbor.
         *
         * Nếu đang ở ngoài nhà qua WireGuard thì packet broadcast
         * có thể bị drop. Điều đó không sao vì unicast phía trên
         * vẫn xử lý WOL qua OpenWrt.
         * =========================================================
         */
        runCatching {

            sendMagicPackets(
                magicPacket = magicPacket,
                destination = broadcastDestination,
                port = port,
                broadcast = true
            )

        }.onSuccess {

            broadcastSuccess = true
        }

        /*
         * Chỉ báo lỗi nếu CẢ HAI đường đều không gửi được.
         */
        check(
            unicastSuccess || broadcastSuccess
        ) {
            "Không thể gửi Wake-on-LAN qua unicast hoặc broadcast"
        }
    }

    private suspend fun sendMagicPackets(
        magicPacket: ByteArray,
        destination: InetAddress,
        port: Int,
        broadcast: Boolean
    ) {

        DatagramSocket().use { socket ->

            /*
             * Bắt buộc bật broadcast khi gửi tới
             * 192.168.2.255.
             */
            socket.broadcast = broadcast

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
             *
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