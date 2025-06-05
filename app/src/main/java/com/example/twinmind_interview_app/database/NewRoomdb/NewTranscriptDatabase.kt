package com.example.twinmind_interview_app.database.NewRoomdb

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TranscriptSessionEntity::class, NewTranscriptSegmentEntity::class],
    version = 1 // Set to 1 for the new, independent DB
)
abstract class NewTranscriptDatabase : RoomDatabase() {
    abstract fun sessionDao(): TranscriptSessionDao
    abstract fun segmentDao(): NewTranscriptSegmentDao

    companion object {
        @Volatile
        private var INSTANCE: NewTranscriptDatabase? = null

        fun getDatabase(context: Context): NewTranscriptDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NewTranscriptDatabase::class.java,
                    "new_transcript_database" // Give this DB a unique name!
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
