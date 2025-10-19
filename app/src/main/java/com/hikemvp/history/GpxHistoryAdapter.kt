package com.hikemvp.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hikemvp.R
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class GpxHistoryAdapter(
    private var items: List<TrackInfo>,
    private val onClick: (TrackInfo) -> Unit,
    private val onLongPress: (TrackInfo) -> Unit
) : RecyclerView.Adapter<GpxHistoryAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.txtTitle)
        val subtitle: TextView = v.findViewById(R.id.txtSubtitle)
        val meta: TextView = v.findViewById(R.id.txtMeta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.row_gpx_track, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.name.ifBlank { item.fileName }
        val km = item.distanceMeters / 1000.0
        val dplus = item.elevationGain.roundToInt()
        val dminus = item.elevationLoss.roundToInt()
        val dur = item.durationMillis?.let { ms ->
            val h = TimeUnit.MILLISECONDS.toHours(ms)
            val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
            if (h > 0) "${h}h${m.toString().padStart(2, '0')}" else "${m} min"
        } ?: "—"
        holder.subtitle.text = holder.itemView.context.getString(
            R.string.history_row_stats,
            String.format("%.2f", km),
            dplus,
            dminus,
            dur
        )
        holder.meta.text = holder.itemView.context.getString(
            R.string.history_row_meta,
            item.points,
            item.fileName
        )

        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener { onLongPress(item); true }
    }

    fun submit(list: List<TrackInfo>) {
        items = list
        notifyDataSetChanged()
    }
}
