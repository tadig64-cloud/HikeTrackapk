package com.hikemvp.water

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "water_points")
data class WaterPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
