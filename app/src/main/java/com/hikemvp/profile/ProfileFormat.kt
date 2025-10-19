package com.hikemvp.profile

import kotlin.math.roundToInt

object ProfileFormat {
    fun distanceLabel(system: UnitSystem, meters: Double): String {
        return when (system) {
            UnitSystem.METRIC -> String.format("%.1f km", meters / 1000.0)
            UnitSystem.IMPERIAL -> String.format("%.1f mi", meters / 1609.344)
        }
    }

    fun elevationLabel(system: UnitSystem, meters: Double): String {
        return when (system) {
            UnitSystem.METRIC -> "${meters.roundToInt()} m"
            UnitSystem.IMPERIAL -> "${(meters * 3.28084).roundToInt()} ft"
        }
    }

    /** speed en m/s → pace mm:ss per km/mi */
    fun paceLabel(system: UnitSystem, speed: Double): String {
        if (speed <= 0.1) return "—"
        val secsPerMeter = 1.0 / speed
        val secs = when (system) {
            UnitSystem.METRIC -> (secsPerMeter * 1000.0).roundToInt()
            UnitSystem.IMPERIAL -> (secsPerMeter * 1609.344).roundToInt()
        }
        val mm = secs / 60
        val ss = secs % 60
        val unit = if (system == UnitSystem.METRIC) "/km" else "/mi"
        return String.format("%d:%02d %s", mm, ss, unit)
    }
}
