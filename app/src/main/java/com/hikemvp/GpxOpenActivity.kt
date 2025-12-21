package com.hikemvp

import android.content.ClipData
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hikemvp.gpx.GpxStorage
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Activity "tampon" pour ouvrir un fichier reçu depuis :
 * - un gestionnaire de fichiers (ACTION_VIEW)
 * - un partage (ACTION_SEND)
 *
 * Objectif : ne JAMAIS bloquer l'UI (sinon écran noir / ANR), copier le fichier
 * dans le dossier des GPX de l'app (si possible), puis rediriger vers MapActivity.
 */
class GpxOpenActivity : AppCompatActivity() {

    private var handledKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mini UI de chargement (évite l'écran noir pendant la copie)
        setContentView(makeLoadingView())

        handleIncomingIntentAsync(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntentAsync(intent)
    }

    private fun makeLoadingView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)

            addView(ProgressBar(this@GpxOpenActivity))
            addView(TextView(this@GpxOpenActivity).apply {
                text = "Import en cours…"
                textSize = 16f
                setPadding(0, 24, 0, 0)
            })
        }
    }

    private fun handleIncomingIntentAsync(inIntent: Intent) {
        val uri = extractUri(inIntent)
        val key = uri?.toString() ?: "no_uri"
        if (handledKey == key) return
        handledKey = key

        kotlin.concurrent.thread {
            if (uri == null) {
                runOnUiThread {
                    Toast.makeText(this, "Aucun fichier détecté.", Toast.LENGTH_LONG).show()
                    finish()
                }
                return@thread
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

            // Copie (si possible) dans le dossier GPX de l'app
            val localPath = tryCopyToAppGpxFolder(uri)

            // Redirection vers la carte
            runOnUiThread {
                val openMap = Intent(this, MapActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = uri
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

                    // Important : propager l'autorisation Uri vers MapActivity
                    clipData = ClipData.newUri(contentResolver, "open", uri)

                    if (!localPath.isNullOrBlank()) {
                        putExtra(EXTRA_GPX_PATH, localPath)
                    }
                    putExtra(EXTRA_GPX_URI, uri.toString())
                }

                startActivity(openMap)
                finish()
            }
        }
    }

    private fun tryCopyToAppGpxFolder(uri: Uri): String? {
        return try {
            val displayName = queryDisplayName(uri) ?: "import.gpx"
            val safeName = sanitizeFileName(displayName)

            val folder = GpxStorage.gpxDir(this@GpxOpenActivity).apply { mkdirs() }
            var outFile = File(folder, safeName)

            // évite d'écraser
            if (outFile.exists()) {
                val base = safeName.substringBeforeLast('.', safeName)
                val ext = safeName.substringAfterLast('.', "gpx")
                outFile = File(folder, "${base}_${System.currentTimeMillis()}.$ext")
            }

            // Copie stream -> fichier local (gros buffer pour limiter le temps)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output, 128 * 1024)
                }
            } ?: return null

            // sanity check : si vide, on ignore
            runCatching {
                if (outFile.length() == 0L) return null
                // tentative de lecture rapide (évite un fichier "corrompu")
                FileInputStream(outFile).use { /* ok */ }
            }

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

    private fun sanitizeFileName(name: String): String {
        // Empêche caractères interdits selon FS
        return name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifBlank { "import.gpx" }
    }

    private fun extractUri(inIntent: Intent): Uri? {
        return when (inIntent.action) {
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                (inIntent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
                    ?: inIntent.clipData?.let { cd -> if (cd.itemCount > 0) cd.getItemAt(0).uri else null }
                    ?: inIntent.data
            }

            Intent.ACTION_VIEW -> {
                // Certains gestionnaires utilisent clipData
                inIntent.clipData?.let { cd -> if (cd.itemCount > 0) cd.getItemAt(0).uri else null }
                    ?: inIntent.data
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
