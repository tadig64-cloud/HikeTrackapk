package com.hikemvp.waypoints

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.hikemvp.R
import com.hikemvp.tabs.Tab
import com.hikemvp.tabs.Tabs

class WaypointsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_waypoints)
        supportActionBar?.setTitle(R.string.title_waypoints)

        val bottom = findViewById<BottomNavigationView>(R.id.bottomTabs)
        Tabs.setup(this, bottom, Tab.WAYPOINTS)
    }
}
