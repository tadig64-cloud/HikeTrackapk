package com.hikemvp.waypoints

import android.view.View
import androidx.fragment.app.Fragment

/**
 * Helper d'appoint pour ouvrir le menu "Groupes" sur un long‑press
 * d'une vue existante — sans modifier ton fragment.
 *
 * Utilisation :
 *   enableGroupMenuOnLongPress(
 *       anchor = myButtonOrIconView,
 *       groupsProvider = { /* ex: items.mapNotNull { it.group }.distinct() */ },
 *       currentGroup = { /* ex: selectedGroup */ }
 *   ) { picked ->
 *       // picked = nom du groupe choisi ou null pour "Tous"
 *       // Ex: selectedGroup = picked; refresh()
 *   }
 */
fun Fragment.enableGroupMenuOnLongPress(
    anchor: View,
    groupsProvider: () -> List<String>,
    currentGroup: () -> String?,
    onPicked: (String?) -> Unit
) {
    anchor.setOnLongClickListener {
        // Cette fonction suppose que attachWaypointGroupsMenu(...) est disponible
        // dans le même package (WaypointGroupMenu.kt) comme convenu précédemment.
        attachWaypointGroupsMenu(
            anchorView = anchor,
            fragment = this,
            groupsProvider = groupsProvider,
            currentGroup = currentGroup,
            onPicked = onPicked
        )
        true
    }
}
