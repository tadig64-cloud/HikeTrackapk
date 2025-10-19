package com.hikemvp.ui.map

import org.osmdroid.util.GeoPoint
import com.hikemvp.Stats
import com.hikemvp.planning.TrackAnalysis

// --- Fonctions de façade (nouveau code) ---
fun computeStats(points: List<GeoPoint>): Stats = TrackAnalysis.computeStats(points)
fun estimateDurationMillis(stats: Stats): Long = TrackAnalysis.estimateDurationMillis(stats)
fun formatDistance(km: Double): String = TrackAnalysis.formatDistance(km)
fun formatElevation(m: Double): String = TrackAnalysis.formatElevation(m)
fun formatDuration(ms: Long): String = TrackAnalysis.formatDuration(ms)

// --- Rétro-compatibilité (ancien code appelant MapStatsUtils.*) ---
object MapStatsUtils {
    @JvmStatic fun computeStats(points: List<GeoPoint>): Stats =
        TrackAnalysis.computeStats(points)

    @JvmStatic fun estimateDurationMillis(stats: Stats): Long =
        TrackAnalysis.estimateDurationMillis(stats)

    @JvmStatic fun formatDistance(km: Double): String =
        TrackAnalysis.formatDistance(km)

    @JvmStatic fun formatElevation(m: Double): String =
        TrackAnalysis.formatElevation(m)

    @JvmStatic fun formatDuration(ms: Long): String =
        TrackAnalysis.formatDuration(ms)
}
