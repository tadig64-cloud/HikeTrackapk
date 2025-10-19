package com.hikemvp.sos

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.hikemvp.TrackStore
import org.osmdroid.util.GeoPoint

object SosManager {
    fun buildSosSmsIntent(
        context: Context,
        emergencyNumber: String,
        lastKnown: GeoPoint? = null,
        altitudeMeters: Double? = null
    ): Intent {
        val p = lastKnown ?: TrackStore.snapshot().lastOrNull()
        val lat = p?.latitude
        val lon = p?.longitude
        val alt = altitudeMeters ?: p?.altitude
        val google = if (lat != null && lon != null) "https://maps.google.com/?q=%1$.6f,%2$.6f".format(lat, lon) else "Position inconnue"
        val altStr = if (alt != null && !alt.isNaN()) "Alt: ${alt.toInt()} m" else ""
        val coords = if (lat != null && lon != null) "Lat: %1$.6f, Lon: %2$.6f".format(lat, lon) else "Coordonnées indisponibles"
        val body = "SOS - Besoin d'aide. ${coords}. ${altStr}\n${google}\nEnvoyé depuis HikeTrack"
        val uri = Uri.parse("smsto:${Uri.encode(emergencyNumber)}")
        return Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", body)
        }
    }
}
