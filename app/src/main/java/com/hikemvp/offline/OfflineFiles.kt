package com.hikemvp.offline

import android.content.Context
import android.os.Environment
import java.io.File

object OfflineFiles {

    fun offlineDir(context: Context): File {
        val root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val dir = File(root, "HikeTrack/offline")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun listMbtiles(context: Context): List<File> =
        offlineDir(context).listFiles { f ->
            f.isFile && (f.name.endsWith(".mbtiles", true) || f.name.endsWith(".sqlite", true))
        }?.sortedBy { it.name.lowercase() } ?: emptyList()
}