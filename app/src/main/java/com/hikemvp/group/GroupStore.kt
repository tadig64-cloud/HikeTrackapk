package com.hikemvp.group

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.io.File

/**
 * Persistance simple des membres (id, lat, lon) en JSON dans le stockage interne.
 */
object GroupStore {
    private const val FILE_NAME = "group_members.json"

    fun load(context: Context): Map<String, GeoPoint> {
        return try {
            val f = File(context.filesDir, FILE_NAME)
            if (!f.exists()) return emptyMap()
            val txt = f.readText()
            val arr = JSONArray(txt)
            val map = LinkedHashMap<String, GeoPoint>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.getString("id")
                val lat = o.getDouble("lat")
                val lon = o.getDouble("lon")
                map[id] = GeoPoint(lat, lon)
            }
            map
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    fun save(context: Context, members: Map<String, GeoPoint>?) {
        if (members == null) return
        try {
            val arr = JSONArray()
            for ((id, gp) in members) {
                val o = JSONObject()
                o.put("id", id)
                o.put("lat", gp.latitude)
                o.put("lon", gp.longitude)
                arr.put(o)
            }
            val f = File(context.filesDir, FILE_NAME)
            f.writeText(arr.toString())
        } catch (_: Throwable) {
            // ignore
        }
    }


    fun clear(context: Context) {
        val f = File(context.filesDir, FILE_NAME)
        if (f.exists()) f.delete()
    }
}
