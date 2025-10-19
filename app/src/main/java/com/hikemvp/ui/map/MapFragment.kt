package com.hikemvp.ui.map

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hikemvp.Constants
import com.hikemvp.R
import com.hikemvp.TrackRecordingService
import com.hikemvp.planning.TrackAnalysis
import com.hikemvp.settings.SettingsActivity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.osmdroid.util.GeoPoint
import com.hikemvp.maps.MapDownloadDialogFragment
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.config.Configuration
import java.io.File
import com.hikemvp.ui.map.formatDistance
import com.hikemvp.ui.map.formatElevation
import com.hikemvp.ui.map.formatDuration
import com.hikemvp.ui.map.computeStats
import com.hikemvp.ui.map.MapStatsUtils.computeStats

class MapFragment : Fragment(R.layout.fragment_map) {

    // UI menu
    private var recordItem: MenuItem? = null
    private var centerItem: MenuItem? = null

    // States
    private var isRecording = false
    private var isPaused = false
    private var followMode = true

    // Stats enregistrement
    private data class TrackStats(
        var distanceM: Float = 0f,
        var gainM: Float = 0f,
        var lossM: Float = 0f,
        var startMs: Long = 0L,
        var pauseStartedMs: Long = 0L,
        var pausedTotalMs: Long = 0L,
        var lastLoc: Location? = null,
        var lastFixTime: Long = 0L,
        var lastSpeedKmh: Float = 0f
    )
    private var stats = TrackStats()

    // HUD REC (panneau du bas)
    private var tvDist: TextView? = null
    private var tvGain: TextView? = null
    private var tvLoss: TextView? = null
    private var tvSpeed: TextView? = null
    private var tvTime: TextView? = null

    // HUD PLANIF (cartouche en haut à droite)
    private var planCard: View? = null
    private var tvPlannedDist: TextView? = null
    private var tvPlannedGain: TextView? = null
    private var tvPlannedLoss: TextView? = null
    private var tvPlannedEta:  TextView? = null

    // Données de planification (polyline prévue)
    private val plannedPoints = mutableListOf<GeoPoint>()

    // Ticker 1s pour le temps enregistré
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            if (isRecording) {
                updateHudTime()
                handler.postDelayed(this, 1000L)
            }
        }
    }

    // Location (pour recentrer à la demande)
    private var locationManager: LocationManager? = null

    // Helper : en “mode planif” si on a des points planifiés et qu’on n’enregistre pas
    private fun isPlanningMode(): Boolean = plannedPoints.isNotEmpty() && !isRecording

    // Service -> UI
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Constants.ACTION_RECORDING_STATUS -> {
                    val wasRecording = isRecording
                    val wasPaused = isPaused
                    isRecording = intent.getBooleanExtra(Constants.EXTRA_ACTIVE, false)
                    isPaused = intent.getBooleanExtra(Constants.EXTRA_IS_PAUSED, false)

                    // gestion temps de pause
                    if (wasRecording && !wasPaused && isPaused) {
                        stats.pauseStartedMs = System.currentTimeMillis()
                    } else if (wasRecording && wasPaused && !isPaused) {
                        if (stats.pauseStartedMs > 0) {
                            stats.pausedTotalMs += (System.currentTimeMillis() - stats.pauseStartedMs)
                            stats.pauseStartedMs = 0L
                        }
                    }

                    refreshRecordIcon()
                    updateTicker()
                    updateHud() // bascule REC/PLANIF si besoin
                }
                Constants.BROADCAST_TRACK_POINT -> {
                    val lat = intent.getDoubleExtra(Constants.EXTRA_LAT, Double.NaN)
                    val lon = intent.getDoubleExtra(Constants.EXTRA_LON, Double.NaN)
                    val alt = if (intent.hasExtra(Constants.EXTRA_ALT))
                        intent.getDoubleExtra(Constants.EXTRA_ALT, Double.NaN) else Double.NaN
                    if (!lat.isNaN() && !lon.isNaN()) {
                        val loc = Location("trk").apply {
                            latitude = lat; longitude = lon
                            if (!alt.isNaN()) altitude = alt
                            time = System.currentTimeMillis()
                        }
                        onNewTrackPoint(loc)
                    }
                }
                Constants.BROADCAST_TRACK_RESET -> {
                    stats = TrackStats()
                    updateHud()
                }
            }
        }
    }

    private val requestLocationPerms =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarse = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (fine || coarse) centerOnUser()
            else Toast.makeText(requireContext(), "Permission localisation refusée", Toast.LENGTH_SHORT).show()
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- IMPORTANT : initialiser osmdroid (cache interne + user agent) ---
        initOsmdroidStorage()

        // Toolbar sans titre au centre
        view.findViewById<MaterialToolbar>(R.id.toolbar)?.apply { title = "" }

        // HUD REC refs
        tvDist = view.findViewById(R.id.hud_distance)
        tvGain = view.findViewById(R.id.hud_gain)
        tvLoss = view.findViewById(R.id.hud_loss)
        tvSpeed = view.findViewById(R.id.hud_speed)
        tvTime = view.findViewById(R.id.hud_time)

        // HUD PLANIF refs
        planCard       = view.findViewById(R.id.plan_card)
        tvPlannedDist  = view.findViewById(R.id.tvPlannedDist)
        tvPlannedGain  = view.findViewById(R.id.tvPlannedGain)
        tvPlannedLoss  = view.findViewById(R.id.tvPlannedLoss)
        tvPlannedEta   = view.findViewById(R.id.tvPlannedEta)

        // Menu
        (requireActivity() as MenuHost).addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.clear()
                menuInflater.inflate(R.menu.menu_map, menu)
                centerItem = menu.findItem(R.id.action_center_me).apply {
                    isCheckable = true
                    isChecked = followMode
                }
                recordItem = menu.findItem(R.id.action_record)
                refreshRecordIcon()
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean {
                return when (item.itemId) {
                    R.id.action_center_me -> { onCenterClicked(); true }
                    R.id.action_record    -> { onRecordClicked(); true }
                    R.id.action_download_tiles -> { onDownloadTilesClicked(); true }
                    R.id.action_settings  -> { startActivity(Intent(requireContext(), SettingsActivity::class.java)); true }
                    R.id.action_info_hub  -> { startActivity(Intent(requireContext(), com.hikemvp.info.InfoHubActivity::class.java)); true }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // init HUDs
        updateHud()
        updatePlannedHud()
    }

    override fun onStart() {
        super.onStart()
        val f = IntentFilter().apply {
            addAction(Constants.ACTION_RECORDING_STATUS)
            addAction(Constants.BROADCAST_TRACK_POINT)
            addAction(Constants.BROADCAST_TRACK_RESET)
        }
        ContextCompat.registerReceiver(requireContext(), statusReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        requireContext().unregisterReceiver(statusReceiver)
        super.onStop()
    }

    // ----- Actions -----

    private fun onCenterClicked() {
        if (!isRecording) { centerOnUser(); return }
        followMode = !followMode
        centerItem?.isChecked = followMode
        Toast.makeText(requireContext(), if (followMode) "Suivi activé" else "Suivi désactivé", Toast.LENGTH_SHORT).show()
    }

    private fun onRecordClicked() {
        if (!isRecording) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.menu_record))
                .setMessage("Démarrer l’enregistrement ?")
                .setPositiveButton("Démarrer") { _, _ ->
                    isRecording = true; isPaused = false; followMode = true
                    centerItem?.isChecked = true
                    stats = TrackStats(startMs = System.currentTimeMillis())
                    refreshRecordIcon()
                    updateTicker()
                    startService(Constants.ACTION_START_RECORD)
                    Toast.makeText(requireContext(), getString(R.string.msg_rec_started), Toast.LENGTH_SHORT).show()
                    updateHud() // bascule HUD en mode live
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
            return
        }

        if (!isPaused) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Enregistrement en cours")
                .setMessage("Que voulez-vous faire ?")
                .setPositiveButton("Mettre en pause") { _, _ ->
                    isPaused = true; stats.pauseStartedMs = System.currentTimeMillis()
                    refreshRecordIcon(); updateTicker()
                    startService(Constants.ACTION_PAUSE_RECORD)
                }
                .setNeutralButton("Arrêter") { _, _ ->
                    isRecording = false; isPaused = false
                    refreshRecordIcon(); updateTicker()
                    startService(Constants.ACTION_STOP_RECORD)
                    updateHud() // si on a une planif, le bas repasse sur la planif
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        } else {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("En pause")
                .setMessage("Que voulez-vous faire ?")
                .setPositiveButton("Reprendre") { _, _ ->
                    if (stats.pauseStartedMs > 0) {
                        stats.pausedTotalMs += (System.currentTimeMillis() - stats.pauseStartedMs)
                        stats.pauseStartedMs = 0L
                    }
                    isPaused = false; refreshRecordIcon(); updateTicker()
                    startService(Constants.ACTION_RESUME_RECORD)
                }
                .setNeutralButton("Arrêter") { _, _ ->
                    isRecording = false; isPaused = false
                    refreshRecordIcon(); updateTicker()
                    startService(Constants.ACTION_STOP_RECORD)
                    updateHud()
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }
    }

    private fun refreshRecordIcon() {
        val item = recordItem ?: return
        when {
            !isRecording -> { item.setIcon(R.drawable.ic_record_24); item.title = getString(R.string.menu_record) }
            isPaused     -> { item.setIcon(R.drawable.ic_record_24); item.title = "Reprendre" }
            else         -> { item.setIcon(R.drawable.ic_pause_24);  item.title = "Pause" }
        }
    }

    private fun updateTicker() {
        handler.removeCallbacks(ticker)
        if (isRecording && !isPaused) handler.post(ticker)
    }

    // ----- Localisation / centrage -----

    private fun hasAnyLocationPermission(): Boolean {
        val ctx = requireContext()
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun centerOnUser() {
        val ctx = requireContext()
        if (!hasAnyLocationPermission()) {
            requestLocationPerms.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
            return
        }

        val lm = locationManager ?: return
        try {
            val last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val zoom = prefs.getInt(getString(R.string.pref_key_map_default_zoom), 15)

            if (last != null) {
                // TODO: centrer/zoomer réellement sur la carte (zoom ci-dessus)
                Toast.makeText(ctx, "Position : ${last.latitude}, ${last.longitude} (zoom $zoom)", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(ctx, "Position indisponible pour le moment", Toast.LENGTH_SHORT).show()
            }
        } catch (_: SecurityException) {
            Toast.makeText(ctx, "Permission localisation manquante", Toast.LENGTH_SHORT).show()
        }
    }

    // ----- Réception des points enregistrés & stats -----

    private fun onNewTrackPoint(loc: Location) {
        if (!isRecording || isPaused) return

        val prev = stats.lastLoc
        if (prev != null) {
            val d = prev.distanceTo(loc) // m
            stats.distanceM += d

            // D+/D- (seuil 1 m)
            if (prev.hasAltitude() && loc.hasAltitude()) {
                val delta = (loc.altitude - prev.altitude).toFloat()
                if (abs(delta) >= 1f) {
                    if (delta > 0) stats.gainM += delta else stats.lossM += abs(delta)
                }
            }

            // Vitesse instantanée lissée simple sur dernier segment
            val dt = (loc.time - prev.time).coerceAtLeast(1L) / 1000f // s
            val instMs = d / dt // m/s
            val instKmh = (instMs * 3.6f)
            stats.lastSpeedKmh = if (stats.lastSpeedKmh == 0f) instKmh else (stats.lastSpeedKmh * 0.6f + instKmh * 0.4f)

            if (followMode) {
                // TODO: recenter map sur 'loc'
            }
        } else {
            stats.startMs = max(stats.startMs, System.currentTimeMillis())
        }

        stats.lastLoc = loc
        stats.lastFixTime = System.currentTimeMillis()
        updateHud()
    }

    // ----- HUD ENREGISTREMENT / PLANIF -----

    private fun updateHud() {
        if (isPlanningMode()) {
            // Affiche la planif dans le panneau du bas
            val st  = TrackAnalysis.computeStats(plannedPoints)
            val eta = TrackAnalysis.estimateDurationMillis(st)

            tvDist?.text = formatDistance(st.distanceM / 1000.0)
            tvGain?.text = "+${formatElevation(st.gainM)}"
            tvLoss?.text = "-${formatElevation(st.lossM)}"
            tvSpeed?.text = "—"
            tvTime?.text  = formatDuration(eta)
            return
        }

        // Mode enregistrement (inchangé)
        val km = stats.distanceM / 1000f
        tvDist?.text = String.format("%.2f km", km)
        tvGain?.text = String.format("%.0f m", stats.gainM)
        tvLoss?.text = String.format("%.0f m", stats.lossM)
        tvSpeed?.text = String.format("%.1f km/h", stats.lastSpeedKmh.coerceAtLeast(0f))
        updateHudTime()
    }

    private fun updateHudTime() {
        if (!isRecording) {
            // en planif on laisse ETA pris en charge par updateHud()
            if (!isPlanningMode()) tvTime?.text = "00:00:00"
            return
        }
        val now = System.currentTimeMillis()
        val pausedMs = stats.pausedTotalMs + if (isPaused && stats.pauseStartedMs > 0) (now - stats.pauseStartedMs) else 0L
        val elapsed = (now - stats.startMs - pausedMs).coerceAtLeast(0L)
        val h = elapsed / 3_600_000
        val m = (elapsed % 3_600_000) / 60_000
        val s = (elapsed % 60_000) / 1000
        tvTime?.text = String.format("%02d:%02d:%02d", h, m, s)
    }

    // ----- HUD PLANIFICATION (cartouche) -----

    fun setPlannedTrack(points: List<GeoPoint>) {
        plannedPoints.clear()
        plannedPoints.addAll(points)
        updatePlannedHud() // cartouche en haut à droite
        updateHud()        // met aussi à jour le petit panneau du bas (Dist / D+ / D- / ETA)
    }

    fun clearPlannedTrack() {
        plannedPoints.clear()
        updatePlannedHud()
        updateHud()
    }

    private fun updatePlannedHud() {
        // visuel de la carte “Plan”
        planCard?.visibility = if (plannedPoints.isEmpty()) View.GONE else View.VISIBLE

        if (tvPlannedDist == null) return
        if (plannedPoints.isEmpty()) {
            tvPlannedDist?.text = "0.00 km"
            tvPlannedGain?.text = "+0 m"
            tvPlannedLoss?.text = "-0 m"
            tvPlannedEta?.text  = "00h00"
            return
        }

        val st  = TrackAnalysis.computeStats(plannedPoints)
        val eta = TrackAnalysis.estimateDurationMillis(st)

        tvPlannedDist?.text = formatDistance(st.distanceM / 1000.0) // km
        tvPlannedGain?.text = "+${formatElevation(st.gainM)}"
        tvPlannedLoss?.text = "-${formatElevation(st.lossM)}"
        tvPlannedEta?.text  = formatDuration(eta)
    }

    // ----- Téléchargement de cartes (offline) -----

    private fun onDownloadTilesClicked() {
        val map = obtainMapViewOrNull()
        if (map == null) {
            Toast.makeText(requireContext(), "Carte indisponible", Toast.LENGTH_SHORT).show()
            return
        }

        MapDownloadDialogFragment { zMinIn: Int, zMaxIn: Int ->
            // Sécurité: clamp et ordre des zooms
            val minZ = min(max(0, zMinIn), 22)
            val maxZ = min(max(0, zMaxIn), 22)
            val z0 = min(minZ, maxZ)
            val z1 = max(minZ, maxZ)

            val bbox: BoundingBox = if (plannedPoints.isNotEmpty()) {
                // Autour de la trace planifiée + petit buffer ~1km (≈0.01°)
                val bb = BoundingBox.fromGeoPoints(plannedPoints)
                BoundingBox(
                    (bb.latNorth + 0.01).coerceIn(-90.0, 90.0),
                    (bb.lonEast  + 0.01).coerceIn(-180.0, 180.0),
                    (bb.latSouth - 0.01).coerceIn(-90.0, 90.0),
                    (bb.lonWest  - 0.01).coerceIn(-180.0, 180.0)
                )
            } else {
                map.boundingBox
            }

            try {
                downloadTiles(
                    mapView = map,
                    bbox    = bbox,
                    zoomMin = z0,
                    zoomMax = z1,
                    onProgress = { _, _ -> },
                    onFinish = {
                        Toast.makeText(requireContext(), "Cartes prêtes hors-ligne", Toast.LENGTH_SHORT).show()
                    },
                    onError = {
                        Toast.makeText(requireContext(), "Erreur téléchargement", Toast.LENGTH_SHORT).show()
                    }
                )
            } catch (t: Throwable) {
                Toast.makeText(requireContext(), "Téléchargement impossible: ${t.message ?: "inconnu"}", Toast.LENGTH_LONG).show()
            }
        }.show(childFragmentManager, "download_tiles")
    }

    /**
     * Récupère ton MapView sans imposer d’id fixe :
     * - Cherche récursivement un MapView à l’intérieur de R.id.map_container.
     * - Si tu as une référence directe ailleurs, utilise-la ici.
     */
    private fun obtainMapViewOrNull(): MapView? {
        val root = view ?: return null
        val container = root.findViewById<View>(R.id.map_container) ?: root
        return findMapViewRecursive(container)
    }

    private fun findMapViewRecursive(v: View): MapView? {
        if (v is MapView) return v
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                val found = findMapViewRecursive(v.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    // ----- Service helper -----

    private fun startService(action: String) {
        val ctx = requireContext().applicationContext
        val intent = Intent(ctx, TrackRecordingService::class.java).apply { this.action = action }
        if (action == Constants.ACTION_START_RECORD) {
            ContextCompat.startForegroundService(ctx, intent)
        } else {
            ctx.startService(intent)
        }
    }

    override fun onDestroyView() {
        handler.removeCallbacks(ticker)
        super.onDestroyView()
    }

    // ====== OSMDROID: init stockage interne ======
    private fun initOsmdroidStorage() {
        val appCtx = requireContext().applicationContext
        val cfg = Configuration.getInstance()
        if (cfg.userAgentValue.isNullOrBlank()) {
            cfg.userAgentValue = appCtx.packageName
        }
        // Force le cache interne pour éviter toute permission stockage
        val base = File(appCtx.cacheDir, "osmdroid")
        val tiles = File(base, "tiles")
        if (!base.exists()) base.mkdirs()
        if (!tiles.exists()) tiles.mkdirs()
        cfg.osmdroidBasePath = base
        cfg.osmdroidTileCache = tiles
    }

    // ====== Téléchargement de tuiles hors-ligne ======
    private fun downloadTiles(
        mapView: MapView,
        bbox: BoundingBox,
        zoomMin: Int,
        zoomMax: Int,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        onFinish: () -> Unit = {},
        onError: () -> Unit = {}
    ) {
        val cm = CacheManager(mapView)
        cm.downloadAreaAsync(
            requireContext(),
            bbox,
            zoomMin,
            zoomMax,
            object : CacheManager.CacheManagerCallback {
                override fun downloadStarted() {}

                // certaines versions d’osmdroid la demandent
                override fun setPossibleTilesInArea(p0: Int) { /* no-op */ }

                override fun updateProgress(
                    progress: Int,
                    currentZoomLevel: Int,
                    zoomMin: Int,
                    zoomMax: Int
                ) {
                    onProgress(progress.coerceIn(0, 100), currentZoomLevel)
                }

                override fun onTaskComplete() = onFinish()
                override fun onTaskFailed(errors: Int) = onError()
            }
        )
    }
}