package com.hikemvp.group

import android.content.Context
import org.osmdroid.util.GeoPoint
import kotlin.math.cos
import kotlin.math.sin

object GroupJoin {

    /**
     * Gère quelques codes DEMO pour charger un roster local prêt à l'emploi.
     * Retourne true si le code est reconnu.
     */
    fun joinByLinkCode(context: Context, code: String, done: (Boolean) -> Unit) {
        val norm = code.trim().uppercase()
        val roster: Map<String, GeoPoint>? = when (norm) {
            "DEMO-TOULOUSE" -> demoAround(43.6045, 1.4440, "TLS", 12)
            "DEMO-ALPES" -> demoAround(45.9237, 6.8694, "ALP", 12) // Chamonix
            else -> null
        }
        if (roster != null) {
            // Applique au module
            GroupBridge.overlay?.setMembers(roster, true)
            // Sauvegarde locale pour persistance
            GroupStore.save(context, roster)
            // Recentrer
            try { GroupActions.zoomAll() } catch (_: Throwable) {}
            done(true)
        } else {
            done(false)
        }
    }

    /**
     * Génére n points autour d'un centre (anneau), IDs lisibles PREFIX-XXXX
     */
    private fun demoAround(lat: Double, lon: Double, prefix: String, n: Int): Map<String, GeoPoint> {
        val center = GeoPoint(lat, lon)
        val out = LinkedHashMap<String, GeoPoint>(n)
        val radiusDegBase = 0.004   // ~ 400 m
        val radiusDegAlt  = 0.0022  // ~ 220 m
        for (i in 0 until n) {
            val angle = (Math.PI * 2.0) * (i.toDouble() / n.toDouble())
            val r = if (i % 2 == 0) radiusDegBase else radiusDegAlt
            val dLat = r * sin(angle)
            val dLon = r * cos(angle)
            val gp = GeoPoint(center.latitude + dLat, center.longitude + dLon)
            val id = "%s-%04d".format(prefix, i + 1)
            out[id] = gp
        }
        return out
    }
}
