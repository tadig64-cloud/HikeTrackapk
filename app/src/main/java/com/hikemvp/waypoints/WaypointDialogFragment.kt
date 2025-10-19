package com.hikemvp.waypoints

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.hikemvp.R

class WaypointDialogFragment(
    private val latitude: Double,
    private val longitude: Double,
    private val onSave: (WaypointMeta) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_waypoint, null, false)
        val etName = view.findViewById<EditText>(R.id.etName)
        val etNote = view.findViewById<EditText>(R.id.etNote)

        if (etName.text.isNullOrBlank()) {
            etName.setText(getString(R.string.waypoint_default_name))
        }

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.wp_dialog_title)
            .setView(view)
            .setPositiveButton(
                R.string.save,
                DialogInterface.OnClickListener { dialog, _ ->
                    val name = etName.text?.toString()?.trim()
                        .takeUnless { it.isNullOrEmpty() }
                        ?: getString(R.string.waypoint_default_name)
                    val note = etNote.text?.toString()?.trim()
                        .takeUnless { it.isNullOrEmpty() }

                    onSave(
                        WaypointMeta(
                            latitude = latitude,
                            longitude = longitude,
                            name = name ?: getString(R.string.waypoint_default_name),
                            note = note
                        )
                    )
                    dialog.dismiss()
                }
            )
            .setNegativeButton(
                android.R.string.cancel,
                DialogInterface.OnClickListener { dialog, _ -> dialog.dismiss() }
            )
            .create()
    }
}
