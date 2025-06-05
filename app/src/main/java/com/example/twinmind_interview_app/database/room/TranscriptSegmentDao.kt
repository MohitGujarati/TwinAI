package com.example.twinmind_interview_app.database.room

// TranscriptSegmentDao.kt
import androidx.room.*

@Dao
interface TranscriptSegmentDao {
    @Insert
    suspend fun insert(segment: TranscriptSegmentEntity): Long

    @Query("SELECT * FROM transcript_segments ORDER BY endTime DESC")
    suspend fun getAll(): List<TranscriptSegmentEntity>

    @Query("SELECT * FROM transcript_segments ORDER BY startTime ASC")
    suspend fun getAllOrdered(): List<TranscriptSegmentEntity>

    @Query("SELECT * FROM transcript_segments WHERE synced = 0 ORDER BY startTime")
    suspend fun getUnsynced(): List<TranscriptSegmentEntity>

    @Update
    suspend fun update(segment: TranscriptSegmentEntity)

    @Delete
    suspend fun delete(segment: TranscriptSegmentEntity)

    @Query("DELETE FROM transcript_segments")
    suspend fun deleteAll()

    @Query("SELECT * FROM transcript_segments ORDER BY endTime DESC LIMIT 1")
    suspend fun getLatest(): TranscriptSegmentEntity?

}
