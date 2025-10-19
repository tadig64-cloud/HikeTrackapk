package com.hikemvp.planning

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Time estimation models implemented as pure functions.
 * Keeps existing API (estimate* -> hours as Double) and adds millis-returning helpers.
 */
object TimeModels {

    // ---------------- Existing functions (kept for compatibility) ----------------

    /**
     * Naismith (base 5 km/h) + Langmuir (descent neutral without per-slope segmentation).
     * Returns hours as Double.
     */
    fun estimateNaismithLangmuir(distanceM: Double, gainM: Double, lossM: Double): Double {
        val distanceKm = max(0.0, distanceM) / 1000.0
        val baseHours = distanceKm / 5.0                    // 5 km/h
        val ascentHours = max(0.0, gainM) / 600.0           // +1h per 600 m up
        val descentAdjHours = 0.0                           // neutral without detailed slopes
        return (baseHours + ascentHours + descentAdjHours).coerceAtLeast(0.0)
    }

    /**
     * Tobler hiking function using an average absolute slope proxy:
     * slope ~= (gain + loss) / distance. v = 6 * e^( -3.5 * |slope + 0.05| )
     * Returns hours as Double.
     */
    fun estimateTobler(distanceM: Double, gainM: Double, lossM: Double): Double {
        val distM = max(1.0, distanceM)
        val slope = ((max(0.0, gainM) + max(0.0, lossM)) / distM).coerceIn(0.0, 1.0) // rise/run
        val vKmh = 6.0 * exp(-3.5 * abs(slope + 0.05))
        val distanceKm = distM / 1000.0
        val hours = if (vKmh <= 0.01) Double.POSITIVE_INFINITY else distanceKm / vKmh
        return hours.coerceAtLeast(0.0)
    }

    // ---------------- New helpers (millis, null-safe inputs) ----------------

    /** Naismith+Langmuir returning milliseconds. */
    fun naismithLangmuirMillis(distanceM: Double, gainM: Double?, lossM: Double?): Long {
        val h = estimateNaismithLangmuir(distanceM, gainM ?: 0.0, lossM ?: 0.0)
        return (h * 3_600_000.0).toLong().coerceAtLeast(0L)
    }

    /** Synonym; some code prefers a name without "Millis". */
    fun naismithLangmuir(distanceM: Double, gainM: Double?, lossM: Double?): Long =
        naismithLangmuirMillis(distanceM, gainM, lossM)

    /** Tobler returning milliseconds (slope proxy from gain+loss). */
    fun toblerMillis(distanceM: Double, gainM: Double?, lossM: Double?): Long {
        val h = estimateTobler(distanceM, gainM ?: 0.0, lossM ?: 0.0)
        if (!h.isFinite()) return Long.MAX_VALUE
        return (h * 3_600_000.0).toLong().coerceAtLeast(0L)
    }

    /** Synonym; some code prefers a name without "Millis". */
    fun tobler(distanceM: Double, gainM: Double?, lossM: Double?): Long =
        toblerMillis(distanceM, gainM, lossM)
}
