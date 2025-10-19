package com.hikemvp.sos

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.hikemvp.R
import java.util.Locale
import kotlin.math.roundToInt

class SosActivity : AppCompatActivity() {

    private lateinit var tvGps: TextView
    private lateinit var tvBattery: TextView
    private lateinit var btnCall: MaterialButton
    private lateinit var btnShare: MaterialButton

    private var lastFix: Location? = null
    private var batteryPct: Int? = null

    // ===== Permissions localisation =====
    private val reqPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grant ->
        val ok = (grant[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                (grant[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        if (ok) startLocationUpdates() else showNoLoc()
    }

    // ===== Location manager simple (pas de Play Services) =====
    private val locMgr by lazy { getSystemService(LOCATION_SERVICE) as LocationManager }

    private val locListener = LocationListener { loc ->
        lastFix = loc
        updateGpsText()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sos)

        // Toolbar
        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setSupportActionBar(this)
            setNavigationOnClickListener { finish() }
        }

        tvGps = findViewById(R.id.tvGps)
        tvBattery = findViewById(R.id.tvBattery)
        btnCall = findViewById(R.id.btnCall112)
        btnShare = findViewById(R.id.btnShare)

        // Bouton Appeler 112 (via ACTION_DIAL, pas besoin de permission CALL)
        btnCall.setOnClickListener {
            try {
                val number = getString(R.string.ht_emergency_number)
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
            } catch (_: Throwable) {
                Toast.makeText(this, "Appel impossible sur cet appareil.", Toast.LENGTH_LONG).show()
            }
        }

        // Bouton Partager le message d’urgence + coordonnées
        btnShare.setOnClickListener { doShareEmergency() }

        // Batterie
        readBatteryOnce()

        // Localisation
        ensureLocation()
    }

    override fun onStart() {
        super.onStart()
        if (hasLocPerm()) startLocationUpdates()
    }

    override fun onStop() {
        super.onStop()
        runCatching { locMgr.removeUpdates(locListener) }
    }

    // ===== Helpers localisation =====
    private fun hasLocPerm(): Boolean {
        val f = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val c = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return f == PackageManager.PERMISSION_GRANTED || c == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureLocation() {
        if (hasLocPerm()) {
            startLocationUpdates()
        } else {
            reqPerms.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    private fun startLocationUpdates() {
        // Dernière position connue pour afficher vite
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        for (p in providers) {
            runCatching { locMgr.getLastKnownLocation(p) } .onSuccess {
                if (it != null && (lastFix == null || it.time > (lastFix?.time ?: 0L))) {
                    lastFix = it
                }
            }
        }
        updateGpsText()

        // Écoutes
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
                locMgr.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 2000L, 1f, locListener, Looper.getMainLooper()
                )
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
                locMgr.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 5000L, 5f, locListener, Looper.getMainLooper()
                )
            }
        } catch (_: SecurityException) { /* ignore */ }
    }

    private fun showNoLoc() {
        tvGps.text = getString(R.string.ht_hud_coords_default)
    }

    private fun updateGpsText() {
        val loc = lastFix
        if (loc == null) {
            showNoLoc(); return
        }
        val lat = loc.latitude
        val lon = loc.longitude
        val alt = if (!loc.hasAltitude()) null else "${loc.altitude.roundToInt()} m"
        val acc = if (!loc.hasAccuracy()) null else "±${loc.accuracy.roundToInt()} m"

        val altStr = alt ?: getString(R.string.weather_na)
        val accStr = acc ?: getString(R.string.weather_na)

        // Plus d’index (%1$...), donc aucun conflit avec l’interpolation Kotlin
        val msg = String.format(
            Locale.US,
            "Lat: %.5f\nLon: %.5f\nAlt: %s • %s",
            lat, lon, altStr, accStr
        )
        tvGps.text = msg
    }

    // ===== Batterie =====
    private fun readBatteryOnce() {
        try {
            val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val status: Intent? = registerReceiver(null, iFilter)
            if (status != null) {
                val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    val pct = ((level * 100f) / scale).roundToInt()
                    batteryPct = pct
                    tvBattery.text = "Batterie : $pct%"
                }
            }
        } catch (_: Throwable) { /* best-effort */ }
    }

    // ===== Partage =====
    private fun doShareEmergency() {
        val base = getString(R.string.ht_sos_share_text)
        val loc = lastFix
        val bat = batteryPct?.let { "$it%" } ?: "—"

        val details = if (loc != null) {
            val alt = if (loc.hasAltitude()) "${loc.altitude.roundToInt()} m" else "—"
            val acc = if (loc.hasAccuracy()) "±${loc.accuracy.roundToInt()} m" else "—"
            "Lat: %.5f, Lon: %.5f, Alt: %s, Précision: %s".format(
                Locale.US, loc.latitude, loc.longitude, alt, acc
            )
        } else {
            "Position: —"
        }

        val text = "$base\n$details\nBatterie: $bat"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        try {
            startActivity(Intent.createChooser(send, getString(R.string.ht_sos_share)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Aucune app de partage disponible.", Toast.LENGTH_SHORT).show()
        }
    }
}
