package com.example.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

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
}

/**
 * 8-band real-time biquad peaking graphic equalizer DSP.
 * Uses cascaded Robert Bristow-Johnson Audio EQ Cookbook peaking filters at standard ISO
 * frequencies (60 Hz .. 12 kHz) with zero-allocation Transposed Direct Form II processing.
 */
object GraphicEqualizerDsp {

  const val BAND_COUNT = 8
  const val MIN_GAIN_DB = -12f
  const val MAX_GAIN_DB = 12f
  const val DEFAULT_Q = 1.41421356

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

  fun formatGainDb(gainDb: Float): String {
    val clean = if (abs(gainDb) < 0.05f) 0f else gainDb
    return if (clean > 0f) "+%.1f".format(clean) else "%.1f".format(clean)
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
          z1[stateIdx] = b1[band] * sample - a1[stateIdx] * out + z2[stateIdx]
          z2[stateIdx] = b2[band] * sample - a2[stateIdx] * out
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
