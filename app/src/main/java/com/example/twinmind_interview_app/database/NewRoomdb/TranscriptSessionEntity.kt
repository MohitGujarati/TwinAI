package com.example.twinmind_interview_app.database.NewRoomdb

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transcript_sessions")
data class TranscriptSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String? = null, // Optional: for naming the recording
    val createdAt: Long = System.currentTimeMillis()
)
