package com.hikemvp

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.Toast

/**
 * Handlers "officiels" (un seul endroit dans le projet) :
 * - nav(view) / mbtiles(view) : pratiques pour setOnClickListener(::nav) ou XML onClick
 * - nav(activity) / mbtiles(activity) : pratiques quand tu es déjà dans une Activity
 *
 * Important : on démarre InfoHubActivity / OfflineMapsActivity par réflexion (Class.forName)
 * → donc ça COMPILE même si ces écrans ne sont pas présents dans une branche.
 */

// -------------------- Top-level handlers --------------------

fun nav(view: View) {
    val act = view.context as? Activity
    if (act == null) {
        Toast.makeText(view.context, "Action indisponible ici", Toast.LENGTH_SHORT).show()
        return
    }
    nav(act)
}

fun mbtiles(view: View) {
    val act = view.context as? Activity
    if (act == null) {
        Toast.makeText(view.context, "Action indisponible ici", Toast.LENGTH_SHORT).show()
        return
    }
    mbtiles(act)
}

fun nav(activity: Activity) {
    val ok = startActivityByName(
        activity,
        listOf(
            "com.hikemvp.info.InfoHubActivity",
            "com.hikemvp.InfoHubActivity" // fallback au cas où
        )
    )
    if (!ok) {
        Toast.makeText(activity, "Écran \"Infos rando\" introuvable (branche incomplète)", Toast.LENGTH_SHORT).show()
    }
}

fun mbtiles(activity: Activity) {
    // 1) Essaye d'ouvrir l'écran OfflineMapsActivity
    val opened = startActivityByName(
        activity,
        listOf(
            "com.hikemvp.offline.OfflineMapsActivity",
            "com.hikemvp.OfflineMapsActivity" // fallback au cas où
        )
    )
    if (opened) return

    // 2) Fallback : si le loader MBTiles existe, on tente d'ouvrir le chooser
    val invoked = invokeMbtilesChooserIfPresent(activity)
    if (invoked) return

    Toast.makeText(activity, "Cartes hors‑ligne indisponibles (écran/loader absent)", Toast.LENGTH_SHORT).show()
}

// -------------------- Legacy wrapper (si tu as encore des appels NavActions.nav(...)) --------------------

object NavActions {
    @JvmStatic fun nav(view: View) = com.hikemvp.nav(view)
    @JvmStatic fun mbtiles(view: View) = com.hikemvp.mbtiles(view)
    @JvmStatic fun nav(activity: Activity) = com.hikemvp.nav(activity)
    @JvmStatic fun mbtiles(activity: Activity) = com.hikemvp.mbtiles(activity)
}

// -------------------- Internal helpers --------------------

private fun startActivityByName(activity: Activity, classNames: List<String>): Boolean {
    for (name in classNames) {
        try {
            val clazz = Class.forName(name)
            val intent = Intent(activity, clazz)
            activity.startActivity(intent)
            return true
        } catch (_: Throwable) {
            // continue
        }
    }
    return false
}

private fun invokeMbtilesChooserIfPresent(activity: Activity): Boolean {
    return try {
        // class com.hikemvp.io.mbtiles.MbtilesLoader { fun openMbtilesChooser(Activity) }
        val loader = Class.forName("com.hikemvp.io.mbtiles.MbtilesLoader")
        val method = loader.getMethod("openMbtilesChooser", Activity::class.java)
        method.invoke(null, activity)
        true
    } catch (_: Throwable) {
        false
    }
}
