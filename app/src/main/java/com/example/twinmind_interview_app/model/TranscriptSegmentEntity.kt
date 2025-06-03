package com.example.twinmind_interview_app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// Update your TranscriptSegmentEntity to include audioFilePath

@Entity(tableName = "transcript_segments")
data class TranscriptSegmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    var text: String,
    val startTime: Int,
    val endTime: Int,
    var synced: Boolean = false,
    val audioFilePath: String? = null, // Add this field
    val createdAt: Long = System.currentTimeMillis()
)



