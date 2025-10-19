package com.hikemvp.gpx

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object GpxExporter {

    data class GpxPoint(
        val lat: Double,
        val lon: Double,
        val name: String? = null,
        val desc: String? = null,
        val timeMillis: Long? = null
    )

    /**
     * Écrit un fichier GPX dans cache/shared_gpx et retourne une Uri FileProvider partageable.
     * L'autorité du FileProvider attendue est ${applicationId}.fileprovider (voir manifest).
     */
    fun writeGpx(context: Context, points: List<GpxPoint>, fileName: String = defaultFileName()): Uri {
        val dir = File(context.cacheDir, "shared_gpx")
        dir.mkdirs()
        val file = File(dir, fileName)

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="HikeTrack" xmlns="http://www.topografix.com/GPX/1/1">
  <metadata>
    <time>${isoUtc(System.currentTimeMillis())}</time>
  </metadata>
""")
        for (p in points) {
            sb.append("""  <wpt lat="${p.lat}" lon="${p.lon}">
""")
            p.timeMillis?.let { sb.append("    <time>${isoUtc(it)}</time>\n") }
            p.name?.let { sb.append("    <name>${escapeXml(it)}</name>\n") }
            p.desc?.let { sb.append("    <desc>${escapeXml(it)}</desc>\n") }
            sb.append("  </wpt>\n")
        }
        sb.append("</gpx>\n")

        file.writeText(sb.toString(), Charset.forName("UTF-8"))
        val authority = context.packageName + ".fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    private fun isoUtc(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(millis))
    }

    private fun escapeXml(s: String): String {
        return s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun defaultFileName(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return "waypoints_${sdf.format(Date())}.gpx"
    }
}
