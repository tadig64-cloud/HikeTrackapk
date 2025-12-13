package com.hikemvp.group

import org.osmdroid.util.GeoPoint

/**
 * Stockage en mémoire des TRACES de groupe (une liste de points par membre).
 *
 * Pour l'instant on garde simplement une liste de GeoPoint par membre.
 * On pourra enrichir plus tard (timestamps, altitudes, stats, export GPX, etc.).
 */
object GroupTrackStore {

    data class Point(
        val geo: GeoPoint
    )

    // id membre -> liste de points
    private val tracks: MutableMap<String, MutableList<Point>> = LinkedHashMap()

    @Synchronized
    fun clearAll() {
        tracks.clear()
    }

    @Synchronized
    fun addPoint(memberId: String, gp: GeoPoint) {
        val list = tracks.getOrPut(memberId) { mutableListOf() }
        val last = list.lastOrNull()
        // Évite d'empiler des doublons exacts
        if (last == null ||
            last.geo.latitude != gp.latitude ||
            last.geo.longitude != gp.longitude
        ) {
            list.add(Point(gp))
        }
    }

    /**
     * Snapshot immuable des données, à utiliser côté overlay.
     */
    @Synchronized
    fun snapshot(): Map<String, List<Point>> {
        if (tracks.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, List<Point>>(tracks.size)
        for ((id, pts) in tracks) {
            out[id] = pts.toList()
        }
        return out
    }
}
