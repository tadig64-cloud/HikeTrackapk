package com.hikemvp.nature

import org.json.JSONObject

enum class NaturePoiType { TREE, SHELTER, PROTECTED }

data class NaturePoi(
    val id: String,
    val type: NaturePoiType,
    val name: String,
    val lat: Double,
    val lon: Double,
    val radiusM: Double? = null,
    val note: String? = null
) {
    companion object {
        fun fromJson(obj: JSONObject): NaturePoi {
            val id = obj.getString("id")
            val type = NaturePoiType.valueOf(obj.getString("type").uppercase())
            val name = obj.getString("name")
            val lat = obj.getDouble("lat")
            val lon = obj.getDouble("lon")
            val radius = if (obj.has("radiusM") && !obj.isNull("radiusM")) obj.getDouble("radiusM") else null
            val note = if (obj.has("note") && !obj.isNull("note")) obj.getString("note") else null
            return NaturePoi(id, type, name, lat, lon, radius, note)
        }
    }
}