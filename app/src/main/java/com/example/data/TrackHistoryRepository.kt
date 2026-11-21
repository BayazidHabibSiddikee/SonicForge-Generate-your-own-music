package com.example.data

import android.content.Context
import com.example.data.local.SonicForgeDatabase
import com.example.data.local.TrackHistoryDao
import com.example.data.local.TrackHistoryItem
import com.example.data.local.TrackHistoryTypes
import com.example.data.local.toDomain
import com.example.data.local.toHistoryEntity
import com.example.model.GeneratedTrack
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repository abstracting local Room persistence for the user's generated audio track history.
 * Follows the Repository pattern so UI and ViewModels never access DAOs directly.
 */
class TrackHistoryRepository(
  private val historyDao: TrackHistoryDao,
  private val trackDao: com.example.data.local.GeneratedTrackDao? = null,
) {

  constructor(context: Context) : this(
    historyDao = SonicForgeDatabase.getInstance(context).trackHistoryDao(),
    trackDao = SonicForgeDatabase.getInstance(context).generatedTrackDao(),
  )

  val allHistoryFlow: Flow<List<TrackHistoryItem>> =
    historyDao.observeAllHistory().map { entities ->
      entities.map { entity ->
        entity.toDomain(fileExists = File(entity.filePath).exists())
      }
    }

  val historyCountFlow: Flow<Int> = historyDao.observeHistoryCount()

  fun observeFilteredHistory(
    instrumentType: String = TrackHistoryTypes.ALL,
    query: String = "",
  ): Flow<List<TrackHistoryItem>> {
    val trimmed = query.trim()
    val hasTypeFilter = instrumentType.isNotBlank() && instrumentType != TrackHistoryTypes.ALL
    val base = when {
      trimmed.isNotEmpty() -> historyDao.searchHistory(trimmed)
      hasTypeFilter -> historyDao.observeHistoryByType(instrumentType)
      else -> historyDao.observeAllHistory()
    }
    return base.map { entities ->
      entities
        .filter { entity -> !hasTypeFilter || entity.instrumentType.equals(instrumentType, ignoreCase = true) }
        .map { entity -> entity.toDomain(fileExists = File(entity.filePath).exists()) }
    }
  }

  suspend fun recordGeneratedTrack(
    track: GeneratedTrack,
    instrumentType: String = TrackHistoryTypes.inferFromTrack(track),
    engineOrSource: String = track.sourceLabel.ifBlank { "Studio Synth" },
  ) = withContext(Dispatchers.IO) {
    historyDao.insertHistoryEntry(
      track.toHistoryEntity(
        instrumentType = instrumentType,
        engineOrSource = engineOrSource,
      ),
    )
  }

  suspend fun recordTrackPlayed(trackId: String) = withContext(Dispatchers.IO) {
    if (trackId.isBlank()) return@withContext
    historyDao.recordPlayback(
      trackId = trackId,
      playedAtEpochMs = System.currentTimeMillis(),
    )
  }

  suspend fun syncTrackTitle(trackId: String, newTitle: String) = withContext(Dispatchers.IO) {
    if (trackId.isBlank() || newTitle.isBlank()) return@withContext
    historyDao.updateTitleByTrackId(trackId, newTitle.trim())
  }

  suspend fun deleteHistoryEntry(entryId: String) = withContext(Dispatchers.IO) {
    historyDao.deleteById(entryId)
  }

  suspend fun deleteHistoryForTrack(trackId: String) = withContext(Dispatchers.IO) {
    historyDao.deleteByTrackId(trackId)
  }

  suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
    historyDao.clearAllHistory()
  }

  /**
   * Populates the local history table from existing generated tracks on first upgrade so past
   * creations already stored on-device appear in Track History right away.
   */
  suspend fun backfillFromExistingTracksIfEmpty() = withContext(Dispatchers.IO) {
    val dao = trackDao ?: return@withContext
    if (historyDao.getHistoryCountOnce() > 0) return@withContext
    val existing = dao.getAllTracksOnce()
      .filter { File(it.filePath).exists() }
      .map { it.toDomain() }
    if (existing.isEmpty()) return@withContext
    historyDao.insertHistoryEntries(existing.map { it.toHistoryEntity() })
  }
}
