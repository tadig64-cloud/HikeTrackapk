package com.hikemvp.group

import android.os.Handler
import android.os.Looper
import org.osmdroid.util.GeoPoint
import kotlin.math.*
import kotlin.jvm.JvmName

/**
 * LiveSim + follow configurable.
 */
object GroupLiveSim {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    @get:JvmName("isRunningProp")
    val isRunning: Boolean get() = running
    fun isRunning(): Boolean = running

    private var followId: String? = null
    private var periodMs: Long = 1500L
    private var amplitudeMeters: Double = 6.0

    fun setFollow(id: String?) { followId = id }
    fun configure(periodMs: Long = 1500L, amplitudeMeters: Double = 6.0) {
        this.periodMs = max(300L, periodMs)
        this.amplitudeMeters = amplitudeMeters.coerceAtLeast(0.0)
        if (running) {
            handler.removeCallbacks(tick); handler.postDelayed(tick, this.periodMs)
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val ov = GroupBridge.overlay
            val map = GroupBridge.mapView
            if (ov != null && map != null) {
                val updated = ArrayList<GroupMember>()
                ov.members.forEach { (id, gp) ->
                    val latJ = metersToDegLat((Math.random() - 0.5) * 2.0 * amplitudeMeters)
                    val lonJ = metersToDegLon((Math.random() - 0.5) * 2.0 * amplitudeMeters, gp.latitude)
                    val newGp = GeoPoint(clampLat(gp.latitude + latJ), clampLon(gp.longitude + lonJ))
                    updated.add(GroupMember(id, id, newGp))
                }
                ov.setMembers(updated, map)
                followId?.let { fid -> ov.focusSmoothOn(fid, map, 17.0) }
            }
            handler.postDelayed(this, periodMs)
        }
    }

    fun start(periodMs: Long = 1500L, amplitudeMeters: Double = 6.0, followId: String? = null) {
        this.periodMs = max(300L, periodMs)
        this.amplitudeMeters = amplitudeMeters.coerceAtLeast(0.0)
        this.followId = followId
        if (!running) {
            running = true
            handler.removeCallbacks(tick)
            handler.postDelayed(tick, this.periodMs)
        }
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }

    private fun metersToDegLat(m: Double): Double = m / 111_320.0
    private fun metersToDegLon(m: Double, latDeg: Double): Double {
        val metersPerDeg = 111_320.0 * cos(Math.toRadians(latDeg).coerceIn(-1.0, 1.0))
        return if (metersPerDeg <= 0.0) 0.0 else m / metersPerDeg
    }
    private fun clampLat(lat: Double): Double = lat.coerceIn(-85.0, 85.0)
    private fun clampLon(lon: Double): Double {
        var x = lon
        while (x < -180.0) x += 360.0
        while (x > 180.0) x -= 360.0
        return x
    }
}
