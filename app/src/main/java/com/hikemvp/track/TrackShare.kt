package com.hikemvp.track

import android.content.Context
import android.content.Intent
import android.location.Location
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Export GPX minimal + Intent de partage.
 * ADDITIF, SANS IMPACT UI (à appeler depuis une Activity/Fragment/ViewModel).
 *
 * Nécessite un FileProvider déclaré dans le manifeste avec @xml/file_paths.
 */
object TrackShare {

    private val isoFormatter: DateTimeFormatter =
        DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)

    /**
     * Écrit un fichier GPX temporaire dans cacheDir/exports et retourne l'URI FileProvider.
     * @param trackName nom facultatif de la trace (affiché dans le gpx).
     */
    @JvmStatic
    fun exportGpx(
        context: Context,
        points: List<Location>,
        trackName: String? = null
    ): android.net.Uri {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, "track_${System.currentTimeMillis()}.gpx")

        FileOutputStream(file).use { fos ->
            fos.writer(Charsets.UTF_8).use { out ->
                out.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
                out.appendLine("""<gpx version="1.1" creator="HikeTrack" xmlns="http://www.topografix.com/GPX/1/1">""")
                out.appendLine("  <trk>")
                if (!trackName.isNullOrBlank()) {
                    out.appendLine("    <name>${escapeXml(trackName)}</name>")
                }
                out.appendLine("    <trkseg>")
                for (loc in points) {
                    val lat = loc.latitude
                    val lon = loc.longitude
                    val timeIso = isoFormatter.format(Instant.ofEpochMilli(loc.time))
                    out.appendLine("""      <trkpt lat="$lat" lon="$lon"><time>$timeIso</time></trkpt>""")
                }
                out.appendLine("    </trkseg>")
                out.appendLine("  </trk>")
                out.appendLine("</gpx>")
            }
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Prépare un Intent chooser pour partager le GPX. À lancer via startActivity(intent).
     */
    @JvmStatic
    fun buildShareGpxIntent(
        context: Context,
        gpxUri: android.net.Uri,
        title: String? = null
    ): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, gpxUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooserTitle = title ?: "Partager la trace (GPX)"
        return Intent.createChooser(send, chooserTitle)
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
