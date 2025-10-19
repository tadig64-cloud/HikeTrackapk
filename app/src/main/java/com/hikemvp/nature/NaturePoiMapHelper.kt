package com.hikemvp.nature

import android.content.Context
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

object NaturePoiMapHelper {

    fun addMarkers(context: Context, map: MapView, pois: List<NaturePoi>): List<Marker> {
        val markers = mutableListOf<Marker>()
        for (poi in pois) {
            val m = Marker(map)
            m.position = GeoPoint(poi.lat, poi.lon)
            m.title = when (poi.type) {
                NaturePoiType.TREE -> "🌳 ${poi.name}"
                NaturePoiType.SHELTER -> "🏠 ${poi.name}"
                NaturePoiType.PROTECTED -> "🛡️ ${poi.name}"
            }
            val radius = poi.radiusM?.let { " — rayon ~${it.toInt()} m" } ?: ""
            m.subDescription = (poi.note ?: "") + radius
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            m.isDraggable = false
            map.overlays.add(m)
            markers.add(m)
        }
        map.invalidate()
        return markers
    }

    fun removeMarkers(map: MapView, markers: List<Marker>) {
        map.overlays.removeAll(markers)
        map.invalidate()
    }
}