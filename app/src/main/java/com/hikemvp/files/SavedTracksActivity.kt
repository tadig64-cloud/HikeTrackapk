package com.hikemvp.files

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.hikemvp.R
import com.hikemvp.gpx.GpxStorage
import java.io.File
import java.text.DateFormat
import java.util.*

class SavedTracksActivity : AppCompatActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var adapter: TracksAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_tracks)

        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setSupportActionBar(this)
            setNavigationOnClickListener { finish() }
        }

        rv = findViewById(R.id.rvTracks)
        rv.layoutManager = LinearLayoutManager(this)
        rv.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        adapter = TracksAdapter(
            onOpen = { openFile(it) },
            onShare = { shareFile(it) },
            onDelete = { deleteFileConfirm(it) }
        )
        rv.adapter = adapter

        loadFiles()
    }

    override fun onResume() {
        super.onResume()
        // Si on revient après un enregistrement, on recharge la liste
        loadFiles()
    }

    /** Répertoire unique des GPX (exactement le même que le service) */
    private fun gpxDir(): File = GpxStorage.gpxDir(this)

    private fun loadFiles() {
        val dir = gpxDir().apply { mkdirs() }
        val items = dir.listFiles { f -> f.isFile && f.name.lowercase(Locale.ROOT).endsWith(".gpx") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
        adapter.submit(items)
        if (items.isEmpty()) {
            Toast.makeText(this, getString(R.string.saved_tracks_empty), Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFile(f: File) {
        if (!f.exists()) {
            Toast.makeText(this, "Fichier introuvable.", Toast.LENGTH_SHORT).show()
            return
        }

        val uri: Uri = try {
            androidx.core.content.FileProvider.getUriForFile(
                this, "${packageName}.fileprovider", f
            )
        } catch (t: Throwable) {
            Toast.makeText(this, "Impossible d’ouvrir ce fichier (URI).", Toast.LENGTH_LONG).show()
            return
        }

        // On propose d'abord l'ouvre-fichier GPX, sinon fallback text/xml, sinon */*
        val candidates = listOf(
            "application/gpx+xml",
            "text/xml",
            "*/*"
        )

        for (mime in candidates) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT) // évite de revenir sur l’appli si le viewer crash
            }

            // Vérifie qu'au moins une app peut gérer ce type avant de lancer
            val pm = packageManager
            val res = pm.queryIntentActivities(intent, 0)
            if (!res.isNullOrEmpty()) {
                // accorde la permission de lecture explicitement (par prudence)
                res.forEach { ri ->
                    grantUriPermission(ri.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, getString(R.string.share_gpx)))
                return
            }
        }

        Toast.makeText(this, getString(R.string.err_no_picker), Toast.LENGTH_LONG).show()
    }

    private fun shareFile(f: File) {
        val uri: Uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.share_gpx)))
    }

    private fun deleteFileConfirm(f: File) {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_clear_title)
            .setMessage(getString(R.string.confirm_clear_msg))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (f.delete()) {
                    Toast.makeText(this, getString(R.string.ht_cleared), Toast.LENGTH_SHORT).show()
                    loadFiles()
                } else {
                    Toast.makeText(this, "Suppression impossible.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private inner class TracksAdapter(
        val onOpen: (File) -> Unit,
        val onShare: (File) -> Unit,
        val onDelete: (File) -> Unit,
    ) : RecyclerView.Adapter<TracksAdapter.VH>() {

        private var data: List<File> = emptyList()

        fun submit(list: List<File>) {
            data = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.row_saved_track, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(data[position])
        }

        override fun getItemCount(): Int = data.size

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            private val tvName: TextView = v.findViewById(R.id.tvName)
            private val tvMeta: TextView = v.findViewById(R.id.tvMeta)
            private val btnOpen: MaterialButton = v.findViewById(R.id.btnOpen)
            private val btnShare: MaterialButton = v.findViewById(R.id.btnShare)
            private val btnDelete: MaterialButton = v.findViewById(R.id.btnDelete)

            fun bind(f: File) {
                tvName.text = f.name
                tvMeta.text = metaFor(f)
                btnOpen.setOnClickListener { onOpen(f) }
                btnShare.setOnClickListener { onShare(f) }
                btnDelete.setOnClickListener { onDelete(f) }
                btnOpen.isVisible = true
            }

            private fun metaFor(f: File): String {
                val size = android.text.format.Formatter.formatShortFileSize(itemView.context, f.length())
                val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(f.lastModified()))
                return "$date • $size"
            }
        }
    }
}
