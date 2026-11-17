package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.model.Playlist
import java.io.File

@Entity(tableName = "playlists")
data class PlaylistEntity(
  @PrimaryKey val id: String,
  val name: String,
  val description: String = "",
  val createdAtEpochMs: Long,
)

@Entity(
  tableName = "playlist_tracks",
  primaryKeys = ["playlistId", "trackId"],
  foreignKeys = [
    ForeignKey(
      entity = PlaylistEntity::class,
      parentColumns = ["id"],
      childColumns = ["playlistId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = GeneratedTrackEntity::class,
      parentColumns = ["id"],
      childColumns = ["trackId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [
    Index(value = ["playlistId"]),
    Index(value = ["trackId"]),
  ],
)
data class PlaylistTrackCrossRef(
  val playlistId: String,
  val trackId: String,
  val addedAtEpochMs: Long = System.currentTimeMillis(),
)

data class PlaylistWithTracks(
  @Embedded val playlist: PlaylistEntity,
  @Relation(
    parentColumn = "id",
    entityColumn = "id",
    associateBy = Junction(
      value = PlaylistTrackCrossRef::class,
      parentColumn = "playlistId",
      entityColumn = "trackId",
    ),
  )
  val tracks: List<GeneratedTrackEntity>,
)

fun PlaylistWithTracks.toDomain(requireExistingFiles: Boolean = true): Playlist {
  val domainTracks = tracks
    .filter { !requireExistingFiles || File(it.filePath).exists() }
    .sortedByDescending { it.createdAtEpochMs }
    .map { it.toDomain() }

  return Playlist(
    id = playlist.id,
    name = playlist.name,
    description = playlist.description,
    createdAtEpochMs = playlist.createdAtEpochMs,
    tracks = domainTracks,
  )
}
