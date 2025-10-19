package com.hikemvp.history

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

/**
 * Persistance locale simple en JSON (fichier interne).
 * Pas de dépendance Room pour rester léger.
 */
object HikeStorage {
    private const val DIR = "history"
    private const val FILE = "hikes.json"

    private fun file(context: Context): File =
        File(context.filesDir, "$DIR/$FILE").apply { parentFile?.mkdirs() }

    suspend fun loadAll(context: Context): List<HikeRecord> = withContext(Dispatchers.IO) {
        val f = file(context)
        if (!f.exists()) return@withContext emptyList<HikeRecord>()
        val txt = f.readText()
        val ja = JSONArray(txt)
        List(ja.length()) { i -> ja.getJSONObject(i).toHikeRecord() }
            .sortedByDescending { it.dateUtcMillis }
    }

    suspend fun add(context: Context, rec: HikeRecord) = withContext(Dispatchers.IO) {
        val list = loadAll(context).toMutableList()
        list.add(rec)
        saveAll(context, list)
    }

    suspend fun saveAll(context: Context, list: List<HikeRecord>) = withContext(Dispatchers.IO) {
        val ja = JSONArray()
        list.forEach { ja.put(it.toJson()) }
        file(context).writeText(ja.toString())
    }

    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        file(context).delete()
    }
}
