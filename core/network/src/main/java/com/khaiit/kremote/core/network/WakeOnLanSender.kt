package com.khaiit.kremote.core.network

interface WakeOnLanSender {

    suspend fun wake(
        macAddress: String,
        broadcastAddress: String,
        port: Int = 9
    )
}