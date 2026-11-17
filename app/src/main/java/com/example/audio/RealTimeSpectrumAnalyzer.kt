package com.example.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class SpectrumVisualizerMode(val label: String) {
  BARS("Bars"),
  CURVE("Curve"),
}

data class RealTimeSpectrumSnapshot(
  val bandLevels: List<Float> = List(RealTimeSpectrumAnalyzer.BAND_COUNT) { 0f },
  val peakHoldLevels: List<Float> = List(RealTimeSpectrumAnalyzer.BAND_COUNT) { 0f },
  val bandCenterHz: List<Int> = RealTimeSpectrumAnalyzer.DEFAULT_BAND_CENTERS_HZ,
  val lowEnergy: Float = 0f,
  val midEnergy: Float = 0f,
  val highEnergy: Float = 0f,
  val spectralCentroidHz: Int = 0,
  val peakFrequencyHz: Int = 0,
  val isActive: Boolean = false,
)

/**
 * Streaming 1024-point Hann-windowed Radix-2 FFT spectrum analyzer that computes real-time
 * energy distribution across 20 logarithmically spaced frequency bands (40 Hz – 16 kHz)
 * with smooth attack/release ballistics, peak-hold caps, and Low/Mid/High energy telemetry.
 */
class RealTimeSpectrumAnalyzer(
  initialSampleRate: Int = FourierAudioBufferProcessor.DEFAULT_SAMPLE_RATE,
  initialChannelCount: Int = 1,
) {

  private var sampleRate: Int = initialSampleRate.coerceIn(8_000, 192_000)
  private var channelCount: Int = initialChannelCount.coerceIn(1, 2)

  private val ringBuffer = DoubleArray(FFT_SIZE)
  private var ringWritePos = 0
  private var ringFilled = 0
  private var samplesSinceFft = 0

  private val hannWindow = DoubleArray(FFT_SIZE) { i ->
    0.5 * (1.0 - cos((2.0 * PI * i) / (FFT_SIZE - 1)))
  }
  private val fftReal = DoubleArray(FFT_SIZE)
  private val fftImag = DoubleArray(FFT_SIZE)

  private val bandBinStart = IntArray(BAND_COUNT)
  private val bandBinEnd = IntArray(BAND_COUNT)
  private val bandCenters = IntArray(BAND_COUNT)

  private val smoothedLevels = FloatArray(BAND_COUNT)
  private val peakHoldLevels = FloatArray(BAND_COUNT)
  private val peakHoldCounters = IntArray(BAND_COUNT)

  private var lastCentroidHz = 0
  private var lastPeakFreqHz = 0
  private var hasActiveSignal = false

  init {
    recomputeBandBins()
  }

  @Synchronized
  fun reset() {
    ringBuffer.fill(0.0)
    ringWritePos = 0
    ringFilled = 0
    samplesSinceFft = 0
    smoothedLevels.fill(0f)
    peakHoldLevels.fill(0f)
    peakHoldCounters.fill(0)
    lastCentroidHz = 0
    lastPeakFreqHz = 0
    hasActiveSignal = false
  }

  @Synchronized
  fun processChunk(
    pcm: ShortArray,
    frameCount: Int,
    sampleRate: Int = this.sampleRate,
    channelCount: Int = this.channelCount,
  ): RealTimeSpectrumSnapshot {
    val safeRate = sampleRate.coerceIn(8_000, 192_000)
    val safeChannels = channelCount.coerceIn(1, 2)
    if (safeRate != this.sampleRate || safeChannels != this.channelCount) {
      this.sampleRate = safeRate
      this.channelCount = safeChannels
      recomputeBandBins()
      ringBuffer.fill(0.0)
      ringWritePos = 0
      ringFilled = 0
      samplesSinceFft = 0
    }

    val maxFrames = min(frameCount, pcm.size / safeChannels).coerceAtLeast(0)
    if (maxFrames <= 0) {
      return currentSnapshot()
    }

    for (f in 0 until maxFrames) {
      val base = f * safeChannels
      var mono = 0.0
      for (c in 0 until safeChannels) {
        mono += pcm[base + c] / 32768.0
      }
      if (safeChannels > 1) {
        mono /= safeChannels
      }

      ringBuffer[ringWritePos] = mono
      ringWritePos = (ringWritePos + 1) % FFT_SIZE
      if (ringFilled < FFT_SIZE) {
        ringFilled++
      }
      samplesSinceFft++

      if (ringFilled >= FFT_SIZE && samplesSinceFft >= HOP_SIZE) {
        samplesSinceFft = 0
        analyzeCurrentWindow()
      }
    }

    // If a short chunk didn't cross HOP_SIZE yet once full, still ensure latest state is returned;
    // or if just starting (< FFT_SIZE) and we already received enough frames to preview (e.g. >= 512),
    // zero-pad cleanly so tests/short bursts get immediate spectrum readout.
    if (ringFilled in 512 until FFT_SIZE && samplesSinceFft >= 512) {
      samplesSinceFft = 0
      analyzePartialWindow(ringFilled)
    }

    return currentSnapshot()
  }

  @Synchronized
  fun currentSnapshot(): RealTimeSpectrumSnapshot {
    val low = averageBandRange(0, 6)
    val mid = averageBandRange(6, 14)
    val high = averageBandRange(14, BAND_COUNT)
    return RealTimeSpectrumSnapshot(
      bandLevels = smoothedLevels.toList(),
      peakHoldLevels = peakHoldLevels.toList(),
      bandCenterHz = bandCenters.toList(),
      lowEnergy = low,
      midEnergy = mid,
      highEnergy = high,
      spectralCentroidHz = lastCentroidHz,
      peakFrequencyHz = lastPeakFreqHz,
      isActive = hasActiveSignal,
    )
  }

  private fun averageBandRange(startInclusive: Int, endExclusive: Int): Float {
    val start = startInclusive.coerceIn(0, BAND_COUNT - 1)
    val end = endExclusive.coerceIn(start + 1, BAND_COUNT)
    var sum = 0f
    for (b in start until end) {
      sum += smoothedLevels[b]
    }
    return (sum / (end - start)).coerceIn(0f, 1f)
  }

  private fun recomputeBandBins() {
    val halfN = FFT_SIZE / 2
    val nyquist = sampleRate / 2.0
    val maxHz = min(MAX_FREQ_HZ, nyquist).coerceAtLeast(MIN_FREQ_HZ * 2.0)

    for (b in 0 until BAND_COUNT) {
      val fStart = MIN_FREQ_HZ * (maxHz / MIN_FREQ_HZ).pow(b.toDouble() / BAND_COUNT)
      val fEnd = MIN_FREQ_HZ * (maxHz / MIN_FREQ_HZ).pow((b + 1).toDouble() / BAND_COUNT)
      val fCenter = sqrt(fStart * fEnd)
      bandCenters[b] = fCenter.roundToInt()

      val rawStart = floor((fStart * FFT_SIZE) / sampleRate).toInt().coerceIn(1, halfN - 1)
      val rawEnd = floor((fEnd * FFT_SIZE) / sampleRate).toInt().coerceIn(rawStart, halfN - 1)
      val centerBin = ((fCenter * FFT_SIZE) / sampleRate).roundToInt().coerceIn(1, halfN - 1)

      bandBinStart[b] = min(rawStart, centerBin)
      bandBinEnd[b] = max(rawEnd, centerBin)
    }
  }

  private fun analyzeCurrentWindow() {
    val startIdx = ringWritePos // oldest sample in full circular buffer
    var sumSq = 0.0
    for (i in 0 until FFT_SIZE) {
      val sample = ringBuffer[(startIdx + i) % FFT_SIZE]
      sumSq += sample * sample
      fftReal[i] = sample * hannWindow[i]
      fftImag[i] = 0.0
    }
    evaluateFftWindow(sqrt(sumSq / FFT_SIZE))
  }

  private fun analyzePartialWindow(validCount: Int) {
    var sumSq = 0.0
    for (i in 0 until FFT_SIZE) {
      val sample = if (i < validCount) ringBuffer[i] else 0.0
      sumSq += sample * sample
      fftReal[i] = sample * hannWindow[i]
      fftImag[i] = 0.0
    }
    evaluateFftWindow(sqrt(sumSq / max(1, validCount)))
  }

  private fun evaluateFftWindow(windowRms: Double) {
    if (windowRms < 1e-4) {
      // Quiet / muted frame: smoothly decay all bands toward zero
      var anyResidual = false
      for (b in 0 until BAND_COUNT) {
        smoothedLevels[b] *= RELEASE_DECAY
        if (smoothedLevels[b] < 0.01f) {
          smoothedLevels[b] = 0f
        } else {
          anyResidual = true
        }
        updatePeakHold(b, smoothedLevels[b])
      }
      hasActiveSignal = anyResidual
      if (!anyResidual) {
        lastCentroidHz = 0
        lastPeakFreqHz = 0
      }
      return
    }

    FourierAudioBufferProcessor.fftInPlace(fftReal, fftImag)

    val halfN = FFT_SIZE / 2
    val coherentNorm = FFT_SIZE * 0.25 // Hann window coherent amplitude normalization

    var weightedFreqSum = 0.0
    var magSum = 0.0
    var maxBinMag = 0.0
    var maxBinIndex = 1

    for (k in 1 until halfN) {
      val mag = sqrt(fftReal[k] * fftReal[k] + fftImag[k] * fftImag[k])
      val freq = (k.toDouble() * sampleRate) / FFT_SIZE
      weightedFreqSum += freq * mag
      magSum += mag
      if (mag > maxBinMag) {
        maxBinMag = mag
        maxBinIndex = k
      }
    }

    lastCentroidHz = if (magSum > 1e-9) (weightedFreqSum / magSum).roundToInt() else 0
    lastPeakFreqHz = ((maxBinIndex.toDouble() * sampleRate) / FFT_SIZE).roundToInt()

    var anyEnergy = false
    for (b in 0 until BAND_COUNT) {
      val binFrom = bandBinStart[b]
      val binTo = bandBinEnd[b]
      var bandSumSq = 0.0
      for (k in binFrom..binTo) {
        val normMag = sqrt(fftReal[k] * fftReal[k] + fftImag[k] * fftImag[k]) / coherentNorm
        bandSumSq += normMag * normMag
      }
      val bandRms = sqrt(bandSumSq)
      val db = 20.0 * log10(max(bandRms, 1e-6))
      val targetLevel = ((db - FLOOR_DB) / -FLOOR_DB).toFloat().coerceIn(0f, 1f)

      val prev = smoothedLevels[b]
      val next = if (targetLevel > prev) {
        prev + (targetLevel - prev) * ATTACK_COEFF
      } else {
        prev * RELEASE_DECAY + targetLevel * (1f - RELEASE_DECAY)
      }.let { if (it < 0.008f) 0f else it.coerceIn(0f, 1f) }

      smoothedLevels[b] = next
      if (next > 0.01f) anyEnergy = true
      updatePeakHold(b, next)
    }

    hasActiveSignal = anyEnergy
  }

  private fun updatePeakHold(band: Int, currentLevel: Float) {
    if (currentLevel >= peakHoldLevels[band]) {
      peakHoldLevels[band] = currentLevel
      peakHoldCounters[band] = PEAK_HOLD_FRAMES
    } else if (peakHoldCounters[band] > 0) {
      peakHoldCounters[band]--
    } else {
      peakHoldLevels[band] = max(currentLevel, peakHoldLevels[band] - PEAK_DROP_STEP)
        .let { if (it < 0.008f) 0f else it }
    }
  }

  companion object {
    const val BAND_COUNT = 20
    const val FFT_SIZE = 1024
    const val HOP_SIZE = 512
    const val MIN_FREQ_HZ = 40.0
    const val MAX_FREQ_HZ = 16_000.0
    private const val FLOOR_DB = -60.0
    private const val ATTACK_COEFF = 0.70f
    private const val RELEASE_DECAY = 0.76f
    private const val PEAK_HOLD_FRAMES = 14
    private const val PEAK_DROP_STEP = 0.028f

    val DEFAULT_BAND_CENTERS_HZ: List<Int> = List(BAND_COUNT) { b ->
      val fStart = MIN_FREQ_HZ * (MAX_FREQ_HZ / MIN_FREQ_HZ).pow(b.toDouble() / BAND_COUNT)
      val fEnd = MIN_FREQ_HZ * (MAX_FREQ_HZ / MIN_FREQ_HZ).pow((b + 1).toDouble() / BAND_COUNT)
      sqrt(fStart * fEnd).roundToInt()
    }

    fun resampleStaticBins(
      staticBins: List<Float>,
      targetCount: Int = BAND_COUNT,
    ): List<Float> {
      if (staticBins.isEmpty() || targetCount <= 0) {
        return List(targetCount.coerceAtLeast(0)) { 0f }
      }
      if (staticBins.size == targetCount) {
        return staticBins.map { it.coerceIn(0f, 1f) }
      }
      return List(targetCount) { idx ->
        val pos = (idx.toFloat() / (targetCount - 1).coerceAtLeast(1)) * (staticBins.size - 1)
        val lo = floor(pos.toDouble()).toInt().coerceIn(0, staticBins.lastIndex)
        val hi = (lo + 1).coerceIn(0, staticBins.lastIndex)
        val frac = pos - lo
        val value = staticBins[lo] * (1f - frac) + staticBins[hi] * frac
        value.coerceIn(0f, 1f)
      }
    }

    fun downsampleToBins(
      levels: List<Float>,
      targetCount: Int = 12,
    ): List<Float> {
      if (levels.isEmpty() || targetCount <= 0) {
        return List(targetCount.coerceAtLeast(0)) { 0f }
      }
      return List(targetCount) { idx ->
        val start = (idx * levels.size) / targetCount
        val end = max(start + 1, ((idx + 1) * levels.size) / targetCount).coerceAtMost(levels.size)
        var peak = 0f
        for (i in start until end) {
          if (levels[i] > peak) peak = levels[i]
        }
        peak.coerceIn(0f, 1f)
      }
    }

    fun formatFreqHz(hz: Int): String {
      if (hz <= 0) return "-- Hz"
      return if (hz >= 1_000) {
        "%.1f kHz".format(hz / 1000.0)
      } else {
        "$hz Hz"
      }
    }
  }
}
