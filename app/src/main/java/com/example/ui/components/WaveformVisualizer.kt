package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.audio.TrackAudioEnvelope
import com.example.audio.VolumeEnvelopeDsp
import com.example.audio.WaveformInteractionMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Canvas-based audio waveform visualizer that renders PCM amplitude peaks,
 * syncs its played fill and glowing playhead cursor with [positionMs] / [durationMs],
 * renders volume envelope curves and muted segments, and supports tap/drag scrubbing
 * as well as visual Envelope & Mute editing modes.
 */
@Composable
fun WaveformVisualizer(
  peaks: List<Float>,
  positionMs: Long,
  durationMs: Long,
  isPlaying: Boolean,
  enabled: Boolean = true,
  contentDescriptionText: String = "Interactive audio waveform",
  onScrubStart: () -> Unit = {},
  onScrubToPosition: (Long) -> Unit = {},
  onScrubEnd: (Long) -> Unit = {},
  modifier: Modifier = Modifier,
  envelope: TrackAudioEnvelope = TrackAudioEnvelope(),
  interactionMode: WaveformInteractionMode = WaveformInteractionMode.SCRUB,
  onPaintEnvelopePoint: (fraction: Float, gain: Float) -> Unit = { _, _ -> },
  onMuteSegment: (startFraction: Float, endFraction: Float) -> Unit = { _, _ -> },
  onToggleMuteTap: (fraction: Float) -> Unit = {},
) {
  val colorScheme = MaterialTheme.colorScheme
  val safeDurationMs = durationMs.coerceAtLeast(1L)
  val rawProgress = if (enabled || durationMs > 0L) {
    (positionMs.toFloat() / safeDurationMs.toFloat()).coerceIn(0f, 1f)
  } else {
    0f
  }

  val animatedProgress by animateFloatAsState(
    targetValue = rawProgress,
    animationSpec = spring(dampingRatio = 0.9f, stiffness = 900f),
    label = "waveformProgress",
  )

  val infiniteTransition = rememberInfiniteTransition(label = "waveformPulse")
  val pulsePhase by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = (2f * Math.PI).toFloat(),
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1_100, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "pulsePhase",
  )

  var canvasWidthPx by remember { mutableFloatStateOf(1f) }
  var canvasHeightPx by remember { mutableFloatStateOf(1f) }
  var isDragging by remember { mutableStateOf(false) }
  var dragFraction by remember { mutableFloatStateOf(0f) }
  var muteDragStartFraction by remember { mutableFloatStateOf(-1f) }
  var muteDragEndFraction by remember { mutableFloatStateOf(-1f) }

  val currentOnScrubStart by rememberUpdatedState(onScrubStart)
  val currentOnScrubToPosition by rememberUpdatedState(onScrubToPosition)
  val currentOnScrubEnd by rememberUpdatedState(onScrubEnd)
  val currentOnPaintEnvelopePoint by rememberUpdatedState(onPaintEnvelopePoint)
  val currentOnMuteSegment by rememberUpdatedState(onMuteSegment)
  val currentOnToggleMuteTap by rememberUpdatedState(onToggleMuteTap)

  val effectiveProgress = if (isDragging) dragFraction else animatedProgress
  val displayPeaks = remember(peaks) {
    if (peaks.isNotEmpty()) peaks else defaultVisualizerPeaks
  }

  val playedStartColor = colorScheme.primary
  val playedEndColor = colorScheme.primary.copy(alpha = 0.82f)
  val unplayedColor = colorScheme.outline.copy(alpha = 0.55f)
  val baselineColor = colorScheme.outlineVariant
  val playheadColor = colorScheme.secondary
  val envelopeColor = colorScheme.tertiary
  val muteColor = colorScheme.error

  val borderColor = when {
    interactionMode == WaveformInteractionMode.ENVELOPE && enabled -> envelopeColor.copy(alpha = 0.7f)
    interactionMode == WaveformInteractionMode.MUTE && enabled -> muteColor.copy(alpha = 0.7f)
    isPlaying -> playheadColor.copy(alpha = 0.35f)
    else -> colorScheme.outlineVariant
  }

  Box(
    modifier = modifier
      .semantics { contentDescription = contentDescriptionText }
      .clip(RoundedCornerShape(14.dp))
      .background(colorScheme.surfaceContainerLow)
      .border(
        BorderStroke(
          width = if (interactionMode != WaveformInteractionMode.SCRUB && enabled) 1.5.dp else 1.dp,
          color = borderColor,
        ),
        RoundedCornerShape(14.dp),
      )
      .onSizeChanged {
        canvasWidthPx = it.width.toFloat().coerceAtLeast(1f)
        canvasHeightPx = it.height.toFloat().coerceAtLeast(1f)
      }
      .pointerInput(enabled, interactionMode, safeDurationMs, canvasWidthPx, canvasHeightPx) {
        if (!enabled) return@pointerInput
        detectTapGestures { offset ->
          val fraction = (offset.x / canvasWidthPx).coerceIn(0f, 1f)
          when (interactionMode) {
            WaveformInteractionMode.SCRUB -> {
              val targetMs = (fraction * safeDurationMs).toLong()
              currentOnScrubStart()
              currentOnScrubEnd(targetMs)
            }
            WaveformInteractionMode.ENVELOPE -> {
              val gain = VolumeEnvelopeDsp.yFractionToGain(offset.y / canvasHeightPx)
              currentOnPaintEnvelopePoint(fraction, gain)
            }
            WaveformInteractionMode.MUTE -> {
              currentOnToggleMuteTap(fraction)
            }
          }
        }
      }
      .pointerInput(enabled, interactionMode, safeDurationMs, canvasWidthPx, canvasHeightPx) {
        if (!enabled) return@pointerInput
        when (interactionMode) {
          WaveformInteractionMode.SCRUB -> {
            detectHorizontalDragGestures(
              onDragStart = { offset ->
                isDragging = true
                val fraction = (offset.x / canvasWidthPx).coerceIn(0f, 1f)
                dragFraction = fraction
                currentOnScrubStart()
                currentOnScrubToPosition((fraction * safeDurationMs).toLong())
              },
              onHorizontalDrag = { change, _ ->
                change.consume()
                val fraction = (change.position.x / canvasWidthPx).coerceIn(0f, 1f)
                dragFraction = fraction
                currentOnScrubToPosition((fraction * safeDurationMs).toLong())
              },
              onDragEnd = {
                val targetMs = (dragFraction * safeDurationMs).toLong()
                isDragging = false
                currentOnScrubEnd(targetMs)
              },
              onDragCancel = {
                val targetMs = (dragFraction * safeDurationMs).toLong()
                isDragging = false
                currentOnScrubEnd(targetMs)
              },
            )
          }
          WaveformInteractionMode.ENVELOPE -> {
            detectDragGestures(
              onDragStart = { offset ->
                val fraction = (offset.x / canvasWidthPx).coerceIn(0f, 1f)
                val gain = VolumeEnvelopeDsp.yFractionToGain(offset.y / canvasHeightPx)
                currentOnPaintEnvelopePoint(fraction, gain)
              },
              onDrag = { change, _ ->
                change.consume()
                val fraction = (change.position.x / canvasWidthPx).coerceIn(0f, 1f)
                val gain = VolumeEnvelopeDsp.yFractionToGain(change.position.y / canvasHeightPx)
                currentOnPaintEnvelopePoint(fraction, gain)
              },
            )
          }
          WaveformInteractionMode.MUTE -> {
            detectHorizontalDragGestures(
              onDragStart = { offset ->
                val fraction = (offset.x / canvasWidthPx).coerceIn(0f, 1f)
                muteDragStartFraction = fraction
                muteDragEndFraction = fraction
              },
              onHorizontalDrag = { change, _ ->
                change.consume()
                muteDragEndFraction = (change.position.x / canvasWidthPx).coerceIn(0f, 1f)
              },
              onDragEnd = {
                if (muteDragStartFraction >= 0f && muteDragEndFraction >= 0f) {
                  currentOnMuteSegment(muteDragStartFraction, muteDragEndFraction)
                }
                muteDragStartFraction = -1f
                muteDragEndFraction = -1f
              },
              onDragCancel = {
                muteDragStartFraction = -1f
                muteDragEndFraction = -1f
              },
            )
          }
        }
      }
      .padding(horizontal = 10.dp, vertical = 8.dp),
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val count = displayPeaks.size.coerceAtLeast(1)
      val slotWidth = size.width / count
      val barWidth = (slotWidth * 0.58f).coerceAtLeast(2f)
      val centerY = size.height / 2f

      // Muted segment background overlays
      envelope.mutedSegments.forEach { seg ->
        val leftX = seg.startFraction.coerceIn(0f, 1f) * size.width
        val rightX = seg.endFraction.coerceIn(0f, 1f) * size.width
        val widthPx = (rightX - leftX).coerceAtLeast(2f)
        drawRect(
          color = muteColor.copy(alpha = 0.22f),
          topLeft = Offset(leftX, 0f),
          size = Size(widthPx, size.height),
        )
        drawLine(
          color = muteColor.copy(alpha = 0.65f),
          start = Offset(leftX, 0f),
          end = Offset(leftX, size.height),
          strokeWidth = 1.5f,
        )
        drawLine(
          color = muteColor.copy(alpha = 0.65f),
          start = Offset(leftX + widthPx, 0f),
          end = Offset(leftX + widthPx, size.height),
          strokeWidth = 1.5f,
        )
      }

      // Live drag mute selection highlight
      if (muteDragStartFraction >= 0f && muteDragEndFraction >= 0f) {
        val leftX = min(muteDragStartFraction, muteDragEndFraction) * size.width
        val rightX = max(muteDragStartFraction, muteDragEndFraction) * size.width
        drawRect(
          color = muteColor.copy(alpha = 0.36f),
          topLeft = Offset(leftX, 0f),
          size = Size((rightX - leftX).coerceAtLeast(3f), size.height),
        )
      }

      // Subtle center timeline & quarter-beat ticks
      drawLine(
        color = baselineColor,
        start = Offset(0f, centerY),
        end = Offset(size.width, centerY),
        strokeWidth = 1f,
      )
      for (q in 1..3) {
        val tickX = size.width * (q / 4f)
        drawLine(
          color = baselineColor.copy(alpha = 0.65f),
          start = Offset(tickX, 2f),
          end = Offset(tickX, size.height - 2f),
          strokeWidth = 1f,
        )
      }

      val playedBrush = Brush.verticalGradient(
        colors = listOf(playedEndColor, playedStartColor, playedEndColor),
      )

      displayPeaks.forEachIndexed { index, rawAmp ->
        val barCenterFraction = (index + 0.5f) / count.toFloat()
        val distanceFromPlayhead = abs(barCenterFraction - effectiveProgress)
        val isPlayed = barCenterFraction <= effectiveProgress
        val isMutedAtBar = envelope.mutedSegments.any { it.contains(barCenterFraction) }
        val envGain = if (envelope.isEdited) {
          VolumeEnvelopeDsp.sampleEnvelopeGainAt(envelope, barCenterFraction)
        } else {
          1f
        }

        // Live harmonic shimmer near the playhead when actively playing & unmuted
        val liveBoost = if (isPlaying && !isMutedAtBar && distanceFromPlayhead < 0.08f) {
          val proximity = 1f - (distanceFromPlayhead / 0.08f)
          0.14f * proximity * ((sin(pulsePhase + index * 0.6f) + 1f) * 0.5f)
        } else {
          0f
        }

        val scaledAmp = if (isMutedAtBar) {
          0.06f
        } else {
          ((rawAmp.coerceIn(0.08f, 1f) + liveBoost) * envGain).coerceIn(0.06f, 1f)
        }
        val barHeight = max(3f, scaledAmp * (size.height * 0.92f))
        val x = index * slotWidth + (slotWidth - barWidth) / 2f
        val y = centerY - barHeight / 2f

        if (isMutedAtBar) {
          drawRoundRect(
            color = muteColor.copy(alpha = 0.52f),
            topLeft = Offset(x, y),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
          )
        } else if (isPlayed) {
          drawRoundRect(
            brush = playedBrush,
            topLeft = Offset(x, y),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
          )
        } else {
          drawRoundRect(
            color = unplayedColor,
            topLeft = Offset(x, y),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
          )
        }
      }

      // Unity 1.0x reference line + continuous volume envelope curve overlay
      if (envelope.points.isNotEmpty() || interactionMode == WaveformInteractionMode.ENVELOPE) {
        val unityY = VolumeEnvelopeDsp.gainToYFraction(VolumeEnvelopeDsp.UNITY_GAIN) * size.height
        drawLine(
          color = envelopeColor.copy(alpha = 0.35f),
          start = Offset(0f, unityY),
          end = Offset(size.width, unityY),
          strokeWidth = 1f,
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
        )

        val steps = 48
        val curvePath = Path()
        val fillPath = Path()
        for (i in 0..steps) {
          val t = i / steps.toFloat()
          val gain = VolumeEnvelopeDsp.sampleCurveOnlyAt(envelope.points, t)
          val px = t * size.width
          val py = VolumeEnvelopeDsp.gainToYFraction(gain) * size.height
          if (i == 0) {
            curvePath.moveTo(px, py)
            fillPath.moveTo(px, size.height)
            fillPath.lineTo(px, py)
          } else {
            curvePath.lineTo(px, py)
            fillPath.lineTo(px, py)
          }
        }
        fillPath.lineTo(size.width, size.height)
        fillPath.close()

        drawPath(
          path = fillPath,
          color = envelopeColor.copy(alpha = if (interactionMode == WaveformInteractionMode.ENVELOPE) 0.18f else 0.08f),
        )
        drawPath(
          path = curvePath,
          color = envelopeColor.copy(alpha = if (interactionMode == WaveformInteractionMode.ENVELOPE) 0.95f else 0.72f),
          style = Stroke(
            width = if (interactionMode == WaveformInteractionMode.ENVELOPE) 3f else 2f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
          ),
        )

        if (interactionMode == WaveformInteractionMode.ENVELOPE) {
          envelope.points.forEach { pt ->
            val px = pt.fraction.coerceIn(0f, 1f) * size.width
            val py = VolumeEnvelopeDsp.gainToYFraction(pt.gain) * size.height
            drawCircle(
              color = envelopeColor,
              radius = 5.5f,
              center = Offset(px, py),
            )
            drawCircle(
              color = Color.White,
              radius = 2.5f,
              center = Offset(px, py),
            )
          }
        }
      }

      // Playhead cursor + ambient halo synced with playback position
      if (enabled) {
        val playheadX = (effectiveProgress * size.width).coerceIn(2f, size.width - 2f)

        drawLine(
          color = playheadColor.copy(alpha = if (isPlaying) 0.30f else 0.16f),
          start = Offset(playheadX, 0f),
          end = Offset(playheadX, size.height),
          strokeWidth = 8f,
        )
        drawLine(
          color = playheadColor,
          start = Offset(playheadX, 2f),
          end = Offset(playheadX, size.height - 2f),
          strokeWidth = 2.5f,
        )
        drawCircle(
          color = playheadColor,
          radius = if (isDragging) 5f else 3.5f,
          center = Offset(playheadX, 4f),
        )
        drawCircle(
          color = playheadColor,
          radius = if (isDragging) 5f else 3.5f,
          center = Offset(playheadX, size.height - 4f),
        )
      }
    }
  }
}

private val defaultVisualizerPeaks = List(56) { idx ->
  val wave = sin(idx * 0.32).toFloat()
  (0.22f + abs(wave) * 0.48f)
}
