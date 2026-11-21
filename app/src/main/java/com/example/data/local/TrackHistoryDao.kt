package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackHistoryDao {

  @Query("SELECT * FROM track_generation_history ORDER BY createdAtEpochMs DESC")
  fun observeAllHistory(): Flow<List<TrackHistoryEntity>>

  @Query(
    "SELECT * FROM track_generation_history WHERE instrumentType = :instrumentType ORDER BY createdAtEpochMs DESC",
  )
  fun observeHistoryByType(instrumentType: String): Flow<List<TrackHistoryEntity>>

  @Query(
    """
    SELECT * FROM track_generation_history
    WHERE title LIKE '%' || :query || '%'
       OR prompt LIKE '%' || :query || '%'
       OR engineOrSource LIKE '%' || :query || '%'
       OR instrumentType LIKE '%' || :query || '%'
       OR genre LIKE '%' || :query || '%'
    ORDER BY createdAtEpochMs DESC
    """,
  )
  fun searchHistory(query: String): Flow<List<TrackHistoryEntity>>

  @Query("SELECT COUNT(*) FROM track_generation_history")
  fun observeHistoryCount(): Flow<Int>

  @Query("SELECT COUNT(*) FROM track_generation_history")
  suspend fun getHistoryCountOnce(): Int

  @Query("SELECT * FROM track_generation_history WHERE trackId = :trackId LIMIT 1")
  suspend fun getByTrackId(trackId: String): TrackHistoryEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertHistoryEntry(entry: TrackHistoryEntity)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertHistoryEntries(entries: List<TrackHistoryEntity>)

  @Query(
    """
    UPDATE track_generation_history
    SET playCount = playCount + 1, lastPlayedAtEpochMs = :playedAtEpochMs
    WHERE trackId = :trackId
    """,
  )
  suspend fun recordPlayback(trackId: String, playedAtEpochMs: Long)

  @Query("UPDATE track_generation_history SET title = :title WHERE trackId = :trackId")
  suspend fun updateTitleByTrackId(trackId: String, title: String)

  @Query("DELETE FROM track_generation_history WHERE id = :id")
  suspend fun deleteById(id: String)

  @Query("DELETE FROM track_generation_history WHERE trackId = :trackId")
  suspend fun deleteByTrackId(trackId: String)

  @Query("DELETE FROM track_generation_history")
  suspend fun clearAllHistory()
}
