package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.model.GeneratedTrack
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AudioEngineStatus {
  IDLE,
  LOADING,
  PLAYING,
  PAUSED,
  COMPLETED,
  ERROR,
}

data class AudioEngineState(
  val status: AudioEngineStatus = AudioEngineStatus.IDLE,
  val activeClipId: String? = null,
  val activeClipTitle: String? = null,
  val sampleRate: Int = AudioEngine.DEFAULT_SAMPLE_RATE,
  val channelCount: Int = 1,
  val positionMs: Long = 0L,
  val durationMs: Long = 0L,
  val volume: Float = 0.9f,
  val isLooping: Boolean = false,
  val errorMessage: String? = null,
) {
  val isPlaying: Boolean
    get() = status == AudioEngineStatus.PLAYING

  val isPaused: Boolean
    get() = status == AudioEngineStatus.PAUSED

  val progressFraction: Float
    get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

/**
 * Abstraction over [AudioTrack] enabling deterministic unit testing without physical audio hardware.
 */
interface AudioTrackHandle {
  fun setVolume(gain: Float): Int
  fun play()
  fun pause()
  fun flush()
  fun writeBlocking(samples: ShortArray, offsetInShorts: Int, sizeInShorts: Int): Int
  fun stop()
  fun release()
}

fun interface AudioTrackFactory {
  fun create(sampleRate: Int, channelCount: Int, bufferSizeInBytes: Int): AudioTrackHandle?
}

private class AndroidAudioTrackHandle(
  private val audioTrack: AudioTrack,
) : AudioTrackHandle {

  override fun setVolume(gain: Float): Int = audioTrack.setVolume(gain.coerceIn(0f, 1f))

  override fun play() {
    audioTrack.play()
  }

  override fun pause() {
    audioTrack.pause()
  }

  override fun flush() {
    audioTrack.flush()
  }

  override fun writeBlocking(samples: ShortArray, offsetInShorts: Int, sizeInShorts: Int): Int =
    audioTrack.write(samples, offsetInShorts, sizeInShorts, AudioTrack.WRITE_BLOCKING)

  override fun stop() {
    audioTrack.stop()
  }

  override fun release() {
    audioTrack.release()
  }
}

object DefaultAudioTrackFactory : AudioTrackFactory {
  override fun create(sampleRate: Int, channelCount: Int, bufferSizeInBytes: Int): AudioTrackHandle? {
    return try {
      val channelMask = if (channelCount >= 2) {
        AudioFormat.CHANNEL_OUT_STEREO
      } else {
        AudioFormat.CHANNEL_OUT_MONO
      }
      val minBuf = AudioTrack.getMinBufferSize(
        sampleRate,
        channelMask,
        AudioFormat.ENCODING_PCM_16BIT,
      ).coerceAtLeast(2048)
      val resolvedBufferBytes = max(minBuf, bufferSizeInBytes)

      val track = AudioTrack.Builder()
        .setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        )
        .setAudioFormat(
          AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(channelMask)
            .build()
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(resolvedBufferBytes)
        .build()

      AndroidAudioTrackHandle(track)
    } catch (e: Exception) {
      Log.w("AudioEngine", "Unable to initialize AudioTrack: ${e.message}")
      null
    }
  }
}

/**
 * Lightweight, general-purpose studio AudioTrack playback engine for generated sound clips,
 * synthesized instrument patterns, and one-shot PCM buffers.
 *
 * Streams 16-bit PCM audio in chunked frames with non-blocking transport controls
 * (`play`, `pause`, `resume`, `seekTo`, `stop`, `setVolume`, `setLooping`).
 */
class AudioEngine(
  private val trackFactory: AudioTrackFactory = DefaultAudioTrackFactory,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private val _state = MutableStateFlow(AudioEngineState())
  val state: StateFlow<AudioEngineState> = _state.asStateFlow()

  private var activeJob: Job? = null
  private val activeTrackRef = AtomicReference<AudioTrackHandle?>(null)
  private val pauseRequested = AtomicBoolean(false)
  private val loopFlag = AtomicBoolean(false)
  private val frameCursor = AtomicInteger(0)

  @Volatile
  private var currentSamples: ShortArray = ShortArray(0)

  @Volatile
  private var currentSampleRate: Int = DEFAULT_SAMPLE_RATE

  @Volatile
  private var currentChannelCount: Int = 1

  @Volatile
  private var currentVolume: Float = 0.9f

  /**
   * Decodes and plays a [GeneratedTrack] audio file using Android [AudioTrack].
   */
  fun playTrack(
    track: GeneratedTrack,
    looping: Boolean = false,
    onCompleted: () -> Unit = {},
  ) {
    playFile(
      file = File(track.filePath),
      clipId = track.id,
      title = track.title,
      looping = looping,
      onCompleted = onCompleted,
    )
  }

  /**
   * Decodes a local WAV/MP3/audio file into 16-bit PCM and streams playback through [AudioTrack].
   */
  fun playFile(
    file: File,
    clipId: String = file.absolutePath,
    title: String = file.nameWithoutExtension,
    looping: Boolean = false,
    onCompleted: () -> Unit = {},
  ) {
    stop()
    if (!file.exists() || file.length() == 0L) {
      _state.update {
        it.copy(
          status = AudioEngineStatus.ERROR,
          activeClipId = clipId,
          activeClipTitle = title,
          errorMessage = "Audio clip file not found.",
        )
      }
      return
    }

    loopFlag.set(looping)
    _state.update {
      it.copy(
        status = AudioEngineStatus.LOADING,
        activeClipId = clipId,
        activeClipTitle = title,
        isLooping = looping,
        positionMs = 0L,
        errorMessage = null,
      )
    }

    activeJob = scope.launch {
      try {
        val decoded = withContext(ioDispatcher) {
          FourierAudioBufferProcessor.decodeFileToRawBuffer(
            file = file,
            bridgeSource = title,
          )
        }
        if (decoded.pcmSamples.isEmpty() || decoded.frameCount <= 0) {
          _state.update {
            it.copy(
              status = AudioEngineStatus.ERROR,
              errorMessage = "Decoded sound clip contains no audio samples.",
            )
          }
          return@launch
        }
        streamPcmInternal(
          samples = decoded.pcmSamples,
          sampleRate = decoded.sampleRate,
          channelCount = decoded.channelCount,
          clipId = clipId,
          title = title,
          looping = looping,
          onCompleted = onCompleted,
        )
      } catch (_: CancellationException) {
      } catch (e: Exception) {
        _state.update {
          it.copy(
            status = AudioEngineStatus.ERROR,
            errorMessage = e.message ?: "Failed to play sound clip.",
          )
        }
      }
    }
  }

  /**
   * Plays an in-memory [RawAudioBuffer] generated by SonicForge synthesizers or stem mergers.
   */
  fun playBuffer(
    buffer: RawAudioBuffer,
    clipId: String? = null,
    title: String? = buffer.bridgeSource,
    looping: Boolean = false,
    onCompleted: () -> Unit = {},
  ) {
    playPcmSamples(
      samples = buffer.pcmSamples,
      sampleRate = buffer.sampleRate,
      channelCount = buffer.channelCount,
      clipId = clipId,
      title = title,
      looping = looping,
      onCompleted = onCompleted,
    )
  }

  /**
   * Plays normalized float PCM samples (`-1f..1f`), converting to 16-bit signed PCM for [AudioTrack].
   */
  fun playFloatSamples(
    samples: FloatArray,
    sampleRate: Int = DEFAULT_SAMPLE_RATE,
    channelCount: Int = 1,
    clipId: String? = null,
    title: String? = null,
    looping: Boolean = false,
    onCompleted: () -> Unit = {},
  ) {
    if (samples.isEmpty()) return
    val pcm = ShortArray(samples.size) { idx ->
      (samples[idx].coerceIn(-1f, 1f) * Short.MAX_VALUE * 0.85f).toInt().toShort()
    }
    playPcmSamples(
      samples = pcm,
      sampleRate = sampleRate,
      channelCount = channelCount,
      clipId = clipId,
      title = title,
      looping = looping,
      onCompleted = onCompleted,
    )
  }

  /**
   * Plays raw 16-bit interleaved PCM samples directly through Android [AudioTrack].
   */
  fun playPcmSamples(
    samples: ShortArray,
    sampleRate: Int = DEFAULT_SAMPLE_RATE,
    channelCount: Int = 1,
    clipId: String? = null,
    title: String? = null,
    looping: Boolean = false,
    onCompleted: () -> Unit = {},
  ) {
    stop()
    if (samples.isEmpty()) return

    val safeRate = sampleRate.coerceIn(8_000, 96_000)
    val safeChannels = channelCount.coerceIn(1, 2)
    loopFlag.set(looping)

    activeJob = scope.launch {
      try {
        streamPcmInternal(
          samples = samples,
          sampleRate = safeRate,
          channelCount = safeChannels,
          clipId = clipId,
          title = title,
          looping = looping,
          onCompleted = onCompleted,
        )
      } catch (_: CancellationException) {
      }
    }
  }

  fun pause() {
    if (_state.value.status != AudioEngineStatus.PLAYING) return
    pauseRequested.set(true)
    try {
      activeTrackRef.get()?.pause()
    } catch (_: Exception) {
    }
    _state.update { it.copy(status = AudioEngineStatus.PAUSED) }
  }

  fun resume() {
    if (_state.value.status != AudioEngineStatus.PAUSED) return
    pauseRequested.set(false)
    try {
      activeTrackRef.get()?.play()
    } catch (_: Exception) {
    }
    _state.update { it.copy(status = AudioEngineStatus.PLAYING) }
  }

  fun togglePlayPause() {
    when (_state.value.status) {
      AudioEngineStatus.PLAYING -> pause()
      AudioEngineStatus.PAUSED -> resume()
      else -> Unit
    }
  }

  fun seekTo(positionMs: Long) {
    val samples = currentSamples
    if (samples.isEmpty()) return
    val channels = currentChannelCount.coerceIn(1, 2)
    val totalFrames = samples.size / channels
    if (totalFrames <= 0) return

    val durationMs = ((totalFrames.toDouble() / currentSampleRate) * 1000.0).toLong().coerceAtLeast(1L)
    val clampedMs = positionMs.coerceIn(0L, durationMs)
    val targetFrame = ((clampedMs / 1000.0) * currentSampleRate).toInt().coerceIn(0, totalFrames - 1)
    frameCursor.set(targetFrame)
    try {
      activeTrackRef.get()?.flush()
    } catch (_: Exception) {
    }
    _state.update { it.copy(positionMs = clampedMs) }
  }

  fun setVolume(volume: Float) {
    val clamped = volume.coerceIn(0f, 1f)
    currentVolume = clamped
    try {
      activeTrackRef.get()?.setVolume(clamped)
    } catch (_: Exception) {
    }
    _state.update { it.copy(volume = clamped) }
  }

  fun setLooping(looping: Boolean) {
    loopFlag.set(looping)
    _state.update { it.copy(isLooping = looping) }
  }

  fun stop() {
    activeJob?.cancel()
    activeJob = null
    pauseRequested.set(false)
    releaseActiveTrack()
    frameCursor.set(0)
    currentSamples = ShortArray(0)
    _state.update {
      it.copy(
        status = AudioEngineStatus.IDLE,
        positionMs = 0L,
        errorMessage = null,
      )
    }
  }

  fun release() {
    stop()
    scope.cancel()
  }

  private suspend fun streamPcmInternal(
    samples: ShortArray,
    sampleRate: Int,
    channelCount: Int,
    clipId: String?,
    title: String?,
    looping: Boolean,
    onCompleted: () -> Unit,
  ) {
    val safeRate = sampleRate.coerceIn(8_000, 96_000)
    val safeChannels = channelCount.coerceIn(1, 2)
    val totalFrames = samples.size / safeChannels
    if (totalFrames <= 0) return

    val durationMs = ((totalFrames.toDouble() / safeRate) * 1000.0).toLong().coerceAtLeast(1L)
    currentSamples = samples
    currentSampleRate = safeRate
    currentChannelCount = safeChannels
    pauseRequested.set(false)
    frameCursor.set(0)
    loopFlag.set(looping)

    val chunkFrames = STREAM_CHUNK_FRAMES
    val chunkShorts = chunkFrames * safeChannels
    val handle = trackFactory.create(
      sampleRate = safeRate,
      channelCount = safeChannels,
      bufferSizeInBytes = chunkShorts * 4,
    )
    if (handle == null) {
      _state.update {
        it.copy(
          status = AudioEngineStatus.ERROR,
          activeClipId = clipId,
          activeClipTitle = title,
          sampleRate = safeRate,
          channelCount = safeChannels,
          durationMs = durationMs,
          errorMessage = "AudioTrack hardware unavailable.",
        )
      }
      onCompleted()
      return
    }

    activeTrackRef.set(handle)
    var completedNormally = false
    try {
      handle.setVolume(currentVolume)
      handle.play()

      _state.update {
        it.copy(
          status = AudioEngineStatus.PLAYING,
          activeClipId = clipId,
          activeClipTitle = title,
          sampleRate = safeRate,
          channelCount = safeChannels,
          positionMs = 0L,
          durationMs = durationMs,
          isLooping = loopFlag.get(),
          errorMessage = null,
        )
      }

      val writeChunk = ShortArray(chunkShorts)
      while (currentCoroutineContext().isActive) {
        if (pauseRequested.get()) {
          delay(25L)
          continue
        }

        val startFrame = frameCursor.get()
        if (startFrame >= totalFrames) {
          if (loopFlag.get()) {
            frameCursor.set(0)
            continue
          } else {
            completedNormally = true
            break
          }
        }

        val framesToWrite = min(chunkFrames, totalFrames - startFrame)
        val shortsToWrite = framesToWrite * safeChannels
        val srcOffset = startFrame * safeChannels
        samples.copyInto(
          destination = writeChunk,
          destinationOffset = 0,
          startIndex = srcOffset,
          endIndex = srcOffset + shortsToWrite,
        )

        val written = handle.writeBlocking(writeChunk, 0, shortsToWrite)
        if (written < 0) {
          throw IllegalStateException("AudioTrack write failed (error code $written).")
        }
        if (written == 0) {
          delay(5L)
          continue
        }

        val advancedFrames = (written / safeChannels).coerceIn(1, framesToWrite)
        val nextFrame = min(totalFrames, startFrame + advancedFrames)
        frameCursor.compareAndSet(startFrame, nextFrame)

        val posMs = ((nextFrame.toDouble() / safeRate) * 1000.0).toLong().coerceIn(0L, durationMs)
        _state.update { it.copy(positionMs = posMs) }
      }
    } finally {
      releaseActiveTrack()
      if (completedNormally) {
        _state.update {
          it.copy(
            status = AudioEngineStatus.COMPLETED,
            positionMs = durationMs,
          )
        }
        onCompleted()
      }
    }
  }

  private fun releaseActiveTrack() {
    val handle = activeTrackRef.getAndSet(null) ?: return
    try {
      handle.stop()
    } catch (_: Exception) {
    }
    try {
      handle.release()
    } catch (_: Exception) {
    }
  }

  companion object {
    const val DEFAULT_SAMPLE_RATE = 44_100
    const val STREAM_CHUNK_FRAMES = 1024
  }
}
