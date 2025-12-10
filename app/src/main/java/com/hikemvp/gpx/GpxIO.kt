package com.hikemvp.gpx

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Xml
import com.hikemvp.model.Track
import com.hikemvp.model.TrackPoint
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object GpxIO {

    /** Import a GPX track from a content Uri. */
    fun importGpx(context: Context, uri: Uri): Track {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open GPX: $uri" }
            return parseGpx(input)
        }
    }

    /** Export a Track as GPX to a content Uri. */
    fun exportGpx(context: Context, track: Track, dest: Uri) {
        context.contentResolver.openOutputStream(dest).use { out ->
            requireNotNull(out) { "Unable to open output: $dest" }
            writeGpx(track, out)
        }
    }

    // --- Internal parsing ---

    private fun parseGpx(input: InputStream): Track {
        val parser = Xml.newPullParser()
        parser.setInput(input, null)

        var event = parser.eventType
        var name: String? = null
        val pts = mutableListOf<TrackPoint>()
        var curLat = 0.0
        var curLon = 0.0
        var curEle: Double? = null
        var curTime: Long? = null

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "name" -> name = nextTextSafe(parser, "name", name)
                        "trkpt" -> {
                            curLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                            curLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                            curEle = null
                            curTime = null
                        }
                        "ele" -> curEle = nextTextSafe(parser, "ele", null)?.toDoubleOrNull()
                        "time" -> curTime = parseIso8601(nextTextSafe(parser, "time", null))
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "trkpt") {
                        pts += TrackPoint(curLat, curLon, curEle, curTime)
                    }
                }
            }
            event = parser.next()
        }
        return Track(name = name, points = pts)
    }

    private fun nextTextSafe(parser: XmlPullParser, tag: String, current: String?): String? {
        return try {
            parser.next()
            if (parser.eventType == XmlPullParser.TEXT) parser.text else current
        } catch (_: Throwable) { current }
    }

    private fun parseIso8601(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            fmt.parse(text)?.time
        } catch (_: Throwable) { null }
    }

    private fun writeGpx(track: Track, out: OutputStream) {
        out.bufferedWriter(Charsets.UTF_8).use { w ->
            w.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            w.appendLine("""<gpx version="1.1" creator="HikeTrack" xmlns="http://www.topografix.com/GPX/1/1">""")
            w.appendLine("<trk>")
            track.name?.let { w.appendLine("<name>${escape(it)}</name>") }
            w.appendLine("<trkseg>")
            for (p in track.points) {
                w.append("""<trkpt lat="${p.lat}" lon="${p.lon}">""")
                p.ele?.let { w.append("<ele>$it</ele>") }
                p.timeMillis?.let { w.append("<time>${formatIso8601(it)}</time>") }
                w.appendLine("</trkpt>")
            }
            w.appendLine("</trkseg>")
            w.appendLine("</trk>")
            w.appendLine("</gpx>")
        }
    }

    private fun formatIso8601(millis: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return fmt.format(millis)
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}