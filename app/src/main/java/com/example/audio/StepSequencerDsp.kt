package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random
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

enum class SequencerLane(
  val id: String,
  val label: String,
  val shortLabel: String,
  val defaultMidiNote: Int,
) {
  KICK(id = "kick", label = "Kick Drum", shortLabel = "KICK", defaultMidiNote = 36),
  SNARE(id = "snare", label = "Snare / Clap", shortLabel = "SNARE", defaultMidiNote = 38),
  CH_HAT(id = "hat_closed", label = "Closed Hat", shortLabel = "HAT", defaultMidiNote = 42),
  OH_HAT(id = "hat_open", label = "Open Hat", shortLabel = "OPEN", defaultMidiNote = 46),
  BASS(id = "bass", label = "Rhythmic Bass", shortLabel = "BASS", defaultMidiNote = 36),
  SYNTH(id = "synth", label = "Rhythmic Arp", shortLabel = "ARP", defaultMidiNote = 60),
}

enum class SequencerPreset(
  val id: String,
  val label: String,
  val defaultBpm: Int,
  val stepsByLane: Map<SequencerLane, Set<Int>>,
) {
  FOUR_ON_FLOOR(
    id = "four_on_floor",
    label = "Four on Floor",
    defaultBpm = 120,
    stepsByLane = mapOf(
      SequencerLane.KICK to setOf(0, 4, 8, 12),
      SequencerLane.SNARE to setOf(4, 12),
      SequencerLane.CH_HAT to setOf(2, 6, 10, 14),
      SequencerLane.OH_HAT to setOf(15),
      SequencerLane.BASS to setOf(0, 3, 6, 8, 11, 14),
      SequencerLane.SYNTH to setOf(0, 4, 7, 10, 12, 14),
    ),
  ),
  LOFI_BOOM_BAP(
    id = "lofi_boom_bap",
    label = "Lo-Fi Boom Bap",
    defaultBpm = 86,
    stepsByLane = mapOf(
      SequencerLane.KICK to setOf(0, 3, 7, 10),
      SequencerLane.SNARE to setOf(4, 12),
      SequencerLane.CH_HAT to setOf(0, 2, 4, 6, 8, 10, 12, 14),
      SequencerLane.OH_HAT to setOf(11),
      SequencerLane.BASS to setOf(0, 7, 10),
      SequencerLane.SYNTH to setOf(0, 6, 9, 14),
    ),
  ),
  SYNTHWAVE_ARP(
    id = "synthwave_arp",
    label = "Synthwave Arp",
    defaultBpm = 112,
    stepsByLane = mapOf(
      SequencerLane.KICK to setOf(0, 4, 8, 12),
      SequencerLane.SNARE to setOf(4, 12),
      SequencerLane.CH_HAT to setOf(2, 6, 10, 14),
      SequencerLane.OH_HAT to setOf(7, 15),
      SequencerLane.BASS to setOf(0, 2, 4, 6, 8, 10, 12, 14),
      SequencerLane.SYNTH to setOf(0, 2, 3, 6, 8, 10, 11, 14),
    ),
  );

  fun buildGrid(): Map<SequencerLane, List<Boolean>> {
    return SequencerLane.entries.associateWith { lane ->
      val active = stepsByLane[lane].orEmpty()
      List(StepSequencerDsp.STEP_COUNT) { step -> step in active }
    }
  }
}

data class StepSequencerState(
  val pattern: Map<SequencerLane, List<Boolean>> = SequencerPreset.FOUR_ON_FLOOR.buildGrid(),
  val drumKit: DrumKitPreset = DrumKitPreset.STUDIO_ACOUSTIC,
  val bpm: Int = 112,
  val isBpmSynced: Boolean = true,
  val detectedAppBpm: Int = 0,
  val bpmSyncSourceLabel: String = "",
  val swingPercent: Int = 0,
  val isPlaying: Boolean = false,
  val currentStep: Int = -1,
  val selectedPresetName: String = SequencerPreset.FOUR_ON_FLOOR.label,
  val isRenderingToLibrary: Boolean = false,
) {
  val activeStepCount: Int
    get() = pattern.values.sumOf { row -> row.count { it } }

  fun isStepActive(lane: SequencerLane, stepIndex: Int): Boolean {
    return pattern[lane]?.getOrNull(stepIndex) == true
  }
}

/**
 * Procedural DSP synthesizer & grid step-duration engine for the 16-step drum and rhythmic
 * sequence programmer. Renders sample-accurate swung 16th-note hits for both live streaming and
 * LUFS-normalized WAV library track creation.
 */
object StepSequencerDsp {

  const val STEP_COUNT = 16
  const val MIN_BPM = 60
  const val MAX_BPM = 176
  const val MAX_SWING_PERCENT = 40
  const val DEFAULT_SAMPLE_RATE = 44_100
  private const val WAVEFORM_BAR_COUNT = 56

  // Natural minor / pentatonic rhythmic offsets (semitones above root) for melodic bass & arp lanes
  private val bassScaleOffsets = intArrayOf(0, 0, 3, 0, 5, 5, 7, 3, 0, 0, 3, 5, 7, 10, 7, 5)
  private val arpScaleOffsets = intArrayOf(0, 3, 7, 12, 7, 10, 12, 15, 0, 5, 7, 12, 10, 7, 15, 19)

  fun emptyGrid(): Map<SequencerLane, List<Boolean>> {
    return SequencerLane.entries.associateWith { List(STEP_COUNT) { false } }
  }

  /**
   * Computes exact duration in milliseconds for 16th-note [stepIndex] at [bpm] with [swingPercent].
   * Pairs `(even, odd)` maintain an exact constant eighth-note duration `30_000 / bpm` so tempo
   * stays locked across swung bars.
   */
  fun computeStepDurationMs(
    bpm: Int,
    stepIndex: Int,
    swingPercent: Int = 0,
  ): Double {
    val safeBpm = bpm.coerceIn(MIN_BPM, MAX_BPM)
    val baseStepMs = 60_000.0 / (safeBpm * 4.0)
    val swingRatio = (swingPercent.coerceIn(0, MAX_SWING_PERCENT) / 100.0) * 0.45
    return if (stepIndex % 2 == 0) {
      baseStepMs * (1.0 + swingRatio)
    } else {
      baseStepMs * (1.0 - swingRatio)
    }
  }

  /**
   * Synthesizes a single polyphonic mono voice buffer for [lane] triggered at [stepIndex]
   * using tuning parameters from [kit].
   */
  fun synthesizeLaneHitSamples(
    lane: SequencerLane,
    stepIndex: Int,
    sampleRate: Int = DEFAULT_SAMPLE_RATE,
    kit: DrumKitPreset = DrumKitPreset.STUDIO_ACOUSTIC,
  ): FloatArray {
    val safeRate = sampleRate.coerceAtLeast(8_000)
    return when (lane) {
      SequencerLane.KICK -> {
        val len = (0.28 * safeRate).toInt()
        FloatArray(len).also { out ->
          var phase = 0.0
          for (i in 0 until len) {
            val t = i.toDouble() / safeRate
            val freq = kit.kickSubHz + 128.0 * exp(-t * 32.0)
            phase += 2.0 * PI * freq / safeRate
            val env = exp(-t * kit.kickDecay) * (1.0 - exp(-t * 360.0))
            out[i] = (tanh(sin(phase) * env * kit.drive) * 0.78).toFloat()
          }
        }
      }
      SequencerLane.SNARE -> {
        val len = (0.20 * safeRate).toInt()
        val rng = Random(701 + stepIndex * 17 + kit.ordinal * 29)
        FloatArray(len).also { out ->
          var bodyPhase = 0.0
          var prevNoise = 0.0
          for (i in 0 until len) {
            val t = i.toDouble() / safeRate
            bodyPhase += 2.0 * PI * kit.snareToneHz / safeRate
            val white = rng.nextDouble() * 2.0 - 1.0
            val hpNoise = white - 0.52 * prevNoise
            prevNoise = white
            val noise = hpNoise * exp(-t * 18.0) * kit.snareCrispness
            val body = sin(bodyPhase) * exp(-t * 24.0)
            val attack = 1.0 - exp(-t * 420.0)
            out[i] = (tanh((noise * 0.34 + body * 0.25) * attack * kit.drive)).toFloat()
          }
        }
      }
      SequencerLane.CH_HAT -> {
        val len = (0.065 * safeRate).toInt()
        val rng = Random(1307 + stepIndex * 31 + kit.ordinal * 41)
        FloatArray(len).also { out ->
          var prev = 0.0
          for (i in 0 until len) {
            val t = i.toDouble() / safeRate
            val white = rng.nextDouble() * 2.0 - 1.0
            val hp = white - prev
            prev = white
            val env = exp(-t * 68.0) * (1.0 - exp(-t * 600.0)) * kit.hatBrightness
            out[i] = (hp * env * 0.26).toFloat()
          }
        }
      }
      SequencerLane.OH_HAT -> {
        val len = (0.26 * safeRate).toInt()
        val rng = Random(2903 + stepIndex * 43 + kit.ordinal * 53)
        FloatArray(len).also { out ->
          var prev1 = 0.0
          var prev2 = 0.0
          for (i in 0 until len) {
            val t = i.toDouble() / safeRate
            val white = rng.nextDouble() * 2.0 - 1.0
            val hp = white - 0.85 * prev1 + 0.25 * prev2
            prev2 = prev1
            prev1 = white
            val shimmer = 0.75 + 0.25 * sin(2.0 * PI * 320.0 * t)
            val env = exp(-t * 13.5) * (1.0 - exp(-t * 320.0)) * kit.hatBrightness
            out[i] = (hp * shimmer * env * 0.24).toFloat()
          }
        }
      }
      SequencerLane.BASS -> {
        val len = (0.28 * safeRate).toInt()
        val midi = 36 + bassScaleOffsets[stepIndex.mod(bassScaleOffsets.size)]
        val freq = 440.0 * 2.0.pow((midi - 69) / 12.0)
        FloatArray(len).also { out ->
          var phase = 0.0
          val inc = 2.0 * PI * freq / safeRate
          for (i in 0 until len) {
            val t = i.toDouble() / safeRate
            val env = exp(-t * 8.0) * (1.0 - exp(-t * 220.0))
            val sub = 0.65 * sin(phase) + 0.25 * sin(phase * 2.0) + 0.12 * sin(phase * 3.0)
            out[i] = (sub * env * 0.44).toFloat()
            phase += inc
          }
        }
      }
      SequencerLane.SYNTH -> {
        val len = (0.26 * safeRate).toInt()
        val midi = 60 + arpScaleOffsets[stepIndex.mod(arpScaleOffsets.size)]
        val freq = 440.0 * 2.0.pow((midi - 69) / 12.0)
        FloatArray(len).also { out ->
          var p1 = 0.0
          var p2 = 0.0
          val inc1 = 2.0 * PI * freq / safeRate
          val inc2 = 2.0 * PI * (freq * 1.003) / safeRate
          for (i in 0 until len) {
            val t = i.toDouble() / safeRate
            val env = exp(-t * 9.5) * (1.0 - exp(-t * 280.0))
            val harm = 0.55 * sin(p1) + 0.30 * sin(p2 * 2.0) + 0.15 * sin(p1 * 3.0)
            out[i] = (harm * env * 0.34).toFloat()
            p1 += inc1
            p2 += inc2
          }
        }
      }
    }
  }

  /**
   * Renders [bars] repetitions of [state]'s grid pattern into a LUFS-normalized [RawAudioBuffer].
   */
  fun renderPatternToRawBuffer(
    state: StepSequencerState,
    bars: Int = 4,
    sampleRate: Int = DEFAULT_SAMPLE_RATE,
    targetLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
    applyPostGain: Boolean = true,
  ): RawAudioBuffer {
    val safeRate = sampleRate.coerceAtLeast(8_000)
    val safeBars = bars.coerceIn(1, 16)
    val safeBpm = state.bpm.coerceIn(MIN_BPM, MAX_BPM)
    val totalSteps = safeBars * STEP_COUNT

    val stepStartSamples = IntArray(totalSteps + 1)
    var runningMs = 0.0
    for (s in 0 until totalSteps) {
      stepStartSamples[s] = ((runningMs / 1000.0) * safeRate).roundToInt()
      runningMs += computeStepDurationMs(safeBpm, s % STEP_COUNT, state.swingPercent)
    }
    val totalSamples = ((runningMs / 1000.0) * safeRate).roundToInt().coerceAtLeast(safeRate)
    val mix = DoubleArray(totalSamples)

    // Pre-cache synthesized voices per (lane, stepInBar) so repeated bars stay instant
    val voiceCache = HashMap<Pair<SequencerLane, Int>, FloatArray>()
    for (s in 0 until totalSteps) {
      val stepInBar = s % STEP_COUNT
      val startSample = stepStartSamples[s]
      if (startSample >= totalSamples) break
      for (lane in SequencerLane.entries) {
        if (!state.isStepActive(lane, stepInBar)) continue
        val voice = voiceCache.getOrPut(lane to stepInBar) {
          synthesizeLaneHitSamples(
            lane = lane,
            stepIndex = stepInBar,
            sampleRate = safeRate,
            kit = state.drumKit,
          )
        }
        val limit = min(voice.size, totalSamples - startSample)
        for (i in 0 until limit) {
          mix[startSample + i] += voice[i].toDouble()
        }
      }
    }

    val fadeOutSamples = min(totalSamples / 10, (0.04 * safeRate).toInt())
    val rawShorts = ShortArray(totalSamples)
    for (i in 0 until totalSamples) {
      var sample = mix[i]
      val distEnd = totalSamples - 1 - i
      if (distEnd < fadeOutSamples) {
        sample *= distEnd.toDouble() / fadeOutSamples.coerceAtLeast(1)
      }
      val limited = tanh(sample * 1.1)
      rawShorts[i] = (limited * Short.MAX_VALUE * 0.90).toInt()
        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        .toShort()
    }

    val finalSamples = if (applyPostGain) {
      LoudnessNormalizerDsp.normalizePcm(
        pcmSamples = rawShorts,
        sampleRate = safeRate,
        channelCount = 1,
        targetLufs = targetLufs,
      ).normalizedSamples
    } else {
      rawShorts
    }
    return FourierAudioBufferProcessor.analyzeRawPcm(
      pcmSamples = finalSamples,
      sampleRate = safeRate,
      channelCount = 1,
      bridgeSource = "Step Sequencer",
    )
  }

  fun extractWaveformPeaks(buffer: RawAudioBuffer): List<Float> {
    return FourierAudioBufferProcessor.extractWaveformPeaks(buffer.pcmSamples, WAVEFORM_BAR_COUNT)
  }
}

/**
 * Stateful real-time Step Sequencer controller supporting live 16-step pattern editing, real-time
 * [AudioTrack] polyphonic synthesis playback, and dynamic sync to the app's detected BPM.
 */
class StepSequencerEngine {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var playbackJob: Job? = null

  private val _state = MutableStateFlow(StepSequencerState())
  val state: StateFlow<StepSequencerState> = _state.asStateFlow()

  private val voiceCacheByKit = ConcurrentHashMap<DrumKitPreset, Map<Pair<SequencerLane, Int>, FloatArray>>()

  private fun getKitVoices(kit: DrumKitPreset): Map<Pair<SequencerLane, Int>, FloatArray> {
    return voiceCacheByKit.getOrPut(kit) {
      val map = HashMap<Pair<SequencerLane, Int>, FloatArray>(SequencerLane.entries.size * StepSequencerDsp.STEP_COUNT)
      for (lane in SequencerLane.entries) {
        for (step in 0 until StepSequencerDsp.STEP_COUNT) {
          map[lane to step] = StepSequencerDsp.synthesizeLaneHitSamples(
            lane = lane,
            stepIndex = step,
            sampleRate = StepSequencerDsp.DEFAULT_SAMPLE_RATE,
            kit = kit,
          )
        }
      }
      map
    }
  }

  fun selectDrumKit(kit: DrumKitPreset) {
    _state.update { it.copy(drumKit = kit) }
    scope.launch(Dispatchers.Default) {
      getKitVoices(kit)
    }
  }

  fun toggleStep(lane: SequencerLane, stepIndex: Int) {
    if (stepIndex !in 0 until StepSequencerDsp.STEP_COUNT) return
    _state.update { current ->
      val existingRow = current.pattern[lane] ?: List(StepSequencerDsp.STEP_COUNT) { false }
      val nextRow = existingRow.toMutableList().apply {
        this[stepIndex] = !this[stepIndex]
      }
      val nextPattern = current.pattern.toMutableMap().apply {
        this[lane] = nextRow
      }
      current.copy(
        pattern = nextPattern,
        selectedPresetName = "Custom",
      )
    }
  }

  fun clearPattern() {
    _state.update { current ->
      current.copy(
        pattern = StepSequencerDsp.emptyGrid(),
        selectedPresetName = "Blank",
      )
    }
  }

  fun applyPreset(preset: SequencerPreset) {
    _state.update { current ->
      val nextBpm = if (current.isBpmSynced && current.detectedAppBpm in StepSequencerDsp.MIN_BPM..StepSequencerDsp.MAX_BPM) {
        current.detectedAppBpm
      } else {
        preset.defaultBpm.coerceIn(StepSequencerDsp.MIN_BPM, StepSequencerDsp.MAX_BPM)
      }
      current.copy(
        pattern = preset.buildGrid(),
        bpm = nextBpm,
        selectedPresetName = preset.label,
      )
    }
  }

  fun setManualBpm(bpm: Int) {
    val clamped = bpm.coerceIn(StepSequencerDsp.MIN_BPM, StepSequencerDsp.MAX_BPM)
    _state.update { current ->
      current.copy(
        bpm = clamped,
        isBpmSynced = false,
      )
    }
  }

  fun stepManualBpm(delta: Int) {
    val target = (_state.value.bpm + delta).coerceIn(StepSequencerDsp.MIN_BPM, StepSequencerDsp.MAX_BPM)
    setManualBpm(target)
  }

  fun setBpmSynced(synced: Boolean) {
    _state.update { current ->
      val nextBpm = if (synced && current.detectedAppBpm in StepSequencerDsp.MIN_BPM..StepSequencerDsp.MAX_BPM) {
        current.detectedAppBpm
      } else {
        current.bpm
      }
      current.copy(
        isBpmSynced = synced,
        bpm = nextBpm,
      )
    }
  }

  fun toggleBpmSync() {
    setBpmSynced(!_state.value.isBpmSynced)
  }

  fun updateDetectedAppBpm(detectedBpm: Int, sourceLabel: String) {
    val validDetected = detectedBpm.takeIf { it in 55..180 }
      ?.coerceIn(StepSequencerDsp.MIN_BPM, StepSequencerDsp.MAX_BPM)
      ?: 0
    _state.update { current ->
      val nextBpm = if (current.isBpmSynced && validDetected > 0) {
        validDetected
      } else {
        current.bpm
      }
      current.copy(
        detectedAppBpm = validDetected,
        bpmSyncSourceLabel = if (validDetected > 0) sourceLabel else "",
        bpm = nextBpm,
      )
    }
  }

  fun cycleSwing() {
    _state.update { current ->
      val nextSwing = when {
        current.swingPercent < 12 -> 15
        current.swingPercent < 25 -> 30
        else -> 0
      }
      current.copy(swingPercent = nextSwing)
    }
  }

  fun setRenderingToLibrary(rendering: Boolean) {
    _state.update { it.copy(isRenderingToLibrary = rendering) }
  }

  fun restoreState(
    pattern: Map<SequencerLane, List<Boolean>>,
    bpm: Int,
    swingPercent: Int,
    isBpmSynced: Boolean,
    selectedPresetName: String,
  ) {
    stop()
    val sanitizedPattern = SequencerLane.entries.associateWith { lane ->
      val row = pattern[lane].orEmpty()
      List(StepSequencerDsp.STEP_COUNT) { idx -> row.getOrElse(idx) { false } }
    }
    _state.update { current ->
      val safeBpm = bpm.coerceIn(StepSequencerDsp.MIN_BPM, StepSequencerDsp.MAX_BPM)
      val resolvedBpm = if (isBpmSynced && current.detectedAppBpm in StepSequencerDsp.MIN_BPM..StepSequencerDsp.MAX_BPM) {
        current.detectedAppBpm
      } else {
        safeBpm
      }
      current.copy(
        pattern = sanitizedPattern,
        bpm = resolvedBpm,
        swingPercent = swingPercent.coerceIn(0, StepSequencerDsp.MAX_SWING_PERCENT),
        isBpmSynced = isBpmSynced,
        selectedPresetName = selectedPresetName.ifBlank { "Custom" },
        currentStep = -1,
      )
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
    _state.update { it.copy(isPlaying = true, currentStep = 0) }

    playbackJob = scope.launch(Dispatchers.Default) {
      val sampleRate = StepSequencerDsp.DEFAULT_SAMPLE_RATE
      val chunkFrames = 512
      val outShort = ShortArray(chunkFrames)
      val tailBufferSize = sampleRate // 1s polyphonic tail ring buffer
      val mixRing = FloatArray(tailBufferSize)
      var ringCursor = 0
      var framesUntilNextStep = 0
      var stepIndex = 0

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
        Log.w(TAG, "Sequencer AudioTrack unavailable, running silent timer fallback", e)
        null
      }

      try {
        if (audioTrack == null) {
          // Headless/emulator-fallback clock loop when AudioTrack cannot play
          while (isActive && _state.value.isPlaying) {
            val snap = _state.value
            val currentStep = stepIndex % StepSequencerDsp.STEP_COUNT
            _state.update { it.copy(currentStep = currentStep) }
            val stepMs = StepSequencerDsp.computeStepDurationMs(snap.bpm, currentStep, snap.swingPercent)
            delay(stepMs.roundToInt().toLong().coerceAtLeast(20L))
            stepIndex = (stepIndex + 1) % StepSequencerDsp.STEP_COUNT
          }
        } else {
          while (isActive && _state.value.isPlaying) {
            val snap = _state.value
            for (i in 0 until chunkFrames) {
              if (framesUntilNextStep <= 0) {
                val activeStep = stepIndex % StepSequencerDsp.STEP_COUNT
                _state.update { it.copy(currentStep = activeStep) }
                val kitCache = getKitVoices(snap.drumKit)
                for (lane in SequencerLane.entries) {
                  if (snap.isStepActive(lane, activeStep)) {
                    val voice = kitCache[lane to activeStep] ?: continue
                    val writeLen = min(voice.size, tailBufferSize)
                    for (v in 0 until writeLen) {
                      val pos = (ringCursor + v) % tailBufferSize
                      mixRing[pos] += voice[v]
                    }
                  }
                }
                val stepMs = StepSequencerDsp.computeStepDurationMs(
                  bpm = snap.bpm,
                  stepIndex = activeStep,
                  swingPercent = snap.swingPercent,
                )
                framesUntilNextStep = ((stepMs / 1000.0) * sampleRate).roundToInt().coerceAtLeast(256)
                stepIndex = (stepIndex + 1) % StepSequencerDsp.STEP_COUNT
              }

              val sampleVal = tanh(mixRing[ringCursor].toDouble() * 1.1)
              mixRing[ringCursor] = 0f
              outShort[i] = (sampleVal * Short.MAX_VALUE * 0.85).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
              ringCursor = (ringCursor + 1) % tailBufferSize
              framesUntilNextStep--
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
    _state.update { it.copy(isPlaying = false, currentStep = -1) }
  }

  companion object {
    private const val TAG = "StepSequencerEngine"
  }
}
