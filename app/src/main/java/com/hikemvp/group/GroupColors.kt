package com.hikemvp.group

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONObject
import kotlin.math.abs

/**
 * GroupColors — version avec API compatible (get/set/remove/clear) et fonctions modernes.
 * Persistance JSON dans SharedPreferences.
 */
object GroupColors {
    private const val PREF_KEY = "group_custom_colors_json"

    private var loaded = false
    private val cache = LinkedHashMap<String, Int>()

    private fun prefs(ctx: Context) = PreferenceManager.getDefaultSharedPreferences(ctx)

    private fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val s = prefs(ctx).getString(PREF_KEY, null) ?: return
        runCatching {
            val obj = JSONObject(s)
            val it = obj.keys()
            while (it.hasNext()) {
                val k = it.next()
                cache[k] = obj.optInt(k)
            }
        }
    }

    private fun persist(ctx: Context) {
        val obj = JSONObject()
        cache.forEach { (k, v) -> obj.put(k, v) }
        prefs(ctx).edit().putString(PREF_KEY, obj.toString()).apply()
    }

    // ---- API moderne ----
    fun colorFor(context: Context, memberId: String, seedName: String? = null): Int {
        load(context)
        cache[memberId]?.let { return it }
        return autoColor(memberId + (seedName ?: ""))
    }

    // ---- API historique (compat) ----
    fun get(context: Context, memberId: String): Int {
        load(context)
        return cache[memberId] ?: autoColor(memberId)
    }

    fun set(context: Context, memberId: String, color: Int) {
        load(context)
        cache[memberId] = color
        persist(context)
    }

    fun remove(context: Context, memberId: String) {
        load(context)
        if (cache.remove(memberId) != null) {
            persist(context)
        }
    }

    fun clear(context: Context) {
        load(context)
        cache.clear()
        prefs(context).edit().remove(PREF_KEY).apply()
    }

    fun all(context: Context): Map<String, Int> {
        load(context)
        return LinkedHashMap(cache)
    }

    // ---- Générateur de couleur stable (fallback) ----
    private fun autoColor(seed: String): Int {
        val h = seed.hashCode()
        val r = 80 + (h and 0x7F)
        val g = 80 + ((h shr 7) and 0x7F)
        val b = 80 + ((h shr 14) and 0x7F)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
