package com.hikemvp

import com.hikemvp.group.GroupOverlay
import org.osmdroid.views.overlay.Overlay

/**
 * Autorise l'appel existant mapView.overlays.add(groupOverlay)
 * même si groupOverlay est nullable, sans modifier MapActivity.
 */
fun MutableList<Overlay>.add(overlay: GroupOverlay?) {
    if (overlay != null) {
        this.add(overlay as Overlay)
    }
}