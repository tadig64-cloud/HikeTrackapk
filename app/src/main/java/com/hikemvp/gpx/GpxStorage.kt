package com.hikemvp.gpx

import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object GpxStorage {

    fun gpxDir(context: Context): File {
        val base = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        return File(base, "HikeTrack/GPX").apply { mkdirs() }
    }

    fun writeGpx(context: Context, points: List<Location>): File {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val out = File(gpxDir(context), "track_$ts.gpx")
        out.bufferedWriter().use { w ->
            w.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            w.appendLine("""<gpx version="1.1" creator="HikeTrack" xmlns="http://www.topografix.com/GPX/1/1">""")
            w.appendLine("<trk><name>HikeTrack $ts</name><trkseg>")
            points.forEach { loc ->
                val lat = loc.latitude
                val lon = loc.longitude
                val ele = loc.altitude.takeIf { it.isFinite() } ?: Double.NaN
                val time = Date(loc.time)
                val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(time)
                w.append("""<trkpt lat="$lat" lon="$lon">""")
                if (ele.isFinite()) w.append("<ele>$ele</ele>")
                w.append("<time>$iso</time></trkpt>\n")
            }
            w.appendLine("</trkseg></trk></gpx>")
        }
        return out
    }

    fun shareGpx(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Partager GPX"))
    }
}
