package com.hikemvp.nature

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Polygon

object ProtectedAreasHelper {

    fun loadFromAssets(
        map: MapView,
        folder: FolderOverlay,
        assets: AssetManager,
        assetNames: List<String>,
        hatch: Boolean = false
    ): Int {
        folder.items.clear()
        var added = 0
        for (name in assetNames) {
            try {
                assets.open(name).use { ins ->
                    val txt = ins.bufferedReader(Charsets.UTF_8).readText()
                    val polys = parseGeoJsonPolygons(txt, map, hatch)
                    polys.forEach { folder.add(it) }
                    added += polys.size
                }
            } catch (_: Throwable) {
                // ignore missing files
            }
        }
        Log.i("ProtectedAreas", "Empreintes chargées: $added")
        map.invalidate()
        return added
    }

    fun parseGeoJsonPolygons(json: String, map: MapView, hatch: Boolean): List<Polygon> {
        val out = mutableListOf<Polygon>()

        fun coordsToPolygon(coords: JSONArray): Polygon {
            val poly = Polygon(map)
            val rings = ArrayList<List<GeoPoint>>()
            for (r in 0 until coords.length()) {
                val ringArr = coords.getJSONArray(r)
                val ring = ArrayList<GeoPoint>(ringArr.length())
                for (i in 0 until ringArr.length()) {
                    val p = ringArr.getJSONArray(i)
                    val lon = p.optDouble(0, Double.NaN)
                    val lat = p.optDouble(1, Double.NaN)
                    if (lat.isFinite() && lon.isFinite()) ring += GeoPoint(lat, lon)
                }
                if (ring.isNotEmpty()) rings += ring
            }
            if (rings.isNotEmpty()) {
                poly.points = rings.first()
                if (rings.size > 1) poly.holes = rings.drop(1)
                styleProtectedPolygon(poly, hatch)
            }
            return poly
        }

        val root = JSONObject(json)
        when (root.optString("type").uppercase()) {
            "FEATURECOLLECTION" -> {
                val feats = root.optJSONArray("features") ?: JSONArray()
                for (f in 0 until feats.length()) {
                    val feat = feats.optJSONObject(f) ?: continue
                    val geom = feat.optJSONObject("geometry") ?: continue
                    when (geom.optString("type").uppercase()) {
                        "POLYGON" -> out += coordsToPolygon(geom.getJSONArray("coordinates"))
                        "MULTIPOLYGON" -> {
                            val mcoords = geom.getJSONArray("coordinates")
                            for (i in 0 until mcoords.length()) {
                                out += coordsToPolygon(mcoords.getJSONArray(i))
                            }
                        }
                    }
                }
            }
            "FEATURE" -> {
                val geom = root.optJSONObject("geometry") ?: return out
                when (geom.optString("type").uppercase()) {
                    "POLYGON" -> out += coordsToPolygon(geom.getJSONArray("coordinates"))
                    "MULTIPOLYGON" -> {
                        val mcoords = geom.getJSONArray("coordinates")
                        for (i in 0 until mcoords.length()) {
                            out += coordsToPolygon(mcoords.getJSONArray(i))
                        }
                    }
                }
            }
            "POLYGON" -> out += coordsToPolygon(root.getJSONArray("coordinates"))
            "MULTIPOLYGON" -> {
                val mcoords = root.getJSONArray("coordinates")
                for (i in 0 until mcoords.length()) out += coordsToPolygon(mcoords.getJSONArray(i))
            }
        }
        return out
    }

    private fun styleProtectedPolygon(pg: Polygon, hatch: Boolean) {
        // Contour net
        pg.outlinePaint.apply {
            color = android.graphics.Color.parseColor("#1B5E20")
            alpha = 220
            strokeWidth = 4.5f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        // Remplissage : solide par défaut (fiable), hachuré si demandé
        pg.fillPaint.apply {
            if (hatch) {
                shader = makeHatchShader()
                style = Paint.Style.FILL
            } else {
                color = android.graphics.Color.argb(70, 27, 94, 32)
                style = Paint.Style.FILL
            }
            isAntiAlias = true
        }
        pg.setOnClickListener { _, _, _ -> true }
    }

    private fun makeHatchShader(
        bgColor: Int = android.graphics.Color.argb(40, 27, 94, 32),
        lineColor: Int = android.graphics.Color.argb(140, 27, 94, 32),
        spacingPx: Int = 12,
        strokePx: Float = 2f
    ): Shader {
        val size = spacingPx * 2
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor; style = Paint.Style.FILL }
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), pBg)
        val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColor
            style = Paint.Style.STROKE
            strokeWidth = strokePx
        }
        c.drawLine(0f, 0f, size.toFloat(), size.toFloat(), pLine)
        c.drawLine(-spacingPx.toFloat(), 0f, size.toFloat() - spacingPx, size.toFloat(), pLine)
        c.drawLine(0f, -spacingPx.toFloat(), size.toFloat(), size.toFloat() - spacingPx, pLine)
        return BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    private fun Double.isFinite(): Boolean =
        !this.isNaN() && this != Double.POSITIVE_INFINITY && this != Double.NEGATIVE_INFINITY
}