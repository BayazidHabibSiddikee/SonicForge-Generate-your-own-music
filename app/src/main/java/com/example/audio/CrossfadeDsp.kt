package com.example.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class CrossfadeSettings(
  val enabled: Boolean = false,
  val durationSec: Float = 4.0f,
) {
  val isActive: Boolean
    get() = enabled && durationSec >= 0.25f

  fun clamped(): CrossfadeSettings = copy(
    durationSec = CrossfadeDsp.clampDurationSec(durationSec),
  )
}

/**
 * Equal-power dual-deck crossfade DSP engine for seamless playlist transitions.
 *
 * Uses constant-power cosine/sine fade curves:
 * - outgoingGain = cos(p * PI / 2)
 * - incomingGain = sin(p * PI / 2)
 * so outgoingGain^2 + incomingGain^2 = 1.0 across the entire transition window,
 * preventing perceived loudness dips between overlapping tracks.
 */
object CrossfadeDsp {

  const val MIN_DURATION_SEC = 0f
  const val MAX_DURATION_SEC = 12f
  const val DEFAULT_DURATION_SEC = 4f
  val PRESET_SECONDS = listOf(0, 2, 4, 6, 8, 12)

  fun clampDurationSec(seconds: Float): Float {
    if (seconds.isNaN()) return DEFAULT_DURATION_SEC
    return seconds.coerceIn(MIN_DURATION_SEC, MAX_DURATION_SEC)
  }

  fun formatDurationBadge(settings: CrossfadeSettings): String {
    if (!settings.isActive) return "Off"
    val roundedHalf = (settings.durationSec * 2f).roundToInt() / 2f
    return if (roundedHalf % 1f == 0f) {
      "${roundedHalf.toInt()}s · Equal-Power"
    } else {
      "%.1fs · Equal-Power".format(roundedHalf)
    }
  }

  /**
   * Computes the effective crossfade duration in milliseconds so short tracks
   * still overlap smoothly without exceeding 45% of either track's length.
   */
  fun computeEffectiveCrossfadeMs(
    settings: CrossfadeSettings,
    outgoingDurationMs: Long,
    incomingDurationMs: Long,
  ): Long {
    if (!settings.isActive) return 0L
    val requestedMs = (settings.durationSec * 1000f).toLong().coerceAtLeast(0L)
    if (requestedMs <= 0L) return 0L
    val shortestDuration = min(
      outgoingDurationMs.coerceAtLeast(0L),
      incomingDurationMs.coerceAtLeast(0L),
    )
    if (shortestDuration < 1_000L) return 0L
    val maxSafeMs = (shortestDuration * 0.45f).toLong().coerceAtLeast(250L)
    return min(requestedMs, maxSafeMs)
  }

  /**
   * Computes constant-power `(outgoingGain, incomingGain)` for normalized transition
   * progress `p` in `0f..1f`.
   */
  fun computeEqualPowerGains(progress: Float): Pair<Float, Float> {
    val p = progress.coerceIn(0f, 1f).toDouble()
    val angle = p * (PI / 2.0)
    return cos(angle).toFloat() to sin(angle).toFloat()
  }

  /**
   * Blends a dual-deck chunk (`outgoing` + `incoming`) sample-by-sample using a smooth
   * equal-power fade progression across `[progressStart, progressEnd]` and scales by [masterGain].
   */
  fun mixDualDeckChunk(
    outgoing: ShortArray,
    incoming: ShortArray,
    frameCount: Int,
    channelCount: Int,
    progressStart: Float,
    progressEnd: Float,
    masterGain: Float,
    output: ShortArray,
  ) {
    val channels = channelCount.coerceIn(1, 2)
    val safeFrames = min(
      frameCount,
      min(outgoing.size / channels, min(incoming.size / channels, output.size / channels)),
    )
    if (safeFrames <= 0) return

    val safeGain = masterGain.coerceIn(0f, 1.5f)
    val denom = (safeFrames - 1).coerceAtLeast(1).toFloat()

    for (f in 0 until safeFrames) {
      val t = f / denom
      val progress = (progressStart + (progressEnd - progressStart) * t).coerceIn(0f, 1f)
      val (outGain, inGain) = computeEqualPowerGains(progress)
      val base = f * channels
      for (c in 0 until channels) {
        val mixed = (outgoing[base + c] * outGain + incoming[base + c] * inGain) * safeGain
        val clamped = softLimitPcm(mixed)
        output[base + c] = clamped
      }
    }
  }

  /**
   * Converts/resamples a chunk of PCM frames from a source deck `(srcRate, srcChannels)` to
   * match the target output AudioTrack format `(dstRate, dstChannels)`, returning the number
   * of source frames consumed so the incoming deck cursor stays phase-accurate at handoff.
   */
  fun extractResampledChunk(
    sourceBuffer: RawAudioBuffer,
    sourceFrameStart: Int,
    targetFrames: Int,
    targetSampleRate: Int,
    targetChannels: Int,
    destination: ShortArray,
  ): Int {
    val srcChannels = sourceBuffer.channelCount.coerceIn(1, 2)
    val dstChannels = targetChannels.coerceIn(1, 2)
    val srcRate = sourceBuffer.sampleRate.coerceAtLeast(8_000)
    val dstRate = targetSampleRate.coerceAtLeast(8_000)
    val totalSrcFrames = sourceBuffer.frameCount

    if (totalSrcFrames <= 0 || sourceFrameStart >= totalSrcFrames || targetFrames <= 0) {
      destination.fill(0, 0, (targetFrames * dstChannels).coerceAtMost(destination.size))
      return 0
    }

    val rateRatio = srcRate.toDouble() / dstRate.toDouble()
    var maxSrcConsumed = 0

    for (f in 0 until targetFrames) {
      val srcPos = sourceFrameStart + f * rateRatio
      val idx0 = srcPos.toInt()
      if (idx0 >= totalSrcFrames) {
        val base = f * dstChannels
        for (c in 0 until dstChannels) {
          destination[base + c] = 0
        }
        continue
      }
      val idx1 = min(idx0 + 1, totalSrcFrames - 1)
      val frac = (srcPos - idx0).toFloat()
      maxSrcConsumed = (idx0 - sourceFrameStart) + 1

      val outBase = f * dstChannels
      if (srcChannels == dstChannels) {
        for (c in 0 until dstChannels) {
          val s0 = sourceBuffer.pcmSamples[idx0 * srcChannels + c].toFloat()
          val s1 = sourceBuffer.pcmSamples[idx1 * srcChannels + c].toFloat()
          destination[outBase + c] = (s0 + (s1 - s0) * frac).roundToInt().toShort()
        }
      } else if (srcChannels == 1 && dstChannels == 2) {
        val s0 = sourceBuffer.pcmSamples[idx0].toFloat()
        val s1 = sourceBuffer.pcmSamples[idx1].toFloat()
        val sample = (s0 + (s1 - s0) * frac).roundToInt().toShort()
        destination[outBase] = sample
        destination[outBase + 1] = sample
      } else {
        // Stereo -> Mono downmix
        val s0 = (sourceBuffer.pcmSamples[idx0 * 2] + sourceBuffer.pcmSamples[idx0 * 2 + 1]) * 0.5f
        val s1 = (sourceBuffer.pcmSamples[idx1 * 2] + sourceBuffer.pcmSamples[idx1 * 2 + 1]) * 0.5f
        destination[outBase] = (s0 + (s1 - s0) * frac).roundToInt().toShort()
      }
    }

    return if (srcRate == dstRate) {
      min(targetFrames, totalSrcFrames - sourceFrameStart)
    } else {
      maxSrcConsumed.coerceAtMost(totalSrcFrames - sourceFrameStart)
    }
  }

  private fun softLimitPcm(sample: Float): Short {
    val norm = sample / 32768f
    val shaped = when {
      norm > 0.92f -> {
        val excess = norm - 0.92f
        0.92f + (excess / (1f + excess * 4f)) * 0.08f
      }
      norm < -0.92f -> {
        val excess = -0.92f - norm
        -0.92f - (excess / (1f + excess * 4f)) * 0.08f
      }
      else -> norm
    }
    return (shaped * 32767f)
      .roundToInt()
      .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
      .toShort()
  }
}
