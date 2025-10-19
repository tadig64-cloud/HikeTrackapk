package com.hikemvp.history

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Modèles pour l'historique des randos
 */
data class HikeRecord(
    val id: String = UUID.randomUUID().toString(),
    val dateUtcMillis: Long,          // fin d'activité (UTC epoch millis)
    val distanceMeters: Double,
    val elevationUpMeters: Double,
    val durationSeconds: Long,
    val notes: String = "",
    val photos: List<String> = emptyList(), // chemins internes ou Uris persistés
    val geojsonPath: String? = null         // tracé exporté si dispo
)

// --- JSON helpers ---
internal fun HikeRecord.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("dateUtcMillis", dateUtcMillis)
    put("distanceMeters", distanceMeters)
    put("elevationUpMeters", elevationUpMeters)
    put("durationSeconds", durationSeconds)
    put("notes", notes)
    put("photos", JSONArray(photos))
    put("geojsonPath", geojsonPath)
}

internal fun JSONObject.toHikeRecord(): HikeRecord = HikeRecord(
    id = optString("id"),
    dateUtcMillis = optLong("dateUtcMillis", 0L),
    distanceMeters = optDouble("distanceMeters", 0.0),
    elevationUpMeters = optDouble("elevationUpMeters", 0.0),
    durationSeconds = optLong("durationSeconds", 0L),
    notes = optString("notes", ""),
    photos = (optJSONArray("photos") ?: JSONArray()).let { ja ->
        List(ja.length()) { i -> ja.optString(i) }
    },
    geojsonPath = if (isNull("geojsonPath")) null else optString("geojsonPath", null)
)
