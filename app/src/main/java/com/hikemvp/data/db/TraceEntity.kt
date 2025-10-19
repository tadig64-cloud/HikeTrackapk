package com.hikemvp.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "traces",
    indices = [Index(value = ["absolutePath"], unique = true)]
)
data class TraceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val absolutePath: String,
    val displayPath: String,
    val importedAt: Long = System.currentTimeMillis(),
    val sizeBytes: Long = 0L,
    val sha256: String? = null
)
