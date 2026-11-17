package com.example.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

enum class WaveformInteractionMode {
  SCRUB,
  ENVELOPE,
  MUTE,
}

data class EnvelopePoint(
  val fraction: Float,
  val gain: Float,
)

data class MutedSegment(
  val startFraction: Float,
  val endFraction: Float,
) {
  val normalized: MutedSegment
    get() {
      val s = min(startFraction, endFraction).coerceIn(0f, 1f)
      val e = max(startFraction, endFraction).coerceIn(0f, 1f)
      return MutedSegment(s, e)
    }

  fun contains(fraction: Float): Boolean = fraction in startFraction..endFraction
}

data class TrackAudioEnvelope(
  val points: List<EnvelopePoint> = emptyList(),
  val mutedSegments: List<MutedSegment> = emptyList(),
) {
  val isEdited: Boolean
    get() = points.isNotEmpty() || mutedSegments.isNotEmpty()

  fun toJsonString(): String {
    if (!isEdited) return ""
    val pointsArr = JSONArray()
    points.forEach { pt ->
      pointsArr.put(
        JSONObject().apply {
          put("f", pt.fraction.toDouble())
          put("g", pt.gain.toDouble())
        }
      )
    }
    val mutesArr = JSONArray()
    mutedSegments.forEach { seg ->
      mutesArr.put(
        JSONObject().apply {
          put("s", seg.startFraction.toDouble())
          put("e", seg.endFraction.toDouble())
        }
      )
    }
    return JSONObject().apply {
      put("points", pointsArr)
      put("mutes", mutesArr)
    }.toString()
  }

  companion object {
    fun fromJsonString(raw: String): TrackAudioEnvelope {
      if (raw.isBlank()) return TrackAudioEnvelope()
      return try {
        val root = JSONObject(raw)
        val parsedPoints = mutableListOf<EnvelopePoint>()
        val pointsArr = root.optJSONArray("points")
        if (pointsArr != null) {
          for (i in 0 until pointsArr.length()) {
            val obj = pointsArr.optJSONObject(i) ?: continue
            val f = obj.optDouble("f", -1.0).toFloat()
            val g = obj.optDouble("g", -1.0).toFloat()
            if (f in 0f..1f && g in VolumeEnvelopeDsp.MIN_GAIN..VolumeEnvelopeDsp.MAX_GAIN) {
              parsedPoints.add(EnvelopePoint(f, g))
            }
          }
        }
        val parsedMutes = mutableListOf<MutedSegment>()
        val mutesArr = root.optJSONArray("mutes")
        if (mutesArr != null) {
          for (i in 0 until mutesArr.length()) {
            val obj = mutesArr.optJSONObject(i) ?: continue
            val s = obj.optDouble("s", -1.0).toFloat().coerceIn(0f, 1f)
            val e = obj.optDouble("e", -1.0).toFloat().coerceIn(0f, 1f)
            if (e > s + 0.001f) {
              parsedMutes.add(MutedSegment(s, e))
            }
          }
        }
        TrackAudioEnvelope(
          points = parsedPoints.sortedBy { it.fraction },
          mutedSegments = mergeSortedSegments(parsedMutes),
        )
      } catch (_: Exception) {
        TrackAudioEnvelope()
      }
    }

    internal fun mergeSortedSegments(segments: List<MutedSegment>): List<MutedSegment> {
      if (segments.isEmpty()) return emptyList()
      val sorted = segments.map { it.normalized }
        .filter { it.endFraction > it.startFraction + 0.001f }
        .sortedBy { it.startFraction }
      if (sorted.isEmpty()) return emptyList()

      val merged = mutableListOf<MutedSegment>()
      var current = sorted.first()
      for (i in 1 until sorted.size) {
        val next = sorted[i]
        current = if (next.startFraction <= current.endFraction + 0.005f) {
          MutedSegment(
            startFraction = current.startFraction,
            endFraction = max(current.endFraction, next.endFraction),
          )
        } else {
          merged.add(current)
          next
        }
      }
      merged.add(current)
      return merged
    }
  }
}

/**
 * Interactive volume envelope & segment mute DSP engine.
 * Supports smooth piecewise-linear gain curves (0.0x to 1.5x) and muted timeline regions
 * applied in real time during PCM streaming and offline during MP3 export.
 */
object VolumeEnvelopeDsp {

  const val MIN_GAIN = 0f
  const val UNITY_GAIN = 1.0f
  const val MAX_GAIN = 1.5f
  const val DEFAULT_TAP_MUTE_HALF_WIDTH = 0.025f

  fun yFractionToGain(yFraction: Float): Float {
    val clampedY = yFraction.coerceIn(0f, 1f)
    return ((1f - clampedY) * MAX_GAIN).coerceIn(MIN_GAIN, MAX_GAIN)
  }

  fun gainToYFraction(gain: Float): Float {
    val clampedG = gain.coerceIn(MIN_GAIN, MAX_GAIN)
    return (1f - (clampedG / MAX_GAIN)).coerceIn(0f, 1f)
  }

  /**
   * Evaluates the gain multiplier at timeline [fraction] (`0f..1f`), returning `0f`
   * inside any muted segment or interpolating the custom volume envelope curve.
   */
  fun sampleEnvelopeGainAt(
    envelope: TrackAudioEnvelope,
    fraction: Float,
  ): Float {
    val x = fraction.coerceIn(0f, 1f)
    if (envelope.mutedSegments.any { it.contains(x) }) {
      return 0f
    }
    return sampleCurveOnlyAt(envelope.points, x)
  }

  /**
   * Evaluates only the continuous volume envelope curve (ignoring mute overlays so UI
   * can draw both the curve and red mute overlays cleanly).
   */
  fun sampleCurveOnlyAt(
    points: List<EnvelopePoint>,
    fraction: Float,
  ): Float {
    if (points.isEmpty()) return UNITY_GAIN
    val x = fraction.coerceIn(0f, 1f)
    if (x <= points.first().fraction) return points.first().gain.coerceIn(MIN_GAIN, MAX_GAIN)
    if (x >= points.last().fraction) return points.last().gain.coerceIn(MIN_GAIN, MAX_GAIN)

    for (i in 0 until points.lastIndex) {
      val a = points[i]
      val b = points[i + 1]
      if (x in a.fraction..b.fraction) {
        val span = (b.fraction - a.fraction).coerceAtLeast(1e-5f)
        val t = ((x - a.fraction) / span).coerceIn(0f, 1f)
        return (a.gain + (b.gain - a.gain) * t).coerceIn(MIN_GAIN, MAX_GAIN)
      }
    }
    return UNITY_GAIN
  }

  /**
   * Inserts or updates an envelope control point at `(fraction, gain)`, consolidating
   * overly dense neighbor points within [mergeThreshold] so finger drags produce smooth curves.
   */
  fun paintEnvelopePoint(
    envelope: TrackAudioEnvelope,
    fraction: Float,
    gain: Float,
    mergeThreshold: Float = 0.022f,
  ): TrackAudioEnvelope {
    val cleanX = fraction.coerceIn(0f, 1f)
    val cleanG = gain.coerceIn(MIN_GAIN, MAX_GAIN)
    val filtered = envelope.points.filterNot { abs(it.fraction - cleanX) < mergeThreshold }.toMutableList()
    filtered.add(EnvelopePoint(cleanX, cleanG))
    filtered.sortBy { it.fraction }
    return envelope.copy(points = filtered)
  }

  fun addOrMergeMutedSegment(
    envelope: TrackAudioEnvelope,
    startFraction: Float,
    endFraction: Float,
  ): TrackAudioEnvelope {
    val s = min(startFraction, endFraction).coerceIn(0f, 1f)
    val e = max(startFraction, endFraction).coerceIn(0f, 1f)
    val safeEnd = if (e - s < 0.015f) (s + 0.015f).coerceAtMost(1f) else e
    val safeStart = if (safeEnd - s < 0.015f) (safeEnd - 0.015f).coerceAtLeast(0f) else s
    val updated = TrackAudioEnvelope.mergeSortedSegments(
      envelope.mutedSegments + MutedSegment(safeStart, safeEnd)
    )
    return envelope.copy(mutedSegments = updated)
  }

  fun toggleMuteAtTap(
    envelope: TrackAudioEnvelope,
    fraction: Float,
  ): TrackAudioEnvelope {
    val x = fraction.coerceIn(0f, 1f)
    val hit = envelope.mutedSegments.firstOrNull { it.contains(x) }
    return if (hit != null) {
      envelope.copy(mutedSegments = envelope.mutedSegments.filterNot { it == hit })
    } else {
      addOrMergeMutedSegment(
        envelope = envelope,
        startFraction = (x - DEFAULT_TAP_MUTE_HALF_WIDTH).coerceAtLeast(0f),
        endFraction = (x + DEFAULT_TAP_MUTE_HALF_WIDTH).coerceAtMost(1f),
      )
    }
  }

  /**
   * Applies the active [envelope] in place to an output PCM chunk starting at [startFrame].
   */
  fun applyEnvelopeToChunk(
    samples: ShortArray,
    frameCount: Int,
    channelCount: Int,
    startFrame: Int,
    totalFrames: Int,
    envelope: TrackAudioEnvelope,
  ) {
    if (!envelope.isEdited || totalFrames <= 0 || frameCount <= 0) return
    val channels = channelCount.coerceIn(1, 2)
    val safeFrames = min(frameCount, samples.size / channels)
    val denom = totalFrames.coerceAtLeast(1).toFloat()

    for (f in 0 until safeFrames) {
      val fraction = ((startFrame + f).toFloat() / denom).coerceIn(0f, 1f)
      val gain = sampleEnvelopeGainAt(envelope, fraction)
      if (abs(gain - UNITY_GAIN) < 0.001f) continue
      val base = f * channels
      for (c in 0 until channels) {
        val scaled = (samples[base + c] * gain)
          .roundToInt()
          .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
          .toShort()
        samples[base + c] = scaled
      }
    }
  }

  fun applyEnvelopeOffline(
    buffer: RawAudioBuffer,
    envelope: TrackAudioEnvelope,
  ): RawAudioBuffer {
    if (!envelope.isEdited || buffer.pcmSamples.isEmpty() || buffer.frameCount <= 0) {
      return buffer
    }
    val processed = buffer.pcmSamples.copyOf()
    applyEnvelopeToChunk(
      samples = processed,
      frameCount = buffer.frameCount,
      channelCount = buffer.channelCount,
      startFrame = 0,
      totalFrames = buffer.frameCount,
      envelope = envelope,
    )
    return FourierAudioBufferProcessor.analyzeRawPcm(
      pcmSamples = processed,
      sampleRate = buffer.sampleRate,
      channelCount = buffer.channelCount,
      bridgeSource = buffer.bridgeSource,
    )
  }
}
