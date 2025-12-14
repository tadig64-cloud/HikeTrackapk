package com.hikemvp.guide

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.hikemvp.R
import com.hikemvp.info.InfoHubActivity

/**
 * Ancien écran "Guide" : l'InfoHub est devenu la page centrale (plus complète).
 * On garde cette Activity pour éviter toute régression si un lien/menu l'appelle encore,
 * mais on redirige vers InfoHub.
 */
class HikingGuideActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_HikeTrack)
        super.onCreate(savedInstanceState)

        startActivity(Intent(this, InfoHubActivity::class.java))
        finish()
        overridePendingTransition(0, 0)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
