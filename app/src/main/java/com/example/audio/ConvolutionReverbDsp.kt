package com.example.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Selectable architectural room size profiles for the convolution reverb engine.
 */
enum class ReverbRoomSizeOption(
  val label: String,
  val roomSize: Float,
) {
  SMALL("Small", 0.28f),
  MEDIUM("Medium", 0.54f),
  LARGE("Large", 0.78f),
  CATHEDRAL("Grand", 0.94f),
}

/**
 * Selectable high-frequency surface absorption / dampening presets for the convolution reverb.
 */
enum class ReverbDampingOption(
  val label: String,
  val damping: Float,
) {
  BRIGHT("Bright", 0.14f),
  BALANCED("Balanced", 0.40f),
  WARM("Warm", 0.66f),
  DARK("Damped", 0.88f),
}

data class ConvolutionImpulseResponse(
  val left: FloatArray,
  val right: FloatArray,
  val sampleRate: Int,
  val rt60Seconds: Float,
  val earlyReflectionMs: Int,
) {
  val frameCount: Int
    get() = min(left.size, right.size)
}

/**
 * Acoustic Impulse Response (IR) synthesizer and uniformly partitioned Overlap-Add FFT
 * convolution engine.
 *
 * Generates stereo room impulse responses with prime-spaced early reflections and progressive
 * low-pass surface dampening controlled by [ReverbSettings.roomSize] and [ReverbSettings.damping],
 * then convolves audio blocks via 1024-point Radix-2 FFT ([FourierAudioBufferProcessor.fftInPlace]).
 */
object ConvolutionReverbDsp {

  const val BLOCK_SIZE = 512
  const val FFT_SIZE = 1024
  private const val MAX_IR_SECONDS = 1.45
  private const val MIN_IR_SECONDS = 0.16

  fun estimateRt60Seconds(roomSize: Float): Float {
    val safeRoom = roomSize.coerceIn(0.05f, 0.98f)
    return (MIN_IR_SECONDS + (MAX_IR_SECONDS - MIN_IR_SECONDS) * (safeRoom * safeRoom * 0.55f + safeRoom * 0.45f)).toFloat()
  }

  fun formatRt60(roomSize: Float): String {
    val rt60 = estimateRt60Seconds(roomSize)
    return "%.2fs".format(rt60)
  }

  /**
   * Computes a normalized 24-point visual decay envelope + dampening profile for UI previewing
   * of the active convolution impulse response.
   */
  fun sampleImpulseEnvelopePreview(
    roomSize: Float,
    damping: Float,
    points: Int = 24,
  ): List<Float> {
    if (points <= 0) return emptyList()
    val safeRoom = roomSize.coerceIn(0.05f, 0.98f)
    val safeDamp = damping.coerceIn(0.05f, 0.95f)
    val decayRate = 2.6f + (1f - safeRoom) * 5.2f + safeDamp * 1.4f
    return List(points) { idx ->
      val t = idx.toFloat() / (points - 1).coerceAtLeast(1)
      val earlySpike = if (idx == 0) 1f else if (idx == 1) 0.78f else 0f
      val tail = exp((-decayRate * t).toDouble()).toFloat() * (0.65f + safeRoom * 0.35f)
      max(earlySpike, tail).coerceIn(0.03f, 1f)
    }
  }

  /**
   * Synthesizes a stereo acoustic Impulse Response [ConvolutionImpulseResponse] scaled to
   * [sampleRate], [roomSize] (0.05..0.98), and [damping] (0.05..0.95).
   */
  fun generateStereoImpulseResponse(
    sampleRate: Int,
    roomSize: Float,
    damping: Float,
    maxFrames: Int = (sampleRate.coerceAtLeast(8_000) * MAX_IR_SECONDS).roundToInt(),
  ): ConvolutionImpulseResponse {
    val safeRate = sampleRate.coerceAtLeast(8_000)
    val safeRoom = roomSize.coerceIn(0.05f, 0.98f)
    val safeDamp = damping.coerceIn(0.05f, 0.95f)
    val rt60Sec = estimateRt60Seconds(safeRoom)
    val irFrames = (rt60Sec * safeRate)
      .roundToInt()
      .coerceIn(BLOCK_SIZE, max(BLOCK_SIZE, maxFrames))

    val irL = FloatArray(irFrames)
    val irR = FloatArray(irFrames)

    // 1. Discrete spatial early reflection taps scaled with room dimensions
    val tapDelaysMs = floatArrayOf(3.7f, 6.9f, 11.3f, 15.8f, 21.4f, 27.9f, 34.6f, 43.2f, 51.5f)
    val roomDelayScale = 0.45f + safeRoom * 0.95f
    val erHfAtten = 1f - safeDamp * 0.35f
    for (tIdx in tapDelaysMs.indices) {
      val delayFrames = ((tapDelaysMs[tIdx] * roomDelayScale / 1000.0) * safeRate).roundToInt()
      if (delayFrames in 1 until irFrames) {
        val baseAmp = (0.58f * exp((-0.18 * tIdx).toDouble()).toFloat()) * erHfAtten
        val isLeftPrimary = tIdx % 2 == 0
        val sign = if (tIdx % 3 == 0) -1f else 1f
        irL[delayFrames] += sign * baseAmp * if (isLeftPrimary) 1.0f else 0.52f
        val rDelay = (delayFrames + 7 + tIdx * 3).coerceAtMost(irFrames - 1)
        irR[rDelay] += -sign * baseAmp * if (isLeftPrimary) 0.52f else 1.0f
      }
    }

    // 2. Deterministic PRNG stereo velvet/Gaussian diffuse tail with progressive HF dampening
    var seedL = 0x5A17_93D1L
    var seedR = 0x2F4C_8B19L
    val earlyEndFrame = ((0.048f * roomDelayScale) * safeRate).roundToInt().coerceIn(64, irFrames - 1)
    val decayConstant = -6.9077553 / irFrames.toDouble() // ln(0.001) = -60 dB at IR end

    val rawTailL = FloatArray(irFrames)
    val rawTailR = FloatArray(irFrames)

    for (n in 1 until irFrames) {
      seedL = (seedL * 6364136223846793005L + 1442695040888963407L)
      seedR = (seedR * 6364136223846793005L + 1442695040888963407L)
      val whiteL = (((seedL ushr 33).toInt() / 2147483647.0) * 2.0 - 1.0)
      val whiteR = (((seedR ushr 33).toInt() / 2147483647.0) * 2.0 - 1.0)

      val attackFade = min(1.0, n.toDouble() / max(32.0, earlyEndFrame * 0.35))
      val env = exp(decayConstant * n) * attackFade
      rawTailL[n] = (whiteL * env * 0.34).toFloat()
      rawTailR[n] = (whiteR * env * 0.34).toFloat()
    }

    // 3. Normalize broadband reference IR energy (early reflections + raw tail) so small/large rooms balance evenly
    var sumSq = 0.0
    for (n in 0 until irFrames) {
      val sL = irL[n] + rawTailL[n]
      val sR = irR[n] + rawTailR[n]
      sumSq += (sL * sL + sR * sR) * 0.5
    }
    val rms = sqrt(max(1e-12, sumSq))
    val targetTotalGain = 0.62 + safeRoom * 0.22
    val normScale = min(2.4, targetTotalGain / rms).toFloat()

    // 4. Apply progressive unity-DC-gain HF dampening lowpass over time so damping absorbs highs cleanly
    // without distorting low-frequency room warmth.
    val baseCutoffAlpha = (0.05 + (1.0 - safeDamp) * 0.90).coerceIn(0.04, 0.96)
    var lpStateL = 0.0
    var lpStateR = 0.0

    for (n in 0 until irFrames) {
      val progress = n.toDouble() / irFrames.toDouble()
      val progressiveAlpha = (baseCutoffAlpha * (1.0 - 0.62 * safeDamp * progress)).coerceIn(0.03, 0.96)
      lpStateL += progressiveAlpha * (rawTailL[n].toDouble() - lpStateL)
      lpStateR += progressiveAlpha * (rawTailR[n].toDouble() - lpStateR)

      irL[n] = (irL[n] + lpStateL.toFloat()) * normScale
      irR[n] = (irR[n] + lpStateR.toFloat()) * normScale
    }

    return ConvolutionImpulseResponse(
      left = irL,
      right = irR,
      sampleRate = safeRate,
      rt60Seconds = rt60Sec,
      earlyReflectionMs = (48f * roomDelayScale).roundToInt(),
    )
  }
}

/**
 * Stateful real-time Uniformly Partitioned Overlap-Add (UPOLS) Stereo Convolution Reverb node.
 * Zero steady-state allocations on the audio streaming thread; updates partitioned IR FFTs cleanly
 * whenever [ReverbSettings.roomSize] or [ReverbSettings.damping] changes.
 */
class RealTimeConvolutionReverb(
  sampleRate: Int,
  channelCount: Int,
) {
  private val safeChannels = channelCount.coerceIn(1, 2)
  private val safeRate = sampleRate.coerceAtLeast(8_000)
  private val maxIrFrames = ((safeRate * 1.45).roundToInt())
    .coerceAtLeast(ConvolutionReverbDsp.BLOCK_SIZE)
  private val maxPartitions = ((maxIrFrames + ConvolutionReverbDsp.BLOCK_SIZE - 1) / ConvolutionReverbDsp.BLOCK_SIZE)
    .coerceIn(1, 140)

  private val fftSize = ConvolutionReverbDsp.FFT_SIZE
  private val blockSize = ConvolutionReverbDsp.BLOCK_SIZE

  // Pre-allocated partitioned IR frequency spectra
  private val irPartRealL = Array(maxPartitions) { DoubleArray(fftSize) }
  private val irPartImagL = Array(maxPartitions) { DoubleArray(fftSize) }
  private val irPartRealR = Array(maxPartitions) { DoubleArray(fftSize) }
  private val irPartImagR = Array(maxPartitions) { DoubleArray(fftSize) }
  private var activePartitions = 0

  // Frequency-domain Delay Line (FDL) of past input block FFTs
  private val fdlReal = Array(maxPartitions) { DoubleArray(fftSize) }
  private val fdlImag = Array(maxPartitions) { DoubleArray(fftSize) }
  private var fdlHead = 0

  // Pre-allocated scratch FFT & Overlap-Add tail buffers
  private val workInReal = DoubleArray(fftSize)
  private val workInImag = DoubleArray(fftSize)
  private val accRealL = DoubleArray(fftSize)
  private val accImagL = DoubleArray(fftSize)
  private val accRealR = DoubleArray(fftSize)
  private val accImagR = DoubleArray(fftSize)
  private val overlapL = DoubleArray(blockSize)
  private val overlapR = DoubleArray(blockSize)

  private var cachedRoomSize = -1f
  private var cachedDamping = -1f
  private var irInitialized = false

  private var smoothedWet = 0f
  private var wetInitialized = false

  fun reset() {
    for (p in 0 until maxPartitions) {
      fdlReal[p].fill(0.0)
      fdlImag[p].fill(0.0)
    }
    fdlHead = 0
    overlapL.fill(0.0)
    overlapR.fill(0.0)
    wetInitialized = false
  }

  private fun ensureImpulseResponse(roomSize: Float, damping: Float) {
    if (irInitialized &&
      abs(roomSize - cachedRoomSize) < 0.015f &&
      abs(damping - cachedDamping) < 0.015f
    ) {
      return
    }
    cachedRoomSize = roomSize
    cachedDamping = damping
    irInitialized = true

    val ir = ConvolutionReverbDsp.generateStereoImpulseResponse(
      sampleRate = safeRate,
      roomSize = roomSize,
      damping = damping,
      maxFrames = maxPartitions * blockSize,
    )
    val partitions = ((ir.frameCount + blockSize - 1) / blockSize).coerceIn(1, maxPartitions)
    activePartitions = partitions

    for (p in 0 until partitions) {
      val start = p * blockSize
      val partRealL = irPartRealL[p]
      val partImagL = irPartImagL[p]
      val partRealR = irPartRealR[p]
      val partImagR = irPartImagR[p]
      partRealL.fill(0.0)
      partImagL.fill(0.0)
      partRealR.fill(0.0)
      partImagR.fill(0.0)

      val copyCount = min(blockSize, ir.frameCount - start)
      for (i in 0 until copyCount) {
        partRealL[i] = ir.left[start + i].toDouble()
        partRealR[i] = ir.right[start + i].toDouble()
      }
      FourierAudioBufferProcessor.fftInPlace(partRealL, partImagL)
      FourierAudioBufferProcessor.fftInPlace(partRealR, partImagR)
    }
  }

  fun processBlock(
    input: ShortArray,
    frameCount: Int,
    settings: ReverbSettings,
    output: ShortArray,
    volumeGain: Float = 1f,
  ) {
    if (frameCount <= 0) return

    val targetWet = settings.wetMix.coerceIn(0f, 1f)
    val targetRoom = settings.roomSize.coerceIn(0.05f, 0.98f)
    val targetDamp = settings.damping.coerceIn(0.05f, 0.95f)
    if (!wetInitialized) {
      smoothedWet = targetWet
      wetInitialized = true
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

    ensureImpulseResponse(targetRoom, targetDamp)

    var processed = 0
    while (processed < frameCount) {
      val subFrames = min(blockSize, frameCount - processed)
      convolveSubBlock(
        input = input,
        frameOffset = processed,
        subFrames = subFrames,
        targetWet = targetWet,
        output = output,
        safeGain = safeGain,
      )
      processed += subFrames
    }
  }

  private fun convolveSubBlock(
    input: ShortArray,
    frameOffset: Int,
    subFrames: Int,
    targetWet: Float,
    output: ShortArray,
    safeGain: Float,
  ) {
    // 1. Forward FFT of mono sum of this sub-block, zero-padded to 1024
    workInReal.fill(0.0)
    workInImag.fill(0.0)
    for (f in 0 until subFrames) {
      val base = (frameOffset + f) * safeChannels
      val mono = if (safeChannels >= 2) {
        (input[base] + input[base + 1]) / 65536.0
      } else {
        input[base] / 32768.0
      }
      workInReal[f] = mono
    }
    FourierAudioBufferProcessor.fftInPlace(workInReal, workInImag)

    fdlHead = (fdlHead + 1) % maxPartitions
    workInReal.copyInto(fdlReal[fdlHead])
    workInImag.copyInto(fdlImag[fdlHead])

    // 2. Partitioned spectral multiply-accumulate for Left and Right IRs
    accRealL.fill(0.0)
    accImagL.fill(0.0)
    accRealR.fill(0.0)
    accImagR.fill(0.0)

    val partCount = activePartitions
    for (p in 0 until partCount) {
      val fdlIdx = (fdlHead - p + maxPartitions) % maxPartitions
      val xR = fdlReal[fdlIdx]
      val xI = fdlImag[fdlIdx]
      val hLR = irPartRealL[p]
      val hLI = irPartImagL[p]
      val hRR = irPartRealR[p]
      val hRI = irPartImagR[p]

      for (k in 0 until fftSize) {
        val xr = xR[k]
        val xi = xI[k]
        accRealL[k] += xr * hLR[k] - xi * hLI[k]
        accImagL[k] += xr * hLI[k] + xi * hLR[k]
        accRealR[k] += xr * hRR[k] - xi * hRI[k]
        accImagR[k] += xr * hRI[k] + xi * hRR[k]
      }
    }

    // 3. Inverse FFT via complex conjugate + scaling
    for (k in 0 until fftSize) {
      accImagL[k] = -accImagL[k]
      accImagR[k] = -accImagR[k]
    }
    FourierAudioBufferProcessor.fftInPlace(accRealL, accImagL)
    FourierAudioBufferProcessor.fftInPlace(accRealR, accImagR)

    val invN = 1.0 / fftSize.toDouble()
    for (f in 0 until subFrames) {
      smoothedWet += (targetWet - smoothedWet) * 0.004f
      val wetGain = smoothedWet * 0.85f
      val dryGain = 1f - (smoothedWet * 0.30f)

      val wetL = ((accRealL[f] * invN) + overlapL[f]).toFloat()
      val wetR = ((accRealR[f] * invN) + overlapR[f]).toFloat()

      val base = (frameOffset + f) * safeChannels
      val dryL = input[base] / 32768f
      if (safeChannels >= 2) {
        val dryR = input[base + 1] / 32768f
        val mixedL = (dryL * dryGain + wetL * wetGain) * safeGain
        val mixedR = (dryR * dryGain + wetR * wetGain) * safeGain
        output[base] = softLimitToShort(mixedL)
        output[base + 1] = softLimitToShort(mixedR)
      } else {
        val wetMono = (wetL + wetR) * 0.5f
        val mixed = (dryL * dryGain + wetMono * wetGain) * safeGain
        output[base] = softLimitToShort(mixed)
      }
    }

    // 4. Carry remaining overlap-add tail into next block
    for (i in 0 until blockSize) {
      val tailIdx = subFrames + i
      overlapL[i] = if (tailIdx < fftSize) accRealL[tailIdx] * invN else 0.0
      overlapR[i] = if (tailIdx < fftSize) accRealR[tailIdx] * invN else 0.0
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
