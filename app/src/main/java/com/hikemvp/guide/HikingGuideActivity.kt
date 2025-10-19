package com.hikemvp.guide

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.hikemvp.R

class HikingGuideActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_HikeTrack) // si tu utilises Material3 etc.
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hiking_guide)
        supportActionBar?.title = getString(R.string.menu_guide)
    }
}
