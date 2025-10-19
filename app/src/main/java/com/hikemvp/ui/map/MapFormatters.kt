package com.hikemvp.ui.map

import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Formatters utilisés par MapFragment.
 * Ils sont volontairement "tolérants" sur les nulls pour éviter les plantages en affichage.
 */
fun formatDistance(meters: Double?): String {
    val m = meters ?: return "—"
    if (m.isNaN()) return "—"
    return if (m >= 1000.0) {
        val km = m / 1000.0
        // 1 décimale pour un rendu compact
        String.format("%.1f km", km)
    } else {
        "${m.roundToInt()} m"
    }
}

fun formatElevation(meters: Double?): String {
    val m = meters ?: return "—"
    if (m.isNaN()) return "—"
    return "${m.roundToInt()} m"
}

fun formatDuration(ms: Long?): String {
    val total = ms ?: return "—"
    if (total < 0) return "—"

    val hours = TimeUnit.MILLISECONDS.toHours(total)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(total) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(total) % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
