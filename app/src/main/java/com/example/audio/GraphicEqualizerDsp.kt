package com.example.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class ThreeBandEqBand(
  val id: String,
  val label: String,
  val frequencyRangeLabel: String,
  val centerHz: Int,
  val bandIndices: IntRange,
) {
  LOW(
    id = "low",
    label = "Low",
    frequencyRangeLabel = "60–310 Hz",
    centerHz = 120,
    bandIndices = 0..2,
  ),
  MID(
    id = "mid",
    label = "Mid",
    frequencyRangeLabel = "600 Hz–3 kHz",
    centerHz = 1000,
    bandIndices = 3..5,
  ),
  HIGH(
    id = "high",
    label = "High",
    frequencyRangeLabel = "6–12 kHz",
    centerHz = 8000,
    bandIndices = 6..7,
  ),
}

enum class EqPreset(
  val label: String,
  val defaultBandsDb: List<Float>,
) {
  FLAT(
    label = "Flat",
    defaultBandsDb = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
  ),
  BASS_BOOST(
    label = "Bass Boost",
    defaultBandsDb = listOf(7.5f, 6.0f, 3.5f, 1.0f, 0f, 0f, 0f, 0f),
  ),
  TREBLE_BOOST(
    label = "Treble Boost",
    defaultBandsDb = listOf(0f, 0f, 0f, 0.5f, 1.5f, 4.0f, 6.5f, 7.5f),
  ),
  VOCAL_ENHANCER(
    label = "Vocal Enhancer",
    defaultBandsDb = listOf(-2.0f, -1.5f, 1.0f, 3.5f, 5.5f, 5.0f, 2.5f, 0.5f),
  ),
  V_SHAPE(
    label = "V-Shape",
    defaultBandsDb = listOf(6.0f, 4.0f, 0.5f, -2.0f, -2.0f, 1.5f, 4.5f, 6.0f),
  ),
  CUSTOM(
    label = "Custom",
    defaultBandsDb = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
  );

  companion object {
    val selectablePresets: List<EqPreset> = listOf(
      FLAT,
      BASS_BOOST,
      TREBLE_BOOST,
      VOCAL_ENHANCER,
      V_SHAPE,
    )
  }
}

data class EqualizerSettings(
  val isEnabled: Boolean = false,
  val preset: EqPreset = EqPreset.FLAT,
  val bandGainsDb: List<Float> = EqPreset.FLAT.defaultBandsDb,
) {
  val normalizedBands: List<Float>
    get() = List(GraphicEqualizerDsp.BAND_COUNT) { idx ->
      bandGainsDb.getOrElse(idx) { 0f }
        .coerceIn(GraphicEqualizerDsp.MIN_GAIN_DB, GraphicEqualizerDsp.MAX_GAIN_DB)
    }

  val isActive: Boolean
    get() = isEnabled && normalizedBands.any { abs(it) >= 0.1f }

  val lowGainDb: Float
    get() = threeBandGain(ThreeBandEqBand.LOW)

  val midGainDb: Float
    get() = threeBandGain(ThreeBandEqBand.MID)

  val highGainDb: Float
    get() = threeBandGain(ThreeBandEqBand.HIGH)

  fun threeBandGain(band: ThreeBandEqBand): Float {
    val bands = normalizedBands
    var sum = 0f
    var count = 0
    for (idx in band.bandIndices) {
      sum += bands[idx]
      count++
    }
    if (count == 0) return 0f
    val avg = sum / count.toFloat()
    return ((avg * 10f).roundToInt() / 10f)
      .coerceIn(GraphicEqualizerDsp.MIN_GAIN_DB, GraphicEqualizerDsp.MAX_GAIN_DB)
  }

  fun withPreset(targetPreset: EqPreset): EqualizerSettings {
    if (targetPreset == EqPreset.FLAT) {
      return EqualizerSettings(
        isEnabled = false,
        preset = EqPreset.FLAT,
        bandGainsDb = EqPreset.FLAT.defaultBandsDb,
      )
    }
    return EqualizerSettings(
      isEnabled = true,
      preset = targetPreset,
      bandGainsDb = targetPreset.defaultBandsDb.toList(),
    )
  }

  fun withBandGain(bandIndex: Int, gainDb: Float): EqualizerSettings {
    if (bandIndex !in 0 until GraphicEqualizerDsp.BAND_COUNT) return this
    val updated = normalizedBands.toMutableList()
    val snapped = (gainDb * 2f).roundToInt() / 2f
    updated[bandIndex] = snapped.coerceIn(GraphicEqualizerDsp.MIN_GAIN_DB, GraphicEqualizerDsp.MAX_GAIN_DB)

    val matchedPreset = EqPreset.selectablePresets.firstOrNull { candidate ->
      candidate.defaultBandsDb.indices.all { i -> abs(candidate.defaultBandsDb[i] - updated[i]) < 0.15f }
    } ?: EqPreset.CUSTOM

    val hasBoostOrCut = updated.any { abs(it) >= 0.1f }
    return EqualizerSettings(
      isEnabled = hasBoostOrCut || isEnabled,
      preset = matchedPreset,
      bandGainsDb = updated,
    )
  }

  fun withThreeBandGain(band: ThreeBandEqBand, gainDb: Float): EqualizerSettings {
    val updated = normalizedBands.toMutableList()
    val snapped = ((gainDb * 2f).roundToInt() / 2f)
      .coerceIn(GraphicEqualizerDsp.MIN_GAIN_DB, GraphicEqualizerDsp.MAX_GAIN_DB)
    for (idx in band.bandIndices) {
      updated[idx] = snapped
    }

    val matchedPreset = EqPreset.selectablePresets.firstOrNull { candidate ->
      candidate.defaultBandsDb.indices.all { i -> abs(candidate.defaultBandsDb[i] - updated[i]) < 0.15f }
    } ?: EqPreset.CUSTOM

    val hasBoostOrCut = updated.any { abs(it) >= 0.1f }
    return EqualizerSettings(
      isEnabled = hasBoostOrCut || isEnabled,
      preset = matchedPreset,
      bandGainsDb = updated,
    )
  }

  fun withThreeBandGains(lowDb: Float, midDb: Float, highDb: Float): EqualizerSettings {
    return this
      .withThreeBandGain(ThreeBandEqBand.LOW, lowDb)
      .withThreeBandGain(ThreeBandEqBand.MID, midDb)
      .withThreeBandGain(ThreeBandEqBand.HIGH, highDb)
  }
}

/**
 * Real-time biquad peaking graphic equalizer DSP supporting both 3-band (Low, Mid, High)
 * and 8-band ISO (60 Hz .. 12 kHz) controls.
 * Uses cascaded Robert Bristow-Johnson Audio EQ Cookbook peaking filters with zero-allocation
 * Transposed Direct Form II processing.
 */
object GraphicEqualizerDsp {

  const val BAND_COUNT = 8
  const val MIN_GAIN_DB = -12f
  const val MAX_GAIN_DB = 12f
  const val DEFAULT_Q = 1.41421356

  private const val CURVE_MIN_HZ = 30.0
  private const val CURVE_MAX_HZ = 16_000.0
  private const val CURVE_REF_SAMPLE_RATE = 44_100.0

  val CENTER_FREQUENCIES_HZ: IntArray = intArrayOf(
    60, 170, 310, 600, 1000, 3000, 6000, 12000,
  )

  val BAND_LABELS: List<String> = listOf(
    "60", "170", "310", "600", "1K", "3K", "6K", "12K",
  )

  fun formatSummaryBadge(settings: EqualizerSettings): String {
    if (!settings.isEnabled) return "Bypass"
    val nonZero = settings.normalizedBands.count { abs(it) >= 0.1f }
    return if (settings.preset == EqPreset.FLAT && nonZero == 0) {
      "Flat (0 dB)"
    } else {
      settings.preset.label
    }
  }

  fun formatThreeBandSummary(settings: EqualizerSettings): String {
    if (!settings.isEnabled) return "Bypass"
    if (!settings.isActive) return "Flat (0 dB)"
    if (settings.preset != EqPreset.CUSTOM && settings.preset != EqPreset.FLAT) {
      return settings.preset.label
    }
    val low = formatGainDb(settings.lowGainDb)
    val mid = formatGainDb(settings.midGainDb)
    val high = formatGainDb(settings.highGainDb)
    return "L $low · M $mid · H $high dB"
  }

  fun formatGainDb(gainDb: Float): String {
    val clean = if (abs(gainDb) < 0.05f) 0f else gainDb
    return if (clean > 0f) "+%.1f".format(clean) else "%.1f".format(clean)
  }

  /**
   * Computes normalized [-1f, +1f] frequency-response curve samples across log-spaced frequencies
   * from 30 Hz to 16 kHz by evaluating the exact cascaded biquad magnitude response |H(e^jw)|.
   */
  fun computeFrequencyCurvePoints(
    settings: EqualizerSettings,
    samplePoints: Int = 48,
  ): List<Float> {
    val count = samplePoints.coerceAtLeast(8)
    if (!settings.isActive) {
      return List(count) { 0f }
    }

    val bands = settings.normalizedBands
    val b0 = DoubleArray(BAND_COUNT) { 1.0 }
    val b1 = DoubleArray(BAND_COUNT)
    val b2 = DoubleArray(BAND_COUNT)
    val a1 = DoubleArray(BAND_COUNT)
    val a2 = DoubleArray(BAND_COUNT)
    val active = BooleanArray(BAND_COUNT)

    for (band in 0 until BAND_COUNT) {
      val gainDb = bands[band]
      if (abs(gainDb) < 0.08f) continue
      val fc = CENTER_FREQUENCIES_HZ[band].toDouble()
      val aAmp = 10.0.pow(gainDb / 40.0)
      val w0 = 2.0 * PI * fc / CURVE_REF_SAMPLE_RATE
      val cosW0 = cos(w0)
      val sinW0 = sin(w0)
      val alpha = sinW0 / (2.0 * DEFAULT_Q)

      val rawA0 = max(1e-6, 1.0 + alpha / aAmp)
      b0[band] = (1.0 + alpha * aAmp) / rawA0
      b1[band] = (-2.0 * cosW0) / rawA0
      b2[band] = (1.0 - alpha * aAmp) / rawA0
      a1[band] = (-2.0 * cosW0) / rawA0
      a2[band] = (1.0 - alpha / aAmp) / rawA0
      active[band] = true
    }

    val logMin = ln(CURVE_MIN_HZ)
    val logSpan = ln(CURVE_MAX_HZ) - logMin
    return List(count) { idx ->
      val t = idx.toDouble() / (count - 1).toDouble()
      val freqHz = kotlin.math.exp(logMin + logSpan * t)
      val w = 2.0 * PI * freqHz / CURVE_REF_SAMPLE_RATE
      val cosW = cos(w)
      val sinW = sin(w)
      val cos2W = cos(2.0 * w)
      val sin2W = sin(2.0 * w)

      var totalMag = 1.0
      for (band in 0 until BAND_COUNT) {
        if (!active[band]) continue
        val numRe = b0[band] + b1[band] * cosW + b2[band] * cos2W
        val numIm = -(b1[band] * sinW + b2[band] * sin2W)
        val denRe = 1.0 + a1[band] * cosW + a2[band] * cos2W
        val denIm = -(a1[band] * sinW + a2[band] * sin2W)
        val numMag = sqrt(numRe * numRe + numIm * numIm)
        val denMag = max(1e-9, sqrt(denRe * denRe + denIm * denIm))
        totalMag *= (numMag / denMag)
      }
      val db = 20.0 * log10(max(1e-6, totalMag))
      (db / MAX_GAIN_DB.toDouble()).toFloat().coerceIn(-1f, 1f)
    }
  }

  /**
   * Maps a frequency in Hz to normalized horizontal [0f, 1f] position matching
   * [computeFrequencyCurvePoints].
   */
  fun frequencyToCurveX(freqHz: Int): Float {
    val clamped = freqHz.toDouble().coerceIn(CURVE_MIN_HZ, CURVE_MAX_HZ)
    val logMin = ln(CURVE_MIN_HZ)
    val logSpan = ln(CURVE_MAX_HZ) - logMin
    return ((ln(clamped) - logMin) / logSpan).toFloat().coerceIn(0f, 1f)
  }

  fun processPcmOffline(
    pcmSamples: ShortArray,
    sampleRate: Int,
    channelCount: Int,
    settings: EqualizerSettings,
  ): ShortArray {
    if (!settings.isActive || pcmSamples.isEmpty()) return pcmSamples.copyOf()
    val channels = channelCount.coerceIn(1, 2)
    val totalFrames = pcmSamples.size / channels
    if (totalFrames <= 0) return pcmSamples.copyOf()

    val eq = RealTimeGraphicEqualizer(sampleRate = sampleRate, channelCount = channels)
    val output = ShortArray(pcmSamples.size)
    val blockSize = 512
    val inChunk = ShortArray(blockSize * channels)
    val outChunk = ShortArray(blockSize * channels)

    var frameOffset = 0
    while (frameOffset < totalFrames) {
      val frames = minOf(blockSize, totalFrames - frameOffset)
      val sampleCount = frames * channels
      pcmSamples.copyInto(
        destination = inChunk,
        destinationOffset = 0,
        startIndex = frameOffset * channels,
        endIndex = frameOffset * channels + sampleCount,
      )
      eq.processBlock(
        input = inChunk,
        frameCount = frames,
        settings = settings,
        output = outChunk,
      )
      outChunk.copyInto(
        destination = output,
        destinationOffset = frameOffset * channels,
        startIndex = 0,
        endIndex = sampleCount,
      )
      frameOffset += frames
    }
    return output
  }
}

class RealTimeGraphicEqualizer(
  sampleRate: Int,
  channelCount: Int,
) {
  private val safeRate = sampleRate.coerceIn(8_000, 192_000)
  private val channels = channelCount.coerceIn(1, 2)

  // Transposed Direct Form II state: [band * channels + ch]
  private val z1 = DoubleArray(GraphicEqualizerDsp.BAND_COUNT * channels)
  private val z2 = DoubleArray(GraphicEqualizerDsp.BAND_COUNT * channels)

  // Normalized biquad coefficients per band
  private val b0 = DoubleArray(GraphicEqualizerDsp.BAND_COUNT) { 1.0 }
  private val b1 = DoubleArray(GraphicEqualizerDsp.BAND_COUNT)
  private val b2 = DoubleArray(GraphicEqualizerDsp.BAND_COUNT)
  private val a1 = DoubleArray(GraphicEqualizerDsp.BAND_COUNT)
  private val a2 = DoubleArray(GraphicEqualizerDsp.BAND_COUNT)
  private val bandActive = BooleanArray(GraphicEqualizerDsp.BAND_COUNT)
  private val cachedGains = FloatArray(GraphicEqualizerDsp.BAND_COUNT) { Float.NaN }

  fun reset() {
    z1.fill(0.0)
    z2.fill(0.0)
  }

  private fun updateCoefficientsIfNeeded(gains: List<Float>) {
    val nyquistCap = safeRate * 0.45
    for (band in 0 until GraphicEqualizerDsp.BAND_COUNT) {
      val gainDb = gains[band]
      if (abs(cachedGains[band] - gainDb) < 0.01f) continue
      cachedGains[band] = gainDb

      if (abs(gainDb) < 0.08f) {
        bandActive[band] = false
        b0[band] = 1.0
        b1[band] = 0.0
        b2[band] = 0.0
        a1[band] = 0.0
        a2[band] = 0.0
        continue
      }

      val fc = minOf(GraphicEqualizerDsp.CENTER_FREQUENCIES_HZ[band].toDouble(), nyquistCap)
        .coerceAtLeast(20.0)
      val aAmp = 10.0.pow(gainDb / 40.0)
      val w0 = 2.0 * PI * fc / safeRate.toDouble()
      val cosW0 = cos(w0)
      val sinW0 = sin(w0)
      val alpha = sinW0 / (2.0 * GraphicEqualizerDsp.DEFAULT_Q)

      val rawB0 = 1.0 + alpha * aAmp
      val rawB1 = -2.0 * cosW0
      val rawB2 = 1.0 - alpha * aAmp
      val rawA0 = max(1e-6, 1.0 + alpha / aAmp)
      val rawA1 = -2.0 * cosW0
      val rawA2 = 1.0 - alpha / aAmp

      b0[band] = rawB0 / rawA0
      b1[band] = rawB1 / rawA0
      b2[band] = rawB2 / rawA0
      a1[band] = rawA1 / rawA0
      a2[band] = rawA2 / rawA0
      bandActive[band] = true
    }
  }

  fun processBlock(
    input: ShortArray,
    frameCount: Int,
    settings: EqualizerSettings,
    output: ShortArray,
    volumeGain: Float = 1f,
  ) {
    val safeFrames = minOf(frameCount, input.size / channels, output.size / channels)
    if (safeFrames <= 0) return

    if (!settings.isActive) {
      val totalSamples = safeFrames * channels
      if (abs(volumeGain - 1f) < 0.001f) {
        input.copyInto(output, 0, 0, totalSamples)
      } else {
        for (i in 0 until totalSamples) {
          output[i] = (input[i] * volumeGain)
            .roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
        }
      }
      return
    }

    updateCoefficientsIfNeeded(settings.normalizedBands)

    for (f in 0 until safeFrames) {
      val base = f * channels
      for (c in 0 until channels) {
        var sample = input[base + c] / 32768.0
        for (band in 0 until GraphicEqualizerDsp.BAND_COUNT) {
          if (!bandActive[band]) continue
          val stateIdx = band * channels + c
          val out = b0[band] * sample + z1[stateIdx]
          z1[stateIdx] = b1[band] * sample - a1[band] * out + z2[stateIdx]
          z2[stateIdx] = b2[band] * sample - a2[band] * out
          sample = out
        }
        val scaled = (sample * 32768.0 * volumeGain)
          .roundToInt()
          .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
          .toShort()
        output[base + c] = scaled
      }
    }
  }
}
