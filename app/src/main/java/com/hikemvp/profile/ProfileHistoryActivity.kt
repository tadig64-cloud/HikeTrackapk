package com.hikemvp.profile

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import com.hikemvp.R
import com.hikemvp.history.GPXReader
import com.hikemvp.history.GpxHistoryAdapter
import com.hikemvp.history.TrackDetailActivity
import com.hikemvp.history.TrackInfo
import java.io.File
import java.io.FileOutputStream

class ProfileHistoryActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private lateinit var headerTotals: TextView
    private lateinit var adapter: GpxHistoryAdapter

    // Pastille de chargement (overlay)
    private lateinit var loadingChip: android.view.View

    // Master list & working list
    private var allItems: List<TrackInfo> = emptyList()
    private var elevOnly: Boolean = false
    private var sortMode: SortMode = SortMode.DATE_DESC
    private var query: String = ""

    private val importGpx = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri = res.data?.data ?: return@registerForActivityResult
            // Montre la pastille pendant la copie
            setLoading(true)
            try {
                val file = copyIntoAppTracks(uri)
                if (file != null) {
                    Snackbar.make(findViewById(android.R.id.content), getString(R.string.history_import_ok, file.name), Snackbar.LENGTH_SHORT).show()
                    loadTracks()
                } else {
                    Snackbar.make(findViewById(android.R.id.content), getString(R.string.history_import_fail), Snackbar.LENGTH_SHORT).show()
                }
            } finally {
                setLoading(false)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_history)

        toolbar = findViewById(R.id.topAppBar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        list = findViewById(R.id.recycler)
        empty = findViewById(R.id.emptyView)
        headerTotals = findViewById(R.id.headerTotals)
        loadingChip = findViewById(R.id.loadingChip)

        adapter = GpxHistoryAdapter(
            emptyList(),
            onClick = { track ->
                val it = Intent(this, TrackDetailActivity::class.java)
                it.putExtra(TrackDetailActivity.EXTRA_FILE_NAME, track.fileName)
                startActivity(it)
            },
            onLongPress = { track ->
                confirmDelete(track)
            }
        )
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        loadTracks()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_history, menu)
        menu?.findItem(R.id.action_history_filter_elev)?.isChecked = elevOnly

        val searchItem = menu?.findItem(R.id.action_history_search)
        val searchView = searchItem?.actionView as? SearchView
        searchView?.queryHint = getString(R.string.history_search_hint)
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                query = newText?.trim().orEmpty()
                applyFiltersAndSort()
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_history_import -> { pickGpx(); true }
            R.id.action_history_rescan -> { loadTracks(); true }
            R.id.action_history_sort -> { showSortDialog(); true }
            R.id.action_history_filter_elev -> {
                elevOnly = !elevOnly
                item.isChecked = elevOnly
                applyFiltersAndSort()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSortDialog() {
        val modes = arrayOf(
            getString(R.string.history_sort_date_desc),
            getString(R.string.history_sort_date_asc),
            getString(R.string.history_sort_dist_desc),
            getString(R.string.history_sort_dist_asc),
            getString(R.string.history_sort_gain_desc),
            getString(R.string.history_sort_gain_asc)
        )
        val currentIdx = when (sortMode) {
            SortMode.DATE_DESC -> 0
            SortMode.DATE_ASC -> 1
            SortMode.DIST_DESC -> 2
            SortMode.DIST_ASC -> 3
            SortMode.GAIN_DESC -> 4
            SortMode.GAIN_ASC -> 5
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.history_sort_title)
            .setSingleChoiceItems(modes, currentIdx) { dlg, which ->
                sortMode = when (which) {
                    0 -> SortMode.DATE_DESC
                    1 -> SortMode.DATE_ASC
                    2 -> SortMode.DIST_DESC
                    3 -> SortMode.DIST_ASC
                    4 -> SortMode.GAIN_DESC
                    5 -> SortMode.GAIN_ASC
                    else -> SortMode.DATE_DESC
                }
                applyFiltersAndSort()
                dlg.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private enum class SortMode { DATE_DESC, DATE_ASC, DIST_DESC, DIST_ASC, GAIN_DESC, GAIN_ASC }

    private fun tracksDir(): File {
        val dir = File(getExternalFilesDir(null), "Tracks")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun pickGpx() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/gpx+xml", "application/octet-stream", "text/xml", "application/xml"))
        }
        importGpx.launch(intent)
    }

    private fun copyIntoAppTracks(uri: Uri): File? {
        return try {
            val name = guessFileName(uri) ?: "track_${System.currentTimeMillis()}.gpx"
            val outFile = File(tracksDir(), ensureGpxExtension(name))
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Throwable) {}
            outFile
        } catch (_: Throwable) {
            null
        }
    }

    private fun ensureGpxExtension(name: String): String =
        if (name.lowercase().endsWith(".gpx")) name else "$name.gpx"

    private fun guessFileName(uri: Uri): String? {
        val last = uri.lastPathSegment ?: return null
        return last.substringAfterLast('/')
    }

    private fun loadTracks() {
        // Affiche la pastille pendant le scan/parse
        setLoading(true)
        try {
            val dir = tracksDir()
            val files = dir.listFiles { f -> f.isFile && f.name.lowercase().endsWith(".gpx") }?.toList().orEmpty()
            val items = ArrayList<TrackInfo>(files.size)
            for (f in files) {
                try {
                    contentResolver.openInputStream(Uri.fromFile(f))?.use { input ->
                        items.add(GPXReader.parseStream(this, input, f.name))
                    }
                } catch (_: Throwable) { /* skip bad file */ }
            }
            allItems = items
            applyFiltersAndSort()
        } finally {
            setLoading(false)
        }
    }

    private fun applyFiltersAndSort() {
        var items = allItems

        val q = query.lowercase()
        if (q.isNotEmpty()) {
            items = items.filter {
                it.name.lowercase().contains(q) || it.fileName.lowercase().contains(q)
            }
        }

        if (elevOnly) {
            items = items.filter { it.hasElevation }
        }

        items = when (sortMode) {
            SortMode.DATE_DESC -> items.sortedByDescending { it.startTimeMillis ?: 0L }
            SortMode.DATE_ASC -> items.sortedBy { it.startTimeMillis ?: 0L }
            SortMode.DIST_DESC -> items.sortedByDescending { it.distanceMeters }
            SortMode.DIST_ASC -> items.sortedBy { it.distanceMeters }
            SortMode.GAIN_DESC -> items.sortedByDescending { it.elevationGain }
            SortMode.GAIN_ASC -> items.sortedBy { it.elevationGain }
        }

        adapter.submit(items)

        val totalKm = items.sumOf { it.distanceMeters } / 1000.0
        val totalUp = items.sumOf { it.elevationGain }
        val totalDown = items.sumOf { it.elevationLoss }
        headerTotals.text = getString(R.string.history_totals, String.format("%.2f", totalKm), totalUp.toInt(), totalDown.toInt())

        val isEmpty = items.isEmpty()
        empty.visibility = if (isEmpty) android.view.View.VISIBLE else android.view.View.GONE
        list.visibility = if (isEmpty) android.view.View.GONE else android.view.View.VISIBLE
        headerTotals.visibility = if (isEmpty) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun confirmDelete(track: TrackInfo) {
        AlertDialog.Builder(this)
            .setTitle(R.string.history_delete_title)
            .setMessage(getString(R.string.history_delete_msg, track.fileName))
            .setPositiveButton(R.string.history_delete_confirm) { _, _ ->
                val src = File(tracksDir(), track.fileName)
                val bak = File(tracksDir(), track.fileName + ".bak")
                // Try rename (atomic on same FS); if fails, try copy/delete could be added
                val moved = if (src.exists()) src.renameTo(bak) else false
                if (moved) {
                    loadTracks()
                    val snack = Snackbar.make(findViewById(android.R.id.content), getString(R.string.history_deleted, track.fileName), Snackbar.LENGTH_LONG)
                    snack.setAction(R.string.undo) {
                        // restore
                        bak.renameTo(src)
                        loadTracks()
                    }
                    snack.addCallback(object : Snackbar.Callback() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                            // If not undone, delete backup
                            if (event != DISMISS_EVENT_ACTION) {
                                if (bak.exists()) bak.delete()
                            }
                        }
                    })
                    snack.show()
                } else {
                    Snackbar.make(findViewById(android.R.id.content), getString(R.string.history_delete_fail), Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // Montre/masque la pastille "chargement"
    private fun setLoading(loading: Boolean) {
        loadingChip.visibility = if (loading) android.view.View.VISIBLE else android.view.View.GONE
    }
}
