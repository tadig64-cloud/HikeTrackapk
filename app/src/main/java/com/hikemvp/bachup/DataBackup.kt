package com.hikemvp.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.IOException
import java.io.InputStream
import java.io.File
import java.io.FileOutputStream

object DataBackup {

    /** Sauvegarde des octets dans Téléchargements/HikeTrack/<fileName> via MediaStore. */
    fun saveBytesToDownloads(
        context: Context,
        fileName: String,
        bytes: ByteArray,
        mimeType: String
    ): Uri? {
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val relativePath = Environment.DIRECTORY_DOWNLOADS + "/HikeTrack"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        var itemUri: Uri? = null
        try {
            itemUri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(itemUri)?.use { os ->
                os.write(bytes)
                os.flush()
            } ?: return null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(itemUri, done, null, null)
            }
            return itemUri
        } catch (_: Throwable) {
            if (itemUri != null) {
                try { resolver.delete(itemUri, null, null) } catch (_: Throwable) {}
            }
            return null
        }
    }

    /** Sauvegarde un flux dans Images/HikeTrack[/subDir]/<fileName> via MediaStore. */
    fun saveStreamToPictures(
        context: Context,
        fileName: String,
        mimeType: String,
        input: InputStream,
        subDir: String = "HikeTrack"
    ): Uri? {
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val relPath = Environment.DIRECTORY_PICTURES + "/" + subDir.trim('/')

        // API < 29 (Q) fallback : tentative d'écriture directe publique (requiert permission sur anciens SDK)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return try {
                val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val dir = if (subDir.isNotEmpty()) File(base, subDir) else base
                if (!dir.exists()) dir.mkdirs()
                val outFile = File(dir, fileName)
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
                // Enregistrer quand même dans MediaStore pour visibilité galerie
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.DATA, outFile.absolutePath)
                }
                resolver.insert(collection, values)
            } catch (_: Throwable) { null }
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        var itemUri: Uri? = null
        try {
            itemUri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(itemUri)?.use { os ->
                input.copyTo(os)
                os.flush()
            } ?: return null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(itemUri, done, null, null)
            }
            return itemUri
        } catch (_: Throwable) {
            if (itemUri != null) {
                try { resolver.delete(itemUri, null, null) } catch (_: Throwable) {}
            }
            return null
        }
    }

    /** Variante utilitaire si tu as déjà les octets. */
    fun saveBytesToPictures(
        context: Context,
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
        subDir: String = "HikeTrack"
    ): Uri? {
        return try {
            bytes.inputStream().use { ins -> saveStreamToPictures(context, fileName, mimeType, ins, subDir) }
        } catch (_: Throwable) { null }
    }
}
