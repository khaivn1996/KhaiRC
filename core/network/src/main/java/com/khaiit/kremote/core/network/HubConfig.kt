package com.khaiit.kremote.core.network

import java.io.IOException

data class HubEndpoint(val ipv4: String) {
    init {
        require(Ipv4Address.isValid(ipv4) && ipv4 == ipv4.trim()) { "Địa chỉ Hub phải là IPv4 LAN" }
        val parts = ipv4.split('.')
        require(parts.all { part ->
            part.all { it in '0'..'9' } && (part.length == 1 || part[0] != '0')
        }) { "IPv4 phải dùng dạng thập phân chuẩn" }
        val octets = parts.map(String::toInt)
        require(octets[0] == 10 ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168)) { "Chỉ hỗ trợ IPv4 private trong LAN" }
        // Without a subnet mask we cannot detect every subnet-directed broadcast.
        // Reject the network/broadcast endpoints of the three supported private blocks.
        require(ipv4 !in setOf("10.0.0.0", "10.255.255.255", "172.16.0.0",
            "172.31.255.255", "192.168.0.0", "192.168.255.255")) { "Cần địa chỉ unicast của Hub" }
    }
}

class HubConfig(val endpoint: HubEndpoint, val apiKey: String) {
    init {
        require(apiKey.length == 64 && apiKey.all { it in ' '..'~' }) {
            "API key phải có 64 ký tự ASCII in được"
        }
    }

    override fun toString(): String = "HubConfig(endpoint=$endpoint, apiKey=<redacted>)"
}

/** Milife only exposes state reads. No write method exists on this interface. */
interface MilifeReadApi {
    suspend fun readMilife(config: HubConfig): Result<Boolean>
}

/** Only a fixed, safe code crosses the client boundary; never attach raw HTTP exceptions. */
class HubClientException(val code: String) : IOException(code)
