package com.hikemvp.utils

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.osmdroid.util.GeoPoint
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.*

/**
 * KML importer (streaming) – designed to avoid loading the whole file in memory.
 *
 * Supports:
 * - LineString coordinates (track/polyline)
 * - Placemark Points (POIs) with name/description + ExtendedData (Data/SimpleData)
 *
 * Compatible with older call-sites:
 *   - parse(context, uri, decimateEvery, minDistMeters, maxPois)
 *   - parse(input = ..., decimateEvery = ..., minDistMeters = ..., maxPois = ...)
 */
object KmlImporter {

    data class POI(
        val title: String,
        val point: GeoPoint,
        val description: String = "",
        val extras: Map<String, String> = emptyMap()
    )

    data class ImportResult(
        val track: List<GeoPoint> = emptyList(),
        val pois: List<POI> = emptyList(),
        val truncatedPois: Boolean = false
    )
    private enum class Geom { NONE, POINT, LINESTRING }


    /**
     * Parse a KML from a content Uri.
     */
    fun parse(
        context: Context,
        uri: Uri,
        decimateEvery: Int = 1,
        minDistMeters: Double = 0.0,
        maxPois: Int = 5000
    ): ImportResult {
        val input = context.contentResolver.openInputStream(uri)
            ?: return ImportResult()
        input.use { ins ->
            return parse(
                input = ins,
                decimateEvery = decimateEvery,
                minDistMeters = minDistMeters,
                maxPois = maxPois
            )
        }
    }

    /**
     * Parse a KML from an InputStream.
     * NOTE: keep the named parameter `input` for existing MapActivity call-sites.
     */
    fun parse(
        input: InputStream,
        decimateEvery: Int = 1,
        minDistMeters: Double = 0.0,
        maxPois: Int = 5000
    ): ImportResult {
        val safeDecimate = max(1, decimateEvery)

        val track = ArrayList<GeoPoint>(4096)
        val pois = ArrayList<POI>(min(512, maxPois))

        var truncatedPois = false

        // Placemark state
        var inPlacemark = false
        var placemarkName = ""
        var placemarkDesc = ""
        var placemarkPoint: GeoPoint? = null
        val placemarkExtras = LinkedHashMap<String, String>()

        // Geometry state (inside current Placemark)
        var geom = Geom.NONE

        // ExtendedData state
        var currentDataName: String? = null

        // For POI spacing filter
        var lastAcceptedPoi: GeoPoint? = null

        val parser: XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)

        // Buffering helps a lot on big KMLs.
        val buffered = if (input is BufferedInputStream) input else BufferedInputStream(input, 64 * 1024)
        parser.setInput(InputStreamReader(buffered, Charsets.UTF_8))

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name ?: ""
                    when (tag) {
                        "Placemark" -> {
                            inPlacemark = true
                            geom = Geom.NONE
                            placemarkName = ""
                            placemarkDesc = ""
                            placemarkPoint = null
                            placemarkExtras.clear()
                            currentDataName = null
                        }

                        "Point" -> if (inPlacemark) geom = Geom.POINT
                        "LineString" -> if (inPlacemark) geom = Geom.LINESTRING

                        "name" -> if (inPlacemark) placemarkName = readText(parser).trim()
                        "description" -> if (inPlacemark) placemarkDesc = readText(parser).trim()

                        // ExtendedData: <Data name="..."><value>...</value></Data>
                        "Data" -> if (inPlacemark) {
                            currentDataName = parser.getAttributeValue(null, "name")?.trim()
                        }

                        "value" -> if (inPlacemark && currentDataName != null) {
                            val v = readText(parser).trim()
                            val k = currentDataName!!
                            if (k.isNotBlank() && v.isNotBlank()) {
                                placemarkExtras[k] = v
                            }
                        }

                        // ExtendedData alternative: <SimpleData name="...">value</SimpleData>
                        "SimpleData" -> if (inPlacemark) {
                            val k = parser.getAttributeValue(null, "name")?.trim()
                            val v = readText(parser).trim()
                            if (!k.isNullOrBlank() && v.isNotBlank()) {
                                placemarkExtras[k] = v
                            }
                        }

                        "coordinates" -> {
                            val text = readText(parser).trim()
                            if (text.isNotBlank()) {
                                if (inPlacemark && geom == Geom.POINT) {
                                    placemarkPoint = parseSingleCoord(text)
                                } else {
                                    // Track coordinates often come outside/inside Placemark; we accept them.
                                    // But avoid absurd memory: decimate while parsing.
                                    parseCoordBlock(text) { lon, lat ->
                                        val idx = track.size
                                        if (idx % safeDecimate == 0) track.add(GeoPoint(lat, lon))
                                    }
                                }
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    val tag = parser.name ?: ""
                    when (tag) {
                        "Placemark" -> {
                            if (inPlacemark) {
                                val p = placemarkPoint
                                if (p != null && pois.size < maxPois) {
                                    val title = placemarkName.ifBlank { "Point d'intérêt" }
                                    val desc = placemarkDesc
                                    val extras = if (placemarkExtras.isEmpty()) emptyMap() else LinkedHashMap(placemarkExtras)

                                    val accept = if (minDistMeters <= 0.0) {
                                        true
                                    } else {
                                        val last = lastAcceptedPoi
                                        if (last == null) true else distanceMeters(last, p) >= minDistMeters
                                    }

                                    if (accept) {
                                        pois.add(POI(title = title, point = p, description = desc, extras = extras))
                                        lastAcceptedPoi = p
                                    }
                                } else if (p != null && pois.size >= maxPois) {
                                    truncatedPois = true
                                }

                                // reset
                                inPlacemark = false
                                geom = Geom.NONE
                                currentDataName = null
                            }
                        }

                        "Data" -> currentDataName = null
                    }
                }
            }
            event = parser.next()
        }

        return ImportResult(track = track, pois = pois, truncatedPois = truncatedPois)
    }

    private fun readText(parser: XmlPullParser): String {
        // When we hit START_TAG, calling next() can return TEXT then END_TAG
        return try {
            if (parser.next() == XmlPullParser.TEXT) {
                val text = parser.text ?: ""
                parser.nextTag() // move to END_TAG
                text
            } else ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseSingleCoord(token: String): GeoPoint? {
        // token: "lon,lat" or "lon,lat,alt"
        val parts = token.trim().split(',')
        if (parts.size < 2) return null
        val lon = parts[0].toDoubleOrNull() ?: return null
        val lat = parts[1].toDoubleOrNull() ?: return null
        return GeoPoint(lat, lon)
    }

    private fun parseCoordBlock(block: String, onLonLat: (lon: Double, lat: Double) -> Unit) {
        // block contains many tokens separated by whitespace; each token: "lon,lat[,alt]"
        val sb = StringBuilder(64)
        fun flushToken() {
            if (sb.isEmpty()) return
            val token = sb.toString()
            sb.setLength(0)

            val parts = token.split(',')
            if (parts.size >= 2) {
                val lon = parts[0].toDoubleOrNull()
                val lat = parts[1].toDoubleOrNull()
                if (lon != null && lat != null) {
                    onLonLat(lon, lat)
                }
            }
        }

        for (c in block) {
            if (c.isWhitespace()) {
                flushToken()
            } else {
                sb.append(c)
            }
        }
        flushToken()
    }

    private fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        // Haversine (fast + no Android dependency)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)

        val sinDLat = sin(dLat / 2.0)
        val sinDLon = sin(dLon / 2.0)

        val aa = sinDLat * sinDLat + cos(lat1) * cos(lat2) * sinDLon * sinDLon
        val c = 2.0 * atan2(sqrt(aa), sqrt(1.0 - aa))
        return 6371000.0 * c
    }
}
