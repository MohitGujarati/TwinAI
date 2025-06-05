package com.example.twinmind_interview_app.database.NewRoomdb

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transcript_segments",
    foreignKeys = [
        ForeignKey(
            entity = TranscriptSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class NewTranscriptSegmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long, // Foreign key: links to session
    val text: String,
    val startTime: Int,
    val endTime: Int,
    val audioFilePath: String? = null,
    val title: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
