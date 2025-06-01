package com.example.twinmind_interview_app.model

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "transcript_segments")
data class TranscriptSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val text: String,
    val startTime: Int,  // seconds since meeting start
    val endTime: Int,
    val synced: Boolean = false
)

