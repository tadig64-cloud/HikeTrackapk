package com.hikemvp.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Écrit des sauvegardes "miroir" durables dans Téléchargements/HikeTrack/.
 * - Sauvegardes GPX: un fichier par enregistrement/export (ex: track_2025-03-03_14-22-10.gpx)
 * - Waypoints: un seul JSON cumulatif "hiketrack_waypoints.json" (liste complète)
 *
 * Pas d'UI, pas de permission runtime sur API 29+ (scoped storage via MediaStore).
 */
object PublicMirror {

    private const val DIR = "Download/HikeTrack/"
    private const val TAG = "PublicMirror"

    /** Écrit/écrase le JSON complet des waypoints. */
    fun writeWaypointsJson(context: Context, waypoints: List<WaypointLike>) {
        val arr = JSONArray()
        for (w in waypoints) {
            val o = JSONObject().apply {
                put("id", w.id)
                put("lat", w.latitude)
                put("lon", w.longitude)
                put("name", w.name ?: "Waypoint")
                if (!w.note.isNullOrBlank()) put("note", w.note)
                if (!w.group.isNullOrBlank()) put("group", w.group)
                put("timestamp", w.timestamp ?: System.currentTimeMillis())
                if (w.attachments?.isNotEmpty() == true) {
                    put("attachments", JSONArray(w.attachments))
                }
            }
            arr.put(o)
        }
        val bytes = arr.toString(2).toByteArray(Charsets.UTF_8)
        writeToDownloads(
            context = context,
            displayName = "hiketrack_waypoints.json",
            mime = "application/json",
            bytes = bytes,
            overwrite = true
        )
    }

    /** Écrit une sauvegarde GPX (un fichier par enregistrement/export). */
    fun writeGpx(context: Context, displayName: String, bytes: ByteArray) {
        // On ne force pas l’overwrite par défaut pour garder l’historique
        writeToDownloads(
            context = context,
            displayName = displayName.ensureGpxExtension(),
            mime = "application/gpx+xml",
            bytes = bytes,
            overwrite = false
        )
    }

    // --- Impl MediaStore ---

    private fun writeToDownloads(
        context: Context,
        displayName: String,
        mime: String,
        bytes: ByteArray,
        overwrite: Boolean
    ) {
        try {
            val resolver = context.contentResolver
            val collection: Uri =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Files.getContentUri("external")
                }

            // Si overwrite demandé, on tente de supprimer l’existant même nom/chemin.
            if (overwrite) {
                val where = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
                val args = arrayOf(displayName, DIR)
                try { resolver.delete(collection, where, args) } catch (_: Throwable) {}
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, DIR)
                }
            }

            val itemUri = resolver.insert(collection, values) ?: return
            resolver.openOutputStream(itemUri, "w")?.use { it.write(bytes) }
        } catch (e: IOException) {
            Log.w(TAG, "writeToDownloads failed for $displayName", e)
        } catch (t: Throwable) {
            Log.w(TAG, "writeToDownloads unexpected error for $displayName", t)
        }
    }

    private fun String.ensureGpxExtension(): String =
        if (lowercase().endsWith(".gpx")) this else "$this.gpx"
}

/**
 * Interface minimale pour ne pas dépendre des classes concrètes du module waypoints.
 * Implémente-la via un adaptateur dans WaypointStorage.
 */
interface WaypointLike {
    val id: Long
    val latitude: Double
    val longitude: Double
    val name: String?
    val note: String?
    val group: String?
    val timestamp: Long?
    val attachments: List<String>?
}
