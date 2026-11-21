package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.example.model.GeneratedTrack
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AudioPlaybackState(
  val currentTrackId: String? = null,
  val isPlaying: Boolean = false,
  val isPreparing: Boolean = false,
  val isPrepared: Boolean = false,
  val positionMs: Long = 0L,
  val durationMs: Long = 0L,
  val volume: Float = 0.85f,
  val isMuted: Boolean = false,
  val isLooping: Boolean = false,
  val isScrubbing: Boolean = false,
  val pitchSemitones: Float = 0f,
  val reverb: ReverbSettings = ReverbSettings(),
  val equalizer: EqualizerSettings = EqualizerSettings(),
  val crossfade: CrossfadeSettings = CrossfadeSettings(),
  val activeEnvelope: TrackAudioEnvelope = TrackAudioEnvelope(),
  val isCrossfading: Boolean = false,
  val crossfadeProgress: Float = 0f,
  val crossfadeNextTrackTitle: String? = null,
  val liveBpm: Int = 0,
  val liveBpmConfidence: Float = 0f,
  val liveBeatPulse: Float = 0f,
  val isBpmAnalyzing: Boolean = false,
  val autoPostGainEnabled: Boolean = true,
  val targetLoudnessLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
  val autoGainTrimDb: Float = 0f,
  val liveSpectrum: RealTimeSpectrumSnapshot = RealTimeSpectrumSnapshot(),
  val errorMessage: String? = null,
) {
  val effectiveVolume: Float
    get() = if (isMuted) 0f else volume

  val pitchRatio: Double
    get() = PitchShiftDsp.semitonesToPitchFactor(pitchSemitones)
}

private data class CrossfadeDeck(
  val track: GeneratedTrack,
  val buffer: RawAudioBuffer,
  val shifter: RealTimePitchShifter,
  val reverb: RealTimeRoomReverb,
  val equalizer: RealTimeGraphicEqualizer,
  @Volatile var frameCursor: Int = 0,
)

/**
 * Real-time DSP audio playback engine streaming decoded PCM buffers through [RealTimePitchShifter],
 * [RealTimeRoomReverb], and [CrossfadeDsp] dual-deck equal-power mixing into Android [AudioTrack]
 * for seamless playlist transitions and live `-12..+12` semitone key transposition.
 * Includes a [MediaPlayer] fallback when an exotic container cannot be pre-decoded to PCM, plus
 * wake-lock sleep-mode persistence and foreground service notification synchronization.
 */
class AudioPlayerController private constructor(
  private val appContext: Context,
) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private var loadJob: Job? = null
  private var streamingJob: Job? = null
  private var fallbackTickerJob: Job? = null
  private var nextDeckLoadJob: Job? = null

  private var activeBuffer: RawAudioBuffer? = null
  private var activeAudioTrack: AudioTrack? = null
  private var activeShifter: RealTimePitchShifter? = null
  private var activeReverb: RealTimeRoomReverb? = null
  private var activeEqualizer: RealTimeGraphicEqualizer? = null
  private var activeBpmDetector: RealTimeBpmDetector? = null
  private var activeSpectrumAnalyzer: RealTimeSpectrumAnalyzer? = null
  private val playbackFrameCursor = AtomicInteger(0)

  @Volatile
  private var preparedNextDeck: CrossfadeDeck? = null
  @Volatile
  private var failedNextDeckTrackId: String? = null

  private var fallbackMediaPlayer: MediaPlayer? = null
  private var fallbackIncomingPlayer: MediaPlayer? = null
  private var fallbackIncomingTrack: GeneratedTrack? = null
  private var fallbackCrossfadeStartMs: Long = -1L
  private var fallbackCrossfadeDurationMs: Long = 0L
  private var controllerWakeLock: PowerManager.WakeLock? = null

  private var lastNonZeroVolume: Float = 0.85f
  private var playlist: List<GeneratedTrack> = emptyList()

  private val _state = MutableStateFlow(AudioPlaybackState())
  val state: StateFlow<AudioPlaybackState> = _state.asStateFlow()

  fun updatePlaylist(tracks: List<GeneratedTrack>) {
    playlist = tracks
  }

  fun currentTrack(): GeneratedTrack? {
    val id = _state.value.currentTrackId ?: return playlist.firstOrNull()
    return playlist.firstOrNull { it.id == id } ?: playlist.firstOrNull()
  }

  fun loadTrack(
    track: GeneratedTrack,
    autoPlay: Boolean,
    initialPositionMs: Long = 0L,
  ) {
    val file = File(track.filePath)
    if (!file.exists()) {
      _state.update {
        it.copy(
          currentTrackId = track.id,
          isPlaying = false,
          isPreparing = false,
          isPrepared = false,
          errorMessage = "Audio file is missing on disk.",
        )
      }
      return
    }

    stopAllPlaybackResources()

    val clampedInitMs = initialPositionMs.coerceIn(0L, track.durationMs.coerceAtLeast(1L))

    _state.update {
      it.copy(
        currentTrackId = track.id,
        isPlaying = false,
        isPreparing = true,
        isPrepared = false,
        positionMs = clampedInitMs,
        durationMs = track.durationMs,
        activeEnvelope = track.audioEnvelope,
        liveBpm = 0,
        liveBpmConfidence = 0f,
        liveBeatPulse = 0f,
        isBpmAnalyzing = false,
        liveSpectrum = RealTimeSpectrumSnapshot(),
        errorMessage = null,
      )
    }

    loadJob = scope.launch {
      try {
        val decodedBuffer = withContext(Dispatchers.IO) {
          FourierAudioBufferProcessor.decodeFileToRawBuffer(
            file = file,
            bridgeSource = track.sourceLabel,
          )
        }

        // Guard if user switched tracks while decoding
        if (_state.value.currentTrackId != track.id) return@launch

        if (decodedBuffer.pcmSamples.isNotEmpty() && decodedBuffer.frameCount > 0) {
          val audioTrack = try {
            FourierAudioBufferProcessor.buildRawPcmAudioTrack(decodedBuffer)
          } catch (e: Exception) {
            Log.w(TAG, "AudioTrack init failed, falling back to MediaPlayer", e)
            null
          }

          if (audioTrack != null) {
            activeBuffer = decodedBuffer
            activeAudioTrack = audioTrack
            activeShifter = RealTimePitchShifter(
              sampleRate = decodedBuffer.sampleRate,
              channelCount = decodedBuffer.channelCount,
            )
            activeReverb = RealTimeRoomReverb(
              sampleRate = decodedBuffer.sampleRate,
              channelCount = decodedBuffer.channelCount,
            )
            activeEqualizer = RealTimeGraphicEqualizer(
              sampleRate = decodedBuffer.sampleRate,
              channelCount = decodedBuffer.channelCount,
            )
            activeBpmDetector = RealTimeBpmDetector(
              initialSampleRate = decodedBuffer.sampleRate,
              initialChannelCount = decodedBuffer.channelCount,
            )
            activeSpectrumAnalyzer = RealTimeSpectrumAnalyzer(
              initialSampleRate = decodedBuffer.sampleRate,
              initialChannelCount = decodedBuffer.channelCount,
            )
            val resolvedDurationMs = decodedBuffer.durationMs.coerceAtLeast(1L)
            val resolvedStartMs = initialPositionMs.coerceIn(0L, resolvedDurationMs)
            val startFrame = ((resolvedStartMs / 1000.0) * decodedBuffer.sampleRate)
              .toInt()
              .coerceIn(0, decodedBuffer.frameCount)
            playbackFrameCursor.set(startFrame)
            val currentSnap = _state.value
            val trimDb = if (currentSnap.autoPostGainEnabled) {
              LoudnessNormalizerDsp.computeGainAdjustmentDb(
                measuredLufs = decodedBuffer.integratedLufs,
                targetLufs = currentSnap.targetLoudnessLufs,
                peakAmplitude = decodedBuffer.peakAmplitude,
              )
            } else {
              0f
            }

            _state.update {
              it.copy(
                isPreparing = false,
                isPrepared = true,
                durationMs = resolvedDurationMs,
                positionMs = resolvedStartMs,
                autoGainTrimDb = trimDb,
                errorMessage = null,
              )
            }

            if (autoPlay) {
              startPcmStreaming()
            }
            return@launch
          }
        }

        // Graceful fallback to MediaPlayer if raw PCM decoding/AudioTrack is unavailable
        prepareFallbackMediaPlayer(track, file, autoPlay, initialPositionMs)
      } catch (_: CancellationException) {
        // Track load canceled by newer request
      } catch (e: Exception) {
        Log.e(TAG, "Failed to load track ${track.title}", e)
        _state.update {
          it.copy(
            isPlaying = false,
            isPreparing = false,
            isPrepared = false,
            errorMessage = e.localizedMessage ?: "Unable to load audio track.",
          )
        }
      }
    }
  }

  private fun prepareFallbackMediaPlayer(
    track: GeneratedTrack,
    file: File,
    autoPlay: Boolean,
    initialPositionMs: Long = 0L,
  ) {
    try {
      val player = MediaPlayer().apply {
        setAudioAttributes(
          AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
        )
        try {
          setWakeMode(appContext, PowerManager.PARTIAL_WAKE_LOCK)
        } catch (e: Exception) {
          Log.w(TAG, "WakeMode not available in this environment", e)
        }
        setDataSource(file.absolutePath)
        isLooping = _state.value.isLooping
        val vol = _state.value.effectiveVolume
        setVolume(vol, vol)

        setOnPreparedListener { mp ->
          applyPitchToFallbackMediaPlayer(mp, _state.value.pitchSemitones)
          val realDuration = mp.duration.toLong().takeIf { it > 0L } ?: track.durationMs
          val clampedStart = initialPositionMs.coerceIn(0L, realDuration.coerceAtLeast(1L))
          if (clampedStart > 0L) {
            try {
              mp.seekTo(clampedStart.toInt())
            } catch (_: IllegalStateException) {
            }
          }
          _state.update {
            it.copy(
              isPreparing = false,
              isPrepared = true,
              durationMs = realDuration,
              positionMs = clampedStart,
              errorMessage = null,
            )
          }
          if (autoPlay) {
            mp.start()
            _state.update { it.copy(isPlaying = true) }
            startFallbackTicker()
            PlaybackForegroundService.syncNotification(appContext)
          }
        }

        setOnCompletionListener { mp ->
          if (!mp.isLooping) {
            stopFallbackTicker()
            val nextInPlaylist = resolveNextTrackInPlaylist(_state.value.currentTrackId)
            val incoming = fallbackIncomingPlayer
            val incomingTrack = fallbackIncomingTrack
            if (incoming != null && incomingTrack != null) {
              promoteFallbackIncomingPlayer(incoming, incomingTrack)
            } else if (nextInPlaylist != null && playlist.size > 1) {
              loadTrack(nextInPlaylist, autoPlay = true)
            } else {
              mp.seekTo(0)
              _state.update {
                it.copy(
                  isPlaying = false,
                  positionMs = 0L,
                  isCrossfading = false,
                  crossfadeProgress = 0f,
                  crossfadeNextTrackTitle = null,
                )
              }
              PlaybackForegroundService.syncNotification(appContext)
            }
          }
        }

        setOnErrorListener { _, what, _ ->
          stopFallbackTicker()
          _state.update {
            it.copy(
              isPlaying = false,
              isPreparing = false,
              isPrepared = false,
              errorMessage = "Playback failed (code $what).",
            )
          }
          PlaybackForegroundService.stopService(appContext)
          true
        }

        prepareAsync()
      }
      fallbackMediaPlayer = player
    } catch (e: Exception) {
      Log.e(TAG, "Failed to initialize fallback MediaPlayer", e)
      _state.update {
        it.copy(
          isPlaying = false,
          isPreparing = false,
          isPrepared = false,
          errorMessage = e.localizedMessage ?: "Unable to load audio track.",
        )
      }
    }
  }

  fun play() {
    val current = _state.value
    if (current.isPlaying) return
    if (!current.isPrepared) {
      currentTrack()?.let { loadTrack(it, autoPlay = true) }
      return
    }

    if (activeBuffer != null && activeAudioTrack != null) {
      startPcmStreaming()
      return
    }

    val mp = fallbackMediaPlayer ?: return
    if (!mp.isPlaying) {
      val vol = current.effectiveVolume
      mp.setVolume(vol, vol)
      applyPitchToFallbackMediaPlayer(mp, current.pitchSemitones)
      mp.start()
      _state.update { it.copy(isPlaying = true, errorMessage = null) }
      startFallbackTicker()
      PlaybackForegroundService.syncNotification(appContext)
    }
  }

  fun pause() {
    val current = _state.value
    if (!current.isPrepared || !current.isPlaying) return

    if (activeBuffer != null && activeAudioTrack != null) {
      pausePcmStreaming()
      return
    }

    val mp = fallbackMediaPlayer ?: return
    if (mp.isPlaying) {
      mp.pause()
      stopFallbackTicker()
      _state.update { it.copy(isPlaying = false, positionMs = mp.currentPosition.toLong()) }
      PlaybackForegroundService.syncNotification(appContext)
    }
  }

  fun togglePlayPause() {
    if (_state.value.isPlaying) {
      pause()
    } else {
      play()
    }
  }

  private fun startPcmStreaming() {
    var buffer = activeBuffer ?: return
    val track = activeAudioTrack ?: return
    var shifter = activeShifter ?: return
    var reverb = activeReverb ?: RealTimeRoomReverb(buffer.sampleRate, buffer.channelCount).also {
      activeReverb = it
    }
    var equalizer = activeEqualizer ?: RealTimeGraphicEqualizer(buffer.sampleRate, buffer.channelCount).also {
      activeEqualizer = it
    }
    var bpmDetector = activeBpmDetector ?: RealTimeBpmDetector(buffer.sampleRate, buffer.channelCount).also {
      activeBpmDetector = it
    }
    var spectrumAnalyzer = activeSpectrumAnalyzer ?: RealTimeSpectrumAnalyzer(buffer.sampleRate, buffer.channelCount).also {
      activeSpectrumAnalyzer = it
    }

    streamingJob?.cancel()
    acquireControllerWakeLock()

    try {
      track.play()
    } catch (e: Exception) {
      Log.e(TAG, "AudioTrack.play() failed", e)
      releaseControllerWakeLock()
      _state.update {
        it.copy(isPlaying = false, errorMessage = e.localizedMessage ?: "Audio output failed.")
      }
      return
    }

    _state.update { it.copy(isPlaying = true, errorMessage = null) }
    PlaybackForegroundService.syncNotification(appContext)

    var channels = buffer.channelCount.coerceIn(1, 2)
    var totalFrames = buffer.frameCount
    var sampleRate = buffer.sampleRate.coerceAtLeast(8_000)

    streamingJob = scope.launch(Dispatchers.Default) {
      val chunkFrames = 512
      var inChunk = ShortArray(chunkFrames * channels)
      var pitchedChunk = ShortArray(chunkFrames * channels)
      var eqChunk = ShortArray(chunkFrames * channels)
      var outChunk = ShortArray(chunkFrames * channels)
      var deckAProcessedChunk = ShortArray(chunkFrames * channels)
      var deckBInChunk = ShortArray(chunkFrames * channels)
      var deckBPitchedChunk = ShortArray(chunkFrames * channels)
      var deckBEqChunk = ShortArray(chunkFrames * channels)
      var deckBProcessedChunk = ShortArray(chunkFrames * channels)
      var uiTickCounter = 0

      try {
        while (isActive && _state.value.isPlaying) {
          val cursor = playbackFrameCursor.get()
          val snapshot = _state.value

          if (cursor >= totalFrames) {
            if (snapshot.isLooping) {
              clearNextDeck()
              playbackFrameCursor.set(0)
              shifter.reset()
              reverb.reset()
              equalizer.reset()
              spectrumAnalyzer.reset()
              _state.update {
                it.copy(
                  positionMs = 0L,
                  isCrossfading = false,
                  crossfadeProgress = 0f,
                  crossfadeNextTrackTitle = null,
                  liveSpectrum = RealTimeSpectrumSnapshot(),
                )
              }
              continue
            }

            val nextDeck = preparedNextDeck
            if (nextDeck != null &&
              nextDeck.buffer.sampleRate == sampleRate &&
              nextDeck.buffer.channelCount.coerceIn(1, 2) == channels
            ) {
              // Zero-gap in-place promotion into the already running AudioTrack
              buffer = nextDeck.buffer
              shifter = nextDeck.shifter
              reverb = nextDeck.reverb
              equalizer = nextDeck.equalizer
              activeBuffer = buffer
              activeShifter = shifter
              activeReverb = reverb
              activeEqualizer = equalizer
              bpmDetector = RealTimeBpmDetector(buffer.sampleRate, buffer.channelCount)
              activeBpmDetector = bpmDetector
              spectrumAnalyzer = RealTimeSpectrumAnalyzer(buffer.sampleRate, buffer.channelCount)
              activeSpectrumAnalyzer = spectrumAnalyzer
              totalFrames = buffer.frameCount
              val handoffFrame = nextDeck.frameCursor.coerceIn(0, totalFrames)
              playbackFrameCursor.set(handoffFrame)
              preparedNextDeck = null
              failedNextDeckTrackId = null
              val handoffPosMs = ((handoffFrame.toDouble() / sampleRate) * 1000.0)
                .toLong()
                .coerceIn(0L, buffer.durationMs.coerceAtLeast(1L))
              val handoffTrimDb = if (snapshot.autoPostGainEnabled) {
                LoudnessNormalizerDsp.computeGainAdjustmentDb(
                  measuredLufs = buffer.integratedLufs,
                  targetLufs = snapshot.targetLoudnessLufs,
                  peakAmplitude = buffer.peakAmplitude,
                )
              } else {
                0f
              }
              _state.update {
                it.copy(
                  currentTrackId = nextDeck.track.id,
                  durationMs = buffer.durationMs.coerceAtLeast(1L),
                  positionMs = handoffPosMs,
                  activeEnvelope = nextDeck.track.audioEnvelope,
                  isCrossfading = false,
                  crossfadeProgress = 0f,
                  crossfadeNextTrackTitle = null,
                  liveBpm = 0,
                  liveBpmConfidence = 0f,
                  liveBeatPulse = 0f,
                  isBpmAnalyzing = true,
                  autoGainTrimDb = handoffTrimDb,
                  liveSpectrum = RealTimeSpectrumSnapshot(),
                )
              }
              PlaybackForegroundService.syncNotification(appContext)
              continue
            } else if (nextDeck != null) {
              preparedNextDeck = null
              withContext(Dispatchers.Main.immediate) {
                promoteNextDeckWithNewAudioTrack(nextDeck)
              }
              break
            } else {
              val nextTrack = resolveNextTrackInPlaylist(snapshot.currentTrackId)
              withContext(Dispatchers.Main.immediate) {
                if (nextTrack != null && playlist.size > 1) {
                  loadTrack(nextTrack, autoPlay = true)
                } else {
                  try {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                      track.pause()
                    }
                    track.flush()
                  } catch (_: IllegalStateException) {
                  }
                  playbackFrameCursor.set(0)
                  shifter.reset()
                  reverb.reset()
                  equalizer.reset()
                  bpmDetector.reset()
                  spectrumAnalyzer.reset()
                  releaseControllerWakeLock()
                  _state.update {
                    it.copy(
                      isPlaying = false,
                      positionMs = 0L,
                      isCrossfading = false,
                      crossfadeProgress = 0f,
                      crossfadeNextTrackTitle = null,
                      liveBpm = 0,
                      liveBpmConfidence = 0f,
                      liveBeatPulse = 0f,
                      isBpmAnalyzing = false,
                      liveSpectrum = RealTimeSpectrumSnapshot(),
                    )
                  }
                  PlaybackForegroundService.syncNotification(appContext)
                }
              }
              break
            }
          }

          val framesToProcess = min(chunkFrames, totalFrames - cursor)
          val sampleOffset = cursor * channels
          val sampleCount = framesToProcess * channels

          buffer.pcmSamples.copyInto(
            destination = inChunk,
            destinationOffset = 0,
            startIndex = sampleOffset,
            endIndex = sampleOffset + sampleCount,
          )

          // Pre-decode next playlist track before entering the crossfade tail
          if (!snapshot.isLooping && snapshot.crossfade.isActive && playlist.size > 1) {
            val remainingMs = (((totalFrames - cursor).toDouble() / sampleRate) * 1000.0).toLong()
            val maxLeadMs = (snapshot.crossfade.durationSec * 1000f).toLong() + 2_500L
            if (remainingMs <= maxLeadMs) {
              maybePreloadNextDeck(
                currentTrackId = snapshot.currentTrackId,
                targetSampleRate = sampleRate,
                targetChannels = channels,
              )
            }
          }

          val nextDeck = if (!snapshot.isLooping && snapshot.crossfade.isActive) preparedNextDeck else null
          val crossfadeMs = if (nextDeck != null) {
            CrossfadeDsp.computeEffectiveCrossfadeMs(
              settings = snapshot.crossfade,
              outgoingDurationMs = buffer.durationMs,
              incomingDurationMs = nextDeck.buffer.durationMs,
            )
          } else {
            0L
          }
          val crossfadeFrames = ((crossfadeMs / 1000.0) * sampleRate)
            .toInt()
            .coerceIn(0, (totalFrames * 0.45f).toInt())
          val crossfadeStartFrame = totalFrames - crossfadeFrames

          val deckATrim = if (snapshot.autoPostGainEnabled) {
            LoudnessNormalizerDsp.computePlaybackGainLinear(
              measuredLufs = buffer.integratedLufs,
              targetLufs = snapshot.targetLoudnessLufs,
              peakAmplitude = buffer.peakAmplitude,
            )
          } else {
            1f
          }

          if (nextDeck != null && crossfadeFrames > 0 && cursor + framesToProcess > crossfadeStartFrame) {
            val deckBTrim = if (snapshot.autoPostGainEnabled) {
              LoudnessNormalizerDsp.computePlaybackGainLinear(
                measuredLufs = nextDeck.buffer.integratedLufs,
                targetLufs = snapshot.targetLoudnessLufs,
                peakAmplitude = nextDeck.buffer.peakAmplitude,
              )
            } else {
              1f
            }
            // Process outgoing deck with its auto-gain trim; master volume applied during dual-deck blend
            shifter.processBlock(
              input = inChunk,
              frameCount = framesToProcess,
              targetSemitones = snapshot.pitchSemitones,
              output = pitchedChunk,
              volumeGain = 1f,
            )
            reverb.processBlock(
              input = pitchedChunk,
              frameCount = framesToProcess,
              settings = snapshot.reverb,
              output = eqChunk,
              volumeGain = 1f,
            )
            equalizer.processBlock(
              input = eqChunk,
              frameCount = framesToProcess,
              settings = snapshot.equalizer,
              output = deckAProcessedChunk,
              volumeGain = deckATrim.coerceIn(0.25f, 1.5f),
            )

            VolumeEnvelopeDsp.applyEnvelopeToChunk(
              samples = deckAProcessedChunk,
              frameCount = framesToProcess,
              channelCount = channels,
              startFrame = cursor,
              totalFrames = totalFrames,
              envelope = snapshot.activeEnvelope,
            )

            // Extract & process incoming deck chunk matching output rate/channels
            val consumedSrcFrames = CrossfadeDsp.extractResampledChunk(
              sourceBuffer = nextDeck.buffer,
              sourceFrameStart = nextDeck.frameCursor,
              targetFrames = framesToProcess,
              targetSampleRate = sampleRate,
              targetChannels = channels,
              destination = deckBInChunk,
            )
            val deckBStartFrame = nextDeck.frameCursor
            nextDeck.frameCursor += consumedSrcFrames

            nextDeck.shifter.processBlock(
              input = deckBInChunk,
              frameCount = framesToProcess,
              targetSemitones = snapshot.pitchSemitones,
              output = deckBPitchedChunk,
              volumeGain = 1f,
            )
            nextDeck.reverb.processBlock(
              input = deckBPitchedChunk,
              frameCount = framesToProcess,
              settings = snapshot.reverb,
              output = deckBEqChunk,
              volumeGain = 1f,
            )
            nextDeck.equalizer.processBlock(
              input = deckBEqChunk,
              frameCount = framesToProcess,
              settings = snapshot.equalizer,
              output = deckBProcessedChunk,
              volumeGain = deckBTrim.coerceIn(0.25f, 1.5f),
            )
            VolumeEnvelopeDsp.applyEnvelopeToChunk(
              samples = deckBProcessedChunk,
              frameCount = framesToProcess,
              channelCount = channels,
              startFrame = deckBStartFrame,
              totalFrames = nextDeck.buffer.frameCount,
              envelope = nextDeck.track.audioEnvelope,
            )

            val progressStart = ((cursor - crossfadeStartFrame).toFloat() / crossfadeFrames.toFloat())
              .coerceIn(0f, 1f)
            val progressEnd = (((cursor + framesToProcess) - crossfadeStartFrame).toFloat() / crossfadeFrames.toFloat())
              .coerceIn(0f, 1f)

            CrossfadeDsp.mixDualDeckChunk(
              outgoing = deckAProcessedChunk,
              incoming = deckBProcessedChunk,
              frameCount = framesToProcess,
              channelCount = channels,
              progressStart = progressStart,
              progressEnd = progressEnd,
              masterGain = snapshot.effectiveVolume,
              output = outChunk,
            )

            _state.update {
              it.copy(
                isCrossfading = true,
                crossfadeProgress = progressEnd,
                crossfadeNextTrackTitle = nextDeck.track.title,
              )
            }
          } else {
            if (snapshot.isCrossfading) {
              _state.update {
                it.copy(
                  isCrossfading = false,
                  crossfadeProgress = 0f,
                  crossfadeNextTrackTitle = null,
                )
              }
            }
            shifter.processBlock(
              input = inChunk,
              frameCount = framesToProcess,
              targetSemitones = snapshot.pitchSemitones,
              output = pitchedChunk,
              volumeGain = 1f,
            )
            reverb.processBlock(
              input = pitchedChunk,
              frameCount = framesToProcess,
              settings = snapshot.reverb,
              output = eqChunk,
              volumeGain = 1f,
            )
            equalizer.processBlock(
              input = eqChunk,
              frameCount = framesToProcess,
              settings = snapshot.equalizer,
              output = outChunk,
              volumeGain = (snapshot.effectiveVolume * deckATrim).coerceIn(0f, 1.5f),
            )
            VolumeEnvelopeDsp.applyEnvelopeToChunk(
              samples = outChunk,
              frameCount = framesToProcess,
              channelCount = channels,
              startFrame = cursor,
              totalFrames = totalFrames,
              envelope = snapshot.activeEnvelope,
            )
          }

          val isDualCrossfadeChunk =
            nextDeck != null && crossfadeFrames > 0 && cursor + framesToProcess > crossfadeStartFrame
          val bpmSourceChunk = if (isDualCrossfadeChunk &&
            (((cursor + framesToProcess) - crossfadeStartFrame).toFloat() / crossfadeFrames.toFloat()) >= 0.5f
          ) {
            deckBInChunk
          } else {
            inChunk
          }
          val liveBpmSnapshot = bpmDetector.processChunk(
            pcm = bpmSourceChunk,
            frameCount = framesToProcess,
            sampleRate = sampleRate,
            channelCount = channels,
          )
          val liveSpectrumSnapshot = spectrumAnalyzer.processChunk(
            pcm = outChunk,
            frameCount = framesToProcess,
            sampleRate = sampleRate,
            channelCount = channels,
          )

          val written = track.write(outChunk, 0, sampleCount, AudioTrack.WRITE_BLOCKING)
          if (written < 0) {
            Log.w(TAG, "AudioTrack write error code=$written")
            break
          }

          // Advance only if user hasn't committed a seek concurrently
          val advanced = playbackFrameCursor.compareAndSet(cursor, cursor + framesToProcess)
          val effectiveFrame = if (advanced) cursor + framesToProcess else playbackFrameCursor.get()

          uiTickCounter++
          if (uiTickCounter % 2 == 0) {
            val posMs = ((effectiveFrame.toDouble() / sampleRate) * 1000.0)
              .toLong()
              .coerceIn(0L, snapshot.durationMs)
            _state.update { current ->
              current.copy(
                positionMs = if (current.isScrubbing) current.positionMs else posMs,
                liveBpm = liveBpmSnapshot.bpm,
                liveBpmConfidence = liveBpmSnapshot.confidence,
                liveBeatPulse = liveBpmSnapshot.beatPulse,
                isBpmAnalyzing = liveBpmSnapshot.isAnalyzing,
                liveSpectrum = liveSpectrumSnapshot,
              )
            }
          }
        }
      } catch (_: CancellationException) {
        // Streaming paused or stopped
      } catch (e: Exception) {
        Log.e(TAG, "PCM streaming loop exception", e)
        withContext(Dispatchers.Main.immediate) {
          releaseControllerWakeLock()
          _state.update {
            it.copy(
              isPlaying = false,
              isCrossfading = false,
              crossfadeProgress = 0f,
              crossfadeNextTrackTitle = null,
              errorMessage = e.localizedMessage ?: "Audio streaming stopped unexpectedly.",
            )
          }
          PlaybackForegroundService.stopService(appContext)
        }
      }
    }
  }

  private fun pausePcmStreaming() {
    streamingJob?.cancel()
    streamingJob = null
    releaseControllerWakeLock()

    val track = activeAudioTrack
    val buffer = activeBuffer
    try {
      if (track != null && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
        track.pause()
      }
      track?.flush()
    } catch (e: IllegalStateException) {
      Log.w(TAG, "Ignored AudioTrack pause error", e)
    }

    val posMs = if (buffer != null) {
      ((playbackFrameCursor.get().toDouble() / buffer.sampleRate) * 1000.0)
        .toLong()
        .coerceIn(0L, _state.value.durationMs)
    } else {
      _state.value.positionMs
    }

    activeSpectrumAnalyzer?.reset()
    _state.update {
      it.copy(
        isPlaying = false,
        positionMs = posMs,
        liveBeatPulse = 0f,
        isBpmAnalyzing = false,
        liveSpectrum = RealTimeSpectrumSnapshot(),
      )
    }
    PlaybackForegroundService.syncNotification(appContext)
  }

  fun skipPrevious() {
    if (playlist.isEmpty()) return
    val currentIdx = playlist.indexOfFirst { it.id == _state.value.currentTrackId }
    val prevIdx = if (currentIdx <= 0) playlist.lastIndex else currentIdx - 1
    loadTrack(playlist[prevIdx], autoPlay = true)
  }

  fun skipNext() {
    if (playlist.isEmpty()) return
    val currentIdx = playlist.indexOfFirst { it.id == _state.value.currentTrackId }
    val nextIdx = if (currentIdx < 0 || currentIdx >= playlist.lastIndex) 0 else currentIdx + 1
    loadTrack(playlist[nextIdx], autoPlay = true)
  }

  fun stopPlayback() {
    streamingJob?.cancel()
    streamingJob = null
    clearNextDeck()
    stopFallbackTicker()
    releaseControllerWakeLock()

    if (activeAudioTrack != null && _state.value.isPrepared) {
      try {
        if (activeAudioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
          activeAudioTrack?.pause()
        }
        activeAudioTrack?.flush()
      } catch (_: IllegalStateException) {
      }
      playbackFrameCursor.set(0)
      activeShifter?.reset()
      activeReverb?.reset()
      activeEqualizer?.reset()
      activeBpmDetector?.reset()
      activeSpectrumAnalyzer?.reset()
    }

    val mp = fallbackMediaPlayer
    if (mp != null && _state.value.isPrepared) {
      try {
        if (mp.isPlaying) mp.pause()
        mp.seekTo(0)
      } catch (_: IllegalStateException) {
      }
    }

    _state.update {
      it.copy(
        isPlaying = false,
        positionMs = 0L,
        isCrossfading = false,
        crossfadeProgress = 0f,
        crossfadeNextTrackTitle = null,
        liveBpm = 0,
        liveBpmConfidence = 0f,
        liveBeatPulse = 0f,
        isBpmAnalyzing = false,
        liveSpectrum = RealTimeSpectrumSnapshot(),
      )
    }
    PlaybackForegroundService.stopService(appContext)
  }

  fun beginSeek() {
    _state.update { it.copy(isScrubbing = true) }
  }

  fun previewSeek(targetPositionMs: Long) {
    val maxDuration = _state.value.durationMs.coerceAtLeast(0L)
    _state.update {
      it.copy(
        isScrubbing = true,
        positionMs = targetPositionMs.coerceIn(0L, maxDuration),
      )
    }
  }

  fun commitSeek(targetPositionMs: Long) {
    val maxDuration = _state.value.durationMs.coerceAtLeast(0L)
    val clamped = targetPositionMs.coerceIn(0L, maxDuration)

    clearNextDeck()

    val buffer = activeBuffer
    if (buffer != null && _state.value.isPrepared) {
      val targetFrame = ((clamped / 1000.0) * buffer.sampleRate)
        .toInt()
        .coerceIn(0, buffer.frameCount)
      playbackFrameCursor.set(targetFrame)
      activeShifter?.reset()
      activeReverb?.reset()
      activeEqualizer?.reset()
      activeBpmDetector?.reset()
      activeSpectrumAnalyzer?.reset()
      if (!_state.value.isPlaying) {
        try {
          activeAudioTrack?.flush()
        } catch (_: IllegalStateException) {
        }
      }
    } else {
      val mp = fallbackMediaPlayer
      if (mp != null && _state.value.isPrepared) {
        try {
          mp.seekTo(clamped.toInt())
        } catch (e: IllegalStateException) {
          Log.e(TAG, "Fallback seek failed", e)
        }
      }
    }

    _state.update {
      it.copy(
        isScrubbing = false,
        positionMs = clamped,
        isCrossfading = false,
        crossfadeProgress = 0f,
        crossfadeNextTrackTitle = null,
        liveBpm = 0,
        liveBpmConfidence = 0f,
        liveBeatPulse = 0f,
        isBpmAnalyzing = it.isPlaying,
        liveSpectrum = RealTimeSpectrumSnapshot(),
      )
    }
  }

  fun seekBy(deltaMs: Long) {
    val current = _state.value
    if (!current.isPrepared) return
    val target = (current.positionMs + deltaMs).coerceIn(0L, current.durationMs)
    commitSeek(target)
  }

  fun setPitchSemitones(semitones: Float) {
    val clamped = PitchShiftDsp.clampSemitones(semitones)
    val clean = if (abs(clamped) < 0.05f) 0f else clamped
    val prev = _state.value.pitchSemitones
    _state.update { it.copy(pitchSemitones = clean) }
    fallbackMediaPlayer?.let { mp ->
      if (_state.value.isPrepared) {
        applyPitchToFallbackMediaPlayer(mp, clean)
      }
    }
    if (_state.value.isPlaying && kotlin.math.round(prev) != kotlin.math.round(clean)) {
      PlaybackForegroundService.syncNotification(appContext)
    }
  }

  fun stepPitchSemitones(delta: Int) {
    val currentWhole = kotlin.math.round(_state.value.pitchSemitones).toInt()
    setPitchSemitones((currentWhole + delta).toFloat())
  }

  fun selectReverbPreset(preset: ReverbPreset) {
    _state.update { it.copy(reverb = it.reverb.withPreset(preset)) }
  }

  fun setReverbWetMix(wetMix: Float) {
    val clamped = wetMix.coerceIn(0f, 1f)
    _state.update { current ->
      val nextPreset = if (clamped <= 0.005f) {
        ReverbPreset.OFF
      } else if (current.reverb.preset == ReverbPreset.OFF) {
        ReverbPreset.CHAMBER
      } else {
        current.reverb.preset
      }
      current.copy(
        reverb = current.reverb.copy(
          preset = nextPreset,
          wetMix = if (clamped <= 0.005f) 0f else clamped,
        )
      )
    }
  }

  fun setReverbRoomSize(roomSize: Float) {
    val clamped = roomSize.coerceIn(0.10f, 0.98f)
    _state.update { current ->
      val nextWet = if (current.reverb.wetMix <= 0.005f) 0.30f else current.reverb.wetMix
      val nextPreset = if (current.reverb.preset == ReverbPreset.OFF) ReverbPreset.CHAMBER else current.reverb.preset
      current.copy(
        reverb = current.reverb.copy(
          preset = nextPreset,
          wetMix = nextWet,
          roomSize = clamped,
        )
      )
    }
  }

  fun setReverbDamping(damping: Float) {
    val clamped = damping.coerceIn(0.05f, 0.95f)
    _state.update { current ->
      val nextWet = if (current.reverb.wetMix <= 0.005f) 0.30f else current.reverb.wetMix
      val nextPreset = if (current.reverb.preset == ReverbPreset.OFF) ReverbPreset.CHAMBER else current.reverb.preset
      current.copy(
        reverb = current.reverb.copy(
          preset = nextPreset,
          wetMix = nextWet,
          damping = clamped,
        )
      )
    }
  }

  fun setReverbEngineMode(mode: ReverbEngineMode) {
    _state.update { current ->
      current.copy(
        reverb = current.reverb.copy(engineMode = mode)
      )
    }
  }

  fun resetReverb() {
    _state.update { it.copy(reverb = ReverbSettings(engineMode = it.reverb.engineMode)) }
  }

  fun setEqualizerEnabled(enabled: Boolean) {
    _state.update { it.copy(equalizer = it.equalizer.copy(isEnabled = enabled)) }
  }

  fun selectEqualizerPreset(preset: EqPreset) {
    _state.update { it.copy(equalizer = it.equalizer.withPreset(preset)) }
  }

  fun setEqualizerBandGain(bandIndex: Int, gainDb: Float) {
    _state.update { it.copy(equalizer = it.equalizer.withBandGain(bandIndex, gainDb)) }
  }

  fun resetEqualizer() {
    _state.update { it.copy(equalizer = EqualizerSettings()) }
  }

  fun setCrossfadeEnabled(enabled: Boolean) {
    _state.update { current ->
      val nextDur = if (enabled && current.crossfade.durationSec < 0.5f) {
        CrossfadeDsp.DEFAULT_DURATION_SEC
      } else {
        current.crossfade.durationSec
      }
      current.copy(
        crossfade = CrossfadeSettings(enabled = enabled, durationSec = nextDur).clamped(),
      )
    }
    if (!enabled) {
      clearNextDeck()
    }
  }

  fun setCrossfadeDurationSec(seconds: Float) {
    val clamped = CrossfadeDsp.clampDurationSec(seconds)
    val enabled = clamped >= 0.25f
    _state.update { current ->
      current.copy(
        crossfade = CrossfadeSettings(
          enabled = enabled,
          durationSec = clamped,
        )
      )
    }
    if (!enabled) {
      clearNextDeck()
    }
  }

  fun selectCrossfadePreset(seconds: Float) {
    val clamped = CrossfadeDsp.clampDurationSec(seconds)
    val enabled = clamped >= 0.25f
    _state.update { current ->
      current.copy(
        crossfade = CrossfadeSettings(
          enabled = enabled,
          durationSec = if (enabled) clamped else current.crossfade.durationSec,
        )
      )
    }
    if (!enabled) {
      clearNextDeck()
    }
  }

  fun updateActiveTrackEnvelope(trackId: String, envelope: TrackAudioEnvelope) {
    if (_state.value.currentTrackId == trackId) {
      _state.update { it.copy(activeEnvelope = envelope) }
    }
    preparedNextDeck?.let { next ->
      if (next.track.id == trackId) {
        preparedNextDeck = next.copy(track = next.track.copy(audioEnvelope = envelope))
      }
    }
  }

  fun setVolume(newVolume: Float) {
    val clamped = newVolume.coerceIn(0f, 1f)
    if (clamped > 0f) {
      lastNonZeroVolume = clamped
    }
    val muted = clamped <= 0.001f
    _state.update { it.copy(volume = clamped, isMuted = muted) }
    applyVolumeToFallbackPlayer()
  }

  fun toggleMute() {
    val current = _state.value
    if (current.isMuted) {
      val restored = if (current.volume > 0.01f) current.volume else lastNonZeroVolume
      _state.update { it.copy(isMuted = false, volume = restored) }
    } else {
      if (current.volume > 0.01f) {
        lastNonZeroVolume = current.volume
      }
      _state.update { it.copy(isMuted = true) }
    }
    applyVolumeToFallbackPlayer()
  }

  fun toggleLoop() {
    val nextLoop = !_state.value.isLooping
    _state.update { it.copy(isLooping = nextLoop) }
    if (nextLoop) {
      clearNextDeck()
      _state.update {
        it.copy(
          isCrossfading = false,
          crossfadeProgress = 0f,
          crossfadeNextTrackTitle = null,
        )
      }
    }
    try {
      fallbackMediaPlayer?.isLooping = nextLoop
    } catch (e: IllegalStateException) {
      Log.e(TAG, "Failed to update looping state", e)
    }
  }

  fun setAutoPostGain(enabled: Boolean, targetLufs: Float) {
    val buf = activeBuffer
    val trimDb = if (enabled && buf != null) {
      LoudnessNormalizerDsp.computeGainAdjustmentDb(
        measuredLufs = buf.integratedLufs,
        targetLufs = targetLufs,
        peakAmplitude = buf.peakAmplitude,
      )
    } else {
      0f
    }
    _state.update {
      it.copy(
        autoPostGainEnabled = enabled,
        targetLoudnessLufs = targetLufs,
        autoGainTrimDb = trimDb,
      )
    }
    applyVolumeToFallbackPlayer()
  }

  fun restoreProjectSettings(
    volume: Float,
    isMuted: Boolean,
    isLooping: Boolean,
    pitchSemitones: Float,
    reverb: ReverbSettings,
    equalizer: EqualizerSettings,
    crossfade: CrossfadeSettings,
    autoPostGainEnabled: Boolean,
    targetLoudnessLufs: Float,
    activeEnvelope: TrackAudioEnvelope? = null,
  ) {
    val safeVolume = volume.coerceIn(0f, 1f)
    if (safeVolume > 0.01f) {
      lastNonZeroVolume = safeVolume
    }
    val clampedPitch = PitchShiftDsp.clampSemitones(pitchSemitones).let {
      if (abs(it) < 0.05f) 0f else it
    }
    val buf = activeBuffer
    val trimDb = if (autoPostGainEnabled && buf != null) {
      LoudnessNormalizerDsp.computeGainAdjustmentDb(
        measuredLufs = buf.integratedLufs,
        targetLufs = targetLoudnessLufs,
        peakAmplitude = buf.peakAmplitude,
      )
    } else {
      0f
    }
    if (!crossfade.enabled) {
      clearNextDeck()
    }
    _state.update { current ->
      current.copy(
        volume = safeVolume,
        isMuted = isMuted,
        isLooping = isLooping,
        pitchSemitones = clampedPitch,
        reverb = reverb,
        equalizer = equalizer,
        crossfade = crossfade.clamped(),
        autoPostGainEnabled = autoPostGainEnabled,
        targetLoudnessLufs = targetLoudnessLufs,
        autoGainTrimDb = trimDb,
        activeEnvelope = activeEnvelope ?: current.activeEnvelope,
      )
    }
    applyVolumeToFallbackPlayer()
    fallbackMediaPlayer?.let { mp ->
      try {
        mp.isLooping = isLooping
      } catch (_: IllegalStateException) {
      }
      if (_state.value.isPrepared) {
        applyPitchToFallbackMediaPlayer(mp, clampedPitch)
      }
    }
  }

  fun clearTrack() {
    stopAllPlaybackResources()
    _state.update {
      AudioPlaybackState(
        volume = it.volume,
        isMuted = it.isMuted,
        isLooping = it.isLooping,
        pitchSemitones = it.pitchSemitones,
        reverb = it.reverb,
        equalizer = it.equalizer,
        crossfade = it.crossfade,
        autoPostGainEnabled = it.autoPostGainEnabled,
        targetLoudnessLufs = it.targetLoudnessLufs,
        autoGainTrimDb = 0f,
      )
    }
    PlaybackForegroundService.stopService(appContext)
  }

  private fun applyVolumeToFallbackPlayer() {
    val effective = _state.value.effectiveVolume
    try {
      fallbackMediaPlayer?.setVolume(effective, effective)
    } catch (e: IllegalStateException) {
      Log.e(TAG, "Failed to apply fallback volume", e)
    }
  }

  private fun applyPitchToFallbackMediaPlayer(mp: MediaPlayer, semitones: Float) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      try {
        val ratio = PitchShiftDsp.semitonesToPitchFactor(semitones).toFloat()
        val params = mp.playbackParams
        params.pitch = ratio
        mp.playbackParams = params
      } catch (e: Exception) {
        Log.w(TAG, "Unable to apply PlaybackParams pitch on fallback MediaPlayer", e)
      }
    }
  }

  private fun startFallbackTicker() {
    stopFallbackTicker()
    fallbackTickerJob = scope.launch {
      while (isActive) {
        val mp = fallbackMediaPlayer
        val currentState = _state.value
        if (mp != null && currentState.isPrepared && mp.isPlaying && !currentState.isScrubbing) {
          val duration = currentState.durationMs.coerceAtLeast(1L)
          val pos = mp.currentPosition.toLong().coerceIn(0L, duration)
          val remaining = (duration - pos).coerceAtLeast(0L)

          if (!currentState.isLooping && currentState.crossfade.isActive && playlist.size > 1) {
            val nextTrack = resolveNextTrackInPlaylist(currentState.currentTrackId)
            val effectiveCf = nextTrack?.let {
              CrossfadeDsp.computeEffectiveCrossfadeMs(currentState.crossfade, duration, it.durationMs)
            } ?: 0L

            if (nextTrack != null && effectiveCf > 0L && remaining <= effectiveCf) {
              if (fallbackIncomingPlayer == null && fallbackCrossfadeStartMs < 0L) {
                startFallbackIncomingPlayer(nextTrack, effectiveCf)
              }
              val incoming = fallbackIncomingPlayer
              if (incoming != null && fallbackCrossfadeDurationMs > 0L) {
                val elapsed = (pos - fallbackCrossfadeStartMs).coerceAtLeast(0L)
                val progress = (elapsed.toFloat() / fallbackCrossfadeDurationMs.toFloat()).coerceIn(0f, 1f)
                val (outGain, inGain) = CrossfadeDsp.computeEqualPowerGains(progress)
                val envGain = if (currentState.activeEnvelope.isEdited) {
                  VolumeEnvelopeDsp.sampleEnvelopeGainAt(
                    currentState.activeEnvelope,
                    pos.toFloat() / duration.toFloat(),
                  )
                } else {
                  1f
                }
                val master = currentState.effectiveVolume
                val outVol = (outGain * envGain * master).coerceIn(0f, 1f)
                val inVol = (inGain * master).coerceIn(0f, 1f)
                try {
                  mp.setVolume(outVol, outVol)
                  incoming.setVolume(inVol, inVol)
                } catch (_: IllegalStateException) {
                }
                _state.update {
                  it.copy(
                    positionMs = pos,
                    isCrossfading = true,
                    crossfadeProgress = progress,
                    crossfadeNextTrackTitle = nextTrack.title,
                  )
                }
              } else {
                applyFallbackEnvelopeGain(mp, currentState, pos, duration)
                _state.update { it.copy(positionMs = pos) }
              }
            } else {
              applyFallbackEnvelopeGain(mp, currentState, pos, duration)
              _state.update { it.copy(positionMs = pos) }
            }
          } else {
            applyFallbackEnvelopeGain(mp, currentState, pos, duration)
            _state.update { it.copy(positionMs = pos) }
          }
        }
        delay(60L)
      }
    }
  }

  private fun applyFallbackEnvelopeGain(
    mp: MediaPlayer,
    currentState: AudioPlaybackState,
    posMs: Long,
    durationMs: Long,
  ) {
    if (!currentState.activeEnvelope.isEdited) return
    val fraction = (posMs.toFloat() / durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
    val envGain = VolumeEnvelopeDsp.sampleEnvelopeGainAt(currentState.activeEnvelope, fraction)
    val vol = (currentState.effectiveVolume * envGain).coerceIn(0f, 1f)
    try {
      mp.setVolume(vol, vol)
    } catch (_: IllegalStateException) {
    }
  }

  private fun stopFallbackTicker() {
    fallbackTickerJob?.cancel()
    fallbackTickerJob = null
  }

  private fun startFallbackIncomingPlayer(nextTrack: GeneratedTrack, crossfadeMs: Long) {
    val file = File(nextTrack.filePath)
    if (!file.exists()) return
    fallbackCrossfadeStartMs = _state.value.positionMs
    fallbackCrossfadeDurationMs = crossfadeMs
    fallbackIncomingTrack = nextTrack
    try {
      val incoming = MediaPlayer().apply {
        setAudioAttributes(
          AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
        )
        setDataSource(file.absolutePath)
        setVolume(0f, 0f)
        setOnPreparedListener { mp ->
          applyPitchToFallbackMediaPlayer(mp, _state.value.pitchSemitones)
          mp.start()
        }
        prepareAsync()
      }
      fallbackIncomingPlayer = incoming
    } catch (e: Exception) {
      Log.w(TAG, "Fallback crossfade incoming prepare failed", e)
      fallbackIncomingPlayer = null
      fallbackIncomingTrack = null
    }
  }

  private fun promoteFallbackIncomingPlayer(incoming: MediaPlayer, track: GeneratedTrack) {
    try {
      fallbackMediaPlayer?.reset()
      fallbackMediaPlayer?.release()
    } catch (_: Exception) {
    }
    fallbackMediaPlayer = incoming
    fallbackIncomingPlayer = null
    fallbackIncomingTrack = null
    fallbackCrossfadeStartMs = -1L
    fallbackCrossfadeDurationMs = 0L
    val vol = _state.value.effectiveVolume
    try {
      incoming.setVolume(vol, vol)
      incoming.setOnCompletionListener { mp ->
        if (!mp.isLooping) {
          stopFallbackTicker()
          val next = resolveNextTrackInPlaylist(_state.value.currentTrackId)
          if (next != null && playlist.size > 1) {
            loadTrack(next, autoPlay = true)
          } else {
            mp.seekTo(0)
            _state.update { it.copy(isPlaying = false, positionMs = 0L) }
          }
        }
      }
    } catch (_: Exception) {
    }
    val realDuration = try {
      incoming.duration.toLong().takeIf { it > 0L } ?: track.durationMs
    } catch (_: Exception) {
      track.durationMs
    }
    _state.update {
      it.copy(
        currentTrackId = track.id,
        durationMs = realDuration,
        positionMs = try { incoming.currentPosition.toLong().coerceAtLeast(0L) } catch (_: Exception) { 0L },
        isPlaying = true,
        activeEnvelope = track.audioEnvelope,
        isCrossfading = false,
        crossfadeProgress = 0f,
        crossfadeNextTrackTitle = null,
      )
    }
    startFallbackTicker()
    PlaybackForegroundService.syncNotification(appContext)
  }

  private fun resolveNextTrackInPlaylist(currentTrackId: String?): GeneratedTrack? {
    if (playlist.size <= 1) return null
    val currentIdx = playlist.indexOfFirst { it.id == currentTrackId }
    val nextIdx = if (currentIdx < 0 || currentIdx >= playlist.lastIndex) 0 else currentIdx + 1
    val candidate = playlist[nextIdx]
    return if (candidate.id == currentTrackId) null else candidate
  }

  private fun maybePreloadNextDeck(
    currentTrackId: String?,
    targetSampleRate: Int,
    targetChannels: Int,
  ) {
    val nextTrack = resolveNextTrackInPlaylist(currentTrackId) ?: return
    if (preparedNextDeck?.track?.id == nextTrack.id || failedNextDeckTrackId == nextTrack.id) {
      return
    }
    if (nextDeckLoadJob?.isActive == true) return

    nextDeckLoadJob = scope.launch(Dispatchers.IO) {
      try {
        val file = File(nextTrack.filePath)
        if (!file.exists()) {
          failedNextDeckTrackId = nextTrack.id
          return@launch
        }
        val decoded = FourierAudioBufferProcessor.decodeFileToRawBuffer(
          file = file,
          bridgeSource = nextTrack.sourceLabel,
        )
        if (decoded.pcmSamples.isEmpty() || decoded.frameCount <= 0) {
          failedNextDeckTrackId = nextTrack.id
          return@launch
        }
        if (_state.value.currentTrackId != currentTrackId || _state.value.isLooping) {
          return@launch
        }
        preparedNextDeck = CrossfadeDeck(
          track = nextTrack,
          buffer = decoded,
          shifter = RealTimePitchShifter(sampleRate = targetSampleRate, channelCount = targetChannels),
          reverb = RealTimeRoomReverb(sampleRate = targetSampleRate, channelCount = targetChannels),
          equalizer = RealTimeGraphicEqualizer(sampleRate = targetSampleRate, channelCount = targetChannels),
          frameCursor = 0,
        )
      } catch (_: CancellationException) {
      } catch (e: Exception) {
        Log.w(TAG, "Crossfade preload failed for ${nextTrack.title}", e)
        failedNextDeckTrackId = nextTrack.id
      }
    }
  }

  private fun promoteNextDeckWithNewAudioTrack(deck: CrossfadeDeck) {
    try {
      activeAudioTrack?.apply {
        if (playState == AudioTrack.PLAYSTATE_PLAYING) pause()
        flush()
        release()
      }
    } catch (_: Exception) {
    }
    val newTrack = try {
      FourierAudioBufferProcessor.buildRawPcmAudioTrack(deck.buffer)
    } catch (_: Exception) {
      null
    }
    if (newTrack == null) {
      loadTrack(deck.track, autoPlay = true)
      return
    }
    val cursor = deck.frameCursor.coerceIn(0, deck.buffer.frameCount)
    val posMs = ((cursor.toDouble() / deck.buffer.sampleRate.coerceAtLeast(8_000)) * 1000.0)
      .toLong()
      .coerceIn(0L, deck.buffer.durationMs.coerceAtLeast(1L))
    activeBuffer = deck.buffer
    activeAudioTrack = newTrack
    activeShifter = RealTimePitchShifter(deck.buffer.sampleRate, deck.buffer.channelCount)
    activeReverb = RealTimeRoomReverb(deck.buffer.sampleRate, deck.buffer.channelCount)
    activeEqualizer = RealTimeGraphicEqualizer(deck.buffer.sampleRate, deck.buffer.channelCount)
    activeBpmDetector = RealTimeBpmDetector(deck.buffer.sampleRate, deck.buffer.channelCount)
    activeSpectrumAnalyzer = RealTimeSpectrumAnalyzer(deck.buffer.sampleRate, deck.buffer.channelCount)
    playbackFrameCursor.set(cursor)
    val currentSnap = _state.value
    val trimDb = if (currentSnap.autoPostGainEnabled) {
      LoudnessNormalizerDsp.computeGainAdjustmentDb(
        measuredLufs = deck.buffer.integratedLufs,
        targetLufs = currentSnap.targetLoudnessLufs,
        peakAmplitude = deck.buffer.peakAmplitude,
      )
    } else {
      0f
    }
    _state.update {
      it.copy(
        currentTrackId = deck.track.id,
        isPreparing = false,
        isPrepared = true,
        durationMs = deck.buffer.durationMs.coerceAtLeast(1L),
        positionMs = posMs,
        activeEnvelope = deck.track.audioEnvelope,
        isCrossfading = false,
        crossfadeProgress = 0f,
        crossfadeNextTrackTitle = null,
        liveBpm = 0,
        liveBpmConfidence = 0f,
        liveBeatPulse = 0f,
        isBpmAnalyzing = true,
        autoGainTrimDb = trimDb,
        liveSpectrum = RealTimeSpectrumSnapshot(),
      )
    }
    startPcmStreaming()
  }

  private fun clearNextDeck() {
    nextDeckLoadJob?.cancel()
    nextDeckLoadJob = null
    preparedNextDeck = null
    failedNextDeckTrackId = null
    try {
      fallbackIncomingPlayer?.reset()
      fallbackIncomingPlayer?.release()
    } catch (_: Exception) {
    } finally {
      fallbackIncomingPlayer = null
      fallbackIncomingTrack = null
      fallbackCrossfadeStartMs = -1L
      fallbackCrossfadeDurationMs = 0L
    }
  }

  private fun stopAllPlaybackResources() {
    loadJob?.cancel()
    loadJob = null
    streamingJob?.cancel()
    streamingJob = null
    clearNextDeck()
    stopFallbackTicker()
    releaseControllerWakeLock()

    try {
      activeAudioTrack?.apply {
        if (playState == AudioTrack.PLAYSTATE_PLAYING) {
          pause()
        }
        flush()
        release()
      }
    } catch (e: Exception) {
      Log.w(TAG, "Ignored AudioTrack release exception", e)
    } finally {
      activeAudioTrack = null
      activeBuffer = null
      activeShifter = null
      activeReverb = null
      activeEqualizer = null
      activeBpmDetector = null
      activeSpectrumAnalyzer = null
      playbackFrameCursor.set(0)
    }

    try {
      fallbackMediaPlayer?.reset()
      fallbackMediaPlayer?.release()
    } catch (e: Exception) {
      Log.w(TAG, "Ignored MediaPlayer release exception", e)
    } finally {
      fallbackMediaPlayer = null
    }
  }

  private fun acquireControllerWakeLock() {
    try {
      if (controllerWakeLock == null) {
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        controllerWakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SonicForge:PcmStreamWakeLock")
          ?.apply { setReferenceCounted(false) }
      }
      if (controllerWakeLock?.isHeld == false) {
        controllerWakeLock?.acquire(10 * 60 * 1000L)
      }
    } catch (e: Exception) {
      Log.w(TAG, "Unable to acquire controller WakeLock", e)
    }
  }

  private fun releaseControllerWakeLock() {
    try {
      if (controllerWakeLock?.isHeld == true) {
        controllerWakeLock?.release()
      }
    } catch (e: Exception) {
      Log.w(TAG, "Unable to release controller WakeLock", e)
    }
  }

  companion object {
    private const val TAG = "AudioPlayerController"

    @Volatile
    private var INSTANCE: AudioPlayerController? = null

    fun getInstance(context: Context): AudioPlayerController {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: AudioPlayerController(context.applicationContext).also { INSTANCE = it }
      }
    }
  }
}
