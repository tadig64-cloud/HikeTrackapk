package com.hikemvp.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * TrackStorage
 * ------------
 * - Copie chaque GPX importé dans un dossier privé de l'appli: files/tracks/
 * - Ce dossier *survit aux mises à jour* (il est supprimé seulement lors d'une désinstallation).
 * - Retourne le File de destination + un hash (SHA-256) pour dédupliquer si besoin.
 */
object TrackStorage {

    /** Dossier interne qui contient les GPX copiés par l'app (persiste après mise à jour). */
    fun tracksDir(context: Context): File {
        val dir = File(context.filesDir, "tracks")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    data class CopyResult(
        val file: File,
        val displayName: String,
        val sizeBytes: Long,
        val sha256: String
    )

    /**
     * Copie le contenu pointé par [source] (typiquement un Uri SAF) vers le stockage privé.
     * Si [preferredName] est null, on tente de deviner depuis le DocumentFile, sinon on génère un nom.
     */
    fun copyIntoPrivateStorage(
        context: Context,
        source: Uri,
        preferredName: String? = null
    ): CopyResult {
        val resolver = context.contentResolver
        val doc = DocumentFile.fromSingleUri(context, source)
        val display = preferredName ?: doc?.name ?: "track-${UUID.randomUUID()}.gpx"

        val inStream: InputStream = resolver.openInputStream(source)
            ?: error("Impossible d'ouvrir le flux d'entrée pour $source")

        // Calcul du hash pendant la copie
        val md = MessageDigest.getInstance("SHA-256")
        val target = File(tracksDir(context), ensureGpxExtension(display))

        FileOutputStream(target).use { out ->
            inStream.use { input ->
                val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                var read: Int
                var total = 0L
                while (true) {
                    read = input.read(buf)
                    if (read == -1) break
                    out.write(buf, 0, read)
                    md.update(buf, 0, read)
                    total += read
                }
                out.flush()
                return CopyResult(
                    file = target,
                    displayName = display,
                    sizeBytes = total,
                    sha256 = md.digest().toHex()
                )
            }
        }
    }

    /**
     * Calcule le hash SHA-256 d'un fichier existant (utile pour vérifier les doublons).
     */
    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = fis.read(buf)
                if (read == -1) break
                md.update(buf, 0, read)
            }
        }
        return md.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun ensureGpxExtension(name: String): String =
        if (name.lowercase().endsWith(".gpx")) name else "$name.gpx"
}
