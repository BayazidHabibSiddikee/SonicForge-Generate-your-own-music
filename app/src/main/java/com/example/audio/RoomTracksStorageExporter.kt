package com.example.audio

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import com.example.model.GeneratedTrack
import java.io.File
import java.util.Locale

private const val TAG = "RoomTracksStorageExporter"

data class RoomExportProgress(
  val exportedCount: Int,
  val totalCount: Int,
  val currentTrackTitle: String,
)

data class BatchStorageExportResult(
  val exportedCount: Int,
  val skippedCount: Int,
  val failedCount: Int,
  val totalBytes: Long,
  val directoryLabel: String,
  val format: AudioExportFormat,
  val exportedFileNames: List<String>,
)

/**
 * Exports generated audio tracks stored in the local Room database to the device's storage
 * directory using zero-permission Scoped MediaStore (`Music/SonicForge` on Android 10+) or the
 * Storage Access Framework (`OpenDocumentTree` directory URI on Android 7.0+).
 */
object RoomTracksStorageExporter {

  val supportedBatchFormats: List<AudioExportFormat> = listOf(
    AudioExportFormat.MP3,
    AudioExportFormat.WAV,
    AudioExportFormat.FLAC,
  )

  fun uniqueFileName(
    trackTitle: String,
    format: AudioExportFormat,
    usedLowerNames: MutableSet<String>,
  ): String {
    val baseFile = Mp3AudioExporter.suggestedFileName(trackTitle, format)
    val ext = ".${format.extension}"
    val stem = baseFile.removeSuffix(ext)
    var candidate = baseFile
    var suffix = 2
    while (!usedLowerNames.add(candidate.lowercase(Locale.US))) {
      candidate = "${stem}_${suffix}$ext"
      suffix++
    }
    return candidate
  }

  /**
   * Batch-exports Room tracks into shared external storage (`Music/SonicForge`) via Scoped
   * MediaStore on Android 10+ (requires zero broad storage permissions).
   */
  fun exportTracksToMediaStoreDirectory(
    context: Context,
    tracks: List<GeneratedTrack>,
    config: AudioExportConfig = AudioExportConfig(format = AudioExportFormat.MP3),
    onProgress: (RoomExportProgress) -> Unit = {},
  ): BatchStorageExportResult {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      throw UnsupportedOperationException(
        "Direct Music/SonicForge folder export requires Android 10+; use Choose Folder.",
      )
    }

    val validTracks = tracks.filter { File(it.filePath).exists() }
    val skippedMissing = tracks.size - validTracks.size
    if (validTracks.isEmpty()) {
      throw IllegalStateException("No playable audio files found in the local Room database.")
    }

    val resolver = context.contentResolver
    val relativePath = "${Environment.DIRECTORY_MUSIC}/SonicForge"
    val usedNames = mutableSetOf<String>()
    val exportedFiles = mutableListOf<String>()
    var totalBytes = 0L
    var failedCount = 0

    validTracks.forEachIndexed { index, track ->
      onProgress(
        RoomExportProgress(
          exportedCount = index,
          totalCount = validTracks.size,
          currentTrackTitle = track.title,
        ),
      )
      val fileName = uniqueFileName(track.title, config.format, usedNames)
      var insertedUri: Uri? = null
      try {
        val audioBytes = Mp3AudioExporter.trackToExportBytes(track, config)
        val values = ContentValues().apply {
          put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
          put(MediaStore.Audio.Media.MIME_TYPE, config.format.mimeType)
          put(MediaStore.Audio.Media.TITLE, track.title)
          put(MediaStore.Audio.Media.ARTIST, "SonicForge")
          put(MediaStore.Audio.Media.DURATION, track.durationMs)
          put(MediaStore.Audio.Media.IS_MUSIC, 1)
          put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
          put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        insertedUri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
          ?: throw IllegalStateException("MediaStore declined creating $fileName")

        resolver.openOutputStream(insertedUri)?.use { out ->
          out.write(audioBytes)
          out.flush()
        } ?: throw IllegalStateException("Could not open MediaStore stream for $fileName")

        val publishValues = ContentValues().apply {
          put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        resolver.update(insertedUri, publishValues, null, null)

        totalBytes += audioBytes.size.toLong()
        exportedFiles.add(fileName)
      } catch (e: Exception) {
        Log.e(TAG, "Failed exporting Room track '${track.title}' to MediaStore", e)
        insertedUri?.let { runCatching { resolver.delete(it, null, null) } }
        failedCount++
      }
    }

    onProgress(
      RoomExportProgress(
        exportedCount = exportedFiles.size,
        totalCount = validTracks.size,
        currentTrackTitle = "",
      ),
    )

    return BatchStorageExportResult(
      exportedCount = exportedFiles.size,
      skippedCount = skippedMissing,
      failedCount = failedCount,
      totalBytes = totalBytes,
      directoryLabel = relativePath,
      format = config.format,
      exportedFileNames = exportedFiles,
    )
  }

  /**
   * Batch-exports Room tracks into any user-selected device storage directory via the Storage
   * Access Framework (`ActivityResultContracts.OpenDocumentTree()`). Requires zero storage
   * permissions on all Android versions (API 24+).
   */
  fun exportTracksToDirectoryTreeUri(
    context: Context,
    tracks: List<GeneratedTrack>,
    treeUri: Uri,
    config: AudioExportConfig = AudioExportConfig(format = AudioExportFormat.MP3),
    onProgress: (RoomExportProgress) -> Unit = {},
  ): BatchStorageExportResult {
    val validTracks = tracks.filter { File(it.filePath).exists() }
    val skippedMissing = tracks.size - validTracks.size
    if (validTracks.isEmpty()) {
      throw IllegalStateException("No playable audio files found in the local Room database.")
    }

    val resolver = context.contentResolver
    val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
    val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
    val folderLabel = formatTreeUriLabel(treeDocId)

    val usedNames = mutableSetOf<String>()
    val exportedFiles = mutableListOf<String>()
    var totalBytes = 0L
    var failedCount = 0

    validTracks.forEachIndexed { index, track ->
      onProgress(
        RoomExportProgress(
          exportedCount = index,
          totalCount = validTracks.size,
          currentTrackTitle = track.title,
        ),
      )
      val fileName = uniqueFileName(track.title, config.format, usedNames)
      var createdDocUri: Uri? = null
      try {
        val audioBytes = Mp3AudioExporter.trackToExportBytes(track, config)
        createdDocUri = DocumentsContract.createDocument(
          resolver,
          parentDocUri,
          config.format.mimeType,
          fileName,
        ) ?: throw IllegalStateException("Unable to create $fileName in selected folder.")

        resolver.openOutputStream(createdDocUri)?.use { out ->
          out.write(audioBytes)
          out.flush()
        } ?: throw IllegalStateException("Unable to open output stream for $fileName.")

        totalBytes += audioBytes.size.toLong()
        exportedFiles.add(fileName)
      } catch (e: Exception) {
        Log.e(TAG, "Failed exporting Room track '${track.title}' to SAF directory", e)
        createdDocUri?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
        failedCount++
      }
    }

    onProgress(
      RoomExportProgress(
        exportedCount = exportedFiles.size,
        totalCount = validTracks.size,
        currentTrackTitle = "",
      ),
    )

    return BatchStorageExportResult(
      exportedCount = exportedFiles.size,
      skippedCount = skippedMissing,
      failedCount = failedCount,
      totalBytes = totalBytes,
      directoryLabel = folderLabel,
      format = config.format,
      exportedFileNames = exportedFiles,
    )
  }

  /**
   * Exports Room tracks directly into a concrete filesystem directory [targetDir] (used for
   * app-scoped external Music directory export or local JVM verification).
   */
  fun exportTracksToLocalDirectory(
    tracks: List<GeneratedTrack>,
    targetDir: File,
    config: AudioExportConfig = AudioExportConfig(format = AudioExportFormat.MP3),
    onProgress: (RoomExportProgress) -> Unit = {},
  ): BatchStorageExportResult {
    if (!targetDir.exists()) {
      targetDir.mkdirs()
    }
    val validTracks = tracks.filter { File(it.filePath).exists() }
    val skippedMissing = tracks.size - validTracks.size
    if (validTracks.isEmpty()) {
      throw IllegalStateException("No playable audio files found to export.")
    }

    val usedNames = mutableSetOf<String>()
    val exportedFiles = mutableListOf<String>()
    var totalBytes = 0L
    var failedCount = 0

    validTracks.forEachIndexed { index, track ->
      onProgress(
        RoomExportProgress(
          exportedCount = index,
          totalCount = validTracks.size,
          currentTrackTitle = track.title,
        ),
      )
      val fileName = uniqueFileName(track.title, config.format, usedNames)
      val outFile = File(targetDir, fileName)
      try {
        val audioBytes = Mp3AudioExporter.trackToExportBytes(track, config)
        outFile.writeBytes(audioBytes)
        totalBytes += audioBytes.size.toLong()
        exportedFiles.add(fileName)
      } catch (e: Exception) {
        if (outFile.exists()) outFile.delete()
        failedCount++
      }
    }

    onProgress(
      RoomExportProgress(
        exportedCount = exportedFiles.size,
        totalCount = validTracks.size,
        currentTrackTitle = "",
      ),
    )

    return BatchStorageExportResult(
      exportedCount = exportedFiles.size,
      skippedCount = skippedMissing,
      failedCount = failedCount,
      totalBytes = totalBytes,
      directoryLabel = targetDir.name.ifBlank { targetDir.absolutePath },
      format = config.format,
      exportedFileNames = exportedFiles,
    )
  }

  fun formatTreeUriLabel(treeDocId: String): String {
    val cleaned = treeDocId
      .substringAfter(':', missingDelimiterValue = treeDocId)
      .trim('/')
    return cleaned.ifBlank { "Selected Storage Folder" }
  }

  fun formatTotalSizeLabel(bytes: Long): String {
    return when {
      bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
      else -> "${(bytes / 1024L).coerceAtLeast(1L)} KB"
    }
  }
}
