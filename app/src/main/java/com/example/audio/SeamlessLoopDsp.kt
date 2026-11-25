package com.example.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Playback looping mode for the Audio Player Studio.
 */
enum class TrackLoopMode(val label: String) {
  OFF("Off"),
  LOOP_TRACK("Loop Track"),
  LOOP_SELECTION("Loop Selection"),
}

/**
 * Immutable configuration for seamless full-track and A–B selection looping.
 * Selection bounds are stored as normalized [0f, 1f] fractions of the active track.
 */
data class TrackLoopSettings(
  val mode: TrackLoopMode = TrackLoopMode.OFF,
  val selectionStartFraction: Float = SeamlessLoopDsp.DEFAULT_SELECTION_START,
  val selectionEndFraction: Float = SeamlessLoopDsp.DEFAULT_SELECTION_END,
) {
  val isLooping: Boolean
    get() = mode != TrackLoopMode.OFF

  val isLoopTrack: Boolean
    get() = mode == TrackLoopMode.LOOP_TRACK

  val isLoopSelection: Boolean
    get() = mode == TrackLoopMode.LOOP_SELECTION

  val selectionSpanFraction: Float
    get() = (selectionEndFraction - selectionStartFraction)
      .coerceAtLeast(SeamlessLoopDsp.MIN_SELECTION_FRACTION)

  fun normalized(): TrackLoopSettings {
    var start = selectionStartFraction.coerceIn(0f, 1f - SeamlessLoopDsp.MIN_SELECTION_FRACTION)
    var end = selectionEndFraction.coerceIn(SeamlessLoopDsp.MIN_SELECTION_FRACTION, 1f)
    if (end < start) {
      val tmp = start
      start = end
      end = tmp
    }
    if (end - start < SeamlessLoopDsp.MIN_SELECTION_FRACTION) {
      if (start + SeamlessLoopDsp.MIN_SELECTION_FRACTION <= 1f) {
        end = start + SeamlessLoopDsp.MIN_SELECTION_FRACTION
      } else {
        end = 1f
        start = (1f - SeamlessLoopDsp.MIN_SELECTION_FRACTION).coerceAtLeast(0f)
      }
    }
    return copy(
      selectionStartFraction = start.coerceIn(0f, 1f),
      selectionEndFraction = end.coerceIn(0f, 1f),
    )
  }

  fun withMode(nextMode: TrackLoopMode): TrackLoopSettings = copy(mode = nextMode).normalized()

  fun nextCycledMode(): TrackLoopMode = when (mode) {
    TrackLoopMode.OFF -> TrackLoopMode.LOOP_TRACK
    TrackLoopMode.LOOP_TRACK -> TrackLoopMode.LOOP_SELECTION
    TrackLoopMode.LOOP_SELECTION -> TrackLoopMode.OFF
  }

  fun withSelectionRange(
    startFraction: Float,
    endFraction: Float,
    autoEnableSelectionMode: Boolean = true,
  ): TrackLoopSettings {
    val lo = min(startFraction, endFraction).coerceIn(0f, 1f)
    val hi = maxOf(startFraction, endFraction).coerceIn(0f, 1f)
    val nextMode = if (autoEnableSelectionMode) TrackLoopMode.LOOP_SELECTION else mode
    return copy(
      mode = nextMode,
      selectionStartFraction = lo,
      selectionEndFraction = hi,
    ).normalized()
  }

  fun withLoopInAt(fraction: Float, autoEnableSelectionMode: Boolean = true): TrackLoopSettings {
    val clamped = fraction.coerceIn(0f, 1f - SeamlessLoopDsp.MIN_SELECTION_FRACTION)
    val adjustedEnd = if (selectionEndFraction - clamped < SeamlessLoopDsp.MIN_SELECTION_FRACTION) {
      (clamped + SeamlessLoopDsp.MIN_SELECTION_FRACTION).coerceAtMost(1f)
    } else {
      selectionEndFraction
    }
    return withSelectionRange(clamped, adjustedEnd, autoEnableSelectionMode)
  }

  fun withLoopOutAt(fraction: Float, autoEnableSelectionMode: Boolean = true): TrackLoopSettings {
    val clamped = fraction.coerceIn(SeamlessLoopDsp.MIN_SELECTION_FRACTION, 1f)
    val adjustedStart = if (clamped - selectionStartFraction < SeamlessLoopDsp.MIN_SELECTION_FRACTION) {
      (clamped - SeamlessLoopDsp.MIN_SELECTION_FRACTION).coerceAtLeast(0f)
    } else {
      selectionStartFraction
    }
    return withSelectionRange(adjustedStart, clamped, autoEnableSelectionMode)
  }

  fun resetSelection(): TrackLoopSettings = copy(
    selectionStartFraction = SeamlessLoopDsp.DEFAULT_SELECTION_START,
    selectionEndFraction = SeamlessLoopDsp.DEFAULT_SELECTION_END,
  ).normalized()

  fun resolvedStartFrame(totalFrames: Int): Int {
    if (totalFrames <= 0) return 0
    return when (mode) {
      TrackLoopMode.LOOP_SELECTION -> {
        val norm = normalized()
        (norm.selectionStartFraction * totalFrames).roundToInt()
          .coerceIn(0, (totalFrames - 1).coerceAtLeast(0))
      }
      TrackLoopMode.LOOP_TRACK, TrackLoopMode.OFF -> 0
    }
  }

  fun resolvedEndFrame(totalFrames: Int): Int {
    if (totalFrames <= 0) return 0
    return when (mode) {
      TrackLoopMode.LOOP_SELECTION -> {
        val norm = normalized()
        val start = resolvedStartFrame(totalFrames)
        val rawEnd = (norm.selectionEndFraction * totalFrames).roundToInt()
        val minFrames = min(totalFrames, maxOf(64, (totalFrames * SeamlessLoopDsp.MIN_SELECTION_FRACTION).roundToInt()))
        rawEnd.coerceIn((start + minFrames).coerceAtMost(totalFrames), totalFrames)
      }
      TrackLoopMode.LOOP_TRACK, TrackLoopMode.OFF -> totalFrames
    }
  }

  fun selectionStartMs(durationMs: Long): Long {
    val safe = durationMs.coerceAtLeast(0L)
    return (normalized().selectionStartFraction * safe).toLong().coerceIn(0L, safe)
  }

  fun selectionEndMs(durationMs: Long): Long {
    val safe = durationMs.coerceAtLeast(0L)
    return (normalized().selectionEndFraction * safe).toLong().coerceIn(0L, safe)
  }

  fun selectionDurationMs(durationMs: Long): Long {
    return (selectionEndMs(durationMs) - selectionStartMs(durationMs)).coerceAtLeast(0L)
  }
}

/**
 * Sample-accurate, click-free looping DSP for both full-track looping and A–B selection looping.
 *
 * Uses a continuous overlap-add equal-power micro-crossfade ([DEFAULT_CROSSFADE_MS]) across the
 * loop seam so arbitrary A–B loop points never produce hard zero-crossing pops, silence gaps,
 * or reset downstream reverb/EQ tails.
 */
object SeamlessLoopDsp {

  const val MIN_SELECTION_FRACTION = 0.04f
  const val DEFAULT_SELECTION_START = 0.20f
  const val DEFAULT_SELECTION_END = 0.75f
  const val DEFAULT_CROSSFADE_MS = 12f

  data class LoopReadResult(
    val framesRead: Int,
    val nextFrameCursor: Int,
    val didWrap: Boolean,
  )

  /**
   * Computes the equal-power micro-crossfade length in frames for a loop of [loopLengthFrames].
   */
  fun computeMicroCrossfadeFrames(
    sampleRate: Int,
    loopLengthFrames: Int,
    crossfadeMs: Float = DEFAULT_CROSSFADE_MS,
  ): Int {
    if (loopLengthFrames < 32 || sampleRate <= 0 || crossfadeMs <= 0f) return 0
    val desired = ((crossfadeMs / 1000.0) * sampleRate).roundToInt()
    val maxAllowed = (loopLengthFrames / 6).coerceAtLeast(0)
    return desired.coerceIn(0, maxAllowed)
  }

  /**
   * Reads [requestedFrames] seamlessly from [pcmSamples] while honoring [loopSettings].
   * Always fills a full continuous chunk across loop boundaries with an equal-power micro-crossfade
   * at the wrap seam so [android.media.AudioTrack] never experiences a gap or click.
   */
  fun extractSeamlessLoopChunk(
    pcmSamples: ShortArray,
    totalFrames: Int,
    sampleRate: Int,
    channelCount: Int,
    cursorFrame: Int,
    requestedFrames: Int,
    loopSettings: TrackLoopSettings,
    destination: ShortArray,
    crossfadeMs: Float = DEFAULT_CROSSFADE_MS,
  ): LoopReadResult {
    val channels = channelCount.coerceIn(1, 2)
    val maxDestFrames = destination.size / channels
    val maxSourceFrames = min(totalFrames, pcmSamples.size / channels)
    val targetFrames = min(requestedFrames, maxDestFrames)
    if (targetFrames <= 0 || maxSourceFrames <= 0) {
      return LoopReadResult(framesRead = 0, nextFrameCursor = cursorFrame, didWrap = false)
    }

    val loopStart = loopSettings.resolvedStartFrame(maxSourceFrames)
    val loopEnd = loopSettings.resolvedEndFrame(maxSourceFrames)
    val loopLen = (loopEnd - loopStart).coerceAtLeast(1)

    // Snap cursor into active loop window if user enabled selection loop while playhead was outside
    var frame = if (cursorFrame < loopStart || cursorFrame >= loopEnd) {
      loopStart
    } else {
      cursorFrame
    }

    val fadeFrames = computeMicroCrossfadeFrames(
      sampleRate = sampleRate,
      loopLengthFrames = loopLen,
      crossfadeMs = crossfadeMs,
    )
    val fadeStartFrame = loopEnd - fadeFrames
    var didWrap = false

    for (outFrame in 0 until targetFrames) {
      val dstBase = outFrame * channels
      if (fadeFrames > 0 && frame >= fadeStartFrame && frame < loopEnd) {
        val fadePos = frame - fadeStartFrame
        val headFrame = loopStart + fadePos
        // Equal-power transition: t in [0, 1) from tail -> head
        val t = (fadePos.toDouble() / fadeFrames.toDouble()).coerceIn(0.0, 1.0)
        val angle = t * (PI * 0.5)
        val outGain = cos(angle)
        val inGain = sin(angle)
        val tailBase = frame * channels
        val headBase = headFrame * channels
        for (ch in 0 until channels) {
          val blended = pcmSamples[tailBase + ch] * outGain + pcmSamples[headBase + ch] * inGain
          destination[dstBase + ch] = blended
            .roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
        }
      } else {
        val srcBase = frame * channels
        for (ch in 0 until channels) {
          destination[dstBase + ch] = pcmSamples[srcBase + ch]
        }
      }

      frame++
      if (frame >= loopEnd) {
        // After blending [loopEnd - fadeFrames .. loopEnd) with [loopStart .. loopStart + fadeFrames),
        // continuing at loopStart + fadeFrames maintains exact waveform continuity with zero seam click.
        frame = (loopStart + fadeFrames).coerceIn(loopStart, (loopEnd - 1).coerceAtLeast(loopStart))
        didWrap = true
      }
    }

    return LoopReadResult(
      framesRead = targetFrames,
      nextFrameCursor = frame,
      didWrap = didWrap,
    )
  }

  /**
   * Formats milliseconds as mm:ss.t (tenths of a second) for precise A–B loop boundary display.
   */
  fun formatLoopTime(ms: Long): String {
    val safeMs = ms.coerceAtLeast(0L)
    val totalTenths = safeMs / 100L
    val tenths = totalTenths % 10L
    val totalSeconds = totalTenths / 10L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d.%d".format(minutes, seconds, tenths)
  }

  fun formatSpanSeconds(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0L) / 1000f)
    return "%.1fs".format(seconds)
  }
}
