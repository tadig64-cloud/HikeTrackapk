package com.hikemvp

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hikemvp.gpx.GpxStorage
import java.io.File
import java.io.FileOutputStream

/**
 * Activity "tampon" pour ouvrir un fichier GPX reçu depuis :
 * - un gestionnaire de fichiers (ACTION_VIEW / ACTION_EDIT)
 * - un partage (ACTION_SEND / ACTION_SEND_MULTIPLE)
 *
 * ⚠️ IMPORTANT (robustesse Android) :
 * Les fichiers venant d'Internet / du gestionnaire de fichiers arrivent souvent en `content://...`
 * et MapActivity (ou d'autres écrans) peuvent ne pas aimer ce format.
 *
 * Donc ici :
 * - on tente de copier le fichier dans le dossier GPX de l'app
 * - on redirige vers MapActivity avec :
 *   - data = Uri d'origine
 *   - EXTRA_GPX_URI = Uri d'origine en string
 *   - EXTRA_GPX_PATH = chemin local copié (si la copie a réussi)
 */
class GpxOpenActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)
        finish() // on ne garde pas cet écran
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) handleIncomingIntent(intent)
        finish()
    }

    private fun handleIncomingIntent(inIntent: Intent) {
        val uri = extractUri(inIntent)

        if (uri == null) {
            Toast.makeText(this, "Aucun fichier GPX détecté.", Toast.LENGTH_LONG).show()
            Log.w(TAG, "No Uri found in incoming intent: action=${inIntent.action}")
            return
        }

        // Essai de conserver l'autorisation de lecture si c'est un Uri persistable (DocumentsProvider)
        try {
            val takeFlags = inIntent.flags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            if (takeFlags != 0) {
                contentResolver.takePersistableUriPermission(uri, takeFlags)
            }
        } catch (_: Throwable) {
            // pas grave
        }

        // Tente de copier le contenu dans le dossier des GPX de l'app (robuste)
        val localPath = tryCopyToAppGpxFolder(uri)

        // Redirection vers la carte
        val openMap = Intent(this, MapActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uri

            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            // Extras "compat"
            putExtra(EXTRA_GPX_URI, uri.toString())
            if (!localPath.isNullOrBlank()) {
                putExtra(EXTRA_GPX_PATH, localPath)
            }
            @Suppress("DEPRECATION")
            putExtra(Intent.EXTRA_STREAM, uri)
        }

        try {
            startActivity(openMap)
        } catch (t: Throwable) {
            Toast.makeText(this, "Impossible d'ouvrir la trace dans HikeTrack.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Failed to start MapActivity with uri=$uri localPath=$localPath", t)
        }
    }

    /**
     * Copie le fichier pointé par un Uri (souvent content://) dans un dossier GPX
     * appartenant à l'app. Renvoie le chemin local si succès, sinon null.
     */
    private fun tryCopyToAppGpxFolder(uri: Uri): String? {
        return try {
            // Si déjà un fichier direct, on ne copie pas (on garde le path)
            if (uri.scheme == "file") {
                return uri.path
            }

            val displayName = queryDisplayName(uri)
            val safeName = (displayName ?: "import_${System.currentTimeMillis()}.gpx")
                .replace(Regex("[^a-zA-Z0-9._ -]"), "_")

            val folder = GpxStorage.gpxDir(this@GpxOpenActivity).apply { mkdirs() }
            var outFile = File(folder, safeName)

            // évite d'écraser
            if (outFile.exists()) {
                val base = safeName.substringBeforeLast('.', safeName)
                val ext = safeName.substringAfterLast('.', "gpx")
                outFile = File(folder, "${base}_${System.currentTimeMillis()}.$ext")
            }

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            outFile.absolutePath
        } catch (t: Throwable) {
            Log.w(TAG, "Copy to app GPX folder failed for uri=$uri", t)
            null
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        var c: Cursor? = null
        return try {
            c = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (c != null && c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx) else null
            } else null
        } catch (_: Throwable) {
            null
        } finally {
            try { c?.close() } catch (_: Throwable) {}
        }
    }

    private fun extractUri(inIntent: Intent): Uri? {
        return when (inIntent.action) {
            Intent.ACTION_VIEW,
            Intent.ACTION_EDIT -> inIntent.data

            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                inIntent.getParcelableExtra(Intent.EXTRA_STREAM)
                    ?: inIntent.data
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                val list = inIntent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                list?.firstOrNull() ?: inIntent.data
            }

            else -> inIntent.data
        }
    }

    companion object {
        private const val TAG = "GpxOpenActivity"

        const val EXTRA_GPX_URI = "com.hikemvp.extra.GPX_URI"
        const val EXTRA_GPX_PATH = "com.hikemvp.extra.GPX_PATH"
    }
}
