package com.hikemvp.maps

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.NumberPicker
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hikemvp.R

class MapDownloadDialogFragment(
    private val onConfirm: (minZ: Int, maxZ: Int) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_download_tiles, null, false)
        val npMin = v.findViewById<NumberPicker>(R.id.npMin)
        val npMax = v.findViewById<NumberPicker>(R.id.npMax)

        npMin.minValue = 3;  npMin.maxValue = 20; npMin.value = 12
        npMax.minValue = 3;  npMax.maxValue = 20; npMax.value = 16

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.download_tiles_title))
            .setView(v)
            .setPositiveButton(R.string.btn_download) { _, _ ->
                val minZ = minOf(npMin.value, npMax.value)
                val maxZ = maxOf(npMin.value, npMax.value)
                onConfirm(minZ, maxZ)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .create()
    }
}