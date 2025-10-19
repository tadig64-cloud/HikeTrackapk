package com.hikemvp.group.ui

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hikemvp.R
import com.hikemvp.group.GroupActions
import com.hikemvp.group.GroupMember
import com.hikemvp.group.GroupBridge

class GroupAdapter(
    private val items: MutableList<GroupMember> = mutableListOf(),
    private val onSelect: (GroupMember) -> Unit
) : RecyclerView.Adapter<GroupAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val color: View = v.findViewById(R.id.colorDot)
        val name: TextView = v.findViewById(R.id.txtName)
        val id: TextView = v.findViewById(R.id.txtId)
        val btnFollow: Button = v.findViewById(R.id.btnFollow)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_group_member, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val m = items[position]
        h.name.text = m.name.ifBlank { m.id }
        h.id.text = m.id
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(m.color)
        h.color.background = d

        h.itemView.setOnClickListener { onSelect(m) }
        h.btnFollow.setOnClickListener {
            onSelect(m)
            GroupActions.follow(m.id)
        }
    }

    fun setData(list: List<GroupMember>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }
}
