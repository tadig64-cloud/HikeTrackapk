package com.hikemvp.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TraceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TraceEntity): Long

    @Delete
    suspend fun delete(entity: TraceEntity)

    @Query("SELECT * FROM traces WHERE absolutePath = :abs LIMIT 1")
    suspend fun getByAbsolutePath(abs: String): TraceEntity?

    @Query("SELECT * FROM traces ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<TraceEntity>>

    @Query("SELECT * FROM traces ORDER BY importedAt DESC")
    suspend fun getAllOnce(): List<TraceEntity>
}
