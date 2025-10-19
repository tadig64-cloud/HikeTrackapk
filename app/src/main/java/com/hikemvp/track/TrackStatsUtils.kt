package com.hikemvp.track

import android.location.Location
import kotlin.math.max

/**
 * Utils de calcul pour:
 *  - temps en mouvement (hors arrêts)
 *  - vitesse moyenne de déplacement (moving average speed)
 *
 * ADDITIF, SANS IMPACT UI.
 * À appeler depuis votre service/VM où vous calculez déjà la distance.
 */
object TrackStatsUtils {
    /**
     * Calcule le temps en mouvement à partir d'une liste ordonnée de Location.
     * @param points Liste de points ordonnés par timestamp croissant.
     * @param speedThresholdMps Vitesse minimale pour être considéré "en mouvement" (m/s). Par défaut 0.5 m/s (~1.8 km/h).
     * @param minAccuracyMeters Précision max acceptable pour utiliser l'échantillon.
     */
    @JvmStatic
    fun computeMovingTimeMillis(
        points: List<Location>,
        speedThresholdMps: Double = 0.5,
        minAccuracyMeters: Float = 50f
    ): Long {
        if (points.size < 2) return 0L
        var movingMillis = 0L
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            // Filtrage précision
            if ((a.hasAccuracy() && a.accuracy > minAccuracyMeters) || (b.hasAccuracy() && b.accuracy > minAccuracyMeters)) {
                continue
            }
            val dt = max(0L, b.time - a.time)
            if (dt <= 0L) continue

            // Vitesse: priorité à Location.getSpeed() si dispo, sinon distance/dt
            val spd = when {
                b.hasSpeed() -> b.speed.toDouble() // m/s
                else -> {
                    val dist = a.distanceTo(b).toDouble() // m
                    dist / (dt / 1000.0)
                }
            }
            if (spd >= speedThresholdMps) {
                movingMillis += dt
            }
        }
        return movingMillis
    }

    /**
     * Vitesse moyenne de déplacement (hors arrêts) = distance totale / temps en mouvement.
     * @param totalDistanceMeters Distance cumulée (m).
     */
    @JvmStatic
    fun computeAvgMovingSpeedMps(totalDistanceMeters: Double, movingTimeMillis: Long): Double {
        if (movingTimeMillis <= 0L) return 0.0
        val movingSeconds = movingTimeMillis / 1000.0
        return totalDistanceMeters / movingSeconds // m/s
    }

    /** Conversions & formats utilitaires **/

    @JvmStatic
    fun mpsToKmh(mps: Double): Double = mps * 3.6

    @JvmStatic
    fun formatDurationHhMmSs(millis: Long): String {
        var seconds = (millis / 1000).toInt()
        val h = seconds / 3600
        seconds -= h * 3600
        val m = seconds / 60
        val s = seconds % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }

    @JvmStatic
    fun formatKmh(kmh: Double): String = String.format("%.1f km/h", kmh)
}