package com.hikemvp

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import org.osmdroid.views.overlay.Polyline

/**
 * Wrapper d’écriture GPX qui s’appuie sur GpxUtils.
 * Inclut des surcharges "compat" pour éviter toute régression
 * si l’ancien code passait un paramètre "name" supplémentaire.
 */
object GpxWriter {

    /** Conversion simple en GPX */
    @JvmStatic
    fun toGpx(polyline: Polyline): String =
        GpxUtils.polylineToGpx(polyline)

    /** Compat: accepte un "name" mais l’ignore pour rester compatible */
    @JvmStatic
    fun toGpx(name: String?, polyline: Polyline): String =
        GpxUtils.polylineToGpx(polyline)

    /** Écrit dans un Uri via un ContentResolver */
    @JvmStatic
    fun write(resolver: ContentResolver, target: Uri, polyline: Polyline) {
        val gpx = GpxUtils.polylineToGpx(polyline)
        resolver.openOutputStream(target)?.use { os ->
            GpxUtils.saveToFile(gpx, os)
        } ?: throw IllegalStateException("Unable to open $target for writing")
    }

    /** Compat: accepte un "name" mais l’ignore (ancienne signature) */
    @JvmStatic
    fun write(resolver: ContentResolver, target: Uri, name: String?, polyline: Polyline) =
        write(resolver, target, polyline)

    /** Variante pratique avec Context */
    @JvmStatic
    fun write(context: Context, target: Uri, polyline: Polyline) =
        write(context.contentResolver, target, polyline)

    /** Compat: Context + name (ancienne signature) */
    @JvmStatic
    fun write(context: Context, target: Uri, name: String?, polyline: Polyline) =
        write(context.contentResolver, target, polyline)
}
