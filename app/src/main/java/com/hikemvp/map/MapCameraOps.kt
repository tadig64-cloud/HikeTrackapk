package com.hikemvp.map

import android.content.Context

object MapCameraOps {
    private const val FILE = "map_camera_prefs"
    fun clearSaved(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
