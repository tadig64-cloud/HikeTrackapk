package com.hikemvp.tabs

import android.app.Activity
import android.content.Intent
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.hikemvp.R
import com.hikemvp.MapActivity
import com.hikemvp.group.GroupActivity
import com.hikemvp.tracks.TracksActivity
import com.hikemvp.waypoints.WaypointsActivity
import com.hikemvp.more.MoreActivity

enum class Tab { MAP, GROUP, TRACKS, WAYPOINTS, MORE }

object Tabs {
    fun setup(activity: Activity, bottom: BottomNavigationView, current: Tab) {
        bottom.selectedItemId = when (current) {
            Tab.MAP -> R.id.tab_map
            Tab.GROUP -> R.id.tab_group
            Tab.TRACKS -> R.id.tab_tracks
            Tab.WAYPOINTS -> R.id.tab_waypoints
            Tab.MORE -> R.id.tab_more
        }

        bottom.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.tab_map -> {
                    if (current != Tab.MAP) {
                        activity.startActivity(
                            Intent(activity, MapActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        )
                    }
                    true
                }
                R.id.tab_group -> {
                    if (current != Tab.GROUP) {
                        activity.startActivity(
                            Intent(activity, GroupActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        )
                    }
                    true
                }
                R.id.tab_tracks -> {
                    if (current != Tab.TRACKS) {
                        activity.startActivity(
                            Intent(activity, TracksActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        )
                    }
                    true
                }
                R.id.tab_waypoints -> {
                    if (current != Tab.WAYPOINTS) {
                        activity.startActivity(
                            Intent(activity, WaypointsActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        )
                    }
                    true
                }
                R.id.tab_more -> {
                    if (current != Tab.MORE) {
                        activity.startActivity(
                            Intent(activity, MoreActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        )
                    }
                    true
                }
                else -> false
            }
        }
    }
}
