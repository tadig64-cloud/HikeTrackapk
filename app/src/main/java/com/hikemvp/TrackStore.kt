package com.hikemvp

import org.osmdroid.util.GeoPoint
import java.util.concurrent.CopyOnWriteArrayList

object TrackStore {
    private val points = CopyOnWriteArrayList<GeoPoint>()
    @Volatile var startTimestamp: Long? = null
        private set

    fun snapshot(): List<GeoPoint> = points.toList()

    fun clear() {
        points.clear()
        startTimestamp = null
    }

    fun add(point: GeoPoint, ts: Long? = null) {
        points.add(point)
        if (startTimestamp == null) startTimestamp = ts ?: System.currentTimeMillis()
    }

    fun setAll(newPoints: List<GeoPoint>) {
        points.clear()
        points.addAll(newPoints)
        if (startTimestamp == null && newPoints.isNotEmpty()) {
            startTimestamp = System.currentTimeMillis()
        }
    }
}
