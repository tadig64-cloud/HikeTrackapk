package com.hikemvp.data

import android.content.Context
import com.hikemvp.data.db.AppDb
import com.hikemvp.data.db.TraceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * TraceRepository
 * ---------------
 * Surcouche simple autour de Room pour gérer les traces.
 * NOTE: On n'utilise *pas* deleteByAbsolutePath du DAO (au cas où il n'existe pas) :
 *       on supprime via l'entité récupérée, ce qui évite le "Unresolved reference".
 */
class TraceRepository(context: Context) {

    private val dao = AppDb.getInstance(context).traceDao()

    suspend fun upsert(entity: TraceEntity): Long = withContext(Dispatchers.IO) {
        dao.upsert(entity)
    }

    suspend fun getByAbsolutePath(path: String): TraceEntity? = withContext(Dispatchers.IO) {
        dao.getByAbsolutePath(path)
    }

    suspend fun deleteByAbsolutePath(path: String) = withContext(Dispatchers.IO) {
        val entity = dao.getByAbsolutePath(path)
        if (entity != null) dao.delete(entity)
    }

    suspend fun getAllOnce(): List<TraceEntity> = withContext(Dispatchers.IO) {
        dao.getAllOnce()
    }
}
