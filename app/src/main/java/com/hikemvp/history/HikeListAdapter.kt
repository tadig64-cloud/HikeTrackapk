package com.hikemvp.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hikemvp.R

data class HikeRowVM(
    val id: String,
    val title: String,
    val subtitle: String,
    val notes: String
)

class HikeListAdapter : RecyclerView.Adapter<HikeListAdapter.VH>() {

    private val items = mutableListOf<HikeRowVM>()

    fun submit(newItems: List<HikeRowVM>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_hike, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = items[position]
        holder.title.text = row.title
        holder.subtitle.text = row.subtitle
        if (row.notes.isBlank()) {
            holder.notes.visibility = View.GONE
        } else {
            holder.notes.visibility = View.VISIBLE
            holder.notes.text = row.notes
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.hike_title)
        val subtitle: TextView = view.findViewById(R.id.hike_subtitle)
        val notes: TextView = view.findViewById(R.id.hike_notes)
    }
}
