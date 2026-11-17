package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GeneratedTrackDao {

  @Query("SELECT * FROM generated_tracks ORDER BY createdAtEpochMs DESC")
  fun observeAllTracks(): Flow<List<GeneratedTrackEntity>>

  @Query("SELECT * FROM generated_tracks WHERE isFavorite = 1 ORDER BY createdAtEpochMs DESC")
  fun observeFavoriteTracks(): Flow<List<GeneratedTrackEntity>>

  @Query("SELECT * FROM generated_tracks WHERE category = :category ORDER BY createdAtEpochMs DESC")
  fun observeTracksByCategory(category: String): Flow<List<GeneratedTrackEntity>>

  @Query("SELECT * FROM generated_tracks WHERE isFavorite = 1 AND category = :category ORDER BY createdAtEpochMs DESC")
  fun observeFavoriteTracksByCategory(category: String): Flow<List<GeneratedTrackEntity>>

  @Query("SELECT DISTINCT category FROM generated_tracks ORDER BY category ASC")
  fun observeDistinctCategories(): Flow<List<String>>

  @Query(
    """
    SELECT * FROM generated_tracks
    WHERE title LIKE '%' || :query || '%'
       OR prompt LIKE '%' || :query || '%'
       OR category LIKE '%' || :query || '%'
       OR tagsCsv LIKE '%' || :query || '%'
    ORDER BY createdAtEpochMs DESC
    """
  )
  fun searchTracks(query: String): Flow<List<GeneratedTrackEntity>>

  @Query(
    """
    SELECT * FROM generated_tracks
    WHERE isFavorite = 1
      AND (title LIKE '%' || :query || '%'
       OR prompt LIKE '%' || :query || '%'
       OR category LIKE '%' || :query || '%'
       OR tagsCsv LIKE '%' || :query || '%')
    ORDER BY createdAtEpochMs DESC
    """
  )
  fun searchFavoriteTracks(query: String): Flow<List<GeneratedTrackEntity>>

  @Query("SELECT * FROM generated_tracks ORDER BY createdAtEpochMs DESC")
  suspend fun getAllTracksOnce(): List<GeneratedTrackEntity>

  @Query("SELECT * FROM generated_tracks WHERE id = :trackId LIMIT 1")
  suspend fun getTrackById(trackId: String): GeneratedTrackEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTrack(track: GeneratedTrackEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTracks(tracks: List<GeneratedTrackEntity>)

  @Update
  suspend fun updateTrack(track: GeneratedTrackEntity)

  @Query("UPDATE generated_tracks SET category = :category, tagsCsv = :tagsCsv WHERE id = :trackId")
  suspend fun updateTrackOrganization(trackId: String, category: String, tagsCsv: String)

  @Query("UPDATE generated_tracks SET isFavorite = :isFavorite WHERE id = :trackId")
  suspend fun updateFavoriteStatus(trackId: String, isFavorite: Boolean)

  @Query("UPDATE generated_tracks SET title = :title WHERE id = :trackId")
  suspend fun updateTrackTitle(trackId: String, title: String)

  @Query("UPDATE generated_tracks SET audioEnvelopeSpec = :envelopeSpec WHERE id = :trackId")
  suspend fun updateAudioEnvelope(trackId: String, envelopeSpec: String)

  @Query("DELETE FROM generated_tracks WHERE id = :trackId")
  suspend fun deleteTrackById(trackId: String)
}
