package com.hikemvp.group

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle

/**
 * Minimal GPS helper without extra dependencies.
 * Call start() when group session starts and stop() when it ends.
 * Replace onLocation callback to actually broadcast positions over BLE later.
 */
class LocationPusher(private val context: Context, private val onLocation: (Location) -> Unit) {

    private var lm: LocationManager? = null
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            onLocation(location)
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    @SuppressLint("MissingPermission")
    fun start(minTimeMs: Long = 10_000L, minDistanceM: Float = 10f) {
        lm = (context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager)?.also { mgr ->
            // Caller must manage runtime permission for ACCESS_FINE_LOCATION.
            val gps = LocationManager.GPS_PROVIDER
            if (mgr.isProviderEnabled(gps)) {
                mgr.requestLocationUpdates(gps, minTimeMs, minDistanceM, listener)
            }
        }
    }

    fun stop() {
        lm?.removeUpdates(listener)
        lm = null
    }
}