package com.hikemvp.group

import org.osmdroid.views.MapView

object GroupMapHook {

    fun attach(mapView: MapView, overlay: GroupOverlay?) {
        overlay?.let { ol ->
            ol.attachTo(mapView)
            if (!mapView.overlays.contains(ol)) {
                mapView.overlays.add(ol)
            }
            mapView.invalidate()
        }
    }

    fun detach(mapView: MapView, overlay: GroupOverlay?) {
        overlay?.let { ol ->
            ol.detachFrom(mapView)
            mapView.overlays.remove(ol)
            mapView.invalidate()
        }
    }

    fun updateMembers(mapView: MapView, overlay: GroupOverlay?, members: List<GroupMember>) {
        overlay?.setMembers(members, mapView)
    }

    fun addOrUpdate(mapView: MapView, overlay: GroupOverlay?, member: GroupMember) {
        overlay?.addOrUpdateMember(member, mapView)
    }

    fun remove(mapView: MapView, overlay: GroupOverlay?, id: String) {
        overlay?.removeMemberById(id, mapView)
    }

    fun zoomAll(mapView: MapView, overlay: GroupOverlay?) {
        overlay?.zoomToAll()
    }

    fun setAutoCenter(overlay: GroupOverlay?, enabled: Boolean, zoomLevel: Double? = null) {
        overlay?.setAutoCenterOnTap(enabled, zoomLevel)
    }

    fun setClustering(mapView: MapView, overlay: GroupOverlay?, enabled: Boolean) {
        overlay?.setClustering(enabled, mapView)
    }

    fun focusOn(mapView: MapView, overlay: GroupOverlay?, id: String) {
        overlay?.focusOn(id, mapView)
    }
}
