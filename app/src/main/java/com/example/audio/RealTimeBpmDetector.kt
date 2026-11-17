package com.example.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Snapshot emitted by [RealTimeBpmDetector] while audio chunks stream through the player engine.
 */
data class RealTimeBpmAnalysisResult(
  val bpm: Int = 0,
  val confidence: Float = 0f,
  val beatPulse: Float = 0f,
  val isAnalyzing: Boolean = false,
  val hasSignal: Boolean = false,
)

/**
 * Streaming, zero-allocation-per-chunk real-time BPM detector and beat-pulse tracker.
 *
 * Processes variable-length interleaved 16-bit PCM chunks incrementally using 512-frame hops
 * (1024-frame overlapping log-energy windows), maintains a ~6.5-second sliding onset-flux ring
 * buffer, emits decayed beat-onset pulses at ~86 Hz, and computes interpolated lag autocorrelation
 * across [minBpm]..[maxBpm] with sub-harmonic confirmation and hysteresis smoothing.
 */
class RealTimeBpmDetector(
  initialSampleRate: Int = FourierAudioBufferProcessor.DEFAULT_SAMPLE_RATE,
  initialChannelCount: Int = 1,
  val minBpm: Int = 60,
  val maxBpm: Int = 176,
) {

  private var sampleRate: Int = initialSampleRate.coerceAtLeast(8_000)
  private var channelCount: Int = initialChannelCount.coerceIn(1, 2)

  private val hopSize = 512
  private val maxHistoryFrames = 560 // ~6.5 seconds at 44.1 kHz / 512 hop

  // Pre-allocated hop energy & onset ring buffers
  private val fluxRing = DoubleArray(maxHistoryFrames)
  private val orderedFlux = DoubleArray(maxHistoryFrames)
  private val orderedOnset = DoubleArray(maxHistoryFrames)
  private val trailingMeanWindow = 16

  private var ringWriteIndex = 0
  private var ringCount = 0

  // 512-frame sub-block accumulator so arbitrary chunk sizes align to exact hop boundaries
  private var accumHalfEnergy = 0.0
  private var accumHalfSamples = 0
  private var prevHalfEnergy = 0.0
  private var hasPrevHalf = false
  private var prevLogEnergy = 0.0
  private var hasPrevLogEnergy = false

  // Signal & beat pulse tracking
  private var silenceHopCount = 0
  private var totalHopsInRun = 0
  private var hopsSinceLastEstimate = 0
  private var hopsSinceLastPulse = 999
  private var currentPulse = 0f
  private var hasActiveSignal = false

  // Smoothed tempo state
  private var smoothedBpm = 0f
  private var lockedBpm = 0
  private var smoothedConfidence = 0f
  private var candidateSwitchBpm = 0
  private var candidateSwitchHits = 0

  @Synchronized
  fun reset() {
    ringWriteIndex = 0
    ringCount = 0
    accumHalfEnergy = 0.0
    accumHalfSamples = 0
    prevHalfEnergy = 0.0
    hasPrevHalf = false
    prevLogEnergy = 0.0
    hasPrevLogEnergy = false
    silenceHopCount = 0
    totalHopsInRun = 0
    hopsSinceLastEstimate = 0
    hopsSinceLastPulse = 999
    currentPulse = 0f
    hasActiveSignal = false
    smoothedBpm = 0f
    lockedBpm = 0
    smoothedConfidence = 0f
    candidateSwitchBpm = 0
    candidateSwitchHits = 0
    fluxRing.fill(0.0)
  }

  /**
   * Feeds [frameCount] interleaved PCM frames from [pcm] and returns the updated live tempo
   * estimate, confidence, and beat-pulse level.
   */
  @Synchronized
  fun processChunk(
    pcm: ShortArray,
    frameCount: Int,
    sampleRate: Int = this.sampleRate,
    channelCount: Int = this.channelCount,
  ): RealTimeBpmAnalysisResult {
    val safeRate = sampleRate.coerceAtLeast(8_000)
    val safeChannels = channelCount.coerceIn(1, 2)
    if (safeRate != this.sampleRate || safeChannels != this.channelCount) {
      this.sampleRate = safeRate
      this.channelCount = safeChannels
      reset()
    }

    val maxFrames = min(frameCount, pcm.size / safeChannels).coerceAtLeast(0)
    if (maxFrames == 0) {
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
      accumHalfEnergy += mono * mono
      accumHalfSamples++

      if (accumHalfSamples >= hopSize) {
        onHopComplete(accumHalfEnergy)
        accumHalfEnergy = 0.0
        accumHalfSamples = 0
      }
    }

    return currentSnapshot()
  }

  @Synchronized
  fun currentSnapshot(): RealTimeBpmAnalysisResult {
    val envelopeRate = sampleRate.toDouble() / hopSize
    val minAnalysisHops = ceil(1.7 * envelopeRate).toInt().coerceAtLeast(48)
    val isAnalyzing = hasActiveSignal && (ringCount < minAnalysisHops || lockedBpm == 0)
    return RealTimeBpmAnalysisResult(
      bpm = lockedBpm,
      confidence = smoothedConfidence,
      beatPulse = currentPulse,
      isAnalyzing = isAnalyzing,
      hasSignal = hasActiveSignal,
    )
  }

  private fun onHopComplete(currHalfSumSq: Double) {
    val halfRms = sqrt(currHalfSumSq / hopSize)
    val envelopeRate = sampleRate.toDouble() / hopSize
    val silenceResetHops = ceil(1.25 * envelopeRate).toInt().coerceAtLeast(40)

    if (halfRms < 0.0035) {
      silenceHopCount++
      if (silenceHopCount >= silenceResetHops) {
        hasActiveSignal = false
        currentPulse = 0f
        smoothedConfidence = (smoothedConfidence * 0.7f).let { if (it < 0.05f) 0f else it }
        if (silenceHopCount == silenceResetHops) {
          // Clear stale onset ring after prolonged silence so new rhythm starts clean
          ringWriteIndex = 0
          ringCount = 0
          lockedBpm = 0
          smoothedBpm = 0f
          smoothedConfidence = 0f
          candidateSwitchBpm = 0
          candidateSwitchHits = 0
          hasPrevLogEnergy = false
        }
      } else {
        currentPulse *= 0.84f
        if (currentPulse < 0.02f) currentPulse = 0f
      }
    } else {
      silenceHopCount = 0
      hasActiveSignal = true
    }

    val windowMeanSq = if (hasPrevHalf) {
      (prevHalfEnergy + currHalfSumSq) / (hopSize * 2)
    } else {
      currHalfSumSq / hopSize
    }
    prevHalfEnergy = currHalfSumSq
    hasPrevHalf = true

    val logEnergy = ln(1.0 + 32.0 * windowMeanSq)
    val flux = if (hasPrevLogEnergy) {
      max(0.0, logEnergy - prevLogEnergy)
    } else {
      0.0
    }
    prevLogEnergy = logEnergy
    hasPrevLogEnergy = true

    // Store raw flux into circular buffer
    fluxRing[ringWriteIndex] = flux
    ringWriteIndex = (ringWriteIndex + 1) % maxHistoryFrames
    if (ringCount < maxHistoryFrames) {
      ringCount++
    }
    totalHopsInRun++
    hopsSinceLastEstimate++
    hopsSinceLastPulse++

    // Decay and update real-time beat pulse indicator
    currentPulse = (currentPulse * 0.85f).let { if (it < 0.02f) 0f else it }
    if (hasActiveSignal) {
      updateBeatPulse(flux, envelopeRate)
    }

    val minAnalysisHops = ceil(1.7 * envelopeRate).toInt().coerceAtLeast(48)
    val estimateIntervalHops = max(12, (envelopeRate * 0.22).roundToInt())
    if (hasActiveSignal && ringCount >= minAnalysisHops && hopsSinceLastEstimate >= estimateIntervalHops) {
      hopsSinceLastEstimate = 0
      estimateTempoFromRing(envelopeRate)
    }
  }

  private fun updateBeatPulse(currentFlux: Double, envelopeRate: Double) {
    val window = min(ringCount, trailingMeanWindow)
    if (window < 4) return

    var sum = 0.0
    for (i in 0 until window) {
      val idx = (ringWriteIndex - 1 - i + maxHistoryFrames) % maxHistoryFrames
      sum += fluxRing[idx]
    }
    val mean = sum / window

    var varSum = 0.0
    for (i in 0 until window) {
      val idx = (ringWriteIndex - 1 - i + maxHistoryFrames) % maxHistoryFrames
      val diff = fluxRing[idx] - mean
      varSum += diff * diff
    }
    val stdDev = sqrt(varSum / window)
    val threshold = mean + 1.05 * stdDev
    val minDebounceHops = max(6, ((60.0 / (maxBpm * 1.4)) * envelopeRate).roundToInt())

    if (currentFlux > 0.015 && currentFlux > threshold && hopsSinceLastPulse >= minDebounceHops) {
      val ratio = (currentFlux / max(mean * 2.8, 0.12)).coerceIn(0.68, 1.0)
      currentPulse = max(currentPulse, ratio.toFloat())
      hopsSinceLastPulse = 0
    }
  }

  private fun estimateTempoFromRing(envelopeRate: Double) {
    val len = ringCount
    if (len < 48) return

    // Unroll circular buffer into contiguous chronological order in zero-alloc scratch arrays
    val startIdx = if (ringCount < maxHistoryFrames) 0 else ringWriteIndex
    for (i in 0 until len) {
      orderedFlux[i] = fluxRing[(startIdx + i) % maxHistoryFrames]
    }

    var onsetSum = 0.0
    val maRadius = 6
    for (m in 0 until len) {
      val lo = max(0, m - maRadius)
      val hi = min(len - 1, m + maRadius)
      var localSum = 0.0
      for (k in lo..hi) {
        localSum += orderedFlux[k]
      }
      val localMean = localSum / (hi - lo + 1)
      val valPositive = max(0.0, orderedFlux[m] - localMean)
      orderedOnset[m] = valPositive
      onsetSum += valPositive
    }

    if (onsetSum <= 1e-6) {
      return
    }

    fun autocorrAtLag(lagExact: Double): Double {
      val lagLow = floor(lagExact).toInt()
      val lagHigh = lagLow + 1
      val frac = lagExact - lagLow
      if (lagLow < 1 || lagHigh >= len - 2) return 0.0

      fun discreteCorr(lag: Int): Double {
        val overlap = len - lag
        if (overlap <= 4) return 0.0
        var sum = 0.0
        for (t in 0 until overlap) {
          sum += orderedOnset[t] * orderedOnset[t + lag]
        }
        return sum / overlap
      }

      val cLow = discreteCorr(lagLow)
      val cHigh = discreteCorr(lagHigh)
      return cLow * (1.0 - frac) + cHigh * frac
    }

    var bestBpm = 0
    var bestScore = -1.0
    var scoreSum = 0.0
    var candidateCount = 0

    val useStabilityBias = lockedBpm in minBpm..maxBpm && smoothedConfidence >= 0.30f

    for (bpm in minBpm..maxBpm) {
      val lag = (60.0 / bpm) * envelopeRate
      if (lag >= len * 0.65) continue
      val rPrimary = autocorrAtLag(lag)
      val rSubHarmonic = if (lag * 2.0 < len * 0.80) autocorrAtLag(lag * 2.0) else 0.0
      val tempoPrior = 1.0 - 0.06 * ((bpm - 115.0) / 65.0).pow(2.0)
      val stabilityBias = if (useStabilityBias) {
        val delta = (bpm - smoothedBpm) / 3.5
        1.0 + 0.04 * exp(-delta * delta)
      } else {
        1.0
      }
      val score = (rPrimary + 0.35 * rSubHarmonic) * tempoPrior * stabilityBias
      scoreSum += score
      candidateCount++
      if (score > bestScore) {
        bestScore = score
        bestBpm = bpm
      }
    }

    if (candidateCount == 0 || bestScore <= 1e-9 || bestBpm <= 0) {
      return
    }

    val meanScore = scoreSum / candidateCount
    val rawConfidence = ((bestScore - meanScore) / bestScore).toFloat().coerceIn(0f, 1f)
    if (rawConfidence < 0.14f && lockedBpm == 0) {
      return
    }

    // Smoothing & hysteresis: blend gentle jitter (±4 BPM), confirm major shifts over 2 windows
    if (lockedBpm == 0) {
      smoothedBpm = bestBpm.toFloat()
      lockedBpm = bestBpm
      smoothedConfidence = rawConfidence
      candidateSwitchBpm = 0
      candidateSwitchHits = 0
    } else if (abs(bestBpm - lockedBpm) <= 4) {
      smoothedBpm = smoothedBpm * 0.55f + bestBpm * 0.45f
      lockedBpm = smoothedBpm.roundToInt().coerceIn(minBpm, maxBpm)
      smoothedConfidence = smoothedConfidence * 0.5f + rawConfidence * 0.5f
      candidateSwitchBpm = 0
      candidateSwitchHits = 0
    } else {
      if (abs(bestBpm - candidateSwitchBpm) <= 4) {
        candidateSwitchHits++
      } else {
        candidateSwitchBpm = bestBpm
        candidateSwitchHits = 1
      }
      if (candidateSwitchHits >= 2 || rawConfidence >= 0.72f) {
        smoothedBpm = bestBpm.toFloat()
        lockedBpm = bestBpm
        smoothedConfidence = rawConfidence
        candidateSwitchBpm = 0
        candidateSwitchHits = 0
      }
    }
  }
}
