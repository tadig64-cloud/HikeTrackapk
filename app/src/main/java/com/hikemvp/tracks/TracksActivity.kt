package com.hikemvp.tracks

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.hikemvp.R
import java.io.File

class TracksActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val files = mutableListOf<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tracks)

        listView = findViewById(R.id.tracks_list)

        // Charge quelques emplacements classiques (à adapter à ton projet)
        val candidates = listOf(
            File(getExternalFilesDir(null), "tracks"),
            File(getExternalFilesDir(null), "gpx"),
            File(getExternalFilesDir(null), "Download/HikeTrack/gpx")
        )

        files.clear()
        for (dir in candidates) {
            if (dir != null && dir.exists() && dir.isDirectory) {
                dir.listFiles { f -> f.isFile && (f.name.endsWith(".gpx", true) || f.name.endsWith(".kml", true)) }
                    ?.let { files.addAll(it) }
            }
        }

        val names = files.map { it.name }
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val f = files[position]
            askOpenOrShare(f)
        }
    }

    private fun askOpenOrShare(file: File) {
        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setMessage(getString(R.string.tracks_open_placeholder))
            .setPositiveButton(R.string.open) { d, _ ->
                d.dismiss()
                openFile(file)
            }
            .setNegativeButton(R.string.share) { d, _ ->
                d.dismiss()
                shareFile(file)
            }
            .setNeutralButton(R.string.cancel) { d, _ -> d.dismiss() }
            .show()
    }

    private fun openFile(file: File) {
        val uri = Uri.fromFile(file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, when {
                file.name.endsWith(".gpx", true) -> "application/gpx+xml"
                file.name.endsWith(".kml", true) -> "application/vnd.google-earth.kml+xml"
                else -> "*/*"
            })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(intent) }
            .onFailure {
                AlertDialog.Builder(this)
                    .setMessage(getString(R.string.open) + " : " + file.name)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            }
    }

    private fun shareFile(file: File) {
        val uri = Uri.fromFile(file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (file.name.endsWith(".gpx", true)) "application/gpx+xml"
                   else "application/vnd.google-earth.kml+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_tracks, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_open -> {
                if (files.isNotEmpty()) openFile(files.first())
                return true
            }
            R.id.action_share -> {
                if (files.isNotEmpty()) shareFile(files.first())
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}