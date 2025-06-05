package com.example.twinmind_interview_app.database.room

// TranscriptDatabase.kt
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TranscriptSegmentEntity::class], version = 1)
abstract class TranscriptDatabase : RoomDatabase() {
    abstract fun transcriptDao(): TranscriptSegmentDao

    companion object {
        @Volatile
        private var INSTANCE: TranscriptDatabase? = null

        fun getDatabase(context: Context): TranscriptDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TranscriptDatabase::class.java,
                    "transcript_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
