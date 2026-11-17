package com.example.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Real-time time-invariant Pitch Shifting DSP & musical key transposition helpers.
 *
 * Uses a multi-channel circular ring buffer with dual Hann-crossfaded fractional delay
 * read heads and cubic Hermite interpolation so pitch shifts cleanly across `-12..+12`
 * semitones during live playback without altering tempo/BPM or introducing zipper clicks.
 */
object PitchShiftDsp {

  const val MIN_SEMITONES = -12f
  const val MAX_SEMITONES = 12f
  const val DEFAULT_GRAIN_FRAMES = 2048

  private val CHROMATIC_SHARPS = listOf(
    "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B",
  )

  private val NOTE_TO_INDEX = mapOf(
    "C" to 0,
    "B#" to 0,
    "C#" to 1,
    "DB" to 1,
    "D" to 2,
    "D#" to 3,
    "EB" to 3,
    "E" to 4,
    "FB" to 4,
    "F" to 5,
    "E#" to 5,
    "F#" to 6,
    "GB" to 6,
    "G" to 7,
    "G#" to 8,
    "AB" to 8,
    "A" to 9,
    "A#" to 10,
    "BB" to 10,
    "B" to 11,
    "CB" to 11,
  )

  fun clampSemitones(semitones: Float): Float {
    return semitones.coerceIn(MIN_SEMITONES, MAX_SEMITONES)
  }

  fun semitonesToPitchFactor(semitones: Float): Double {
    val clamped = clampSemitones(semitones).toDouble()
    return 2.0.pow(clamped / 12.0)
  }

  fun formatSemitoneBadge(semitones: Float): String {
    val rounded = semitones.roundToInt()
    val isWhole = abs(semitones - rounded) < 0.05f
    if (abs(semitones) < 0.05f) return "0 st"
    return if (isWhole) {
      if (rounded > 0) "+$rounded st" else "$rounded st"
    } else {
      val formatted = "%.1f".format(semitones)
      if (semitones > 0f) "+$formatted st" else "$formatted st"
    }
  }

  /**
   * Computes the transposed musical key label when shifting [baseKey] by [semitones].
   * Supports keys like `"C Min"`, `"F# Maj"`, `"Am"`, `"Db Minor"`, or non-tonal format tags
   * like `"WAV"` / `"MP3"`.
   */
  fun transposeKeyLabel(baseKey: String, semitones: Float): String {
    val steps = semitones.roundToInt()
    if (steps == 0) return baseKey
    val trimmed = baseKey.trim()
    val match = Regex("""^([A-Ga-g][#b]?)(.*)$""").matchEntire(trimmed)
    if (match != null) {
      val rootRaw = match.groupValues[1].uppercase()
      val suffix = match.groupValues[2]
      val rootIndex = NOTE_TO_INDEX[rootRaw]
      if (rootIndex != null) {
        val shiftedIndex = ((rootIndex + steps) % 12 + 12) % 12
        return "${CHROMATIC_SHARPS[shiftedIndex]}$suffix"
      }
    }
    val sign = if (steps > 0) "+$steps" else "$steps"
    return "$baseKey ($sign st)"
  }

  /**
   * Convenience offline processor useful for verification and DSP diagnostics.
   * Preserves exact sample rate, channel count, and frame duration while shifting pitch.
   */
  fun processPcmOffline(
    pcmSamples: ShortArray,
    sampleRate: Int,
    channelCount: Int,
    semitones: Float,
    grainFrames: Int = DEFAULT_GRAIN_FRAMES,
  ): ShortArray {
    if (pcmSamples.isEmpty()) return pcmSamples
    val safeChannels = channelCount.coerceAtLeast(1)
    val totalFrames = pcmSamples.size / safeChannels
    val shifter = RealTimePitchShifter(
      sampleRate = sampleRate,
      channelCount = safeChannels,
      grainFrames = grainFrames,
    )
    val output = ShortArray(pcmSamples.size)
    // Pre-prime half a grain from initial samples or zeros cleanly, then process block-by-block
    val blockFrames = 512
    val inBlock = ShortArray(blockFrames * safeChannels)
    val outBlock = ShortArray(blockFrames * safeChannels)
    var frameOffset = 0
    while (frameOffset < totalFrames) {
      val count = minOf(blockFrames, totalFrames - frameOffset)
      val sampleStart = frameOffset * safeChannels
      pcmSamples.copyInto(
        destination = inBlock,
        destinationOffset = 0,
        startIndex = sampleStart,
        endIndex = sampleStart + count * safeChannels,
      )
      shifter.processBlock(
        input = inBlock,
        frameCount = count,
        targetSemitones = semitones,
        output = outBlock,
        volumeGain = 1f,
      )
      outBlock.copyInto(
        destination = output,
        destinationOffset = sampleStart,
        startIndex = 0,
        endIndex = count * safeChannels,
      )
      frameOffset += count
    }
    return output
  }
}

/**
 * Stateful, allocation-free-per-block real-time pitch shifting DSP processor.
 *
 * Thread-safe when driven sequentially from the audio streaming thread; call [reset]
 * after seeking or track transitions.
 */
class RealTimePitchShifter(
  sampleRate: Int,
  channelCount: Int,
  grainFrames: Int = PitchShiftDsp.DEFAULT_GRAIN_FRAMES,
) {
  private val safeChannels = channelCount.coerceIn(1, 2)
  private val windowFrames = grainFrames.coerceIn(512, maxOf(512, sampleRate / 10))
  private val ringSize = windowFrames * 4
  private val ringBuffers = Array(safeChannels) { FloatArray(ringSize) }
  private var writeCursor = 0
  private var phase1 = 0.25
  private var smoothedPitchFactor = 1.0
  private var factorInitialized = false

  fun reset() {
    for (c in 0 until safeChannels) {
      ringBuffers[c].fill(0f)
    }
    writeCursor = 0
    phase1 = 0.25
    factorInitialized = false
  }

  /**
   * Processes [frameCount] interleaved PCM frames from [input] into [output] applying
   * real-time pitch shifting ([targetSemitones]) and gain ([volumeGain]).
   */
  fun processBlock(
    input: ShortArray,
    frameCount: Int,
    targetSemitones: Float,
    output: ShortArray,
    volumeGain: Float = 1f,
  ) {
    if (frameCount <= 0) return

    val targetFactor = PitchShiftDsp.semitonesToPitchFactor(targetSemitones)
    if (!factorInitialized) {
      smoothedPitchFactor = targetFactor
      factorInitialized = true
    }

    val isDryBypass = abs(targetSemitones) < 0.05f && abs(smoothedPitchFactor - 1.0) < 0.001
    val safeGain = volumeGain.coerceIn(0f, 1.5f)
    val invRing = ringSize
    val wFrames = windowFrames.toDouble()

    for (f in 0 until frameCount) {
      // Smooth pitch ratio per sample to prevent zipper noise while dragging slider live
      smoothedPitchFactor += (targetFactor - smoothedPitchFactor) * 0.0035
      val activeFactor = if (abs(targetSemitones) < 0.05f && abs(smoothedPitchFactor - 1.0) < 0.001) {
        smoothedPitchFactor = 1.0
        1.0
      } else {
        smoothedPitchFactor
      }

      val baseIdx = f * safeChannels
      // Always feed ring buffer so toggling pitch on mid-song is continuous and glitch-free
      for (c in 0 until safeChannels) {
        ringBuffers[c][writeCursor] = input[baseIdx + c].toFloat()
      }

      // Advance dual-tap normalized phases (0.5 cycle apart for constant-sum Hann crossfade)
      val deltaPhase = (1.0 - activeFactor) / wFrames
      phase1 = wrapUnit(phase1 + deltaPhase)
      val phase2 = wrapUnit(phase1 + 0.5)

      if (isDryBypass && abs(activeFactor - 1.0) < 0.0005) {
        for (c in 0 until safeChannels) {
          val dry = ringBuffers[c][writeCursor] * safeGain
          output[baseIdx + c] = dry.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
      } else {
        val delay1 = phase1 * wFrames
        val delay2 = phase2 * wFrames
        // Complementary Hann windows: w1 + w2 == 1.0 everywhere
        val w1 = 0.5 * (1.0 - cos(2.0 * PI * phase1))
        val w2 = 1.0 - w1

        for (c in 0 until safeChannels) {
          val buf = ringBuffers[c]
          val sample1 = readHermite(buf, writeCursor, delay1, invRing)
          val sample2 = readHermite(buf, writeCursor, delay2, invRing)
          val mixed = (sample1 * w1 + sample2 * w2) * safeGain
          output[baseIdx + c] = mixed.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
      }

      writeCursor = (writeCursor + 1) % invRing
    }
  }

  private fun wrapUnit(value: Double): Double {
    var v = value % 1.0
    if (v < 0.0) v += 1.0
    return v
  }

  /**
   * 4-point cubic Hermite interpolation inside the circular ring buffer at `writeIdx - delaySamples`.
   */
  private fun readHermite(
    buf: FloatArray,
    writeIdx: Int,
    delaySamples: Double,
    size: Int,
  ): Double {
    val exactRead = writeIdx - delaySamples
    val base = kotlin.math.floor(exactRead)
    val frac = exactRead - base
    val i1 = ((base.toInt() % size) + size) % size
    val i0 = (i1 - 1 + size) % size
    val i2 = (i1 + 1) % size
    val i3 = (i1 + 2) % size

    val y0 = buf[i0].toDouble()
    val y1 = buf[i1].toDouble()
    val y2 = buf[i2].toDouble()
    val y3 = buf[i3].toDouble()

    val c0 = y1
    val c1 = 0.5 * (y2 - y0)
    val c2 = y0 - 2.5 * y1 + 2.0 * y2 - 0.5 * y3
    val c3 = 0.5 * (y3 - y0) + 1.5 * (y1 - y2)
    return ((c3 * frac + c2) * frac + c1) * frac + c0
  }
}
