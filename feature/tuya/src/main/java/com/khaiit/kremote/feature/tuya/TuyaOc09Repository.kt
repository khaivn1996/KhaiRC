package com.khaiit.kremote.feature.tuya

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

interface TuyaOc09Repository {
    suspend fun readStatus(credentials: TuyaCredentials): Map<String, Boolean>
    suspend fun setPower(credentials: TuyaCredentials, code: String, enabled: Boolean)
    /** One POST carrying six independent OC09 switch commands; not a Tuya master DP. */
    suspend fun setAllPower(credentials: TuyaCredentials, enabled: Boolean)
    fun clearSession()
}

/** Cloud-only, Singapore region, no private API bridge. */
class TuyaCloudOc09Repository : TuyaOc09Repository {
    private val auth = TuyaAuthManager()

    override fun clearSession() = auth.invalidate()

    override suspend fun readStatus(credentials: TuyaCredentials): Map<String, Boolean> {
        val config = credentials.validated()
        val token = auth.token(config) { path -> perform(config, "GET", path, null, null) }
        val path = "/v1.0/iot-03/devices/${config.deviceId}/status"
        val result = perform(config, "GET", path, null, token).getJSONArray("result")
        val states = mutableMapOf<String, Boolean>()
        for (i in 0 until result.length()) {
            val entry = result.getJSONObject(i)
            val code = entry.optString("code")
            if (code in TuyaChannel.CODES) {
                val raw = entry.get("value")
                check(raw is Boolean) { "Tuya trả trạng thái không phải Boolean cho $code" }
                states[code] = raw
            }
        }
        check(TuyaChannel.CODES.all(states::containsKey)) { "Tuya chưa trả đủ trạng thái 6 kênh OC09" }
        return states
    }

    override suspend fun setPower(credentials: TuyaCredentials, code: String, enabled: Boolean) {
        require(code in TuyaChannel.CODES) { "Mã công tắc OC09 không được hỗ trợ" }
        sendPowerCommands(credentials, listOf(code), enabled)
    }

    override suspend fun setAllPower(credentials: TuyaCredentials, enabled: Boolean) {
        // Hardware support MUST be confirmed in Tuya API Explorer before enabling the UI.
        sendPowerCommands(credentials, TuyaChannel.ALL.map { it.code }, enabled)
    }

    private suspend fun sendPowerCommands(
        credentials: TuyaCredentials,
        codes: List<String>,
        enabled: Boolean
    ) {
        require(codes.isNotEmpty() && codes.distinct().size == codes.size && codes.all { it in TuyaChannel.CODES })
        val config = credentials.validated()
        val token = auth.token(config) { path -> perform(config, "GET", path, null, null) }
        val path = "/v1.0/iot-03/devices/${config.deviceId}/commands"
        val commands = JSONArray()
        codes.forEach { code -> commands.put(JSONObject().put("code", code).put("value", enabled)) }
        val body = JSONObject().put("commands", commands).toString().toByteArray(Charsets.UTF_8)
        val result = perform(config, "POST", path, body, token)
        check(result.optBoolean("result", false)) { "Tuya không xác nhận tiếp nhận lệnh" }
    }

    private suspend fun perform(
        config: TuyaCredentials,
        method: String,
        pathAndQuery: String,
        body: ByteArray?,
        token: String?
    ): JSONObject = withContext(Dispatchers.IO) {
        val payload = body ?: byteArrayOf()
        val timestamp = System.currentTimeMillis().toString()
        val nonce = UUID.randomUUID().toString().replace("-", "")
        val signature = TuyaRequestSigner.sign(
            clientId = config.clientId,
            clientSecret = config.clientSecret,
            method = method,
            pathAndQuery = pathAndQuery,
            body = payload,
            timestamp = timestamp,
            nonce = nonce,
            accessToken = token
        )
        val connection = (URL(BASE_URL + pathAndQuery).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = method
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("client_id", config.clientId)
            connection.setRequestProperty("sign", signature)
            connection.setRequestProperty("sign_method", "HMAC-SHA256")
            connection.setRequestProperty("t", timestamp)
            connection.setRequestProperty("nonce", nonce)
            connection.setRequestProperty("Accept", "application/json")
            token?.let { connection.setRequestProperty("access_token", it) }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(payload) }
            }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            val json = runCatching { JSONObject(response) }.getOrElse {
                throw IOException("Tuya HTTP $status: phản hồi không hợp lệ")
            }
            if (status !in 200..299 || !json.optBoolean("success", false)) {
                val code = json.opt("code")?.toString() ?: status.toString()
                val message = json.optString("msg", "Tuya API không thành công").take(140)
                throw IOException("Tuya $code: $message")
            }
            json
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val BASE_URL = "https://openapi-sg.iotbing.com"
    }
}
