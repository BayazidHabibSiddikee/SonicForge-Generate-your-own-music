package com.example.audio

import com.example.model.GeneratedTrack
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.tanh

data class MixStemSpec(
  val track: GeneratedTrack,
  val gain: Float = 1.0f,
  val loopToMatch: Boolean = true,
)

/**
 * Multi-track PCM stem merger that decodes Piano, Guitar, Drum, imported, and studio stems,
 * harmonizes sample rates and channels, optionally loops shorter stems to match the longest
 * arrangement, balances per-stem gain, limits headroom cleanly, and normalizes loudness.
 */
object MultiTrackMixerDsp {

  const val TARGET_SAMPLE_RATE = 44_100
  const val TARGET_CHANNELS = 2

  fun mergeTracksToRawBuffer(
    stems: List<MixStemSpec>,
    targetLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
    applyPostGain: Boolean = true,
  ): RawAudioBuffer {
    if (stems.size < 2) {
      throw IllegalArgumentException("Select at least 2 tracks to merge into a master mix.")
    }

    val decodedStems = stems.mapNotNull { spec ->
      val file = File(spec.track.filePath)
      if (!file.exists()) return@mapNotNull null
      val buffer = FourierAudioBufferProcessor.decodeFileToRawBuffer(
        file = file,
        bridgeSource = spec.track.sourceLabel,
      )
      if (buffer.pcmSamples.isEmpty() || buffer.frameCount <= 0) return@mapNotNull null
      val stereoFrames = toTargetStereoFloat(buffer)
      Triple(spec, stereoFrames, buffer.bridgeSource)
    }

    if (decodedStems.size < 2) {
      throw IllegalStateException("At least 2 readable audio files are required to merge.")
    }

    val maxFrames = decodedStems.maxOf { (_, stereo, _) -> stereo.size / TARGET_CHANNELS }
      .coerceAtLeast(TARGET_SAMPLE_RATE)
    val accumulator = DoubleArray(maxFrames * TARGET_CHANNELS)

    // Sum all stems with per-stem gain scaling and smooth loop wrap if shorter
    for ((spec, stereo, _) in decodedStems) {
      val stemGain = spec.gain.coerceIn(0f, 1.5f).toDouble()
      if (stemGain <= 0.001) continue
      val stemFrames = stereo.size / TARGET_CHANNELS
      if (stemFrames <= 0) continue

      val copyLimit = if (spec.loopToMatch) maxFrames else min(maxFrames, stemFrames)
      val crossfadeFrames = min(stemFrames / 12, (0.015 * TARGET_SAMPLE_RATE).roundToInt())

      for (frame in 0 until copyLimit) {
        val wrappedFrame = frame % stemFrames
        var env = 1.0
        if (spec.loopToMatch && frame >= stemFrames && crossfadeFrames > 4) {
          if (wrappedFrame < crossfadeFrames) {
            env = wrappedFrame.toDouble() / crossfadeFrames
          } else if (wrappedFrame >= stemFrames - crossfadeFrames) {
            env = (stemFrames - 1 - wrappedFrame).toDouble() / crossfadeFrames
          }
        }
        val outIdx = frame * 2
        val srcIdx = wrappedFrame * 2
        accumulator[outIdx] += stereo[srcIdx] * stemGain * env
        accumulator[outIdx + 1] += stereo[srcIdx + 1] * stemGain * env
      }
    }

    // Soft-knee saturation + clean edge fades
    val headScale = 1.0 / kotlin.math.sqrt(decodedStems.size.toDouble()).coerceAtLeast(1.0)
    val fadeInFrames = min(maxFrames / 20, (0.012 * TARGET_SAMPLE_RATE).roundToInt())
    val fadeOutFrames = min(maxFrames / 10, (0.045 * TARGET_SAMPLE_RATE).roundToInt())
    val mixedShorts = ShortArray(maxFrames * TARGET_CHANNELS)

    for (frame in 0 until maxFrames) {
      var fade = 1.0
      if (frame < fadeInFrames) {
        fade *= frame.toDouble() / fadeInFrames.coerceAtLeast(1)
      }
      val distEnd = maxFrames - 1 - frame
      if (distEnd < fadeOutFrames) {
        fade *= distEnd.toDouble() / fadeOutFrames.coerceAtLeast(1)
      }
      val l = tanh(accumulator[frame * 2] * headScale) * fade
      val r = tanh(accumulator[frame * 2 + 1] * headScale) * fade
      mixedShorts[frame * 2] = (l * Short.MAX_VALUE * 0.90).toInt()
        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        .toShort()
      mixedShorts[frame * 2 + 1] = (r * Short.MAX_VALUE * 0.90).toInt()
        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        .toShort()
    }

    val finalSamples = if (applyPostGain) {
      LoudnessNormalizerDsp.normalizePcm(
        pcmSamples = mixedShorts,
        sampleRate = TARGET_SAMPLE_RATE,
        channelCount = TARGET_CHANNELS,
        targetLufs = targetLufs,
      ).normalizedSamples
    } else {
      mixedShorts
    }

    val sourceSummary = decodedStems.joinToString(" + ") { (spec, _, _) ->
      spec.track.category
    }.take(48)
    return FourierAudioBufferProcessor.analyzeRawPcm(
      pcmSamples = finalSamples,
      sampleRate = TARGET_SAMPLE_RATE,
      channelCount = TARGET_CHANNELS,
      bridgeSource = "Merged ($sourceSummary)",
    )
  }

  private fun toTargetStereoFloat(buffer: RawAudioBuffer): FloatArray {
    val srcRate = buffer.sampleRate.coerceAtLeast(8_000)
    val srcCh = buffer.channelCount.coerceIn(1, 2)
    val srcFrames = buffer.frameCount.coerceAtLeast(1)

    // Convert integer PCM to normalized stereo [-1, 1] at srcRate
    val srcStereo = FloatArray(srcFrames * TARGET_CHANNELS)
    if (srcCh == 2) {
      for (f in 0 until srcFrames) {
        srcStereo[f * 2] = buffer.pcmSamples[f * 2] / 32768f
        srcStereo[f * 2 + 1] = buffer.pcmSamples[f * 2 + 1] / 32768f
      }
    } else {
      for (f in 0 until srcFrames) {
        val v = buffer.pcmSamples[f] / 32768f
        srcStereo[f * 2] = v
        srcStereo[f * 2 + 1] = v
      }
    }

    if (srcRate == TARGET_SAMPLE_RATE) {
      return srcStereo
    }

    // Linear interpolation resampler to TARGET_SAMPLE_RATE
    val dstFrames = ((srcFrames.toDouble() * TARGET_SAMPLE_RATE) / srcRate).roundToInt().coerceAtLeast(1)
    val out = FloatArray(dstFrames * TARGET_CHANNELS)
    val ratio = (srcFrames - 1).toDouble() / (dstFrames - 1).coerceAtLeast(1)
    for (df in 0 until dstFrames) {
      val srcPos = df * ratio
      val i0 = srcPos.toInt().coerceIn(0, srcFrames - 1)
      val i1 = min(i0 + 1, srcFrames - 1)
      val frac = (srcPos - i0).toFloat()
      out[df * 2] = srcStereo[i0 * 2] + (srcStereo[i1 * 2] - srcStereo[i0 * 2]) * frac
      out[df * 2 + 1] = srcStereo[i0 * 2 + 1] + (srcStereo[i1 * 2 + 1] - srcStereo[i0 * 2 + 1]) * frac
    }
    return out
  }
}
