package com.hikemvp.waypoints

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

/**
 * Simple picker dialog for choosing a waypoint group.
 * No layout/resources required.
 */
class WaypointGroupPickerDialog : DialogFragment() {

    var initialGroup: String? = null
    var onPicked: ((String?) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        // Provided groups from arguments, default empty.
        val groups: ArrayList<String> =
            arguments?.getStringArrayList(ARG_GROUPS) ?: arrayListOf()

        // Build list: "Tous (sans filtre)" + known groups
        val display = ArrayList<String>(groups.size + 1).apply {
            add("Tous (sans filtre)")
            addAll(groups.sorted())
        }

        val selectedIndex = display.indexOfFirst { it == initialGroup }.let { idx ->
            if (idx >= 0) idx else 0
        }

        return AlertDialog.Builder(ctx)
            .setTitle("Choisir un groupe")
            .setSingleChoiceItems(display.toTypedArray(), selectedIndex) { dialog, which ->
                val picked = if (which == 0) null else display[which]
                onPicked?.invoke(picked)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    companion object {
        private const val ARG_GROUPS = "groups"

        fun newInstance(groups: List<String>, initial: String? = null, onPicked: ((String?) -> Unit)? = null): WaypointGroupPickerDialog {
            return WaypointGroupPickerDialog().apply {
                arguments = Bundle().apply {
                    putStringArrayList(ARG_GROUPS, ArrayList(groups))
                }
                initialGroup = initial
                this.onPicked = onPicked
            }
        }
    }
}
