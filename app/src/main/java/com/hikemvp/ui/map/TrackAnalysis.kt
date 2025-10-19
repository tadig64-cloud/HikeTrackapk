package com.hikemvp.ui.map

import org.osmdroid.util.GeoPoint
import com.hikemvp.Stats
import com.hikemvp.planning.TrackAnalysis as CoreTrackAnalysis

/**
 * Pont UI -> cœur : garde le nom TrackAnalysis *dans le package ui.map* pour
 * permettre aux écrans de continuer à l'utiliser sans toucher leur code.
 * Le vrai calcul reste dans com.hikemvp.planning.TrackAnalysis.
 */
object TrackAnalysis {
    fun computeStats(points: List<GeoPoint>): Stats = CoreTrackAnalysis.computeStats(points)
    fun estimateDurationMillis(stats: Stats): Long = CoreTrackAnalysis.estimateDurationMillis(stats)
    fun formatDistance(km: Double): String = CoreTrackAnalysis.formatDistance(km)
    fun formatElevation(m: Double): String = CoreTrackAnalysis.formatElevation(m)
    fun formatDuration(ms: Long): String = CoreTrackAnalysis.formatDuration(ms)
}
