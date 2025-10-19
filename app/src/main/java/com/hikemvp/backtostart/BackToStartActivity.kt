package com.hikemvp.backtostart

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.hikemvp.R
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class BackToStartActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var myLocationOverlay: MyLocationNewOverlay
    private var backLine: Polyline? = null

    private val prefs by lazy { getSharedPreferences("hiketrack_prefs", MODE_PRIVATE) }

    private val reqPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ){ _ -> ensureLocationReady() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // osmdroid - charge config
        Configuration.getInstance().load(this, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this))
        setContentView(R.layout.activity_back_to_start)

        // Toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        // Map
        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), map)
        myLocationOverlay.enableMyLocation()
        map.overlays.add(myLocationOverlay)

        // UI listeners
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSetHere).setOnClickListener { setStartHere() }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnClear).setOnClickListener { clearStart() }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnNavigate).setOnClickListener { drawLineAndUpdateInfo() }

        // État initial
        showCurrentTargetInfo()

        ensureLocationReady()
    }

    private fun ensureLocationReady() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            reqPerms.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        myLocationOverlay.enableMyLocation()
        myLocationOverlay.runOnFirstFix {
            val loc = myLocationOverlay.lastFix
            if (loc != null) {
                val gp = GeoPoint(loc.latitude, loc.longitude)
                runOnUiThread {
                    map.controller.setZoom(16.0)
                    map.controller.animateTo(gp)
                }
            }
        }
    }

    private fun setStartHere() {
        val loc = myLocationOverlay.lastFix
        if (loc == null) {
            Toast.makeText(this, getString(R.string.err_no_location_yet), Toast.LENGTH_SHORT).show()
            return
        }
        prefs.edit()
            .putFloat("start_lat", loc.latitude.toFloat())
            .putFloat("start_lon", loc.longitude.toFloat())
            .apply()
        Toast.makeText(this, getString(R.string.back_to_start_set_here_ok), Toast.LENGTH_SHORT).show()
        showCurrentTargetInfo()
        drawLineAndUpdateInfo()
    }

    private fun clearStart() {
        prefs.edit().remove("start_lat").remove("start_lon").apply()
        Toast.makeText(this, getString(R.string.back_to_start_clear), Toast.LENGTH_SHORT).show()
        findViewById<android.widget.TextView>(R.id.tvTarget).text = getString(R.string.back_to_start_no_target)
        findViewById<android.widget.TextView>(R.id.tvDistance).text = getString(R.string.distance_to_start_default)
        findViewById<android.widget.TextView>(R.id.tvBearing).text = getString(R.string.bearing_default)
        backLine?.let { map.overlays.remove(it) }
        backLine = null
        map.invalidate()
    }

    private fun hasTarget(): Boolean =
        prefs.contains("start_lat") && prefs.contains("start_lon")

    private fun getTarget(): GeoPoint? {
        if (!hasTarget()) return null
        val lat = prefs.getFloat("start_lat", 0f).toDouble()
        val lon = prefs.getFloat("start_lon", 0f).toDouble()
        return GeoPoint(lat, lon)
    }

    private fun showCurrentTargetInfo() {
        val tgt = getTarget()
        val tvTarget = findViewById<android.widget.TextView>(R.id.tvTarget)
        if (tgt == null) {
            tvTarget.text = getString(R.string.back_to_start_no_target)
        } else {
            tvTarget.text = getString(R.string.back_to_start_target_fmt, tgt.latitude, tgt.longitude)
        }
    }

    private fun currentGeo(): GeoPoint? {
        val loc: Location? = myLocationOverlay.lastFix
        return if (loc != null) GeoPoint(loc.latitude, loc.longitude) else null
    }

    private fun drawLineAndUpdateInfo() {
        val tgt = getTarget()
        val cur = currentGeo()
        if (tgt == null) {
            Toast.makeText(this, getString(R.string.back_to_start_no_target), Toast.LENGTH_SHORT).show()
            return
        }
        if (cur == null) {
            Toast.makeText(this, getString(R.string.err_no_location_yet), Toast.LENGTH_SHORT).show()
            return
        }

        // polyline
        val pts = arrayListOf(cur, tgt)
        if (backLine == null) {
            backLine = Polyline().apply { outlinePaint.strokeWidth = 6f }
            map.overlays.add(backLine)
        }
        backLine!!.setPoints(pts)
        map.invalidate()

        // distance + cap
        val tvDistance = findViewById<android.widget.TextView>(R.id.tvDistance)
        val tvBearing = findViewById<android.widget.TextView>(R.id.tvBearing)

        val distM = cur.distanceToAsDouble(tgt)
        if (distM >= 1000.0) {
            tvDistance.text = getString(R.string.distance_km_fmt, distM / 1000.0)
        } else {
            tvDistance.text = getString(R.string.distance_m_fmt, distM.roundToInt())
        }
        val brg = bearingDeg(cur.latitude, cur.longitude, tgt.latitude, tgt.longitude).roundToInt()
        tvBearing.text = getString(R.string.bearing_fmt, brg)

        map.controller.animateTo(cur)
    }

    /** Bearing géodésique en degrés 0..360 */
    private fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val φ1 = Math.toRadians(lat1)
        val φ2 = Math.toRadians(lat2)
        val Δλ = Math.toRadians(lon2 - lon1)
        val y = sin(Δλ) * cos(φ2)
        val x = cos(φ1) * sin(φ2) - sin(φ1) * cos(φ2) * cos(Δλ)
        val θ = atan2(y, x)
        var deg = Math.toDegrees(θ)
        if (deg < 0) deg += 360.0
        return deg
    }
}
