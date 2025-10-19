package com.hikemvp.utils

import android.content.Context
import androidx.preference.PreferenceManager

object Prefs {
    private const val KEY_EMERGENCY = "ht_emergency_number"
    private const val KEY_NIGHT_MODE = "ht_night_mode" // "system" | "yes" | "no"

    fun getEmergencyNumber(ctx: Context): String? =
        PreferenceManager.getDefaultSharedPreferences(ctx).getString(KEY_EMERGENCY, null)

    fun setEmergencyNumber(ctx: Context, num: String) {
        PreferenceManager.getDefaultSharedPreferences(ctx).edit().putString(KEY_EMERGENCY, num).apply()
    }

    fun getNightMode(ctx: Context): String =
        PreferenceManager.getDefaultSharedPreferences(ctx).getString(KEY_NIGHT_MODE, "system") ?: "system"

    fun setNightMode(ctx: Context, value: String) {
        PreferenceManager.getDefaultSharedPreferences(ctx).edit().putString(KEY_NIGHT_MODE, value).apply()
    }
}
