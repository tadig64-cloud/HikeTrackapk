package com.hikemvp.utils

import java.util.Locale

object Units {
    fun metersToFeet(m: Double): Double = m * 3.2808398950131
    fun kmToMiles(km: Double): Double = km * 0.62137119223733
    fun feetToMeters(ft: Double): Double = ft / 3.2808398950131
    fun milesToKm(mi: Double): Double = mi / 0.62137119223733

    fun useImperial(locale: Locale = Locale.getDefault()): Boolean {
        val c = locale.country.uppercase(Locale.ROOT)
        return c == "US" || c == "LR" || c == "MM" // keep GB metric to avoid surprises
    }

    fun fmtDistanceKm(km: Double, locale: Locale = Locale.getDefault(), withUnit: Boolean = true): String {
        // Conservative: keep metric formatting by default to avoid UI changes
        val txt = if (km < 100.0) String.format(locale, "%.1f", km) else String.format(locale, "%.0f", km)
        return if (withUnit) "$txt km" else txt
    }

    fun fmtElevationM(m: Double, locale: Locale = Locale.getDefault(), withUnit: Boolean = true): String {
        val txt = String.format(locale, "%.0f", m)
        return if (withUnit) "$txt m" else txt
    }
}
