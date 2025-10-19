package com.hikemvp.utils

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.osmdroid.util.GeoPoint
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

object KmlImporter {

    data class POI(
        val title: String,
        val point: GeoPoint,
        val description: String? = null,
        val extras: Map<String, String> = emptyMap()
    )

    data class Result(
        val track: List<GeoPoint>,
        val pois: List<POI>,
        val trackName: String? = null,
        val trackDescription: String? = null,
        val trackExtras: Map<String, String> = emptyMap()
    )

    fun parse(
        context: Context,
        uri: Uri,
        decimateEvery: Int = 5,
        minDistMeters: Double = 8.0,
        maxPois: Int = 2000
    ): Result {
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return Result(emptyList(), emptyList())
            return parse(input, decimateEvery, minDistMeters, maxPois)
        }
    }

    fun parse(
        input: InputStream,
        decimateEvery: Int = 5,
        minDistMeters: Double = 8.0,
        maxPois: Int = 2000
    ): Result {
        val parser = Xml.newPullParser()
        parser.setFeature(Xml.FEATURE_RELAXED, true)
        parser.setInput(input, "UTF-8")

        var event = parser.eventType

        var inPlacemark = false
        var inLineString = false
        var inPoint = false
        var inName = false
        var inDescription = false
        var inExtendedData = false
        var inValue = false
        var currentDataName: String? = null
        var currentSimpleName: String? = null

        var currentName: String? = null
        var currentDescription: StringBuilder? = null
        var currentExtras: MutableMap<String, String> = linkedMapOf()

        var coordBuffer: StringBuilder? = null

        val trackPoints = ArrayList<GeoPoint>(1024)
        val pois = ArrayList<POI>(256)

        var trackName: String? = null
        var trackDesc: String? = null
        var trackExtras: Map<String, String> = emptyMap()

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase()) {
                        "placemark" -> {
                            inPlacemark = true
                            inLineString = false
                            inPoint = false
                            inName = false
                            inDescription = false
                            inExtendedData = false
                            inValue = false
                            currentDataName = null
                            currentSimpleName = null
                            currentName = null
                            currentDescription = null
                            currentExtras = linkedMapOf()
                            coordBuffer = null
                        }
                        "name" -> if (inPlacemark) inName = true
                        "description" -> if (inPlacemark) {
                            inDescription = true
                            if (currentDescription == null) currentDescription = StringBuilder()
                        }
                        "linestring" -> if (inPlacemark) inLineString = true
                        "point" -> if (inPlacemark) inPoint = true
                        "coordinates" -> if (inPlacemark && (inLineString || inPoint)) {
                            coordBuffer = StringBuilder(4096)
                        }
                        "extendeddata" -> if (inPlacemark) inExtendedData = true
                        "data" -> if (inExtendedData) {
                            currentDataName = parser.getAttributeValue(null, "name")
                        }
                        "value" -> if (inExtendedData && currentDataName != null) {
                            inValue = true
                        }
                        "simpledata" -> if (inExtendedData) {
                            currentSimpleName = parser.getAttributeValue(null, "name")
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val txt = parser.text ?: ""
                    if (inPlacemark && txt.isNotEmpty()) {
                        when {
                            inName -> {
                                val t = txt.trim()
                                if (t.isNotEmpty()) currentName = t
                            }
                            inDescription -> {
                                currentDescription?.append(txt)
                            }
                            inValue && currentDataName != null -> {
                                val t = txt.trim()
                                if (t.isNotEmpty()) currentExtras[currentDataName!!] = appendOrSet(currentExtras[currentDataName!!], t)
                            }
                            currentSimpleName != null -> {
                                val t = txt.trim()
                                if (t.isNotEmpty()) currentExtras[currentSimpleName!!] = appendOrSet(currentExtras[currentSimpleName!!], t)
                            }
                            coordBuffer != null -> {
                                coordBuffer!!.append(txt)
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name.lowercase()) {
                        "name" -> inName = false
                        "description" -> inDescription = false
                        "value" -> inValue = false
                        "data" -> currentDataName = null
                        "simpledata" -> currentSimpleName = null
                        "coordinates" -> {
                            val coords = coordBuffer?.toString()?.trim() ?: ""
                            coordBuffer = null
                            if (inLineString) {
                                appendLineString(trackPoints, coords, decimateEvery, minDistMeters)
                            } else if (inPoint && pois.size < maxPois) {
                                parsePoint(coords)?.let { gp ->
                                    val title = (currentName ?: "POI").ifBlank { "POI" }
                                    val desc = currentDescription?.toString()?.trim().takeUnless { it.isNullOrEmpty() }
                                    pois += POI(title, gp, desc, LinkedHashMap(currentExtras))
                                }
                            }
                        }
                        "linestring" -> {
                            inLineString = false
                            trackName = trackName ?: currentName
                            if (trackDesc == null) {
                                val d = currentDescription?.toString()?.trim()
                                if (!d.isNullOrEmpty()) trackDesc = d
                            }
                            if (trackExtras.isEmpty() && currentExtras.isNotEmpty()) {
                                trackExtras = LinkedHashMap(currentExtras)
                            }
                        }
                        "point" -> inPoint = false
                        "placemark" -> {
                            inPlacemark = false
                            inName = false
                            inDescription = false
                            inExtendedData = false
                            inValue = false
                            currentDataName = null
                            currentSimpleName = null
                        }
                    }
                }
            }
            event = parser.next()
        }
        return Result(trackPoints, pois, trackName, trackDesc, trackExtras)
    }

    private fun appendLineString(
        out: MutableList<GeoPoint>,
        coordsText: String,
        decimateEvery: Int,
        minDistMeters: Double
    ) {
        if (coordsText.isEmpty()) return
        val tokens = coordsText.split(Regex("""\s+""")).filter { it.isNotBlank() }
        var lastKept: GeoPoint? = out.lastOrNull()
        for ((idx, tok) in tokens.withIndex()) {
            if (decimateEvery > 1 && (idx % decimateEvery != 0)) continue
            val parts = tok.split(',')
            if (parts.size < 2) continue
            val lon = parts[0].toDoubleOrNull() ?: continue
            val lat = parts[1].toDoubleOrNull() ?: continue
            val alt = parts.getOrNull(2)?.toDoubleOrNull()
            val gp = if (alt != null) GeoPoint(lat, lon, alt) else GeoPoint(lat, lon)
            if (lastKept != null) {
                val d = lastKept.distanceToAsDouble(gp)
                if (d < minDistMeters) continue
            }
            out += gp
            lastKept = gp
        }
    }

    private fun parsePoint(coords: String): GeoPoint? {
        val parts = coords.trim().split(',', limit = 3)
        if (parts.size < 2) return null
        val lon = parts[0].toDoubleOrNull() ?: return null
        val lat = parts[1].toDoubleOrNull() ?: return null
        val alt = parts.getOrNull(2)?.toDoubleOrNull()
        return if (alt != null) GeoPoint(lat, lon, alt) else GeoPoint(lat, lon)
    }

    private fun appendOrSet(existing: String?, value: String): String =
        if (existing == null) value else existing + "\n" + value
}
