package com.hikemvp

import android.app.Activity
import org.osmdroid.views.MapView

/**
 * Compat shim for legacy call sites.
 * Some parts of the app still call ZoomPadApi.attach(activity, mapView).
 * We provide both overloads; they are currently no-ops (safe).
 * The actual zoom UI is handled in layout / MapActivity.
 */
object ZoomPadApi {

    @JvmStatic
    fun attach(activity: Activity) {
        // No-op compat. Keep to avoid crashes if invoked.
    }

    @JvmStatic
    fun attach(activity: Activity, mapView: MapView) {
        // Delegate to single-arg compat.
        attach(activity)
    }
}
