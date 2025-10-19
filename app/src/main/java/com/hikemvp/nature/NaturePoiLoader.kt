package com.hikemvp.nature

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object NaturePoiLoader {

    @Throws(Exception::class)
    fun loadFromAssets(context: Context, assetName: String = "nature_pois.json"): List<NaturePoi> {
        context.assets.open(assetName).use { ins ->
            return parse(ins)
        }
    }

    @Throws(Exception::class)
    fun loadFromUri(cr: ContentResolver, uri: Uri): List<NaturePoi> {
        cr.openInputStream(uri).use { ins ->
            if (ins == null) error("Impossible d'ouvrir le fichier: $uri")
            return parse(ins)
        }
    }

    private fun parse(input: InputStream): List<NaturePoi> {
        val text = BufferedReader(InputStreamReader(input)).use { it.readText() }
        val arr = JSONArray(text)
        val list = ArrayList<NaturePoi>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(NaturePoi.fromJson(obj))
        }
        return list
    }
}