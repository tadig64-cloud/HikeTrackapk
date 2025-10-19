package com.hikemvp.history

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hikemvp.R
import com.hikemvp.profile.ProfileProvider
import com.hikemvp.profile.ProfileFormat
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.*

class HikeHistoryActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private lateinit var progress: ProgressBar
    private val adapter = HikeListAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hike_history)

        list = findViewById(R.id.hike_list)
        empty = findViewById(R.id.hike_empty)
        progress = findViewById(R.id.hike_progress)

        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        loadData()
    }

    private fun loadData() {
        progress.visibility = View.VISIBLE
        empty.visibility = View.GONE
        list.visibility = View.GONE

        launch {
            val data = withContext(Dispatchers.IO) { HikeStorage.loadAll(this@HikeHistoryActivity) }
            val profile = ProfileProvider.get(this@HikeHistoryActivity)
            val df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())

            val rows = data.map { rec ->
                val date = df.format(Date(rec.dateUtcMillis))
                val dist = ProfileFormat.distanceLabel(profile.unitSystem, rec.distanceMeters)
                val elev = ProfileFormat.elevationLabel(profile.unitSystem, rec.elevationUpMeters)
                val dur = formatDuration(rec.durationSeconds)
                HikeRowVM(
                    id = rec.id,
                    title = date,
                    subtitle = "$dist  •  D+ $elev  •  $dur",
                    notes = rec.notes
                )
            }

            adapter.submit(rows)

            progress.visibility = View.GONE
            if (rows.isEmpty()) {
                empty.visibility = View.VISIBLE
            } else {
                list.visibility = View.VISIBLE
            }
        }
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return if (h > 0) String.format("%dh %02d", h, m) else String.format("%d min", m)
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }
}
