package com.hikemvp.media

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

object PhotoUtils {

    @JvmStatic
    fun takePersistablePermission(context: Context, uri: Uri) {
        if ("content".equals(uri.scheme, ignoreCase = true)) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Not a document URI or no persistable permission available.
            } catch (_: Throwable) {
                // Ignore
            }
        }
    }

    @JvmStatic
    fun viewImage(context: Context, uri: Uri) {
        val mime = try {
            context.contentResolver.getType(uri) ?: "image/*"
        } catch (_: Throwable) {
            "image/*"
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Grant read permission to all potential receivers (avoids SecurityException on some OEMs)
        try {
            val resInfo = context.packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            for (ri in resInfo) {
                try {
                    context.grantUriPermission(
                        ri.activityInfo.packageName,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Throwable) { /* ignore */ }
            }
        } catch (_: Throwable) { /* ignore */ }

        context.startActivity(intent)
    }

    @JvmStatic
    fun parseUri(value: String?): Uri? = value?.let {
        try { Uri.parse(it) } catch (_: Throwable) { null }
    }
}
