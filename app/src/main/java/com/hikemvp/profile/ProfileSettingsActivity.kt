package com.hikemvp.profile

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.hikemvp.R
import com.google.android.material.appbar.MaterialToolbar

class ProfileSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, ProfileSettingsFragment())
                .commit()
        }
    }
}
