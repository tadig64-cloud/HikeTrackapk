package com.hikemvp.group

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * Récupère le pseudo choisi dans le module Profil (clé "profile_pseudo").
 * Si vide/non défini, on retourne le fallback (ex: nom de l'appareil).
 */
object GroupMemberName {
    fun localDisplayName(ctx: Context, fallback: String): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val p = prefs.getString("profile_pseudo", null)?.trim()
        return if (!p.isNullOrEmpty()) p else fallback
    }
}
