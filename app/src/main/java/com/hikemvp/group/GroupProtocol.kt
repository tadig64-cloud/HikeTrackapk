
package com.hikemvp.group

import org.json.JSONObject

sealed class GroupWire {
    data class JoinRequest(val id: String, val name: String, val pin: String): GroupWire()
    data class JoinAck(val accepted: Boolean, val reason: String? = null): GroupWire()
    data class Position(val id: String, val lat: Double, val lon: Double, val acc: Float, val ts: Long): GroupWire()
    data class Leave(val id: String): GroupWire()

    companion object {
        private const val K_TYPE = "t"
        fun encode(msg: GroupWire): ByteArray {
            val o = JSONObject()
            when (msg) {
                is JoinRequest -> { o.put(K_TYPE, "jr"); o.put("id", msg.id); o.put("name", msg.name); o.put("pin", msg.pin) }
                is JoinAck -> { o.put(K_TYPE, "ja"); o.put("ok", msg.accepted); if (msg.reason != null) o.put("why", msg.reason) }
                is Position -> { o.put(K_TYPE, "pos"); o.put("id", msg.id); o.put("lat", msg.lat); o.put("lon", msg.lon); o.put("acc", msg.acc.toDouble()); o.put("ts", msg.ts) }
                is Leave -> { o.put(K_TYPE, "bye"); o.put("id", msg.id) }
            }
            return o.toString().encodeToByteArray()
        }
        fun decode(bytes: ByteArray): GroupWire? {
            return try {
                val o = JSONObject(String(bytes, Charsets.UTF_8))
                when (o.getString(K_TYPE)) {
                    "jr" -> JoinRequest(o.getString("id"), o.getString("name"), o.optString("pin", ""))
                    "ja" -> JoinAck(o.getBoolean("ok"), if (o.has("why")) o.getString("why") else null)
                    "pos" -> Position(o.getString("id"), o.getDouble("lat"), o.getDouble("lon"), o.getDouble("acc").toFloat(), o.getLong("ts"))
                    "bye" -> Leave(o.getString("id"))
                    else -> null
                }
            } catch (_: Throwable) { null }
        }
    }
}
