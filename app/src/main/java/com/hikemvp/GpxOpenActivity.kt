package com.hikemvp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Petite Activity “pont” pour permettre à HikeTrack d’apparaître dans
 * “Ouvrir avec…” pour les fichiers .gpx.
 *
 * Elle récupère l’URI puis redirige vers MapActivity (qui sait importer + afficher).
 */
class GpxOpenActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri: Uri? = intent?.data
            ?: runCatching { intent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) }.getOrNull()
            ?: intent?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri

        // On relance MapActivity en "singleTop" pour éviter d’empiler des écrans.
        val target = Intent(this, MapActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uri
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            // Propage au maximum la permission de lecture
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(target)
        finish()
    }
}
