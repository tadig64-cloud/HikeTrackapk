package com.hikemvp.group

import android.content.Context
import androidx.core.content.edit
import java.util.UUID

object DeviceId {
    private const val PREFS = "group_repo_v1"
    private const val KEY_DEVICE_ID = "device_id"

    fun get(ctx: Context): String {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var id = sp.getString(KEY_DEVICE_ID, null)
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString().substring(0, 8)
            sp.edit { putString(KEY_DEVICE_ID, id) }
        }
        return id!!
    }
}
