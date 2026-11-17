package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

  @Transaction
  @Query("SELECT * FROM playlists ORDER BY createdAtEpochMs DESC")
  fun observePlaylistsWithTracks(): Flow<List<PlaylistWithTracks>>

  @Transaction
  @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
  fun observePlaylistWithTracks(playlistId: String): Flow<PlaylistWithTracks?>

  @Query("SELECT * FROM playlists ORDER BY createdAtEpochMs DESC")
  suspend fun getAllPlaylistsOnce(): List<PlaylistEntity>

  @Transaction
  @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
  suspend fun getPlaylistWithTracksOnce(playlistId: String): PlaylistWithTracks?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPlaylist(playlist: PlaylistEntity)

  @Query("UPDATE playlists SET name = :name, description = :description WHERE id = :playlistId")
  suspend fun updatePlaylistDetails(playlistId: String, name: String, description: String)

  @Query("DELETE FROM playlists WHERE id = :playlistId")
  suspend fun deletePlaylistById(playlistId: String)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun addTrackToPlaylist(crossRef: PlaylistTrackCrossRef)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun addTracksToPlaylist(crossRefs: List<PlaylistTrackCrossRef>)

  @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
  suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String)

  @Query("SELECT playlistId FROM playlist_tracks WHERE trackId = :trackId")
  fun observePlaylistIdsForTrack(trackId: String): Flow<List<String>>
}
