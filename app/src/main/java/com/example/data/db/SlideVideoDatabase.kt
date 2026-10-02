package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PresentationEntity::class,
        SlideEntity::class,
        LanguageTrackEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SlideVideoDatabase : RoomDatabase() {
    abstract fun presentationDao(): PresentationDao

    companion object {
        @Volatile
        private var INSTANCE: SlideVideoDatabase? = null

        fun getInstance(context: Context): SlideVideoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SlideVideoDatabase::class.java,
                    "slide_video_db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
