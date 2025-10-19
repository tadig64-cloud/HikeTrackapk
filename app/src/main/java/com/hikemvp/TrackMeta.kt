package com.hikemvp
import org.json.JSONObject
data class TrackMeta(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "Trace",
    val label: String = "Trace",
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
    val colorArgb: Int = 0xFFFF0000.toInt(),
    val durationMs: Long? = null,
    val movingTimeMs: Long? = null,
    val startTimeMs: Long? = null,
    val endTimeMs: Long? = null,
) {
    val createdAtHuman: String get() = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(createdAtMs))
    fun toJson(): String = JSONObject().apply {
        put("id", id); put("name", name); put("label", label)
        put("createdAtMs", createdAtMs); put("updatedAtMs", updatedAtMs)
        put("colorArgb", colorArgb)
        put("durationMs", durationMs); put("movingTimeMs", movingTimeMs)
        put("startTimeMs", startTimeMs); put("endTimeMs", endTimeMs)
    }.toString()
    companion object {
        fun fromJson(s: String): TrackMeta = try {
            val o = JSONObject(s)
            TrackMeta(
                id = o.optString("id", java.util.UUID.randomUUID().toString()),
                name = o.optString("name", "Trace"),
                label = o.optString("label", "Trace"),
                createdAtMs = o.optLong("createdAtMs", System.currentTimeMillis()),
                updatedAtMs = o.optLong("updatedAtMs", System.currentTimeMillis()),
                colorArgb = o.optInt("colorArgb", 0xFFFF0000.toInt()),
                durationMs = if (o.has("durationMs")) o.optLong("durationMs") else null,
                movingTimeMs = if (o.has("movingTimeMs")) o.optLong("movingTimeMs") else null,
                startTimeMs = if (o.has("startTimeMs")) o.optLong("startTimeMs") else null,
                endTimeMs = if (o.has("endTimeMs")) o.optLong("endTimeMs") else null,
            )
        } catch (e: Exception) { TrackMeta() }
    }
}
