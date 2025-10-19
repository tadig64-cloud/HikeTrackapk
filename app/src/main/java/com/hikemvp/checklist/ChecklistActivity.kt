package com.hikemvp.checklist

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hikemvp.R
import com.hikemvp.checklist.data.CheckItem
import com.hikemvp.checklist.ui.CheckAdapter

class ChecklistActivity : AppCompatActivity() {

    private val prefKey = "gear_checked_ids"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checklist)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).apply {
            setNavigationOnClickListener { finish() }
        }

        val items = defaultItems()
        val rv = findViewById<RecyclerView>(R.id.rvChecklist)
        rv.layoutManager = LinearLayoutManager(this)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val checked = prefs.getStringSet(prefKey, emptySet()) ?: emptySet()
        items.forEach { it.checked = checked.contains(it.id) }

        val adapter = CheckAdapter(items) { _, list ->
            val set = list.filter { it.checked }.map { it.id }.toSet()
            prefs.edit().putStringSet(prefKey, set).apply()
        }
        rv.adapter = adapter
    }

    private fun defaultItems(): MutableList<CheckItem> = mutableListOf(
        CheckItem("water", "Eau / gourde"),
        CheckItem("food", "Snack / repas"),
        CheckItem("jacket", "Veste imperméable"),
        CheckItem("layers", "Couche chaude"),
        CheckItem("map", "Carte / topo"),
        CheckItem("phone", "Téléphone chargé"),
        CheckItem("powerbank", "Batterie externe"),
        CheckItem("firstaid", "Trousse de secours"),
        CheckItem("headlamp", "Lampe frontale"),
        CheckItem("sunscreen", "Crème solaire"),
        CheckItem("hat", "Casquette / chapeau"),
        CheckItem("id", "Pièce d’identité / CB"),
        CheckItem("trash", "Sac de déchets")
    )
}
