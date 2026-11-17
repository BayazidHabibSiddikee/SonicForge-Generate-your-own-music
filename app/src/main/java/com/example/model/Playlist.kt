package com.example.model

data class Playlist(
  val id: String,
  val name: String,
  val description: String = "",
  val createdAtEpochMs: Long,
  val tracks: List<GeneratedTrack> = emptyList(),
) {
  val trackCount: Int
    get() = tracks.size

  val totalDurationMs: Long
    get() = tracks.sumOf { it.durationMs }
}
