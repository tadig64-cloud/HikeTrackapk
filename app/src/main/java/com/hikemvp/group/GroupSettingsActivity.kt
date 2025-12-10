package com.hikemvp.group

import android.os.Bundle
import android.widget.SeekBar
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import com.hikemvp.R

class GroupSettingsActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("group_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_settings)
        title = "Réglages — Groupe"

        val swLabels = findViewById<SwitchCompat>(R.id.swLabelsDefault)
        val swAutoZoom = findViewById<SwitchCompat>(R.id.swAutoZoomTap)
        val swCluster = findViewById<SwitchCompat>(R.id.swClusterDefault)
        val swSim = findViewById<SwitchCompat>(R.id.swSimDefault)
        val seekDot = findViewById<SeekBar>(R.id.seekDotSize)
        val tvDot = findViewById<TextView>(R.id.tvDotSizeValue)

        val labelsDefault = prefs.getBoolean("labels_default", true)
        val autoZoomTap = prefs.getBoolean("auto_zoom_tap", true)
        val clusterDefault = prefs.getBoolean("cluster_default", false)
        val simDefault = prefs.getBoolean("sim_default", false)
        val dotSize = prefs.getInt("dot_size_dp", 24)

        swLabels.isChecked = labelsDefault
        swAutoZoom.isChecked = autoZoomTap
        swCluster.isChecked = clusterDefault
        swSim.isChecked = simDefault
        seekDot.progress = dotSize
        tvDot.text = "${dotSize}dp"

        swLabels.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("labels_default", isChecked).apply()
        }
        swAutoZoom.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_zoom_tap", isChecked).apply()
        }
        swCluster.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("cluster_default", isChecked).apply()
        }
        swSim.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("sim_default", isChecked).apply()
        }
        seekDot.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvDot.text = "${progress}dp"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val value = seekBar?.progress ?: 24
                prefs.edit().putInt("dot_size_dp", value).apply()
            }
        })
        val btnResetMembers = findViewById<Button>(R.id.btnResetMembers)
        val btnResetColors = findViewById<Button>(R.id.btnResetColors)

        btnResetMembers.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Réinitialiser la liste ?")
                .setMessage("Cela efface les membres enregistrés localement.")
                .setPositiveButton("Effacer") { _, _ ->
                    GroupStore.clear(this)
                    Toast.makeText(this, "Liste de membres réinitialisée", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Annuler", null)
                .show()
        }

        btnResetColors.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Réinitialiser les couleurs ?")
                .setMessage("Toutes les couleurs personnalisées seront oubliées.")
                .setPositiveButton("Effacer") { _, _ ->
                    GroupColors.clear(this)
                    Toast.makeText(this, "Couleurs personnalisées réinitialisées", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Annuler", null)
                .show()
        }
    
    }
}
