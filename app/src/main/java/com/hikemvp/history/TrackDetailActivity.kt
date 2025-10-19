package com.hikemvp.history

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.hikemvp.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import java.io.File

class TrackDetailActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private var fileName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_track_detail)

        toolbar = findViewById(R.id.topAppBar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        fileName = intent.getStringExtra(EXTRA_FILE_NAME)
        render()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_track_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_share_gpx -> { shareGpx(); true }
            R.id.action_delete_gpx -> { confirmDelete(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun render() {
        val fn = fileName ?: return
        title = fn
    }

    private fun shareGpx() {
        val fn = fileName ?: return
        val f = File(getExternalFilesDir(null), "Tracks/$fn")
        if (!f.exists()) return

        val authority = applicationContext.packageName + ".fileprovider"
        val uri: Uri = FileProvider.getUriForFile(this, authority, f)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fn)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(share, getString(R.string.history_share_title)))
    }

    private fun confirmDelete() {
        val fn = fileName ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.history_delete_title)
            .setMessage(getString(R.string.history_delete_msg, fn))
            .setPositiveButton(R.string.history_delete_confirm) { _, _ ->
                val src = File(getExternalFilesDir(null), "Tracks/$fn")
                val bak = File(getExternalFilesDir(null), "Tracks/$fn.bak")
                val moved = if (src.exists()) src.renameTo(bak) else false
                if (moved) {
                    // Show undo in place; if not undone, finish the screen after dismissal
                    val snack = Snackbar.make(findViewById(android.R.id.content), getString(R.string.history_deleted, fn), Snackbar.LENGTH_LONG)
                    snack.setAction(R.string.undo) {
                        bak.renameTo(src)
                        render()
                    }
                    snack.addCallback(object : Snackbar.Callback() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                            if (event != DISMISS_EVENT_ACTION) {
                                if (bak.exists()) bak.delete()
                                finish() // leave detail after confirmed deletion
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

    companion object {
        const val EXTRA_FILE_NAME = "extra_file_name"
    }
}
