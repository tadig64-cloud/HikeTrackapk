package com.hikemvp.water

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterPointDao {

    @Query("SELECT * FROM water_points ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<WaterPointEntity>>

    @Query("SELECT * FROM water_points ORDER BY createdAt DESC")
    suspend fun getAll(): List<WaterPointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: WaterPointEntity): Long

    @Update
    suspend fun update(point: WaterPointEntity)

    @Delete
    suspend fun delete(point: WaterPointEntity)

    @Query("DELETE FROM water_points")
    suspend fun clear()
}
