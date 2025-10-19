package com.hikemvp.checklist.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hikemvp.R
import com.hikemvp.checklist.data.CheckItem

class CheckAdapter(
    private val items: MutableList<CheckItem>,
    private val onChanged: (item: CheckItem, list: List<CheckItem>) -> Unit
) : RecyclerView.Adapter<CheckAdapter.VH>() {

    class VH(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_checkable_row, parent, false)
    ) {
        val cb: CheckBox = itemView.findViewById(R.id.cb)
        val tv: TextView = itemView.findViewById(R.id.tv)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(parent)

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tv.text = item.label
        holder.cb.setOnCheckedChangeListener(null)
        holder.cb.isChecked = item.checked
        holder.cb.setOnCheckedChangeListener { _, isChecked ->
            item.checked = isChecked
            onChanged(item, items)
        }
        holder.itemView.setOnClickListener {
            holder.cb.isChecked = !holder.cb.isChecked
        }
    }

    override fun getItemCount(): Int = items.size
}
