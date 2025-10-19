package com.hikemvp

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import kotlin.math.max

class GearChecklistActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("gear_checklist", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gear_checklist)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        toolbar.title = getString(R.string.menu_checklist)

        val container = findViewById<LinearLayout>(R.id.gearContainer)
        if (container == null) {
            Toast.makeText(this, "Layout checklist introuvable", Toast.LENGTH_SHORT).show()
            return
        }

        val sections: List<Pair<String, List<String>>> = listOf(
            "Navigation"   to safeArray(R.array.gear_nav),
            "Vêtements"    to safeArray(R.array.gear_vetements),
            "Couchage"     to safeArray(R.array.gear_couchage),
            "Abri"         to safeArray(R.array.gear_abri),
            "Cuisine"      to safeArray(R.array.gear_cuisine),
            "Hydratation"  to safeArray(R.array.gear_hydratation),
            "Sécurité"     to safeArray(R.array.gear_securite),
            "Électronique" to safeArray(R.array.gear_electronique),
            "Divers"       to safeArray(R.array.gear_divers),
        )

        sections.forEach { (title, items) ->
            addSectionCard(container, title, items)
        }
    }

    private fun safeArray(id: Int): List<String> = try {
        resources.getStringArray(id).toList()
    } catch (_: Exception) { emptyList() }

    private fun addSectionCard(parent: LinearLayout, sectionTitle: String, items: List<String>) {
        val card = MaterialCardView(this).apply {
            val m = dp(12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(m, m, m, 0) }
            radius = dp(16).toFloat()
            cardElevation = dp(2).toFloat()
        }

        val vbox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        card.addView(vbox)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(4))
        }
        val tvTitle = TextView(this).apply {
            text = sectionTitle
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvCount = TextView(this).apply {
            text = "0/0"
            textSize = 14f
            setPadding(0, 0, dp(8), 0)
        }
        val tvArrow = TextView(this).apply {
            text = "▼"
            textSize = 18f
        }
        header.addView(tvTitle)
        header.addView(tvCount)
        header.addView(tvArrow)
        vbox.addView(header)

        val group = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        vbox.addView(group)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnAllOn = TextView(this).apply {
            text = "Tout cocher"
            setPadding(0, 0, dp(16), dp(8))
            setTextColor(getColorCompat(android.R.color.holo_green_light))
            setOnClickListener {
                toggleAllInSection(group, check = true)
                updateCount(tvCount, group)
            }
        }
        val btnAllOff = TextView(this).apply {
            text = "Tout décocher"
            setPadding(0, 0, 0, dp(8))
            setTextColor(getColorCompat(android.R.color.holo_red_light))
            setOnClickListener {
                toggleAllInSection(group, check = false)
                updateCount(tvCount, group)
            }
        }
        actions.addView(btnAllOn)
        actions.addView(btnAllOff)
        group.addView(actions)

        val itemsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        group.addView(itemsBox)

        val sectionKey = sanitize("section_$sectionTitle")
        items.forEach { label ->
            val key = sanitize("gear_${sectionTitle}_$label")
            val cb = CheckBox(this).apply {
                text = label
                isChecked = prefs.getBoolean(key, false)
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean(key, checked).apply()
                    updateCount(tvCount, group)
                }
            }
            itemsBox.addView(cb)
        }

        val collapsedKey = "collapsed_$sectionKey"
        val collapsed = prefs.getBoolean(collapsedKey, false)
        group.visibility = if (collapsed) View.GONE else View.VISIBLE
        tvArrow.text = if (collapsed) "▲" else "▼"

        header.setOnClickListener {
            val newCollapsed = group.visibility == View.VISIBLE
            group.visibility = if (newCollapsed) View.GONE else View.VISIBLE
            tvArrow.text = if (newCollapsed) "▲" else "▼"
            prefs.edit().putBoolean(collapsedKey, newCollapsed).apply()
        }

        updateCount(tvCount, group)
        parent.addView(card)
    }

    private fun toggleAllInSection(group: LinearLayout, check: Boolean) {
        fun visit(view: View) {
            when (view) {
                is CheckBox -> if (view.isChecked != check) view.isChecked = check
                is LinearLayout -> view.children.forEach { visit(it) }
            }
        }
        group.children.forEach { visit(it) }
    }

    private fun updateCount(tvCount: TextView, group: LinearLayout) {
        var total = 0
        var checked = 0
        fun visit(view: View) {
            when (view) {
                is CheckBox -> { total++; if (view.isChecked) checked++ }
                is LinearLayout -> view.children.forEach { visit(it) }
            }
        }
        group.children.forEach { visit(it) }
        tvCount.text = "${checked}/${max(1, total)}"
    }

    private fun dp(v: Int): Int = (resources.displayMetrics.density * v).toInt()

    private fun getColorCompat(id: Int): Int = resources.getColor(id, theme)

    private fun sanitize(raw: String): String =
        raw.lowercase().replace(Regex("[^a-z0-9]+"), "_")
}
