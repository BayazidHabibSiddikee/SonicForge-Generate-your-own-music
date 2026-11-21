package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.GeneratedTrack
import java.util.UUID

/**
 * Room entity persisting the chronological history of user-generated audio tracks
 * (Prompt API/DSP generations, Piano takes, Guitar tracks, Drum beats, Sound Packs, and Merged mixes).
 */
@Entity(
  tableName = "track_generation_history",
  indices = [
    Index(value = ["trackId"]),
    Index(value = ["createdAtEpochMs"]),
    Index(value = ["instrumentType"]),
  ],
)
data class TrackHistoryEntity(
  @PrimaryKey val id: String = UUID.randomUUID().toString(),
  val trackId: String,
  val title: String,
  val prompt: String,
  val engineOrSource: String,
  val instrumentType: String,
  val genre: String,
  val bpm: Int,
  val durationMs: Long,
  val filePath: String,
  val createdAtEpochMs: Long,
  val lastPlayedAtEpochMs: Long = 0L,
  val playCount: Int = 0,
)

data class TrackHistoryItem(
  val id: String,
  val trackId: String,
  val title: String,
  val prompt: String,
  val engineOrSource: String,
  val instrumentType: String,
  val genre: String,
  val bpm: Int,
  val durationMs: Long,
  val filePath: String,
  val createdAtEpochMs: Long,
  val lastPlayedAtEpochMs: Long,
  val playCount: Int,
  val fileExists: Boolean = true,
)

object TrackHistoryTypes {
  const val ALL = "All"
  const val PROMPT = "Prompt"
  const val PIANO = "Piano"
  const val GUITAR = "Guitar"
  const val DRUMS = "Drums"
  const val MERGED = "Merged"
  const val SOUND_PACK = "Sound Pack"

  fun inferFromTrack(track: GeneratedTrack): String {
    val src = track.sourceLabel.lowercase()
    val cat = track.category.lowercase()
    return when {
      cat == "piano" || src.startsWith("piano") -> PIANO
      cat == "guitar" || src.startsWith("guitar") -> GUITAR
      cat == "drums" || src.startsWith("drums") || src.contains("sequencer") -> DRUMS
      cat == "merged" || src.contains("mixdown") -> MERGED
      src.startsWith("sound pack") -> SOUND_PACK
      else -> PROMPT
    }
  }
}

fun TrackHistoryEntity.toDomain(fileExists: Boolean = true): TrackHistoryItem = TrackHistoryItem(
  id = id,
  trackId = trackId,
  title = title,
  prompt = prompt,
  engineOrSource = engineOrSource,
  instrumentType = instrumentType,
  genre = genre,
  bpm = bpm,
  durationMs = durationMs,
  filePath = filePath,
  createdAtEpochMs = createdAtEpochMs,
  lastPlayedAtEpochMs = lastPlayedAtEpochMs,
  playCount = playCount,
  fileExists = fileExists,
)

fun GeneratedTrack.toHistoryEntity(
  instrumentType: String = TrackHistoryTypes.inferFromTrack(this),
  engineOrSource: String = sourceLabel.ifBlank { "Studio Synth" },
): TrackHistoryEntity = TrackHistoryEntity(
  id = "hist_$id",
  trackId = id,
  title = title,
  prompt = prompt,
  engineOrSource = engineOrSource,
  instrumentType = instrumentType,
  genre = genre.ifBlank { category },
  bpm = bpm,
  durationMs = durationMs,
  filePath = filePath,
  createdAtEpochMs = createdAtEpochMs,
  lastPlayedAtEpochMs = 0L,
  playCount = 0,
)
