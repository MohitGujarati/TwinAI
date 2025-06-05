package com.example.twinmind_interview_app.database.NewRoomdb

import androidx.room.*

@Dao
interface TranscriptSessionDao {
    @Insert
    suspend fun insertSession(session: TranscriptSessionEntity): Long

    @Query("SELECT * FROM transcript_sessions ORDER BY createdAt DESC")
    suspend fun getAllSessions(): List<TranscriptSessionEntity>

    @Query("SELECT * FROM transcript_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: Long): TranscriptSessionEntity?

    @Update
    suspend fun updateSession(session: TranscriptSessionEntity)

    @Delete
    suspend fun deleteSession(session: TranscriptSessionEntity)

    @Query("DELETE FROM transcript_sessions")
    suspend fun deleteAllSessions()
}
