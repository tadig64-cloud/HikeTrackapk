package com.hikemvp.info

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.hikemvp.R

class InfoHubActivity : AppCompatActivity() {

    // Ordre commun à toutes les listes d’IDs
    private val HEADER_IDS = intArrayOf(
        R.id.header_markings, R.id.header_weather,  R.id.header_storms,
        R.id.header_orient,   R.id.header_respect,  R.id.header_firstaid,
        R.id.header_heat,     R.id.header_cold,     R.id.header_nav,
        R.id.header_animals
    )
    private val TOGGLE_IDS = intArrayOf(
        R.id.toggle_markings, R.id.toggle_weather,  R.id.toggle_storms,
        R.id.toggle_orient,   R.id.toggle_respect,  R.id.toggle_firstaid,
        R.id.toggle_heat,     R.id.toggle_cold,     R.id.toggle_nav,
        R.id.toggle_animals
    )
    private val BODY_IDS = intArrayOf(
        R.id.body_markings, R.id.body_weather, R.id.body_storms,
        R.id.body_orient,   R.id.body_respect, R.id.body_firstaid,
        R.id.body_heat,     R.id.body_cold,    R.id.body_nav,
        R.id.body_animals
    )
    private val CHEVRON_IDS = intArrayOf(
        R.id.chevron_markings, R.id.chevron_weather,  R.id.chevron_storms,
        R.id.chevron_orient,   R.id.chevron_respect,  R.id.chevron_firstaid,
        R.id.chevron_heat,     R.id.chevron_cold,     R.id.chevron_nav,
        R.id.chevron_animals
    )

    companion object {
        const val EXTRA_SECTION_KEY = "extra_section_key" // pour le deep-link
    }

    // clé lisible -> id du body
    private val SECTION_KEYS = mapOf(
        "markings" to R.id.body_markings,
        "weather"  to R.id.body_weather,
        "storms"   to R.id.body_storms,
        "orient"   to R.id.body_orient,
        "respect"  to R.id.body_respect,
        "firstaid" to R.id.body_firstaid,
        "heat"     to R.id.body_heat,
        "cold"     to R.id.body_cold,
        "nav"      to R.id.body_nav,
        "animals"  to R.id.body_animals
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_info_hub)

        // --- Toolbar ---
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_info)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val bg = ContextCompat.getColor(this, R.color.topbar_idle_bg)
        val fg = ContextCompat.getColor(this, R.color.topbar_idle_icon)
        toolbar.setBackgroundColor(bg)
        toolbar.setTitleTextColor(fg)
        toolbar.navigationIcon?.mutate()?.setTint(fg)
        toolbar.overflowIcon?.mutate()?.setTint(fg)

        // --- Orages : on fusionne avant/pendant/après pour un seul bloc texte ---
        findViewById<TextView>(R.id.body_storms)?.text = buildString {
            appendLine(getString(R.string.ht_safety_storm_title))
            appendLine()
            appendLine(getString(R.string.ht_safety_storm_before).trim())
            appendLine()
            appendLine(getString(R.string.ht_safety_storm_during).trim())
            appendLine()
            append(getString(R.string.ht_safety_storm_after).trim())
        }.trim()

        // --- Bind Expand/Collapse pour toutes les sections + état initial masqué ---
        for (i in BODY_IDS.indices) {
            bindExpandableSection(HEADER_IDS[i], TOGGLE_IDS[i], BODY_IDS[i])
            setSectionExpanded(TOGGLE_IDS[i], BODY_IDS[i], expanded = false, scroll = false)
            rotateChevron(i, expanded = false)
        }

        // -- Restaure l'état (rotation)
        savedInstanceState?.let { state ->
            for (i in BODY_IDS.indices) {
                val bodyId = BODY_IDS[i]
                val expanded = state.getBoolean("ih_exp_$bodyId", false)
                setSectionExpanded(TOGGLE_IDS[i], bodyId, expanded, scroll = false)
                rotateChevron(i, expanded)
            }
        }

        // -- Restaure depuis prefs si nouveau lancement
        if (savedInstanceState == null) {
            restoreFromPrefs()
        }

        // -- Deep-link éventuel vers une section
        intent.getStringExtra(EXTRA_SECTION_KEY)?.let { key ->
            SECTION_KEYS[key]?.let { bodyId ->
                val i = indexOfBody(bodyId)
                if (i >= 0) {
                    setSectionExpanded(TOGGLE_IDS[i], bodyId, expanded = true, scroll = true)
                    rotateChevron(i, true)
                }
            }
        }

        // --- Long-clic = copie ---
        enableCopyOnLongClick(*BODY_IDS)

        // --- Boutons d’action ---
        findViewById<MaterialButton>(R.id.btn_open_firstaid)?.setOnClickListener {
            runCatching { startActivity(Intent(this, com.hikemvp.safety.FirstAidActivity::class.java)) }
        }
        findViewById<MaterialButton>(R.id.btn_open_respect)?.setOnClickListener {
            runCatching { startActivity(Intent(this, com.hikemvp.safety.RespectNatureActivity::class.java)) }
        }
        findViewById<MaterialButton>(R.id.btn_open_sos)?.setOnClickListener {
            runCatching { startActivity(Intent(this, com.hikemvp.sos.SosActivity::class.java)) }
        }
    }

    // ---------- Toolbar menu (Recherche + Expand/Collapse All) ----------
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_info_hub, menu)
        val item = menu.findItem(R.id.action_search)
        val sv = item.actionView as SearchView
        sv.queryHint = getString(R.string.search)

        sv.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                applySearchFilter(query)
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                applySearchFilter(newText)
                return true
            }
        })
        item.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(p0: MenuItem): Boolean = true
            override fun onMenuItemActionCollapse(p0: MenuItem): Boolean {
                applySearchFilter(null)
                return true
            }
        })
        return true
    }

    // ---------- Menu actions (UNIQUE) ----------
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { onBackPressedDispatcher.onBackPressed(); true }
            R.id.action_expand_all -> { setAll(expanded = true, scroll = true); true }
            R.id.action_collapse_all -> { setAll(expanded = false, scroll = false); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ---------- Expand/Collapse helpers ----------
    private fun bindExpandableSection(headerId: Int, toggleId: Int, bodyId: Int) {
        val header = findViewById<View>(headerId)
        val body = findViewById<View>(bodyId)
        val onToggle = {
            val expanded = body.visibility != View.VISIBLE
            setSectionExpanded(toggleId, bodyId, expanded, scroll = true)
            val index = indexOfBody(bodyId)
            rotateChevron(index, expanded)
        }
        header.setOnClickListener { onToggle() }
        findViewById<TextView>(toggleId).setOnClickListener { onToggle() }
    }

    private fun setSectionExpanded(toggleId: Int, bodyId: Int, expanded: Boolean, scroll: Boolean) {
        val toggle = findViewById<TextView>(toggleId)
        val body = findViewById<View>(bodyId)
        body.visibility = if (expanded) View.VISIBLE else View.GONE
        toggle.setText(if (expanded) R.string.ih_collapse else R.string.ih_expand)
        if (expanded && scroll) scrollToSection(bodyId)
    }

    private fun rotateChevron(index: Int, expanded: Boolean) {
        if (index !in CHEVRON_IDS.indices) return
        val chev = findViewById<ImageView?>(CHEVRON_IDS[index])
        chev?.animate()?.rotation(if (expanded) 90f else 0f)?.setDuration(150)?.start()
    }

    private fun scrollToSection(bodyId: Int) {
        val idx = indexOfBody(bodyId)
        if (idx == -1) return
        val headerView = findViewById<View>(HEADER_IDS[idx])
        val scroll = findViewById<ScrollView>(R.id.scroll_infohub)
        scroll?.post {
            val y = (headerView.top - headerView.resources.displayMetrics.density * 8).toInt().coerceAtLeast(0)
            scroll.smoothScrollTo(0, y)
        }
    }

    private fun setAll(expanded: Boolean, scroll: Boolean) {
        for (i in BODY_IDS.indices) {
            setSectionExpanded(TOGGLE_IDS[i], BODY_IDS[i], expanded, scroll = false)
            rotateChevron(i, expanded)
        }
        if (expanded && scroll) {
            findViewById<ScrollView>(R.id.scroll_infohub)?.smoothScrollTo(0, 0)
        }
    }

    private fun indexOfBody(bodyId: Int): Int = BODY_IDS.indexOf(bodyId)

    // ---------- Recherche + surlignage ----------
    private fun applySearchFilter(query: String?) {
        val q = query?.trim().orEmpty()
        the@ run {
            val searching = q.length >= 2
            var scrolled = false
            for (i in BODY_IDS.indices) {
                val toggleId = TOGGLE_IDS[i]
                val bodyId = BODY_IDS[i]
                val bodyTv = findViewById<TextView>(bodyId)

                if (!searching) {
                    clearHighlight(bodyTv)
                    setSectionExpanded(toggleId, bodyId, expanded = false, scroll = false)
                    rotateChevron(i, expanded = false)
                } else {
                    val text = bodyTv.text?.toString().orEmpty()
                    val match = text.contains(q, ignoreCase = true)
                    clearHighlight(bodyTv)
                    if (match) {
                        setSectionExpanded(toggleId, bodyId, expanded = true, scroll = false)
                        rotateChevron(i, expanded = true)
                        highlightMatches(bodyTv, q)
                        if (!scrolled) {
                            scrollToSection(bodyId)
                            scrolled = true
                        }
                    } else {
                        setSectionExpanded(toggleId, bodyId, expanded = false, scroll = false)
                        rotateChevron(i, expanded = false)
                    }
                }
            }
        }
    }

    private fun highlightMatches(tv: TextView, query: String?) {
        val text = tv.text ?: return
        if (query.isNullOrBlank()) { tv.text = text; return }
        val str = text.toString()
        val lower = str.lowercase()
        val q = query.lowercase()
        val spannable = SpannableString(str)

        var idx = lower.indexOf(q)
        while (idx >= 0) {
            spannable.setSpan(
                BackgroundColorSpan(0xFFFFFF00.toInt()), // jaune
                idx, idx + q.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            idx = lower.indexOf(q, idx + q.length)
        }
        tv.text = spannable
    }

    private fun clearHighlight(tv: TextView) {
        tv.text = tv.text.toString()
    }

    // ---------- Copie au long-clic ----------
    private fun enableCopyOnLongClick(vararg bodyIds: Int) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        bodyIds.forEach { id ->
            (findViewById<View>(id) as? TextView)?.setOnLongClickListener { tv ->
                val text = (tv as TextView).text
                if (!text.isNullOrBlank()) {
                    cm.setPrimaryClip(ClipData.newPlainText("Info", text))
                    Toast.makeText(this, "Texte copié", Toast.LENGTH_SHORT).show()
                }
                true
            }
        }
    }

    // ---------- État rotation ----------
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        BODY_IDS.forEach { bodyId ->
            val visible = findViewById<View>(bodyId).visibility == View.VISIBLE
            outState.putBoolean("ih_exp_$bodyId", visible)
        }
    }

    // ---------- Persistance simple (prefs) ----------
    override fun onPause() {
        super.onPause()
        val prefs = getSharedPreferences("infohub", MODE_PRIVATE).edit()
        BODY_IDS.forEach { bodyId ->
            val visible = findViewById<View>(bodyId).visibility == View.VISIBLE
            prefs.putBoolean("ih_pref_$bodyId", visible)
        }
        prefs.apply()
    }

    private fun restoreFromPrefs() {
        val prefs = getSharedPreferences("infohub", MODE_PRIVATE)
        for (i in BODY_IDS.indices) {
            val bodyId = BODY_IDS[i]
            val expanded = prefs.getBoolean("ih_pref_$bodyId", false)
            setSectionExpanded(TOGGLE_IDS[i], bodyId, expanded, scroll = false)
            rotateChevron(i, expanded)
        }
    }
}
