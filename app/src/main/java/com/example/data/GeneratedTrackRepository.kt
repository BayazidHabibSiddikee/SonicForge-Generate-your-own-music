package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.example.ai.PlaceholderTrackAutoTagger
import com.example.ai.TrackAutoTagResult
import com.example.ai.TrackAutoTagger
import com.example.audio.AudioDownloadProgress
import com.example.audio.AudioExportConfig
import com.example.audio.AudioExportFormat
import com.example.audio.AudioWaveformExtractor
import com.example.audio.FourierAudioBufferProcessor
import com.example.audio.GuitarChordShape
import com.example.audio.GuitarStrumStyle
import com.example.audio.GuitarVoice
import com.example.audio.HfMusicSpace
import com.example.audio.HuggingFaceSpaceClient
import com.example.audio.InstrumentSynthDsp
import com.example.audio.LoudnessNormalizerDsp
import com.example.audio.MixStemSpec
import com.example.audio.Mp3AudioExporter
import com.example.audio.Mp3ExportOutcome
import com.example.audio.MultiTrackMixerDsp
import com.example.audio.PianoProgressionPreset
import com.example.audio.PianoVoice
import com.example.audio.PitchShiftDsp
import com.example.audio.RawBufferDiagnostics
import com.example.audio.RecordedNoteEvent
import com.example.audio.ReverbSettings
import com.example.audio.RoomReverbDsp
import com.example.audio.SegmentedAudioDownloader
import com.example.audio.StepSequencerDsp
import com.example.audio.StepSequencerState
import com.example.audio.StudioSoundResource
import com.example.audio.TrackAudioEnvelope
import com.example.audio.WavTrackSynthesizer
import com.example.audio.DrumKitPreset
import com.example.audio.RecordedDrumHit
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistTrackCrossRef
import com.example.data.local.SonicForgeDatabase
import com.example.data.local.TrackHistoryTypes
import com.example.data.local.toDomain
import com.example.data.local.toEntity
import com.example.data.local.toHistoryEntity
import androidx.room.withTransaction
import com.example.model.BulkRenameConfig
import com.example.model.GeneratedTrack
import com.example.model.Playlist
import com.example.model.TrackBulkRenameFormatter
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class TrackWithBufferDiagnostics(
  val track: GeneratedTrack,
  val diagnostics: RawBufferDiagnostics,
  val autoTagResult: TrackAutoTagResult? = null,
)

data class AiArrangedSessionResult(
  val masterTrack: GeneratedTrack,
  val masterDiagnostics: RawBufferDiagnostics,
  val stemTrackIds: List<String>,
  val stemGains: Map<String, Float>,
)

class GeneratedTrackRepository(
  private val context: Context,
  private val autoTagger: TrackAutoTagger = PlaceholderTrackAutoTagger(),
) {

  private val dao = SonicForgeDatabase.getInstance(context).generatedTrackDao()
  private val playlistDao = SonicForgeDatabase.getInstance(context).playlistDao()
  private val historyDao = SonicForgeDatabase.getInstance(context).trackHistoryDao()
  private val hfSpaceClient = HuggingFaceSpaceClient()
  private val segmentedDownloader = SegmentedAudioDownloader()
  private val tracksDir = File(context.filesDir, "generated_tracks")

  val tracksFlow: Flow<List<GeneratedTrack>> =
    dao.observeAllTracks().map { entities ->
      entities
        .filter { File(it.filePath).exists() }
        .map { it.toDomain() }
    }

  val playlistsFlow: Flow<List<Playlist>> =
    playlistDao.observePlaylistsWithTracks().map { list ->
      list.map { it.toDomain(requireExistingFiles = true) }
    }

  val categoriesFlow: Flow<List<String>> = dao.observeDistinctCategories()

  fun observeTracksFiltered(
    category: String?,
    query: String,
    favoritesOnly: Boolean = false,
  ): Flow<List<GeneratedTrack>> {
    val trimmedQuery = query.trim()
    val hasCategoryFilter = !category.isNullOrBlank() && category != ALL_CATEGORIES
    val baseFlow = when {
      trimmedQuery.isNotEmpty() && favoritesOnly -> dao.searchFavoriteTracks(trimmedQuery)
      trimmedQuery.isNotEmpty() -> dao.searchTracks(trimmedQuery)
      favoritesOnly && hasCategoryFilter -> dao.observeFavoriteTracksByCategory(category!!)
      favoritesOnly -> dao.observeFavoriteTracks()
      hasCategoryFilter -> dao.observeTracksByCategory(category!!)
      else -> dao.observeAllTracks()
    }
    return baseFlow.map { entities ->
      entities
        .filter { File(it.filePath).exists() }
        .filter { entity ->
          !hasCategoryFilter || entity.category == category
        }
        .filter { entity ->
          !favoritesOnly || entity.isFavorite
        }
        .map { it.toDomain() }
    }
  }

  suspend fun ensureSeeded(): List<GeneratedTrack> = withContext(Dispatchers.IO) {
    if (!tracksDir.exists()) {
      tracksDir.mkdirs()
    }

    val existing = dao.getAllTracksOnce()
      .filter { File(it.filePath).exists() }
      .map { it.toDomain() }

    if (existing.isNotEmpty()) {
      return@withContext existing
    }

    val starterPrompts = listOf(
      "Neon midnight synthwave drive 112 bpm",
      "Rainy lo-fi rhodes study beat 84 bpm",
    )
    val seeded = starterPrompts.mapIndexed { index, prompt ->
      val track = WavTrackSynthesizer.synthesizeToFile(prompt, tracksDir)
      track.copy(
        createdAtEpochMs = System.currentTimeMillis() + index,
        sourceLabel = "Fourier Synth",
      )
    }

    dao.insertTracks(seeded.map { it.toEntity() })
    historyDao.insertHistoryEntries(
      seeded.map { it.toHistoryEntity(instrumentType = TrackHistoryTypes.PROMPT, engineOrSource = it.sourceLabel) },
    )
    seeded.sortedByDescending { it.createdAtEpochMs }
  }

  suspend fun generateTrack(
    prompt: String,
    targetLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
    applyPostGain: Boolean = true,
  ): GeneratedTrack = generateTrackWithBuffer(prompt, targetLufs, applyPostGain).track

  suspend fun generateTrackWithBuffer(
    prompt: String,
    targetLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
    applyPostGain: Boolean = true,
  ): TrackWithBufferDiagnostics = withContext(Dispatchers.IO) {
    val (created, rawBuffer) = WavTrackSynthesizer.synthesizeWithRawBuffer(
      prompt = prompt,
      outputDir = tracksDir,
      targetLufs = targetLufs,
      applyPostGain = applyPostGain,
    )
    val baseTrack = created.copy(sourceLabel = "Fourier Synth")
    val tagResult = autoTagger.analyzeTrack(
      PlaceholderTrackAutoTagger.buildInput(track = baseTrack, rawBuffer = rawBuffer)
    )
    val saved = baseTrack.copy(
      category = tagResult.suggestedCategory,
      tags = (baseTrack.tags + tagResult.suggestedTags).distinct(),
    )
    dao.insertTrack(saved.toEntity())
    historyDao.insertHistoryEntry(
      saved.toHistoryEntity(
        instrumentType = TrackHistoryTypes.PROMPT,
        engineOrSource = saved.sourceLabel,
      ),
    )
    TrackWithBufferDiagnostics(
      track = saved,
      diagnostics = rawBuffer.toDiagnostics(),
      autoTagResult = tagResult,
    )
  }

  suspend fun renderSequencerPatternToTrack(
    sequencerState: StepSequencerState,
    targetLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
    applyPostGain: Boolean = true,
  ): TrackWithBufferDiagnostics = withContext(Dispatchers.IO) {
    if (!tracksDir.exists()) {
      tracksDir.mkdirs()
    }
    if (sequencerState.activeStepCount <= 0) {
      throw IllegalArgumentException("Program at least one step in the sequencer grid before rendering.")
    }

    val rawBuffer = StepSequencerDsp.renderPatternToRawBuffer(
      state = sequencerState,
      bars = 4,
      targetLufs = targetLufs,
      applyPostGain = applyPostGain,
    )
    val id = UUID.randomUUID().toString()
    val wavFile = File(tracksDir, "seq_$id.wav")
    FourierAudioBufferProcessor.writeWavFile(wavFile, rawBuffer)

    val presetTitle = sequencerState.selectedPresetName
      .takeIf { it.isNotBlank() && it != "Blank" && it != "Custom" }
      ?: "Drum Beat"
    val kitLabel = sequencerState.drumKit.label
    val trackTitle = "$presetTitle ($kitLabel) ${sequencerState.bpm} BPM"
    val baseTrack = GeneratedTrack(
      id = id,
      title = trackTitle,
      prompt = "Drum studio $kitLabel · $presetTitle at ${sequencerState.bpm} BPM",
      genre = "Drums",
      bpm = sequencerState.bpm,
      musicalKey = "C Min",
      durationMs = rawBuffer.durationMs,
      filePath = wavFile.absolutePath,
      fileSizeBytes = wavFile.length(),
      waveformPeaks = StepSequencerDsp.extractWaveformPeaks(rawBuffer),
      createdAtEpochMs = System.currentTimeMillis(),
      sourceLabel = "Drums · $kitLabel",
      category = "Drums",
      tags = listOf("Drums", kitLabel, "${sequencerState.bpm} BPM"),
    )
    val tagResult = autoTagger.analyzeTrack(
      PlaceholderTrackAutoTagger.buildInput(track = baseTrack, rawBuffer = rawBuffer)
    )
    val saved = baseTrack.copy(
      category = "Drums",
      tags = (baseTrack.tags + tagResult.suggestedTags).distinct().take(4),
    )
    dao.insertTrack(saved.toEntity())
    historyDao.insertHistoryEntry(
      saved.toHistoryEntity(
        instrumentType = TrackHistoryTypes.DRUMS,
        engineOrSource = saved.sourceLabel,
      ),
    )
    TrackWithBufferDiagnostics(
      track = saved,
      diagnostics = rawBuffer.toDiagnostics(),
      autoTagResult = tagResult,
    )
  }

  suspend fun generateDrumPadTakeTrack(
    kit: DrumKitPreset,
    recordedHits: List<RecordedDrumHit>,
    bpm: Int,
    targetLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
    applyPostGain: Boolean = true,
  ): TrackWithBufferDiagnostics = withContext(Dispatchers.IO) {
    if (!tracksDir.exists()) {
      tracksDir.mkdirs()
    }
    val rawBuffer = InstrumentSynthDsp.synthesizeDrumPadTakeToBuffer(
      kit = kit,
      recordedHits = recordedHits,
      bpm = bpm,
      targetLufs = targetLufs,
      applyPostGain = applyPostGain,
    )
    val id = UUID.randomUUID().toString()
    val wavFile = File(tracksDir, "drum_take_$id.wav")
    FourierAudioBufferProcessor.writeWavFile(wavFile, rawBuffer)

    val title = "${kit.label} · Live Pads (${recordedHits.size} hits)"
    val saved = GeneratedTrack(
      id = id,
      title = title,
      prompt = "Live drum pad take on ${kit.label} (${recordedHits.size} hits at $bpm BPM)",
      genre = "Drums",
      bpm = bpm,
      musicalKey = "Perc",
      durationMs = rawBuffer.durationMs,
      filePath = wavFile.absolutePath,
      fileSizeBytes = wavFile.length(),
      waveformPeaks = FourierAudioBufferProcessor.extractWaveformPeaks(rawBuffer.pcmSamples),
      createdAtEpochMs = System.currentTimeMillis(),
      sourceLabel = "Drums · ${kit.label}",
      category = "Drums",
      tags = listOf("Drums", kit.label, "Live Pads"),
    )
    dao.insertTrack(saved.toEntity())
    historyDao.insertHistoryEntry(
      saved.toHistoryEntity(
        instrumentType = TrackHistoryTypes.DRUMS,
        engineOrSource = saved.sourceLabel,
      ),
    )
    TrackWithBufferDiagnostics(
      track = saved,
      diagnostics = rawBuffer.toDiagnostics(),
    )
  }

  suspend fun generatePianoTrack(
    voice: PianoVoice,
    progression: PianoProgressionPreset,
    recordedNotes: List<RecordedNoteEvent>,
    bpm: Int,
    bars: Int = 4,
    targetLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
    applyPostGain: Boolean = true,
  ): TrackWithBufferDiagnostics = withContext(Dispatchers.IO) {
    if (!tracksDir.exists()) {
      tracksDir.mkdirs()
    }
    val rawBuffer = InstrumentSynthDsp.synthesizePianoTrackToBuffer(
      voice = voice,
      progression = progression,
      recordedNotes = recordedNotes,
      bpm = bpm,
      bars = bars,
      targetLufs = targetLufs,
      applyPostGain = applyPostGain,
    )
    val id = UUID.randomUUID().toString()
    val wavFile = File(tracksDir, "piano_$id.wav")
    FourierAudioBufferProcessor.writeWavFile(wavFile, rawBuffer)

    val modeLabel = if (recordedNotes.isNotEmpty()) "Live Take" else progression.label
    val title = "${voice.label} · $modeLabel"
    val baseTrack = GeneratedTrack(
      id = id,
      title = title,
      prompt = "${voice.label} piano performance ($modeLabel) at $bpm BPM",
      genre = "Piano",
      bpm = bpm,
      musicalKey = progression.keyLabel,
      durationMs = rawBuffer.durationMs,
      filePath = wavFile.absolutePath,
      fileSizeBytes = wavFile.length(),
      waveformPeaks = FourierAudioBufferProcessor.extractWaveformPeaks(rawBuffer.pcmSamples),
      createdAtEpochMs = System.currentTimeMillis(),
      sourceLabel = "Piano · ${voice.label}",
      category = "Piano",
      tags = listOf("Piano", voice.label, "${bpm} BPM"),
    )
    dao.insertTrack(baseTrack.toEntity())
    historyDao.insertHistoryEntry(
      baseTrack.toHistoryEntity(
        instrumentType = TrackHistoryTypes.PIANO,
        engineOrSource = baseTrack.sourceLabel,
      ),
    )
    TrackWithBufferDiagnostics(
      track = baseTrack,
      diagnostics = rawBuffer.toDiagnostics(),
    )
  }

  suspend fun generateGuitarTrack(
    voice: GuitarVoice,
    chordSequence: List<GuitarChordShape>,
    strumStyle: GuitarStrumStyle,
    recordedPlucks: List<RecordedNoteEvent>,
    bpm: Int,
    bars: Int = 4,
    targetLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
    applyPostGain: Boolean = true,
  ): TrackWithBufferDiagnostics = withContext(Dispatchers.IO) {
    if (!tracksDir.exists()) {
      tracksDir.mkdirs()
    }
    val rawBuffer = InstrumentSynthDsp.synthesizeGuitarTrackToBuffer(
      voice = voice,
      chordSequence = chordSequence,
      strumStyle = strumStyle,
      recordedPlucks = recordedPlucks,
      bpm = bpm,
      bars = bars,
      targetLufs = targetLufs,
      applyPostGain = applyPostGain,
    )
    val id = UUID.randomUUID().toString()
    val wavFile = File(tracksDir, "guitar_$id.wav")
    FourierAudioBufferProcessor.writeWavFile(wavFile, rawBuffer)

    val chordSummary = if (recordedPlucks.isNotEmpty()) {
      "Plucked Take"
    } else {
      chordSequence.take(4).joinToString("-") { it.label }.ifBlank { "C-G-Am-F" }
    }
    val title = "${voice.label} · $chordSummary"
    val baseTrack = GeneratedTrack(
      id = id,
      title = title,
      prompt = "${voice.label} guitar (${strumStyle.label}, $chordSummary) at $bpm BPM",
      genre = "Guitar",
      bpm = bpm,
      musicalKey = chordSequence.firstOrNull()?.label ?: "C Maj",
      durationMs = rawBuffer.durationMs,
      filePath = wavFile.absolutePath,
      fileSizeBytes = wavFile.length(),
      waveformPeaks = FourierAudioBufferProcessor.extractWaveformPeaks(rawBuffer.pcmSamples),
      createdAtEpochMs = System.currentTimeMillis(),
      sourceLabel = "Guitar · ${voice.label}",
      category = "Guitar",
      tags = listOf("Guitar", voice.label, strumStyle.label),
    )
    dao.insertTrack(baseTrack.toEntity())
    historyDao.insertHistoryEntry(
      baseTrack.toHistoryEntity(
        instrumentType = TrackHistoryTypes.GUITAR,
        engineOrSource = baseTrack.sourceLabel,
      ),
    )
    TrackWithBufferDiagnostics(
      track = baseTrack,
      diagnostics = rawBuffer.toDiagnostics(),
    )
  }

  suspend fun generateSoundResourceTrack(
    resource: StudioSoundResource,
    overrideBpm: Int? = null,
    targetLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
    applyPostGain: Boolean = true,
  ): TrackWithBufferDiagnostics = withContext(Dispatchers.IO) {
    if (!tracksDir.exists()) {
      tracksDir.mkdirs()
    }
    val effectiveBpm = (overrideBpm ?: resource.bpm).coerceIn(55, 180)
    val promptWithBpm = resource.promptRecipe.replace(Regex("""\b\d+\s*bpm\b""", RegexOption.IGNORE_CASE), "$effectiveBpm bpm")
    val (created, rawBuffer) = WavTrackSynthesizer.synthesizeWithRawBuffer(
      prompt = promptWithBpm,
      outputDir = tracksDir,
      targetLufs = targetLufs,
      applyPostGain = applyPostGain,
    )
    val saved = created.copy(
      title = resource.title,
      bpm = effectiveBpm,
      genre = resource.category,
      sourceLabel = "Sound Pack · ${resource.category}",
      category = resource.category,
      tags = listOf("Resource", resource.category, "$effectiveBpm BPM"),
    )
    dao.insertTrack(saved.toEntity())
    historyDao.insertHistoryEntry(
      saved.toHistoryEntity(
        instrumentType = TrackHistoryTypes.SOUND_PACK,
        engineOrSource = saved.sourceLabel,
      ),
    )
    TrackWithBufferDiagnostics(
      track = saved,
      diagnostics = rawBuffer.toDiagnostics(),
    )
  }

  suspend fun mergeTracksToMaster(
    title: String,
    stems: List<MixStemSpec>,
    targetLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
    applyPostGain: Boolean = true,
  ): TrackWithBufferDiagnostics = withContext(Dispatchers.IO) {
    if (!tracksDir.exists()) {
      tracksDir.mkdirs()
    }
    val mergedBuffer = MultiTrackMixerDsp.mergeTracksToRawBuffer(
      stems = stems,
      targetLufs = targetLufs,
      applyPostGain = applyPostGain,
    )
    val id = UUID.randomUUID().toString()
    val wavFile = File(tracksDir, "master_$id.wav")
    FourierAudioBufferProcessor.writeWavFile(wavFile, mergedBuffer)

    val cleanTitle = title.trim().ifBlank {
      val parts = stems.map { it.track.category }.distinct().take(3).joinToString(" + ")
      "Master Mix ($parts)"
    }
    val avgBpm = stems.map { it.track.bpm }.filter { it in 55..180 }.average().takeIf { !it.isNaN() }?.toInt()
      ?: mergedBuffer.detectedBpm.takeIf { it in 55..180 }
      ?: 100
    val stemTags = (listOf("Merged", "Master Mix") + stems.map { it.track.category }).distinct().take(5)
    val saved = GeneratedTrack(
      id = id,
      title = cleanTitle,
      prompt = "Merged multi-track studio mix: ${stems.joinToString(", ") { it.track.title }}",
      genre = "Merged",
      bpm = avgBpm,
      musicalKey = "Stereo",
      durationMs = mergedBuffer.durationMs,
      filePath = wavFile.absolutePath,
      fileSizeBytes = wavFile.length(),
      waveformPeaks = FourierAudioBufferProcessor.extractWaveformPeaks(mergedBuffer.pcmSamples),
      createdAtEpochMs = System.currentTimeMillis(),
      sourceLabel = "Studio Mixdown (${stems.size} stems)",
      category = "Merged",
      tags = stemTags,
    )
    dao.insertTrack(saved.toEntity())
    historyDao.insertHistoryEntry(
      saved.toHistoryEntity(
        instrumentType = TrackHistoryTypes.MERGED,
        engineOrSource = saved.sourceLabel,
      ),
    )
    TrackWithBufferDiagnostics(
      track = saved,
      diagnostics = mergedBuffer.toDiagnostics(),
    )
  }

  /**
   * Imports a synthesized built-in offline studio music loop or texture WAV into local studio
   * storage, optionally normalizes loudness, and persists it in Room + Track History.
   */
  suspend fun importOfflineMusicAsset(
    asset: com.example.audio.OfflineMusicAsset,
    renderedWavFile: File,
    targetLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
    applyPostGain: Boolean = true,
  ): TrackWithBufferDiagnostics = withContext(Dispatchers.IO) {
    if (!tracksDir.exists()) {
      tracksDir.mkdirs()
    }
    if (!renderedWavFile.exists() || renderedWavFile.length() == 0L) {
      throw IllegalStateException("Offline studio audio file is missing or empty.")
    }

    val id = UUID.randomUUID().toString()
    val localCopy = File(tracksDir, "offline_${asset.id}_$id.wav")
    renderedWavFile.copyTo(localCopy, overwrite = true)

    val decodedBuffer = FourierAudioBufferProcessor.decodeFileToRawBuffer(
      file = localCopy,
      bridgeSource = "Offline Studio Pack",
    )
    val (finalFile, rawBuffer) = if (
      applyPostGain &&
      decodedBuffer.pcmSamples.isNotEmpty() &&
      asset.category != com.example.audio.OfflineSampleCategory.TEXTURES
    ) {
      val norm = LoudnessNormalizerDsp.normalizePcm(
        pcmSamples = decodedBuffer.pcmSamples,
        sampleRate = decodedBuffer.sampleRate,
        channelCount = decodedBuffer.channelCount,
        targetLufs = targetLufs,
      )
      val analyzed = FourierAudioBufferProcessor.analyzeRawPcm(
        pcmSamples = norm.normalizedSamples,
        sampleRate = decodedBuffer.sampleRate,
        channelCount = decodedBuffer.channelCount,
        bridgeSource = "Offline Studio Pack",
      )
      FourierAudioBufferProcessor.writeWavFile(localCopy, analyzed)
      localCopy to analyzed
    } else {
      localCopy to decodedBuffer
    }

    val analysis = AudioWaveformExtractor.analyzeFile(finalFile)
    val waveformPeaks = if (rawBuffer.pcmSamples.isNotEmpty()) {
      FourierAudioBufferProcessor.extractWaveformPeaks(rawBuffer.pcmSamples)
    } else {
      analysis.waveformPeaks
    }
    val durationMs = rawBuffer.durationMs.takeIf { it > 300L }
      ?: analysis.durationMs.takeIf { it > 300L }
      ?: (asset.durationSec * 1000.0).toLong().coerceAtLeast(500L)

    val categoryLabel = when (asset.category) {
      com.example.audio.OfflineSampleCategory.FULL_MUSIC -> "Offline Music"
      com.example.audio.OfflineSampleCategory.TEXTURES -> "Textures"
      else -> "Samples"
    }
    val saved = GeneratedTrack(
      id = id,
      title = asset.title,
      prompt = "Built-in Offline ${asset.category.label} · ${asset.subtitle}",
      genre = asset.genre,
      bpm = asset.bpm,
      musicalKey = asset.musicalKey,
      durationMs = durationMs,
      filePath = finalFile.absolutePath,
      fileSizeBytes = finalFile.length(),
      waveformPeaks = waveformPeaks,
      createdAtEpochMs = System.currentTimeMillis(),
      sourceLabel = "Offline Pack · ${asset.category.label}",
      category = categoryLabel,
      tags = (listOf("Offline", asset.genre) + asset.tags.take(3)).distinct(),
    )
    dao.insertTrack(saved.toEntity())
    historyDao.insertHistoryEntry(
      saved.toHistoryEntity(
        instrumentType = TrackHistoryTypes.SOUND_PACK,
        engineOrSource = "Offline Studio Pack",
      ),
    )
    TrackWithBufferDiagnostics(
      track = saved,
      diagnostics = rawBuffer.toDiagnostics(),
    )
  }

  /**
   * Renders a coordinated Piano + Guitar + Drum session from a [MusicArrangementPlan],
   * blends in an optional attached Offline Music/Texture layer, and merges all stems into a
   * finished master mix persisted in Room and Track History.
   */
  suspend fun renderAiArrangementSession(
    plan: com.example.ai.MusicArrangementPlan,
    attachedSampleTrack: GeneratedTrack? = null,
    targetLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
    applyPostGain: Boolean = true,
  ): AiArrangedSessionResult = withContext(Dispatchers.IO) {
    val pianoResult = generatePianoTrack(
      voice = plan.pianoVoice,
      progression = plan.pianoProgression,
      recordedNotes = emptyList(),
      bpm = plan.bpm,
      bars = 4,
      targetLufs = targetLufs,
      applyPostGain = applyPostGain,
    )
    val guitarResult = generateGuitarTrack(
      voice = plan.guitarVoice,
      chordSequence = plan.guitarChords,
      strumStyle = plan.guitarStrumStyle,
      recordedPlucks = emptyList(),
      bpm = plan.bpm,
      bars = 4,
      targetLufs = targetLufs,
      applyPostGain = applyPostGain,
    )
    val drumState = StepSequencerState(
      pattern = plan.drumPreset.buildGrid(),
      bpm = plan.bpm,
      drumKit = plan.drumKit,
      selectedPresetName = plan.drumPreset.label,
    )
    val drumResult = renderSequencerPatternToTrack(
      sequencerState = drumState,
      targetLufs = targetLufs,
      applyPostGain = applyPostGain,
    )

    val stems = buildList {
      add(MixStemSpec(track = pianoResult.track, gain = plan.pianoGain, loopToMatch = true))
      add(MixStemSpec(track = guitarResult.track, gain = plan.guitarGain, loopToMatch = true))
      add(MixStemSpec(track = drumResult.track, gain = plan.drumGain, loopToMatch = true))
      if (attachedSampleTrack != null && File(attachedSampleTrack.filePath).exists()) {
        add(MixStemSpec(track = attachedSampleTrack, gain = plan.sampleGain, loopToMatch = true))
      }
    }

    val masterResult = mergeTracksToMaster(
      title = plan.title,
      stems = stems,
      targetLufs = targetLufs,
      applyPostGain = applyPostGain,
    )
    val updatedMaster = masterResult.track.copy(
      prompt = "${plan.engineLabel} Arrangement (${plan.genre} · ${plan.musicalKey} · ${plan.bpm} BPM): ${plan.sourcePrompt}",
      genre = plan.genre,
      bpm = plan.bpm,
      musicalKey = plan.musicalKey,
      sourceLabel = if (attachedSampleTrack != null) {
        "${plan.engineLabel} + Layer (${stems.size} stems)"
      } else {
        "${plan.engineLabel} Mix (${stems.size} stems)"
      },
      tags = listOf(plan.engineLabel, plan.genre, "${plan.bpm} BPM", "Merged").distinct(),
    )
    dao.insertTrack(updatedMaster.toEntity())
    historyDao.updateTitleByTrackId(updatedMaster.id, updatedMaster.title)

    AiArrangedSessionResult(
      masterTrack = updatedMaster,
      masterDiagnostics = masterResult.diagnostics,
      stemTrackIds = stems.map { it.track.id },
      stemGains = stems.associate { it.track.id to it.gain },
    )
  }

  suspend fun exportCurrentProjectMergedAudioToMp3(
    title: String,
    stems: List<MixStemSpec>,
    config: AudioExportConfig = AudioExportConfig(format = AudioExportFormat.MP3, bitrateKbps = 192),
    targetLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
    applyPostGain: Boolean = true,
  ): Mp3ExportOutcome = withContext(Dispatchers.IO) {
    val mergedTrackResult = mergeTracksToMaster(
      title = title,
      stems = stems,
      targetLufs = targetLufs,
      applyPostGain = applyPostGain,
    )
    exportTrackToMusicMp3(mergedTrackResult.track, config)
  }

  suspend fun generateTrackWithHfSpace(
    prompt: String,
    space: HfMusicSpace,
    targetLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
    applyPostGain: Boolean = true,
  ): GeneratedTrack = generateTrackWithHfSpaceBuffer(prompt, space, targetLufs, applyPostGain).track

  suspend fun generateTrackWithHfSpaceBuffer(
    prompt: String,
    space: HfMusicSpace,
    targetLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
    applyPostGain: Boolean = true,
  ): TrackWithBufferDiagnostics = withContext(Dispatchers.IO) {
    val result = hfSpaceClient.generateMusicWithRawBuffer(
      prompt = prompt,
      space = space,
      outputDir = tracksDir,
      targetLufs = targetLufs,
      applyPostGain = applyPostGain,
    )
    val tagResult = autoTagger.analyzeTrack(
      PlaceholderTrackAutoTagger.buildInput(track = result.track, rawBuffer = result.rawBuffer)
    )
    val saved = result.track.copy(
      category = tagResult.suggestedCategory,
      tags = (result.track.tags + tagResult.suggestedTags).distinct(),
    )
    dao.insertTrack(saved.toEntity())
    historyDao.insertHistoryEntry(
      saved.toHistoryEntity(
        instrumentType = TrackHistoryTypes.PROMPT,
        engineOrSource = space.displayName,
      ),
    )
    TrackWithBufferDiagnostics(
      track = saved,
      diagnostics = result.rawBuffer.toDiagnostics(),
      autoTagResult = tagResult,
    )
  }

  suspend fun suggestAutoTags(track: GeneratedTrack): TrackAutoTagResult =
    withContext(Dispatchers.IO) {
      val file = File(track.filePath)
      val rawBuffer = if (file.exists()) {
        try {
          FourierAudioBufferProcessor.decodeFileToRawBuffer(
            file = file,
            bridgeSource = track.sourceLabel,
          )
        } catch (_: Exception) {
          null
        }
      } else {
        null
      }
      autoTagger.analyzeTrack(
        PlaceholderTrackAutoTagger.buildInput(track = track, rawBuffer = rawBuffer)
      )
    }

  suspend fun downloadTrackFromUrl(
    url: String,
    connections: Int = 4,
    targetLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
    applyPostGain: Boolean = true,
    onProgress: (AudioDownloadProgress) -> Unit = {},
  ): TrackWithBufferDiagnostics = withContext(Dispatchers.IO) {
    if (!tracksDir.exists()) {
      tracksDir.mkdirs()
    }
    val downloaded = segmentedDownloader.downloadDirectAudioUrl(
      rawUrl = url,
      outputDir = tracksDir,
      maxConnections = connections,
      onProgress = onProgress,
    )
    val decoded = FourierAudioBufferProcessor.decodeFileToRawBuffer(
      file = downloaded.file,
      bridgeSource = downloaded.transportLabel,
    )
    val (finalFile, finalBuffer, finalKey) = if (applyPostGain && decoded.pcmSamples.isNotEmpty()) {
      val norm = LoudnessNormalizerDsp.normalizePcm(
        pcmSamples = decoded.pcmSamples,
        sampleRate = decoded.sampleRate,
        channelCount = decoded.channelCount,
        targetLufs = targetLufs,
      )
      val analyzed = FourierAudioBufferProcessor.analyzeRawPcm(
        pcmSamples = norm.normalizedSamples,
        sampleRate = decoded.sampleRate,
        channelCount = decoded.channelCount,
        bridgeSource = downloaded.transportLabel,
      )
      val normFile = File(tracksDir, "dl_norm_${UUID.randomUUID()}.wav")
      FourierAudioBufferProcessor.writeWavFile(normFile, analyzed)
      if (downloaded.file.absolutePath != normFile.absolutePath) {
        downloaded.file.delete()
      }
      Triple(normFile, analyzed, "WAV")
    } else {
      Triple(downloaded.file, decoded, downloaded.extension.uppercase())
    }

    val analysis = AudioWaveformExtractor.analyzeFile(finalFile)
    val waveformPeaks = if (finalBuffer.pcmSamples.isNotEmpty()) {
      FourierAudioBufferProcessor.extractWaveformPeaks(finalBuffer.pcmSamples)
    } else {
      analysis.waveformPeaks
    }
    val detectedBpm = finalBuffer.detectedBpm.takeIf { it in 55..180 } ?: 100
    val baseTrack = GeneratedTrack(
      id = UUID.randomUUID().toString(),
      title = downloaded.displayTitle,
      prompt = "Downloaded via ${downloaded.transportLabel}: $url",
      genre = "Downloaded",
      bpm = detectedBpm,
      musicalKey = finalKey,
      durationMs = finalBuffer.durationMs.takeIf { it > 1_000L } ?: analysis.durationMs,
      filePath = finalFile.absolutePath,
      fileSizeBytes = finalFile.length(),
      waveformPeaks = waveformPeaks,
      createdAtEpochMs = System.currentTimeMillis(),
      sourceLabel = downloaded.transportLabel,
      category = "Downloaded",
      tags = listOf("Downloaded", downloaded.extension.uppercase()),
    )
    val tagResult = autoTagger.analyzeTrack(
      PlaceholderTrackAutoTagger.buildInput(track = baseTrack, rawBuffer = finalBuffer)
    )
    val saved = baseTrack.copy(
      category = tagResult.suggestedCategory,
      tags = (baseTrack.tags + tagResult.suggestedTags).distinct(),
    )
    dao.insertTrack(saved.toEntity())
    TrackWithBufferDiagnostics(
      track = saved,
      diagnostics = finalBuffer.toDiagnostics(),
      autoTagResult = tagResult,
    )
  }

  suspend fun submitAria2RpcDownload(
    rpcUrl: String,
    sourceUrl: String,
    secret: String = "",
    connections: Int = 4,
    onProgress: (AudioDownloadProgress) -> Unit = {},
  ): String = withContext(Dispatchers.IO) {
    segmentedDownloader.submitToAria2JsonRpc(
      rpcEndpointUrl = rpcUrl,
      sourceUrl = sourceUrl,
      rpcSecret = secret,
      connections = connections,
      onProgress = onProgress,
    )
  }

  suspend fun inspectTrackBuffer(track: GeneratedTrack): RawBufferDiagnostics =
    withContext(Dispatchers.IO) {
      FourierAudioBufferProcessor.decodeFileToRawBuffer(
        file = File(track.filePath),
        bridgeSource = track.sourceLabel,
      ).toDiagnostics()
    }

  suspend fun getTrackById(trackId: String): GeneratedTrack? =
    withContext(Dispatchers.IO) {
      dao.getTrackById(trackId)?.toDomain()
    }

  suspend fun importAudioFromUri(
    uri: Uri,
    targetLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
    applyPostGain: Boolean = true,
  ): GeneratedTrack =
    withContext(Dispatchers.IO) {
      if (!tracksDir.exists()) {
        tracksDir.mkdirs()
      }

      val displayName = resolveDisplayName(uri) ?: "Imported Track"
      val extension = displayName.substringAfterLast('.', "").lowercase()
        .takeIf { it in setOf("wav", "mp3", "flac", "m4a", "ogg", "aac") }
        ?: "mp3"

      val id = UUID.randomUUID().toString()
      val destFile = File(tracksDir, "imported_$id.$extension")

      context.contentResolver.openInputStream(uri)?.use { input ->
        destFile.outputStream().use { output ->
          input.copyTo(output)
        }
      } ?: throw IllegalStateException("Unable to read selected audio file.")

      if (!destFile.exists() || destFile.length() == 0L) {
        destFile.delete()
        throw IllegalStateException("Selected audio file was empty.")
      }

      val decodedBuffer = FourierAudioBufferProcessor.decodeFileToRawBuffer(
        file = destFile,
        bridgeSource = "Imported Audio",
      )
      val (finalFile, rawBuffer, finalKey) = if (applyPostGain && decodedBuffer.pcmSamples.isNotEmpty()) {
        val norm = LoudnessNormalizerDsp.normalizePcm(
          pcmSamples = decodedBuffer.pcmSamples,
          sampleRate = decodedBuffer.sampleRate,
          channelCount = decodedBuffer.channelCount,
          targetLufs = targetLufs,
        )
        val analyzed = FourierAudioBufferProcessor.analyzeRawPcm(
          pcmSamples = norm.normalizedSamples,
          sampleRate = decodedBuffer.sampleRate,
          channelCount = decodedBuffer.channelCount,
          bridgeSource = "Imported Audio",
        )
        val normFile = File(tracksDir, "imported_norm_$id.wav")
        FourierAudioBufferProcessor.writeWavFile(normFile, analyzed)
        if (destFile.absolutePath != normFile.absolutePath) {
          destFile.delete()
        }
        Triple(normFile, analyzed, "WAV")
      } else {
        Triple(destFile, decodedBuffer, extension.uppercase())
      }

      val analysis = AudioWaveformExtractor.analyzeFile(finalFile)
      val waveformPeaks = if (rawBuffer.pcmSamples.isNotEmpty()) {
        FourierAudioBufferProcessor.extractWaveformPeaks(rawBuffer.pcmSamples)
      } else {
        analysis.waveformPeaks
      }
      val detectedBpm = rawBuffer.detectedBpm.takeIf { it in 55..180 } ?: 100
      val cleanTitle = displayName.substringBeforeLast('.').ifBlank { "Imported Audio" }
      val track = GeneratedTrack(
        id = id,
        title = cleanTitle,
        prompt = "Imported local audio ($extension)",
        genre = "Imported",
        bpm = detectedBpm,
        musicalKey = finalKey,
        durationMs = rawBuffer.durationMs.takeIf { it > 1_000L } ?: analysis.durationMs,
        filePath = finalFile.absolutePath,
        fileSizeBytes = finalFile.length(),
        waveformPeaks = waveformPeaks,
        createdAtEpochMs = System.currentTimeMillis(),
        sourceLabel = "Imported Audio",
        category = "Imported",
        tags = listOf("Imported", extension.uppercase()),
      )

      dao.insertTrack(track.toEntity())
      track
    }

  suspend fun updateTrackOrganization(trackId: String, category: String, tags: List<String>) =
    withContext(Dispatchers.IO) {
      val cleanCategory = category.trim().ifBlank { "General" }
      val cleanTagsCsv = tags
        .map { it.trim().replace(",", "") }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(",")
      dao.updateTrackOrganization(trackId = trackId, category = cleanCategory, tagsCsv = cleanTagsCsv)
    }

  suspend fun setTrackFavorite(trackId: String, isFavorite: Boolean) =
    withContext(Dispatchers.IO) {
      dao.updateFavoriteStatus(trackId = trackId, isFavorite = isFavorite)
    }

  suspend fun updateTrackAudioEnvelope(trackId: String, envelope: TrackAudioEnvelope) =
    withContext(Dispatchers.IO) {
      dao.updateAudioEnvelope(trackId = trackId, envelopeSpec = envelope.toJsonString())
    }

  suspend fun renameTrackAndFile(
    trackId: String,
    newTitle: String,
  ): GeneratedTrack = withContext(Dispatchers.IO) {
    val cleanTitle = newTitle.trim()
    require(cleanTitle.isNotEmpty()) { "Project name cannot be empty." }
    val existing = dao.getTrackById(trackId)
      ?: throw IllegalStateException("Saved audio project could not be found.")
    val oldFile = File(existing.filePath)
    val resolvedFile = if (oldFile.exists()) {
      val parentDir = oldFile.parentFile ?: tracksDir
      val ext = oldFile.extension.ifBlank { "wav" }
      val slug = buildSanitizedFileSlug(cleanTitle, fallback = "project_${existing.id.take(6)}")
      var targetFile = File(parentDir, "$slug.$ext")
      if (targetFile.absolutePath != oldFile.absolutePath && targetFile.exists()) {
        targetFile = File(parentDir, "${slug}_${existing.id.take(6)}.$ext")
      }
      if (targetFile.absolutePath == oldFile.absolutePath) {
        oldFile
      } else if (oldFile.renameTo(targetFile)) {
        targetFile
      } else {
        try {
          oldFile.copyTo(targetFile, overwrite = true)
          oldFile.delete()
          targetFile
        } catch (_: Exception) {
          oldFile
        }
      }
    } else {
      oldFile
    }
    val updatedEntity = existing.copy(
      title = cleanTitle,
      filePath = resolvedFile.absolutePath,
      fileSizeBytes = if (resolvedFile.exists()) resolvedFile.length() else existing.fileSizeBytes,
    )
    dao.updateTrack(updatedEntity)
    historyDao.updateTitleByTrackId(existing.id, cleanTitle)
    updatedEntity.toDomain()
  }

  private fun buildSanitizedFileSlug(rawTitle: String, fallback: String): String {
    return rawTitle
      .replace(Regex("[^A-Za-z0-9._-]+"), "_")
      .trim('_', '.')
      .ifBlank { fallback }
      .take(48)
  }

  suspend fun bulkRenameTracks(
    trackIds: List<String>,
    config: BulkRenameConfig,
    referenceNowMs: Long = System.currentTimeMillis(),
  ): Int = withContext(Dispatchers.IO) {
    if (trackIds.isEmpty()) return@withContext 0
    val orderedTracks = trackIds.distinct().mapNotNull { id ->
      dao.getTrackById(id)?.toDomain()
    }
    if (orderedTracks.isEmpty()) return@withContext 0
    val previews = TrackBulkRenameFormatter.generatePreviews(
      tracks = orderedTracks,
      config = config,
      referenceNowMs = referenceNowMs,
    )
    SonicForgeDatabase.getInstance(context).withTransaction {
      previews.forEach { item ->
        dao.updateTrackTitle(trackId = item.trackId, title = item.newTitle)
        historyDao.updateTitleByTrackId(trackId = item.trackId, title = item.newTitle)
      }
    }
    previews.size
  }

  suspend fun createPlaylist(
    name: String,
    description: String = "",
    initialTrackIds: List<String> = emptyList(),
  ): Playlist = withContext(Dispatchers.IO) {
    val cleanName = name.trim().ifBlank { "Untitled Playlist" }
    val cleanDesc = description.trim()
    val playlistId = UUID.randomUUID().toString()
    val now = System.currentTimeMillis()
    val entity = PlaylistEntity(
      id = playlistId,
      name = cleanName,
      description = cleanDesc,
      createdAtEpochMs = now,
    )
    playlistDao.insertPlaylist(entity)
    if (initialTrackIds.isNotEmpty()) {
      val refs = initialTrackIds.distinct().mapIndexed { index, trackId ->
        PlaylistTrackCrossRef(
          playlistId = playlistId,
          trackId = trackId,
          addedAtEpochMs = now + index,
        )
      }
      playlistDao.addTracksToPlaylist(refs)
    }
    playlistDao.getPlaylistWithTracksOnce(playlistId)?.toDomain(requireExistingFiles = true)
      ?: Playlist(
        id = playlistId,
        name = cleanName,
        description = cleanDesc,
        createdAtEpochMs = now,
        tracks = emptyList(),
      )
  }

  suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
    playlistDao.deletePlaylistById(playlistId)
  }

  suspend fun addTrackToPlaylist(playlistId: String, trackId: String) =
    withContext(Dispatchers.IO) {
      playlistDao.addTrackToPlaylist(
        PlaylistTrackCrossRef(
          playlistId = playlistId,
          trackId = trackId,
          addedAtEpochMs = System.currentTimeMillis(),
        )
      )
    }

  suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) =
    withContext(Dispatchers.IO) {
      playlistDao.removeTrackFromPlaylist(playlistId = playlistId, trackId = trackId)
    }

  suspend fun setTrackPlaylistMembership(
    playlistId: String,
    trackId: String,
    isMember: Boolean,
  ) = withContext(Dispatchers.IO) {
    if (isMember) {
      addTrackToPlaylist(playlistId = playlistId, trackId = trackId)
    } else {
      removeTrackFromPlaylist(playlistId = playlistId, trackId = trackId)
    }
  }

  suspend fun trimTrack(
    trackId: String,
    startMs: Long,
    endMs: Long,
    saveAsCopy: Boolean,
    targetLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
    applyPostGain: Boolean = true,
  ): TrackWithBufferDiagnostics = withContext(Dispatchers.IO) {
    if (!tracksDir.exists()) {
      tracksDir.mkdirs()
    }
    val existingEntity = dao.getTrackById(trackId)
      ?: throw IllegalStateException("Track could not be found in library.")
    val existingTrack = existingEntity.toDomain()
    val sourceFile = File(existingTrack.filePath)
    if (!sourceFile.exists()) {
      throw IllegalStateException("Audio file is missing on disk.")
    }

    val decoded = FourierAudioBufferProcessor.decodeFileToRawBuffer(
      file = sourceFile,
      bridgeSource = existingTrack.sourceLabel,
    )
    if (decoded.pcmSamples.isEmpty()) {
      throw IllegalStateException("Unable to decode raw PCM audio samples for trimming.")
    }

    val rawTrimmed = FourierAudioBufferProcessor.trimRawBuffer(
      source = decoded,
      startMs = startMs,
      endMs = endMs,
      bridgeSource = existingTrack.sourceLabel,
    )
    val trimmedBuffer = if (applyPostGain && rawTrimmed.pcmSamples.isNotEmpty()) {
      val norm = LoudnessNormalizerDsp.normalizePcm(
        pcmSamples = rawTrimmed.pcmSamples,
        sampleRate = rawTrimmed.sampleRate,
        channelCount = rawTrimmed.channelCount,
        targetLufs = targetLufs,
      )
      FourierAudioBufferProcessor.analyzeRawPcm(
        pcmSamples = norm.normalizedSamples,
        sampleRate = rawTrimmed.sampleRate,
        channelCount = rawTrimmed.channelCount,
        bridgeSource = existingTrack.sourceLabel,
      )
    } else {
      rawTrimmed
    }
    val peaks = FourierAudioBufferProcessor.extractWaveformPeaks(trimmedBuffer.pcmSamples)

    val outFile = File(tracksDir, "trimmed_${UUID.randomUUID()}.wav")
    FourierAudioBufferProcessor.writeWavFile(outFile, trimmedBuffer)

    val updatedTags = (existingTrack.tags + "Trimmed").distinct()
    val resultTrack = if (saveAsCopy) {
      val cleanBaseTitle = existingTrack.title.removeSuffix(" (Trim)")
      existingTrack.copy(
        id = UUID.randomUUID().toString(),
        title = "$cleanBaseTitle (Trim)",
        musicalKey = "WAV",
        durationMs = trimmedBuffer.durationMs,
        filePath = outFile.absolutePath,
        fileSizeBytes = outFile.length(),
        waveformPeaks = peaks,
        createdAtEpochMs = System.currentTimeMillis(),
        tags = updatedTags,
        isFavorite = false,
      )
    } else {
      if (sourceFile.absolutePath != outFile.absolutePath) {
        sourceFile.delete()
      }
      existingTrack.copy(
        musicalKey = "WAV",
        durationMs = trimmedBuffer.durationMs,
        filePath = outFile.absolutePath,
        fileSizeBytes = outFile.length(),
        waveformPeaks = peaks,
        tags = updatedTags,
      )
    }

    dao.insertTrack(resultTrack.toEntity())
    TrackWithBufferDiagnostics(
      track = resultTrack,
      diagnostics = trimmedBuffer.toDiagnostics(),
    )
  }

  suspend fun renderReverbCopy(
    trackId: String,
    reverbSettings: ReverbSettings,
    pitchSemitones: Float = 0f,
    targetLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
    applyPostGain: Boolean = true,
  ): TrackWithBufferDiagnostics = withContext(Dispatchers.IO) {
    if (!tracksDir.exists()) {
      tracksDir.mkdirs()
    }
    val existingEntity = dao.getTrackById(trackId)
      ?: throw IllegalStateException("Track could not be found in library.")
    val existingTrack = existingEntity.toDomain()
    val sourceFile = File(existingTrack.filePath)
    if (!sourceFile.exists()) {
      throw IllegalStateException("Audio file is missing on disk.")
    }

    val decoded = FourierAudioBufferProcessor.decodeFileToRawBuffer(
      file = sourceFile,
      bridgeSource = existingTrack.sourceLabel,
    )
    if (decoded.pcmSamples.isEmpty()) {
      throw IllegalStateException("Unable to decode PCM samples for reverb rendering.")
    }

    val pitchProcessed = if (kotlin.math.abs(pitchSemitones) >= 0.05f) {
      PitchShiftDsp.processPcmOffline(
        pcmSamples = decoded.pcmSamples,
        sampleRate = decoded.sampleRate,
        channelCount = decoded.channelCount,
        semitones = pitchSemitones,
      )
    } else {
      decoded.pcmSamples
    }

    val tailMs = if (reverbSettings.isActive) {
      (600L + (reverbSettings.roomSize * 1000f).toLong()).coerceIn(600L, 1600L)
    } else {
      0L
    }
    val reverbedPcm = RoomReverbDsp.processPcmOffline(
      pcmSamples = pitchProcessed,
      sampleRate = decoded.sampleRate,
      channelCount = decoded.channelCount,
      settings = reverbSettings,
      tailDurationMs = tailMs,
    )
    val finalSamples = if (applyPostGain && reverbedPcm.isNotEmpty()) {
      LoudnessNormalizerDsp.normalizePcm(
        pcmSamples = reverbedPcm,
        sampleRate = decoded.sampleRate,
        channelCount = decoded.channelCount,
        targetLufs = targetLufs,
      ).normalizedSamples
    } else {
      reverbedPcm
    }

    val analyzed = FourierAudioBufferProcessor.analyzeRawPcm(
      pcmSamples = finalSamples,
      sampleRate = decoded.sampleRate,
      channelCount = decoded.channelCount,
      bridgeSource = "${existingTrack.sourceLabel} + ${reverbSettings.preset.label} Reverb",
    )
    val peaks = FourierAudioBufferProcessor.extractWaveformPeaks(analyzed.pcmSamples)
    val outFile = File(tracksDir, "reverb_${UUID.randomUUID()}.wav")
    FourierAudioBufferProcessor.writeWavFile(outFile, analyzed)

    val cleanBase = existingTrack.title.replace(Regex("""\s*\([^)]*Reverb\)$"""), "")
    val updatedTags = (existingTrack.tags + listOf("Reverb", reverbSettings.preset.label)).distinct()
    val transposedKey = PitchShiftDsp.transposeKeyLabel(existingTrack.musicalKey, pitchSemitones)
    val newTrack = existingTrack.copy(
      id = UUID.randomUUID().toString(),
      title = "$cleanBase (${reverbSettings.preset.label} Reverb)",
      musicalKey = transposedKey,
      bpm = analyzed.detectedBpm.takeIf { it in 55..180 } ?: existingTrack.bpm,
      durationMs = analyzed.durationMs,
      filePath = outFile.absolutePath,
      fileSizeBytes = outFile.length(),
      waveformPeaks = peaks,
      createdAtEpochMs = System.currentTimeMillis(),
      tags = updatedTags,
      isFavorite = false,
    )

    dao.insertTrack(newTrack.toEntity())
    TrackWithBufferDiagnostics(
      track = newTrack,
      diagnostics = analyzed.toDiagnostics(),
    )
  }

  suspend fun normalizeTrackToTargetLufs(
    trackId: String,
    targetLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
  ): TrackWithBufferDiagnostics = withContext(Dispatchers.IO) {
    if (!tracksDir.exists()) {
      tracksDir.mkdirs()
    }
    val existingEntity = dao.getTrackById(trackId)
      ?: throw IllegalStateException("Track could not be found in library.")
    val existingTrack = existingEntity.toDomain()
    val sourceFile = File(existingTrack.filePath)
    if (!sourceFile.exists()) {
      throw IllegalStateException("Audio file is missing on disk.")
    }

    val decoded = FourierAudioBufferProcessor.decodeFileToRawBuffer(
      file = sourceFile,
      bridgeSource = existingTrack.sourceLabel,
    )
    if (decoded.pcmSamples.isEmpty()) {
      throw IllegalStateException("Unable to decode PCM samples for loudness normalization.")
    }

    val normResult = LoudnessNormalizerDsp.normalizePcm(
      pcmSamples = decoded.pcmSamples,
      sampleRate = decoded.sampleRate,
      channelCount = decoded.channelCount,
      targetLufs = targetLufs,
    )
    val analyzed = FourierAudioBufferProcessor.analyzeRawPcm(
      pcmSamples = normResult.normalizedSamples,
      sampleRate = decoded.sampleRate,
      channelCount = decoded.channelCount,
      bridgeSource = existingTrack.sourceLabel,
    )
    val peaks = FourierAudioBufferProcessor.extractWaveformPeaks(analyzed.pcmSamples)
    val outFile = File(tracksDir, "norm_${UUID.randomUUID()}.wav")
    FourierAudioBufferProcessor.writeWavFile(outFile, analyzed)
    if (sourceFile.absolutePath != outFile.absolutePath) {
      sourceFile.delete()
    }

    val updatedTrack = existingTrack.copy(
      filePath = outFile.absolutePath,
      fileSizeBytes = outFile.length(),
      waveformPeaks = peaks,
      durationMs = analyzed.durationMs,
    )
    dao.updateTrack(updatedTrack.toEntity())
    TrackWithBufferDiagnostics(
      track = updatedTrack,
      diagnostics = analyzed.toDiagnostics(),
    )
  }

  suspend fun normalizeAllTracksToTargetLufs(
    targetLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
  ): Int = withContext(Dispatchers.IO) {
    val allEntities = dao.getAllTracksOnce()
    var normalizedCount = 0
    for (entity in allEntities) {
      val file = File(entity.filePath)
      if (!file.exists()) continue
      val decoded = FourierAudioBufferProcessor.decodeFileToRawBuffer(
        file = file,
        bridgeSource = entity.sourceLabel,
      )
      if (decoded.pcmSamples.isEmpty()) continue
      val currentLufs = decoded.integratedLufs
      if (kotlin.math.abs(currentLufs - targetLufs) < 0.35f) {
        normalizedCount++
        continue
      }
      normalizeTrackToTargetLufs(entity.id, targetLufs)
      normalizedCount++
    }
    normalizedCount
  }

  suspend fun exportTrackToMusicMp3(
    track: GeneratedTrack,
    config: AudioExportConfig = AudioExportConfig(),
  ): Mp3ExportOutcome =
    withContext(Dispatchers.IO) {
      Mp3AudioExporter.exportTrackToMediaStoreMusic(context, track, config)
    }

  suspend fun exportTrackMp3ToUri(
    track: GeneratedTrack,
    uri: Uri,
    config: AudioExportConfig = AudioExportConfig(),
  ): Mp3ExportOutcome =
    withContext(Dispatchers.IO) {
      Mp3AudioExporter.exportTrackToUri(context, track, uri, config)
    }

  suspend fun prepareShareMp3Intent(
    track: GeneratedTrack,
    config: AudioExportConfig = AudioExportConfig(),
  ): Pair<Intent, Mp3ExportOutcome> =
    withContext(Dispatchers.IO) {
      Mp3AudioExporter.createShareAudioIntent(context, track, config)
    }

  suspend fun getRoomTrackById(trackId: String): GeneratedTrack? = withContext(Dispatchers.IO) {
    dao.getTrackById(trackId)?.toDomain()
  }

  /**
   * Queries generated audio tracks directly from the local Room database and batch-exports them
   * into the device's public Music/SonicForge directory via Scoped MediaStore (Android 10+).
   */
  suspend fun exportRoomTracksToDeviceMusicDirectory(
    trackIds: Set<String>? = null,
    config: AudioExportConfig = AudioExportConfig(format = AudioExportFormat.MP3),
    onProgress: (com.example.audio.RoomExportProgress) -> Unit = {},
  ): com.example.audio.BatchStorageExportResult = withContext(Dispatchers.IO) {
    val roomTracks = dao.getAllTracksOnce()
      .map { it.toDomain() }
      .let { all ->
        if (trackIds.isNullOrEmpty()) all else all.filter { it.id in trackIds }
      }
    com.example.audio.RoomTracksStorageExporter.exportTracksToMediaStoreDirectory(
      context = context,
      tracks = roomTracks,
      config = config,
      onProgress = onProgress,
    )
  }

  /**
   * Queries generated audio tracks directly from the local Room database and exports them into
   * a user-selected device storage directory URI (Storage Access Framework OpenDocumentTree).
   */
  suspend fun exportRoomTracksToStorageTreeUri(
    treeUri: Uri,
    trackIds: Set<String>? = null,
    config: AudioExportConfig = AudioExportConfig(format = AudioExportFormat.MP3),
    onProgress: (com.example.audio.RoomExportProgress) -> Unit = {},
  ): com.example.audio.BatchStorageExportResult = withContext(Dispatchers.IO) {
    val roomTracks = dao.getAllTracksOnce()
      .map { it.toDomain() }
      .let { all ->
        if (trackIds.isNullOrEmpty()) all else all.filter { it.id in trackIds }
      }
    com.example.audio.RoomTracksStorageExporter.exportTracksToDirectoryTreeUri(
      context = context,
      tracks = roomTracks,
      treeUri = treeUri,
      config = config,
      onProgress = onProgress,
    )
  }

  suspend fun deleteTrack(trackId: String) = withContext(Dispatchers.IO) {
    val existing = dao.getTrackById(trackId)
    if (existing != null) {
      File(existing.filePath).delete()
      dao.deleteTrackById(trackId)
    }
  }

  private fun resolveDisplayName(uri: Uri): String? {
    return try {
      context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
          if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) cursor.getString(idx) else null
          } else {
            null
          }
        }
    } catch (_: Exception) {
      null
    }
  }

  companion object {
    const val ALL_CATEGORIES = "All"
  }
}
