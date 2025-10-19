package com.hikemvp.waypoints

import android.view.View
import android.widget.PopupMenu
import androidx.fragment.app.Fragment

/**
 * Small helper to attach a "Groupes" popup to any view.
 *
 * Usage (inside a Fragment):
 *   val popup = attachWaypointGroupsMenu(anchorView = myButton, fragment = this,
 *       groupsProvider = { /* return list of group names */ },
 *       currentGroup = { /* return currently selected group or null */ },
 *       onPicked = { grp -> /* apply selection */ })
 *   popup.show()
 *
 * All parameters have sensible defaults so existing calls with fewer parameters won't crash.
 */
fun attachWaypointGroupsMenu(
    anchorView: View,
    fragment: Fragment,
    groupsProvider: () -> List<String> = { emptyList() },
    currentGroup: () -> String? = { null },
    onPicked: ((String?) -> Unit)? = null
): PopupMenu {
    val ctx = anchorView.context
    val popup = PopupMenu(ctx, anchorView)
    val menu = popup.menu

    val selected = currentGroup() ?: "Tous (sans filtre)"
    menu.add(0, 1, 0, "Groupe actuel : $selected").apply { isEnabled = false }
    menu.add(0, 2, 1, "Changer de groupe…")
    menu.add(0, 3, 2, "Tous (sans filtre)")

    popup.setOnMenuItemClickListener { item ->
        when (item.itemId) {
            2 -> {
                val groups = groupsProvider().distinct().sorted()
                WaypointGroupPickerDialog
                    .newInstance(groups = groups, initial = currentGroup(), onPicked = { picked ->
                        onPicked?.invoke(picked)
                    })
                    .show(fragment.parentFragmentManager, "WaypointGroupPickerDialog")
                true
            }
            3 -> {
                onPicked?.invoke(null)
                true
            }
            else -> false
        }
    }
    return popup
}
