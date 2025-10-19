package com.hikemvp.profile

import android.content.Context
import android.content.SharedPreferences

object ProfileProvider {
    private const val PREF = "profile_prefs"
    private const val K_NAME = "display_name"
    private const val K_PHOTO = "photo_path"
    private const val K_THEME = "theme"
    private const val K_UNIT = "unit"

    fun get(context: Context): Profile {
        val p = prefs(context)
        val unit = when (p.getString(K_UNIT, "metric")) {
            "imperial" -> UnitSystem.IMPERIAL
            else -> UnitSystem.METRIC
        }
        return Profile(
            displayName = p.getString(K_NAME, "") ?: "",
            photoPath = p.getString(K_PHOTO, "") ?: "",
            theme = p.getString(K_THEME, "system") ?: "system",
            unitSystem = unit
        )
    }

    fun update(context: Context, block: (Profile) -> Profile) {
        val before = get(context)
        val after = block(before)
        val e = prefs(context).edit()
        e.putString(K_NAME, after.displayName)
        e.putString(K_PHOTO, after.photoPath)
        e.putString(K_THEME, after.theme)
        e.putString(K_UNIT, if (after.unitSystem == UnitSystem.IMPERIAL) "imperial" else "metric")
        e.apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
