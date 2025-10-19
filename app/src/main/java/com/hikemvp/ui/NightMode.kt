package com.hikemvp.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.hikemvp.utils.Prefs

object NightMode {
    fun applyFromPrefs(ctx: Context) {
        when (Prefs.getNightMode(ctx)) {
            "yes" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "no" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}
