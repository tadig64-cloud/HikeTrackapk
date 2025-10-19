package com.hikemvp.history

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import kotlin.math.*

object GPXReader {

    data class GpxPoint(val lat: Double, val lon: Double, val ele: Double?, val timeMillis: Long?)

    fun parseStream(context: Context, input: InputStream, fileName: String = "file.gpx"): TrackInfo {
        val parser = Xml.newPullParser()
        parser.setInput(input, null)

        var event = parser.eventType
        var insideTrk = false
        var insideTrkseg = false

        var name: String? = null
        val pts = ArrayList<GpxPoint>()

        var currentEle: Double? = null
        var currentTime: Long? = null

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "name" -> if (name == null) name = parser.nextTextOrEmpty()
                        "trk" -> insideTrk = true
                        "trkseg" -> if (insideTrk) insideTrk = true.also { insideTrkseg = true }
                        "trkpt" -> {
                            val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                            val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                            currentEle = null
                            currentTime = null
                            // Read nested tags of trkpt
                            val depth = parser.depth
                            while (true) {
                                val ev = parser.next()
                                if (ev == XmlPullParser.END_TAG && parser.depth == depth && parser.name == "trkpt") {
                                    break
                                }
                                if (ev == XmlPullParser.START_TAG) {
                                    when (parser.name) {
                                        "ele" -> currentEle = parser.nextTextOrEmpty().toDoubleOrNull()
                                        "time" -> currentTime = isoToMillis(parser.nextTextOrEmpty())
                                    }
                                }
                            }
                            if (lat != null && lon != null) {
                                pts.add(GpxPoint(lat, lon, currentEle, currentTime))
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "trkseg") insideTrkseg = false
                }
            }
            event = parser.next()
        }

        val dist = totalDistance(pts)
        val (gain, loss, hasEle) = elevationStats(pts)
        val start = pts.firstOrNull { it.timeMillis != null }?.timeMillis
        val end = pts.lastOrNull { it.timeMillis != null }?.timeMillis
        val nm = name ?: fileName.substringBeforeLast('.', fileName)

        return TrackInfo(
            name = nm,
            fileName = fileName,
            points = pts.size,
            distanceMeters = dist,
            elevationGain = gain,
            elevationLoss = loss,
            startTimeMillis = start,
            endTimeMillis = end,
            hasElevation = hasEle
        )
    }

    private fun XmlPullParser.nextTextOrEmpty(): String {
        return try { this.nextText() } catch (e: Exception) { "" }
    }

    private fun isoToMillis(iso: String): Long? {
        return try {
            java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        } catch (_: Throwable) {
            try {
                java.time.ZonedDateTime.parse(iso).toInstant().toEpochMilli()
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun totalDistance(pts: List<GpxPoint>): Double {
        var d = 0.0
        for (i in 1 until pts.size) {
            d += haversine(pts[i-1].lat, pts[i-1].lon, pts[i].lat, pts[i].lon)
        }
        return d
    }

    // meters
    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat/2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon/2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1-a))
        return R * c
    }

    private fun elevationStats(pts: List<GpxPoint>): Triple<Double, Double, Boolean> {
        var up = 0.0
        var down = 0.0
        var prev: Double? = null
        var has = false
        for (p in pts) {
            val e = p.ele
            if (e != null) {
                if (prev != null) {
                    val delta = e - prev!!
                    if (delta > 0) up += delta
                    else down += -delta
                }
                prev = e
                has = true
            }
        }
        return Triple(up, down, has)
    }
}
