package com.hikemvp

import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min

/**
 * Utilitaires GPX.
 *
 * NOTE: API rétro-compatible – les fonctions existantes sont conservées.
 *       De nouvelles API sûres sont ajoutées pour extraire des informations de temps/altitude.
 */
object GpxUtils {

    /** Représente un point de trace avec option altitude et timestamp (epoch ms). */
    data class TrackPoint(
        val lat: Double,
        val lon: Double,
        val ele: Double? = null,
        val timeEpochMs: Long? = null
    )

    // ===== PUBLIC API =====

    /** Convertit une polyline en GPX 1.1. (altitude si présente dans GeoPoint) */
    fun polylineToGpx(polyline: Polyline, trackName: String? = null): String {
        val pts = polyline.actualPoints ?: emptyList()

        val nowIso = isoUtcNow()
        val autoName = "HikeTrack $nowIso".replace("T", " ").replace("Z", "")
        val name = trackName ?: autoName

        val bounds = computeBounds(pts)

        val sb = StringBuilder()
        sb.append("""<?xml version=\"1.0\" encoding=\"UTF-8\"?>""").append('\n')
        sb.append(
            """<gpx version=\"1.1\" creator=\"HikeTrack\" """
        )
        sb.append(
            """xmlns=\"http://www.topografix.com/GPX/1/1\" """
        )
        sb.append(
            """xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" """
        )
        sb.append(
            """xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">"""
        ).append('\n')

        // metadata
        sb.append("<metadata>\n")
        sb.append("<name>").append(escapeXml(name)).append("</name>\n")
        sb.append("<time>").append(nowIso).append("</time>\n")
        bounds?.let {
            sb.append(
                """<bounds minlat=\"${it.minLat}\" minlon=\"${it.minLon}\" maxlat=\"${it.maxLat}\" maxlon=\"${it.maxLon}\"/>"""
            ).append('\n')
        }
        sb.append("</metadata>\n")

        // track
        sb.append("<trk>\n")
        sb.append("<name>").append(escapeXml(name)).append("</name>\n")
        sb.append("<type>Hike</type>\n")
        sb.append("<trkseg>\n")
        for (p in pts) {
            sb.append("""<trkpt lat=\"${p.latitude}\" lon=\"${p.longitude}\">""")
            val ele = p.altitude
            if (!ele.isNaN()) sb.append("<ele>").append(trimFloat(ele)).append("</ele>")
            sb.append("</trkpt>\n")
        }
        sb.append("</trkseg>\n")
        sb.append("</trk>\n")

        sb.append("</gpx>\n")
        return sb.toString()
    }

    /** Enregistre une chaîne GPX en UTF-8. */
    fun saveToFile(gpxXml: String, os: OutputStream) {
        os.writer(Charsets.UTF_8).use { it.write(gpxXml) }
    }

    /**
     * Parse un GPX (supporte trkpt / rtept / wpt) et retourne une Polyline (compat historique).
     * Pour récupérer temps/altitude de façon structurée, utilisez [parseToTrackPoints].
     */
    fun parseToPolyline(input: InputStream): Polyline {
        val points = parseToTrackPoints(input).map { GeoPoint(it.lat, it.lon, it.ele ?: Double.NaN) }
        return Polyline().apply { setPoints(points) }
    }

    /**
     * Parse un GPX en liste de [TrackPoint] (lat,lon,ele?,time?).
     * - Auto-détection de l’encodage
     * - Prise en charge des namespaces
     * - Tolérant aux balises inconnues
     */
    fun parseToTrackPoints(input: InputStream): List<TrackPoint> {
        val pts = ArrayList<TrackPoint>()
        val parser = newParser(input)

        var event = parser.eventType
        var sdfIso: SimpleDateFormat? = null

        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val tag = parser.name?.lowercase(Locale.US)
                if (tag == "trkpt" || tag == "rtept" || tag == "wpt") {
                    val latStr = parser.getAttributeValue(null, "lat")
                        ?: parser.getAttributeValue("", "lat")
                    val lonStr = parser.getAttributeValue(null, "lon")
                        ?: parser.getAttributeValue("", "lon")
                    val lat = latStr?.replace(',', '.')?.toDoubleOrNull()
                    val lon = lonStr?.replace(',', '.')?.toDoubleOrNull()

                    var ele: Double? = null
                    var timeMs: Long? = null

                    // Lire enfants (ele / time / …)
                    val startDepth = parser.depth
                    while (true) {
                        val inner = parser.next()
                        if (inner == XmlPullParser.END_DOCUMENT) break
                        if (inner == XmlPullParser.END_TAG &&
                            parser.depth == startDepth &&
                            parser.name.equals(tag, true)
                        ) break

                        if (inner == XmlPullParser.START_TAG) {
                            val innerTag = parser.name?.lowercase(Locale.US)
                            when (innerTag) {
                                "ele" -> {
                                    val txt = safeNextText(parser)
                                    ele = txt?.replace(',', '.')?.toDoubleOrNull()
                                }
                                "time" -> {
                                    val txt = safeNextText(parser)?.trim()
                                    if (!txt.isNullOrEmpty()) {
                                        // Parser ISO 8601 en UTC
                                        if (sdfIso == null) {
                                            sdfIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                                                timeZone = TimeZone.getTimeZone("UTC")
                                            }
                                        }
                                        // Multiples formats possibles: 2024-01-01T12:34:56Z | 2024-01-01T12:34:56.000Z
                                        timeMs = try {
                                            if (txt.contains('.')) {
                                                // tronquer les millisecondes si présentes
                                                val main = txt.substringBefore('.')
                                                val z = if (txt.endsWith("Z", true)) "Z" else ""
                                                sdfIso!!.parse("$main$z")?.time
                                            } else {
                                                sdfIso!!.parse(txt)?.time
                                            }
                                        } catch (_: Throwable) { null }
                                    }
                                }
                                else -> skipTag(parser) // ignorer proprement
                            }
                        }
                    }

                    if (lat != null && lon != null) {
                        pts.add(TrackPoint(lat, lon, ele, timeMs))
                    }
                } else {
                    // autres balises: laisser avancer
                }
            }
            event = parser.next()
        }

        return pts
    }

    // ===== INTERNAL HELPERS =====

    private data class Bounds(val minLat: Double, val minLon: Double, val maxLat: Double, val maxLon: Double)

    private fun computeBounds(points: List<GeoPoint>): Bounds? {
        if (points.isEmpty()) return null
        var minLat = Double.POSITIVE_INFINITY
        var minLon = Double.POSITIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY
        var maxLon = Double.NEGATIVE_INFINITY
        for (p in points) {
            minLat = min(minLat, p.latitude)
            minLon = min(minLon, p.longitude)
            maxLat = max(maxLat, p.latitude)
            maxLon = max(maxLon, p.longitude)
        }
        return Bounds(minLat, minLon, maxLat, maxLon)
    }

    private fun isoUtcNow(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(System.currentTimeMillis())
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private fun trimFloat(v: Double): String {
        val s = "%.3f".format(Locale.US, v)
        return s.trimEnd('0').trimEnd('.')
    }

    /** Parser namespace-aware + détection auto d’encodage. */
    private fun newParser(input: InputStream): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newPullParser().apply {
            // null => laisse le parser lire l’en-tête XML et détecter l’encodage
            setInput(input, /*inputEncoding=*/ null)
        }
    }

    /** nextText() sûr même si la balise est vide. */
    private fun safeNextText(parser: XmlPullParser): String? = try {
        parser.nextText()
    } catch (_: Throwable) { null }

    /** Skip profond d’une balise courante (avec enfants). */
    private fun skipTag(parser: XmlPullParser) {
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_DOCUMENT -> return
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
            }
        }
    }
}
