@file:Suppress("DEPRECATION")
package com.hikemvp.utils

import android.content.Context
import android.os.Environment
import android.media.MediaScannerConnection
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.channels.FileChannel

/**
 * Utilities for creating a public backup of user data (GPX, waypoints, etc.).
 * These helpers are additive and do not change existing code paths unless you call them.
 *
 * Backups are written under:  Documents/HikeTrack/{Tracks|Waypoints|Other}
 * Storing files here makes them survive app uninstall.
 */
object BackupUtils {

    private const val ROOT_DIR_NAME = "HikeTrack"
    private const val TRACKS_DIR_NAME = "Tracks"
    private const val WAYPOINTS_DIR_NAME = "Waypoints"
    private const val OTHER_DIR_NAME = "Other"

    /** Returns the public 'Documents/HikeTrack' directory. */
    @JvmStatic
    fun getPublicRoot(context: Context): File {
        val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        return File(docs, ROOT_DIR_NAME)
    }

    /** Returns a subdirectory (Tracks/Waypoints/Other) under Documents/HikeTrack and ensures it exists. */
    @JvmStatic
    fun ensurePublicSubdir(context: Context, subdir: String): File {
        val root = getPublicRoot(context)
        val out = File(root, subdir)
        if (!out.exists()) out.mkdirs()
        return out
    }

    /** Convenience: the public Tracks dir. */
    @JvmStatic
    fun ensureTracksDir(context: Context): File = ensurePublicSubdir(context, TRACKS_DIR_NAME)

    /** Convenience: the public Waypoints dir. */
    @JvmStatic
    fun ensureWaypointsDir(context: Context): File = ensurePublicSubdir(context, WAYPOINTS_DIR_NAME)

    /**
     * Mirror (copy) a file into a public backup directory.
     * Fails silently (with a Toast) if scoped storage denies direct File access on newer Android,
     * but will still not crash your app.
     */
    @JvmStatic
    fun mirrorToPublic(
        context: Context,
        source: File,
        subdir: String,
        targetName: String = source.name
    ): Boolean {
        return runCatching {
            if (!source.exists() || !source.canRead()) return false

            val dstDir = ensurePublicSubdir(context, subdir)
            val dst = File(dstDir, targetName)

            copyFile(source, dst)
            // Let media providers discover the file (gallery / file managers)
            MediaScannerConnection.scanFile(context, arrayOf(dst.absolutePath), null, null)
            true
        }.onFailure {
            // Keep quiet but helpful during tests
            Toast.makeText(context, "Backup (public) failed: " + it.message, Toast.LENGTH_SHORT).show()
        }.getOrDefault(false)
    }

    /** Low level file copy using channels (fast & simple). */
    private fun copyFile(from: File, to: File) {
        if (!to.parentFile.exists()) to.parentFile.mkdirs()
        FileInputStream(from).channel.use { inChan: FileChannel ->
            FileOutputStream(to).channel.use { outChan: FileChannel ->
                inChan.transferTo(0, inChan.size(), outChan)
            }
        }
    }

    /**
     * Show a confirmation dialog before deleting user data.
     * If the user accepts, [onConfirm] is invoked.
     */
    @JvmStatic
    fun confirmDeletion(
        context: Context,
        @StringRes titleRes: Int,
        @StringRes messageRes: Int,
        onConfirm: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle(titleRes)
            .setMessage(messageRes)
            .setPositiveButton(android.R.string.ok) { d, _ ->
                d.dismiss()
                onConfirm()
            }
            .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
            .show()
    }
}