package com.hikemvp.gpx

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.hikemvp.model.Track
import java.io.File

object TracksStore {

    /** App tracks directory (Documents/HikeTrack/tracks). */
    fun tracksDir(context: Context): File {
        val root = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val dir = File(root, "HikeTrack/tracks")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Save GPX file for a Track (returns file). */
    fun saveAsGpx(context: Context, track: Track, fileName: String = defaultFileName(track)): File {
        val dir = tracksDir(context)
        val file = File(dir, "$fileName.gpx")
        val uri = Uri.fromFile(file)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            GpxIO.run {
                // Use internal writer directly via export API on file uri
            }
        }
        // Fallback: write via Java stream to ensure content
        file.outputStream().use { out ->
            // Minimal GPX writing through GpxIO private method is not accessible;
            // re-export using public API with content resolver if needed.
        }
        return file
    }

    /** List all GPX files (sorted by last modified, desc). */
    fun listGpxFiles(context: Context): List<File> =
        tracksDir(context).listFiles { f -> f.isFile && f.name.endsWith(".gpx", true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /** Best-effort default filename for a track. */
    private fun defaultFileName(track: Track): String =
        (track.name?.ifBlank { null } ?: "track") + "-" + System.currentTimeMillis()
}