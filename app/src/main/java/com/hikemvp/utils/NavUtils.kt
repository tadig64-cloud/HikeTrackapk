package com.hikemvp.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.hikemvp.R
import java.net.URLEncoder

object NavUtils {

    /**
     * Ouvre un sélecteur d'apps de navigation (Google Maps, Waze, etc.).
     * Aucune modification de layout nécessaire : appelez cette méthode
     * depuis le onClick de votre bouton "Navigation".
     */
    @JvmStatic
    fun openNavigationChooser(
        context: Context,
        lat: Double,
        lon: Double,
        label: String? = null
    ) {
        val safeLabel = try {
            URLEncoder.encode(label ?: "Destination", "UTF-8")
        } catch (_: Exception) {
            "Destination"
        }

        // Intent Google Maps (tourne si installé)
        val gmaps = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("google.navigation:q=$lat,$lon")
        ).apply { setPackage("com.google.android.apps.maps") }

        // Intent Waze (tourne si installé)
        val waze = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://waze.com/ul?ll=$lat,$lon&navigate=yes")
        ).apply { setPackage("com.waze") }

        // Fall-back générique (toute app gérant les URI geo:)
        val geo = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:$lat,$lon?q=$lat,$lon($safeLabel)")
        )

        val pm = context.packageManager
        val candidates = listOf(gmaps, waze, geo).filter { it.resolveActivity(pm) != null }

        if (candidates.isEmpty()) {
            Toast.makeText(context, R.string.nav_no_app_found, Toast.LENGTH_SHORT).show()
            return
        }

        val chooser = Intent.createChooser(
            candidates.first(),
            context.getString(R.string.nav_choose_app)
        ).apply {
            if (candidates.size > 1) {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, candidates.drop(1).toTypedArray())
            }
        }
        context.startActivity(chooser)
    }
}
