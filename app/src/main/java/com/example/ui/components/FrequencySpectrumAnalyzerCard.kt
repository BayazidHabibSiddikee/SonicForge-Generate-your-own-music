package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.RawBufferDiagnostics
import com.example.audio.RealTimeSpectrumAnalyzer
import com.example.audio.RealTimeSpectrumSnapshot
import com.example.audio.SpectrumVisualizerMode
import kotlin.math.roundToInt

/**
 * Interactive 20-band logarithmic real-time frequency spectrum analyzer (40 Hz – 16 kHz)
 * with Bar and Filled Curve display modes, toggleable Peak-Hold caps, and Low/Mid/High
 * energy telemetry for the SonicForge studio audio player.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FrequencySpectrumAnalyzerCard(
  liveSpectrum: RealTimeSpectrumSnapshot,
  bufferDiagnostics: RawBufferDiagnostics?,
  isPlaying: Boolean,
  hasTrack: Boolean,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  var visualMode by rememberSaveable { mutableStateOf(SpectrumVisualizerMode.BARS) }
  var peakHoldEnabled by rememberSaveable { mutableStateOf(true) }

  val isLiveActive = isPlaying && liveSpectrum.isActive && liveSpectrum.bandLevels.isNotEmpty()
  val hasStaticBins = hasTrack && bufferDiagnostics != null && bufferDiagnostics.fourierSpectrumBins.isNotEmpty()

  val displayBands: List<Float> = when {
    isLiveActive -> liveSpectrum.bandLevels
    hasStaticBins -> RealTimeSpectrumAnalyzer.resampleStaticBins(
      staticBins = bufferDiagnostics!!.fourierSpectrumBins,
      targetCount = RealTimeSpectrumAnalyzer.BAND_COUNT,
    )
    else -> List(RealTimeSpectrumAnalyzer.BAND_COUNT) { 0.04f }
  }

  val peakBands: List<Float> = if (isLiveActive && liveSpectrum.peakHoldLevels.size == displayBands.size) {
    liveSpectrum.peakHoldLevels
  } else {
    displayBands
  }

  val lowEnergy = when {
    isLiveActive -> liveSpectrum.lowEnergy
    hasStaticBins -> displayBands.subList(0, 6).average().toFloat().coerceIn(0f, 1f)
    else -> 0f
  }
  val midEnergy = when {
    isLiveActive -> liveSpectrum.midEnergy
    hasStaticBins -> displayBands.subList(6, 14).average().toFloat().coerceIn(0f, 1f)
    else -> 0f
  }
  val highEnergy = when {
    isLiveActive -> liveSpectrum.highEnergy
    hasStaticBins -> displayBands.subList(14, displayBands.size).average().toFloat().coerceIn(0f, 1f)
    else -> 0f
  }

  val diagCentroidHz = bufferDiagnostics?.spectralCentroidHz ?: 0
  val peakHz = when {
    isLiveActive && liveSpectrum.peakFrequencyHz > 0 -> liveSpectrum.peakFrequencyHz
    hasTrack && diagCentroidHz > 0 -> diagCentroidHz
    else -> 0
  }
  val centroidHz = when {
    isLiveActive && liveSpectrum.spectralCentroidHz > 0 -> liveSpectrum.spectralCentroidHz
    hasTrack && diagCentroidHz > 0 -> diagCentroidHz
    else -> 0
  }

  val statusLabel = when {
    isLiveActive -> stringResource(R.string.spectrum_live_fft_badge)
    hasStaticBins -> stringResource(R.string.spectrum_static_fft_badge)
    else -> stringResource(R.string.spectrum_idle_badge)
  }

  val lowColor = colorScheme.secondary
  val midColor = colorScheme.primary
  val highColor = colorScheme.tertiary
  val slotBgColor = colorScheme.surfaceContainerHigh
  val gridLineColor = colorScheme.outlineVariant.copy(alpha = 0.55f)
  val peakCapColor = colorScheme.onSurface
  val canvasDescription = stringResource(R.string.cd_spectrum_canvas)

  Surface(
    shape = MaterialTheme.shapes.medium,
    color = if (isLiveActive) {
      colorScheme.primaryContainer.copy(alpha = 0.24f)
    } else {
      colorScheme.surfaceContainerLow
    },
    border = BorderStroke(
      width = 1.dp,
      color = if (isLiveActive) colorScheme.primary.copy(alpha = 0.5f) else colorScheme.outlineVariant,
    ),
    modifier = modifier
      .fillMaxWidth()
      .testTag("player_spectrum_analyzer_card"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Header row: title + mode badge + visualization controls
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector = Icons.Rounded.GraphicEq,
            contentDescription = null,
            tint = if (isLiveActive) colorScheme.primary else colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = stringResource(R.string.spectrum_analyzer_title),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Surface(
            shape = MaterialTheme.shapes.small,
            color = when {
              isLiveActive -> colorScheme.primaryContainer
              hasStaticBins -> colorScheme.secondaryContainer
              else -> colorScheme.surfaceContainerHigh
            },
            modifier = Modifier.testTag("spectrum_mode_badge"),
          ) {
            Text(
              text = statusLabel,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = when {
                isLiveActive -> colorScheme.onPrimaryContainer
                hasStaticBins -> colorScheme.onSecondaryContainer
                else -> colorScheme.onSurfaceVariant
              },
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
        }
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        FilterChip(
          selected = visualMode == SpectrumVisualizerMode.CURVE,
          onClick = {
            visualMode = if (visualMode == SpectrumVisualizerMode.BARS) {
              SpectrumVisualizerMode.CURVE
            } else {
              SpectrumVisualizerMode.BARS
            }
          },
          leadingIcon = {
            Icon(
              imageVector = if (visualMode == SpectrumVisualizerMode.CURVE) {
                Icons.AutoMirrored.Rounded.ShowChart
              } else {
                Icons.Rounded.GraphicEq
              },
              contentDescription = null,
              modifier = Modifier.size(15.dp),
            )
          },
          label = {
            Text(
              text = if (visualMode == SpectrumVisualizerMode.BARS) {
                stringResource(R.string.spectrum_style_bars)
              } else {
                stringResource(R.string.spectrum_style_curve)
              },
              style = MaterialTheme.typography.labelSmall,
            )
          },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colorScheme.secondaryContainer,
            selectedLabelColor = colorScheme.onSecondaryContainer,
          ),
          modifier = Modifier.testTag("spectrum_style_toggle"),
        )

        FilterChip(
          selected = peakHoldEnabled,
          onClick = { peakHoldEnabled = !peakHoldEnabled },
          leadingIcon = {
            Icon(
              imageVector = Icons.Rounded.VerticalAlignTop,
              contentDescription = null,
              modifier = Modifier.size(15.dp),
            )
          },
          label = {
            Text(
              text = stringResource(R.string.spectrum_peak_hold),
              style = MaterialTheme.typography.labelSmall,
            )
          },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colorScheme.tertiaryContainer,
            selectedLabelColor = colorScheme.onTertiaryContainer,
          ),
          modifier = Modifier.testTag("spectrum_peak_hold_toggle"),
        )
      }

      // 20-Band Frequency Spectrum Canvas
      Surface(
        shape = MaterialTheme.shapes.small,
        color = colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.7f)),
      ) {
        Canvas(
          modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .semantics { contentDescription = canvasDescription }
            .testTag("player_spectrum_canvas"),
        ) {
          val w = size.width
          val h = size.height
          if (w <= 0f || h <= 0f) return@Canvas

          // Reference horizontal dB grid lines (-20 dBFS & -40 dBFS)
          val topGridY = h * 0.333f
          val bottomGridY = h * 0.667f
          drawLine(
            color = gridLineColor,
            start = Offset(0f, topGridY),
            end = Offset(w, topGridY),
            strokeWidth = 1.dp.toPx(),
          )
          drawLine(
            color = gridLineColor,
            start = Offset(0f, bottomGridY),
            end = Offset(w, bottomGridY),
            strokeWidth = 1.dp.toPx(),
          )

          val bandCount = displayBands.size.coerceAtLeast(1)
          val slotWidth = w / bandCount.toFloat()

          if (visualMode == SpectrumVisualizerMode.BARS) {
            val barWidth = (slotWidth * 0.66f).coerceAtLeast(3.dp.toPx())
            val corner = CornerRadius(barWidth / 2.4f, barWidth / 2.4f)
            val capHeight = 2.dp.toPx()

            displayBands.forEachIndexed { idx, rawAmp ->
              val amp = rawAmp.coerceIn(0f, 1f)
              val x = idx * slotWidth + (slotWidth - barWidth) / 2f
              drawRoundRect(
                color = slotBgColor.copy(alpha = 0.55f),
                topLeft = Offset(x, 0f),
                size = Size(barWidth, h),
                cornerRadius = corner,
              )

              val barHeight = (h * amp).coerceAtLeast(if (hasTrack) 2.5.dp.toPx() else 2.dp.toPx())
              val topY = (h - barHeight).coerceIn(0f, h)
              val bandColor = when {
                idx < 6 -> lowColor
                idx < 14 -> midColor
                else -> highColor
              }

              drawRoundRect(
                brush = Brush.verticalGradient(
                  colors = listOf(bandColor, bandColor.copy(alpha = 0.45f)),
                  startY = topY,
                  endY = h,
                ),
                topLeft = Offset(x, topY),
                size = Size(barWidth, barHeight),
                cornerRadius = corner,
              )

              if (peakHoldEnabled && isLiveActive) {
                val peakAmp = peakBands.getOrElse(idx) { amp }.coerceIn(0f, 1f)
                if (peakAmp > 0.03f) {
                  val peakY = (h * (1f - peakAmp)).coerceIn(0f, h - capHeight)
                  drawRoundRect(
                    color = peakCapColor.copy(alpha = 0.85f),
                    topLeft = Offset(x, peakY),
                    size = Size(barWidth, capHeight),
                    cornerRadius = CornerRadius(capHeight / 2f, capHeight / 2f),
                  )
                }
              }
            }
          } else {
            val points = List(bandCount) { idx ->
              val x = idx * slotWidth + slotWidth / 2f
              val amp = displayBands[idx].coerceIn(0f, 1f)
              val y = (h * (1f - amp)).coerceIn(1.dp.toPx(), h - 1.dp.toPx())
              Offset(x, y)
            }

            if (points.size >= 2) {
              val strokePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 0 until points.lastIndex) {
                  val p0 = points[i]
                  val p1 = points[i + 1]
                  val midX = (p0.x + p1.x) / 2f
                  cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                }
              }
              val fillPath = Path().apply {
                addPath(strokePath)
                lineTo(points.last().x, h)
                lineTo(points.first().x, h)
                close()
              }

              drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                  colors = listOf(
                    midColor.copy(alpha = 0.42f),
                    lowColor.copy(alpha = 0.14f),
                  ),
                  startY = 0f,
                  endY = h,
                ),
              )

              drawPath(
                path = strokePath,
                brush = Brush.horizontalGradient(
                  colors = listOf(lowColor, midColor, highColor),
                ),
                style = Stroke(
                  width = 2.4.dp.toPx(),
                  cap = StrokeCap.Round,
                  join = StrokeJoin.Round,
                ),
              )

              if (peakHoldEnabled && isLiveActive) {
                val peakPath = Path().apply {
                  peakBands.forEachIndexed { idx, pAmp ->
                    val x = idx * slotWidth + slotWidth / 2f
                    val y = (h * (1f - pAmp.coerceIn(0f, 1f))).coerceIn(1.dp.toPx(), h - 1.dp.toPx())
                    if (idx == 0) moveTo(x, y) else lineTo(x, y)
                  }
                }
                drawPath(
                  path = peakPath,
                  color = peakCapColor.copy(alpha = 0.65f),
                  style = Stroke(
                    width = 1.2.dp.toPx(),
                    cap = StrokeCap.Round,
                  ),
                )
              }
            }
          }
        }
      }

      // Logarithmic frequency axis ticks
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        val ticks = listOf("40 Hz", "160 Hz", "640 Hz", "2.5 kHz", "16 kHz")
        ticks.forEach { tick ->
          Text(
            text = tick,
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
          )
        }
      }

      // Low / Mid / High energy distribution + Peak / Centroid telemetry
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("spectrum_zone_summary"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          SpectrumZonePill(
            label = stringResource(R.string.spectrum_zone_low),
            percent = (lowEnergy * 100f).roundToInt(),
            accentColor = lowColor,
          )
          SpectrumZonePill(
            label = stringResource(R.string.spectrum_zone_mid),
            percent = (midEnergy * 100f).roundToInt(),
            accentColor = midColor,
          )
          SpectrumZonePill(
            label = stringResource(R.string.spectrum_zone_high),
            percent = (highEnergy * 100f).roundToInt(),
            accentColor = highColor,
          )
        }

        val centroidText = if (peakHz > 0 && isLiveActive) {
          stringResource(R.string.spectrum_peak_freq_label, RealTimeSpectrumAnalyzer.formatFreqHz(peakHz))
        } else {
          stringResource(R.string.spectrum_centroid_label, RealTimeSpectrumAnalyzer.formatFreqHz(centroidHz))
        }
        Text(
          text = centroidText,
          style = MaterialTheme.typography.labelSmall,
          color = colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.testTag("spectrum_centroid_badge"),
        )
      }
    }
  }
}

@Composable
private fun SpectrumZonePill(
  label: String,
  percent: Int,
  accentColor: androidx.compose.ui.graphics.Color,
) {
  Surface(
    shape = MaterialTheme.shapes.small,
    color = accentColor.copy(alpha = 0.14f),
    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
  ) {
    Text(
      text = "$label ${percent.coerceIn(0, 100)}%",
      style = MaterialTheme.typography.labelSmall,
      color = accentColor,
      fontWeight = FontWeight.Medium,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
    )
  }
}
