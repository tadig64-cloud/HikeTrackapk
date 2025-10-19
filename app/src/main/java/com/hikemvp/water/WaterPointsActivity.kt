package com.hikemvp.water

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.appbar.MaterialToolbar
import com.hikemvp.R
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class WaterPointsActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var myLocation: MyLocationNewOverlay
    private val prefs by lazy { getSharedPreferences("hiketrack_prefs", MODE_PRIVATE) }
    private val key = "water_points_json"

    private val reqPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ){ _ -> ensureLocationReady() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this))
        setContentView(R.layout.activity_water_points)

        // Toolbar
        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setSupportActionBar(this)
            title = getString(R.string.water_points_title)
            setNavigationOnClickListener { finish() }
            setOnMenuItemClickListener { mi ->
                when (mi.itemId) {
                    R.id.action_share_all -> { shareAllAsGpx(); true }
                    R.id.action_clear_all -> { confirmClearAll(); true }
                    else -> false
                }
            }
        }

        // Map
        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        myLocation = MyLocationNewOverlay(GpsMyLocationProvider(this), map).apply {
            enableMyLocation()
        }
        map.overlays.add(myLocation)

        // Ajout par appui long
        val receiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
            override fun longPressHelper(p: GeoPoint?): Boolean {
                if (p == null) return false
                promptAddAt(p)
                return true
            }
        }
        map.overlays.add(MapEventsOverlay(receiver))

        // Boutons bas
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAddHere).setOnClickListener {
            val loc = myLocation.lastFix
            if (loc == null) {
                Toast.makeText(this, getString(R.string.err_no_location_yet), Toast.LENGTH_SHORT).show()
            } else {
                promptAddAt(GeoPoint(loc.latitude, loc.longitude))
            }
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnShare).setOnClickListener { shareAllAsGpx() }

        // Affichage initial
        ensureLocationReady()
        loadAndRenderMarkers()
    }

    private fun ensureLocationReady() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            reqPerms.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        myLocation.enableMyLocation()
        myLocation.runOnFirstFix {
            myLocation.lastFix?.let { fix ->
                runOnUiThread {
                    map.controller.setZoom(16.0)
                    map.controller.animateTo(GeoPoint(fix.latitude, fix.longitude))
                }
            }
        }
    }

    // ===== Stockage JSON simple =====
    private fun load(): JSONArray =
        try { JSONArray(prefs.getString(key, "[]")) } catch (_: Throwable) { JSONArray() }

    private fun save(arr: JSONArray) {
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun addPoint(lat: Double, lon: Double, name: String?, note: String?) {
        val arr = load()
        val obj = JSONObject().apply {
            put("lat", lat)
            put("lon", lon)
            if (!name.isNullOrBlank()) put("name", name)
            if (!note.isNullOrBlank()) put("note", note)
        }
        arr.put(obj)
        save(arr)
        loadAndRenderMarkers()
        Toast.makeText(this, getString(R.string.water_added_ok), Toast.LENGTH_SHORT).show()
    }

    private fun clearAll() {
        save(JSONArray())
        loadAndRenderMarkers()
        Toast.makeText(this, getString(R.string.water_cleared_ok), Toast.LENGTH_SHORT).show()
    }

    // ===== UI =====
    private fun promptAddAt(p: GeoPoint) {
        val inputName = EditText(this).apply { hint = getString(R.string.wp_name_hint) }
        val inputNote = EditText(this).apply { hint = getString(R.string.wp_note_hint) }

        val content = com.google.android.material.card.MaterialCardView(this).apply {
            useCompatPadding = true
            val ll = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                val pad = (16 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
                addView(inputName)
                addView(inputNote.apply { setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0) })
            }
            addView(ll)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.water_add_here_title, p.latitude, p.longitude))
            .setView(content)
            .setPositiveButton(R.string.save) { _, _ ->
                addPoint(p.latitude, p.longitude, inputName.text?.toString(), inputNote.text?.toString())
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun loadAndRenderMarkers() {
        // retire anciens marqueurs (on garde l’overlay de localisation)
        map.overlays.removeAll { it is Marker && it != myLocation }

        val arr = load()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val gp = GeoPoint(o.getDouble("lat"), o.getDouble("lon"))
            val m = Marker(map).apply {
                position = gp
                title = o.optString("name", getString(R.string.water_point_label))
                subDescription = o.optString("note", "")
                icon = ContextCompat.getDrawable(this@WaterPointsActivity, R.drawable.ic_water_drop_24)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { marker, _ ->
                    val fix = myLocation.lastFix
                    val dist = if (fix != null) {
                        GeoPoint(fix.latitude, fix.longitude).distanceToAsDouble(marker.position).roundToInt()
                    } else null
                    val distStr = dist?.let { if (it >= 1000) String.format("%.2f km", it / 1000.0) else "$it m" } ?: getString(R.string.weather_na)
                    val txt = buildString {
                        append(marker.title ?: getString(R.string.water_point_label))
                        append("\n")
                        if (!marker.subDescription.isNullOrBlank()) append(marker.subDescription).append("\n")
                        append("Lat: %.5f, Lon: %.5f".format(marker.position.latitude, marker.position.longitude))
                        append("\n")
                        append(getString(R.string.water_distance_fmt, distStr))
                    }
                    AlertDialog.Builder(this@WaterPointsActivity)
                        .setTitle(R.string.water_point_details)
                        .setMessage(txt)
                        .setPositiveButton(android.R.string.ok, null)
                        .setNegativeButton(R.string.water_delete_this) { _, _ ->
                            removePoint(marker.position.latitude, marker.position.longitude)
                        }
                        .show()
                    true
                }
            }
            map.overlays.add(m)
        }
        map.invalidate()
    }

    private fun removePoint(lat: Double, lon: Double) {
        val arr = load()
        val kept = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val la = o.getDouble("lat")
            val lo = o.getDouble("lon")
            if (kotlin.math.abs(la - lat) > 1e-7 || kotlin.math.abs(lo - lon) > 1e-7) {
                kept.put(o)
            }
        }
        save(kept)
        loadAndRenderMarkers()
        Toast.makeText(this, getString(R.string.water_deleted_ok), Toast.LENGTH_SHORT).show()
    }

    // ===== Partage GPX simple =====
    private fun toGpx(arr: JSONArray): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<gpx version="1.1" creator="HikeTrack" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val lat = o.getDouble("lat")
            val lon = o.getDouble("lon")
            val name = o.optString("name", "Water")
            val note = o.optString("note", "")
            sb.append("""  <wpt lat="$lat" lon="$lon">""").append('\n')
            sb.append("""    <name>${escapeXml(name)}</name>""").append('\n')
            if (note.isNotBlank()) sb.append("""    <desc>${escapeXml(note)}</desc>""").append('\n')
            sb.append("""  </wpt>""").append('\n')
        }
        sb.append("</gpx>")
        return sb.toString()
    }
    private fun escapeXml(s: String): String =
        s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")

    private fun shareAllAsGpx() {
        val arr = load()
        if (arr.length() == 0) {
            Toast.makeText(this, getString(R.string.saved_tracks_empty), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val gpx = toGpx(arr)
            val dir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS) ?: filesDir
            val outFile = File(dir, "water_points.gpx")
            FileOutputStream(outFile).use { it.write(gpx.toByteArray(Charsets.UTF_8)) }
            val uri: Uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", outFile)

            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/gpx+xml"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, getString(R.string.share_gpx)))
        } catch (t: Throwable) {
            t.printStackTrace()
            // fallback texte
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/xml"
                putExtra(Intent.EXTRA_TEXT, toGpx(load()))
            }
            startActivity(Intent.createChooser(send, getString(R.string.share_gpx)))
        }
    }

    private fun confirmClearAll() {
        AlertDialog.Builder(this)
            .setTitle(R.string.water_clear_all_title)
            .setMessage(R.string.water_clear_all_msg)
            .setPositiveButton(android.R.string.ok) { _, _ -> clearAll() }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }
}
