@file:Suppress("DEPRECATION")
package com.hikemvp.group

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.TextView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.hikemvp.R

class GroupMemberAdapter(
    private val ctx: Context,
    private val items: MutableList<GroupMember>
) : BaseAdapter() {

    companion object {
        const val ACTION_FOCUS_MEMBER = "com.hikemvp.group.ACTION_FOCUS_MEMBER"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LON = "lon"
    }

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val v = convertView ?: LayoutInflater.from(ctx)
            .inflate(R.layout.row_group_member, parent, false)

        val member = items[position]
        val name = v.findViewById<TextView>(R.id.member_name)
        val dot = v.findViewById<View>(R.id.member_dot)
        val btn = v.findViewById<Button>(R.id.btn_show_on_map)

        name.text = member.name

        // petit rond coloré
        val d = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(member.color)
        }
        dot.background = d

        val focus: (View) -> Unit = {
            val i = Intent(ACTION_FOCUS_MEMBER)
                .putExtra(EXTRA_LAT, member.point.latitude)
                .putExtra(EXTRA_LON, member.point.longitude)
            LocalBroadcastManager.getInstance(ctx).sendBroadcast(i)
        }
        btn.setOnClickListener(focus)
        name.setOnClickListener(focus)

        return v
    }

    fun replaceAll(newItems: List<GroupMember>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
