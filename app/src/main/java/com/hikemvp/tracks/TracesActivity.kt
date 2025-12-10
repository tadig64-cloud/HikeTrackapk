package com.hikemvp.tracks

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.hikemvp.R

class TracesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tracks)
        title = getString(R.string.title_tracks)
    }
}