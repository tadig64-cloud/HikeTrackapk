package com.hikemvp
import com.hikemvp.group.GroupBridge
import com.hikemvp.utils.OsmdroidStorage
import com.hikemvp.group.GroupPrefs
import com.hikemvp.map.MapCameraPrefs
import com.hikemvp.profile.ProfilePrefs
import android.content.SharedPreferences
import android.util.Log
import android.widget.ArrayAdapter


import android.widget.FrameLayout
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.InputFilter
import android.text.InputType
import android.view.Menu
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.text.HtmlCompat
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.slider.RangeSlider
import com.hikemvp.info.InfoHubActivity
import com.hikemvp.info.QuickWeatherDialogFragment
import com.hikemvp.storage.ActiveTrackRepo
import com.hikemvp.tiles.IgnWmtsTileSource
import com.hikemvp.waypoints.WaypointDialogFragment
import com.hikemvp.waypoints.WaypointStorage
import com.hikemvp.waypoints.WaypointMeta
import com.hikemvp.profile.ProfileActivity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.modules.OfflineTileProvider
import org.osmdroid.tileprovider.tilesource.FileBasedTileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.max as kmax
import com.hikemvp.aide.AidePreventionActivity
import androidx.core.view.isVisible
import com.hikemvp.waypoints.WaypointAutoRestore
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.provider.Settings
import org.osmdroid.views.overlay.Polygon
import com.hikemvp.group.GroupMember

import com.hikemvp.group.GroupNearbyService

class MapActivity : AppCompatActivity() {
    // A7.2: Nearby group → UI updates
    private val groupConnectedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == "com.hikemvp.group.CONNECTED") {
                val names = intent.getStringArrayExtra("names") ?: emptyArray()
                val base = toolbar.subtitle?.toString()?.takeIf { it.isNotBlank() } ?: ""
                val add = if (names.isEmpty()) "" else "  •  " + names.joinToString(", ")
                toolbar.subtitle = (base + add).trim()
            }
        }
    }
    private val groupPosReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == "com.hikemvp.group.POSITION") {
                val id = intent.getStringExtra("id") ?: return
                val lat = intent.getDoubleExtra("lat", java.lang.Double.NaN)
                val lon = intent.getDoubleExtra("lon", java.lang.Double.NaN)
                if (!lat.isNaN() && !lon.isNaN()) {
                    val gp = org.osmdroid.util.GeoPoint(lat, lon)
                    try { com.hikemvp.group.GroupBridge.upsert(id, gp) } catch (_: Throwable) {}
                    map.invalidate()
                }
            }
        }
    }
    private val groupMarkersReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != "com.hikemvp.group.ACTION_GROUP_MARKERS") return
            val json = intent.getStringExtra("extra_markers_json") ?: return
            try {
                val arr = JSONArray(json)
                val members = mutableListOf<GroupMember>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id", i.toString())
                    val name = o.optString("name", id)
                    val lat = o.optDouble("lat", 0.0)
                    val lon = o.optDouble("lon", 0.0)
                    val color = o.optInt("color", 0xFF888888.toInt())
                    members += GroupMember(id = id, name = name, point = GeoPoint(lat, lon), color = color)
                }
                com.hikemvp.group.GroupBridge.overlay?.setMembers(
                    members, com.hikemvp.GroupGlobals.mapView!!
                )
            } catch (_: Throwable) { /* no-op */ }
        }
    }

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(this) }
    private var autoFollowEnabled: Boolean = true

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            ProfilePrefs.KEY_HUD_WEATHER -> {
                val enabled = prefs.getBoolean(ProfilePrefs.KEY_HUD_WEATHER, true)
                applyHudWeather(enabled)
            }
            ProfilePrefs.KEY_AUTO_FOLLOW -> {
                val enabled = prefs.getBoolean(ProfilePrefs.KEY_AUTO_FOLLOW, true)
                applyAutoFollow(enabled)
            }
        }
    }

    // --- KML minimal parser: LineString -> track points, Point -> POI markers ---
    private fun parseKmlFromStream(input: java.io.InputStream): Pair<List<GeoPoint>, List<Marker>> {
        val text = input.bufferedReader(Charsets.UTF_8).readText()


        // 1) Trace (LineString)
        val trackPts = mutableListOf<GeoPoint>()
        runCatching {
            // Récupère le premier <coordinates> à l’intérieur d’un <LineString>
            val lineStringRegex = Regex(
                "<LineString[\\s\\S]*?<coordinates>([\\s\\S]*?)</coordinates>[\\s\\S]*?</LineString>",
                RegexOption.IGNORE_CASE
            )
            val coordsBlock = lineStringRegex.find(text)?.groupValues?.get(1)
            coordsBlock?.trim()?.let { block ->
                // Les coordonnées KML sont "lon,lat[,alt]" séparées par des espaces ou des retours ligne
                val tokens = block.split(Regex("\\s+")).filter { it.isNotBlank() }
                for (tok in tokens) {
                    val parts = tok.split(',')
                    if (parts.size >= 2) {
                        val lon = parts[0].toDoubleOrNull() ?: continue
                        val lat = parts[1].toDoubleOrNull() ?: continue
                        val alt = parts.getOrNull(2)?.toDoubleOrNull()
                        trackPts += if (alt != null) GeoPoint(lat, lon, alt) else GeoPoint(lat, lon)
                    }
                }
            }
        }

        // 2) POI (Placemark avec Point)
        val poiMarkers = mutableListOf<Marker>()
        runCatching {
            // Matche chaque Placemark qui contient un Point
            val placemarkRegex = Regex(
                "<Placemark[\\s\\S]*?</Placemark>",
                RegexOption.IGNORE_CASE
            )
            val nameRegex = Regex("<name>([\\s\\S]*?)</name>", RegexOption.IGNORE_CASE)
            val pointRegex = Regex(
                "<Point[\\s\\S]*?<coordinates>([\\s\\S]*?)</coordinates>[\\s\\S]*?</Point>",
                RegexOption.IGNORE_CASE
            )

            placemarkRegex.findAll(text).forEach { pm ->
                val block = pm.value
                val point = pointRegex.find(block)?.groupValues?.get(1)?.trim() ?: return@forEach
                val name = nameRegex.find(block)?.groupValues?.get(1)?.trim() ?: "POI"
                // On ne prend que la première coordonnée du Point
                val parts = point.split(',').map { it.trim() }
                if (parts.size >= 2) {
                    val lon = parts[0].toDoubleOrNull()
                    val lat = parts[1].toDoubleOrNull()
                    if (lat != null && lon != null) {
                        val m = Marker(map).apply {
                            position = GeoPoint(lat, lon)
                            title = name
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            icon = ContextCompat.getDrawable(this@MapActivity, android.R.drawable.ic_menu_mylocation)
                        }
                        poiMarkers += m
                    }
                }
            }
        }

        return trackPts to poiMarkers
    }



    // --- Patch: éviter "Unresolved reference 'leaveTrackEdit'" ---
    private fun leaveTrackEdit() {
        // Essaie de quitter proprement le mode édition de trace, sans casser l'existant.
        try {
            val f = this::class.java.getDeclaredFields().firstOrNull { it.name == "isTrackEditMode" || it.name == "isEditingTrack" }
            f?.let { it.isAccessible = true; if (it.type == java.lang.Boolean.TYPE || it.type == java.lang.Boolean::class.java) it.setBoolean(this, false) }
        } catch (_: Throwable) {}

        // Cache un éventuel panneau d'édition
        try {
            val viewField = this::class.java.getDeclaredFields().firstOrNull { it.name == "editTrackPanel" || it.name == "trackEditPanel" }
            viewField?.let { it.isAccessible = true; (it.get(this) as? android.view.View)?.let { v -> v.visibility = android.view.View.GONE } }
        } catch (_: Throwable) {}

        // Retire des overlays temporaires connus
        try {
            val toRemove = map.overlays.filter { it?.javaClass?.simpleName?.contains("Edit", ignoreCase = true) == true }
            map.overlays.removeAll(toRemove)
            map.invalidate()
        } catch (_: Throwable) {}
    }
    // Surcharge tolérante si l'appelant passait un booléen (save/discard)
    private fun leaveTrackEdit(save: Boolean) {
        leaveTrackEdit()
    }

    // Helper pour éviter les fuites de fenêtre provenant d'un PopupMenu/ListPopupWindow
    private fun safeStartActivity(intent: Intent) {
        try { toolbar.dismissPopupMenus() } catch (_: Throwable) {}
        try { startActivity(intent) } catch (_: Throwable) {}
    }

    // --- Couche polygones Parcs/Réserves ---
    // --- Couche "Zones protégées" : parsing + attache/détache propres ---
    private val protectedAreaOverlays = mutableListOf<Polygon>()

    // Remplacer TOUT le bloc existant par ceci
    private fun addProtectedAreaFileIfExists(dir: File, name: String, attachToMap: Boolean) {
        val f = File(dir, name)
        if (!f.exists() || !f.isFile) return
        runCatching {
            val json = f.readText(Charsets.UTF_8)
            val polys = parseGeoJsonPolygons(json)

            // Couleurs spécifiques par fichier (mêmes règles que ta sauvegarde)
            val (fill, stroke) = colorsForProtectedFile(name)

            polys.forEach { pg ->
                // On écrase le style par défaut appliqué dans parseGeoJsonPolygons()
                pg.fillPaint.color = fill
                pg.outlinePaint.color = stroke
                pg.outlinePaint.strokeWidth = 2f * resources.displayMetrics.density

                protectedAreaOverlays += pg
                if (attachToMap) map.overlays.add(0, pg)
            }
            map.invalidate()
        }
    }

    /** Recharge la liste des polygones depuis Documents (ou assets) SANS forcer l'affichage.
     *  Si attachToMap==true, on attache la couche à la carte, sinon on ne fait que préparer en mémoire. */
    private fun refreshProtectedAreas(attachToMap: Boolean) {
        // Détache tout ce qui existe déjà
        protectedAreaOverlays.forEach { map.overlays.remove(it) }
        protectedAreaOverlays.clear()

        val docs = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: filesDir
        android.util.Log.i("ProtectedAreas", "Documents dir = ${docs.absolutePath}")
        runCatching {
            val listed = docs.listFiles()?.joinToString { it.name } ?: "(vide)"
            android.util.Log.i("ProtectedAreas", "Fichiers dans Documents: $listed")
        }

        val names = listOf(
            "pnx-reserves-integrales.geojson",
            "pnx-coeur-aa-ama.geojson",
            "pnx-aoa.geojson"
        )

        var found = 0
        names.forEach { n ->
            val f = File(docs, n)
            if (f.exists() && f.isFile) {
                addProtectedAreaFileIfExists(docs, n, attachToMap)
                found++
            }
        }

        // fallback: assets
        if (found == 0) {
            val am = assets
            names.forEach { n ->
                runCatching {
                    am.open(n).use { ins ->
                        val txt = ins.bufferedReader(Charsets.UTF_8).readText()
                        val polys = parseGeoJsonPolygons(txt)

                        // >>> APPLIQUE LES COULEURS SPÉCIFIQUES ICI <<<
                        val (fill, stroke) = colorsForProtectedFile(n)
                        polys.forEach { pg ->
                            // on écrase le style par défaut (vert) posé au parsing
                            pg.fillPaint.shader = null
                            pg.fillPaint.color = fill
                            pg.fillPaint.isAntiAlias = true
                            pg.fillPaint.style = android.graphics.Paint.Style.FILL

                            pg.outlinePaint.color = stroke
                            pg.outlinePaint.strokeWidth = 2f * resources.displayMetrics.density
                            pg.outlinePaint.isAntiAlias = true
                            pg.outlinePaint.style = android.graphics.Paint.Style.STROKE

                            protectedAreaOverlays += pg
                            if (attachToMap) map.overlays.add(0, pg)
                        }
                    }
                }.onFailure {
                    android.util.Log.w("ProtectedAreas", "Pas trouvé dans assets: $n")
                }
            }
            if (attachToMap) map.invalidate()
        }
    }
    // --- Import générique par extension/MIME (GPX / KML / CSV) ---
    private fun onImportDocument(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Throwable) {}

        val name = DocumentFile.fromSingleUri(this, uri)?.name ?: "fichier"
        val lower = name.lowercase(Locale.getDefault())

        fun addPoisFromCsv(lines: List<String>) {
            // CSV simple: lat,lon[,name]
            lines.forEach { line ->
                val parts = line.trim().split(',', ';').map { it.trim() }
                if (parts.size >= 2) {
                    val lat = parts[0].toDoubleOrNull()
                    val lon = parts[1].toDoubleOrNull()
                    val title = parts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "POI"
                    if (lat != null && lon != null) {
                        val m = Marker(map).apply {
                            position = GeoPoint(lat, lon)
                            this.title = title
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            icon = ContextCompat.getDrawable(
                                this@MapActivity, android.R.drawable.ic_menu_mylocation
                            )
                        }


                    }
                }
            }
            map.invalidate()
        }

        runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                when {
                    // ----- GPX -----
                    lower.endsWith(".gpx") || (
                            contentResolver.getType(uri)?.contains("gpx", ignoreCase = true) == true
                            ) -> {
                        val poly = GpxUtils.parseToPolyline(input)
                        addImportedTrackToMap(poly.actualPoints, name.removeSuffix(".gpx"))
                        TrackStore.setAll(poly.actualPoints)
                        resetStats()
                        if (poly.actualPoints.size > 1) computeStatsFrom(poly.actualPoints)
                        updateStatsUI()
                        Toast.makeText(this, "GPX importé", Toast.LENGTH_SHORT).show()
                    }

                    // ----- KML -----
                    lower.endsWith(".kml") || (
                        contentResolver.getType(uri)?.contains("kml", ignoreCase = true) == true
                    ) -> {
                        Thread {
                            try {
                                val res = com.hikemvp.utils.KmlImporter.parse(
                                    this@MapActivity, uri,
                                    decimateEvery = 5,
                                    minDistMeters = 8.0,
                                    maxPois = 2000
                                )
                                runOnUiThread {
                                    if (res.track.size > 1) {
                                        val polyline = org.osmdroid.views.overlay.Polyline().apply {
                                            setPoints(res.track)
                                            outlinePaint.color = nextColor()
                                            outlinePaint.strokeWidth = 4f * resources.displayMetrics.density
                                            outlinePaint.isAntiAlias = true
                                        }
                                        map.overlays.add(polyline)
                                        importedOverlays.add(polyline)
                                    }
                                    res.pois.forEach { poi ->
                                        val m = org.osmdroid.views.overlay.Marker(map).apply {
                                            position = poi.point
                                            title = poi.title
                                            val extrasText = if (poi.extras.isNotEmpty())
                                                poi.extras.entries.joinToString("\n") { (k,v) -> "$k: $v" }
                                            else null
                                            val desc = poi.description?.takeIf { it.isNotBlank() }
                                            snippet = listOfNotNull(desc, extrasText).joinToString("\n\n").ifBlank { null }
                                            setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,
                                                org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)
                                            icon = androidx.core.content.ContextCompat.getDrawable(
                                                this@MapActivity, android.R.drawable.ic_menu_mylocation
                                            )
                                        }
                                        map.overlays.add(m)
                                        importedOverlays.add(m)
                                    }
                                    map.invalidate()
                                    android.widget.Toast.makeText(this, "KML importé", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } catch (_: Throwable) {
                                runOnUiThread {
                                    android.widget.Toast.makeText(this, "Échec import KML", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }.start()
                    }


                    // ----- CSV -----
                    lower.endsWith(".csv") || lower.endsWith(".txt") -> {
                        val txt = input.bufferedReader(Charsets.UTF_8).readText()
                        val lines = txt.lineSequence().filter { it.isNotBlank() }.toList()
                        // enlève un éventuel header
                        val pay = if (lines.isNotEmpty()
                            && lines.first().contains("lat", true)
                            && lines.first().contains("lon", true)
                        ) lines.drop(1) else lines
                        addPoisFromCsv(pay)
                        Toast.makeText(this, "CSV importé", Toast.LENGTH_SHORT).show()
                    }

                    else -> {
                        Toast.makeText(this, "Type non reconnu ($name)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.onFailure {
            Toast.makeText(this, "Échec import ($name)", Toast.LENGTH_SHORT).show()
        }
    }

        /** Active/désactive visuellement la couche et mémorise le choix utilisateur. */
    private fun setProtectedAreasVisible(enabled: Boolean) {
        showProtectedAreas = enabled
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit().putBoolean("pref_key_show_protected_areas", enabled).apply()

        // (Re)charge la liste mais détachée de la carte, puis attache/détache selon 'enabled'
        refreshProtectedAreas(attachToMap = false)

        // Nettoie la carte des versions précédentes
        protectedAreaOverlays.forEach { map.overlays.remove(it) }

        if (enabled) {
            protectedAreaOverlays.forEach { map.overlays.add(0, it) }
            Toast.makeText(this, "Zones protégées : ${protectedAreaOverlays.size}", Toast.LENGTH_SHORT).show()
        }
        map.invalidate()
    }
    // --- KML → Markers (POI) ---
    private fun parseKmlPoisFromStream(input: java.io.InputStream): List<org.osmdroid.views.overlay.Marker> {
        val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }

        // On isole chaque <Placemark>…</Placemark>
        val placemarkRe = Regex("<Placemark[\\s\\S]*?</Placemark>", RegexOption.IGNORE_CASE)
        // On récupère un éventuel <name>…</name> à l’intérieur
        val nameRe = Regex("<name>([\\s\\S]*?)</name>", RegexOption.IGNORE_CASE)
        // On cible uniquement les Point (pas les LineString/Polygon) et on prend le 1er trio lon,lat[,alt]
        val pointCoordRe = Regex(
            "<Point[\\s\\S]*?<coordinates>\\s*([-+0-9.]+),\\s*([-+0-9.]+)(?:,[-+0-9.]+)?\\s*</coordinates>[\\s\\S]*?</Point>",
            RegexOption.IGNORE_CASE
        )

        val markers = mutableListOf<org.osmdroid.views.overlay.Marker>()
        placemarkRe.findAll(text).forEach { pm ->
            val block = pm.value

            val hasPoint = pointCoordRe.find(block)
            if (hasPoint != null) {
                val lon = hasPoint.groupValues[1].toDoubleOrNull()
                val lat = hasPoint.groupValues[2].toDoubleOrNull()
                if (lat != null && lon != null) {
                    val title = nameRe.find(block)?.groupValues?.getOrNull(1)?.trim()
                        ?.replace(Regex("\\s+"), " ")
                        ?: "POI"

                    val marker = org.osmdroid.views.overlay.Marker(map).apply {
                        position = org.osmdroid.util.GeoPoint(lat, lon)
                        this.title = title
                        setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,
                            org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)
                        // petite couleur par défaut (facultatif)
                        icon = resources.getDrawable(android.R.drawable.star_big_on, theme)
                    }
                    markers += marker
                }
            }
        }
        return markers
    }


    private lateinit var map: MapView
    private lateinit var toolbar: MaterialToolbar

private var hudPseudoView: TextView? = null


    private lateinit var myLocation: MyLocationNewOverlay
    private lateinit var compass: CompassOverlay
    private lateinit var rotationOverlay: RotationGestureOverlay
    private lateinit var scaleBarOverlay: ScaleBarOverlay

    private var trackPolyline: Polyline? = null
    private var startMarker: Marker? = null
    private var endMarker: Marker? = null

    private var isRecording = false
    private var isPaused = false
    private var followWhileRecording = true

    private lateinit var tvCoordsAlt: TextView
    private lateinit var tvWeather: TextView

    // Stats UI
    private lateinit var cardStats: MaterialCardView
    private lateinit var tvStatDist: TextView
    private lateinit var tvStatGain: TextView
    private lateinit var tvStatLoss: TextView
    private lateinit var tvStatSpeed: TextView
    private lateinit var tvStatElapsed: TextView

    

private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
private fun refreshProfileSubtitle() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val pseudo = prefs.getString("profile_pseudo", null)
        toolbar.subtitle = pseudo?.takeIf { it.isNotBlank() } ?: ""

// Also update HUD chip
val display = pseudo?.takeIf { it.isNotBlank() } ?: ""
hudPseudoView?.let { view ->
    if (display.isBlank()) {
        view.visibility = View.GONE
    } else {
        view.text = "👤  " + display
        view.visibility = View.VISIBLE
    }
}

    }

    // Stats data
    private var lastWeatherLat: Double? = null
    private var lastWeatherLon: Double? = null
    private val trackPoints = mutableListOf<GeoPoint>()
    private var lastPoint: GeoPoint? = null
    private var totalDistanceM = 0.0
    private var totalGainM = 0.0
    private var totalLossM = 0.0
    private var startTimeMs: Long? = null

    private var isFollowing = true
    private var ticker: android.os.Handler? = null

    // FAB GPX
    // Overlays importés (KML/CSV) pour pouvoir les masquer/effacer
    private val importedOverlays = mutableListOf<org.osmdroid.views.overlay.Overlay>()

    private var gpxFab: ExtendedFloatingActionButton? = null

    // Planification
    private var plannerMode = false
    private var planPolyline: Polyline? = null
    private val planPoints = mutableListOf<GeoPoint>()
    private var cardPlan: MaterialCardView? = null
    private var tvPlanDist: TextView? = null
    private var tvPlanElev: TextView? = null
    private var tvPlanTime: TextView? = null
    private var tvPlanDiff: TextView? = null
    private var planHelpFab: FloatingActionButton? = null
    private var plannerHelpShown = false

    // âœ‚ï¸ Trim plan
    private var planTrimPreview: Polyline? = null
    private var planTrimFab: FloatingActionButton? = null

    // ORS (snap to trails)
    private var snapToTrails = true
    private val http by lazy { OkHttpClient() }
    private val orsKey by lazy {
        try {
            getString(R.string.ors_api_key)
        } catch (_: Exception) {
            ""
        }
    }

    // Guidage nature
    private var natureGuidanceOn = false
    private val naturePois = mutableListOf<NaturePOI>()
    private val natureMarkers = mutableListOf<Marker>()
    private val natureNotified = mutableSetOf<String>()
    private var natureTicker: android.os.Handler? = null
    // --- Parcs / zones protégées (polygones GeoJSON) ---

    private var showProtectedAreas: Boolean = false

    // Off-trail
    private var offTrailEnabled = true
    private var offTrailThresholdM = 40.0
    private var offTrailMinDurationMs = 15_000L
    private var offTrailSince: Long? = null
    private var offTrailNotified = false
    private var offTrailTicker: android.os.Handler? = null

    // ===== Elevation smoothing + robust hysteresis =====
    private var elevMinStepM = 3.0
    private var elevAlpha = 0.35
    private var elevMedianN = 5
    private var elevSpikeHorizMaxM = 5.0
    private var elevSpikeMinJumpM = 20.0
    private var elevMinHorizM = 1.5

    private val liveAltWindow = ArrayDeque<Double>()
    private var lastAltFiltered: Double? = null
    private var liveTrend = 0
    private var liveBaseAlt: Double? = null
    private var liveExtAlt: Double? = null

    // === ÉDITION DE TRACE (handles sur la carte) ===
    private var isEditingTrack = false
    private var editingTrack: TrackOverlay? = null
    private var editWorking: MutableList<GeoPoint>? = null
    private var editOriginal: List<GeoPoint> = emptyList()
    private val editMarkers = mutableListOf<Marker>()
    private val editMarkerIndex = mutableMapOf<Marker, Int>()

    private var fabEditSave: FloatingActionButton? = null
    private var fabEditCancel: FloatingActionButton? = null

    // Overlay de saisie (tap pour insérer un point)
    private var editTapOverlay: MapEventsOverlay? = null

    // ===== Import / Export GPX =====
    private val importGpx =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri == null) {
                Toast.makeText(this, R.string.gpx_import_fail, Toast.LENGTH_SHORT)
                    .show(); return@registerForActivityResult
            }
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    val polyline = GpxUtils.parseToPolyline(input)
                    val name = DocumentFile.fromSingleUri(this, uri)?.name?.removeSuffix(".gpx")
                        ?: "Import"
                    addImportedTrackToMap(polyline.actualPoints, name)
                    TrackStore.setAll(polyline.actualPoints)
                    resetStats()
                    if (polyline.actualPoints.size > 1) computeStatsFrom(polyline.actualPoints)
                    updateStatsUI()
                }
                Toast.makeText(this, R.string.gpx_import_ok, Toast.LENGTH_SHORT).show()
                maybeStartOffTrailTicker()
            } catch (_: Throwable) {
                Toast.makeText(this, R.string.gpx_import_fail, Toast.LENGTH_SHORT).show()
            }
        }

    private val exportGpxCreate =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                val poly = trackPolyline
                if (poly == null || poly.actualPoints.isEmpty()) {
                    Toast.makeText(this, R.string.err_nothing_to_export, Toast.LENGTH_SHORT)
                        .show(); return@registerForActivityResult
                }
                val gpx = GpxUtils.polylineToGpx(poly)
                contentResolver.openOutputStream(uri)?.use { os -> GpxUtils.saveToFile(gpx, os) }
                Toast.makeText(this, R.string.msg_export_ready, Toast.LENGTH_SHORT).show()
            } catch (_: Throwable) {
                Toast.makeText(this, R.string.err_export_failed, Toast.LENGTH_SHORT).show()
            }
        }
    // Couleurs par type de fichier de parc/réserve
    // Couleurs par type de fichier de parc/réserve (détection robuste)
    private fun colorsForProtectedFile(name: String): Pair<Int, Int> {
        val norm = java.text.Normalizer.normalize(
            name.substringBeforeLast('.').lowercase(java.util.Locale.getDefault()),
            java.text.Normalizer.Form.NFD
        ).replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")

        val isCoeur   = norm.contains("coeur") || norm.contains("coer")
        val isReserve = norm.contains("reserve") || norm.contains("reserves") || norm.contains("integrale")
        val isAoa     = norm.contains("aoa")

        return when {
            isCoeur   -> android.graphics.Color.argb(80, 244,  67,  54) to android.graphics.Color.rgb(183,  28,  28) // rouge
            isReserve -> android.graphics.Color.argb(70,  66, 165, 245) to android.graphics.Color.rgb( 25, 118, 210) // bleu clair
            isAoa     -> android.graphics.Color.argb(70, 124, 252,   0) to android.graphics.Color.rgb( 46, 125,  50) // vert pomme
            else      -> android.graphics.Color.argb(60,  76, 175,  80) to android.graphics.Color.rgb( 27,  94,  32) // fallback doux
        }
    }
    // Import générique (KML/GPX/CSV/…)
    private val importAnyDoc = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) onImportDocument(uri)
    }

    // ===== Multi-traces =====
    private data class TrackOverlay(
        var id: String,
        var name: String,
        var file: File?,
        val polyline: Polyline,
        var color: Int,
        var alarmMeters: Double? = null,
        var offSince: Long? = null,
        var offNotified: Boolean = false
    )

    private val otherTracks = LinkedHashMap<String, TrackOverlay>()
    private var exportPendingTrack: TrackOverlay? = null

    private val trackColors = intArrayOf(
        Color.parseColor("#0D47A1"),
        Color.parseColor("#1B5E20"),
        Color.parseColor("#E65100"),
        Color.parseColor("#4A148C"),
        Color.parseColor("#006064"),
        Color.parseColor("#B71C1C"),
        Color.parseColor("#33691E")
    )
    private var colorCursor = 0
    private fun nextColor(): Int {
        val c = trackColors[colorCursor % trackColors.size]; colorCursor++; return c
    }

    private val exportAnyGpxCreate =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
            val t = exportPendingTrack ?: return@registerForActivityResult
            exportPendingTrack = null
            if (uri == null) return@registerForActivityResult
            try {
                val gpx = GpxUtils.polylineToGpx(t.polyline)
                contentResolver.openOutputStream(uri)?.use { os -> GpxUtils.saveToFile(gpx, os) }
                Toast.makeText(this, R.string.msg_export_ready, Toast.LENGTH_SHORT).show()
            } catch (_: Throwable) {
                Toast.makeText(this, R.string.err_export_failed, Toast.LENGTH_SHORT).show()
            }
        }

    private fun makePolyline(points: List<GeoPoint>, color: Int): Polyline =
        Polyline(map).apply {
            outlinePaint.strokeWidth = 6f
            outlinePaint.color = color
            setPoints(points)
            setOnClickListener { _, _, _ ->
                val t = otherTracks.values.firstOrNull { it.polyline === this }
                if (t != null) showTrackBottomSheet(t)
                true
            }
        }


    private fun loadSavedTracksToMap() {
        runCatching {
            val dir = com.hikemvp.gpx.GpxStorage.gpxDir(this)
            val files =
                dir.listFiles { f -> f.isFile && f.name.endsWith(".gpx", true) } ?: emptyArray()
            files.sortedBy { it.name.lowercase(Locale.getDefault()) }.forEach { file ->
                val id = file.absolutePath
                if (otherTracks.containsKey(id)) return@forEach
                contentResolver.openInputStream(android.net.Uri.fromFile(file))?.use { input ->
                    val poly = GpxUtils.parseToPolyline(input)
                    val c = nextColor()
                    val p = makePolyline(poly.actualPoints, c)
                    map.overlays.add(p)
                    otherTracks[id] = TrackOverlay(id, file.nameWithoutExtension, file, p, c)
                }
            }
            map.invalidate()
        }
    }

    private fun addImportedTrackToMap(points: List<GeoPoint>, nameHint: String = "Import") {
        val id = "import_${System.currentTimeMillis()}"
        val c = nextColor()
        val p = makePolyline(points, c)
        map.overlays.add(p)
        otherTracks[id] = TrackOverlay(id, nameHint, file = null, polyline = p, color = c)
        map.invalidate()
    }

    private fun zoomToTrack(t: TrackOverlay) {
        val pts = t.polyline.actualPoints
        if (pts.isNullOrEmpty()) return
        val bb = org.osmdroid.util.BoundingBox.fromGeoPointsSafe(pts)
        map.zoomToBoundingBox(bb, true, 100)
    }

    // ===== Aide D+ / Dâˆ’ =====
    private fun showElevationHelpDialog() {
        val msg = HtmlCompat.fromHtml(
            getString(R.string.elev_help_text),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.elev_help_title)
            .setMessage(msg)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ====== Filters helpers ======
    private fun clampAlt(a: Double) = a.coerceIn(-500.0, 9000.0)
    private fun pushMedian(win: ArrayDeque<Double>, v: Double, maxN: Int) {
        if (v.isNaN()) return
        win.addLast(clampAlt(v))
        if (win.size > maxN) win.removeFirst()
    }

    private fun median(win: ArrayDeque<Double>): Double? {
        if (win.isEmpty()) return null
        val arr = win.toDoubleArray()
        arr.sort()
        val n = arr.size
        return if (n % 2 == 1) arr[n / 2] else (arr[n / 2 - 1] + arr[n / 2]) / 2.0
    }

    private fun isSpike(horizM: Double, dAlt: Double): Boolean =
        (horizM < elevSpikeHorizMaxM && abs(dAlt) > elevSpikeMinJumpM)

    // Décime la liste pour ne pas poser 1000 ancres dâ€™un coup
    private fun decimateIndexes(total: Int, maxHandles: Int = 150): List<Int> {
        if (total <= 0) return emptyList()
        if (total <= maxHandles) return (0 until total).toList()
        val step =
            kotlin.math.ceil(total.toDouble() / maxHandles.toDouble()).toInt().coerceAtLeast(1)
        val out = ArrayList<Int>()
        var i = 0
        while (i < total) {
            out += i; i += step
        }
        if (out.last() != total - 1) out += total - 1
        return out
    }

    // Projette un point P sur le segment AB (approx. equirect.)
    private data class Proj(
        val indexA: Int,
        val indexB: Int,
        val t: Double,
        val onSeg: GeoPoint,
        val distM: Double
    )

    private fun nearestSegment(points: List<GeoPoint>, p: GeoPoint): Proj? {
        if (points.size < 2) return null
        // origine locale pour projection rapide
        val lat0 = Math.toRadians(p.latitude)
        val lon0 = Math.toRadians(p.longitude)
        val cosLat0 = kotlin.math.cos(lat0)
        val R = 6371000.0
        fun toXY(g: GeoPoint): Pair<Double, Double> {
            val lat = Math.toRadians(g.latitude)
            val lon = Math.toRadians(g.longitude)
            return ((lon - lon0) * cosLat0 * R) to ((lat - lat0) * R)
        }

        fun fromXY(x: Double, y: Double): GeoPoint {
            val lat = Math.toDegrees(y / R + lat0)
            val lon = Math.toDegrees(x / (R * cosLat0) + lon0)
            return GeoPoint(lat, lon)
        }

        val (px, py) = toXY(p)
        var best: Proj? = null

        var (ax, ay) = toXY(points[0])
        for (i in 1 until points.size) {
            val (bx, by) = toXY(points[i])
            val vx = bx - ax
            val vy = by - ay
            val wx = px - ax
            val wy = py - ay
            val vv = vx * vx + vy * vy
            val t = if (vv <= 0) 0.0 else ((wx * vx + wy * vy) / vv).coerceIn(0.0, 1.0)
            val cx = ax + t * vx
            val cy = ay + t * vy
            val dx = px - cx
            val dy = py - cy
            val d = kotlin.math.hypot(dx, dy)
            if (best == null || d < best!!.distM) {
                best = Proj(i - 1, i, t, fromXY(cx, cy), d)
            }
            ax = bx; ay = by
        }
        return best
    }

    // ===== Robust gain/loss on a static list =====
    private fun computeGainLossFiltered(points: List<GeoPoint>): Pair<Double, Double> {
        if (points.size < 2) return 0.0 to 0.0
        val win = ArrayDeque<Double>()
        var lastF: Double? = null
        var base: Double? = null
        var extremum: Double? = null
        var trend = 0
        var prev: GeoPoint? = null
        var gain = 0.0
        var loss = 0.0

        for (p in points) {
            pushMedian(win, p.altitude, elevMedianN)
            val med = median(win)
            val curF = when {
                med == null -> null
                lastF == null -> med
                else -> elevAlpha * med + (1 - elevAlpha) * lastF!!
            }?.also { lastF = it } ?: continue

            if (base == null) {
                base = curF; extremum = curF; prev = p; continue
            }

            val horiz = if (prev != null) prev!!.distanceToAsDouble(p) else 0.0
            prev = p
            if (horiz < elevMinHorizM) continue

            when (trend) {
                0 -> {
                    if (curF > extremum!!) extremum = curF
                    if (curF < extremum!! - elevMinStepM) {
                        gain += (extremum!! - base!!)
                        base = curF; extremum = curF; trend = -1
                    }
                    if (curF < extremum!!) extremum = min(extremum!!, curF)
                    if (curF > extremum!! + elevMinStepM) {
                        loss += (base!! - extremum!!)
                        base = curF; extremum = curF; trend = +1
                    }
                }

                +1 -> {
                    if (curF >= extremum!!) extremum = curF
                    else if (extremum!! - curF >= elevMinStepM) {
                        gain += (extremum!! - base!!)
                        base = curF; extremum = curF; trend = -1
                    }
                }

                -1 -> {
                    if (curF <= extremum!!) extremum = curF
                    else if (curF - extremum!! >= elevMinStepM) {
                        loss += (base!! - extremum!!)
                        base = curF; extremum = curF; trend = +1
                    }
                }
            }
        }
        return gain to loss
    }

    private fun resetElevationFilterState() {
        liveAltWindow.clear()
        lastAltFiltered = null
        liveTrend = 0
        liveBaseAlt = null
        liveExtAlt = null
    }

    private fun seedLiveElevationFrom(last: GeoPoint) {
        resetElevationFilterState()
        val a = if (!last.altitude.isNaN()) clampAlt(last.altitude) else null
        if (a != null) {
            liveAltWindow.addLast(a)
            lastAltFiltered = a
            liveBaseAlt = a
            liveExtAlt = a
            liveTrend = 0
        }
        lastPoint = last
    }

    private data class LocalStats(
        val distM: Double,
        val gain: Int,
        val loss: Int,
        val altMin: Int?,
        val altMax: Int?
    )

    private fun computeStats(points: List<GeoPoint>): LocalStats {
        if (points.isEmpty()) return LocalStats(0.0, 0, 0, null, null)
        var dist = 0.0
        for (i in 1 until points.size) dist += points[i - 1].distanceToAsDouble(points[i])

        val win = ArrayDeque<Double>()
        var lastF: Double? = null
        var aMin: Double? = null
        var aMax: Double? = null
        points.forEach { p ->
            pushMedian(win, p.altitude, elevMedianN)
            val med = median(win)
            val curF = when {
                med == null -> null
                lastF == null -> med
                else -> elevAlpha * med + (1 - elevAlpha) * lastF!!
            }
            if (curF != null) {
                aMin = if (aMin == null) curF else min(aMin!!, curF)
                aMax = if (aMax == null) curF else kmax(aMax!!, curF)
                lastF = curF
            }
        }

        val (g, l) = computeGainLossFiltered(points)
        return LocalStats(dist, g.roundToInt(), l.roundToInt(), aMin?.roundToInt(), aMax?.roundToInt())
    }

    private fun enterTrackEdit(t: TrackOverlay) {
        if (isEditingTrack) leaveTrackEdit(false)
        val pts = (t.polyline.actualPoints ?: emptyList())
        if (pts.size < 2) {
            Toast.makeText(this, "Trace trop courte pour éditer.", Toast.LENGTH_SHORT).show()
            return
        }
        isEditingTrack = true
        editingTrack = t
        editOriginal = pts.map { GeoPoint(it) }           // copie profonde
        editWorking = editOriginal.map { GeoPoint(it) }.toMutableList()
        t.polyline.setPoints(editWorking)

        // Accentue visuellement la trace en édition
        t.polyline.outlinePaint.strokeWidth = 8f

        addEditHandles()
        ensureEditTapOverlay()
        showEditFabs(true)

        Toast.makeText(
            this,
            "Mode édition: glisse les ancres, tape sur la ligne pour insérer, tape une ancre pour supprimer.",
            Toast.LENGTH_LONG
        ).show()
        map.invalidate()
    }

    // === Rendu visuel des polygones : hachurage + contour prononcé ===
    private fun makeHatchPaint(bgColor: Int = Color.argb(40, 27, 94, 32), // vert très léger translucide
                               lineColor: Int = Color.argb(140, 27, 94, 32), // lignes de hachures plus visibles
                               spacingPx: Int = 12,
                               strokePx: Float = 2f): Paint {
        val size = spacingPx * 2
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // fond léger translucide (optionnel)
        val pBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor; style = Paint.Style.FILL }
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), pBg)

        // diagonales pour hachures
        val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColor
            style = Paint.Style.STROKE
            strokeWidth = strokePx
        }
        // \ diagonales
        c.drawLine(0f, 0f, size.toFloat(), size.toFloat(), pLine)
        c.drawLine(-spacingPx.toFloat(), 0f, size.toFloat()-spacingPx, size.toFloat(), pLine)
        c.drawLine(0f, -spacingPx.toFloat(), size.toFloat(), size.toFloat()-spacingPx, pLine)

        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
    }

    // --- Rendu visuel des parcs/réserves (UNE SEULE COPIE) ---
    private fun styleProtectedPolygon(pg: Polygon) {
        if (pg.fillPaint.color != 0 && pg.outlinePaint.color != 0) return
        // Contour marqué
        pg.outlinePaint.apply {
            color = Color.parseColor("#1B5E20")
            alpha = 220
            strokeWidth = 4.5f * resources.displayMetrics.density
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        // Remplissage simple et fiable (léger vert)
        pg.fillPaint.apply {
            color = Color.argb(60, 27, 94, 32)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
    }

    // Lecture GeoJSON minimaliste (Polygon & MultiPolygon)
    private fun parseGeoJsonPolygons(json: String): List<Polygon> {
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
            poly.points = rings.firstOrNull() ?: emptyList()
            if (rings.size > 1) {
                val holes = ArrayList<List<GeoPoint>>()
                for (i in 1 until rings.size) holes += rings[i]
                poly.holes = holes
            }
            styleProtectedPolygon(poly)
            return poly
        }

        val out = mutableListOf<Polygon>()
        val root = JSONObject(json)
        when (root.optString("type", "")) {
            "FeatureCollection", "featurecollection" -> {
                val feats = root.optJSONArray("features") ?: JSONArray()
                for (f in 0 until feats.length()) {
                    val geom = feats.getJSONObject(f).optJSONObject("geometry") ?: continue
                    when (geom.optString("type", "").uppercase(Locale.ROOT)) {
                        "POLYGON" -> out += coordsToPolygon(geom.getJSONArray("coordinates"))
                        "MULTIPOLYGON" -> {
                            val m = geom.getJSONArray("coordinates")
                            for (i in 0 until m.length()) out += coordsToPolygon(m.getJSONArray(i))
                        }
                    }
                }
            }
            "Feature", "feature" -> {
                val geom = root.optJSONObject("geometry") ?: return emptyList()
                when (geom.optString("type", "").uppercase(Locale.ROOT)) {
                    "POLYGON" -> out += coordsToPolygon(geom.getJSONArray("coordinates"))
                    "MULTIPOLYGON" -> {
                        val m = geom.getJSONArray("coordinates")
                        for (i in 0 until m.length()) out += coordsToPolygon(m.getJSONArray(i))
                    }
                }
            }
            "Polygon", "polygon"       -> out += coordsToPolygon(root.getJSONArray("coordinates"))
            "MultiPolygon", "multipolygon" -> {
                val m = root.getJSONArray("coordinates")
                for (i in 0 until m.length()) {
                    out += coordsToPolygon(m.getJSONArray(i))
                }
            }
        }
        return out
    }
    private fun addProtectedAreaFromAssets(name: String) {
        runCatching {
            val json = assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val polys = parseGeoJsonPolygons(json)
            val (fill, stroke) = colorsForProtectedFile(name)

            polys.forEach { pg ->
                // IMPORTANT: pas de hatch / pas d'appel à un style générique ici
                pg.fillPaint.shader = null
                pg.fillPaint.color = fill
                pg.fillPaint.isAntiAlias = true
                pg.fillPaint.style = android.graphics.Paint.Style.FILL

                pg.outlinePaint.color = stroke
                pg.outlinePaint.strokeWidth = 2f * resources.displayMetrics.density
                pg.outlinePaint.isAntiAlias = true
                pg.outlinePaint.style = android.graphics.Paint.Style.STROKE

                map.overlays.add(0, pg)
                protectedAreaOverlays += pg
            }
        }
    }

    private fun refreshProtectedAreas() {
        // 1) Nettoyage ancien rendu
        protectedAreaOverlays.forEach { map.overlays.remove(it) }
        protectedAreaOverlays.clear()

        // 2) Où on cherche les fichiers
        val docs = getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS) ?: filesDir
        android.util.Log.i("ProtectedAreas", "Documents dir = ${docs.absolutePath}")

        // 3) Noms attendus (adapter si tu as d'autres noms)
        val names = listOf(
            "pnx-reserves-integrales.geojson",
            "pnx-coeur-aa-ama.geojson",
            "pnx-aoa.geojson"
        )

        // 4) Debug: log des fichiers présents dans Documents
        runCatching {
            val listed = docs.listFiles()?.joinToString { it.name } ?: "(vide)"
            android.util.Log.i("ProtectedAreas", "Fichiers dans Documents: $listed")
        }

        // 6) Fallback: assets (si rien en Documents)
        Toast.makeText(this, "Zones protégées : ${protectedAreaOverlays.size}", Toast.LENGTH_SHORT).show()

        val assetNames = assets.list("")?.toList().orEmpty()
            names.filter { it in assetNames }.forEach { n ->
                runCatching {
                    val json = assets.open(n).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val polys = parseGeoJsonPolygons(json)

                    // Couleurs par fichier
                    val (fill, stroke) = colorsForProtectedFile(n)
                    polys.forEach { poly ->
                        poly.fillPaint.color = fill
                        poly.outlinePaint.color = stroke
                        poly.outlinePaint.strokeWidth = 2f * resources.displayMetrics.density
                        poly.outlinePaint.isAntiAlias = true
                        poly.fillPaint.isAntiAlias = true

                        protectedAreaOverlays += poly
                        map.overlays.add(0, poly)
                    }
                }
            }
        }


    private fun addEditHandles() {
        val work = editWorking ?: return
        val idxs = decimateIndexes(work.size, 150)
        editMarkers.clear()
        editMarkerIndex.clear()

        idxs.forEach { i ->
            val m = Marker(map).apply {
                position = work[i]
                isDraggable = true
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Point #$i"
                icon = ContextCompat.getDrawable(
                    this@MapActivity,
                    android.R.drawable.checkbox_on_background
                )
                setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                    override fun onMarkerDragStart(marker: Marker?) {}
                    override fun onMarkerDrag(marker: Marker?) {
                        marker ?: return
                        val k = editMarkerIndex[marker] ?: return
                        if (k in work.indices) {
                            work[k] = GeoPoint(
                                marker.position.latitude,
                                marker.position.longitude,
                                marker.position.altitude
                            )
                            editingTrack?.polyline?.setPoints(work)
                            map.invalidate()
                        }
                    }

                    override fun onMarkerDragEnd(marker: Marker?) {
                        onMarkerDrag(marker)
                    }
                })
                setOnMarkerClickListener { marker, _ ->
                    val k = editMarkerIndex[marker] ?: return@setOnMarkerClickListener true
                    AlertDialog.Builder(this@MapActivity)
                        .setTitle("Supprimer le point ?")
                        .setMessage("Point #$k")
                        .setPositiveButton("Supprimer") { _, _ ->
                            if (k in work.indices && work.size > 2) {
                                work.removeAt(k)
                                editingTrack?.polyline?.setPoints(work)
                                // on reconstruit les handles pour tenir compte des index
                                removeEditHandles(); addEditHandles()
                                map.invalidate()
                            } else {
                                Toast.makeText(
                                    this@MapActivity,
                                    "Impossible de supprimer (trop peu de points).",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .setNegativeButton(R.string.btn_cancel, null)
                        .show()
                    true
                }
            }
            editMarkers += m
            editMarkerIndex[m] = i
            map.overlays.add(m)
                        importedOverlays.add(m)
                    }
    }

    private fun removeEditHandles() {
        editMarkers.forEach { map.overlays.remove(it) }
        editMarkers.clear()
        editMarkerIndex.clear()
    }

    private fun ensureEditTapOverlay() {
        if (editTapOverlay != null) return
        val receiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                if (!isEditingTrack || p == null) return false
                val work = editWorking ?: return false
                val proj = nearestSegment(work, p) ?: return false
                val insertAt = proj.indexA + 1
                work.add(insertAt, proj.onSeg)
                editingTrack?.polyline?.setPoints(work)
                // On reconstruit les handles (nouveaux index)
                removeEditHandles(); addEditHandles()
                map.invalidate()
                return true
            }

            override fun longPressHelper(p: GeoPoint?): Boolean = false
        }
        editTapOverlay = MapEventsOverlay(receiver)
        map.overlays.add(editTapOverlay)
    }

    private fun removeEditTapOverlay() {
        editTapOverlay?.let { map.overlays.remove(it) }
        editTapOverlay = null
    }

    private fun showEditFabs(show: Boolean) {
        if (!show) {
            fabEditSave?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
            fabEditCancel?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
            fabEditSave = null; fabEditCancel = null
            return
        }
        if (fabEditSave != null || fabEditCancel != null) return

        fabEditSave = FloatingActionButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_save)
            setOnClickListener { leaveTrackEdit(true) }
        }
        fabEditCancel = FloatingActionButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setOnClickListener { leaveTrackEdit(false) }
        }

        val m = resources.getDimensionPixelSize(R.dimen.fab_margin)
        val lpSave = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.END or android.view.Gravity.BOTTOM
            setMargins(m, m, m, m * 7) // au-dessus du FAB GPX
        }
        val lpCancel = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.END or android.view.Gravity.BOTTOM
            setMargins(m, m, m, m * 10)
        }

        addContentView(fabEditSave, lpSave)
        addContentView(fabEditCancel, lpCancel)
    }


    // --- Helpers pour estimer Durée / Mouvement / Vitesse moyenne (pour la bottom sheet) ---
    private fun parseGpxTimes(file: java.io.File): List<Long> {
        return try {
            val times = mutableListOf<Long>()
            file.inputStream().bufferedReader(Charsets.UTF_8).useLines { seq ->
                val re = Regex("<time>([^<]+)</time>")
                seq.forEach { line ->
                    val m = re.find(line)
                    if (m != null) {
                        val iso = m.groupValues[1].trim()
                        // Parsing ISO 8601 with or without Z; fallback to instant
                        val t = runCatching {
                            java.time.Instant.parse(iso).toEpochMilli()
                        }.getOrElse {
                            // Some GPX may use local time without Z; try offsetDateTime
                            runCatching {
                                java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli()
                            }
                                .getOrElse { 0L }
                        }
                        if (t > 0L) times += t
                    }
                }
            }
            times
        } catch (_: Throwable) {
            emptyList()
        }
    }
    // --- Affichage des zones protégées (toggle menu) ---

    private data class TimeStats(val totalMs: Long, val movingMs: Long, val avgKmh: Double)

    private fun computeTimeStats(points: List<GeoPoint>, file: java.io.File?): TimeStats {
        // 1) Si GPX avec timestamps -> durée réelle et mouvement filtré
        if (file != null) {
            val times = parseGpxTimes(file)
            if (times.size >= 2 && points.size == times.size) {
                var totalDist = 0.0
                var movingMs = 0L
                for (i in 1 until points.size) {
                    val dt = (times[i] - times[i - 1]).coerceAtLeast(0L)
                    val d = points[i - 1].distanceToAsDouble(points[i]).coerceAtLeast(0.0)
                    totalDist += d
                    // considéré "en mouvement" si vitesse > 0.5 m/s (1.8 km/h) ou saut > 5 m
                    val moving = (dt > 0 && (d / (dt / 1000.0)) > 0.5) || d > 5.0
                    if (moving) movingMs += dt
                }
                val totalMs = (times.last() - times.first()).coerceAtLeast(0L)
                val avgKmh = if (totalMs > 0) (totalDist / (totalMs / 1000.0)) * 3.6 else 0.0
                return TimeStats(totalMs, movingMs, avgKmh)
            } else if (times.size >= 2) {
                // taille décalée : prendre durée globale comme borne basse
                val totalMs = (times.last() - times.first()).coerceAtLeast(0L)
                val dist = if (points.size >= 2) {
                    var d =
                        0.0; for (i in 1 until points.size) d += points[i - 1].distanceToAsDouble(
                        points[i]
                    ); d
                } else 0.0
                val avgKmh = if (totalMs > 0) (dist / (totalMs / 1000.0)) * 3.6 else 0.0
                return TimeStats(totalMs, 0L, avgKmh)
            }
        }
        // 2) Sinon: estimation Naismith sur la distance (mouvement ~= total)
        val pts = points
        val distM = if (pts.size >= 2) {
            var d = 0.0; for (i in 1 until pts.size) d += pts[i - 1].distanceToAsDouble(pts[i]); d
        } else 0.0
        val st = computeStats(points)
        val estMs = com.hikemvp.planning.TrackAnalysis.estimateDurationMillis(
            com.hikemvp.planning.TrackStats(st.distM, st.gain.toDouble(), st.loss.toDouble())
        )
        val avgKmh = if (estMs > 0) (distM / (estMs / 1000.0)) * 3.6 else 0.0
        return TimeStats(estMs, estMs, avgKmh)
    }

    private fun fmtHms(ms: Long): String {
        val h = ms / 3_600_000L
        val m = (ms / 60_000L) % 60
        val s = (ms / 1000L) % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }

    // --------- Bottom sheet détails ---------
    private fun showTrackBottomSheet(t: TrackOverlay) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.track_detail_bottomsheet, null)
        dialog.setContentView(view)


        ensureNaturePoiFile()
        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val tvSubtitle = view.findViewById<TextView>(R.id.tvSubtitle)
        val tvDistance = view.findViewById<TextView>(R.id.tvDistance)
        val tvDuration = view.findViewById<TextView>(R.id.tvDuration)
        val tvMoving = view.findViewById<TextView>(R.id.tvMoving)
        val tvPace = view.findViewById<TextView>(R.id.tvPace)
        val tvAlt = view.findViewById<TextView>(R.id.tvAlt)
        val tvGainLoss = view.findViewById<TextView>(R.id.tvGainLoss)
        val tvStart = view.findViewById<TextView>(R.id.tvStart)
        val tvEnd = view.findViewById<TextView>(R.id.tvEnd)

        val btnShare = view.findViewById<View>(R.id.btnShare)
        val btnExport = view.findViewById<View>(R.id.btnExport)
        val btnRename = view.findViewById<View>(R.id.btnRename)
        val btnColor = view.findViewById<View>(R.id.btnColor)
        val btnFollow = view.findViewById<View>(R.id.btnFollow)
        val btnAlarm = view.findViewById<View>(R.id.btnAlarm)
        val btnDelete = view.findViewById<View>(R.id.btnDelete)
        // Nouveau bouton (si présent dans le layout)
        val btnEdit: View? = run {
            val optionalId = view.resources.getIdentifier("btnEdit", "id", packageName)
            if (optionalId != 0) view.findViewById(optionalId) else null
        }
        tvTitle.text = t.name
        tvSubtitle.text = t.file?.name ?: "Importée (session courante)"

        fun refreshStatsInSheet() {
            val pts = t.polyline.actualPoints ?: emptyList()
            val st = computeStats(pts)
            tvDistance.text = "Distance : %.2f km".format(st.distM / 1000.0)
            tvGainLoss.text = "D+ %d m • D- %d m".format(st.gain, st.loss)
            tvAlt.text = "Altitude min/max : %s".format(
                if (st.altMin != null && st.altMax != null) "${st.altMin} / ${st.altMax} m" else "—"
            )
            val startTxt =
                pts.firstOrNull()?.let { "Lat %.5f, Lon %.5f".format(it.latitude, it.longitude) }
                    ?: "—"
            val endTxt =
                pts.lastOrNull()?.let { "Lat %.5f, Lon %.5f".format(it.latitude, it.longitude) }
                    ?: "—"
            tvStart.text = "Départ : $startTxt"
            tvEnd.text = "Arrivée : $endTxt"
        }
        refreshStatsInSheet()
        val timeStats = computeTimeStats(emptyList(), t.file)
        tvDuration.text = "Durée totale : " + fmtHms(timeStats.totalMs)
        tvMoving.text = "En mouvement : " + fmtHms(timeStats.movingMs)
        tvPace.text = "Vitesse moyenne : %.1f km/h".format(timeStats.avgKmh)

        btnFollow.setOnClickListener { zoomToTrack(t) }
        // Appui LONG sur "Suivre" => entrer en mode édition de cette trace
        btnFollow.setOnLongClickListener {
            dialog.dismiss()
            enterTrackEdit(t)
            true
        }

        // ====== Modifier la trace (trim) ======
        val launchEdit = {
            showTrimOverlayDialog(t) {
                refreshStatsInSheet()
                map.invalidate()
            }
        }
        if (btnEdit != null) {
            // Si ton layout a un bouton dédié (id: btnEdit)
            btnEdit.setOnClickListener { launchEdit() }
        } else {
            // Fallback : appui long sur â€œRenommerâ€ ouvre un menu avec â€œModifier la traceâ€
            btnRename.setOnLongClickListener { anchor ->
                val pm = PopupMenu(this, anchor)
                pm.menu.add(0, 1001, 0, "Modifier la trace")
                pm.setOnMenuItemClickListener {
                    if (it.itemId == 1001) {
                        launchEdit(); true
                    } else false
                }
                pm.show()
                true
            }
        }

        btnExport.setOnClickListener {
            val safeName =
                t.name.replace(Regex("[^A-Za-z0-9 _-]"), "_").replace(' ', '_').ifEmpty { "trace" }
            exportPendingTrack = t
            exportAnyGpxCreate.launch("$safeName.gpx")
            dialog.dismiss()
        }

        btnRename.setOnClickListener {
            val input = EditText(this).apply {
                hint = "Nouveau nom"; setText(t.name); setSelection(text.length); filters =
                arrayOf(InputFilter.LengthFilter(64))
            }
            AlertDialog.Builder(this)
                .setTitle("Renommer la trace")
                .setView(input)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val newName = input.text.toString().trim().ifEmpty { t.name }
                    if (newName != t.name) {
                        if (t.file != null) {
                            val parent = t.file?.parentFile
                            val renamed = File(parent, "$newName.gpx")
                            if (!renamed.exists()) {
                                runCatching { t.file?.renameTo(renamed) }
                                val oldId = t.id
                                t.file = renamed; t.name = newName; t.id = renamed.absolutePath
                                otherTracks.remove(oldId); otherTracks[t.id] = t
                                tvTitle.text = newName; tvSubtitle.text = renamed.name
                            } else Toast.makeText(
                                this,
                                "Un fichier du même nom existe déjà.",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            t.name = newName; tvTitle.text = newName
                        }
                    }
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }

        btnColor.setOnClickListener {
            val newColor = nextColor()
            t.color = newColor
            t.polyline.outlinePaint.color = newColor
            map.invalidate()
        }

        btnAlarm.setOnClickListener {
            val input = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                hint = "Alerte à (m)"
                setText(t.alarmMeters?.roundToInt()?.toString() ?: "")
            }
            AlertDialog.Builder(this)
                .setTitle("Définir une alerte")
                .setView(input)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val meters = input.text.toString().toDoubleOrNull()
                    t.alarmMeters = meters?.takeIf { it > 0 }
                    t.offSince = null; t.offNotified = false
                    Toast.makeText(
                        this,
                        if (t.alarmMeters != null) "Alerte à ${t.alarmMeters!!.roundToInt()} m" else "Alerte désactivée",
                        Toast.LENGTH_SHORT
                    ).show()
                    maybeStartOffTrailTicker()
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }

        btnShare.setOnClickListener {
            runCatching {
                val shareDir = File(cacheDir, "share").apply { mkdirs() }
                val out = File(shareDir, "trace_${System.currentTimeMillis()}.gpx")
                out.outputStream()
                    .use { os -> GpxUtils.saveToFile(GpxUtils.polylineToGpx(t.polyline), os) }
                val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", out)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/gpx+xml"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra(Intent.EXTRA_SUBJECT, t.name)
                }
                safeStartActivity(Intent(Intent.createChooser(send, "Partager")))
            }.onFailure {
                Toast.makeText(
                    this,
                    "Partage indisponible (FileProvider).",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        btnDelete.setOnClickListener {
            map.overlays.remove(t.polyline); otherTracks.remove(t.id); t.file?.let { f -> runCatching { f.delete() } }
            map.invalidate(); dialog.dismiss()
            Toast.makeText(this, "Trace supprimée", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }
    // --------- /Bottom sheet ---------

    // Export GPX (plan)
    private val exportPlanCreate =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                if (planPoints.isEmpty()) {
                    Toast.makeText(this, R.string.err_nothing_to_export, Toast.LENGTH_SHORT)
                        .show(); return@registerForActivityResult
                }
                val poly = Polyline().apply { setPoints(planPoints) }
                val gpx = GpxUtils.polylineToGpx(poly)
                contentResolver.openOutputStream(uri)?.use { os -> GpxUtils.saveToFile(gpx, os) }
                Toast.makeText(this, R.string.msg_export_ready, Toast.LENGTH_SHORT).show()
            } catch (_: Throwable) {
                Toast.makeText(this, R.string.err_export_failed, Toast.LENGTH_SHORT).show()
            }
        }

    // ===== Live points =====
    private val pointReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Constants.BROADCAST_TRACK_POINT -> {
                    val lat = intent.getDoubleExtra(Constants.EXTRA_LAT, Double.NaN)
                    val lon = intent.getDoubleExtra(Constants.EXTRA_LON, Double.NaN)
                    if (!lat.isNaN() && !lon.isNaN()) {
                        val alt = intent.getDoubleExtra(Constants.EXTRA_ALT, Double.NaN)
                        val gp = if (alt.isNaN()) GeoPoint(lat, lon) else GeoPoint(lat, lon, alt)
                        trackPoints.add(gp)
                        updateStatsWithPoint(gp)
                        showPolyline(trackPoints)
                        if (trackPoints.size == 1) setStartMarker(gp)
                        setEndMarker(gp)
                        if (isRecording && followWhileRecording) {
                            map.controller.animateTo(gp); runCatching { myLocation.enableFollowLocation() }
                        }
                        if (natureGuidanceOn) checkNatureProximity(gp)
                    }
                }

                Constants.BROADCAST_TRACK_RESET -> {
                    TrackStore.clear(); trackPoints.clear(); trackPolyline?.setPoints(emptyList())
                    removeStartEndMarkers(); resetStats(); resetElevationFilterState(); map.invalidate()
                }
            }
        }
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Constants.ACTION_RECORDING_STATUS) {
                isRecording = intent.getBooleanExtra(Constants.EXTRA_ACTIVE, false)
                isPaused = intent.getBooleanExtra(Constants.EXTRA_IS_PAUSED, false)
                updateToolbarTheme(); updateStatsVisibility(); invalidateOptionsMenu(); updateRecordButtonIcon()
            }
        }
    }

        // ===== Group live markers (receiver + timeout ticker) =====
    private val groupMarkers = mutableMapOf<String, org.osmdroid.views.overlay.Marker>()
    private var groupOverlay: com.hikemvp.group.GroupOverlay? = null
    private val pendingGroup = mutableMapOf<String, GroupFix>()
    private val groupUiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var groupTickScheduled = false
    private var lastGroupRx = 0L
    private data class GroupFix(val user: String, val lat: Double, val lon: Double, val acc: Float, val ts: Long)

    private val groupLastFix = mutableMapOf<String, Long>()
    private var groupTicker: android.os.Handler? = null


    
// [removed duplicate block of groupPosReceiver]


    private fun startGroupTicker() {
        stopGroupTicker()
        groupTicker = android.os.Handler(mainLooper).also { h ->
            h.post(object : Runnable {
                override fun run() {
                    val now = java.lang.System.currentTimeMillis()
                    // Grise les marqueurs inactifs > 60s
                    groupMarkers.forEach { (user, marker) ->
                        val t = groupLastFix[user] ?: 0L
                        val stale = (now - t) > 60_000L
                        marker.setAlpha(if (stale) 0.35f else 1.0f)
                    }
                    if (groupMarkers.isNotEmpty()) map.postInvalidate()
                    h.postDelayed(this, 15_000L)
                }
            })
        }
    }
    private fun stopGroupTicker() {
        groupTicker?.removeCallbacksAndMessages(null)
        groupTicker = null
    }

// ===== Permissions =====
    private val reqLocationPerms =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            val ok = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (ok) enableMyLocation()
        }
    private val reqPostNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    // ===== Waypoints additions =====
    private val waypointMarkers = LinkedHashMap<Long, Marker>()
    private var waypointFab: FloatingActionButton? = null
    private var pendingAttachForId: Long? = null

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val id = pendingAttachForId ?: return@registerForActivityResult
            pendingAttachForId = null
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
                val path = saveWaypointAttachment(id, uri)
                if (path != null) Toast.makeText(this, "Photo ajoutée.", Toast.LENGTH_SHORT)
                    .show() else Toast.makeText(
                    this,
                    "Échec de lâ€™import de la photo.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    // ===== Lifecycle =====
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        Configuration.getInstance().userAgentValue = packageName
        try {val base = File(getExternalFilesDir(null), "osmdroid")
            val tiles = File(base, "tiles")
            if (!tiles.exists()) tiles.mkdirs()
            Configuration.getInstance().osmdroidBasePath = base
            Configuration.getInstance().osmdroidTileCache = tiles
            // Vérification/fallback Scoped Storage (API 30+)
    OsmdroidStorage.ensureWritable(this)
} catch (_: Throwable) {
    // silencieux: on laisse osmdroid gérer ses fallbacks
}

        setContentView(R.layout.activity_map)
        map = findViewById(R.id.map)
        com.hikemvp.GroupGlobals.mapView = map
        if (com.hikemvp.group.GroupBridge.overlay == null) {
            com.hikemvp.group.GroupBridge.overlay = com.hikemvp.group.GroupOverlay()
        }
        // Attache l’overlay de groupe à la carte (rendu visuel)
        try { com.hikemvp.group.GroupBridge.overlay?.attachTo(map) } catch (_: Throwable) {}
        com.hikemvp.group.GroupBridge.attach(map, com.hikemvp.group.GroupBridge.overlay)
        // A6.1 — Restore camera & optional Fit group on launch (unless focus intent)
        runCatching {
            val _skipFocus = intent?.hasExtra("focus_member_id") == true
            if (!_skipFocus && GroupPrefs.isRestoreCameraEnabled(this)) {
                MapCameraPrefs.restore(this, map, skipIfFocusExtra = false)
            }
            if (!_skipFocus && GroupPrefs.isFitGroupOnLaunch(this)) {
                GroupBridge.overlay?.let { ol ->
                    if (ol.members.isNotEmpty()) {
                        ol.zoomToAll(map)
                    }
                }
            }
        }
        groupOverlay = com.hikemvp.group.GroupOverlay(this)
        map.overlays.add(groupOverlay)
        map.invalidate()
        toolbar = findViewById(R.id.toolbar)
        tvCoordsAlt = findViewById(R.id.tvCoordsAlt)
        tvWeather = findViewById(R.id.tvWeather)
        WaypointAutoRestore.restoreIfEmpty(this)

        findViewById<View>(R.id.hud_box).setOnClickListener {
            QuickWeatherDialogFragment.show(
                supportFragmentManager
            )
        }
        tvWeather.setOnLongClickListener { QuickWeatherDialogFragment.show(supportFragmentManager); true }

        cardStats = findViewById(R.id.card_stats)
        tvStatDist = findViewById(R.id.tvStatDist)
        tvStatGain = findViewById(R.id.tvStatGain)
        tvStatLoss = findViewById(R.id.tvStatLoss)
        tvStatSpeed = findViewById(R.id.tvStatSpeed)
        tvStatElapsed = findViewById(R.id.tvStatElapsed)
        updateStatsVisibility()

        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayShowTitleEnabled(false)
            setLogo(R.drawable.bg_hiketrack)
        }
        updateToolbarTheme()

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            reqPostNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setupMap()
        applyUserPrefs()
        setupLongPressToAddWaypoint()
        setupPlannerTapOverlay()
        setupCenterInfoUpdater()
        setupWeatherUpdater()

        naturePois.clear()
        naturePois.addAll(loadNaturePois())
        setNatureMarkersVisible(false)

        addGpxFab()
        addPlannerHud()
        addWaypointsFab()

        if (!hasLocationPerm()) requestLocationPerm()

        if (TrackStore.snapshot().isNotEmpty()) {
            showPolyline(TrackStore.snapshot())
            updateStartEndMarkers()
            resetStats()
            computeStatsFrom(TrackStore.snapshot())
            updateStatsUI()
        }

        runCatching {
            val persisted = ActiveTrackRepo.readAllGeoPoints(this)
            if (persisted.isNotEmpty()) {
                TrackStore.setAll(persisted)
                showPolyline(persisted)
                updateStartEndMarkers()
                resetStats()
                computeStatsFrom(persisted)
                updateStatsUI()
            }
        }

        loadSavedTracksToMap()
        refreshWaypointsOnMap()
    
        try { refreshProfileSubtitle() } catch (_: Throwable) {}


// Lightweight HUD chip for profile pseudo (non-intrusive overlay)
runCatching {
    val root = findViewById<FrameLayout>(android.R.id.content)
    if (hudPseudoView == null) {
        hudPseudoView = TextView(this).apply {
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.WHITE)
            setBackgroundColor(0x66000000) // semi-transparent
            isAllCaps = false
            setOnClickListener { /* no-op */ }
        }
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        ).apply {
            setMargins(0, dp(88), dp(12), 0) // below toolbar, right margin
        }
        root.addView(hudPseudoView, lp)
    }
}.onFailure { /* ignore */ }
}

    override fun onStart() {
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(groupConnectedReceiver, IntentFilter("com.hikemvp.group.CONNECTED"), Context.RECEIVER_NOT_EXPORTED)
        
        // Réception de la liste des membres / marqueurs du groupe
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                groupMarkersReceiver,
                IntentFilter("com.hikemvp.group.ACTION_GROUP_MARKERS"),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(
                groupMarkersReceiver,
                IntentFilter("com.hikemvp.group.ACTION_GROUP_MARKERS")
            )
        }
} else {
            @Suppress("DEPRECATION")
            registerReceiver(groupConnectedReceiver, IntentFilter("com.hikemvp.group.CONNECTED"))
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(groupPosReceiver, IntentFilter("com.hikemvp.group.POSITION"), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(groupPosReceiver, IntentFilter("com.hikemvp.group.POSITION"))
        }
        runCatching {
            val f = IntentFilter("com.hikemvp.group.ACTION_GROUP_MARKERS")
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                registerReceiver(groupMarkersReceiver, f, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(groupMarkersReceiver, f)
            }
        }
        // Réception des marqueurs (liste des membres / positions)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                groupMarkersReceiver,
                IntentFilter("com.hikemvp.group.ACTION_GROUP_MARKERS"),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(
                groupMarkersReceiver,
                IntentFilter("com.hikemvp.group.ACTION_GROUP_MARKERS")
            )
        }


        super.onStart()
        val trackFilter = IntentFilter().apply {
            addAction(Constants.BROADCAST_TRACK_POINT)
            addAction(Constants.BROADCAST_TRACK_RESET)
        }
        ContextCompat.registerReceiver(
            this,
            pointReceiver,
            trackFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            this,
            statusReceiver,
            IntentFilter(Constants.ACTION_RECORDING_STATUS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    
        // Register group positions receiver
        run {
            val filter = IntentFilter("com.hikemvp.group.POS")
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(groupPosReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(groupPosReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        }
            }
        }
}

    override fun onStop() {
        runCatching { unregisterReceiver(groupConnectedReceiver)
        runCatching { unregisterReceiver(groupMarkersReceiver) }
    }
        runCatching { unregisterReceiver(groupPosReceiver) }
        runCatching { unregisterReceiver(groupMarkersReceiver) }

        super.onStop()
        runCatching { unregisterReceiver(pointReceiver) }
        runCatching { unregisterReceiver(statusReceiver) }
        runCatching { unregisterReceiver(groupPosReceiver) }
        stopGroupTicker()
        stopElapsedTicker(); stopNatureTicker(); stopOffTrailTicker() }

    override fun onResume() {
        super.onResume()
        map.onResume()
        applyUserPrefs()
        updateToolbarTheme()
        updateStatsVisibility()
        if (isRecording && !isPaused) startElapsedTicker()
        if (natureGuidanceOn) startNatureTicker()
        loadSavedTracksToMap()
        maybeStartOffTrailTicker()
        refreshWaypointsOnMap()
        refreshProfileSubtitle()

        // Réapplique simplement l'état mémorisé sans forcer le rechargement intempestif
        val prefShow = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("pref_key_show_protected_areas", false)
        setProtectedAreasVisible(prefShow)
    
    applyHudWeather(prefs.getBoolean(ProfilePrefs.KEY_HUD_WEATHER, true))
    applyAutoFollow(prefs.getBoolean(ProfilePrefs.KEY_AUTO_FOLLOW, true))
}

    override fun onPause() {
        if (isEditingTrack) leaveTrackEdit(false) // rollback si on quitte lâ€™écran en plein edit
        super.onPause()
        map.onPause()
    }

    // ===== Setup Carte =====
    private fun setupMap() {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(6.0)
        map.controller.setCenter(GeoPoint(46.5, 2.5))
        myLocation = MyLocationNewOverlay(GpsMyLocationProvider(this), map).apply {
            enableMyLocation(); enableFollowLocation()
        }
        map.overlays.add(myLocation)
        compass = CompassOverlay(this, map).apply { enableCompass() }
        map.overlays.add(compass)
        rotationOverlay = RotationGestureOverlay(map).apply { isEnabled = true }
        map.overlays.add(rotationOverlay)
        scaleBarOverlay = ScaleBarOverlay(map)
        map.overlays.add(scaleBarOverlay)
        if (trackPolyline == null) {
            trackPolyline = Polyline(map).apply { outlinePaint.strokeWidth = 6f }
            trackPolyline!!.setOnClickListener { _, _, _ ->
                safeStartActivity(Intent(this, TrackDetailsActivity::class.java)); true
            }
            map.overlays.add(trackPolyline)
        }
        myLocation.runOnFirstFix {
            runOnUiThread {
                myLocation.myLocation?.let {
                    map.controller.setZoom(15.0)
                    map.controller.animateTo(it)
                    fetchWeatherIfMoved(it.latitude, it.longitude, force = true)
                }
            }
        }

        if (planPolyline == null) {
            planPolyline = Polyline(map).apply {
                outlinePaint.strokeWidth = 6f
                outlinePaint.color = Color.CYAN
            }
            map.overlays.add(planPolyline)
        }
    }

    private fun applyUserPrefs() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        when (prefs.getString(getString(R.string.pref_key_map_source), "osm")) {
            "ign" -> map.setTileSource(IgnWmtsTileSource.get())
            else -> map.setTileSource(TileSourceFactory.MAPNIK)
        }

        val defaultZoom: Int = try {
            prefs.getInt(getString(R.string.pref_key_map_default_zoom), 15)
        } catch (_: Exception) {
            prefs.getString(getString(R.string.pref_key_map_default_zoom), "15")?.toIntOrNull()
                ?: 15
        }
        if (map.zoomLevelDouble < 2.0) map.controller.setZoom(defaultZoom.toDouble())

        val lockRotation = prefs.getBoolean("pref_key_lock_rotation", false)
        rotationOverlay.isEnabled = !lockRotation

        snapToTrails = prefs.getBoolean("pref_key_snap_to_trails", true)
        offTrailThresholdM =
            prefs.getString("pref_key_offtrail_threshold_m", null)?.toDoubleOrNull()
                ?: offTrailThresholdM

        elevMinStepM =
            prefs.getString("pref_key_elev_min_step_m", null)?.toDoubleOrNull() ?: elevMinStepM
        elevAlpha =
            prefs.getString("pref_key_elev_alpha", null)?.toDoubleOrNull()?.coerceIn(0.1, 0.9)
                ?: elevAlpha
        elevMedianN = prefs.getString("pref_key_elev_median_n", null)?.toIntOrNull()
            ?.let { if (it % 2 == 0) it + 1 else it } ?: elevMedianN
        elevSpikeHorizMaxM =
            prefs.getString("pref_key_elev_spike_horiz_max_m", null)?.toDoubleOrNull()
                ?: elevSpikeHorizMaxM
        elevSpikeMinJumpM =
            prefs.getString("pref_key_elev_spike_min_jump_m", null)?.toDoubleOrNull()
                ?: elevSpikeMinJumpM
        elevMinHorizM =
            prefs.getString("pref_key_elev_min_horiz_m", null)?.toDoubleOrNull() ?: elevMinHorizM

        plannerHelpShown = prefs.getBoolean("pref_key_planner_help_shown", false)
        elevHelpShown = prefs.getBoolean("pref_key_elev_help_shown", false)
    }

    private var elevHelpShown = false

    private fun enableMyLocation() {
        runCatching { myLocation.enableMyLocation() }
        if (isFollowing) runCatching { myLocation.enableFollowLocation() }
    }

    private fun hasLocationPerm(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPerm() {
        val perms = mutableListOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        reqLocationPerms.launch(perms.toTypedArray())
    }

    private fun setupLongPressToAddWaypoint() {
        val receiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
            override fun longPressHelper(p: GeoPoint?): Boolean {
                if (p == null) return false
                val items = arrayOf("Ajouter un waypoint", "Ajouter un POI nature")
                AlertDialog.Builder(this@MapActivity)
                    .setTitle("Que souhaitez-vous ajouter ?")
                    .setItems(items) { _, which ->
                        when (which) {
                            0 -> {
                                WaypointDialogFragment(
                                    latitude = p.latitude,
                                    longitude = p.longitude
                                ) { meta: WaypointMeta ->
                                    addWaypoint(meta)
                                }.show(supportFragmentManager, "waypoint")
                            }

                            1 -> {
                                // Quick inline editor for a Nature POI
                                val ctx = this@MapActivity
                                val dp = resources.displayMetrics.density
                                val wrap = LinearLayout(ctx).apply {
                                    orientation = LinearLayout.VERTICAL
                                    setPadding(
                                        (20 * dp).toInt(),
                                        (12 * dp).toInt(),
                                        (20 * dp).toInt(),
                                        (8 * dp).toInt()
                                    )
                                }
                                val etName = EditText(ctx).apply { hint = "Nom du POI" }
                                val etRadius = EditText(ctx).apply {
                                    hint = "Rayon (m) — ex: 50"
                                    inputType =
                                        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                                }
                                val etNote = EditText(ctx).apply { hint = "Note (facultatif)" }
                                val spType = android.widget.Spinner(ctx).apply {
                                    adapter = ArrayAdapter(
                                        ctx,
                                        android.R.layout.simple_spinner_dropdown_item,
                                        NatureType.values().map { it.name }
                                    )
                                    setSelection(
                                        NatureType.values().indexOf(NatureType.PROTECTED)
                                            .coerceAtLeast(0)
                                    )
                                }
                                wrap.addView(TextView(ctx).apply { text = "Type" })
                                wrap.addView(spType)
                                wrap.addView(TextView(ctx).apply { text = "Nom" })
                                wrap.addView(etName)
                                wrap.addView(TextView(ctx).apply { text = "Rayon (m)" })
                                wrap.addView(etRadius)
                                wrap.addView(TextView(ctx).apply { text = "Note" })
                                wrap.addView(etNote)

                                AlertDialog.Builder(ctx)
                                    .setTitle("Nouveau POI nature")
                                    .setView(wrap)
                                    .setPositiveButton(android.R.string.ok) { _, _ ->
                                        val name = etName.text.toString().ifBlank { "Point" }
                                        val radius = etRadius.text.toString().replace(',', '.')
                                            .toDoubleOrNull() ?: 50.0
                                        val note = etNote.text.toString().ifBlank { null }
                                        val type = runCatching {
                                            NatureType.valueOf(spType.selectedItem.toString())
                                        }.getOrDefault(NatureType.PROTECTED)

                                        val poi = NaturePOI(
                                            id = "usr_${System.currentTimeMillis()}",
                                            type = type,
                                            name = name,
                                            lat = p.latitude,
                                            lon = p.longitude,
                                            radiusM = radius,
                                            note = note
                                        )
                                        addNaturePoi(poi)
                                        Toast.makeText(
                                            ctx,
                                            "POI ajouté : ${poi.name}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .setNegativeButton(android.R.string.cancel, null)
                                    .show()
                            }
                        }
                    }
                    .show()
                return true
            }
        }
        map.overlays.add(MapEventsOverlay(receiver))
    }

    private fun setupPlannerTapOverlay() {
        val receiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                if (!plannerMode || p == null) return false
                addPlanPoint(p); return true
            }

            override fun longPressHelper(p: GeoPoint?): Boolean = false
        }
        map.overlays.add(MapEventsOverlay(receiver))
    }

    private fun setupCenterInfoUpdater() {
        val update = {
            val c = map.mapCenter
            val lat = c.latitude
            val lon = c.longitude
            val altFromFix = myLocation.myLocation?.altitude
            val altStr =
                if (altFromFix != null && !altFromFix.isNaN()) "${altFromFix.roundToInt()} m" else getString(
                    R.string.weather_na
                )
            tvCoordsAlt.text = "Lat: %.5f\nLon: %.5f\nAlt: %s".format(lat, lon, altStr)
        }
        update()
        map.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                update();
                val c = map.mapCenter; fetchWeatherIfMoved(c.latitude, c.longitude); return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                update(); return false
            }
        })
    }

    private fun setupWeatherUpdater() {
        tvWeather.text = getString(R.string.weather_loading)
        myLocation.myLocation?.let { fetchWeatherIfMoved(it.latitude, it.longitude, force = true) }
    }

    private fun fetchWeatherIfMoved(lat: Double, lon: Double, force: Boolean = false) {
        val movedEnough = lastWeatherLat == null || lastWeatherLon == null ||
                abs(lat - lastWeatherLat!!) > 0.01 || abs(lon - lastWeatherLon!!) > 0.01
        if (!force && !movedEnough) return
        WeatherClient.fetch(this, lat, lon) { cw ->
            if (cw == null) {
                tvWeather.text = getString(R.string.weather_na)
            } else {
                val t = cw.tempC?.let { "${it.toInt()}°C" } ?: getString(R.string.weather_na)
                val w = cw.windKmh?.let { "${it.toInt()} km/h" } ?: getString(R.string.weather_na)
                val d = cw.description ?: ""
                tvWeather.text = "M\u00E9t\u00E9o: ${t} \u2022 Vent: ${w}\n${d}"
                lastWeatherLat = lat; lastWeatherLon = lon
            }
        }
    }

    // ======= Waypoints =======
    private fun refreshWaypointsOnMap() {
        // Remove previous markers
        waypointMarkers.values.forEach { map.overlays.remove(it) }
        waypointMarkers.clear()

        val hidden = loadHiddenGroups()
        val list = WaypointStorage.list(this)
        list.forEach { meta ->
            val group = getWaypointGroup(meta.id)
            if (group in hidden) return@forEach
            addWaypointMarker(meta)
        }
        map.invalidate()
    }

    private fun addWaypointMarker(meta: WaypointMeta) {
        val m = Marker(map).apply {
            position = GeoPoint(meta.latitude, meta.longitude)
            title = meta.name
            subDescription = meta.note ?: ""
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(this@MapActivity, android.R.drawable.ic_menu_myplaces)
            setOnMarkerClickListener { _, _ ->
                showWaypointOptions(meta.id)
                true
            }
        }
        waypointMarkers[meta.id] = m
        map.overlays.add(m)
                        importedOverlays.add(m)
                    }

    private fun showWaypointOptions(id: Long) {
        val meta = WaypointStorage.list(this).firstOrNull { it.id == id } ?: return
        val group = getWaypointGroup(id)
        val items = arrayOf(
            "Modifier…",
            "Déplacer vers un groupe…",
            "Ajouter une photo",
            "Voir les photos",
            "Supprimer"
        )
        AlertDialog.Builder(this)
            .setTitle("${meta.name} — $group")
            .setItems(items) { dlg, which ->
                when (which) {
                    0 -> showEditWaypointDialog(meta)
                    1 -> showMoveToGroupDialog(id)
                    2 -> {
                        pendingAttachForId = id; pickImage.launch(arrayOf("image/*"))
                    }

                    3 -> showAttachmentsDialog(id)
                    4 -> confirmDeleteWaypoint(id)
                }
                dlg.dismiss()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showEditWaypointDialog(meta: WaypointMeta) {
        val ctx = this
        val dp = resources.displayMetrics.density
        val wrap = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (12 * dp).toInt(), (20 * dp).toInt(), (8 * dp).toInt())
        }
        val etName = EditText(ctx).apply { setText(meta.name); hint = "Nom" }
        val etNote = EditText(ctx).apply { setText(meta.note ?: ""); hint = "Note" }
        wrap.addView(etName); wrap.addView(etNote)
        AlertDialog.Builder(ctx)
            .setTitle("Modifier le waypoint")
            .setView(wrap)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val upd = meta.copy(
                    name = etName.text.toString().ifBlank { meta.name },
                    note = etNote.text.toString().ifBlank { null }
                )
                WaypointStorage.delete(ctx, meta.id)
                WaypointStorage.add(ctx, upd)
                refreshWaypointsOnMap()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showMoveToGroupDialog(id: Long) {
        val groups = getAllGroupsSorted().toMutableList()
        if ("Divers" !in groups) groups.add(0, "Divers")
        groups.add("Nouveau groupe…")
        val current = getWaypointGroup(id)
        val idx = groups.indexOf(current).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Déplacer vers un groupe")
            .setSingleChoiceItems(groups.toTypedArray(), idx) { dlg, which ->
                val choice = groups[which]
                if (choice == "Nouveau groupe…") {
                    dlg.dismiss()
                    promptCreateGroupAndAssign(id)
                } else {
                    setWaypointGroup(id, choice)
                    PreferenceManager.getDefaultSharedPreferences(this)
                        .edit().putString("wp_last_group", choice).apply()
                    refreshWaypointsOnMap()
                    dlg.dismiss()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun promptCreateGroupAndAssign(id: Long) {
        val input = EditText(this).apply { hint = "Nom du groupe" }
        AlertDialog.Builder(this)
            .setTitle("Nouveau groupe")
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim().ifBlank { "Divers" }
                setWaypointGroup(id, name)
                PreferenceManager.getDefaultSharedPreferences(this)
                    .edit().putString("wp_last_group", name).apply()
                refreshWaypointsOnMap()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun confirmDeleteWaypoint(id: Long) {
        AlertDialog.Builder(this)
            .setTitle("Supprimer ce waypoint ?")
            .setMessage("Cette action supprimera aussi ses pièces jointes.")
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val atts = getAttachments(id)
                atts.forEach { path -> runCatching { File(path).delete() } }
                saveAttachments(id, emptyList())
                val map = loadGroupMap()
                map.remove(id)
                saveGroupMap(map)
                WaypointStorage.delete(this, id)
                refreshWaypointsOnMap()
                Toast.makeText(this, "Waypoint supprimé", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showAttachmentsDialog(id: Long) {
        val list = getAttachments(id)
        if (list.isEmpty()) {
            Toast.makeText(this, "Aucune pièce jointe.", Toast.LENGTH_SHORT).show(); return
        }
        val labels = list.map { File(it).name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Pièces jointes")
            .setItems(labels) { _, which -> openAttachmentFile(list[which]) }
            .setPositiveButton("Supprimer…") { _, _ ->
                val labels2 = list.map { File(it).name }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle("Supprimer une pièce")
                    .setItems(labels2) { _, w ->
                        val path = list[w]
                        runCatching { File(path).delete() }
                        list.removeAt(w)
                        saveAttachments(id, list)
                        Toast.makeText(this, "Supprimé.", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun openAttachmentFile(path: String) {
        val f = File(path)
        if (!f.exists()) {
            Toast.makeText(this, "Fichier introuvable.", Toast.LENGTH_SHORT).show(); return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { safeStartActivity(intent) }.onFailure {
            Toast.makeText(this, "Aucune application pour ouvrir lâ€™image.", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun addWaypointsFab() {
        if (waypointFab != null) return
        waypointFab = FloatingActionButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_myplaces)
            size = FloatingActionButton.SIZE_MINI
            setOnClickListener { showWaypointsManager() }
        }
        val m = resources.getDimensionPixelSize(R.dimen.fab_margin)
        val lp = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.START or android.view.Gravity.BOTTOM
            setMargins(m * 2 + 8, m, m, m * 12)
        }
        addContentView(waypointFab, lp)
    }

    private fun showWaypointsManager() {
        val groups = getAllGroupsSorted()
        val hidden = loadHiddenGroups()
        val names = groups.toTypedArray()
        val checks = groups.map { it !in hidden }.toBooleanArray()
        AlertDialog.Builder(this)
            .setTitle("Groupes de waypoints")
            .setMultiChoiceItems(names, checks) { _, which, isChecked ->
                val g = groups[which]
                if (isChecked) hidden.remove(g) else hidden.add(g)
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                saveHiddenGroups(hidden)
                refreshWaypointsOnMap()
            }
            .setNeutralButton("Nouveau groupe") { _, _ ->
                val input = EditText(this).apply { hint = "Nom du groupe" }
                AlertDialog.Builder(this)
                    .setTitle("Nouveau groupe")
                    .setView(input)
                    .setPositiveButton(android.R.string.ok, null)
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun getAllGroupsSorted(): MutableList<String> {
        val map = loadGroupMap()
        val set = map.values.toMutableSet()
        set.add("Divers")
        return set.toMutableList().sorted().toMutableList()
    }

    // === Un seul addWaypoint(meta) simple (celui utilisé partout) ===

    // ===== Groupes & visibilité (prefs locales) =====
    private fun loadGroupMap(): MutableMap<Long, String> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val json = prefs.getString("wp_group_by_id", "{}") ?: "{}"
        val out = mutableMapOf<Long, String>()
        return try {
            val o = JSONObject(json)
            o.keys().forEach { k -> out[k.toLong()] = o.getString(k) }
            out
        } catch (_: Throwable) {
            mutableMapOf()
        }
    }

    private fun saveGroupMap(map: Map<Long, String>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k.toString(), v) }
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit().putString("wp_group_by_id", o.toString()).apply()
    }

    private fun getWaypointGroup(id: Long): String = loadGroupMap()[id] ?: "Divers"
    private fun setWaypointGroup(id: Long, group: String) {
        val map = loadGroupMap(); map[id] = group; saveGroupMap(map)
    }

    private fun loadHiddenGroups(): MutableSet<String> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val json = prefs.getString("wp_hidden_groups", "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            val s = mutableSetOf<String>()
            for (i in 0 until arr.length()) s += arr.getString(i)
            s
        } catch (_: Throwable) {
            mutableSetOf()
        }
    }

    private fun saveHiddenGroups(set: Set<String>) {
        val arr = JSONArray(); set.forEach { arr.put(it) }
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit().putString("wp_hidden_groups", arr.toString()).apply()
    }

    // ===== Pièces jointes par waypoint =====
    private fun getAttachments(id: Long): MutableList<String> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val json = prefs.getString("wp_att_$id", "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            val out = mutableListOf<String>()
            for (i in 0 until arr.length()) out += arr.getString(i)
            out
        } catch (_: Throwable) {
            mutableListOf()
        }
    }

    private fun saveAttachments(id: Long, list: List<String>) {
        val arr = JSONArray(); list.forEach { arr.put(it) }
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit().putString("wp_att_$id", arr.toString()).apply()
    }

    private fun saveWaypointAttachment(id: Long, src: Uri): String? {
        return try {
            val dir = File(
                getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "waypoints"
            ).apply { mkdirs() }
            val ext = guessExtFor(src)
            val name = "wp_${id}_${
                SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    Locale.getDefault()
                ).format(java.util.Date())
            }.$ext"
            val out = File(dir, name)
            contentResolver.openInputStream(src)?.use { input ->
                out.outputStream().use { os -> input.copyTo(os) }
            } ?: return null
            val list = getAttachments(id)
            list += out.absolutePath
            saveAttachments(id, list)
            // Miroir dans Images/HikeTrack/Waypoints pour que la photo survive à une désinstallation
            try {
                val mime = contentResolver.getType(src) ?: when (ext.lowercase()) {
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "heic" -> "image/heic"
                    "heif" -> "image/heif"
                    else -> "image/*"
                }
                contentResolver.openInputStream(src)?.use { ins ->
                    com.hikemvp.backup.DataBackup.saveStreamToPictures(
                        this, name, mime, ins, "HikeTrack/Waypoints"
                    )
                }
            } catch (_: Throwable) { /* ignore */
            }
            out.absolutePath
        } catch (_: Throwable) {
            null
        }
    }

    private fun guessExtFor(uri: Uri): String {
        return runCatching {
            val type = contentResolver.getType(uri) ?: return@runCatching "jpg"
            when (type.lowercase(Locale.getDefault())) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/heic" -> "heic"
                "image/heif" -> "heif"
                else -> "jpg"
            }
        }.getOrDefault("jpg")
    }

    // Aides à lâ€™ajout programmatique
    // ======= Waypoints =======
// (garde le reste inchangé au-dessus : refreshWaypointsOnMap(), addWaypointMarker(...), showWaypointOptions(...), etc.)

    private fun addWaypoint(metaLat: Double, metaLon: Double, name: String, note: String?) {
        val meta = WaypointMeta(
            id = System.currentTimeMillis(),
            latitude = metaLat,
            longitude = metaLon,
            name = name,
            note = note
        )
        addWaypoint(meta)
    }

    /** Variante avec groupe explicite si tu lâ€™utilises quelque part */
    private fun addWaypoint(meta: WaypointMeta, group: String? = null) {
        val saved = WaypointStorage.add(this, meta)
        val g = group ?: PreferenceManager.getDefaultSharedPreferences(this)
            .getString("wp_last_group", "Divers") ?: "Divers"
        setWaypointGroup(saved.id, g)
        refreshWaypointsOnMap()
    }

    /** Entrée principale (ne DOIT exister quâ€™UNE fois) */
    private fun addWaypoint(meta: WaypointMeta) {
        val saved = WaypointStorage.add(this, meta)
        val g = PreferenceManager.getDefaultSharedPreferences(this)
            .getString("wp_last_group", "Divers") ?: "Divers"
        setWaypointGroup(saved.id, g)
        Toast.makeText(this, "Waypoint ajouté (${saved.name}) â†’ groupe $g", Toast.LENGTH_SHORT)
            .show()
        refreshWaypointsOnMap()
    }


    // ====== Tracé principal & marqueurs ======
    private fun showPolyline(points: List<GeoPoint>) {
        if (trackPolyline == null) {
            trackPolyline = Polyline(map).apply { outlinePaint.strokeWidth = 6f }
            trackPolyline!!.setOnClickListener { _, _, _ ->
                safeStartActivity(Intent(this, TrackDetailsActivity::class.java)); true
            }
            map.overlays.add(trackPolyline)
        }
        trackPolyline!!.setPoints(points)
        if (points.isNotEmpty()) map.controller.animateTo(points.last())
        map.invalidate()
    }

    private fun removeStartEndMarkers() {
        startMarker?.let { map.overlays.remove(it) }
        endMarker?.let { map.overlays.remove(it) }
        startMarker = null; endMarker = null
    }

    private fun setStartMarker(gp: GeoPoint) {
        if (startMarker == null) {
            startMarker = Marker(map).apply {
                icon = ContextCompat.getDrawable(this@MapActivity, R.drawable.ic_dot_green)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = getString(R.string.marker_start)
            }
            map.overlays.add(startMarker)
        }
        startMarker?.position = gp; map.invalidate()
    }

    private fun setEndMarker(gp: GeoPoint) {
        if (endMarker == null) {
            endMarker = Marker(map).apply {
                icon = ContextCompat.getDrawable(this@MapActivity, R.drawable.ic_dot_red)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = getString(R.string.marker_end)
            }
            map.overlays.add(endMarker)
        }
        endMarker?.position = gp; map.invalidate()
    }

    private fun updateStartEndMarkers() {
        removeStartEndMarkers()
        val pts = trackPolyline?.actualPoints ?: return
        if (pts.isEmpty()) return
        setStartMarker(pts.first()); setEndMarker(pts.last())
    }

    // ===== MENU =====
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_map, menu)
        setupRecordActionView(menu)
        updateRecordMenu(menu.findItem(R.id.action_record))

        menu.findItem(R.id.action_plan_toggle)?.isChecked = plannerMode
        menu.findItem(R.id.action_plan_clear)?.isVisible = planPoints.isNotEmpty()
        menu.findItem(R.id.action_plan_export)?.isVisible = planPoints.isNotEmpty()
        menu.findItem(R.id.action_plan_snap)?.isChecked = snapToTrails
        menu.findItem(R.id.action_nature_toggle)?.isChecked = natureGuidanceOn
        menu.findItem(R.id.action_protected_areas_toggle)?.isChecked = showProtectedAreas

        tintMenuIcons(menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        updateRecordMenu(menu.findItem(R.id.action_record))
        menu.findItem(R.id.action_follow)?.isChecked = isFollowing
        menu.findItem(R.id.action_pause)?.isVisible = isRecording && !isPaused
        menu.findItem(R.id.action_resume)?.isVisible = isRecording && isPaused

        menu.findItem(R.id.action_plan_toggle)?.isChecked = plannerMode
        menu.findItem(R.id.action_plan_clear)?.isVisible = planPoints.isNotEmpty()
        menu.findItem(R.id.action_plan_export)?.isVisible = planPoints.isNotEmpty()
        menu.findItem(R.id.action_plan_snap)?.isChecked = snapToTrails

        menu.findItem(R.id.action_nature_toggle)?.isChecked = natureGuidanceOn

        tintMenuIcons(menu)
        return super.onPrepareOptionsMenu(menu)
    }

    private fun setupRecordActionView(menu: Menu) {
        val item = menu.findItem(R.id.action_record)
        val btn =
            layoutInflater.inflate(R.layout.action_record_button, toolbar, false) as ImageButton
        item.actionView = btn
        btn.setOnClickListener { if (!isRecording) startRecording() else pauseOrResumeRecording() }
        btn.setOnLongClickListener {
            if (isRecording) {
                confirmStopRecording(); true
            } else false
        }
        updateRecordButtonIcon(btn)
    }

    private fun updateRecordButtonIcon(
        btn: ImageButton? = (toolbar.menu.findItem(R.id.action_record)?.actionView as? ImageButton)
    ) {
        val imageButton = btn ?: return
        val resId = if (isRecording) R.drawable.ic_pause_colored else R.drawable.ic_record_colored
        imageButton.setImageResource(resId)
}

    private fun updateRecordMenu(item: android.view.MenuItem) {
        item.isChecked = isRecording
        item.title = if (isRecording) getString(R.string.btn_stop) else "Enregistrer"
        item.setIcon(if (isRecording) R.drawable.ic_stop_square else R.drawable.ic_record_colored)
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_center_me -> {
                centerOnMe(); true
            }
            R.id.action_about -> {
                startActivity(Intent(this, com.hikemvp.about.AboutActivity::class.java))
                true
            }

            R.id.action_record -> {
                if (!isRecording) startRecording() else pauseOrResumeRecording(); true
            }

            R.id.action_pause -> {
                if (isRecording && !isPaused) pauseOrResumeRecording(); true
            }

            R.id.action_protected_areas_toggle -> {
                val newVal = !item.isChecked
                item.isChecked = newVal
                setProtectedAreasVisible(newVal)
                true
            }

            R.id.action_resume -> {
                if (isRecording && isPaused) pauseOrResumeRecording(); true
            }

            R.id.action_follow -> {
                toggleFollow(); true
            }
            R.id.action_group -> { openGroup() }

            R.id.action_about -> {
                startActivity(Intent(this, com.hikemvp.about.AboutActivity::class.java))
                true
            }

            // ===== Planification =====
            R.id.action_plan_toggle -> {
                togglePlanner(); true
            }

            R.id.action_plan_clear -> {
                clearPlan(); true
            }

            R.id.action_plan_export -> {
                exportPlan(); true
            }

            R.id.action_plan_snap -> {
                snapToTrails = !snapToTrails
                PreferenceManager.getDefaultSharedPreferences(this)
                    .edit().putBoolean("pref_key_snap_to_trails", snapToTrails).apply()
                item.isChecked = snapToTrails
                Toast.makeText(
                    this,
                    if (snapToTrails) "Snap ORS activé" else "Snap ORS désactivé",
                    Toast.LENGTH_SHORT
                ).show()
                true
            }

            R.id.action_offtrail_threshold -> {
                promptOfftrailThreshold(); true
            }

            // Réglages D+/D–
            R.id.action_elev_settings -> {
                showElevationTuningSheet(); true
            }

            R.id.action_elev_help -> {
                showElevationHelpDialog(); true
            }

            // Guidage nature
            R.id.action_nature_toggle -> {
                toggleNatureGuidance(); true
            }

            R.id.action_nature_sources -> {
                Toast.makeText(
                    this,
                    "Sources: assets/nature_pois.json (modifiable)",
                    Toast.LENGTH_LONG
                ).show()
                true
            }

            // Tracé / outils
            R.id.action_track_details -> {
                safeStartActivity(Intent(this, TrackDetailsActivity::class.java)); true
            }

            R.id.action_download_tiles -> {
                downloadVisibleTiles(); true
            }

            R.id.action_replay -> {
                if ((trackPolyline?.actualPoints?.size
                        ?: 0) < 2 && TrackStore.snapshot().size < 2
                ) {
                    Toast.makeText(this, "Aucun tracé à rejouer.", Toast.LENGTH_SHORT).show()
                } else safeStartActivity(Intent(this, ReplayActivity::class.java))
                true
            }

            R.id.action_profile -> {
                startActivity(Intent(this, ProfileActivity::class.java))
                true
            }

            // Autres écrans
            R.id.action_info_hub -> {
                runCatching { safeStartActivity(Intent(this, InfoHubActivity::class.java)) }
                    .onFailure {
                        Toast.makeText(
                            this,
                            "Infos rando indisponibles pour le moment.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                true
            }

            R.id.action_sos -> {
                runCatching { safeStartActivity(Intent(this, com.hikemvp.sos.SosActivity::class.java)) }
                    .onFailure {
                        Toast.makeText(
                            this,
                            "Écran SOS indisponible pour le moment.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                true
            }

            R.id.action_back_to_start -> {
                runCatching {
                    safeStartActivity(
                        Intent(
                            this,
                            com.hikemvp.backtostart.BackToStartActivity::class.java
                        )
                    )
                }
                    .onFailure {
                        Toast.makeText(
                            this,
                            "Retour au départ indisponible pour le moment.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                true
            }

            R.id.action_water_points -> {
                runCatching {
                    safeStartActivity(
                        Intent(
                            this,
                            com.hikemvp.water.WaterPointsActivity::class.java
                        )
                    )
                }
                    .onFailure {
                        Toast.makeText(
                            this,
                            "Points dâ€™eau : fonctionnalité à venir.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                true
            }

            // Fichiers / réglages
            R.id.action_open_tracks_folder -> {
                try {
                    val initial = Uri.parse(
                        "content://com.android.externalstorage.documents/document/primary:Android/data/$packageName/files/Documents"
                    )
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                        putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, initial)
                    }
                    safeStartActivity(intent)
                } catch (_: Throwable) {
                    Toast.makeText(
                        this,
                        "Impossible d’ouvrir le dossier ; utilisez un explorateur.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                true
            }

            R.id.action_waypoint_list -> {
                openWaypointListDialog(); true
            }

            R.id.action_aide_prevention -> {
                safeStartActivity(Intent(this, AidePreventionActivity::class.java)); true
            }

            R.id.action_gear -> {
                safeStartActivity(Intent(this, com.hikemvp.GearChecklistActivity::class.java)); true
            }

            R.id.action_settings -> {
                safeStartActivity(Intent(this, com.hikemvp.settings.SettingsActivity::class.java)); true
            }
            resources.getIdentifier("action_nature_import", "id", packageName) -> {
                safeStartActivity(Intent(this, com.hikemvp.nature.NatureImportActivity::class.java))
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun promptExportGpxName() {
        val poly = trackPolyline
        if (poly == null || poly.actualPoints.isNullOrEmpty()) {
            Toast.makeText(this, R.string.err_nothing_to_export, Toast.LENGTH_SHORT).show(); return
        }

        val input = EditText(this).apply {
            hint = getString(R.string.export_gpx_name_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            filters = arrayOf(InputFilter.LengthFilter(64))
            setText(getString(R.string.export_gpx_name_default))
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.export_gpx_name_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                var base = input.text.toString().trim()
                if (base.isEmpty()) base = getString(R.string.export_gpx_name_default)
                base = base.replace(Regex("[^A-Za-z0-9 _-]"), "_").replace(' ', '_')
                val fileName = if (base.endsWith(".gpx", true)) base else "$base.gpx"
                exportGpxCreate.launch(fileName)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun centerOnMe() {
        if (!hasLocationPerm()) {
            requestLocationPerm(); return
        }
        val loc = myLocation.myLocation
        if (loc != null) {
            if (map.zoomLevelDouble < 15.0) map.controller.setZoom(15.0)
            val gp = GeoPoint(loc.latitude, loc.longitude)
            map.controller.animateTo(gp)
            fetchWeatherIfMoved(loc.latitude, loc.longitude)
        } else {
            Toast.makeText(this, "Localisation non disponible…", Toast.LENGTH_SHORT).show()
            enableMyLocation()
        }
    }

    private fun downloadVisibleTiles() {
        val bbox = map.boundingBox
        val minZoom = 6
        val maxZoom = 16

        runCatching {
            val provider = map.tileProvider
            val ts = provider.tileSource
            if (provider is OfflineTileProvider || ts is FileBasedTileSource) {
                Toast.makeText(this, R.string.msg_already_offline, Toast.LENGTH_SHORT)
                    .show(); return
            }
            if (ts is OnlineTileSourceBase) {
                val policy = ts.tileSourcePolicy
                if (policy == null || !policy.acceptsBulkDownload()) {
                    Toast.makeText(this, R.string.msg_download_not_allowed, Toast.LENGTH_LONG)
                        .show(); return
                }
            }
        }.onFailure {
            Toast.makeText(this, R.string.msg_download_not_allowed, Toast.LENGTH_LONG)
                .show(); return
        }

        if (minZoom > maxZoom ||
            !bbox.latNorth.isFinite() || !bbox.latSouth.isFinite() ||
            !bbox.lonEast.isFinite() || !bbox.lonWest.isFinite()
        ) {
            Toast.makeText(this, R.string.msg_download_failed, Toast.LENGTH_SHORT).show(); return
        }

        try {
            val cm = CacheManager(map)
            val estimate =
                runCatching { cm.possibleTilesInArea(bbox, minZoom, maxZoom) }.getOrDefault(-1)
            if (estimate == 0) {
                Toast.makeText(this, R.string.msg_download_nothing, Toast.LENGTH_SHORT)
                    .show(); return
            }
            Toast.makeText(this, R.string.msg_download_start, Toast.LENGTH_SHORT).show()
            cm.downloadAreaAsyncNoUI(
                applicationContext, bbox, minZoom, maxZoom,
                object : CacheManager.CacheManagerCallback {
                    override fun downloadStarted() {}
                    override fun setPossibleTilesInArea(p0: Int) {}
                    override fun updateProgress(
                        progress: Int,
                        currentZoomLevel: Int,
                        zoomMin: Int,
                        zoomMax: Int
                    ) {
                    }

                    override fun onTaskComplete() {
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) Toast.makeText(
                                this@MapActivity,
                                R.string.msg_download_ok,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onTaskFailed(errors: Int) {
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) Toast.makeText(
                                this@MapActivity,
                                R.string.msg_download_failed,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            )
        } catch (_: Throwable) {
            if (!isFinishing && !isDestroyed) Toast.makeText(
                this,
                R.string.msg_download_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun toggleFollow() {
        isFollowing = !isFollowing
        if (isFollowing) {
            runCatching { myLocation.enableFollowLocation() }; Toast.makeText(
                this,
                "Suivi activé",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            runCatching { myLocation.disableFollowLocation() }; Toast.makeText(
                this,
                "Suivi désactivé",
                Toast.LENGTH_SHORT
            ).show()
        }
        invalidateOptionsMenu()
    }

    private fun startRecording() {
        isRecording = true; isPaused = false; followWhileRecording = true
        runCatching { if (isFollowing) myLocation.enableFollowLocation() }
        startTimeMs = System.currentTimeMillis()
        resetStats()
        resetElevationFilterState()
        updateStatsVisibility()
        startElapsedTicker()
        updateToolbarTheme()
        try {
            val svc = Intent(
                this,
                TrackRecordingService::class.java
            ).setAction(Constants.ACTION_START_RECORD)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(
                this,
                svc
            ) else startService(svc)
            Toast.makeText(this, R.string.msg_rec_started, Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
            isRecording = false; isPaused = false
            updateToolbarTheme(); updateStatsVisibility()
            Toast.makeText(this, getString(R.string.err_recording_unavailable), Toast.LENGTH_LONG)
                .show()
        }
        invalidateOptionsMenu(); updateRecordButtonIcon(); maybeStartOffTrailTicker()
    }

    private fun pauseOrResumeRecording() {
        if (!isRecording) return
        val action =
            if (!isPaused) Constants.ACTION_PAUSE_RECORD else Constants.ACTION_RESUME_RECORD
        try {
            val svc = Intent(this, TrackRecordingService::class.java).setAction(action)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(
                this,
                svc
            ) else startService(svc)
            isPaused = !isPaused
            if (isPaused) {
                stopElapsedTicker(); stopOffTrailTicker(); Toast.makeText(
                    this,
                    R.string.msg_rec_paused,
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                startElapsedTicker(); maybeStartOffTrailTicker(); Toast.makeText(
                    this,
                    R.string.msg_rec_resumed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (_: Throwable) {
            Toast.makeText(this, getString(R.string.err_recording_unavailable), Toast.LENGTH_LONG)
                .show()
        }
        invalidateOptionsMenu(); updateRecordButtonIcon()
    }

    private fun confirmStopRecording() {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_stop_title)
            .setMessage(R.string.confirm_stop_msg)
            .setPositiveButton(R.string.btn_stop) { _, _ -> stopRecording() }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun stopRecording() {
        if (!isRecording) return
        stopElapsedTicker(); stopOffTrailTicker()
        isRecording = false; isPaused = false
        updateStatsVisibility(); updateToolbarTheme()
        try {
            val svc = Intent(
                this,
                TrackRecordingService::class.java
            ).setAction(Constants.ACTION_STOP_RECORD)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(
                this,
                svc
            ) else startService(svc)
            Toast.makeText(this, R.string.msg_rec_stopped, Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
            Toast.makeText(this, getString(R.string.err_recording_unavailable), Toast.LENGTH_LONG)
                .show()
        }
        invalidateOptionsMenu(); updateRecordButtonIcon()
    }

    private fun confirmClearTrack() {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_clear_title)
            .setMessage(R.string.confirm_clear_msg)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                TrackStore.clear(); trackPoints.clear(); trackPolyline?.setPoints(emptyList())
                removeStartEndMarkers(); resetStats(); resetElevationFilterState(); map.invalidate()
                ActiveTrackRepo.clear(this)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun updateToolbarTheme() {
        val bg = if (isRecording) R.color.topbar_rec_bg else R.color.topbar_idle_bg
        val fg = if (isRecording) R.color.topbar_rec_icon else R.color.topbar_idle_icon
        toolbar.setBackgroundColor(ContextCompat.getColor(this, bg))
        toolbar.setTitleTextColor(ContextCompat.getColor(this, fg))
        toolbar.navigationIcon?.mutate()?.setTint(ContextCompat.getColor(this, fg))
        toolbar.overflowIcon?.mutate()?.setTint(ContextCompat.getColor(this, fg))
        toolbar.menu?.let { tintMenuIcons(it) }
    }

    private fun tintMenuIcons(menu: Menu) {
        val color = ContextCompat.getColor(
            this,
            if (isRecording) R.color.topbar_rec_icon else R.color.topbar_idle_icon
        )
        for (i in 0 until menu.size()) {
            val it = menu.getItem(i); it.icon?.mutate()?.setTint(color)
        }
        toolbar.overflowIcon?.mutate()?.setTint(color)
    }

    private fun addGpxFab() {
        if (gpxFab != null) return
        gpxFab = ExtendedFloatingActionButton(this).apply {
            text = "GPX"; setIconResource(R.drawable.ic_export)
            iconPadding = resources.getDimensionPixelSize(R.dimen.fab_icon_padding)
            setOnClickListener { showGpxActionsPopup(this) }
        }
        val size = resources.getDimensionPixelSize(R.dimen.fab_size_normal)
        val m = resources.getDimensionPixelSize(R.dimen.fab_margin)
        val lp = android.widget.FrameLayout.LayoutParams(
            size,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        )
        lp.gravity = android.view.Gravity.END or android.view.Gravity.BOTTOM
        lp.setMargins(m, m, m, m)
        addContentView(gpxFab, lp)
        updateGpxFabPosition()
    }

    private fun updateGpxFabPosition() {
        val fab = gpxFab ?: return
        val params = fab.layoutParams as? android.widget.FrameLayout.LayoutParams ?: return
        val m = resources.getDimensionPixelSize(R.dimen.fab_margin)
        val extra = if (cardStats.visibility == View.VISIBLE) m * 2 else 0
        params.setMargins(m, m, m, m + extra)
        fab.layoutParams = params
    }

    private fun showGpxActionsPopup(anchor: View) {
        val pm = PopupMenu(this, anchor)
        pm.menu.add(Menu.NONE, 99, 0, "Importer fichier…")
        pm.menu.add(Menu.NONE, 98, 0, "Effacer imports (KML/CSV)") // <— AJOUT
        pm.menu.add(Menu.NONE, 1, 1, getString(R.string.menu_import_gpx))
        pm.menu.add(Menu.NONE, 2, 2, getString(R.string.menu_export_gpx))
        pm.menu.add(Menu.NONE, 3, 3, getString(R.string.menu_clear_track))
        pm.menu.add(Menu.NONE, 4, 4, getString(R.string.saved_tracks_title))
        pm.setOnMenuItemClickListener {
            when (it.itemId) {
                98 -> { clearImportedOverlays() }
                99 -> {
                    // KML/GPX/CSV/… via SAF
                    importAnyDoc.launch(arrayOf("*/*"))
                }
                1 -> importGpx.launch(
                    arrayOf("*/*","application/gpx+xml","application/octet-stream","text/xml")
                )
                2 -> promptExportGpxName()
                3 -> confirmClearTrack()
                4 -> safeStartActivity(Intent(this, com.hikemvp.files.SavedTracksActivity::class.java))
            }
            true
        }
        pm.show()
    }


    private fun addPlannerHud() {
        if (cardPlan != null) return
        val ctx = this
        val density = resources.displayMetrics.density
        val card = MaterialCardView(ctx).apply {
            radius = 16f * density
            cardElevation = 6f * density
            setCardBackgroundColor(ContextCompat.getColor(ctx, android.R.color.white))
            preventCornerOverlap = true
            useCompatPadding = true
            visibility = View.GONE
        }
        val wrap = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (24 * density).toInt(),
                (20 * density).toInt(),
                (24 * density).toInt(),
                (20 * density).toInt()
            )
        }
        tvPlanDist = TextView(ctx)
        tvPlanElev = TextView(ctx)
        tvPlanTime = TextView(ctx)
        tvPlanDiff = TextView(ctx)
        wrap.addView(tvPlanDist); wrap.addView(tvPlanElev); wrap.addView(tvPlanTime); wrap.addView(
            tvPlanDiff
        )
        card.addView(wrap)
        cardPlan = card
        tvPlanElev?.setOnLongClickListener {
            forceRecomputePlanElevation()
            true
        }

        val lp = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        )
        lp.gravity = android.view.Gravity.START or android.view.Gravity.BOTTOM
        val m = resources.getDimensionPixelSize(R.dimen.fab_margin)
        lp.setMargins(m, m, m, m * 4)
        addContentView(card, lp)

        planHelpFab = FloatingActionButton(ctx).apply {
            setImageResource(android.R.drawable.ic_menu_help)
            size = FloatingActionButton.SIZE_MINI
            visibility = View.GONE
            setOnClickListener { showPlannerHelpDialog() }
        }
        val lpHelp = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        )
        lpHelp.gravity = android.view.Gravity.START or android.view.Gravity.BOTTOM
        lpHelp.setMargins(m * 2 + 8, m, m, m * 9)
        addContentView(planHelpFab, lpHelp)

        planTrimFab = FloatingActionButton(ctx).apply {
            setImageResource(android.R.drawable.ic_menu_crop)
            size = FloatingActionButton.SIZE_MINI
            visibility = View.GONE
            setOnClickListener { showTrimPlanDialog() }
        }
        val lpTrim = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        )
        lpTrim.gravity = android.view.Gravity.START or android.view.Gravity.BOTTOM
        lpTrim.setMargins(m * 2 + 8, m, m, m * 6)
        addContentView(planTrimFab, lpTrim)

        updatePlannerHud()
    }

    private fun showPlannerHud(show: Boolean) {
        cardPlan?.visibility = if (show) View.VISIBLE else View.GONE
        planHelpFab?.visibility = if (show) View.VISIBLE else View.GONE
        planTrimFab?.visibility = if (show && (planPoints.size >= 2)) View.VISIBLE else View.GONE
    }

    private fun updatePlannerHud() {
        val distM = computePlanDistanceM()
        val distKm = distM / 1000.0
        tvPlanDist?.text = "Plan • Distance : %.2f km".format(distKm)

        val (g, _) = computeGainLossFiltered(planPoints)
        if (!hasPlanAlt() && planPoints.size >= 2) {
            tvPlanElev?.text = "Dénivelé : 0 m (alt. en cours…)"
            ensurePlanElevationIfMissing()
        } else {
            tvPlanElev?.text = "Dénivelé : ${g.roundToInt()} m"
        }

        tvPlanTime?.text = "Durée estimée : " + estimatePlanTime(distKm)
        tvPlanDiff?.text = "Difficulté : " + estimatePlanDifficulty(distKm)

        planTrimFab?.visibility =
            if ((plannerMode || planPoints.isNotEmpty()) && planPoints.size >= 2) View.VISIBLE else View.GONE
    }

    private fun estimatePlanTime(distKm: Double): String {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val speed = sp.getString("pref_key_plan_speed_kmh", "4.0")?.toDoubleOrNull() ?: 4.0
        val hours = if (speed > 0) distKm / speed else 0.0
        val h = hours.toInt();
        val m = ((hours - h) * 60.0).toInt()
        return "%dh%02d".format(h, m)
    }

    private fun estimatePlanDifficulty(distKm: Double): String =
        when {
            distKm < 8 -> "Facile"; distKm < 16 -> "Modérée"; distKm < 25 -> "Difficile"; else -> "Très difficile"
        }

    private fun computePlanDistanceM(): Double {
        if (planPoints.size < 2) return 0.0
        var d = 0.0
        for (i in 1 until planPoints.size) d += planPoints[i - 1].distanceToAsDouble(planPoints[i])
        return d
    }

    private fun addPlanPoint(p: GeoPoint) {
        if (planPoints.isEmpty() || !snapToTrails || orsKey.isBlank()) {
            addPlanPointStraight(p); return
        }
        val a = planPoints.last()
        routeBetweenORS(a, p) { routed ->
            if (routed == null || routed.size < 2) {
                runOnUiThread { addPlanPointStraight(p) }
            } else {
                val toAppend = routed.drop(1)
                runOnUiThread {
                    planPoints.addAll(toAppend)
                    fixFirstPlanPointElevationFromNext()
                    planPolyline?.setPoints(planPoints)
                    updatePlannerHud()
                    showPlannerHud(true)
                    map.invalidate()
                    maybeStartOffTrailTicker()
                    ensurePlanElevationIfMissing()
                }
            }
        }
    }

    private fun addPlanPointStraight(p: GeoPoint) {
        planPoints.add(p)
        fixFirstPlanPointElevationFromNext()
        planPolyline?.setPoints(planPoints)
        updatePlannerHud()
        showPlannerHud(true)
        map.invalidate()
        maybeStartOffTrailTicker()
        ensurePlanElevationIfMissing()
    }

    private fun routeBetweenORS(a: GeoPoint, b: GeoPoint, onResult: (List<GeoPoint>?) -> Unit) {
        if (orsKey.isBlank()) {
            onResult(null); return
        }

        val url = "https://api.openrouteservice.org/v2/directions/foot-hiking/geojson"
        val media = "application/json; charset=utf-8".toMediaType()
        val bodyJson = """
            {
              "coordinates":[[${a.longitude},${a.latitude}],[${b.longitude},${b.latitude}]],
              "instructions": false,
              "elevation": true
            }
        """.trimIndent()

        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", orsKey)
            .addHeader("Cache-Control", "no-cache")
            .post(bodyJson.toRequestBody(media))
            .build()

        http.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                onResult(null)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        onResult(null); return
                    }
                    val txt = resp.body?.string() ?: return onResult(null)
                    try {
                        val root = JSONObject(txt)
                        val features = root.optJSONArray("features") ?: return onResult(null)
                        if (features.length() == 0) {
                            onResult(null); return
                        }
                        val geom = features.getJSONObject(0).getJSONObject("geometry")
                        if (!"LineString".equals(geom.optString("type"), ignoreCase = true)) {
                            onResult(null); return
                        }
                        val coords = geom.getJSONArray("coordinates")
                        val pts = ArrayList<GeoPoint>(coords.length())
                        for (i in 0 until coords.length()) {
                            val c = coords.getJSONArray(i)
                            val lon = c.getDouble(0);
                            val lat = c.getDouble(1)
                            val ele =
                                if (c.length() >= 3) c.optDouble(2, Double.NaN) else Double.NaN
                            if (!ele.isNaN()) pts.add(
                                GeoPoint(
                                    lat,
                                    lon,
                                    ele
                                )
                            ) else pts.add(GeoPoint(lat, lon))
                        }
                        onResult(pts)
                    } catch (_: Throwable) {
                        onResult(null)
                    }
                }
            }
        })
    }

    private fun clearPlan() {
        planPoints.clear()
        planPolyline?.setPoints(emptyList())
        updatePlannerHud()
        invalidateOptionsMenu()
        map.invalidate()
        stopOffTrailTicker()
    }

    private fun exportPlan() {
        if (planPoints.isEmpty()) {
            Toast.makeText(this, R.string.err_nothing_to_export, Toast.LENGTH_SHORT).show(); return
        }
        val time = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.getDefault())
            .format(java.util.Date())
        exportPlanCreate.launch("plan_$time.gpx")
    }

    private fun togglePlanner() {
        plannerMode = !plannerMode
        Toast.makeText(
            this,
            if (plannerMode) "Planification ON (tap pour ajouter)" else "Planification OFF",
            Toast.LENGTH_SHORT
        ).show()
        showPlannerHud(plannerMode || planPoints.isNotEmpty())
        if (plannerMode && !plannerHelpShown) {
            showPlannerHelpDialog()
            PreferenceManager.getDefaultSharedPreferences(this)
                .edit().putBoolean("pref_key_planner_help_shown", true).apply()
            plannerHelpShown = true
        }
        invalidateOptionsMenu()
        maybeStartOffTrailTicker()
    }

    // ===== Stats =====
    private fun resetStats() {
        lastPoint = null
        totalDistanceM = 0.0
        totalGainM = 0.0
        totalLossM = 0.0
        if (isRecording) startTimeMs = System.currentTimeMillis()
        updateStatsUI()
    }

    private fun updateStatsWithPoint(p: GeoPoint) {
        val prev = lastPoint
        if (prev != null) {
            val d = prev.distanceToAsDouble(p)
            totalDistanceM += max(0.0, d)

            pushMedian(liveAltWindow, p.altitude, elevMedianN)
            val med = median(liveAltWindow)
            val newF = when {
                med == null -> null
                lastAltFiltered == null -> med
                else -> elevAlpha * med + (1 - elevAlpha) * lastAltFiltered!!
            }?.also { lastAltFiltered = it }

            if (newF != null && d >= elevMinHorizM) {
                if (liveBaseAlt == null) {
                    liveBaseAlt = newF; liveExtAlt = newF; liveTrend = 0
                }
                when (liveTrend) {
                    0 -> {
                        if (newF > liveExtAlt!!) liveExtAlt = newF
                        if (newF < liveExtAlt!! - elevMinStepM) {
                            totalGainM += (liveExtAlt!! - liveBaseAlt!!)
                            liveBaseAlt = newF; liveExtAlt = newF; liveTrend = -1
                        }
                        if (newF < liveExtAlt!!) liveExtAlt = min(liveExtAlt!!, newF)
                        if (newF > liveExtAlt!! + elevMinStepM) {
                            totalLossM += (liveBaseAlt!! - liveExtAlt!!)
                            liveBaseAlt = newF; liveExtAlt = newF; liveTrend = +1
                        }
                    }

                    +1 -> {
                        if (newF >= liveExtAlt!!) liveExtAlt = newF
                        else if (liveExtAlt!! - newF >= elevMinStepM) {
                            totalGainM += (liveExtAlt!! - liveBaseAlt!!)
                            liveBaseAlt = newF; liveExtAlt = newF; liveTrend = -1
                        }
                    }

                    -1 -> {
                        if (newF <= liveExtAlt!!) liveExtAlt = newF
                        else if (newF - liveExtAlt!! >= elevMinStepM) {
                            totalLossM += (liveBaseAlt!! - liveExtAlt!!)
                            liveBaseAlt = newF; liveExtAlt = newF; liveTrend = +1
                        }
                    }
                }
            }
        } else if (isRecording && startTimeMs == null) {
            startTimeMs = System.currentTimeMillis()
        }
        lastPoint = p
        updateStatsUI()
    }

    private fun computeStatsFrom(points: List<GeoPoint>) {
        if (points.size < 2) return
        for (i in 1 until points.size) {
            val a = points[i - 1];
            val b = points[i]
            totalDistanceM += max(0.0, a.distanceToAsDouble(b))
        }
        val (g, l) = computeGainLossFiltered(points)
        totalGainM += g; totalLossM += l
        seedLiveElevationFrom(points.last())
        if (startTimeMs == null) startTimeMs = System.currentTimeMillis()
    }

    private fun updateStatsUI() {
        tvStatDist.text = String.format("%.2f km", totalDistanceM / 1000.0)
        tvStatGain.text = String.format("D+ %d m", totalGainM.roundToInt())
        tvStatLoss.text = String.format("D- %d m", totalLossM.roundToInt())
        val elapsedMs = (startTimeMs?.let { System.currentTimeMillis() - it } ?: 0L)
        val hours = elapsedMs / 3600000L;
        val mins = (elapsedMs / 60000L) % 60;
        val secs = (elapsedMs / 1000L) % 60
        tvStatElapsed.text = String.format("%02d:%02d:%02d", hours, mins, secs)
        val speedKmh = if (elapsedMs > 0) (totalDistanceM / (elapsedMs / 1000.0)) * 3.6 else 0.0
        tvStatSpeed.text = String.format("%.1f km/h", speedKmh)
    }

    private fun updateStatsVisibility() {
        cardStats.visibility = if (isRecording) View.VISIBLE else View.GONE
        updateGpxFabPosition()
    }

    private fun startElapsedTicker() {
        stopElapsedTicker()
        if (!isRecording || isPaused) return
        if (startTimeMs == null) startTimeMs = System.currentTimeMillis()
        ticker = android.os.Handler(mainLooper).also { h ->
            h.post(object : Runnable {
                override fun run() {
                    updateStatsUI()
                    if (isRecording && !isPaused) h.postDelayed(this, 1000)
                }
            })
        }
    }

    private fun stopElapsedTicker() {
        ticker?.removeCallbacksAndMessages(null); ticker = null
    }

    // ======== Guidage nature ========
    private enum class NatureType { TREE, SHELTER, PROTECTED }
    private data class NaturePOI(
        val id: String,
        val type: NatureType,
        val name: String,
        val lat: Double,
        val lon: Double,
        val radiusM: Double,
        val note: String?
    )

    private fun loadNaturePois(): List<NaturePOI> {
        return try {
            val list = mutableListOf<NaturePOI>()
            val user = java.io.File(filesDir, "nature_pois_user.json")
            if (user.exists()) {
                val txt = user.readText(Charsets.UTF_8)
                val arr = JSONArray(txt)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.optString("id", "poi_$i")
                    val typeStr = o.optString("type", "PROTECTED")
                    val type =
                        runCatching { NatureType.valueOf(typeStr) }.getOrDefault(NatureType.PROTECTED)
                    val name = o.optString("name", "Point")
                    val lat = o.optDouble("lat")
                    val lon = o.optDouble("lon")
                    val radiusM =
                        if (o.has("radiusM") && !o.isNull("radiusM")) o.optDouble("radiusM") else 50.0
                    val note = if (o.has("note") && !o.isNull("note")) o.getString("note") else null
                    list += NaturePOI(id, type, name, lat, lon, radiusM, note)
                }
            } else {
                assets.open("nature_pois.json").use { input ->
                    val txt = input.bufferedReader(Charsets.UTF_8).readText()
                    val arr = JSONArray(txt)
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val id = o.optString("id", "poi_$i")
                        val typeStr = o.optString("type", "PROTECTED")
                        val type =
                            runCatching { NatureType.valueOf(typeStr) }.getOrDefault(NatureType.PROTECTED)
                        val name = o.optString("name", "Point")
                        val lat = o.optDouble("lat")
                        val lon = o.optDouble("lon")
                        val radiusM =
                            if (o.has("radiusM") && !o.isNull("radiusM")) o.optDouble("radiusM") else 50.0
                        val note =
                            if (o.has("note") && !o.isNull("note")) o.getString("note") else null
                        list += NaturePOI(id, type, name, lat, lon, radiusM, note)
                    }
                }
            }
            list
        } catch (t: Throwable) {
            t.printStackTrace()
            emptyList()
        }
    }


    private fun setNatureMarkersVisible(visible: Boolean) {
        if (visible && natureMarkers.isEmpty()) {
            naturePois.forEach { poi ->
                val m = Marker(map).apply {
                    position = GeoPoint(poi.lat, poi.lon)
                    title = poi.name
                    subDescription = poi.note ?: ""
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = when (poi.type) {
                        NatureType.TREE -> ContextCompat.getDrawable(
                            this@MapActivity,
                            android.R.drawable.ic_menu_myplaces
                        )

                        NatureType.SHELTER -> ContextCompat.getDrawable(
                            this@MapActivity,
                            android.R.drawable.ic_menu_compass
                        )

                        NatureType.PROTECTED -> ContextCompat.getDrawable(
                            this@MapActivity,
                            android.R.drawable.ic_dialog_info
                        )
                    }
                }
                natureMarkers += m; map.overlays.add(m)
                        importedOverlays.add(m)
                    }
            map.invalidate()
        } else if (!visible && natureMarkers.isNotEmpty()) {
            natureMarkers.forEach { map.overlays.remove(it) }
            natureMarkers.clear(); map.invalidate()
        }
    }

    private fun toggleNatureGuidance() {
        natureGuidanceOn = !natureGuidanceOn
        setNatureMarkersVisible(natureGuidanceOn)
        if (natureGuidanceOn) startNatureTicker() else stopNatureTicker()
        invalidateOptionsMenu()
        Toast.makeText(
            this,
            if (natureGuidanceOn) "Guidage nature activé" else "Guidage nature désactivé",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun startNatureTicker() {
        stopNatureTicker()
        natureTicker = android.os.Handler(android.os.Looper.getMainLooper()).also { h ->
            h.post(object : Runnable {
                override fun run() {
                    myLocation.myLocation?.let { loc ->
                        checkNatureProximity(
                            GeoPoint(
                                loc.latitude,
                                loc.longitude
                            )
                        )
                    }
                    if (natureGuidanceOn) h.postDelayed(this, 5000)
                }
            })
        }
    }

    private fun stopNatureTicker() {
        natureTicker?.removeCallbacksAndMessages(null); natureTicker = null
    }

    private fun checkNatureProximity(current: GeoPoint) {
        naturePois.forEach { poi ->
            if (poi.id in natureNotified) return@forEach
            val d = current.distanceToAsDouble(GeoPoint(poi.lat, poi.lon))
            if (d <= poi.radiusM) {
                notifyNaturePoi(poi, d); natureNotified += poi.id
            }
        }
    }

    private fun notifyNaturePoi(poi: NaturePOI, distanceM: Double) {
        if (!natureGuidanceOn) return
        val msg = when (poi.type) {
            NatureType.TREE -> "Arbre remarquable : ${poi.name} – à ${distanceM.toInt()} m"
            NatureType.SHELTER -> "Refuge : ${poi.name} – à ${distanceM.toInt()} m"
            NatureType.PROTECTED -> "Zone protégée : ${poi.name} – à ${distanceM.toInt()} m"
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    // ===== Off-trail =====
    private fun distanceMetersToPolyline(p: GeoPoint, path: List<GeoPoint>): Double {
        if (path.size < 2) return Double.POSITIVE_INFINITY

        val lat0 = Math.toRadians(p.latitude)
        val lon0 = Math.toRadians(p.longitude)
        val cosLat0 = kotlin.math.cos(lat0)
        val R = 6371000.0

        fun toXY(g: GeoPoint): Pair<Double, Double> {
            val lat = Math.toRadians(g.latitude)
            val lon = Math.toRadians(g.longitude)
            val x = (lon - lon0) * cosLat0 * R
            val y = (lat - lat0) * R
            return x to y
        }

        val (px, py) = toXY(p)
        var best = Double.POSITIVE_INFINITY
        var (ax, ay) = toXY(path[0])

        for (i in 1 until path.size) {
            val (bx, by) = toXY(path[i])

            val vx = bx - ax
            val vy = by - ay
            val wx = px - ax
            val wy = py - ay

            val vv = vx * vx + vy * vy
            val t = if (vv <= 0.0) 0.0 else ((wx * vx + wy * vy) / vv).coerceIn(0.0, 1.0)

            val cx = ax + t * vx
            val cy = ay + t * vy

            val dx = px - cx
            val dy = py - cy

            val d = hypot(dx, dy)
            if (d < best) best = d

            ax = bx; ay = by
        }
        return best
    }

    private fun startOffTrailTicker() {
        stopOffTrailTicker()
        offTrailTicker = android.os.Handler(mainLooper).also { h ->
            h.post(object : Runnable {
                override fun run() {
                    try {
                        checkOffTrail()
                    } catch (_: Throwable) {
                    }
                    val hasPlanWatch =
                        offTrailEnabled && isRecording && planPoints.size >= 2 && !plannerMode
                    val hasTrackAlarms = otherTracks.values.any {
                        it.alarmMeters != null && (it.polyline.actualPoints?.size ?: 0) >= 2
                    }
                    if (offTrailEnabled && (hasPlanWatch || hasTrackAlarms)) h.postDelayed(
                        this,
                        2000
                    )
                }
            })
        }
    }

    private fun stopOffTrailTicker() {
        offTrailTicker?.removeCallbacksAndMessages(null)
        offTrailTicker = null
        offTrailSince = null
        offTrailNotified = false
        otherTracks.values.forEach { it.offSince = null; it.offNotified = false }
    }

    private fun checkOffTrail() {
        val fix = myLocation.myLocation ?: return
        val me = GeoPoint(fix.latitude, fix.longitude)

        if (offTrailEnabled && isRecording && planPoints.size >= 2 && !plannerMode) {
            val d = distanceMetersToPolyline(me, planPoints)
            val now = System.currentTimeMillis()
            if (d >= offTrailThresholdM) {
                if (offTrailSince == null) offTrailSince = now
                val elapsed = now - (offTrailSince ?: now)
                if (elapsed >= offTrailMinDurationMs && !offTrailNotified) {
                    offTrailNotified = true
                    Toast.makeText(this, "Hors sentier (~${d.toInt()} m)", Toast.LENGTH_LONG).show()
                }
            } else {
                offTrailSince = null
                if (offTrailNotified) offTrailNotified = false
            }
        }

        otherTracks.values.forEach { t ->
            val m = t.alarmMeters ?: return@forEach
            val pts = t.polyline.actualPoints ?: return@forEach
            if (pts.size < 2) return@forEach

            val d = distanceMetersToPolyline(me, pts)
            val now = System.currentTimeMillis()
            if (d >= m) {
                if (t.offSince == null) t.offSince = now
                val elapsed = now - (t.offSince ?: now)
                if (elapsed >= offTrailMinDurationMs && !t.offNotified) {
                    t.offNotified = true
                    Toast.makeText(this, "Hors de '${t.name}' (~${d.toInt()} m)", Toast.LENGTH_LONG)
                        .show()
                }
            } else {
                t.offSince = null; t.offNotified = false
            }
        }
    }

    private fun maybeStartOffTrailTicker() {
        val hasPlanWatch = offTrailEnabled && isRecording && planPoints.size >= 2 && !plannerMode
        val hasTrackAlarms = otherTracks.values.any {
            it.alarmMeters != null && (it.polyline.actualPoints?.size ?: 0) >= 2
        }
        if (hasPlanWatch || hasTrackAlarms) startOffTrailTicker() else stopOffTrailTicker()
    }

    private fun promptOfftrailThreshold() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Distance en mètres"
            setText(offTrailThresholdM.roundToInt().toString())
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("Seuil hors-sentier (m)")
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val v = input.text.toString().replace(',', '.').toDoubleOrNull()
                if (v != null && v > 0) {
                    offTrailThresholdM = v
                    PreferenceManager.getDefaultSharedPreferences(this)
                        .edit().putString("pref_key_offtrail_threshold_m", v.toString()).apply()
                    Toast.makeText(this, "Seuil réglé à ${v.roundToInt()} m", Toast.LENGTH_SHORT)
                        .show()
                    maybeStartOffTrailTicker()
                } else Toast.makeText(this, "Valeur invalide.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    // --- Plan: altitude helpers ---
    private fun hasPlanAlt(): Boolean = planPoints.any { !it.altitude.isNaN() }

    private fun fixFirstPlanPointElevationFromNext() {
        if (planPoints.size >= 2) {
            val a = planPoints[0]
            val b = planPoints[1]
            if (a.altitude.isNaN() && !b.altitude.isNaN()) a.altitude = b.altitude
        }
    }

    private fun requestElevationForLine(indices: IntRange, onDone: (Boolean) -> Unit) {
        if (indices.count() < 2) {
            onDone(false); return
        }

        val media = "application/json; charset=utf-8".toMediaType()
        val coords = JSONArray().apply {
            for (i in indices) put(
                JSONArray().put(planPoints[i].longitude).put(planPoints[i].latitude)
            )
        }
        val geometry = JSONObject().put("type", "LineString").put("coordinates", coords)
        val body = JSONObject()
            .put("format_in", "geojson")
            .put("format_out", "geojson")
            .put("geometry", geometry)

        val req = Request.Builder()
            .url("https://api.openrouteservice.org/elevation/line")
            .addHeader("Authorization", orsKey)
            .post(body.toString().toRequestBody(media))
            .build()

        http.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                runOnUiThread {
                    Toast.makeText(
                        this@MapActivity,
                        "Altimétrie ORS indisponible",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                onDone(false)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val code = resp.code
                        runOnUiThread {
                            Toast.makeText(
                                this@MapActivity,
                                "ORS elevation: erreur $code",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        onDone(false); return
                    }
                    val txt = resp.body?.string() ?: run { onDone(false); return }
                    try {
                        val root = JSONObject(txt)
                        val geom = root.optJSONObject("geometry") ?: run { onDone(false); return }
                        val arr = geom.optJSONArray("coordinates") ?: run { onDone(false); return }

                        val n = kotlin.math.min(arr.length(), indices.count())
                        for (k in 0 until n) {
                            val c = arr.getJSONArray(k)
                            if (c.length() >= 3) {
                                val ele = c.optDouble(2, Double.NaN)
                                if (!ele.isNaN()) planPoints[indices.first + k].altitude = ele
                            }
                        }
                        onDone(true)
                    } catch (_: Throwable) {
                        onDone(false)
                    }
                }
            }
        })
    }

    private fun ensurePlanElevationIfMissing(force: Boolean = false) {
        if (orsKey.isBlank()) return
        if (planPoints.size < 2) return
        if (!force && hasPlanAlt()) return

        val maxPts = 200
        val ranges = mutableListOf<IntRange>()
        var start = 0
        val last = planPoints.size - 1
        while (start < last) {
            val end = min(start + maxPts - 1, last)
            ranges += (start..end)
            start = end
        }
        if (ranges.isEmpty()) return

        fetchElevationRangesSequential(ranges) {
            runOnUiThread {
                fixFirstPlanPointElevationFromNext()
                updatePlannerHud()
                map.invalidate()
            }
        }
    }

    private fun fetchElevationRangesSequential(ranges: List<IntRange>, onAllDone: () -> Unit) {
        var idx = 0
        fun next() {
            if (idx >= ranges.size) {
                onAllDone(); return
            }
            val r = ranges[idx++]
            fetchElevationForRange(r) { next() }
        }
        next()
    }

    private fun fetchElevationForRange(range: IntRange, onDone: () -> Unit) {
        try {
            val url = "https://api.openrouteservice.org/elevation/line"
            val media = "application/json; charset=utf-8".toMediaType()

            val coords = JSONArray().apply {
                for (i in range) {
                    val p = planPoints[i]
                    put(JSONArray().put(p.longitude).put(p.latitude))
                }
            }

            val body = JSONObject().apply {
                put("format_in", "geojson")
                put("format_out", "geojson")
                put("geometry", coords)
            }

            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", orsKey)
                .addHeader("Cache-Control", "no-cache")
                .post(body.toString().toRequestBody(media))
                .build()

            http.newCall(req).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    onDone()
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.use { resp ->
                        if (!resp.isSuccessful) {
                            onDone(); return
                        }
                        val txt = resp.body?.string() ?: return onDone()
                        try {
                            val root = JSONObject(txt)
                            val geom = root.optJSONObject("geometry") ?: return onDone()
                            val arr = geom.optJSONArray("coordinates") ?: return onDone()
                            val n = min(arr.length(), range.count())
                            for (k in 0 until n) {
                                val c = arr.getJSONArray(k)
                                if (c.length() >= 3) {
                                    val ele = c.optDouble(2, Double.NaN)
                                    if (!ele.isNaN()) {
                                        val idx = range.first + k
                                        if (idx in planPoints.indices) planPoints[idx].altitude =
                                            ele
                                    }
                                }
                            }
                        } catch (_: Throwable) { /* ignore */
                        }
                        onDone()
                    }
                }
            })
        } catch (_: Throwable) {
            onDone()
        }
    }

    private fun clearPlanAltitudes() {
        for (p in planPoints) p.altitude = Double.NaN
    }

    private fun forceRecomputePlanElevation() {
        if (planPoints.size < 2) return
        clearPlanAltitudes()
        Toast.makeText(this, "Recalcul du D+…", Toast.LENGTH_SHORT).show()
        ensurePlanElevationIfMissing(force = true)
    }

    private fun showElevationTuningSheet() {
        val ctx = this
        val dp = resources.displayMetrics.density
        val pad = (16 * dp).toInt()
        val wrap = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        fun showParamHelpDialog(title: String, body: String) {
            AlertDialog.Builder(ctx)
                .setTitle("â„¹ï¸Ž $title")
                .setMessage(body.trim())
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        fun makeNumberField(label: String, def: String, help: String): Pair<TextView, EditText> {
            val tv = TextView(ctx).apply {
                text = label
                setOnClickListener { showParamHelpDialog(label, help) }
                setOnLongClickListener { showParamHelpDialog(label, help); true }
            }
            val et = EditText(ctx).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText(def); setSelection(text.length)
            }
            wrap.addView(tv); wrap.addView(et)
            return tv to et
        }

        val helpStep = """
        Hystérésis verticale (seuil d'inversion).
        Tant que la variation depuis lâ€™extremum nâ€™atteint pas ce seuil, on ignore les yoyos.
        â†‘ plus grand = moins de bruit, mais D+ un peu plus faible sur micro-relief.
        Reco : 1–2 m (baro) • 3–5 m (GPS seul).
        """.trimIndent()

        val helpAlpha = """
        EMA Î± = Exponential Moving Average (coefficient de lissage).
        0.1 = très lissé ; 0.9 = très réactif. Sâ€™applique après le médian.
        """.trimIndent()

        val helpMedian = """
        Fenêtre du filtre médian (impair : 3, 5, 7…).
        Plus grand = plus stable, relief un peu arrondi.
        """.trimIndent()

        val helpSpikeH = "Anti-spike — distance horizontale maximale (m) pour suspecter un spike."
        val helpSpikeJ = "Anti-spike — saut vertical minimal (m) déclenchant lâ€™ignore."
        val helpMinHz = "Seuil horizontal minimal pour comptabiliser la variation."

        val (_, etStep) = makeNumberField(
            "Pas minimal (m) – hystérésis",
            elevMinStepM.toString(),
            helpStep
        )
        val (_, etAlpha) = makeNumberField("EMA Î± (0.1–0.9)", elevAlpha.toString(), helpAlpha)
        val (_, etMedian) = makeNumberField(
            "Fenêtre médiane (impair)",
            elevMedianN.toString(),
            helpMedian
        )
        val (_, etSpikeH) = makeNumberField(
            "Anti-spike: horiz < (m)",
            elevSpikeHorizMaxM.toString(),
            helpSpikeH
        )
        val (_, etSpikeJ) = makeNumberField(
            "Anti-spike: saut > (m)",
            elevSpikeMinJumpM.toString(),
            helpSpikeJ
        )
        val (_, etMinHz) = makeNumberField(
            "Seuil horizontal min (m)",
            elevMinHorizM.toString(),
            helpMinHz
        )

        wrap.addView(TextView(ctx).apply {
            text = "Astuce : touchez un libellé pour lâ€™explication détaillée."
            setPadding(0, (8 * dp).toInt(), 0, 0)
        })

        AlertDialog.Builder(ctx)
            .setTitle("Réglages D+/D– (avancé)")
            .setView(wrap)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val sp = PreferenceManager.getDefaultSharedPreferences(ctx).edit()

                elevMinStepM =
                    etStep.text.toString().replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.5)
                        ?: elevMinStepM
                sp.putString("pref_key_elev_min_step_m", elevMinStepM.toString())

                elevAlpha =
                    etAlpha.text.toString().replace(',', '.').toDoubleOrNull()?.coerceIn(0.1, 0.9)
                        ?: elevAlpha
                sp.putString("pref_key_elev_alpha", elevAlpha.toString())

                elevMedianN = etMedian.text.toString().toIntOrNull()
                    ?.let { if (it < 3) 3 else if (it % 2 == 0) it + 1 else it } ?: elevMedianN
                sp.putString("pref_key_elev_median_n", elevMedianN.toString())

                elevSpikeHorizMaxM =
                    etSpikeH.text.toString().replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.5)
                        ?: elevSpikeHorizMaxM
                sp.putString("pref_key_elev_spike_horiz_max_m", elevSpikeHorizMaxM.toString())

                elevSpikeMinJumpM =
                    etSpikeJ.text.toString().replace(',', '.').toDoubleOrNull()?.coerceAtLeast(1.0)
                        ?: elevSpikeMinJumpM
                sp.putString("pref_key_elev_spike_min_jump_m", elevSpikeMinJumpM.toString())

                elevMinHorizM =
                    etMinHz.text.toString().replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.5)
                        ?: elevMinHorizM
                sp.putString("pref_key_elev_min_horiz_m", elevMinHorizM.toString())

                sp.apply()
                Toast.makeText(ctx, "Réglages D+/D– appliqués", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .setNeutralButton("Aide") { _, _ -> showElevationHelpDialog() }
            .show()

        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        if (!prefs.getBoolean("pref_key_elev_help_shown", false)) {
            showElevationHelpDialog()
            prefs.edit().putBoolean("pref_key_elev_help_shown", true).apply()
        }
    }

    // ===== âœ‚ï¸ Trim dialogs =====

    private fun showTrimPlanDialog() {
        val pts = planPoints
        if (pts.size < 2) {
            Toast.makeText(this, "Aucun plan à réduire.", Toast.LENGTH_SHORT).show(); return
        }

        if (planTrimPreview == null) {
            planTrimPreview = Polyline(map).apply {
                outlinePaint.strokeWidth = 8f
                outlinePaint.color = Color.MAGENTA
            }
            map.overlays.add(planTrimPreview)
        }

        val ctx = this
        val dp = resources.displayMetrics.density
        val wrap = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (12 * dp).toInt(), (20 * dp).toInt(), (8 * dp).toInt())
        }

        val tvInfo = TextView(ctx)
        wrap.addView(tvInfo)

        val slider = RangeSlider(ctx).apply {
            valueFrom = 0f
            valueTo = (pts.size - 1).toFloat()
            stepSize = 1f
            values = listOf(0f, valueTo)
            setLabelFormatter { v -> v.toInt().toString() }
            addOnChangeListener { _, _, _ ->
                val a = values[0].toInt()
                val b = values[1].toInt().coerceAtLeast(a + 1)
                val sub = pts.subList(a, b + 1)
                planTrimPreview?.setPoints(sub)
                val dist = sub.zipWithNext { p, q -> p.distanceToAsDouble(q) }.sum()
                val (g, _) = computeGainLossFiltered(sub)
                val distKm = dist / 1000.0
                tvInfo.text =
                    "Aperçu : %.2f km • D+ %d m (points %d–%d)".format(distKm, g.roundToInt(), a, b)
                map.invalidate()
            }
        }
        wrap.addView(slider)

        val dlg = AlertDialog.Builder(ctx)
            .setTitle("Réduire le plan")
            .setView(wrap)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val a = slider.values[0].toInt()
                val b = slider.values[1].toInt().coerceAtLeast(a + 1)
                val trimmed =
                    pts.subList(a, b + 1).map { GeoPoint(it.latitude, it.longitude, it.altitude) }
                planPoints.clear()
                planPoints.addAll(trimmed)
                planPolyline?.setPoints(planPoints)
                planTrimPreview?.let { map.overlays.remove(it); planTrimPreview = null }
                updatePlannerHud()
                map.invalidate()
                ensurePlanElevationIfMissing()
                showPlannerHud(true)
                zoomToPoints(trimmed)
            }
            .setNegativeButton(R.string.btn_cancel) { _, _ ->
                planTrimPreview?.let { map.overlays.remove(it); planTrimPreview = null }
                map.invalidate()
            }
            .create()

        dlg.setOnShowListener { slider.values = slider.values }
        dlg.show()
    }

    private fun showTrimTrackDialog() {
        val pts = trackPolyline?.actualPoints ?: emptyList()
        if (pts.size < 2) {
            Toast.makeText(this, "Aucune trace à réduire.", Toast.LENGTH_SHORT).show(); return
        }

        var preview: Polyline? = Polyline(map).apply {
            outlinePaint.strokeWidth = 8f
            outlinePaint.color = Color.MAGENTA
        }
        map.overlays.add(preview)

        val ctx = this
        val dp = resources.displayMetrics.density
        val wrap = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (12 * dp).toInt(), (20 * dp).toInt(), (8 * dp).toInt())
        }

        val tvInfo = TextView(ctx)
        wrap.addView(tvInfo)

        val slider = RangeSlider(ctx).apply {
            valueFrom = 0f
            valueTo = (pts.size - 1).toFloat()
            stepSize = 1f
            values = listOf(0f, valueTo)
            setLabelFormatter { v -> v.toInt().toString() }
            addOnChangeListener { _, _, _ ->
                val a = values[0].toInt()
                val b = values[1].toInt().coerceAtLeast(a + 1)
                val sub = pts.subList(a, b + 1)
                preview?.setPoints(sub)
                val dist = sub.zipWithNext { p, q -> p.distanceToAsDouble(q) }.sum()
                val (g, l) = computeGainLossFiltered(sub)
                tvInfo.text = "Aperçu : %.2f km • D+ %d m • D- %d m".format(
                    dist / 1000.0,
                    g.roundToInt(),
                    l.roundToInt()
                )
                map.invalidate()
            }
        }
        wrap.addView(slider)

        val dlg = AlertDialog.Builder(ctx)
            .setTitle("Couper la trace")
            .setView(wrap)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val a = slider.values[0].toInt()
                val b = slider.values[1].toInt().coerceAtLeast(a + 1)
                val trimmed =
                    pts.subList(a, b + 1).map { GeoPoint(it.latitude, it.longitude, it.altitude) }
                trackPolyline?.setPoints(trimmed)
                TrackStore.setAll(trimmed)
                updateStartEndMarkers()
                resetStats()
                computeStatsFrom(trimmed)
                updateStatsUI()
                preview?.let { map.overlays.remove(it) }; preview = null
                map.invalidate()
                Toast.makeText(this, "Trace réduite.", Toast.LENGTH_SHORT).show()
                zoomToPoints(trimmed)
            }
            .setNegativeButton(R.string.btn_cancel) { _, _ ->
                preview?.let { map.overlays.remove(it) }; preview = null
                map.invalidate()
            }
            .create()

        dlg.setOnShowListener { slider.values = slider.values }
        dlg.show()
    }

    // âœ¨ Trim dâ€™une trace â€œoverlayâ€
    private fun showTrimOverlayDialog(t: TrackOverlay, onApplied: (() -> Unit)? = null) {
        val pts = t.polyline.actualPoints ?: emptyList()
        if (pts.size < 2) {
            Toast.makeText(this, "Aucune trace à modifier.", Toast.LENGTH_SHORT).show(); return
        }

        var preview: Polyline? = Polyline(map).apply {
            outlinePaint.strokeWidth = 8f
            outlinePaint.color = Color.MAGENTA
        }
        map.overlays.add(preview)

        val ctx = this
        val dp = resources.displayMetrics.density
        val wrap = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (12 * dp).toInt(), (20 * dp).toInt(), (8 * dp).toInt())
        }

        val tvInfo = TextView(ctx)
        wrap.addView(tvInfo)

        val slider = RangeSlider(ctx).apply {
            valueFrom = 0f
            valueTo = (pts.size - 1).toFloat()
            stepSize = 1f
            values = listOf(0f, valueTo)
            setLabelFormatter { v -> v.toInt().toString() }
            addOnChangeListener { _, _, _ ->
                val a = values[0].toInt()
                val b = values[1].toInt().coerceAtLeast(a + 1)
                val sub = pts.subList(a, b + 1)
                preview?.setPoints(sub)
                val dist = sub.zipWithNext { p, q -> p.distanceToAsDouble(q) }.sum()
                val (g, l) = computeGainLossFiltered(sub)
                tvInfo.text = "Aperçu : %.2f km • D+ %d m • D- %d m".format(
                    dist / 1000.0,
                    g.roundToInt(),
                    l.roundToInt()
                )
                map.invalidate()
            }
        }
        wrap.addView(slider)

        val dlg = AlertDialog.Builder(ctx)
            .setTitle("Modifier la trace")
            .setView(wrap)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val a = slider.values[0].toInt()
                val b = slider.values[1].toInt().coerceAtLeast(a + 1)
                val trimmed =
                    pts.subList(a, b + 1).map { GeoPoint(it.latitude, it.longitude, it.altitude) }
                t.polyline.setPoints(trimmed)
                preview?.let { map.overlays.remove(it) }; preview = null
                map.invalidate()
                zoomToPoints(trimmed)
                onApplied?.invoke()
                Toast.makeText(this, "Trace modifiée.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.btn_cancel) { _, _ ->
                preview?.let { map.overlays.remove(it) }; preview = null
                map.invalidate()
            }
            .create()

        dlg.setOnShowListener { slider.values = slider.values }
        dlg.show()
    }

    private fun zoomToPoints(pts: List<GeoPoint>) {
        if (pts.isEmpty()) return
        val bb = org.osmdroid.util.BoundingBox.fromGeoPointsSafe(pts)
        map.zoomToBoundingBox(bb, true, 100)
    }

    private fun showPlannerHelpDialog() {
        val msg = """
ðŸ§­ Planifier un itinéraire
• Tap sur la carte : ajoute un point (ou prolonge la ligne).
• « Snap to trails (ORS) » : si activé, on colle automatiquement aux sentiers ORS.
• Menu Plan â†’ « Effacer le plan » / « Exporter le plan GPX ».
• Hors-sentier : pendant lâ€™enregistrement, alerte si tu tâ€™éloignes du plan dâ€™au moins le seuil (menu « Seuil hors-sentier… »).
• Astuce : rapproche le zoom pour placer précisément ; désactive le snap si tu veux tracer au cordeau.

Dénivelé du plan
• Calcul D+ robuste (lissage + hystérésis) identique au calcul des traces.
• Les petits yoyos < seuil ne sont pas comptés.
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("Aide — Planification")
            .setMessage(msg)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }


    // --- Handlers pour les boutons de l'onglet Outils (déplacés au niveau de la classe) ---
    fun onWaypointsClick(view: android.view.View) {
        openWaypointListDialog()
    }

    fun onAidePreventionClick(view: android.view.View) {
        val intent = android.content.Intent(this, com.hikemvp.aide.AidePreventionActivity::class.java)
        safeStartActivity(intent)
    }

    // Surcharge pour prise en charge XML : android:onClick="openWaypointListDialog"
    fun openWaypointListDialog(view: android.view.View) {
        openWaypointListDialog()
    }

    private fun openWaypointListDialog() {
        try {
            com.hikemvp.waypoints.WaypointListDialogFragment()
                .show(supportFragmentManager, "WAYPOINT_LIST")
        } catch (t: Throwable) {
            t.printStackTrace()
            android.widget.Toast.makeText(
                this,
                "Impossible dâ€™ouvrir la liste des waypoints",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    // === Waypoint list dialog callbacks ===
    fun getAllWaypoints(): List<com.hikemvp.waypoints.WaypointMeta> {
        return com.hikemvp.waypoints.WaypointStorage.list(this)
    }

    fun openWaypointOnMap(id: Long) {
        try {
            val wp = com.hikemvp.waypoints.WaypointStorage.list(this).firstOrNull { it.id == id } ?: return
            refreshWaypointsOnMap()
            map.controller.setZoom(16.0)
            map.controller.animateTo(org.osmdroid.util.GeoPoint(wp.latitude, wp.longitude))
        } catch (t: Throwable) {
            t.printStackTrace()
            android.widget.Toast.makeText(
                this,
                "Impossible dâ€™ouvrir le waypoint sur la carte",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun shareWaypoint(id: Long) {
        val wp = com.hikemvp.waypoints.WaypointStorage.list(this).firstOrNull { it.id == id } ?: return
        val name = if (wp.name.isNullOrBlank()) getString(R.string.wp_noname) else wp.name
        val text = "${name} — ${wp.latitude}, ${wp.longitude}\nhttps://maps.google.com/?q=${wp.latitude},${wp.longitude}"
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.wp_share))
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        safeStartActivity(android.content.Intent.createChooser(intent, getString(R.string.wp_share)))
    }

    fun deleteWaypoint(id: Long) {
        try {
            com.hikemvp.waypoints.WaypointStorage.delete(this, id)
            refreshWaypointsOnMap()
            android.widget.Toast.makeText(this, getString(R.string.waypoint_deleted), android.widget.Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            t.printStackTrace()
            android.widget.Toast.makeText(this, "Suppression impossible", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // === Nature POI persistence helpers ===
    private fun natureUserFile(): java.io.File = java.io.File(filesDir, "nature_pois_user.json")

    private fun ensureNaturePoiFile() {
        val f = natureUserFile()
        if (!f.exists()) {
            try {
                assets.open("nature_pois.json").use { ins ->
                    f.outputStream().use { os -> ins.copyTo(os) }
                }
            } catch (_: Throwable) {
                // no-op
            }
        }
    }

    private fun saveNaturePois(list: List<NaturePOI>) {
        try {
            val arr = org.json.JSONArray()
            for (p in list) {
                val o = org.json.JSONObject()
                o.put("id", p.id)
                o.put("type", p.type.name)
                o.put("name", p.name)
                o.put("lat", p.lat)
                o.put("lon", p.lon)
                o.put("radiusM", p.radiusM)
                if (p.note != null) o.put("note", p.note)
                arr.put(o)
            }
            natureUserFile().writeText(arr.toString(2), Charsets.UTF_8)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun addNaturePoi(p: NaturePOI) {
        naturePois.add(p)
        saveNaturePois(naturePois)
        setNatureMarkersVisible(false)
        setNatureMarkersVisible(true)
    }


    // === STYLE PROTECTED POLYGON ===




    // === PARSE GEOJSON POLYGONS ===


    // === LOAD & REFRESH ===



    /** Retire de la carte les overlays issus des derniers imports (KML/CSV) */
    private fun clearImportedOverlays() {
        if (importedOverlays.isEmpty()) {
            android.widget.Toast.makeText(this, "Aucun import à effacer.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        importedOverlays.forEach { map.overlays.remove(it) }
        importedOverlays.clear()
        map.invalidate()
        android.widget.Toast.makeText(this, "Imports effacés.", android.widget.Toast.LENGTH_SHORT).show()
    }


    private fun openGroup(): Boolean {
        return try {
            startActivity(Intent(this, com.hikemvp.group.GroupActivity::class.java))
            true
        } catch (t: Throwable) {
            Log.e("GroupLaunch", "Impossible d’ouvrir GroupActivity", t)
            android.widget.Toast.makeText(this, "Impossible d’ouvrir Groupe : " + t.javaClass.simpleName, android.widget.Toast.LENGTH_LONG).show()
            true
        }
    }


    private fun applyHudWeather(enabled: Boolean) {
        // Try to toggle tvWeather if present; otherwise ignore
        try {
            findViewById<android.view.View?>(R.id.tvWeather)?.isVisible = enabled
        } catch (_: Throwable) { }
    }


    private fun applyAutoFollow(enabled: Boolean) {
        autoFollowEnabled = enabled
        try {
            // If you have a MyLocation overlay, reflect state
            val field = this::class.java.declaredFields.firstOrNull { it.name == "myLocation" }
            field?.isAccessible = true
            val overlay = field?.get(this)
            val m = overlay?.javaClass?.methods
            m?.firstOrNull { it.name == "enableFollowLocation" }?.takeIf { enabled }?.invoke(overlay)
            m?.firstOrNull { it.name == "disableFollowLocation" }?.takeIf { !enabled }?.invoke(overlay)
        } catch (_: Throwable) { }
    }



    
// [removed duplicate onPause]

    
}