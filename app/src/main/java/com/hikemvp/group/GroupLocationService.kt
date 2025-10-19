@file:Suppress("DEPRECATION")
package com.hikemvp.group

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*

class GroupLocationService : Service() {

    companion object {
        const val ACTION_LOCATION = "com.hikemvp.group.ACTION_LOCATION"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LON = "lon"
    }

    private lateinit var fused: FusedLocationProviderClient

    private val callback = object: LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            val i = Intent(ACTION_LOCATION)
                .putExtra(EXTRA_LAT, loc.latitude)
                .putExtra(EXTRA_LON, loc.longitude)
            LocalBroadcastManager.getInstance(this@GroupLocationService).sendBroadcast(i)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        start()
    }

    private fun start() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000L)
            .setMinUpdateIntervalMillis(2000L).build()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(); return
        }
        fused.requestLocationUpdates(req, callback, Looper.getMainLooper())
    }

    override fun onDestroy() {
        runCatching { fused.removeLocationUpdates(callback) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
