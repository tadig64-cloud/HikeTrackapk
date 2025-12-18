package com.hikemvp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Activity "tampon" (très simple) pour ouvrir un fichier GPX reçu depuis :
 * - un gestionnaire de fichiers (ACTION_VIEW / ACTION_EDIT)
 * - un partage (ACTION_SEND)
 *
 * Elle redirige vers MapActivity en lui transmettant l'Uri du GPX.
 *
 * Objectif : être robuste et ne jamais faire crasher l'app au moment de l'ouverture.
 */
class GpxOpenActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)
        finish() // on ne garde pas cet écran
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            handleIncomingIntent(intent)
        }
        finish()
    }

    private fun handleIncomingIntent(inIntent: Intent) {
        val uri = extractUri(inIntent)

        if (uri == null) {
            Toast.makeText(this, "Aucun fichier GPX détecté.", Toast.LENGTH_LONG).show()
            Log.w("GpxOpenActivity", "No Uri found in incoming intent: action=${inIntent.action}")
            return
        }

        // Essai de conserver l'autorisation de lecture si c'est un Uri type SAF (DocumentsProvider)
        try {
            val takeFlags = inIntent.flags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            if (takeFlags != 0) {
                contentResolver.takePersistableUriPermission(uri, takeFlags)
            }
        } catch (_: Throwable) {
            // Pas grave : l'Uri peut ne pas être persistable.
        }

        // Redirection vers la carte
        val openMap = Intent(this, MapActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uri
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            // Extras "compat" au cas où MapActivity lit un extra au lieu de data
            putExtra(EXTRA_GPX_URI, uri.toString())
            putExtra(Intent.EXTRA_STREAM, uri)
        }

        try {
            startActivity(openMap)
        } catch (t: Throwable) {
            Toast.makeText(this, "Impossible d'ouvrir la trace dans HikeTrack.", Toast.LENGTH_LONG).show()
            Log.e("GpxOpenActivity", "Failed to start MapActivity with uri=$uri", t)
        }
    }

    private fun extractUri(inIntent: Intent): Uri? {
        return when (inIntent.action) {
            Intent.ACTION_VIEW,
            Intent.ACTION_EDIT -> inIntent.data

            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                inIntent.getParcelableExtra(Intent.EXTRA_STREAM)
                    ?: inIntent.data
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                val list = inIntent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                list?.firstOrNull() ?: inIntent.data
            }

            else -> inIntent.data
        }
    }

    companion object {
        const val EXTRA_GPX_URI = "com.hikemvp.extra.GPX_URI"
    }
}
