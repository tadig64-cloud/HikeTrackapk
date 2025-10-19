package com.hikemvp.waypoints

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * Generic helpers for persisting group UI state.
 * Using simple SharedPreferences so it works anywhere.
 */
object WaypointGroups {

    private const val PREFS_SELECTED_GROUP = "waypoints.selected_group"
    private const val PREFS_SHOW_HIDDEN   = "waypoints.show_hidden_groups"
    private const val PREFS_HIDDEN_SET    = "waypoints.hidden_groups_set"

    fun getSelectedGroup(ctx: Context): String? =
        PreferenceManager.getDefaultSharedPreferences(ctx)
            .getString(PREFS_SELECTED_GROUP, null)

    fun setSelectedGroup(ctx: Context, group: String?) {
        PreferenceManager.getDefaultSharedPreferences(ctx).edit()
            .putString(PREFS_SELECTED_GROUP, group)
            .apply()
    }

    fun getShowHidden(ctx: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(ctx)
            .getBoolean(PREFS_SHOW_HIDDEN, false)

    fun setShowHidden(ctx: Context, value: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(ctx).edit()
            .putBoolean(PREFS_SHOW_HIDDEN, value)
            .apply()
    }

    fun getHiddenGroups(ctx: Context): MutableSet<String> =
        PreferenceManager.getDefaultSharedPreferences(ctx)
            .getStringSet(PREFS_HIDDEN_SET, emptySet())?.toMutableSet() ?: mutableSetOf()

    fun setHiddenGroups(ctx: Context, value: Set<String>) {
        PreferenceManager.getDefaultSharedPreferences(ctx).edit()
            .putStringSet(PREFS_HIDDEN_SET, value.toSet())
            .apply()
    }
}
