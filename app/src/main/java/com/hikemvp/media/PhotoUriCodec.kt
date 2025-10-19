package com.hikemvp.media

import android.content.SharedPreferences
import org.json.JSONArray

/**
 * Stocke/charge une liste d'URI photo en **String JSON** dans SharedPreferences, pour éviter
 * les ClassCastException (ex: Boolean/HashSet -> String).
 */
object PhotoUriCodec {

    fun encode(list: List<String>): String = JSONArray(list).toString()

    fun decode(maybeJson: String?): List<String> {
        if (maybeJson.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(maybeJson)
            (0 until arr.length()).mapNotNull { idx ->
                arr.optString(idx, null)
            }
        }.getOrElse { emptyList() }
    }

    /**
     * getString() peut lever une ClassCastException si une ancienne version a stocké
     * un autre type (Boolean, Set<String>, etc.). On absorbe et on migre silencieusement.
     */
    fun getList(prefs: SharedPreferences, key: String): List<String> {
        val raw: String? = try {
            prefs.getString(key, null)
        } catch (_: ClassCastException) {
            // Migration légère : on supprime la mauvaise clé et on repart proprement.
            prefs.edit().remove(key).apply()
            null
        } catch (_: Throwable) {
            null
        }
        return decode(raw)
    }

    fun putList(prefs: SharedPreferences, key: String, list: List<String>) {
        prefs.edit().putString(key, encode(list)).apply()
    }
}
