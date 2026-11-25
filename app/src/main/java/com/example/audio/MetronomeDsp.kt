package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class MetronomeTone(
  val id: String,
  val label: String,
) {
  DIGITAL_CLICK(id = "digital", label = "Digital"),
  WOODBLOCK(id = "woodblock", label = "Woodblock"),
  STUDIO_BELL(id = "bell", label = "Studio Bell"),
}

enum class MetronomeTimeSignature(
  val id: String,
  val label: String,
  val beatsPerBar: Int,
  val beatUnit: Int,
) {
  TWO_FOUR(id = "2_4", label = "2/4", beatsPerBar = 2, beatUnit = 4),
  THREE_FOUR(id = "3_4", label = "3/4", beatsPerBar = 3, beatUnit = 4),
  FOUR_FOUR(id = "4_4", label = "4/4", beatsPerBar = 4, beatUnit = 4),
  SIX_EIGHT(id = "6_8", label = "6/8", beatsPerBar = 6, beatUnit = 8),
}

data class MetronomeState(
  val isPlaying: Boolean = false,
  val bpm: Int = 112,
  val isBpmSynced: Boolean = true,
  val syncedAppBpm: Int = 0,
  val bpmSyncSourceLabel: String = "",
  val volume: Float = 0.80f,
  val pitchHz: Float = MetronomeDsp.DEFAULT_PITCH_HZ,
  val toneStyle: MetronomeTone = MetronomeTone.DIGITAL_CLICK,
  val timeSignature: MetronomeTimeSignature = MetronomeTimeSignature.FOUR_FOUR,
  val accentDownbeat: Boolean = true,
  val currentBeat: Int = -1,
  val beatPulse: Float = 0f,
)

/**
 * Procedural audio DSP & sample-accurate timing engine for SonicForge's built-in recording and
 * sequencing metronome. Supports real-time adjustable volume, fundamental pitch (Hz), tone timber,
 * time signatures, downbeat accenting, tap tempo, and automatic BPM sync.
 */
object MetronomeDsp {

  const val MIN_BPM = 40
  const val MAX_BPM = 220
  const val MIN_PITCH_HZ = 360f
  const val MAX_PITCH_HZ = 1760f
  const val DEFAULT_PITCH_HZ = 880f
  const val DEFAULT_SAMPLE_RATE = 44_100
  private const val ACCENT_PITCH_RATIO = 1.5f

  fun computeBeatIntervalMs(
    bpm: Int,
    timeSignature: MetronomeTimeSignature = MetronomeTimeSignature.FOUR_FOUR,
  ): Double {
    val safeBpm = bpm.coerceIn(MIN_BPM, MAX_BPM)
    val unitScale = 4.0 / timeSignature.beatUnit.coerceAtLeast(1)
    return (60_000.0 / safeBpm) * unitScale
  }

  /**
   * Computes tempo in BPM from recent tap timestamps in milliseconds. Returns `null` if fewer than
   * 2 taps exist in the active window.
   */
  fun computeTapTempoBpm(tapTimestampsMs: List<Long>): Int? {
    if (tapTimestampsMs.size < 2) return null
    val intervals = mutableListOf<Long>()
    for (i in 1 until tapTimestampsMs.size) {
      val delta = tapTimestampsMs[i] - tapTimestampsMs[i - 1]
      if (delta in 220L..2_000L) {
        intervals += delta
      }
    }
    if (intervals.isEmpty()) return null
    val avgMs = intervals.average()
    if (avgMs <= 0.0) return null
    return (60_000.0 / avgMs).roundToInt().coerceIn(MIN_BPM, MAX_BPM)
  }

  /**
   * Synthesizes a single mono click transient at [pitchHz] scaled by [volume].
   * Downbeat accents shift up by a musical fifth (`1.5x` frequency) and use full gain.
   */
  fun synthesizeClickSamples(
    pitchHz: Float,
    volume: Float,
    isAccentBeat: Boolean,
    toneStyle: MetronomeTone = MetronomeTone.DIGITAL_CLICK,
    sampleRate: Int = DEFAULT_SAMPLE_RATE,
  ): FloatArray {
    val safeRate = sampleRate.coerceAtLeast(8_000)
    val safeVolume = volume.coerceIn(0f, 1f)
    val baseFreq = pitchHz.coerceIn(MIN_PITCH_HZ, MAX_PITCH_HZ)
    val activeFreq = (if (isAccentBeat) baseFreq * ACCENT_PITCH_RATIO else baseFreq)
      .coerceAtMost(safeRate * 0.42f)
    val beatGain = safeVolume * (if (isAccentBeat) 0.92f else 0.64f)

    val durationSec = when (toneStyle) {
      MetronomeTone.DIGITAL_CLICK -> 0.030
      MetronomeTone.WOODBLOCK -> 0.048
      MetronomeTone.STUDIO_BELL -> 0.085
    }
    val len = (durationSec * safeRate).roundToInt().coerceAtLeast(128)
    val out = FloatArray(len)
    if (beatGain <= 0.0001f) return out

    var phase1 = 0.0
    var phase2 = 0.0
    val inc1 = 2.0 * PI * activeFreq / safeRate
    val harmonicRatio = when (toneStyle) {
      MetronomeTone.DIGITAL_CLICK -> 2.0
      MetronomeTone.WOODBLOCK -> 2.58
      MetronomeTone.STUDIO_BELL -> 2.0
    }
    val inc2 = 2.0 * PI * min(activeFreq * harmonicRatio, safeRate * 0.45) / safeRate

    for (i in 0 until len) {
      val t = i.toDouble() / safeRate
      val attack = 1.0 - exp(-t * 1_600.0)
      val decay = when (toneStyle) {
        MetronomeTone.DIGITAL_CLICK -> exp(-t * 145.0)
        MetronomeTone.WOODBLOCK -> exp(-t * 92.0)
        MetronomeTone.STUDIO_BELL -> exp(-t * 48.0)
      }
      val wave = when (toneStyle) {
        MetronomeTone.DIGITAL_CLICK -> 0.78 * sin(phase1) + 0.22 * sin(phase2)
        MetronomeTone.WOODBLOCK -> 0.68 * sin(phase1) + 0.32 * sin(phase2)
        MetronomeTone.STUDIO_BELL -> 0.72 * sin(phase1) + 0.28 * sin(phase2)
      }
      val fadeEnd = if (i > len - 32) (len - 1 - i).toDouble() / 32.0 else 1.0
      out[i] = (wave * attack * decay * fadeEnd * beatGain).toFloat()
      phase1 += inc1
      phase2 += inc2
    }
    return out
  }
}

/**
 * Stateful real-time metronome synthesizer synced to SonicForge's live/detected/sequencer BPM.
 */
class MetronomeEngine {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var playbackJob: Job? = null
  private val recentTapTimesMs = ArrayDeque<Long>()

  private val _state = MutableStateFlow(MetronomeState())
  val state: StateFlow<MetronomeState> = _state.asStateFlow()

  fun setVolume(volume: Float) {
    val clamped = volume.coerceIn(0f, 1f)
    _state.update { it.copy(volume = clamped) }
  }

  fun setPitchHz(pitchHz: Float) {
    val clamped = pitchHz.coerceIn(MetronomeDsp.MIN_PITCH_HZ, MetronomeDsp.MAX_PITCH_HZ)
    _state.update { it.copy(pitchHz = clamped) }
  }

  fun selectToneStyle(toneStyle: MetronomeTone) {
    _state.update { it.copy(toneStyle = toneStyle) }
  }

  fun selectTimeSignature(timeSignature: MetronomeTimeSignature) {
    _state.update { current ->
      val clampedBeat = if (current.currentBeat >= timeSignature.beatsPerBar) {
        0
      } else {
        current.currentBeat
      }
      current.copy(timeSignature = timeSignature, currentBeat = clampedBeat)
    }
  }

  fun toggleAccentDownbeat() {
    _state.update { it.copy(accentDownbeat = !it.accentDownbeat) }
  }

  fun setManualBpm(bpm: Int) {
    val clamped = bpm.coerceIn(MetronomeDsp.MIN_BPM, MetronomeDsp.MAX_BPM)
    _state.update { it.copy(bpm = clamped, isBpmSynced = false) }
  }

  fun stepManualBpm(delta: Int) {
    val target = (_state.value.bpm + delta).coerceIn(MetronomeDsp.MIN_BPM, MetronomeDsp.MAX_BPM)
    setManualBpm(target)
  }

  fun setBpmSynced(synced: Boolean) {
    _state.update { current ->
      val nextBpm = if (synced && current.syncedAppBpm in MetronomeDsp.MIN_BPM..MetronomeDsp.MAX_BPM) {
        current.syncedAppBpm
      } else {
        current.bpm
      }
      current.copy(isBpmSynced = synced, bpm = nextBpm)
    }
  }

  fun toggleBpmSync() {
    setBpmSynced(!_state.value.isBpmSynced)
  }

  fun updateSyncedAppBpm(detectedOrActiveBpm: Int, sourceLabel: String) {
    val validBpm = detectedOrActiveBpm.takeIf { it in MetronomeDsp.MIN_BPM..MetronomeDsp.MAX_BPM } ?: 0
    _state.update { current ->
      val nextBpm = if (current.isBpmSynced && validBpm > 0) {
        validBpm
      } else {
        current.bpm
      }
      current.copy(
        syncedAppBpm = validBpm,
        bpmSyncSourceLabel = if (validBpm > 0) sourceLabel else "",
        bpm = nextBpm,
      )
    }
  }

  fun restoreState(
    bpm: Int,
    isBpmSynced: Boolean,
    volume: Float,
    pitchHz: Float,
    toneStyle: MetronomeTone,
    timeSignature: MetronomeTimeSignature,
    accentDownbeat: Boolean,
  ) {
    stop()
    _state.update { current ->
      val safeBpm = bpm.coerceIn(MetronomeDsp.MIN_BPM, MetronomeDsp.MAX_BPM)
      val resolvedBpm = if (isBpmSynced && current.syncedAppBpm in MetronomeDsp.MIN_BPM..MetronomeDsp.MAX_BPM) {
        current.syncedAppBpm
      } else {
        safeBpm
      }
      current.copy(
        bpm = resolvedBpm,
        isBpmSynced = isBpmSynced,
        volume = volume.coerceIn(0f, 1f),
        pitchHz = pitchHz.coerceIn(MetronomeDsp.MIN_PITCH_HZ, MetronomeDsp.MAX_PITCH_HZ),
        toneStyle = toneStyle,
        timeSignature = timeSignature,
        accentDownbeat = accentDownbeat,
        currentBeat = -1,
        beatPulse = 0f,
      )
    }
  }

  fun tapTempo(nowMs: Long = SystemClock.elapsedRealtime()) {
    val lastTap = recentTapTimesMs.lastOrNull()
    if (lastTap != null && (nowMs - lastTap) > 2_200L) {
      recentTapTimesMs.clear()
    }
    recentTapTimesMs.addLast(nowMs)
    while (recentTapTimesMs.size > 6) {
      recentTapTimesMs.removeFirst()
    }
    MetronomeDsp.computeTapTempoBpm(recentTapTimesMs.toList())?.let { tappedBpm ->
      setManualBpm(tappedBpm)
    }
  }

  fun togglePlayStop() {
    if (_state.value.isPlaying) {
      stop()
    } else {
      start()
    }
  }

  fun start() {
    if (_state.value.isPlaying) return
    playbackJob?.cancel()
    _state.update { it.copy(isPlaying = true, currentBeat = 0, beatPulse = 1f) }

    playbackJob = scope.launch(Dispatchers.Default) {
      val sampleRate = MetronomeDsp.DEFAULT_SAMPLE_RATE
      val chunkFrames = 512
      val outShort = ShortArray(chunkFrames)
      val ringSize = sampleRate / 2 // 500 ms tail ring
      val mixRing = FloatArray(ringSize)
      var ringCursor = 0
      var framesUntilNextBeat = 0
      var beatIndex = 0

      val audioTrack = try {
        val minBuf = AudioTrack.getMinBufferSize(
          sampleRate,
          AudioFormat.CHANNEL_OUT_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4096)
        AudioTrack.Builder()
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
              .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
              .build()
          )
          .setTransferMode(AudioTrack.MODE_STREAM)
          .setBufferSizeInBytes(minBuf * 2)
          .build()
          .apply { play() }
      } catch (e: Exception) {
        Log.w(TAG, "Metronome AudioTrack unavailable, running silent timer fallback", e)
        null
      }

      try {
        if (audioTrack == null) {
          while (isActive && _state.value.isPlaying) {
            val snap = _state.value
            val activeBeat = beatIndex % snap.timeSignature.beatsPerBar
            _state.update {
              it.copy(
                currentBeat = activeBeat,
                beatPulse = if (activeBeat == 0 && snap.accentDownbeat) 1f else 0.72f,
              )
            }
            val beatMs = MetronomeDsp.computeBeatIntervalMs(snap.bpm, snap.timeSignature)
            delay(beatMs.roundToInt().toLong().coerceAtLeast(40L))
            beatIndex = (activeBeat + 1) % snap.timeSignature.beatsPerBar
          }
        } else {
          while (isActive && _state.value.isPlaying) {
            val snap = _state.value
            for (i in 0 until chunkFrames) {
              if (framesUntilNextBeat <= 0) {
                val activeBeat = beatIndex % snap.timeSignature.beatsPerBar
                val isAccent = snap.accentDownbeat && activeBeat == 0
                val click = MetronomeDsp.synthesizeClickSamples(
                  pitchHz = snap.pitchHz,
                  volume = snap.volume,
                  isAccentBeat = isAccent,
                  toneStyle = snap.toneStyle,
                  sampleRate = sampleRate,
                )
                val writeLen = min(click.size, ringSize)
                for (c in 0 until writeLen) {
                  val pos = (ringCursor + c) % ringSize
                  mixRing[pos] += click[c]
                }
                _state.update {
                  it.copy(
                    currentBeat = activeBeat,
                    beatPulse = if (isAccent) 1f else 0.72f,
                  )
                }
                val beatMs = MetronomeDsp.computeBeatIntervalMs(snap.bpm, snap.timeSignature)
                framesUntilNextBeat = ((beatMs / 1000.0) * sampleRate).roundToInt().coerceAtLeast(512)
                beatIndex = (activeBeat + 1) % snap.timeSignature.beatsPerBar
              }

              val sample = tanh(mixRing[ringCursor].toDouble() * 1.05)
              mixRing[ringCursor] = 0f
              outShort[i] = (sample * Short.MAX_VALUE * 0.90).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
              ringCursor = (ringCursor + 1) % ringSize
              framesUntilNextBeat--
            }

            // Decay pulse smoothly between beats
            _state.update { current ->
              val decayed = (current.beatPulse * 0.84f).let { if (it < 0.05f) 0f else it }
              if (decayed != current.beatPulse) current.copy(beatPulse = decayed) else current
            }

            val writeStartNs = System.nanoTime()
            val written = audioTrack.write(outShort, 0, chunkFrames, AudioTrack.WRITE_BLOCKING)
            if (written < 0) break
            val writeElapsedMs = (System.nanoTime() - writeStartNs) / 1_000_000L
            if (writeElapsedMs < 2L) {
              delay(11L)
            }
          }
        }
      } catch (_: CancellationException) {
      } finally {
        try {
          audioTrack?.apply {
            if (playState == AudioTrack.PLAYSTATE_PLAYING) pause()
            flush()
            release()
          }
        } catch (_: Exception) {
        }
      }
    }
  }

  fun stop() {
    playbackJob?.cancel()
    playbackJob = null
    _state.update { it.copy(isPlaying = false, currentBeat = -1, beatPulse = 0f) }
  }

  fun release() {
    stop()
    scope.cancel()
  }

  companion object {
    private const val TAG = "MetronomeEngine"
  }
}
