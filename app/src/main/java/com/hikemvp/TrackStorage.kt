package com.hikemvp

import kotlin.math.*

/**
 * Calcul des stats sur une trace — version corrigée et stable (aucun impact UI).
 * API identique à l'ancienne pour éviter les régressions.
 */
object TrackStorage {

    data class Stats(
        val distanceMeters: Double = 0.0,
        val altMin: Double? = null,
        val altMax: Double? = null,
        // Ajouts (rétro-compatibles via valeurs par défaut)
        val gain: Double? = null,
        val loss: Double? = null,
        val durationMsTotal: Long? = null,
        val durationMsMoving: Long? = null,
        val avgSpeedKmh: Double? = null,
        val movingAvgSpeedKmh: Double? = null,
        val pointsCount: Int = 0
    )

    /** Calcule des stats à partir d'une liste de TrackPoint (voir GpxUtils.TrackPoint). */
    fun computeStats(
        points: List<GpxUtils.TrackPoint>,
        elevationSmoothingWindow: Int = 5,
        elevationChangeThresholdMeters: Double = 1.0,
        movingSpeedThresholdMps: Double = 0.5
    ): Stats {
        val n = points.size
        if (n < 2) return Stats(pointsCount = n)

        // Distance
        var distance = 0.0
        for (i in 1 until n) {
            val p0 = points[i - 1]
            val p1 = points[i]
            distance += haversineMeters(p0.lat, p0.lon, p1.lat, p1.lon)
        }

        // Élévations lissées (tolère null/NaN)
        val rawEle: List<Double?> = points.map { it.ele }
        val smoothedEle: List<Double?> = smoothElevations(rawEle, elevationSmoothingWindow)

        // Alt min/max + D+/D- robustes
        var altMin: Double? = null
        var altMax: Double? = null
        var gain = 0.0
        var loss = 0.0
        var prevEle: Double? = smoothedEle.firstOrNull()

        for (i in 0 until n) {
            val e = smoothedEle[i]
            if (e != null && !e.isNaN()) {
                altMin = if (altMin == null) e else min(altMin!!, e)
                altMax = if (altMax == null) e else max(altMax!!, e)
            }
            if (e != null && !e.isNaN() && prevEle != null && !prevEle.isNaN()) {
                val delta = e - prevEle
                if (delta > elevationChangeThresholdMeters) gain += delta
                else if (delta < -elevationChangeThresholdMeters) loss += -delta
            }
            prevEle = e
        }

        // Durées
        val firstTs = points.firstOrNull { it.timeEpochMs != null }?.timeEpochMs
        val lastTs  = points.asReversed().firstOrNull { it.timeEpochMs != null }?.timeEpochMs
        val durationTotal: Long? = if (firstTs != null && lastTs != null && lastTs >= firstTs) lastTs - firstTs else null

        // Durée en mouvement (seuil de vitesse instantanée)
        var durationMoving = 0L
        for (i in 1 until n) {
            val p0 = points[i - 1]
            val p1 = points[i]
            val t0 = p0.timeEpochMs
            val t1 = p1.timeEpochMs
            if (t0 != null && t1 != null && t1 > t0) {
                val d = haversineMeters(p0.lat, p0.lon, p1.lat, p1.lon)
                val speed = d / ((t1 - t0) / 1000.0)
                if (speed >= movingSpeedThresholdMps) {
                    durationMoving += (t1 - t0)
                }
            }
        }
        val movingMs: Long? = if (durationMoving > 0) durationMoving else null

        // Vitesses
        val avgSpeedKmh = durationTotal?.let { ms ->
            val h = ms / 3600000.0
            if (h > 0) (distance / 1000.0) / h else null
        }
        val movingAvgSpeedKmh = movingMs?.let { ms ->
            val h = ms / 3600000.0
            if (h > 0) (distance / 1000.0) / h else null
        }

        return Stats(
            distanceMeters = distance,
            altMin = altMin,
            altMax = altMax,
            gain = if (gain > 0.0) gain else 0.0,
            loss = if (loss > 0.0) loss else 0.0,
            durationMsTotal = durationTotal,
            durationMsMoving = movingMs,
            avgSpeedKmh = avgSpeedKmh,
            movingAvgSpeedKmh = movingAvgSpeedKmh,
            pointsCount = n
        )
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    /** Lissage simple par moyenne glissante (ignore les null). */
    private fun smoothElevations(values: List<Double?>, window: Int): List<Double?> {
        if (window <= 1) return values
        val half = window / 2
        return values.indices.map { i ->
            var sum = 0.0
            var cnt = 0
            for (k in (i - half)..(i + half)) {
                if (k >= 0 && k < values.size) {
                    val v = values[k]
                    if (v != null && !v.isNaN()) {
                        sum += v
                        cnt++
                    }
                }
            }
            if (cnt > 0) sum / cnt else values[i]
        }
    }
}
