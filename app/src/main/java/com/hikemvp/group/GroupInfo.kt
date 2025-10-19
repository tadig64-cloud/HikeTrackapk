package com.hikemvp.group

import org.osmdroid.util.GeoPoint
import kotlin.math.*

/** Utilitaires simples pour infos enrichies (distance, cap, etc.). */
object GroupInfo {
    /** Distance (m) Haversine approx. */
    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val x = sin(dLat/2)*sin(dLat/2) + cos(lat1)*cos(lat2)*sin(dLon/2)*sin(dLon/2)
        val c = 2 * atan2(sqrt(x), sqrt(1-x))
        return R * c
    }

    /** Cap en degrés 0..360 */
    fun bearingDeg(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1)*sin(lat2) - sin(lat1)*cos(lat2)*cos(dLon)
        var brng = Math.toDegrees(atan2(y, x))
        if (brng < 0) brng += 360.0
        return brng
    }
}
