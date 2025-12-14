package com.hikemvp

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * Petit helper partagé (UI + service) pour connaître l’état réel de l’enregistrement.
 * Objectif : éviter les désynchronisations entre écrans (Map, Groupe, etc.).
 */
object RecordingStatePrefs {
    private const val KEY_ACTIVE = "rec_active"
    private const val KEY_PAUSED = "rec_paused"

    fun set(context: Context, active: Boolean, paused: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(KEY_ACTIVE, active)
            .putBoolean(KEY_PAUSED, paused)
            .apply()
    }

    fun isActive(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY_ACTIVE, false)

    fun isPaused(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY_PAUSED, false)
}
