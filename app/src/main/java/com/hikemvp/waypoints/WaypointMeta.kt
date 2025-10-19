package com.hikemvp.waypoints

/**
 * Modèle simple et rétro‑compatible pour un waypoint.
 * - id == 0L => un id sera généré lors de l'ajout (timestamp ms)
 * - name : nom affiché
 * - note : note optionnelle
 * - latitude / longitude en WGS84 (EPSG:4326)
 */
data class WaypointMeta(
    val id: Long = 0L,
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val note: String? = null
)
