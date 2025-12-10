package com.hikemvp.group.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.TextView
import android.view.Gravity
import android.graphics.Typeface

class GroupStatusBadgeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val text = TextView(context).apply {
        setPadding(16, 8, 16, 8)
        setTypeface(typeface, Typeface.BOLD)
    }

    init {
        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.END
            setMargins(16, 16, 16, 16)
        }
        addView(text, lp)
        update(false, "Solo", 0)
    }

    fun update(inGroup: Boolean, role: String, count: Int) {
        text.text = if (inGroup) "👥 $role · $count" else "Solo"
    }
}
