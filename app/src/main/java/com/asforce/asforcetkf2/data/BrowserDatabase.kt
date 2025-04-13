package com.asforce.asforcetkf2.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database that manages the persistent data for the browser
 */
@Database(entities = [TabEntity::class], version = 2, exportSchema = false)
abstract class BrowserDatabase : RoomDatabase() {
    
    abstract fun tabDao(): TabDao
    
    companion object {
        @Volatile
        private var INSTANCE: BrowserDatabase? = null
        
        fun getDatabase(context: Context): BrowserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BrowserDatabase::class.java,
                    "browser_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}
