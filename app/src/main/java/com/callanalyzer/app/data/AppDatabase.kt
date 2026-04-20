package com.callanalyzer.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.callanalyzer.app.data.dao.CallLogDao
import com.callanalyzer.app.data.entity.CallLogEntity

@Database(
    entities = [CallLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun callLogDao(): CallLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "call_analyzer.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
