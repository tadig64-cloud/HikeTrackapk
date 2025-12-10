package com.hikemvp.ui

import android.app.Activity
import android.content.Intent
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.hikemvp.tracks.TracksActivity
import com.hikemvp.help.HelpActivity

object BottomNavActions {

    /**
     * Attache les actions de base si les items existent.
     * IDs cherchés dynamiquement : nav_map, nav_group, nav_tracks, nav_more
     * (si absents, on ignore sans casser).
     */
    fun bind(activity: Activity, bottomNav: BottomNavigationView?) {
        if (bottomNav == null) return

        val res = activity.resources
        val pkg = activity.packageName

        fun id(name: String): Int = res.getIdentifier(name, "id", pkg)

        val idMap    = id("nav_map")
        val idGroup  = id("nav_group")
        val idTracks = id("nav_tracks")
        val idMore   = id("nav_more")

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                idTracks -> {
                    activity.startActivity(Intent(activity, TracksActivity::class.java))
                    true
                }
                idMore -> {
                    activity.startActivity(Intent(activity, HelpActivity::class.java))
                    true
                }
                idMap, idGroup -> {
                    // La logique de navigation principale reste dans ton app.
                    // Ici on ne fait rien pour éviter toute régression.
                    false
                }
                else -> false
            }
        }
    }
}