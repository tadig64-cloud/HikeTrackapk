package com.hikemvp.group

import android.app.Activity
import android.util.Log
import org.osmdroid.views.MapView

/**
 * Compat pour l'ancien appel: GroupDeepLink.handleIfPresent(this, mapView)
 * Redirige vers l'API actuelle (lambda).
 */
fun GroupDeepLink.handleIfPresent(activity: Activity, mapView: MapView): Boolean {
    return this.handleIfPresent { code: String ->
        Log.d("Group", "deeplink code = $code")
        // (activity as? GroupActivity)?.let { GroupJoin.joinByLinkCode(it, code) }
    }
}
