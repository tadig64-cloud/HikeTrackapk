package com.hikemvp.profile

import android.content.Context
import android.net.Uri
import androidx.preference.PreferenceManager

object ProfileUtils {
    fun getPseudo(context: Context): String? =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getString(ProfilePrefs.KEY_PSEUDO, null)
            ?.takeIf { it.isNotBlank() }

    fun setPseudo(context: Context, value: String) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(ProfilePrefs.KEY_PSEUDO, value).apply()
    }

    fun getAvatarUri(context: Context): Uri? =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getString(ProfilePrefs.KEY_AVATAR_URI, null)
            ?.let { Uri.parse(it) }

    fun setAvatarUri(context: Context, uri: Uri?) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(ProfilePrefs.KEY_AVATAR_URI, uri?.toString()).apply()
    }

    fun isHudWeatherEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(ProfilePrefs.KEY_HUD_WEATHER, true)

    fun isAutoFollowEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(ProfilePrefs.KEY_AUTO_FOLLOW, true)

    object ProfileUtils {
        private const val K_HUD = "profile_hud_weather"
        private const val K_AUTO = "profile_auto_follow"

        fun isHudWeatherEnabled(ctx: Context): Boolean =
            PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(K_HUD, true)

        fun setHudWeatherEnabled(ctx: Context, value: Boolean) {
            PreferenceManager.getDefaultSharedPreferences(ctx)
                .edit().putBoolean(K_HUD, value).apply()
        }

        fun isAutoFollowEnabled(ctx: Context): Boolean =
            PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(K_AUTO, true)

        fun setAutoFollowEnabled(ctx: Context, value: Boolean) {
            PreferenceManager.getDefaultSharedPreferences(ctx)
                .edit().putBoolean(K_AUTO, value).apply()
        }
    }

}
