package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.audio.TrackAudioEnvelope
import com.example.model.GeneratedTrack

@Entity(tableName = "generated_tracks")
data class GeneratedTrackEntity(
  @PrimaryKey val id: String,
  val title: String,
  val prompt: String,
  val genre: String,
  val bpm: Int,
  val musicalKey: String,
  val durationMs: Long,
  val filePath: String,
  val fileSizeBytes: Long,
  val waveformPeaksCsv: String,
  val createdAtEpochMs: Long,
  val sourceLabel: String,
  val category: String = "General",
  val tagsCsv: String = "",
  val isFavorite: Boolean = false,
  val audioEnvelopeSpec: String = "",
)

fun GeneratedTrackEntity.toDomain(): GeneratedTrack {
  val peaks = if (waveformPeaksCsv.isBlank()) {
    emptyList()
  } else {
    waveformPeaksCsv.split(",").mapNotNull { it.toFloatOrNull() }
  }
  val parsedTags = tagsCsv
    .split(",")
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .distinct()

  return GeneratedTrack(
    id = id,
    title = title,
    prompt = prompt,
    genre = genre,
    bpm = bpm,
    musicalKey = musicalKey,
    durationMs = durationMs,
    filePath = filePath,
    fileSizeBytes = fileSizeBytes,
    waveformPeaks = peaks,
    createdAtEpochMs = createdAtEpochMs,
    sourceLabel = sourceLabel,
    category = category.ifBlank { genre },
    tags = parsedTags,
    isFavorite = isFavorite,
    audioEnvelope = TrackAudioEnvelope.fromJsonString(audioEnvelopeSpec),
  )
}

fun GeneratedTrack.toEntity(): GeneratedTrackEntity {
  val normalizedTags = tags
    .map { it.trim().replace(",", "") }
    .filter { it.isNotEmpty() }
    .distinct()
    .joinToString(",")

  return GeneratedTrackEntity(
    id = id,
    title = title,
    prompt = prompt,
    genre = genre,
    bpm = bpm,
    musicalKey = musicalKey,
    durationMs = durationMs,
    filePath = filePath,
    fileSizeBytes = fileSizeBytes,
    waveformPeaksCsv = waveformPeaks.joinToString(",") { "%.4f".format(it) },
    createdAtEpochMs = createdAtEpochMs,
    sourceLabel = sourceLabel,
    category = category.trim().ifBlank { genre },
    tagsCsv = normalizedTags,
    isFavorite = isFavorite,
    audioEnvelopeSpec = audioEnvelope.toJsonString(),
  )
}
