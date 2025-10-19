package com.hikemvp.group

import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

/**
 * API unifiée & tolérante.
 * Ajoute des surcharges Any? pour accepter les appels où la carte est typée trop large.
 */
interface GroupOverlayApi {
    val members: MutableMap<String, GeoPoint>

    fun attachTo(mapView: MapView)
    fun detachFrom(mapView: MapView)

    fun setMembers(list: List<GroupMember>, map: MapView)
    fun addOrUpdateMember(member: GroupMember, map: MapView)
    fun removeMemberById(id: String, map: MapView)

    fun upsertPosition(id: String, gp: GeoPoint)

    fun focusOn(id: String, map: MapView)
    fun focusSmoothOn(id: String, map: MapView, zoomLevel: Double? = null)

    fun zoomToAll()
    fun zoomToAll(map: MapView) = zoomToAll()

    // ====== Surcharges tolérantes ======
    fun setMembers(list: List<GroupMember>, map: Any?) {
        val mv = (map as? MapView) ?: com.hikemvp.GroupGlobals.mapView ?: return
        setMembers(list, mv)
    }
    fun addOrUpdateMember(member: GroupMember, map: Any?) {
        val mv = (map as? MapView) ?: com.hikemvp.GroupGlobals.mapView ?: return
        addOrUpdateMember(member, mv)
    }
    fun removeMemberById(id: String, map: Any?) {
        val mv = (map as? MapView) ?: com.hikemvp.GroupGlobals.mapView ?: return
        removeMemberById(id, mv)
    }
    fun focusOn(id: String, map: Any?) {
        val mv = (map as? MapView) ?: com.hikemvp.GroupGlobals.mapView ?: return
        focusOn(id, mv)
    }
    fun focusSmoothOn(id: String, map: Any?, zoomLevel: Double? = null) {
        val mv = (map as? MapView) ?: com.hikemvp.GroupGlobals.mapView ?: return
        focusSmoothOn(id, mv, zoomLevel)
    }
    fun zoomToAll(map: Any?) = zoomToAll()

    fun setAutoCenterOnTap(enabled: Boolean, zoomLevel: Double? = null)
    fun setClustering(enabled: Boolean, map: MapView)

    fun setMembers(any: Any?, replace: Boolean = true)
}
