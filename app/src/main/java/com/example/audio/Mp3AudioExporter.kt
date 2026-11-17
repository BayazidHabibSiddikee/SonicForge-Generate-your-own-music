package com.example.audio

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.model.GeneratedTrack
import de.sciss.jump3r.mp3.BitStream
import de.sciss.jump3r.mp3.GainAnalysis
import de.sciss.jump3r.mp3.ID3Tag
import de.sciss.jump3r.mp3.Lame
import de.sciss.jump3r.mp3.MPEGMode
import de.sciss.jump3r.mp3.Presets
import de.sciss.jump3r.mp3.Quantize
import de.sciss.jump3r.mp3.QuantizePVT
import de.sciss.jump3r.mp3.Reservoir
import de.sciss.jump3r.mp3.Takehiro
import de.sciss.jump3r.mp3.VBRTag
import de.sciss.jump3r.mp3.Version
import de.sciss.jump3r.mpg.Common
import de.sciss.jump3r.mpg.Interface
import de.sciss.jump3r.mpg.MPGLib
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.min

enum class AudioExportFormat(
  val extension: String,
  val mimeType: String,
  val displayName: String,
) {
  MP3(extension = "mp3", mimeType = "audio/mpeg", displayName = "MP3"),
  WAV(extension = "wav", mimeType = "audio/wav", displayName = "WAV"),
  FLAC(extension = "flac", mimeType = "audio/flac", displayName = "FLAC"),
  MIDI(extension = "mid", mimeType = "audio/midi", displayName = "MIDI"),
}

data class AudioExportBitrateOption(
  val id: String,
  val label: String,
  val bitrateKbps: Int,
  val parameterValue: Int,
)

data class AudioExportConfig(
  val format: AudioExportFormat = AudioExportFormat.MP3,
  val bitrateKbps: Int = Mp3AudioExporter.DEFAULT_BITRATE_KBPS,
  val wavBitsPerSample: Int = 16,
  val flacCompressionLevel: Int = 5,
  val midiExportMode: MidiExportMode = MidiExportMode.FULL_ARRANGEMENT,
  val normalizeOutputGain: Boolean = true,
  val targetOutputLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
) {
  val summaryLabel: String
    get() = when (format) {
      AudioExportFormat.MP3 -> "$bitrateKbps kbps · MPEG-1 Layer III (.mp3)"
      AudioExportFormat.WAV -> "${wavBitsPerSample}-bit PCM · $bitrateKbps kbps (.wav)"
      AudioExportFormat.FLAC -> "Lossless L$flacCompressionLevel · ~$bitrateKbps kbps (.flac)"
      AudioExportFormat.MIDI -> "${midiExportMode.summaryTag} · 480 PPQ (.mid)"
    }

  val selectedOptionId: String
    get() = when (format) {
      AudioExportFormat.MP3 -> "mp3_$bitrateKbps"
      AudioExportFormat.WAV -> "wav_$wavBitsPerSample"
      AudioExportFormat.FLAC -> "flac_$flacCompressionLevel"
      AudioExportFormat.MIDI -> midiExportMode.id
    }

  fun withOption(option: AudioExportBitrateOption): AudioExportConfig = when (format) {
    AudioExportFormat.MP3 -> copy(bitrateKbps = option.bitrateKbps)
    AudioExportFormat.WAV -> copy(bitrateKbps = option.bitrateKbps, wavBitsPerSample = option.parameterValue)
    AudioExportFormat.FLAC -> copy(bitrateKbps = option.bitrateKbps, flacCompressionLevel = option.parameterValue)
    AudioExportFormat.MIDI -> {
      val mode = MidiExportMode.entries.getOrElse(option.parameterValue) { MidiExportMode.FULL_ARRANGEMENT }
      copy(bitrateKbps = 0, midiExportMode = mode)
    }
  }

  companion object {
    fun optionsFor(format: AudioExportFormat): List<AudioExportBitrateOption> = when (format) {
      AudioExportFormat.MP3 -> listOf(
        AudioExportBitrateOption("mp3_128", "128 kbps", 128, 128),
        AudioExportBitrateOption("mp3_192", "192 kbps", 192, 192),
        AudioExportBitrateOption("mp3_256", "256 kbps", 256, 256),
        AudioExportBitrateOption("mp3_320", "320 kbps", 320, 320),
      )
      AudioExportFormat.WAV -> listOf(
        AudioExportBitrateOption("wav_16", "16-bit (1411 kbps)", 1411, 16),
        AudioExportBitrateOption("wav_24", "24-bit (2116 kbps)", 2116, 24),
      )
      AudioExportFormat.FLAC -> listOf(
        AudioExportBitrateOption("flac_0", "Fast (~1050 kbps)", 1050, 0),
        AudioExportBitrateOption("flac_5", "Balanced (~750 kbps)", 750, 5),
        AudioExportBitrateOption("flac_8", "Max (~650 kbps)", 650, 8),
      )
      AudioExportFormat.MIDI -> MidiExportMode.entries.mapIndexed { idx, mode ->
        AudioExportBitrateOption(
          id = mode.id,
          label = mode.label,
          bitrateKbps = 0,
          parameterValue = idx,
        )
      }
    }

    fun defaultFor(
      format: AudioExportFormat,
      normalizeOutputGain: Boolean = true,
      targetOutputLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
    ): AudioExportConfig = when (format) {
      AudioExportFormat.MP3 -> AudioExportConfig(
        format = AudioExportFormat.MP3,
        bitrateKbps = 192,
        normalizeOutputGain = normalizeOutputGain,
        targetOutputLufs = targetOutputLufs,
      )
      AudioExportFormat.WAV -> AudioExportConfig(
        format = AudioExportFormat.WAV,
        bitrateKbps = 1411,
        wavBitsPerSample = 16,
        normalizeOutputGain = normalizeOutputGain,
        targetOutputLufs = targetOutputLufs,
      )
      AudioExportFormat.FLAC -> AudioExportConfig(
        format = AudioExportFormat.FLAC,
        bitrateKbps = 750,
        flacCompressionLevel = 5,
        normalizeOutputGain = normalizeOutputGain,
        targetOutputLufs = targetOutputLufs,
      )
      AudioExportFormat.MIDI -> AudioExportConfig(
        format = AudioExportFormat.MIDI,
        bitrateKbps = 0,
        midiExportMode = MidiExportMode.FULL_ARRANGEMENT,
        normalizeOutputGain = normalizeOutputGain,
        targetOutputLufs = targetOutputLufs,
      )
    }
  }
}

data class Mp3ExportOutcome(
  val fileName: String,
  val outputUriString: String?,
  val fileSizeBytes: Long,
  val bitrateKbps: Int,
  val locationLabel: String,
  val format: AudioExportFormat = AudioExportFormat.MP3,
)

/**
 * Pure-Java LAME (Jump3r) MP3, lossless FLAC, and RIFF PCM WAV audio exporter & scoped
 * external storage / FileProvider share helper.
 * Requires zero broad storage permissions on Android 10+ (MediaStore) and uses SAF CreateDocument
 * or FileProvider ACTION_SEND across all Android versions.
 */
object Mp3AudioExporter {

  const val DEFAULT_BITRATE_KBPS = 192

  fun suggestedFileName(title: String, format: AudioExportFormat = AudioExportFormat.MP3): String {
    val clean = title
      .replace(Regex("""[^A-Za-z0-9._ -]"""), " ")
      .trim()
      .replace(Regex("""\s+"""), "_")
      .take(48)
      .ifBlank { "SonicForge_Track" }
    return "$clean.${format.extension}"
  }

  fun suggestedMp3FileName(title: String): String =
    suggestedFileName(title = title, format = AudioExportFormat.MP3)

  private fun createConfiguredLame(): Pair<Lame, ID3Tag> {
    val lame = Lame()
    val ga = GainAnalysis()
    val bs = BitStream()
    val presets = Presets()
    val qupvt = QuantizePVT()
    val qu = Quantize()
    val vbr = VBRTag()
    val ver = Version()
    val id3 = ID3Tag()
    val rv = Reservoir()
    val tak = Takehiro()
    val mpg = MPGLib()
    val intf = Interface()
    val common = Common()

    lame.setModules(ga, bs, presets, qupvt, qu, vbr, ver, id3, mpg)
    bs.setModules(ga, mpg, ver, vbr)
    id3.setModules(bs, ver)
    presets.setModules(lame)
    qu.setModules(bs, rv, qupvt, tak)
    qupvt.setModules(tak, rv, lame.enc.psy)
    rv.setModules(bs)
    tak.setModules(qupvt)
    vbr.setModules(lame, bs, ver)
    mpg.setModules(intf, common)
    intf.setModules(vbr, common)
    return lame to id3
  }

  /**
   * Encodes a [RawAudioBuffer] into valid MPEG-1 Layer III (.mp3) frames using Jump3r.
   */
  fun encodeRawBufferToMp3Bytes(
    buffer: RawAudioBuffer,
    bitrateKbps: Int = DEFAULT_BITRATE_KBPS,
    trackTitle: String? = null,
  ): ByteArray {
    if (buffer.pcmSamples.isEmpty()) {
      throw IllegalArgumentException("No PCM audio samples available to encode as MP3.")
    }

    val channels = buffer.channelCount.coerceIn(1, 2)
    val totalFrames = buffer.pcmSamples.size / channels
    if (totalFrames <= 0) {
      throw IllegalArgumentException("Audio buffer has zero frames.")
    }

    // Jump3r's lame_encode_buffer_int expects 32-bit left-justified PCM integers (short << 16).
    val left = IntArray(totalFrames)
    val right = if (channels >= 2) IntArray(totalFrames) else null
    if (channels >= 2 && right != null) {
      for (f in 0 until totalFrames) {
        left[f] = buffer.pcmSamples[f * 2].toInt() shl 16
        right[f] = buffer.pcmSamples[f * 2 + 1].toInt() shl 16
      }
    } else {
      for (f in 0 until totalFrames) {
        left[f] = buffer.pcmSamples[f].toInt() shl 16
      }
    }

    val (lame, id3) = createConfiguredLame()
    val gfp = lame.lame_init()
    try {
      gfp.in_samplerate = buffer.sampleRate
      gfp.out_samplerate = buffer.sampleRate
      gfp.num_channels = channels
      gfp.brate = bitrateKbps
      gfp.quality = 2
      gfp.bWriteVbrTag = false
      gfp.mode = if (channels >= 2) MPEGMode.JOINT_STEREO else MPEGMode.MONO

      id3.id3tag_init(gfp)
      id3.id3tag_set_artist(gfp, "SonicForge")
      if (!trackTitle.isNullOrBlank()) {
        id3.id3tag_set_title(gfp, trackTitle)
      }

      val initCode = lame.lame_init_params(gfp)
      if (initCode < 0) {
        throw IllegalStateException("LAME MP3 encoder initialization failed (code $initCode).")
      }

      val output = ByteArrayOutputStream(maxOf(4096, totalFrames / 4))
      val chunkFrames = 4096
      val mp3ChunkSize = (1.25 * chunkFrames + 7200).toInt()
      val mp3Buf = ByteArray(mp3ChunkSize)
      val leftChunk = IntArray(chunkFrames)
      val rightChunk = IntArray(chunkFrames)

      var offset = 0
      while (offset < totalFrames) {
        val count = min(chunkFrames, totalFrames - offset)
        left.copyInto(leftChunk, destinationOffset = 0, startIndex = offset, endIndex = offset + count)
        if (right != null) {
          right.copyInto(rightChunk, destinationOffset = 0, startIndex = offset, endIndex = offset + count)
        } else {
          leftChunk.copyInto(rightChunk, destinationOffset = 0, startIndex = 0, endIndex = count)
        }

        val bytesEncoded = lame.lame_encode_buffer_int(
          gfp,
          leftChunk,
          rightChunk,
          count,
          mp3Buf,
          0,
          mp3ChunkSize,
        )
        if (bytesEncoded > 0) {
          output.write(mp3Buf, 0, bytesEncoded)
        }
        offset += count
      }

      val flushed = lame.lame_encode_flush(gfp, mp3Buf, 0, mp3ChunkSize)
      if (flushed > 0) {
        output.write(mp3Buf, 0, flushed)
      }

      val result = output.toByteArray()
      if (result.isEmpty()) {
        throw IllegalStateException("MP3 encoder produced empty output.")
      }
      return result
    } finally {
      try {
        lame.lame_close(gfp)
      } catch (_: Exception) {
      }
    }
  }

  /**
   * Produces encoded audio bytes for any saved [GeneratedTrack] in the requested [AudioExportConfig]
   * (`MP3`, `WAV`, `FLAC`, or `MIDI`), applying any custom waveform envelope / mute edits when decoding PCM.
   */
  fun trackToExportBytes(
    track: GeneratedTrack,
    config: AudioExportConfig = AudioExportConfig(),
  ): ByteArray {
    if (config.format == AudioExportFormat.MIDI) {
      return MidiSequenceExporter.exportTrackToMidiBytes(
        track = track,
        mode = config.midiExportMode,
      )
    }

    val sourceFile = File(track.filePath)
    if (!sourceFile.exists()) {
      throw IllegalStateException("Source audio file is missing on disk.")
    }

    // Fast path only when source is already MP3 at default bitrate, no waveform edits exist, and gain leveling is off
    if (config.format == AudioExportFormat.MP3 &&
      config.bitrateKbps == DEFAULT_BITRATE_KBPS &&
      !track.audioEnvelope.isEdited &&
      !config.normalizeOutputGain &&
      sourceFile.extension.equals("mp3", ignoreCase = true)
    ) {
      return sourceFile.readBytes()
    }

    val decoded = FourierAudioBufferProcessor.decodeFileToRawBuffer(
      file = sourceFile,
      bridgeSource = track.sourceLabel,
    )
    val envelopedBuffer = if (track.audioEnvelope.isEdited && decoded.pcmSamples.isNotEmpty()) {
      VolumeEnvelopeDsp.applyEnvelopeOffline(decoded, track.audioEnvelope)
    } else {
      decoded
    }
    val processedBuffer = if (config.normalizeOutputGain && envelopedBuffer.pcmSamples.isNotEmpty()) {
      val norm = LoudnessNormalizerDsp.normalizePcm(
        pcmSamples = envelopedBuffer.pcmSamples,
        sampleRate = envelopedBuffer.sampleRate,
        channelCount = envelopedBuffer.channelCount,
        targetLufs = config.targetOutputLufs,
      )
      FourierAudioBufferProcessor.analyzeRawPcm(
        pcmSamples = norm.normalizedSamples,
        sampleRate = envelopedBuffer.sampleRate,
        channelCount = envelopedBuffer.channelCount,
        bridgeSource = envelopedBuffer.bridgeSource,
      )
    } else {
      envelopedBuffer
    }

    return when (config.format) {
      AudioExportFormat.MP3 -> encodeRawBufferToMp3Bytes(
        buffer = processedBuffer,
        bitrateKbps = config.bitrateKbps,
        trackTitle = track.title,
      )
      AudioExportFormat.WAV -> FlacAudioEncoder.encodeBufferToWavBytes(
        buffer = processedBuffer,
        bitsPerSample = config.wavBitsPerSample,
      )
      AudioExportFormat.FLAC -> FlacAudioEncoder.encodeBufferToFlacBytes(
        buffer = processedBuffer,
        compressionLevel = config.flacCompressionLevel,
      )
      AudioExportFormat.MIDI -> MidiSequenceExporter.exportTrackToMidiBytes(
        track = track,
        mode = config.midiExportMode,
      )
    }
  }

  /**
   * Produces MP3 bytes for any saved [GeneratedTrack] (passes through existing MP3 files or
   * decodes WAV/FLAC/OGG into raw PCM and encodes via LAME).
   */
  fun trackToMp3Bytes(track: GeneratedTrack): ByteArray =
    trackToExportBytes(track = track, config = AudioExportConfig(format = AudioExportFormat.MP3))

  /**
   * Writes an exported copy of [track] in the selected format into shared external storage
   * (`Music/SonicForge`) via scoped MediaStore on Android 10+ (requires zero storage permissions).
   */
  fun exportTrackToMediaStoreMusic(
    context: Context,
    track: GeneratedTrack,
    config: AudioExportConfig = AudioExportConfig(),
  ): Mp3ExportOutcome {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      throw UnsupportedOperationException(
        "Scoped MediaStore Music export requires Android 10+; use Choose Folder."
      )
    }

    val audioBytes = trackToExportBytes(track, config)
    val fileName = suggestedFileName(track.title, config.format)
    val resolver = context.contentResolver

    var targetRelativePath = "${Environment.DIRECTORY_MUSIC}/SonicForge"
    var collectionUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    var values = ContentValues().apply {
      put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
      put(MediaStore.Audio.Media.MIME_TYPE, config.format.mimeType)
      put(MediaStore.Audio.Media.TITLE, track.title)
      put(MediaStore.Audio.Media.ARTIST, "SonicForge")
      put(MediaStore.Audio.Media.DURATION, track.durationMs)
      put(MediaStore.Audio.Media.IS_MUSIC, 1)
      put(MediaStore.Audio.Media.RELATIVE_PATH, targetRelativePath)
      put(MediaStore.Audio.Media.IS_PENDING, 1)
    }

    var uri = try {
      resolver.insert(collectionUri, values)
    } catch (_: IllegalArgumentException) {
      null
    }

    if (uri == null && config.format == AudioExportFormat.MIDI) {
      targetRelativePath = "${Environment.DIRECTORY_DOWNLOADS}/SonicForge"
      collectionUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
      values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
        put(MediaStore.Downloads.MIME_TYPE, config.format.mimeType)
        put(MediaStore.Downloads.TITLE, track.title)
        put(MediaStore.Downloads.RELATIVE_PATH, targetRelativePath)
        put(MediaStore.Downloads.IS_PENDING, 1)
      }
      uri = resolver.insert(collectionUri, values)
    }

    val insertedUri = uri
      ?: throw IllegalStateException("Unable to create MediaStore ${config.format.displayName} entry.")

    try {
      resolver.openOutputStream(insertedUri)?.use { out ->
        out.write(audioBytes)
        out.flush()
      } ?: throw IllegalStateException("Unable to open MediaStore output stream.")

      val publishValues = ContentValues().apply {
        put(MediaStore.MediaColumns.IS_PENDING, 0)
      }
      resolver.update(insertedUri, publishValues, null, null)

      return Mp3ExportOutcome(
        fileName = fileName,
        outputUriString = insertedUri.toString(),
        fileSizeBytes = audioBytes.size.toLong(),
        bitrateKbps = config.bitrateKbps,
        locationLabel = "$targetRelativePath/$fileName",
        format = config.format,
      )
    } catch (e: Exception) {
      resolver.delete(insertedUri, null, null)
      throw e
    }
  }

  /**
   * Exports [track] in the selected format to a user-chosen SAF document [destinationUri].
   */
  fun exportTrackToUri(
    context: Context,
    track: GeneratedTrack,
    destinationUri: Uri,
    config: AudioExportConfig = AudioExportConfig(),
  ): Mp3ExportOutcome {
    val audioBytes = trackToExportBytes(track, config)
    val fileName = suggestedFileName(track.title, config.format)
    context.contentResolver.openOutputStream(destinationUri)?.use { out ->
      out.write(audioBytes)
      out.flush()
    } ?: throw IllegalStateException("Unable to write ${config.format.displayName} to selected destination.")

    return Mp3ExportOutcome(
      fileName = fileName,
      outputUriString = destinationUri.toString(),
      fileSizeBytes = audioBytes.size.toLong(),
      bitrateKbps = config.bitrateKbps,
      locationLabel = fileName,
      format = config.format,
    )
  }

  /**
   * Writes exported audio into `cacheDir/shared_mp3` and builds a FileProvider share [Intent].
   */
  fun createShareAudioIntent(
    context: Context,
    track: GeneratedTrack,
    config: AudioExportConfig = AudioExportConfig(),
  ): Pair<Intent, Mp3ExportOutcome> {
    val audioBytes = trackToExportBytes(track, config)
    val shareDir = File(context.cacheDir, "shared_mp3").apply {
      if (!exists()) mkdirs()
    }
    val fileName = suggestedFileName(track.title, config.format)
    val outFile = File(shareDir, fileName)
    outFile.writeBytes(audioBytes)

    val contentUri = FileProvider.getUriForFile(
      context,
      "${context.packageName}.fileprovider",
      outFile,
    )
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
      type = config.format.mimeType
      putExtra(Intent.EXTRA_STREAM, contentUri)
      putExtra(Intent.EXTRA_SUBJECT, track.title)
      putExtra(Intent.EXTRA_TEXT, "${track.title} — created in SonicForge")
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(sendIntent, "Share ${config.format.displayName}")
    val outcome = Mp3ExportOutcome(
      fileName = fileName,
      outputUriString = contentUri.toString(),
      fileSizeBytes = audioBytes.size.toLong(),
      bitrateKbps = config.bitrateKbps,
      locationLabel = "Ready to share ($fileName)",
      format = config.format,
    )
    return chooser to outcome
  }

  fun createShareMp3Intent(
    context: Context,
    track: GeneratedTrack,
  ): Pair<Intent, Mp3ExportOutcome> =
    createShareAudioIntent(context, track, AudioExportConfig(format = AudioExportFormat.MP3))
}
