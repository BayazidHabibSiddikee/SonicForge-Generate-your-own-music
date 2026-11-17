package com.example.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BulkRenameMode(val testTagSuffix: String) {
  PREFIX_TITLE("prefix_title"),
  PREFIX_NUMBERED("prefix_numbered"),
  TIMESTAMP("timestamp"),
}

data class BulkRenameConfig(
  val mode: BulkRenameMode = BulkRenameMode.PREFIX_TITLE,
  val prefix: String = "",
  val timestampPattern: String = DEFAULT_TIMESTAMP_PATTERN,
  val useCurrentTime: Boolean = false,
) {
  companion object {
    const val DEFAULT_TIMESTAMP_PATTERN = "yyyyMMdd_HHmmss"
    val PRESET_PATTERNS = listOf(
      "yyyyMMdd_HHmmss",
      "yyyy-MM-dd",
      "MMMdd_HHmm",
    )
  }
}

data class BulkRenamePreviewItem(
  val trackId: String,
  val oldTitle: String,
  val newTitle: String,
)

object TrackBulkRenameFormatter {

  fun isValidTimestampPattern(pattern: String): Boolean {
    val clean = pattern.trim()
    if (clean.isEmpty()) return false
    return try {
      SimpleDateFormat(clean, Locale.US)
      true
    } catch (_: IllegalArgumentException) {
      false
    }
  }

  fun canApply(config: BulkRenameConfig, selectedCount: Int): Boolean {
    if (selectedCount <= 0) return false
    return when (config.mode) {
      BulkRenameMode.PREFIX_TITLE,
      BulkRenameMode.PREFIX_NUMBERED -> config.prefix.trim().isNotEmpty()
      BulkRenameMode.TIMESTAMP -> isValidTimestampPattern(config.timestampPattern)
    }
  }

  fun generatePreviews(
    tracks: List<GeneratedTrack>,
    config: BulkRenameConfig,
    referenceNowMs: Long = System.currentTimeMillis(),
  ): List<BulkRenamePreviewItem> {
    if (tracks.isEmpty()) return emptyList()
    val cleanPrefix = config.prefix.trim()

    return when (config.mode) {
      BulkRenameMode.PREFIX_TITLE -> {
        tracks.map { track ->
          val newTitle = if (cleanPrefix.isEmpty()) {
            track.title
          } else {
            joinPrefix(cleanPrefix, track.title)
          }
          BulkRenamePreviewItem(
            trackId = track.id,
            oldTitle = track.title,
            newTitle = newTitle,
          )
        }
      }

      BulkRenameMode.PREFIX_NUMBERED -> {
        val width = tracks.size.toString().length.coerceAtLeast(2)
        val base = cleanPrefix.ifEmpty { "Track" }
        tracks.mapIndexed { index, track ->
          val num = (index + 1).toString().padStart(width, '0')
          BulkRenamePreviewItem(
            trackId = track.id,
            oldTitle = track.title,
            newTitle = "${base}_$num",
          )
        }
      }

      BulkRenameMode.TIMESTAMP -> {
        val pattern = config.timestampPattern.trim().ifEmpty { BulkRenameConfig.DEFAULT_TIMESTAMP_PATTERN }
        val formatter = try {
          SimpleDateFormat(pattern, Locale.US)
        } catch (_: IllegalArgumentException) {
          SimpleDateFormat(BulkRenameConfig.DEFAULT_TIMESTAMP_PATTERN, Locale.US)
        }

        val rawBases = tracks.mapIndexed { index, track ->
          val epoch = if (config.useCurrentTime) {
            referenceNowMs
          } else {
            track.createdAtEpochMs.takeIf { it > 0L } ?: referenceNowMs
          }
          val formattedDate = formatter.format(Date(epoch))
          val baseName = if (cleanPrefix.isNotEmpty()) {
            joinPrefix(cleanPrefix, formattedDate, separator = "_")
          } else {
            formattedDate
          }
          index to baseName
        }

        val duplicateCounts = rawBases.groupingBy { it.second }.eachCount()
        val seenIndices = mutableMapOf<String, Int>()
        val width = tracks.size.toString().length.coerceAtLeast(2)

        rawBases.map { (index, baseName) ->
          val track = tracks[index]
          val totalForBase = duplicateCounts[baseName] ?: 1
          val finalName = if (totalForBase > 1) {
            val nextSeq = (seenIndices[baseName] ?: 0) + 1
            seenIndices[baseName] = nextSeq
            "${baseName}_${nextSeq.toString().padStart(width, '0')}"
          } else {
            baseName
          }
          BulkRenamePreviewItem(
            trackId = track.id,
            oldTitle = track.title,
            newTitle = finalName,
          )
        }
      }
    }
  }

  private fun joinPrefix(prefix: String, suffix: String, separator: String = " "): String {
    return if (prefix.endsWith("-") || prefix.endsWith("_") || prefix.endsWith(" ")) {
      "$prefix$suffix"
    } else if (separator == "_") {
      "${prefix}_$suffix"
    } else {
      "$prefix - $suffix"
    }
  }
}
