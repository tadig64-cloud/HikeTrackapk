package com.hikemvp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TraceEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun traceDao(): TraceDao

    companion object {
        @Volatile private var INSTANCE: AppDb? = null

        fun getInstance(context: Context): AppDb =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDb::class.java,
                    "app.db"
                )
                // TODO: remplacer par une vraie migration si vous aviez déjà une base.
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
    }
}
