package com.hikemvp.group

import android.app.Activity
import android.content.Intent
import android.net.Uri
import org.osmdroid.views.MapView
import android.content.Context

/**
 * Parsing et aide pour traiter les liens d'invitation au niveau de GroupActivity.
 */
object GroupDeepLink {
    @Volatile var pendingCode: String? = null

    fun handleIfPresent(ctx: Context, mapView: MapView?): Boolean {
        val code = pendingCode ?: return false
        val mv = mapView ?: return false
        val ok = GroupRepo.joinByCode(ctx, code, mv)
        if (ok) pendingCode = null
        return ok
    }
}
    const val EXTRA_JOIN_CODE = "extra_join_code"

    /**
     * Retourne le code d'invitation, si présent, à partir d'une URI.
     * Exemples acceptés :
     *  - hiketrack://group/join?code=ABCD12
     *  - hiketrack://group/join/ABCD12
     *  - https://hiketrack.app/join?code=ABCD12
     */
    fun parse(uri: Uri): String? {
        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        val path = uri.path ?: ""

        // Cas natif : hiketrack://group/join...
        if (scheme.equals("hiketrack", true) && host.equals("group", true)) {
            if (path.startsWith("/join")) {
                uri.getQueryParameter("code")?.let { if (it.isNotBlank()) return it }
                val segs = uri.pathSegments
                if (segs.size >= 2) return segs[1] // join/<code>
            }
        }



        // Cas web générique : .../join?code=
        if (path.contains("/join")) {
            uri.getQueryParameter("code")?.let { if (it.isNotBlank()) return it }
        }

        // Fallback très permissif : dernier segment non vide
        val segs = uri.pathSegments
        return segs.lastOrNull { it.isNotBlank() }
    }

    /**
     * A appeler dans GroupActivity une fois la MapView prête.
     * Ne change pas l'UI : utilise la logique existante via GroupRepo.
     */
    fun maybeJoinFromIntent(activity: Activity, mapView: MapView, intent: Intent) {
        val code = intent.getStringExtra(EXTRA_JOIN_CODE)?.trim()
        if (!code.isNullOrEmpty()) {
            GroupRepo.joinByCode(activity, code, mapView)
            intent.removeExtra(EXTRA_JOIN_CODE) // évite double-joindre sur recréations
        }
    }