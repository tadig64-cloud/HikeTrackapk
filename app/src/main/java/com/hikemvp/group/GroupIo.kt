package com.hikemvp.group

import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

/**
 * Import/Export JSON des membres du groupe (add-only).
 */
object GroupIo {

    fun exportMembers(pretty: Boolean = true): String {
        val ov = GroupBridge.overlay ?: return "[]"
        val arr = JSONArray()
        // tente de lire membersData pour garder name/color
        val map: Map<String, GroupMember>? = try {
            val f = ov.javaClass.getDeclaredField("membersData"); f.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            f.get(ov) as? Map<String, GroupMember>
        } catch (_: Throwable) { null }

        ov.members.forEach { (id, gp) ->
            val m = map?.get(id) ?: GroupMember(id, id, gp)
            val o = JSONObject()
                .put("id", m.id)
                .put("name", m.name)
                .put("lat", m.point.latitude)
                .put("lon", m.point.longitude)
                .put("color", m.color)
            arr.put(o)
        }
        return if (pretty) arr.toString(2) else arr.toString()
    }

    /**
     * Remplace ou fusionne les membres depuis un JSON.
     * @param replace si true, remplace la liste, sinon fusion (upsert).
     */
    fun importMembers(json: String, mapView: MapView, replace: Boolean = false) {
        val ov = GroupBridge.overlay ?: return
        val arr = JSONArray(json)
        val list = ArrayList<GroupMember>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.getString("id")
            val name = o.optString("name", id)
            val lat = o.getDouble("lat")
            val lon = o.getDouble("lon")
            val color = o.optInt("color", 0xff4285F4.toInt())
            list.add(GroupMember(id, name, GeoPoint(lat, lon), color))
        }
        if (replace) ov.setMembers(list, mapView)
        else list.forEach { m -> ov.addOrUpdateMember(m, mapView) }
        ov.zoomToAll()
    }
}
