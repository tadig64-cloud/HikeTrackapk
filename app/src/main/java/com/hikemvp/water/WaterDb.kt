package com.hikemvp.water

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [WaterPointEntity::class],
    version = 1,
    exportSchema = false
)
abstract class WaterDb : RoomDatabase() {

    abstract fun waterPointDao(): WaterPointDao

    companion object {
        @Volatile private var INSTANCE: WaterDb? = null

        fun get(context: Context): WaterDb {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context, WaterDb::class.java, "water.db")
                    .fallbackToDestructiveMigration(true) // ✅ remplace l’appel déprécié
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
