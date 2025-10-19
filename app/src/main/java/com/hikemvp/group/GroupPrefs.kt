package com.hikemvp.group

import android.content.Context

object GroupPrefs {
    private const val FILE = "group_prefs"
    private const val KEY_NAME = "display_name"
    private const val KEY_IS_HOST = "is_host"
    private const val KEY_DOT_COLOR = "dot_color"
    private const val KEY_RESTORE_CAMERA = "restore_camera_enabled"
    private const val KEY_FIT_GROUP = "fit_group_on_launch"

    fun getName(ctx: Context): String? =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_NAME, null)

    fun setName(ctx: Context, name: String?) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NAME, name ?: "")
            .apply()
    }

    fun getIsHost(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_IS_HOST, false)

    fun setIsHost(ctx: Context, isHost: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_HOST, isHost)
            .apply()
    }

    fun setDotColor(ctx: Context, color: Int) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_DOT_COLOR, color)
            .apply()
    }

    fun getDotColor(ctx: Context, defaultColor: Int): Int =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_DOT_COLOR, defaultColor)

    // ---- Camera prefs ----
    fun isRestoreCameraEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_RESTORE_CAMERA, true)

    fun setRestoreCameraEnabled(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RESTORE_CAMERA, enabled)
            .apply()
    }

    fun isFitGroupOnLaunch(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_FIT_GROUP, false)

    fun setFitGroupOnLaunch(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FIT_GROUP, enabled)
            .apply()
    }
}
