package com.hikemvp.waypoints

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * Restaure automatiquement les waypoints à partir du miroir public (Downloads/HikeTrack/hiketrack_waypoints.json)
 * si la base interne est vide (ex: après réinstallation).
 *
 * Intégration recommandée (une seule ligne) :
 *   WaypointAutoRestore.restoreIfEmpty(this)
 * à mettre très tôt (ex: dans MapActivity.onCreate juste après setContentView).
 *
 * Aucun changement d’UI, aucune permission runtime nécessaire (API 29+).
 */
object WaypointAutoRestore {

    fun restoreIfEmpty(context: Context) {
        // Si on a déjà des données, on ne fait rien.
        try {
            if (WaypointStorage.listFull(context).isNotEmpty()) return
        } catch (_: Throwable) {
            // En cas d'erreur de lecture locale, on tente quand même une restauration.
        }

        // Cherche le JSON miroir le plus récent via MediaStore (Downloads/HikeTrack).
        val resolver = context.contentResolver
        val downloads: Uri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val idCol = MediaStore.MediaColumns._ID
        val nameCol = MediaStore.MediaColumns.DISPLAY_NAME
        val pathCol = MediaStore.MediaColumns.RELATIVE_PATH
        val dateCol = MediaStore.MediaColumns.DATE_MODIFIED

        val projection = arrayOf(idCol, nameCol, pathCol, dateCol)
        val selection = "$nameCol=? AND $pathCol LIKE ?"
        val args = arrayOf("hiketrack_waypoints.json", "%Download/HikeTrack%")
        val sort = "$dateCol DESC"

        val fileUri: Uri = resolver.query(downloads, projection, selection, args, sort)?.use { c ->
            if (!c.moveToFirst()) return
            val id = c.getLong(c.getColumnIndexOrThrow(idCol))
            ContentUris.withAppendedId(downloads, id)
        } ?: return

        // Lit et importe
        resolver.openInputStream(fileUri)?.use { ins ->
            val text = ins.readBytes().toString(Charsets.UTF_8)
            val arr = JSONArray(text)
            var imported = 0
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val ext = jsonToWaypointExt(o) ?: continue
                try {
                    WaypointStorage.addFull(context, ext)
                    imported++
                } catch (_: Throwable) {
                    // on continue, best-effort
                }
            }
            // Si rien importé, on ne touche pas au stockage interne.
            // Sinon, tout a été ajouté via l'API publique de WaypointStorage.
        }
    }

    /** Conversion JSON -> WaypointExt en tolérant les vieux champs. */
    private fun jsonToWaypointExt(o: JSONObject): WaypointExt? {
        val id = if (o.has("id")) o.optLong("id") else System.currentTimeMillis()
        val lat = when {
            o.has("lat") -> o.optDouble("lat")
            o.has("latitude") -> o.optDouble("latitude")
            else -> return null
        }
        val lon = when {
            o.has("lon") -> o.optDouble("lon")
            o.has("longitude") -> o.optDouble("longitude")
            else -> return null
        }
        val name = o.optString("name", "Waypoint")
        val note = if (o.has("note")) o.optString("note") else null
        val group = if (o.has("group")) o.optString("group") else null
        val timestamp = if (o.has("timestamp")) o.optLong("timestamp") else System.currentTimeMillis()
        val attachments = mutableListOf<String>()
        if (o.has("attachments")) {
            val a = o.optJSONArray("attachments")
            if (a != null) for (j in 0 until a.length()) attachments.add(a.optString(j))
        }
        return WaypointExt(
            id = id,
            latitude = lat,
            longitude = lon,
            name = name,
            note = note,
            group = group,
            timestamp = timestamp,
            attachments = attachments
        )
    }
}