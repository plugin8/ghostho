package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.PyFileEntity

@Database(entities = [PyFileEntity::class], version = 1, exportSchema = false)
abstract class PyDatabase : RoomDatabase() {
    abstract fun pyFileDao(): PyFileDao

    companion object {
        @Volatile
        private var INSTANCE: PyDatabase? = null

        fun getInstance(context: Context): PyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PyDatabase::class.java,
                    "pyhost_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
