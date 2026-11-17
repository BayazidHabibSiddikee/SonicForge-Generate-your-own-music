package com.example.model

import com.example.audio.TrackAudioEnvelope

data class GeneratedTrack(
  val id: String,
  val title: String,
  val prompt: String,
  val genre: String,
  val bpm: Int,
  val musicalKey: String,
  val durationMs: Long,
  val filePath: String,
  val fileSizeBytes: Long,
  val waveformPeaks: List<Float>,
  val createdAtEpochMs: Long,
  val sourceLabel: String = "On-Device DSP",
  val category: String = genre,
  val tags: List<String> = emptyList(),
  val isFavorite: Boolean = false,
  val audioEnvelope: TrackAudioEnvelope = TrackAudioEnvelope(),
)
