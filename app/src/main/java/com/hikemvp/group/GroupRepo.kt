package com.hikemvp.group

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import kotlin.random.Random
import java.util.UUID

/**
 * Repo local (SharedPreferences + JSON) avec rôles + import/export.
 */
object GroupRepo {
    private const val PREFS = "group_repo_v1"
    private const val KEY_GROUPS = "groups"
    private const val KEY_CURRENT = "current_group"
    private const val KEY_CODES = "invite_codes"

    fun listGroups(ctx: Context): List<GroupMeta> {
        val o = loadGroups(ctx)
        val out = ArrayList<GroupMeta>()
        o.keys().forEach { id ->
            val g = o.getJSONObject(id)
            out.add(GroupMeta(id, g.optString("name", id)))
        }
        return out.sortedBy { it.name.lowercase() }
    }

    fun currentGroupId(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CURRENT, null)

    fun setCurrentGroup(ctx: Context, id: String?) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_CURRENT, id) }
    }

    fun createGroup(ctx: Context, name: String, mapView: MapView, fromOverlay: Boolean = false): String {
        val id = UUID.randomUUID().toString().substring(0, 8)
        val groups = loadGroups(ctx)
        val owner = DeviceId.get(ctx)
        val g = JSONObject()
            .put("id", id)
            .put("name", name)
            .put("ownerId", owner)
            .put("admins", JSONArray().put(owner))
            .put("members", JSONArray())
        groups.put(id, g)
        saveGroups(ctx, groups)
        setCurrentGroup(ctx, id)

        if (fromOverlay) snapshotOverlayIntoGroup(ctx, id) else applyGroupToOverlay(ctx, id, mapView)
        return id
    }

    fun renameGroup(ctx: Context, id: String, newName: String) {
        val groups = loadGroups(ctx)
        val g = groups.optJSONObject(id) ?: return
        g.put("name", newName)
        saveGroups(ctx, groups)
    }

    fun deleteGroup(ctx: Context, id: String) {
        val groups = loadGroups(ctx)
        groups.remove(id)
        saveGroups(ctx, groups)
        if (currentGroupId(ctx) == id) setCurrentGroup(ctx, null)
    }

    fun applyGroupToOverlay(ctx: Context, id: String, mapView: MapView) {
        val ov = GroupBridge.overlay ?: return
        val groups = loadGroups(ctx)
        val g = groups.optJSONObject(id) ?: return
        val arr = g.optJSONArray("members") ?: JSONArray()
        val list = ArrayList<GroupMember>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val gid = o.getString("id")
            val name = o.optString("name", gid)
            val lat = o.getDouble("lat")
            val lon = o.getDouble("lon")
            val color = o.optInt("color", 0xff4285F4.toInt())
            list.add(GroupMember(gid, name, org.osmdroid.util.GeoPoint(lat, lon), color))
        }
        ov.setMembers(list, mapView)
        ov.zoomToAll()
    }

    fun snapshotOverlayIntoGroup(ctx: Context, id: String) {
        val ov = GroupBridge.overlay ?: return
        val groups = loadGroups(ctx)
        val g = groups.optJSONObject(id) ?: return
        val arr = JSONArray()

        val map: Map<String, GroupMember>? = try {
            val f = ov.javaClass.getDeclaredField("membersData"); f.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            f.get(ov) as? Map<String, GroupMember>
        } catch (_: Throwable) { null }

        ov.members.forEach { (mid, gp) ->
            val m = map?.get(mid) ?: GroupMember(mid, mid, gp)
            val o = JSONObject()
                .put("id", m.id)
                .put("name", m.name)
                .put("lat", m.point.latitude)
                .put("lon", m.point.longitude)
                .put("color", m.color)
            arr.put(o)
        }
        g.put("members", arr)
        saveGroups(ctx, groups)
    }

    // ----- Roles -----
    fun isOwner(ctx: Context, id: String): Boolean {
        val groups = loadGroups(ctx)
        val g = groups.optJSONObject(id) ?: return false
        return g.optString("ownerId", "") == DeviceId.get(ctx)
    }
    fun isAdmin(ctx: Context, id: String): Boolean {
        if (isOwner(ctx, id)) return true
        val groups = loadGroups(ctx)
        val g = groups.optJSONObject(id) ?: return false
        val admins = g.optJSONArray("admins") ?: JSONArray()
        val me = DeviceId.get(ctx)
        for (i in 0 until admins.length()) if (admins.getString(i) == me) return true
        return false
    }
    fun roleLabel(ctx: Context, id: String?): String =
        if (id == null) "(aucun)" else when {
            isOwner(ctx, id) -> "owner"
            isAdmin(ctx, id) -> "admin"
            else -> "membre"
        }

    fun grantAdminCurrentDevice(ctx: Context, id: String) {
        if (!isOwner(ctx, id)) return
        val groups = loadGroups(ctx)
        val g = groups.optJSONObject(id) ?: return
        val admins = g.optJSONArray("admins") ?: JSONArray()
        val me = DeviceId.get(ctx)
        var present = false
        for (i in 0 until admins.length()) if (admins.getString(i) == me) { present = true; break }
        if (!present) admins.put(me)
        g.put("admins", admins)
        saveGroups(ctx, groups)
    }
    fun revokeAdminCurrentDevice(ctx: Context, id: String) {
        if (!isOwner(ctx, id)) return
        val groups = loadGroups(ctx)
        val g = groups.optJSONObject(id) ?: return
        val admins = g.optJSONArray("admins") ?: JSONArray()
        val me = DeviceId.get(ctx)
        val kept = JSONArray()
        for (i in 0 until admins.length()) {
            val s = admins.getString(i)
            if (s != me) kept.put(s)
        }
        g.put("admins", kept)
        saveGroups(ctx, groups)
    }

    // ----- Import / Export (group level) -----
    fun exportGroupMembers(ctx: Context, id: String): String {
        val groups = loadGroups(ctx)
        val g = groups.optJSONObject(id) ?: return "[]"
        val arr = g.optJSONArray("members") ?: JSONArray()
        return arr.toString(2)
    }

    fun importIntoCurrent(ctx: Context, json: String, mapView: MapView, replace: Boolean) {
        val cur = currentGroupId(ctx) ?: return
        val groups = loadGroups(ctx)
        val g = groups.optJSONObject(cur) ?: return
        val inArr = JSONArray(json)
        val curArr = if (replace) JSONArray() else (g.optJSONArray("members") ?: JSONArray())

        val index = HashMap<String, Int>()
        for (i in 0 until curArr.length()) {
            val o = curArr.getJSONObject(i)
            index[o.getString("id")] = i
        }

        for (i in 0 until inArr.length()) {
            val o = inArr.getJSONObject(i)
            val id = o.getString("id")
            val entry = JSONObject()
                .put("id", id)
                .put("name", o.optString("name", id))
                .put("lat", o.getDouble("lat"))
                .put("lon", o.getDouble("lon"))
                .put("color", o.optInt("color", 0xff4285F4.toInt()))
            val pos = index[id]
            if (pos == null) curArr.put(entry) else curArr.put(pos, entry)
        }

        g.put("members", curArr)
        saveGroups(ctx, groups)
        applyGroupToOverlay(ctx, cur, mapView)
    }

    // ----- Invites -----
    private fun generateCode(n: Int): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val sb = StringBuilder(); repeat(n) { sb.append(chars[Random.nextInt(chars.length)]) }; return sb.toString()
    }
    

    fun createInvite(ctx: Context): Pair<String, String>? {
        val id = currentGroupId(ctx) ?: return null
        val code = generateCode(6)
        val codes = loadCodes(ctx)
        codes.put(code, id)
        saveCodes(ctx, codes)
        val deeplink = "hiketrack://group/join?code=$code"
        val text = "Rejoins mon groupe HikeTrack: code $code\n$deeplink"
        return code to text
    }

    fun joinByCode(ctx: Context, code: String, mapView: MapView): Boolean {
        val id = loadCodes(ctx).optString(code, null) ?: return false
        setCurrentGroup(ctx, id)
        applyGroupToOverlay(ctx, id, mapView)
        return true
    }

    fun shareText(ctx: Context, text: String) {
        val sendIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            putExtra(android.content.Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = android.content.Intent.createChooser(sendIntent, "Partager")
        ctx.startActivity(shareIntent)
    }

    // ----- Storage -----
    private fun loadGroups(ctx: Context): JSONObject {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val s = sp.getString(KEY_GROUPS, "{}") ?: "{}"
        return JSONObject(s)
    }
    private fun saveGroups(ctx: Context, o: JSONObject) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_GROUPS, o.toString()) }
    }

    private fun loadCodes(ctx: Context): JSONObject {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val s = sp.getString(KEY_CODES, "{}") ?: "{}"
        return JSONObject(s)
    }
    private fun saveCodes(ctx: Context, o: JSONObject) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_CODES, o.toString()) }
    }
}
