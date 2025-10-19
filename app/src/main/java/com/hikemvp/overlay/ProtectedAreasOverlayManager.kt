
package com.hikemvp.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.os.Environment
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import java.io.File
import java.nio.charset.Charset
import java.util.Locale

class ProtectedAreasOverlayManager(
    private val ctx: Context,
    private val map: MapView
) {
    private val overlays = mutableListOf<Polygon>()

    fun refresh() {
        // remove old
        overlays.forEach { map.overlays.remove(it) }
        overlays.clear()

        val docs = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: ctx.filesDir
        val names = listOf(
            "pnx-reserves-integrales.geojson",
            "pnx-coeur-aa-ama.geojson",
            "pnx-aoa.geojson"
        )

        var found = 0
        for (n in names) {
            val f = File(docs, n)
            if (f.exists() && f.isFile) {
                addFile(f)
                found++
            }
        }
        if (found == 0) {
            // fallback assets
            val am = ctx.assets
            for (n in names) {
                try {
                    am.open(n).use { ins ->
                        val txt = ins.bufferedReader(Charset.forName("UTF-8")).readText()
                        addText(txt)
                    }
                } catch (_: Throwable) {}
            }
        }

        android.util.Log.i("ProtectedAreas", "Polygones chargés: ${overlays.size}")
        map.invalidate()
    }

    private fun addFile(f: File) {
        runCatching {
            val txt = f.readText(Charset.forName("UTF-8"))
            addText(txt)
        }
    }

    private fun addText(json: String) {
        val polys = parseGeoJsonPolygons(json)
        polys.forEach {
            overlays += it
            map.overlays.add(0, it) // sous les markers
        }
    }

    // ---- parsing & style ----

    private fun stylePolygon(pg: Polygon) {
        pg.outlinePaint.apply {
            color = android.graphics.Color.parseColor("#1B5E20")
            alpha = 230
            strokeWidth = 4.5f * ctx.resources.displayMetrics.density
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        // hachurage avec fallback couleur pleine
        try {
            val hatch = makeHatchPaint()
            pg.fillPaint.apply {
                setShader(hatch.shader)
                style = Paint.Style.FILL
                isAntiAlias = true
            }
        } catch (_: Throwable) {
            pg.fillPaint.apply {
                color = android.graphics.Color.argb(70, 27, 94, 32)
                style = Paint.Style.FILL
                isAntiAlias = true
            }
        }
        pg.setOnClickListener { _, _, _ -> true }
    }

    private fun makeHatchPaint(
        bgColor: Int = android.graphics.Color.argb(40, 27, 94, 32),
        lineColor: Int = android.graphics.Color.argb(140, 27, 94, 32),
        spacingPx: Int = 12,
        strokePx: Float = 2f
    ): Paint {
        val size = spacingPx * 2
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), pBg)
        val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColor
            style = Paint.Style.STROKE
            strokeWidth = strokePx
        }
        c.drawLine(0f, 0f, size.toFloat(), size.toFloat(), pLine)
        c.drawLine(-spacingPx.toFloat(), 0f, size.toFloat() - spacingPx, size.toFloat(), pLine)
        c.drawLine(0f, -spacingPx.toFloat(), size.toFloat(), size.toFloat() - spacingPx, pLine)
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
    }

    private fun parseGeoJsonPolygons(json: String): List<Polygon> {
        val out = mutableListOf<Polygon>()

        fun coordsToPolygon(coords: JSONArray): Polygon {
            val poly = Polygon(map)
            val rings = mutableListOf<List<GeoPoint>>()
            for (r in 0 until coords.length()) {
                val ringArr = coords.getJSONArray(r)
                val ring = ArrayList<GeoPoint>(ringArr.length())
                for (i in 0 until ringArr.length()) {
                    val p = ringArr.getJSONArray(i)
                    val lon = p.getDouble(0)
                    val lat = p.getDouble(1)
                    ring += GeoPoint(lat, lon)
                }
                rings += ring
            }
            if (rings.isNotEmpty()) {
                poly.points = rings.first()
                if (rings.size > 1) poly.holes = rings.drop(1)
                stylePolygon(poly)
            }
            return poly
        }

        val root = JSONObject(json)
        when (root.optString("type", "").uppercase(Locale.ROOT)) {
            "FEATURECOLLECTION" -> {
                val feats = root.optJSONArray("features") ?: JSONArray()
                for (f in 0 until feats.length()) {
                    val feat = feats.optJSONObject(f) ?: continue
                    val geom = feat.optJSONObject("geometry") ?: continue
                    when (geom.optString("type", "").uppercase(Locale.ROOT)) {
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
                when (geom.optString("type", "").uppercase(Locale.ROOT)) {
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
                for (i in 0 until mcoords.length()) {
                    out += coordsToPolygon(mcoords.getJSONArray(i))
                }
            }
        }
        return out
    }
}
