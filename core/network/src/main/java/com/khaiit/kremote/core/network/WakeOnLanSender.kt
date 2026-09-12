package com.khaiit.kremote.core.network

interface WakeOnLanSender {

    suspend fun wake(
        macAddress: String,
        targetAddress: String,
        port: Int = 9
    )
}