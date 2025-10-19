package com.hikemvp.storage

import android.content.Context
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline
import com.hikemvp.GpxUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Persistance simple en NDJSON (1 point / ligne) pour la trace active.
 * Chaque ligne: {"t":<epochMs>,"lat":..,"lon":..,"alt":<nullable>}
 */
object ActiveTrackRepo {

    private const val DIR_ACTIVE = "HikeTrack/active"
    private const val FILE_ACTIVE = "active_track.ndjson"

    private fun dir(context: Context): File {
        val base = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        return File(base, DIR_ACTIVE).apply { if (!exists()) mkdirs() }
    }
    private fun file(context: Context): File = File(dir(context), FILE_ACTIVE)

    fun hasActive(context: Context): Boolean = file(context).let { it.exists() && it.length() > 0 }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    fun append(
        context: Context,
        lat: Double,
        lon: Double,
        alt: Double?,
        timeMs: Long
    ) {
        val f = file(context)
        val jo = JSONObject()
            .put("t", timeMs)
            .put("lat", lat)
            .put("lon", lon)
        if (alt != null && !alt.isNaN()) jo.put("alt", alt)
        f.appendText(jo.toString() + "\n", Charsets.UTF_8)
    }

    /** Lit tous les points sous forme de GeoPoint (altitude quand dispo). */
    fun readAllGeoPoints(context: Context): List<GeoPoint> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        val pts = mutableListOf<GeoPoint>()
        f.forEachLine(Charsets.UTF_8) { line ->
            if (line.isBlank()) return@forEachLine
            runCatching {
                val o = JSONObject(line)
                val lat = o.getDouble("lat")
                val lon = o.getDouble("lon")
                val alt = if (o.has("alt")) o.optDouble("alt") else Double.NaN
                pts += if (alt.isNaN()) GeoPoint(lat, lon) else GeoPoint(lat, lon, alt)
            }
        }
        return pts
    }

    /** Renvoie le dernier point (ou null). */
    fun readLastPoint(context: Context): GeoPoint? = readAllGeoPoints(context).lastOrNull()

    /** Distance totale (m) recalculée depuis le fichier. */
    fun computeTotalDistanceM(context: Context): Double {
        val pts = readAllGeoPoints(context)
        if (pts.size < 2) return 0.0
        var d = 0.0
        for (i in 1 until pts.size) d += pts[i - 1].distanceToAsDouble(pts[i])
        return d
    }

    /** Exporte la trace active en GPX (renvoie le fichier ou null). */
    fun exportToGpx(context: Context, nameHint: String? = null): File? {
        val pts = readAllGeoPoints(context)
        if (pts.isEmpty()) return null
        val poly = Polyline().apply { setPoints(pts) }
        val gpx = GpxUtils.polylineToGpx(poly)

        val base = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val outDir = File(base, "HikeTrack/exports").apply { if (!exists()) mkdirs() }
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(java.util.Date())
        val out = File(outDir, (nameHint ?: "track_$ts") + ".gpx")
        runCatching {
            out.outputStream().use { os -> com.hikemvp.GpxUtils.saveToFile(gpx, os) }
        }.getOrElse { return null }
        return out
    }
}
