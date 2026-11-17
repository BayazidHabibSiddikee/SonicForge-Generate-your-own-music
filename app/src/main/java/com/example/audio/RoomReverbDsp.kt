package com.example.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.tanh

enum class ReverbEngineMode(val label: String) {
  CONVOLUTION("Convolution IR"),
  SCHROEDER("Algorithmic"),
}

enum class ReverbPreset(
  val label: String,
  val wetMix: Float,
  val roomSize: Float,
  val damping: Float,
) {
  OFF("Off", 0f, 0.35f, 0.50f),
  CHAMBER("Room", 0.30f, 0.50f, 0.48f),
  STUDIO("Studio", 0.44f, 0.68f, 0.38f),
  HALL("Hall", 0.60f, 0.84f, 0.28f),
  CATHEDRAL("Cathedral", 0.75f, 0.93f, 0.18f),
}

data class ReverbSettings(
  val preset: ReverbPreset = ReverbPreset.OFF,
  val wetMix: Float = 0f,
  val roomSize: Float = 0.55f,
  val damping: Float = 0.40f,
  val engineMode: ReverbEngineMode = ReverbEngineMode.CONVOLUTION,
) {
  val isActive: Boolean
    get() = wetMix > 0.005f

  val estimatedRt60Seconds: Float
    get() = ConvolutionReverbDsp.estimateRt60Seconds(roomSize)

  fun withPreset(target: ReverbPreset): ReverbSettings = ReverbSettings(
    preset = target,
    wetMix = target.wetMix,
    roomSize = target.roomSize,
    damping = target.damping,
    engineMode = engineMode,
  )
}

/**
 * Stereo Schroeder / Freeverb-style Room Reverb DSP processor.
 *
 * Uses 8 parallel low-pass damped comb filters, 4 series all-pass diffusers, early reflection
 * taps, and stereo decorrelation spread scaled cleanly to any host sample rate.
 */
object RoomReverbDsp {

  private val COMB_TUNINGS_44K = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
  private val ALLPASS_TUNINGS_44K = intArrayOf(556, 441, 341, 225)
  private const val STEREO_SPREAD_SAMPLES_44K = 23

  fun combLengthsForRate(sampleRate: Int, channelOffset: Int): IntArray {
    val ratio = sampleRate.coerceAtLeast(8_000) / 44_100.0
    val spread = (STEREO_SPREAD_SAMPLES_44K * ratio).roundToInt() * channelOffset
    return IntArray(COMB_TUNINGS_44K.size) { i ->
      max(32, (COMB_TUNINGS_44K[i] * ratio).roundToInt() + spread)
    }
  }

  fun allpassLengthsForRate(sampleRate: Int, channelOffset: Int): IntArray {
    val ratio = sampleRate.coerceAtLeast(8_000) / 44_100.0
    val spread = (STEREO_SPREAD_SAMPLES_44K * ratio).roundToInt() * channelOffset
    return IntArray(ALLPASS_TUNINGS_44K.size) { i ->
      max(16, (ALLPASS_TUNINGS_44K[i] * ratio).roundToInt() + spread)
    }
  }

  fun formatWetPercent(wetMix: Float): String {
    val pct = (wetMix.coerceIn(0f, 1f) * 100f).roundToInt()
    return "$pct%"
  }

  /**
   * Offline renderer used for saving a reverberated track copy or running deterministic
   * DSP verification tests. Optionally appends [tailDurationMs] of silence so the reverb tail
   * rings out cleanly at the end of the track.
   */
  fun processPcmOffline(
    pcmSamples: ShortArray,
    sampleRate: Int,
    channelCount: Int,
    settings: ReverbSettings,
    tailDurationMs: Long = 0L,
  ): ShortArray {
    if (pcmSamples.isEmpty()) return pcmSamples
    val safeChannels = channelCount.coerceIn(1, 2)
    val baseFrames = pcmSamples.size / safeChannels
    val extraTailFrames = if (settings.isActive && tailDurationMs > 0L) {
      ((tailDurationMs / 1000.0) * sampleRate).toInt().coerceAtLeast(0)
    } else {
      0
    }
    val totalFrames = baseFrames + extraTailFrames
    val output = ShortArray(totalFrames * safeChannels)

    val reverb = RealTimeRoomReverb(sampleRate = sampleRate, channelCount = safeChannels)
    val blockFrames = 512
    val inBlock = ShortArray(blockFrames * safeChannels)
    val outBlock = ShortArray(blockFrames * safeChannels)

    var frameOffset = 0
    while (frameOffset < totalFrames) {
      val count = min(blockFrames, totalFrames - frameOffset)
      inBlock.fill(0)
      if (frameOffset < baseFrames) {
        val dryFrames = min(count, baseFrames - frameOffset)
        val startSample = frameOffset * safeChannels
        pcmSamples.copyInto(
          destination = inBlock,
          destinationOffset = 0,
          startIndex = startSample,
          endIndex = startSample + dryFrames * safeChannels,
        )
      }
      reverb.processBlock(
        input = inBlock,
        frameCount = count,
        settings = settings,
        output = outBlock,
        volumeGain = 1f,
      )
      outBlock.copyInto(
        destination = output,
        destinationOffset = frameOffset * safeChannels,
        startIndex = 0,
        endIndex = count * safeChannels,
      )
      frameOffset += count
    }
    return output
  }
}

private class LowpassCombFilter(size: Int) {
  private val buffer = FloatArray(size)
  private var index = 0
  private var filterStore = 0f

  fun reset() {
    buffer.fill(0f)
    index = 0
    filterStore = 0f
  }

  fun process(input: Float, feedback: Float, damp1: Float, damp2: Float): Float {
    val output = buffer[index]
    filterStore = output * damp2 + filterStore * damp1
    // Anti-denormal guard + write feedback into delay buffer
    val next = input + filterStore * feedback
    buffer[index] = if (abs(next) < 1e-18f) 0f else next
    index++
    if (index >= buffer.size) index = 0
    return output
  }
}

private class AllpassFilter(size: Int) {
  private val buffer = FloatArray(size)
  private var index = 0

  fun reset() {
    buffer.fill(0f)
    index = 0
  }

  fun process(input: Float): Float {
    val bufOut = buffer[index]
    val output = -input + bufOut
    val next = input + bufOut * 0.5f
    buffer[index] = if (abs(next) < 1e-18f) 0f else next
    index++
    if (index >= buffer.size) index = 0
    return output
  }
}

/**
 * Stateful real-time stereo Room Reverb DSP node driven sequentially on the streaming thread.
 */
class RealTimeRoomReverb(
  sampleRate: Int,
  channelCount: Int,
) {
  private val safeChannels = channelCount.coerceIn(1, 2)
  private val safeRate = sampleRate.coerceAtLeast(8_000)

  private val combsL = RoomReverbDsp.combLengthsForRate(safeRate, 0).map { LowpassCombFilter(it) }
  private val combsR = RoomReverbDsp.combLengthsForRate(safeRate, 1).map { LowpassCombFilter(it) }
  private val allpassesL = RoomReverbDsp.allpassLengthsForRate(safeRate, 0).map { AllpassFilter(it) }
  private val allpassesR = RoomReverbDsp.allpassLengthsForRate(safeRate, 1).map { AllpassFilter(it) }

  // Short early-reflection taps (~11 ms and ~19 ms) for room intimacy
  private val earlyDelaySamples1 = max(16, (safeRate * 0.011).roundToInt())
  private val earlyDelaySamples2 = max(32, (safeRate * 0.019).roundToInt())
  private val earlyRingL = FloatArray(earlyDelaySamples2 + 4)
  private val earlyRingR = FloatArray(earlyDelaySamples2 + 4)
  private var earlyCursor = 0

  private var smoothedWet = 0f
  private var smoothedRoom = 0.55f
  private var smoothedDamp = 0.40f
  private var initialized = false
  private val convolutionEngine = RealTimeConvolutionReverb(sampleRate = safeRate, channelCount = safeChannels)

  fun reset() {
    combsL.forEach { it.reset() }
    combsR.forEach { it.reset() }
    allpassesL.forEach { it.reset() }
    allpassesR.forEach { it.reset() }
    earlyRingL.fill(0f)
    earlyRingR.fill(0f)
    earlyCursor = 0
    initialized = false
    convolutionEngine.reset()
  }

  fun processBlock(
    input: ShortArray,
    frameCount: Int,
    settings: ReverbSettings,
    output: ShortArray,
    volumeGain: Float = 1f,
  ) {
    if (frameCount <= 0) return

    if (settings.engineMode == ReverbEngineMode.CONVOLUTION) {
      convolutionEngine.processBlock(
        input = input,
        frameCount = frameCount,
        settings = settings,
        output = output,
        volumeGain = volumeGain,
      )
      return
    }

    val targetWet = settings.wetMix.coerceIn(0f, 1f)
    val targetRoom = settings.roomSize.coerceIn(0.05f, 0.98f)
    val targetDamp = settings.damping.coerceIn(0.05f, 0.95f)
    if (!initialized) {
      smoothedWet = targetWet
      smoothedRoom = targetRoom
      smoothedDamp = targetDamp
      initialized = true
    }

    val safeGain = volumeGain.coerceIn(0f, 1.5f)
    val bypass = targetWet <= 0.005f && smoothedWet <= 0.002f
    if (bypass) {
      smoothedWet = 0f
      for (f in 0 until frameCount) {
        val base = f * safeChannels
        for (c in 0 until safeChannels) {
          val dry = input[base + c] * safeGain
          output[base + c] = dry.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
      }
      return
    }

    val earlyLen = earlyRingL.size
    for (f in 0 until frameCount) {
      smoothedWet += (targetWet - smoothedWet) * 0.004f
      smoothedRoom += (targetRoom - smoothedRoom) * 0.002f
      smoothedDamp += (targetDamp - smoothedDamp) * 0.002f

      val feedback = 0.68f + smoothedRoom * 0.27f // 0.69 .. 0.945
      val damp1 = smoothedDamp * 0.52f
      val damp2 = 1f - damp1

      val wetGain = smoothedWet * 0.85f
      val dryGain = 1f - (smoothedWet * 0.32f)

      val base = f * safeChannels
      val dryL = input[base] / 32768f
      val dryR = if (safeChannels >= 2) input[base + 1] / 32768f else dryL

      // Early reflections bus
      earlyRingL[earlyCursor] = dryL
      earlyRingR[earlyCursor] = dryR
      val tap1Idx = (earlyCursor - earlyDelaySamples1 + earlyLen) % earlyLen
      val tap2Idx = (earlyCursor - earlyDelaySamples2 + earlyLen) % earlyLen
      val earlyL = earlyRingL[tap1Idx] * 0.45f + earlyRingR[tap2Idx] * 0.30f
      val earlyR = earlyRingR[tap1Idx] * 0.45f + earlyRingL[tap2Idx] * 0.30f
      earlyCursor = (earlyCursor + 1) % earlyLen

      val feedMono = (dryL + dryR) * 0.022f

      var accL = 0f
      for (i in combsL.indices) {
        accL += combsL[i].process(feedMono, feedback, damp1, damp2)
      }
      var accR = 0f
      for (i in combsR.indices) {
        accR += combsR[i].process(feedMono, feedback, damp1, damp2)
      }

      for (i in allpassesL.indices) {
        accL = allpassesL[i].process(accL)
      }
      for (i in allpassesR.indices) {
        accR = allpassesR[i].process(accR)
      }

      // Blend early reflections with diffused late reverb & stereo width cross-feed
      val spatialL = accL * 0.85f + accR * 0.15f + earlyL * 0.35f
      val spatialR = accR * 0.85f + accL * 0.15f + earlyR * 0.35f

      if (safeChannels >= 2) {
        val mixedL = (dryL * dryGain + spatialL * wetGain) * safeGain
        val mixedR = (dryR * dryGain + spatialR * wetGain) * safeGain
        output[base] = softLimitToShort(mixedL)
        output[base + 1] = softLimitToShort(mixedR)
      } else {
        val spatialMono = (spatialL + spatialR) * 0.5f
        val mixed = (dryL * dryGain + spatialMono * wetGain) * safeGain
        output[base] = softLimitToShort(mixed)
      }
    }
  }

  private fun softLimitToShort(normalizedSample: Float): Short {
    val limited = if (abs(normalizedSample) > 0.92f) {
      tanh(normalizedSample.toDouble()).toFloat()
    } else {
      normalizedSample
    }
    return (limited * 32767f).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
  }
}
