package com.khaiit.kremote.feature.xiaomi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

interface XiaomiPlugRepository {
    suspend fun readPower(config: XiaomiPlugConfig): Boolean
    suspend fun setPower(config: XiaomiPlugConfig, on: Boolean)
}

class XiaomiLanPlugRepository(
    private val client: MiioUdpClient = MiioUdpClient()
) : XiaomiPlugRepository {
    override suspend fun readPower(config: XiaomiPlugConfig): Boolean = withContext(Dispatchers.IO) {
        val valid = config.validated()
        val params = JSONArray().put(
            JSONObject().put("did", "2-1").put("siid", 2).put("piid", 1)
        )
        val rows = client.send(valid.ip, valid.token, "get_properties", params)
        val row = findSwitchResult(rows)
        if (row.optInt("code", -1) != 0 || !row.has("value")) {
            throw IOException("Không đọc được trạng thái công tắc nguồn chính")
        }
        val value = row.get("value")
        if (value !is Boolean) throw IOException("Trạng thái nguồn không phải Boolean")
        value
    }

    override suspend fun setPower(config: XiaomiPlugConfig, on: Boolean): Unit =
        withContext(Dispatchers.IO) {
            val valid = config.validated()
            val params = JSONArray().put(
                JSONObject().put("did", "2-1").put("siid", 2).put("piid", 1)
                    .put("value", on)
            )
            // Never retry: an OFF may disconnect the VPN before an acknowledgement arrives.
            val rows = client.send(valid.ip, valid.token, "set_properties", params)
            val row = findSwitchResult(rows)
            if (row.optInt("code", -1) != 0) {
                throw IOException("Thiết bị từ chối lệnh: " + row.optInt("code", -1))
            }
        }

    private fun findSwitchResult(rows: JSONArray): JSONObject {
        for (index in 0 until rows.length()) {
            val item = rows.optJSONObject(index) ?: continue
            if (item.optInt("siid", -1) == 2 && item.optInt("piid", -1) == 1) return item
        }
        throw IOException("Thiếu kết quả MIoT cho công tắc 2-1")
    }
}
