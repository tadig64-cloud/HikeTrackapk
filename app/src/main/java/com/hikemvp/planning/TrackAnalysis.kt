package com.hikemvp.planning

import kotlin.math.*
import org.osmdroid.util.GeoPoint
import com.hikemvp.Stats

/**
 * Outils d'analyse d'itinéraire — cœur de l'app.
 * Minimal et non intrusif : ne perturbe pas les autres modules.
 */
object TrackAnalysis {

    /** Calcule les stats de base à partir d'une liste de points. */
    fun computeStats(points: List<GeoPoint>): Stats {
        if (points.isEmpty()) {
            return Stats(
                distanceM = 0.0, gainM = 0.0, lossM = 0.0,
                altMin = null, altMax = null,
                durationMsTotal = null, durationMsMoving = null,
                avgSpeedKmh = null, movingAvgSpeedKmh = null,
                pointsCount = 0
            )
        }
        var distanceM = 0.0
        var gainM = 0.0
        var lossM = 0.0
        var altMin: Double? = null
        var altMax: Double? = null

        var prev = points.first()
        altMin = prev.altitude
        altMax = prev.altitude

        for (i in 1 until points.size) {
            val cur = points[i]
            distanceM += haversineMeters(prev, cur)

            // Altitude
            val a1 = prev.altitude
            val a2 = cur.altitude
            if (!a1.isNaN() && !a2.isNaN()) {
                val delta = a2 - a1
                if (delta > 0) gainM += delta else lossM += -delta
                altMin = min(altMin ?: a2, min(a1, a2))
                altMax = max(altMax ?: a2, max(a1, a2))
            }
            prev = cur
        }
        return Stats(
            distanceM = distanceM, gainM = gainM, lossM = lossM,
            altMin = altMin, altMax = altMax,
            durationMsTotal = null, durationMsMoving = null,
            avgSpeedKmh = null, movingAvgSpeedKmh = null,
            pointsCount = points.size
        )
    }

    /** Estimation simple de durée (heuristique légère). */
    fun estimateDurationMillis(stats: Stats): Long {
        val basePaceMsPerKm = 12 * 60 * 1000L      // 12 min / km
        val gainPenaltyPer100m = 6 * 60 * 1000L    // +6 min par 100 m D+
        val km = stats.distanceM / 1000.0
        val penalty = (stats.gainM / 100.0) * gainPenaltyPer100m
        return (km * (basePaceMsPerKm + penalty)).toLong()
    }

    // ---- Helpers d'affichage ----

    fun formatDistance(km: Double): String {
        return if (km < 1.0) {
            val m = (km * 1000).roundToInt()
            "$m m"
        } else {
            val v = (km * 10.0).roundToInt() / 10.0
            "${v} km"
        }
    }

    fun formatElevation(meters: Double): String {
        val m = meters.roundToInt()
        return "$m m"
    }

    fun formatDuration(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    // ---- Géodésie ----
    private fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
        val R = 6371000.0 // rayon terre en m
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)

        val sinDLat = sin(dLat / 2)
        val sinDLon = sin(dLon / 2)
        val h = sinDLat * sinDLat + cos(lat1) * cos(lat2) * sinDLon * sinDLon
        val c = 2 * atan2(sqrt(h), sqrt(1 - h))
        return R * c
    }
}
