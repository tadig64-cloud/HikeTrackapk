package com.hikemvp.waypoints

data class WaypointExt(
    val id: Long,
    var latitude: Double,
    var longitude: Double,
    var name: String,
    var note: String? = null,
    var group: String? = null,
    var timestamp: Long = System.currentTimeMillis(),
    val attachments: MutableList<String> = mutableListOf()
)

/** Conversions entre le modèle public (WaypointMeta) et le modèle étendu (WaypointExt). */
fun WaypointExt.toMeta(): WaypointMeta =
    WaypointMeta(id = id, latitude = latitude, longitude = longitude, name = name, note = note)

fun WaypointMeta.toExt(
    group: String? = null,
    timestamp: Long = System.currentTimeMillis()
): WaypointExt =
    WaypointExt(
        id = if (id == 0L) System.currentTimeMillis() else id,
        latitude = latitude,
        longitude = longitude,
        name = name,
        note = note,
        group = group,
        timestamp = timestamp,
        attachments = mutableListOf()
    )
