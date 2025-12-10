package com.hikemvp.io

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast

class ImportExportController(private val activity: Activity) {
    fun startImportChooser() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/gpx+xml",
                "application/vnd.google-earth.kml+xml",
                "application/zip",
                "application/geo+json",
                "application/json",
                "text/csv","text/tab-separated-values","text/plain",
                "application/vnd.garmin.tcx+xml",
                "application/octet-stream"
            ))
        }
        try { activity.startActivityForResult(intent, 41001) }
        catch (_: Throwable) { Toast.makeText(activity, "Importer: indisponible", Toast.LENGTH_SHORT).show() }
    }
    fun startExportChooser() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_TITLE, "HikeTrack_"+System.currentTimeMillis()+".gpx")
        }
        try { activity.startActivityForResult(intent, 42001) }
        catch (_: Throwable) { Toast.makeText(activity, "Exporter: indisponible", Toast.LENGTH_SHORT).show() }
    }
    fun importFromUri(uri: Uri) {
        Toast.makeText(activity, "Import: "+uri, Toast.LENGTH_SHORT).show()
    }
    fun exportToUri(uri: Uri) {
        Toast.makeText(activity, "Export: "+uri, Toast.LENGTH_SHORT).show()
    }
}
