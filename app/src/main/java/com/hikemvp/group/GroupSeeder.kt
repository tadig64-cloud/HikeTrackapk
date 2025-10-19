package com.hikemvp.group

import android.graphics.Color
import org.osmdroid.util.GeoPoint
import kotlin.math.cos
import kotlin.math.sin

object GroupSeeder {

    fun seedAround(center: GeoPoint, count: Int = 10): List<GroupMember> {
        val out = ArrayList<GroupMember>()
        val R = 120.0 // mètres
        val step = (Math.PI * 2) / count
        var a = 0.0
        for (i in 0 until count) {
            val dx = R * cos(a)
            val dy = R * sin(a)
            val gp = GeoPoint(center.latitude + metersToDegLat(dy), center.longitude + metersToDegLon(dx, center.latitude))
            val id = "m%02d".format(i+1)
            val name = "Membre %02d".format(i+1)
            val color = Color.HSVToColor(floatArrayOf(((i*36)%360).toFloat(), 0.75f, 0.95f))
            out.add(GroupMember(id, name, gp, color))
            a += step
        }
        return out
    }

    private fun metersToDegLat(m: Double): Double = m / 111_320.0
    private fun metersToDegLon(m: Double, latDeg: Double): Double {
        val metersPerDeg = 111_320.0 * kotlin.math.cos(Math.toRadians(latDeg).coerceIn(-1.0, 1.0))
        return if (metersPerDeg <= 0.0) 0.0 else m / metersPerDeg
    }
}
