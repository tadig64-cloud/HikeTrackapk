package com.hikemvp.waypoints

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stockage JSON simple dans SharedPreferences.
 * Expose une API "simple" (WaypointMeta) et "complète" (WaypointExt) pour compat.
 */
object WaypointStorage {

    private const val PREFS_KEY = "waypoints_json"
    private const val PREFS_HIDDEN_GROUPS = "waypoints_hidden_groups"

    // ===== API simple =====
    fun list(context: Context): List<WaypointMeta> =
        listFull(context).map { it.toMeta() }

    fun add(context: Context, meta: WaypointMeta): WaypointMeta {
        val all = listFull(context).toMutableList()
        val ext = meta.toExt()
        all.add(ext)
        saveFull(context, all)
        return ext.toMeta()
    }

    fun delete(context: Context, id: Long): Boolean {
        val all = listFull(context).toMutableList()
        val initial = all.size
        all.removeAll { it.id == id }
        saveFull(context, all)
        return all.size != initial
    }

    fun rename(context: Context, id: Long, newName: String): Boolean {
        val w = get(context, id) ?: return false
        w.name = newName
        return updateFull(context, w)
    }

    // ===== API complète =====
    fun listFull(context: Context): List<WaypointExt> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val json = prefs.getString(PREFS_KEY, "[]") ?: "[]"
        val arr = JSONArray(json)
        val out = ArrayList<WaypointExt>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(fromJson(o))
        }
        return out
    }

    fun get(context: Context, id: Long): WaypointExt? =
        listFull(context).firstOrNull { it.id == id }

    fun addFull(context: Context, w: WaypointExt): WaypointExt {
        val all = listFull(context).toMutableList()
        val id = if (w.id == 0L) System.currentTimeMillis() else w.id
        all.add(w.copy(id = id))
        saveFull(context, all)
        return w.copy(id = id)
    }

    fun updateFull(context: Context, w: WaypointExt): Boolean {
        val all = listFull(context).toMutableList()
        val idx = all.indexOfFirst { it.id == w.id }
        if (idx == -1) return false
        all[idx] = w
        saveFull(context, all)
        return true
    }

    fun saveFull(context: Context, list: List<WaypointExt>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putString(PREFS_KEY, arr.toString()).apply()
    }

    fun clear(context: Context) {
        saveFull(context, emptyList())
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().remove(PREFS_HIDDEN_GROUPS).apply()
    }

    // ===== Hidden groups (facultatif, sûr si non utilisé) =====
    fun getHiddenGroups(context: Context): Set<String> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val s = prefs.getString(PREFS_HIDDEN_GROUPS, "") ?: ""
        return s.split('|').filter { it.isNotBlank() }.toSet()
    }

    fun setHiddenGroups(context: Context, groups: Set<String>) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val s = groups.joinToString("|")
        prefs.edit().putString(PREFS_HIDDEN_GROUPS, s).apply()
    }

    // ===== JSON helpers =====
    private fun toJson(w: WaypointExt): JSONObject = JSONObject().apply {
        put("id", w.id)
        put("lat", w.latitude)
        put("lon", w.longitude)
        put("name", w.name)
        put("note", w.note)
        put("group", w.group)
        put("timestamp", w.timestamp)
        put("attachments", JSONArray(w.attachments))
    }

    private fun fromJson(o: JSONObject): WaypointExt {
        val id = o.optLong("id", 0L)
        val lat = o.optDouble("lat", o.optDouble("latitude"))
        val lon = o.optDouble("lon", o.optDouble("longitude"))
        val name = o.optString("name", "WP")
        val note = if (o.has("note")) o.optString("note") else null
        val group = if (o.has("group")) o.optString("group") else null
        val ts = o.optLong("timestamp", System.currentTimeMillis())
        val atts = mutableListOf<String>().apply {
            val arr = o.optJSONArray("attachments")
            if (arr != null) for (i in 0 until arr.length()) add(arr.optString(i))
        }
        return WaypointExt(
            id = if (id == 0L) System.currentTimeMillis() else id,
            latitude = lat,
            longitude = lon,
            name = name,
            note = note,
            group = group,
            timestamp = ts,
            attachments = atts
        )
    }
}
