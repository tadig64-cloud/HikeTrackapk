package com.hikemvp

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.hikemvp.gpx.GpxStorage
import com.hikemvp.storage.ActiveTrackRepo
import org.osmdroid.util.GeoPoint
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import android.media.MediaScannerConnection

/**
 * Enregistrement robuste & précis :
 * - Gate sur les 2 premiers fixes (anti ligne droite de départ)
 * - Rejet des fixes trop anciens / trop imprécis
 * - Enregistre uniquement les points GPS (pas de NETWORK)
 * - GPX écrit en direct dans le dossier lu par “Fichiers GPX”
 * - Service FGS + WakeLock
 * - Reprise via ActiveTrackRepo
 * - Anti-saut après LONG_GAP_MS pour éviter la grande diagonale au réveil
 */
class TrackRecordingService : Service(), LocationListener {

    private val TAG = "TrackRec"

    private var isRecording = false
    private var isPaused = false

    private var totalDistanceM = 0.0
    private var lastLocation: Location? = null
    private var pendingFirstFix: Location? = null

    // --- Anti-saut après longue pause ---
    private val LONG_GAP_MS = 120_000L           // ≥ 2 min sans point = on "recalle" la base
    private var pendingGapFix: Location? = null  // premier fix reçu après un long gap (non diffusé)

    private lateinit var lm: LocationManager
    private var wakeLock: PowerManager.WakeLock? = null

    private val maxAcceptableAccuracyM = 50.0
    private val minDistanceMetersWhenMoving = 5.0
    private val MAX_FIRST_FIX_AGE_MS = 20_000L
    private val MAX_START_SPEED_MS = 6.0

    private enum class Tier { ACTIVE, SLOW, IDLE }
    private var currentTier: Tier = Tier.ACTIVE
    private var lastMoveTs: Long = 0L

    private val TIER_ACTIVE_MIN_TIME_MS get() = Constants.TIER_ACTIVE_MIN_TIME_MS
    private val TIER_ACTIVE_MIN_DIST_M  get() = Constants.TIER_ACTIVE_MIN_DIST_M
    private val TIER_SLOW_MIN_TIME_MS   get() = Constants.TIER_SLOW_MIN_TIME_MS
    private val TIER_SLOW_MIN_DIST_M    get() = Constants.TIER_SLOW_MIN_DIST_M
    private val TIER_IDLE_MIN_TIME_MS   get() = Constants.TIER_IDLE_MIN_TIME_MS
    private val TIER_IDLE_MIN_DIST_M    get() = Constants.TIER_IDLE_MIN_DIST_M
    private val SPEED_ACTIVE_MS         get() = Constants.SPEED_ACTIVE_MS
    private val IDLE_WINDOW_MS          get() = Constants.IDLE_WINDOW_MS

    private val batterySaverEnabled: Boolean
        get() = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(Constants.PREF_BATTERY_SAVER, true)

    // GPX live (dossier GpxStorage)
    private var gpxWriter: BufferedWriter? = null
    private var gpxFile: File? = null
    private val isoFmtUTC = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotifChannelIfNeeded()
        if (RecordingPrefs.isActive(this) && !isRecording) {
            startRecording(resume = true)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_START_RECORD  -> startRecording(resume = false)
            Constants.ACTION_STOP_RECORD   -> stopRecording()
            Constants.ACTION_PAUSE_RECORD  -> pauseRecording()
            Constants.ACTION_RESUME_RECORD -> resumeRecording()
            else -> {
                if (RecordingPrefs.isActive(this) && !isRecording) {
                    startRecording(resume = true)
                } else if (isRecording) {
                    updateForegroundNotification()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        closeGpx()
        releaseWakeLock()
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
    }

    // ===== API =====

    @SuppressLint("MissingPermission")
    private fun startRecording(resume: Boolean) {
        if (isRecording) return
        isRecording = true
        isPaused = false
        pendingFirstFix = null
        pendingGapFix = null
        lastLocation = null

        acquireWakeLock()
        startForeground(Constants.NOTIF_ID_RECORDING, buildNotification())

        if (!resume) {
            ActiveTrackRepo.clear(this)
            totalDistanceM = 0.0
            openGpx()
        } else {
            totalDistanceM = ActiveTrackRepo.computeTotalDistanceM(this)
            val last = ActiveTrackRepo.readLastPoint(this)
            lastLocation = last?.toLocation()
            openGpx()
        }

        if (hasLocationPermission()) {
            requestAdaptiveUpdates()
            lastLocation?.let { sendPointBroadcast(it) } ?: run {
                lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { sendPointBroadcast(it) }
                    ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let { sendPointBroadcast(it) }
            }
        }

        RecordingPrefs.set(this, active = true, paused = false)
        broadcastStatus()
    }

    private fun stopRecording() {
        if (!isRecording) { stopSelf(); return }
        isRecording = false
        isPaused = false

        stopLocationUpdates()
        closeGpx()

        releaseWakeLock()
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)

        runCatching { ActiveTrackRepo.clear(this) }

        broadcastReset()
        RecordingPrefs.set(this, active = false, paused = false)
        broadcastStatus()
        stopSelf()
    }

    private fun pauseRecording() {
        if (!isRecording || isPaused) return
        isPaused = true
        stopLocationUpdates()
        updateForegroundNotification()
        RecordingPrefs.set(this, active = true, paused = true)
        broadcastStatus()
    }

    private fun resumeRecording() {
        if (!isRecording || !isPaused) return
        isPaused = false
        requestAdaptiveUpdates()
        updateForegroundNotification()
        RecordingPrefs.set(this, active = true, paused = false)
        broadcastStatus()
    }

    private fun hasLocationPermission(): Boolean {
        val fine = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarse = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    // ===== WakeLock =====
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HikeMVP:TrackWakelock").apply {
            setReferenceCounted(false)
            acquire()
        }
    }
    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    // ===== LocationListener =====
    override fun onLocationChanged(locIn: Location) {
        if (!isRecording || isPaused) return
        if (locIn.provider == LocationManager.NETWORK_PROVIDER) return

        val loc = Location(locIn)

        // 1) Filtres de qualité et d'horodatage
        if (loc.hasAccuracy() && loc.accuracy > maxAcceptableAccuracyM) return

        val now = System.currentTimeMillis()
        val locTime = if (loc.time > 0) loc.time else now
        if (lastLocation == null && now - locTime > MAX_FIRST_FIX_AGE_MS) {
            Log.w(TAG, "Fix ignoré (trop ancien au démarrage)")
            return
        }
        loc.time = locTime

        // 2) Gate de départ (tes 2 premiers fixes)
        if (lastLocation == null) {
            val pending = pendingFirstFix
            if (pending == null) {
                pendingFirstFix = Location(loc)
                Log.i(TAG, "Premier fix en attente de validation…")
                return
            } else {
                val dt = max(1L, loc.time - pending.time) / 1000.0
                val dist = pending.distanceTo(loc).toDouble()
                val speedMs = if (dt > 0) dist / dt else 0.0
                val ok = (dist <= 50.0) || (speedMs <= MAX_START_SPEED_MS)
                if (!ok) {
                    Log.w(TAG, "Fix initial rejeté (dist=${"%.1f".format(dist)}m, v=${"%.1f".format(speedMs)}m/s)")
                    pendingFirstFix = Location(loc)
                    return
                }
                acceptPoint(pending)
                acceptPoint(loc)
                pendingFirstFix = null
                return
            }
        }

        // 3) Anti-trait-droit après une longue pause (pas de segment jusqu'au prochain vrai fix)
        val prev = lastLocation!!
        val gapMs = loc.time - prev.time
        if (gapMs >= LONG_GAP_MS) {
            if (pendingGapFix == null) {
                // On "recale" la base: on remplace lastLocation par ce fix,
                // mais on n'ajoute rien (pas de distance, pas de broadcast, pas de GPX)
                pendingGapFix = Location(loc)
                lastLocation = Location(loc)
                Log.i(TAG, "Long gap détecté (${gapMs}ms) → baseline réinitialisée, attente du prochain fix…")
                return
            } else {
                // Deuxième fix après le gap : on reprend le flux normal
                pendingGapFix = null
                // (fall-through vers acceptPoint plus bas)
            }
        }

        // 4) Filtre distance mini quand on bouge
        val d = prev.distanceTo(loc).toDouble()
        if (d < minDistanceMetersWhenMoving) {
            maybeAdjustTier(loc, prev)
            return
        }

        // 5) Acceptation normale
        acceptPoint(loc)
    }

    private fun acceptPoint(loc: Location) {
        val prev = lastLocation
        if (prev != null) {
            totalDistanceM += max(0.0, prev.distanceTo(loc).toDouble())
        }
        lastLocation = Location(loc)

        ActiveTrackRepo.append(
            this,
            lat = loc.latitude,
            lon = loc.longitude,
            alt = if (loc.hasAltitude()) loc.altitude else null,
            timeMs = loc.time
        )

        appendGpxPoint(loc)

        sendPointBroadcast(loc)
        maybeAdjustTier(loc, prev)
        updateForegroundNotification()
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    @Deprecated("Deprecated by Android Location APIs")
    @Suppress("DEPRECATION")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}

    private fun stopLocationUpdates() = runCatching { lm.removeUpdates(this) }.let {}

    // ===== GPS adaptatif =====
    private fun requestAdaptiveUpdates() {
        if (!hasLocationPermission()) return
        if (!batterySaverEnabled) { requestUpdatesFor(Tier.ACTIVE); return }
        requestUpdatesFor(currentTier)
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdatesFor(tier: Tier) {
        if (!hasLocationPermission()) return
        currentTier = tier
        val (minTime, minDist) = when (tier) {
            Tier.ACTIVE -> TIER_ACTIVE_MIN_TIME_MS to TIER_ACTIVE_MIN_DIST_M
            Tier.SLOW   -> TIER_SLOW_MIN_TIME_MS   to TIER_SLOW_MIN_DIST_M
            Tier.IDLE   -> TIER_IDLE_MIN_TIME_MS   to TIER_IDLE_MIN_DIST_M
        }
        stopLocationUpdates()
        runCatching {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTime, minDist, this)
            }
        }
    }

    private fun maybeAdjustTier(newLoc: Location, prev: Location?) {
        if (!batterySaverEnabled) {
            if (currentTier != Tier.ACTIVE) requestUpdatesFor(Tier.ACTIVE)
            return
        }
        val speedMs: Double = when {
            newLoc.hasSpeed() -> newLoc.speed.toDouble()
            prev != null -> {
                val dt = max(1L, newLoc.time - prev.time) / 1000.0
                if (dt > 0) prev.distanceTo(newLoc) / dt else 0.0
            }
            else -> 0.0
        }
        val moved = if (prev != null) prev.distanceTo(newLoc).toDouble() else 0.0
        val moving = speedMs >= SPEED_ACTIVE_MS || moved >= TIER_SLOW_MIN_DIST_M
        val now = System.currentTimeMillis()
        if (moving) lastMoveTs = now
        val target = when {
            moving -> Tier.ACTIVE
            now - lastMoveTs > IDLE_WINDOW_MS -> Tier.IDLE
            else -> Tier.SLOW
        }
        if (target != currentTier) requestUpdatesFor(target)
    }

    // ===== Broadcasts UI =====
    private fun sendPointBroadcast(loc: Location) {
        val it = Intent(Constants.BROADCAST_TRACK_POINT).apply {
            putExtra(Constants.EXTRA_LAT, loc.latitude)
            putExtra(Constants.EXTRA_LON, loc.longitude)
            if (loc.hasAltitude()) putExtra(Constants.EXTRA_ALT, loc.altitude)
        }
        sendBroadcast(it)
    }
    private fun broadcastReset() = sendBroadcast(Intent(Constants.BROADCAST_TRACK_RESET))
    private fun broadcastStatus() {
        val it = Intent(Constants.ACTION_RECORDING_STATUS).apply {
            putExtra(Constants.EXTRA_ACTIVE, isRecording)
            putExtra(Constants.EXTRA_IS_PAUSED, isPaused)
        }
        sendBroadcast(it)
    }

    // ===== Notification =====
    private fun createNotifChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val ch = android.app.NotificationChannel(
            Constants.CHANNEL_ID_RECORDING,
            getString(R.string.notif_channel_rec),
            android.app.NotificationManager.IMPORTANCE_LOW
        )
        ch.setShowBadge(false)
        ch.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        nm.createNotificationChannel(ch)
    }

    private fun pendingSelf(action: String, reqCode: Int): PendingIntent {
        val i = Intent(this, TrackRecordingService::class.java).setAction(action)
        val flags = if (Build.VERSION.SDK_INT >= 23)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getService(this, reqCode, i, flags)
    }

    private fun buildNotification(): Notification {
        val text = if (!isRecording) "" else if (isPaused)
            getString(R.string.notif_recording_text_paused, totalDistanceM / 1000.0)
        else
            getString(R.string.notif_recording_text_active, totalDistanceM / 1000.0)

        val title = if (isPaused)
            getString(R.string.notif_recording_title_paused)
        else
            getString(R.string.notif_recording_title_active)

        
val contentIntent = run {
    val i = Intent(this, MapActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    val flags = if (Build.VERSION.SDK_INT >= 23)
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    else PendingIntent.FLAG_UPDATE_CURRENT
    PendingIntent.getActivity(this, 10, i, flags)
}

        val builder = NotificationCompat.Builder(this, Constants.CHANNEL_ID_RECORDING)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(isRecording)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        builder.addAction(
            NotificationCompat.Action(
                0, getString(R.string.btn_stop),
                pendingSelf(Constants.ACTION_STOP_RECORD, 1003)
            )
        )

        if (isRecording && !isPaused) {
            builder.addAction(
                NotificationCompat.Action(
                    0, getString(R.string.btn_pause),
                    pendingSelf(Constants.ACTION_PAUSE_RECORD, 1001)
                )
            )
        } else if (isRecording && isPaused) {
            builder.addAction(
                NotificationCompat.Action(
                    0, getString(R.string.btn_resume),
                    pendingSelf(Constants.ACTION_RESUME_RECORD, 1002)
                )
            )
        }

        return builder.build()
    }

    private fun updateForegroundNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(Constants.NOTIF_ID_RECORDING, buildNotification())
    }

    // ===== GPX live =====
    private fun openGpx() {
        runCatching {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date())
            gpxFile = File(GpxStorage.gpxDir(this), "track_$ts.gpx")
            gpxWriter = gpxFile!!.bufferedWriter().apply {
                appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
                appendLine("""<gpx version="1.1" creator="HikeTrack" xmlns="http://www.topografix.com/GPX/1/1">""")
                appendLine("<trk><name>HikeTrack $ts</name><trkseg>")
                flush()
            }
            Log.i(TAG, "GPX ouvert -> ${gpxFile?.absolutePath}")
        }.onFailure { Log.e(TAG, "openGpx FAILED", it) }
    }

    private fun appendGpxPoint(loc: Location) {
        val w = gpxWriter ?: return
        runCatching {
            val t = isoFmtUTC.format(java.util.Date(loc.time))
            w.append("""<trkpt lat="${loc.latitude}" lon="${loc.longitude}">""")
            if (loc.hasAltitude()) w.append("<ele>${loc.altitude}</ele>")
            w.append("<time>$t</time></trkpt>\n")
            w.flush()
        }.onFailure { Log.e(TAG, "appendGpxPoint FAILED", it) }
    }

    private fun closeGpx() {
        runCatching {
            gpxWriter?.apply {
                appendLine("</trkseg></trk></gpx>")
                flush()
                close()
            }
        }.onFailure { Log.e(TAG, "closeGpx FAILED", it) }
        val file = gpxFile
        gpxWriter = null
        gpxFile = null

        if (file != null) {
            MediaScannerConnection.scanFile(
                this,
                arrayOf(file.absolutePath),
                arrayOf("application/gpx+xml")
            ) { _, _ -> /* no-op */ }

            showToast("GPX enregistré : ${file.name}")
            Log.i(TAG, "GPX fermé -> ${file.absolutePath}")
        }
    }

    private fun showToast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            runCatching { Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show() }
        }
    }
}

// ===== util =====
private fun GeoPoint.toLocation(): Location = Location("restore").apply {
    latitude = this@toLocation.latitude
    longitude = this@toLocation.longitude
    if (!this@toLocation.altitude.isNaN()) altitude = this@toLocation.altitude
}

// Persistance état rec
private object RecordingPrefs {
    private const val KEY_ACTIVE = "rec_active"
    private const val KEY_PAUSED = "rec_paused"
    fun set(context: Context, active: Boolean, paused: Boolean) {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putBoolean(KEY_ACTIVE, active).putBoolean(KEY_PAUSED, paused).apply()
    }
    fun isActive(context: Context): Boolean =
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY_ACTIVE, false)
}
