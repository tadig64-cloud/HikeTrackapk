package com.hikemvp.utils

import android.content.Context
import org.osmdroid.config.Configuration
import java.io.File

/**
 * Gestion robuste du stockage osmdroid (Scoped Storage friendly).
 * - Par défaut, l'app peut pointer sur externalFilesDir/osmdroid
 * - Si l'I/O échoue (policy FUSE / ioctl denied), on bascule en stockage interne (cacheDir).
 * - Aucune permission spéciale requise.
 */
object OsmdroidStorage {

    /** Vérifie que le dossier tuile est réellement accessible en écriture, sinon bascule en interne. */
    fun ensureWritable(context: Context) {
        val cfg = Configuration.getInstance()
        val tiles = cfg.osmdroidTileCache
        val ok = try {
            if (tiles == null) false else {
                if (!tiles.exists()) tiles.mkdirs()
                val probe = File(tiles, ".probe")
                probe.writeText("ok")
                probe.delete()
                true
            }
        } catch (_: Throwable) { false }
        if (!ok) {
            initInternal(context)
        }
    }

    /** Force l'usage d'un cache interne (évite FUSE/ioctl). */
    fun initInternal(context: Context) {
        val cfg = Configuration.getInstance()
        val base = File(context.cacheDir, "osmdroid")
        val tiles = File(base, "tiles")
        try {
            if (!tiles.exists()) tiles.mkdirs()
        } catch (_: Throwable) {}
        cfg.osmdroidBasePath = base
        cfg.osmdroidTileCache = tiles

        // Optionnel: plafonds raisonnables (50–150 Mo)
        val fiftyMo = 50L * 1024L * 1024L
        val hundredMo = 100L * 1024L * 1024L
        runCatching {
            cfg.tileFileSystemCacheMaxBytes = hundredMo
            cfg.tileFileSystemCacheTrimBytes = fiftyMo
        }
    }
}
