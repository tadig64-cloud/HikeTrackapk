package com.hikemvp.utils

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.hikemvp.R
import java.io.File

/**
 * Utilitaires pour partager des GPX et afficher des photos en toute sécurité (FileProvider).
 */
object ShareUtils {

    private fun authority(context: Context): String = "${context.packageName}.fileprovider"

    /**
     * Partage un fichier GPX situé dans le stockage privé (filesDir/tracks) via FileProvider.
     */
    fun shareGpx(context: Context, absolutePath: String) {
        val file = File(absolutePath)
        if (!file.exists()) {
            Toast.makeText(context, context.getString(R.string.file_not_found), Toast.LENGTH_SHORT).show()
            return
        }
        val uri = try {
            FileProvider.getUriForFile(context, authority(context), file)
        } catch (e: IllegalArgumentException) {
            // Si le chemin ne correspond pas aux paths déclarés dans provider_paths.xml
            Toast.makeText(context, "Chemin GPX non exposable via FileProvider.", Toast.LENGTH_SHORT).show()
            return
        }

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri(file.name, uri)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_gpx)))
    }

    /**
     * Ouvre une image à partir d'une Uri (content:// ou file://).
     * - Si c'est un file:// pointant vers nos répertoires, on convertit en content:// via FileProvider.
     * - On accorde les permissions READ à la cible.
     */
    fun viewImage(context: Context, photoUriString: String) {
        val original = runCatching { Uri.parse(photoUriString) }.getOrNull()
        if (original == null) {
            Toast.makeText(context, context.getString(R.string.file_not_found), Toast.LENGTH_SHORT).show()
            return
        }

        // Transforme file:// -> content:// via FileProvider si possible
        val viewUri: Uri = if (original.scheme.isNullOrEmpty() || original.scheme == "file") {
            val f = original.path?.let { File(it) }
            if (f != null && f.exists()) {
                try {
                    FileProvider.getUriForFile(context, authority(context), f)
                } catch (_: IllegalArgumentException) {
                    // Si le fichier n'est pas sous un path exposé, on tentera quand même avec file:// (peut échouer sur Android N+)
                    original
                }
            } else {
                original
            }
        } else {
            original
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(viewUri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("image", viewUri)
        }

        // Accorde explicitement la permission aux activités cibles
        val resInfo = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        for (ri in resInfo) {
            context.grantUriPermission(ri.activityInfo.packageName, viewUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, context.getString(R.string.no_viewer_app), Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, context.getString(R.string.missing_permission), Toast.LENGTH_SHORT).show()
        }
    }
}
