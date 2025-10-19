package com.hikemvp.group

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.hikemvp.R
import org.osmdroid.views.MapView

class TilePreloaderDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val v: View = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_preload_tiles, null, false)
        val txt = v.findViewById<TextView>(R.id.txtStatus)
        val pb = v.findViewById<ProgressBar>(R.id.progress)

        val mv: MapView = GroupBridge.mapView ?: return AlertDialog.Builder(requireContext())
            .setTitle("Préchargement")
            .setMessage("Carte indisponible")
            .setPositiveButton("OK", null)
            .create()

        val bbox = mv.boundingBox
        txt.text = "Préchargement…"

        TilePreloader.preload(
            requireContext(),
            mv,
            bbox,
            zoomMin = 12,
            zoomMax = 17,
            onProgress = { progress, z ->
                activity?.runOnUiThread {
                    pb.isIndeterminate = false
                    pb.progress = progress
                    txt.text = "Zoom " + z + " : " + progress + "%"
                }
            },
            onDone = { ok ->
                activity?.runOnUiThread {
                    txt.text = if (ok) "Terminé" else "Échec"
                    dismissAllowingStateLoss()
                }
            }
        )

        return AlertDialog.Builder(requireContext())
            .setTitle("Précharger les cartes")
            .setView(v)
            .setNegativeButton("Fermer", null)
            .create()
    }

    companion object {
        fun show(host: FragmentActivity) {
            TilePreloaderDialog().show(host.supportFragmentManager, "tiles_preloader")
        }
    }
}
