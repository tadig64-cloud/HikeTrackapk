package com.hikemvp.planning

import java.util.Locale
import kotlin.math.roundToInt

object UnitSystems {
    enum class DistanceUnit { KM, MI }
    enum class ElevUnit { M, FT }

    data class LocaleUnits(val distance: DistanceUnit, val elevation: ElevUnit)

    fun unitsForLocale(locale: Locale = Locale.getDefault()): LocaleUnits {
        val usesImperial = when (locale.country.uppercase(Locale.ROOT)) {
            "US", "GB", "LR", "MM" -> true
            else -> false
        }
        return if (usesImperial) LocaleUnits(DistanceUnit.MI, ElevUnit.FT)
        else LocaleUnits(DistanceUnit.KM, ElevUnit.M)
    }

    fun formatDistance(meters: Double, locale: Locale = Locale.getDefault()): String {
        val u = unitsForLocale(locale)
        return if (u.distance == DistanceUnit.KM) {
            val km = meters / 1000.0
            String.format(locale, "%.2f km", km)
        } else {
            val mi = meters / 1609.344
            String.format(locale, "%.2f mi", mi)
        }
    }

    fun formatElevation(meters: Double, locale: Locale = Locale.getDefault()): String {
        val u = unitsForLocale(locale)
        return if (u.elevation == ElevUnit.M) {
            "${meters.roundToInt()} m"
        } else {
            val ft = meters * 3.28084
            "${ft.roundToInt()} ft"
        }
    }
}
