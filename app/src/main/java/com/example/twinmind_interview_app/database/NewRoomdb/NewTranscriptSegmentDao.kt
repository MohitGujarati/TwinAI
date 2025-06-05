package com.example.twinmind_interview_app.database.NewRoomdb

import androidx.room.*

@Dao
interface NewTranscriptSegmentDao {
    @Insert
    suspend fun insertSegment(segment: NewTranscriptSegmentEntity): Long

    @Query("SELECT * FROM transcript_segments WHERE sessionId = :sessionId ORDER BY startTime ASC")
    suspend fun getSegmentsForSession(sessionId: Long): List<NewTranscriptSegmentEntity>

    @Update
    suspend fun updateSegment(segment: NewTranscriptSegmentEntity)

    @Delete
    suspend fun deleteSegment(segment: NewTranscriptSegmentEntity)

    @Query("DELETE FROM transcript_segments WHERE sessionId = :sessionId")
    suspend fun deleteSegmentsForSession(sessionId: Long)

    @Query("DELETE FROM transcript_segments")
    suspend fun deleteAllSegments()
}
