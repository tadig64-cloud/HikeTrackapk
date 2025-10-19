package com.hikemvp.group

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.hikemvp.R

/**
 * BottomSheet simple pour piloter le groupe.
 * Besoin du thème MaterialComponents + dépendance material.
 */
class GroupBottomSheet : BottomSheetDialogFragment() {

    interface Host {
        fun onZoomAll()
        fun onToggleLabels(): Boolean
        fun onToggleCluster(): Boolean
        fun onStartStopSim(): Boolean // retourne true si sim active
    }

    private var host: Host? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        host = context as? Host
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.bs_group_controls, container, false)
        val btnZoom = v.findViewById<Button>(R.id.bsZoomAll)
        val btnLabels = v.findViewById<Button>(R.id.bsLabels)
        val btnCluster = v.findViewById<Button>(R.id.bsCluster)
        val btnSim = v.findViewById<Button>(R.id.bsSim)
        btnZoom.setOnClickListener { host?.onZoomAll() }
        btnLabels.setOnClickListener {
            val on = host?.onToggleLabels() == true
            btnLabels.text = if (on) "Labels ON" else "Labels"
        }
        btnCluster.setOnClickListener {
            val on = host?.onToggleCluster() == true
            btnCluster.text = if (on) "Cluster ON" else "Cluster"
        }
        btnSim.setOnClickListener {
            val active = host?.onStartStopSim() == true
            btnSim.text = if (active) "Sim OFF" else "Sim ON"
        }
        return v
    }
}
