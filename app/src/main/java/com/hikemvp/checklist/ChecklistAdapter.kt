package com.hikemvp.checklist

import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hikemvp.R

class ChecklistAdapter(
    private val items: List<ChecklistItem>,
    private val prefs: SharedPreferences
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int =
        if (items[position].isHeader) TYPE_HEADER else TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            val v = inf.inflate(R.layout.item_check_header, parent, false)
            HeaderVH(v)
        } else {
            val v = inf.inflate(R.layout.item_check, parent, false)
            ItemVH(v)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val it = items[position]
        if (holder is HeaderVH) {
            holder.title.text = it.label
        } else if (holder is ItemVH) {
            holder.cb.text = it.label
            val saved = prefs.getBoolean(it.id, false)
            holder.cb.setOnCheckedChangeListener(null)
            holder.cb.isChecked = saved
            holder.cb.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean(it.id, isChecked).apply()
            }
        }
    }

    class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvHeader)
    }

    class ItemVH(v: View) : RecyclerView.ViewHolder(v) {
        val cb: CheckBox = v.findViewById(R.id.cbItem)
    }
}
