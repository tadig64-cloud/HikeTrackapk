
package com.hikemvp.nature

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.Charset

class NatureImportActivity : AppCompatActivity() {

    private val openDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            Toast.makeText(this, "Import annulé", Toast.LENGTH_SHORT).show()
            finish()
            return@registerForActivityResult
        }
        try {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
            val txt = readTextFromUri(uri)
            val normalized = normalizeToNaturePoiJson(txt)
            val arr = JSONArray(normalized)
            natureUserFile().writeText(arr.toString(2), Charsets.UTF_8)
            val name = getDisplayName(uri) ?: "fichier"
            Toast.makeText(this, "Import réussi depuis $name (${arr.length()} POI)", Toast.LENGTH_LONG).show()
            setResult(Activity.RESULT_OK)
        } catch (t: Throwable) {
            t.printStackTrace()
            Toast.makeText(this, "Échec import: ${t.message}", Toast.LENGTH_LONG).show()
            setResult(Activity.RESULT_CANCELED)
        } finally {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openDoc.launch(arrayOf("application/json", "application/*", "text/*"))
    }

    private fun natureUserFile(): File = File(filesDir, "nature_pois_user.json")

    private fun readTextFromUri(uri: Uri): String {
        contentResolver.openInputStream(uri).use { ins ->
            requireNotNull(ins) { "Flux introuvable" }
            return ins.readBytes().toString(Charset.forName("UTF-8"))
        }
    }

    private fun getDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && c.moveToFirst()) c.getString(nameIndex) else null
            }
        } catch (_: Throwable) { null }
    }

    private fun normalizeToNaturePoiJson(input: String): String {
        val trimmed = input.trim()
        if (trimmed.startsWith("[")) {
            JSONArray(trimmed) // validate
            return trimmed
        }
        if (trimmed.startsWith("{")) {
            val obj = JSONObject(trimmed)
            if (obj.optString("type").equals("FeatureCollection", ignoreCase = true)) {
                val out = JSONArray()
                val feats = obj.optJSONArray("features") ?: JSONArray()
                for (i in 0 until feats.length()) {
                    val f = feats.optJSONObject(i) ?: continue
                    val geom = f.optJSONObject("geometry") ?: continue
                    val gtype = geom.optString("type", "")
                    val props = f.optJSONObject("properties") ?: JSONObject()
                    val (lat, lon) = when (gtype.uppercase()) {
                        "POINT" -> {
                            val coords = geom.optJSONArray("coordinates") ?: JSONArray()
                            val lon = coords.optDouble(0, Double.NaN)
                            val lat = coords.optDouble(1, Double.NaN)
                            lat to lon
                        }
                        "MULTIPOINT" -> {
                            val arr = geom.optJSONArray("coordinates") ?: JSONArray()
                            val first = arr.optJSONArray(0) ?: JSONArray()
                            val lon = first.optDouble(0, Double.NaN)
                            val lat = first.optDouble(1, Double.NaN)
                            lat to lon
                        }
                        "POLYGON", "MULTIPOLYGON", "LINESTRING", "MULTILINESTRING" -> {
                            val all = extractAllCoords(geom)
                            if (all.isNotEmpty()) {
                                val avgLat = all.map { it.first }.average()
                                val avgLon = all.map { it.second }.average()
                                avgLat to avgLon
                            } else Double.NaN to Double.NaN
                        }
                        else -> Double.NaN to Double.NaN
                    }
                    if (lat.isFinite() && lon.isFinite()) {
                        val item = JSONObject()
                        item.put("id", props.optString("id", "poi_$i"))
                        val rawType = (props.optString("type") + " " + props.optString("amenity") + " " + props.optString("natural") + " " + props.optString("tourism")).lowercase()
                        val type = when {
                            "shelter" in rawType || "refuge" in rawType || "cabin" in rawType || "alpine_hut" in rawType -> "SHELTER"
                            "spring" in rawType || "reserve" in rawType || "protected" in rawType || "park" in rawType -> "PROTECTED"
                            "tree" in rawType || "arbre" in rawType -> "TREE"
                            else -> "PROTECTED"
                        }
                        item.put("type", type)
                        val name = listOf(
                            props.optString("name"),
                            props.optString("nom"),
                            props.optString("title"),
                            props.optString("refuge"),
                        ).firstOrNull { it.isNotBlank() } ?: "Point"
                        item.put("name", name)
                        item.put("lat", lat)
                        item.put("lon", lon)
                        val radius = props.optDouble("radiusM", Double.NaN)
                        item.put("radiusM", if (radius.isFinite()) radius else defaultRadiusFor(type))
                        val note = props.optString("description", props.optString("desc", ""))
                        if (note.isNotBlank()) item.put("note", note)
                        out.put(item)
                    }
                }
                return out.toString()
            }
        }
        throw IllegalArgumentException("Format non reconnu. Fournis un JSON [ ... ] ou un GeoJSON FeatureCollection.")
    }

    private fun defaultRadiusFor(type: String): Double = when (type.uppercase()) {
        "SHELTER" -> 50.0
        "TREE" -> 25.0
        else -> 500.0
    }

    private fun extractAllCoords(geom: JSONObject): List<Pair<Double, Double>> {
        val type = geom.optString("type", "").uppercase()
        val out = mutableListOf<Pair<Double, Double>>()

        fun addCoordPair(arr: JSONArray) {
            val lon = arr.optDouble(0, Double.NaN)
            val lat = arr.optDouble(1, Double.NaN)
            if (lat.isFinite() && lon.isFinite()) out += lat to lon
        }

        when (type) {
            "LINESTRING" -> {
                val coords = geom.optJSONArray("coordinates") ?: JSONArray()
                for (i in 0 until coords.length()) addCoordPair(coords.optJSONArray(i) ?: JSONArray())
            }
            "MULTILINESTRING" -> {
                val m = geom.optJSONArray("coordinates") ?: JSONArray()
                for (i in 0 until m.length()) {
                    val line = m.optJSONArray(i) ?: JSONArray()
                    for (j in 0 until line.length()) addCoordPair(line.optJSONArray(j) ?: JSONArray())
                }
            }
            "POLYGON" -> {
                val rings = geom.optJSONArray("coordinates") ?: JSONArray()
                for (i in 0 until rings.length()) {
                    val ring = rings.optJSONArray(i) ?: JSONArray()
                    for (j in 0 until ring.length()) addCoordPair(ring.optJSONArray(j) ?: JSONArray())
                }
            }
            "MULTIPOLYGON" -> {
                val polys = geom.optJSONArray("coordinates") ?: JSONArray()
                for (i in 0 until polys.length()) {
                    val poly = polys.optJSONArray(i) ?: JSONArray()
                    for (j in 0 until poly.length()) {
                        val ring = poly.optJSONArray(j) ?: JSONArray()
                        for (k in 0 until ring.length()) addCoordPair(ring.optJSONArray(k) ?: JSONArray())
                    }
                }
            }
        }
        return out
    }

    private fun Double.isFinite(): Boolean = !isNaN() && this != Double.POSITIVE_INFINITY && this != Double.NEGATIVE_INFINITY
}
