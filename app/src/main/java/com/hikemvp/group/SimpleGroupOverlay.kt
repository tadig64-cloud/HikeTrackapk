package com.hikemvp.group

import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

/**
 * Variante minimaliste (sans Overlay), compatible avec l'API.
 */
class SimpleGroupOverlay : GroupOverlayApi {
    private val _members = LinkedHashMap<String, GeoPoint>()
    override val members: MutableMap<String, GeoPoint> get() = _members

    override fun attachTo(mapView: MapView) { /* no-op */ }
    override fun detachFrom(mapView: MapView) { /* no-op */ }

    override fun setMembers(list: List<GroupMember>, map: MapView) {
        _members.clear()
        list.forEach { m -> _members[m.id] = m.point }
        map.invalidate()
    }

    override fun addOrUpdateMember(member: GroupMember, map: MapView) {
        _members[member.id] = member.point
        map.invalidate()
    }

    override fun removeMemberById(id: String, map: MapView) {
        _members.remove(id)
        map.invalidate()
    }

    override fun upsertPosition(id: String, gp: GeoPoint) {
        _members[id] = gp
    }

    override fun focusOn(id: String, map: MapView) {
        val gp = _members[id] ?: return
        runCatching { map.controller.setCenter(gp) }
    }

    override fun focusSmoothOn(id: String, map: MapView, zoomLevel: Double?) {
        val gp = _members[id] ?: return
        runCatching {
            if (zoomLevel != null) map.controller.animateTo(gp, zoomLevel, 600L)
            else map.controller.animateTo(gp)
        }
    }

    override fun zoomToAll() {
        val mv = com.hikemvp.GroupGlobals.mapView ?: return
        if (_members.isEmpty()) return
        val bbox = BoundingBox.fromGeoPointsSafe(_members.values.toList())
        mv.zoomToBoundingBox(bbox, true)
    }

    override fun setAutoCenterOnTap(enabled: Boolean, zoomLevel: Double?) { /* no-op */ }

    override fun setClustering(enabled: Boolean, map: MapView) { /* no-op */ }

    override fun setMembers(any: Any?, replace: Boolean) {
        if (replace) _members.clear()
        when (any) {
            is List<*> -> any.forEach { item ->
                when (item) {
                    is GroupMember -> _members[item.id] = item.point
                    is Pair<*,*> -> {
                        val id = item.first?.toString() ?: return@forEach
                        val gp = item.second as? GeoPoint ?: return@forEach
                        _members[id] = gp
                    }
                }
            }
        }
    }
}
