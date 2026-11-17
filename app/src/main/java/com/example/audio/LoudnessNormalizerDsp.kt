package com.example.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan
import kotlin.math.tanh

enum class LoudnessTarget(
  val lufs: Float,
  val label: String,
  val subtitle: String,
) {
  STREAMING_14(-14.0f, "-14 LUFS", "Streaming"),
  MOBILE_16(-16.0f, "-16 LUFS", "Balanced"),
  MASTER_18(-18.0f, "-18 LUFS", "Headroom"),
}

data class LoudnessNormalizationResult(
  val normalizedSamples: ShortArray,
  val inputLufs: Float,
  val outputLufs: Float,
  val appliedGainDb: Float,
  val truePeakDbfs: Float,
)

/**
 * ITU-R BS.1770-4 / EBU R128 K-weighted integrated loudness (LUFS) meter and
 * post-processing loudness normalizer with soft-knee true-peak limiting (-1.0 dBTP ceiling).
 */
object LoudnessNormalizerDsp {

  const val DEFAULT_TARGET_LUFS = -14.0f
  const val TRUE_PEAK_CEILING_DB = -1.0f
  private const val ABSOLUTE_GATE_LUFS = -70.0
  private const val MIN_GAIN_DB = -24.0
  private const val MAX_GAIN_DB = 24.0

  private class BiquadFilter(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
  ) {
    private var z1 = 0.0
    private var z2 = 0.0

    fun process(input: Double): Double {
      val out = b0 * input + z1
      z1 = b1 * input - a1 * out + z2
      z2 = b2 * input - a2 * out
      return out
    }
  }

  /**
   * Computes integrated gated loudness in LUFS per ITU-R BS.1770-4 using a two-stage
   * K-weighting filter (high-shelf head model + RLB high-pass) and 400 ms / 75% overlap blocks.
   */
  fun measureIntegratedLufs(
    pcmSamples: ShortArray,
    sampleRate: Int,
    channelCount: Int = 1,
  ): Float {
    val safeChannels = channelCount.coerceAtLeast(1)
    val safeRate = sampleRate.coerceAtLeast(8_000)
    val totalFrames = pcmSamples.size / safeChannels
    if (totalFrames <= 0) return -70.0f

    // Run two-stage K-weighting filter per channel
    val weighted = Array(safeChannels) { c ->
      val stage1 = createStage1HighShelf(safeRate)
      val stage2 = createStage2RlbHighPass(safeRate)
      DoubleArray(totalFrames) { f ->
        val sample = pcmSamples[f * safeChannels + c] / 32768.0
        stage2.process(stage1.process(sample))
      }
    }

    val blockFrames = max(1, (safeRate * 0.400).roundToInt())
    val hopFrames = max(1, (safeRate * 0.100).roundToInt())

    val blockEnergies = ArrayList<Double>()
    if (totalFrames <= blockFrames) {
      blockEnergies.add(computeBlockEnergy(weighted, safeChannels, 0, totalFrames))
    } else {
      var start = 0
      while (start + blockFrames <= totalFrames) {
        blockEnergies.add(computeBlockEnergy(weighted, safeChannels, start, start + blockFrames))
        start += hopFrames
      }
      if (blockEnergies.isEmpty()) {
        blockEnergies.add(computeBlockEnergy(weighted, safeChannels, 0, totalFrames))
      }
    }

    // 1. Absolute gate at -70 LUFS
    val absSurviving = blockEnergies.filter { energy ->
      energy > 1e-12 && energyToLufs(energy) > ABSOLUTE_GATE_LUFS
    }
    if (absSurviving.isEmpty()) {
      val fallbackEnergy = blockEnergies.maxOrNull()?.coerceAtLeast(1e-12) ?: 1e-12
      return energyToLufs(fallbackEnergy).toFloat().coerceIn(-70.0f, 0.0f)
    }

    // 2. Relative gate at -10 LU below absolute-gated mean loudness
    val absMeanEnergy = absSurviving.average()
    val relativeGateLufs = energyToLufs(absMeanEnergy) - 10.0
    val relSurviving = absSurviving.filter { energy ->
      energyToLufs(energy) > relativeGateLufs
    }

    val finalEnergy = if (relSurviving.isNotEmpty()) {
      relSurviving.average()
    } else {
      absMeanEnergy
    }
    return energyToLufs(finalEnergy).toFloat().coerceIn(-70.0f, 0.0f)
  }

  /**
   * Normalizes [pcmSamples] to [targetLufs] (default -14.0 LUFS) using measured ITU-R BS.1770-4
   * loudness and a transparent soft-knee limiter protecting the -1.0 dBTP ceiling.
   */
  fun normalizePcm(
    pcmSamples: ShortArray,
    sampleRate: Int,
    channelCount: Int = 1,
    targetLufs: Float = DEFAULT_TARGET_LUFS,
  ): LoudnessNormalizationResult {
    if (pcmSamples.isEmpty()) {
      return LoudnessNormalizationResult(
        normalizedSamples = ShortArray(0),
        inputLufs = -70.0f,
        outputLufs = -70.0f,
        appliedGainDb = 0f,
        truePeakDbfs = -70.0f,
      )
    }

    val inputLufs = measureIntegratedLufs(pcmSamples, sampleRate, channelCount)
    if (inputLufs <= -68.0f) {
      // Essentially silent buffer: return unmodified copy
      return LoudnessNormalizationResult(
        normalizedSamples = pcmSamples.copyOf(),
        inputLufs = inputLufs,
        outputLufs = inputLufs,
        appliedGainDb = 0f,
        truePeakDbfs = computePeakDbfs(pcmSamples),
      )
    }

    val desiredGainDb = (targetLufs - inputLufs).toDouble().coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
    val linearGain = 10.0.pow(desiredGainDb / 20.0)

    val ceiling = 10.0.pow(TRUE_PEAK_CEILING_DB / 20.0) // ~0.89125 (-1.0 dBTP)
    val kneeThreshold = ceiling * 0.84
    val kneeRange = ceiling - kneeThreshold

    val output = ShortArray(pcmSamples.size) { i ->
      val scaled = (pcmSamples[i] / 32768.0) * linearGain
      val absVal = abs(scaled)
      val limited = if (absVal <= kneeThreshold) {
        scaled
      } else {
        val excess = (absVal - kneeThreshold) / kneeRange.coerceAtLeast(1e-6)
        val compressed = kneeThreshold + kneeRange * tanh(excess)
        if (scaled >= 0.0) compressed else -compressed
      }
      (limited * 32767.0)
        .roundToInt()
        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        .toShort()
    }

    val outputLufs = measureIntegratedLufs(output, sampleRate, channelCount)
    return LoudnessNormalizationResult(
      normalizedSamples = output,
      inputLufs = inputLufs,
      outputLufs = outputLufs,
      appliedGainDb = desiredGainDb.toFloat(),
      truePeakDbfs = computePeakDbfs(output),
    )
  }

  fun computePeakDbfs(pcmSamples: ShortArray): Float {
    if (pcmSamples.isEmpty()) return -70.0f
    var maxAbs = 0
    for (s in pcmSamples) {
      val a = abs(s.toInt())
      if (a > maxAbs) maxAbs = a
    }
    if (maxAbs <= 0) return -70.0f
    return (20.0 * log10(maxAbs / 32768.0)).toFloat().coerceIn(-70.0f, 0.0f)
  }

  fun computeTruePeakDbfsFromPeakAmplitude(peakAmplitude: Float): Float {
    if (peakAmplitude <= 1e-5f) return -70.0f
    return (20.0 * log10(peakAmplitude.toDouble())).toFloat().coerceIn(-70.0f, 0.0f)
  }

  /**
   * Computes a safe real-time linear gain multiplier to match [measuredLufs] to [targetLufs]
   * without exceeding the -1.0 dBTP true-peak ceiling given the buffer's normalized [peakAmplitude].
   */
  fun computePlaybackGainLinear(
    measuredLufs: Float,
    targetLufs: Float,
    peakAmplitude: Float = 1.0f,
  ): Float {
    if (measuredLufs <= -68.0f) return 1.0f
    val desiredDb = (targetLufs - measuredLufs).toDouble().coerceIn(-12.0, 12.0)
    var linearGain = 10.0.pow(desiredDb / 20.0)
    val ceilingLinear = 10.0.pow(TRUE_PEAK_CEILING_DB / 20.0)
    if (peakAmplitude > 0.01f && linearGain > 1.0) {
      val maxSafeGain = ceilingLinear / peakAmplitude.toDouble()
      linearGain = min(linearGain, maxSafeGain.coerceAtLeast(1.0))
    }
    return linearGain.toFloat().coerceIn(0.25f, 4.0f)
  }

  fun computeGainAdjustmentDb(
    measuredLufs: Float,
    targetLufs: Float,
    peakAmplitude: Float = 1.0f,
  ): Float {
    val linear = computePlaybackGainLinear(measuredLufs, targetLufs, peakAmplitude)
    return (20.0 * log10(linear.toDouble())).toFloat()
  }

  fun formatGainTrimBadge(gainDb: Float): String {
    return when {
      gainDb >= 0.05f -> "+%.1f dB".format(gainDb)
      gainDb <= -0.05f -> "%.1f dB".format(gainDb)
      else -> "0.0 dB"
    }
  }

  private fun computeBlockEnergy(
    weighted: Array<DoubleArray>,
    channels: Int,
    startFrame: Int,
    endFrame: Int,
  ): Double {
    val length = (endFrame - startFrame).coerceAtLeast(1)
    var totalEnergy = 0.0
    for (c in 0 until channels) {
      var sumSq = 0.0
      val chData = weighted[c]
      for (f in startFrame until endFrame) {
        val v = chData[f]
        sumSq += v * v
      }
      totalEnergy += sumSq / length
    }
    return totalEnergy
  }

  private fun energyToLufs(energy: Double): Double {
    return -0.691 + 10.0 * log10(max(energy, 1e-12))
  }

  private fun createStage1HighShelf(sampleRate: Int): BiquadFilter {
    val f0 = 1681.974450955533
    val dbGain = 3.999843853973347
    val q = 0.7071752369554196
    val k = tan(PI * f0 / sampleRate)
    val vh = 10.0.pow(dbGain / 20.0)
    val vb = vh.pow(0.4996667741545416)
    val a0 = 1.0 + k / q + k * k
    return BiquadFilter(
      b0 = (vh + vb * k / q + k * k) / a0,
      b1 = 2.0 * (k * k - vh) / a0,
      b2 = (vh - vb * k / q + k * k) / a0,
      a1 = 2.0 * (k * k - 1.0) / a0,
      a2 = (1.0 - k / q + k * k) / a0,
    )
  }

  private fun createStage2RlbHighPass(sampleRate: Int): BiquadFilter {
    val f0 = 38.13547087602444
    val q = 0.5003270373238773
    val k = tan(PI * f0 / sampleRate)
    val a0 = 1.0 + k / q + k * k
    return BiquadFilter(
      b0 = 1.0 / a0,
      b1 = -2.0 / a0,
      b2 = 1.0 / a0,
      a1 = 2.0 * (k * k - 1.0) / a0,
      a2 = (1.0 - k / q + k * k) / a0,
    )
  }
}
