package com.hikemvp.group

import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

/**
 * Impl simple qui n'hérite PAS d'Overlay et se contente de gérer l'état + zoom.
 * Peut être utilisée dans des tests/unitaires, ou en fallback si nécessaire.
 */
class GroupOverlayImpl : GroupOverlayApi {
    override val members: MutableMap<String, GeoPoint> = LinkedHashMap()

    override fun attachTo(mapView: MapView) { /* no-op */ }
    override fun detachFrom(mapView: MapView) { /* no-op */ }

    override fun setMembers(list: List<GroupMember>, map: MapView) {
        members.clear()
        list.forEach { m -> members[m.id] = m.point }
        map.invalidate()
    }

    override fun addOrUpdateMember(member: GroupMember, map: MapView) {
        members[member.id] = member.point
        map.invalidate()
    }

    override fun removeMemberById(id: String, map: MapView) {
        members.remove(id)
        map.invalidate()
    }

    override fun upsertPosition(id: String, gp: GeoPoint) {
        members[id] = gp
    }

    override fun focusOn(id: String, map: MapView) {
        val gp = members[id] ?: return
        runCatching { map.controller.setCenter(gp) }
    }

    override fun focusSmoothOn(id: String, map: MapView, zoomLevel: Double?) {
        val gp = members[id] ?: return
        runCatching {
            if (zoomLevel != null) map.controller.animateTo(gp, zoomLevel, 600L)
            else map.controller.animateTo(gp)
        }
    }

    override fun zoomToAll() {
        val mv = com.hikemvp.GroupGlobals.mapView ?: return
        if (members.isEmpty()) return
        val bbox = BoundingBox.fromGeoPointsSafe(members.values.toList())
        mv.zoomToBoundingBox(bbox, true)
    }

    override fun setAutoCenterOnTap(enabled: Boolean, zoomLevel: Double?) { /* no-op */ }

    override fun setClustering(enabled: Boolean, map: MapView) { /* no-op */ }

    override fun setMembers(any: Any?, replace: Boolean) {
        if (replace) members.clear()
        when (any) {
            null -> {}
            is List<*> -> any.forEach { item ->
                when (item) {
                    is GroupMember -> members[item.id] = item.point
                    is Pair<*,*> -> {
                        val id = item.first?.toString() ?: return@forEach
                        val gp = item.second as? GeoPoint ?: return@forEach
                        members[id] = gp
                    }
                }
            }
        }
    }
}
