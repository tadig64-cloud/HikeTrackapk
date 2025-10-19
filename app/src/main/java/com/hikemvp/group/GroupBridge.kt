package com.hikemvp.group

import org.osmdroid.views.MapView
import org.osmdroid.util.GeoPoint
import java.lang.ref.WeakReference

/**
 * Bridge "groupe" — version compatible avec affectation directe depuis MapActivity.
 * - mapView: carte courante (via WeakReference)
 * - overlay: GroupOverlayApi? (setter PUBLIC pour compat: MapActivity peut faire `GroupBridge.overlay = ...`)
 * - attach/detach helpers + upsert tolérant
 */
object GroupBridge {

    // ===== Référence carte =====
    @Volatile private var mapRef: WeakReference<MapView?>? = null
    val mapView: MapView?
        get() = mapRef?.get()

    fun setMap(map: MapView?) {
        mapRef = WeakReference(map)
    }

    // ===== Overlay courant =====
    @Volatile
    var overlay: GroupOverlayApi? = null  // setter PUBLIC pour compat avec ton code existant

    fun attach(map: MapView, ov: GroupOverlayApi?) {
        setMap(map)
        overlay = ov
    }

    fun detach(map: MapView) {
        if (mapRef?.get() === map) mapRef = null
    }

    // ===== Injection de position souple =====
    fun upsert(id: String, gp: GeoPoint) {
        overlay?.upsertPosition(id, gp)
        // Fallback "amical" au cas où une autre impl existe encore
        runCatching {
            val anyOv = overlay ?: return@runCatching
            val m = anyOv.javaClass.methods.firstOrNull {
                it.name == "upsertPosition" &&
                it.parameterCount == 2 &&
                it.parameterTypes[0] == String::class.java &&
                it.parameterTypes[1].name == "org.osmdroid.util.GeoPoint"
            }
            if (m != null) m.invoke(anyOv, id, gp)
        }
    }
}
