package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.EqPreset
import com.example.audio.EqualizerSettings
import com.example.audio.GraphicEqualizerDsp
import com.example.audio.ThreeBandEqBand
import kotlin.math.abs

/**
 * Tactile 3-Band Equalizer (Low, Mid, High) card for the Audio Player Studio.
 * Synchronizes directly with the real-time biquad DSP equalizer engine and renders a live
 * analytical frequency-response curve across 30 Hz – 16 kHz.
 */
@Composable
fun ThreeBandEqualizerCard(
  equalizer: EqualizerSettings,
  onEnabledChange: (Boolean) -> Unit,
  onSelectPreset: (EqPreset) -> Unit,
  onBandGainChange: (ThreeBandEqBand, Float) -> Unit,
  onReset: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val hasNonZeroBands = remember(equalizer.bandGainsDb) {
    equalizer.normalizedBands.any { abs(it) >= 0.1f }
  }
  val summaryText = remember(equalizer) {
    GraphicEqualizerDsp.formatThreeBandSummary(equalizer)
  }
  val curvePoints = remember(equalizer) {
    GraphicEqualizerDsp.computeFrequencyCurvePoints(equalizer, samplePoints = 48)
  }

  val borderColor by animateColorAsState(
    targetValue = if (equalizer.isActive) {
      colorScheme.primary.copy(alpha = 0.42f)
    } else {
      colorScheme.outlineVariant
    },
    animationSpec = tween(durationMillis = 220),
    label = "threeBandEqBorder",
  )

  Surface(
    shape = MaterialTheme.shapes.medium,
    color = if (equalizer.isActive) {
      colorScheme.surfaceContainerHigh
    } else {
      colorScheme.surfaceContainerLow
    },
    border = BorderStroke(1.dp, borderColor),
    modifier = modifier
      .fillMaxWidth()
      .testTag("three_band_eq_card"),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .drawBehind {
          if (equalizer.isActive) {
            drawRect(
              brush = Brush.linearGradient(
                colors = listOf(
                  colorScheme.primary.copy(alpha = 0.08f),
                  Color.Transparent,
                  colorScheme.secondary.copy(alpha = 0.06f),
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
              ),
            )
          }
        },
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        // Header row: icon, title, live balance badge, reset action, enable switch
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = if (equalizer.isActive) {
                colorScheme.primary.copy(alpha = 0.16f)
              } else {
                colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
              },
              modifier = Modifier.size(34.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Rounded.Equalizer,
                  contentDescription = null,
                  tint = if (equalizer.isActive) colorScheme.primary else colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(18.dp),
                )
              }
            }

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
              Text(
                text = stringResource(R.string.eq_3band_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = summaryText,
                style = MaterialTheme.typography.labelSmall,
                color = if (equalizer.isActive) colorScheme.primary else colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("three_band_eq_summary_label"),
              )
            }
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            if (hasNonZeroBands || equalizer.isEnabled) {
              TextButton(
                onClick = onReset,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("three_band_eq_reset_button"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Refresh,
                  contentDescription = null,
                  modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                  text = stringResource(R.string.pitch_dsp_reset),
                  style = MaterialTheme.typography.labelSmall,
                )
              }
            }

            val toggleCd = stringResource(R.string.cd_eq_3band_toggle)
            Switch(
              checked = equalizer.isEnabled,
              onCheckedChange = onEnabledChange,
              colors = SwitchDefaults.colors(
                checkedThumbColor = colorScheme.onPrimary,
                checkedTrackColor = colorScheme.primary,
              ),
              modifier = Modifier
                .semantics { contentDescription = toggleCd }
                .testTag("three_band_eq_toggle"),
            )
          }
        }

        // Real-time analytical frequency-response curve visualizer
        ThreeBandResponseCurve(
          equalizer = equalizer,
          curvePoints = curvePoints,
        )

        // Quick studio EQ presets
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          EqPreset.selectablePresets.forEach { preset ->
            val selected = if (preset == EqPreset.FLAT) {
              !equalizer.isEnabled || (equalizer.preset == EqPreset.FLAT && !hasNonZeroBands)
            } else {
              equalizer.isEnabled && equalizer.preset == preset
            }
            FilterChip(
              selected = selected,
              onClick = { onSelectPreset(preset) },
              label = {
                Text(
                  text = preset.label,
                  style = MaterialTheme.typography.labelSmall,
                  maxLines = 1,
                )
              },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = colorScheme.primary.copy(alpha = 0.2f),
                selectedLabelColor = colorScheme.primary,
              ),
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("three_band_eq_preset_${preset.name.lowercase()}"),
            )
          }
        }

        // Low / Mid / High tactile frequency balance faders
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          ThreeBandFaderRow(
            band = ThreeBandEqBand.LOW,
            bandTitle = stringResource(R.string.eq_3band_low_label),
            rangeLabel = stringResource(R.string.eq_3band_low_range),
            gainDb = equalizer.lowGainDb,
            isEqEnabled = equalizer.isEnabled,
            onGainChange = { onBandGainChange(ThreeBandEqBand.LOW, it) },
          )
          ThreeBandFaderRow(
            band = ThreeBandEqBand.MID,
            bandTitle = stringResource(R.string.eq_3band_mid_label),
            rangeLabel = stringResource(R.string.eq_3band_mid_range),
            gainDb = equalizer.midGainDb,
            isEqEnabled = equalizer.isEnabled,
            onGainChange = { onBandGainChange(ThreeBandEqBand.MID, it) },
          )
          ThreeBandFaderRow(
            band = ThreeBandEqBand.HIGH,
            bandTitle = stringResource(R.string.eq_3band_high_label),
            rangeLabel = stringResource(R.string.eq_3band_high_range),
            gainDb = equalizer.highGainDb,
            isEqEnabled = equalizer.isEnabled,
            onGainChange = { onBandGainChange(ThreeBandEqBand.HIGH, it) },
          )
        }
      }
    }
  }
}

@Composable
private fun ThreeBandResponseCurve(
  equalizer: EqualizerSettings,
  curvePoints: List<Float>,
) {
  val colorScheme = MaterialTheme.colorScheme
  val curveCd = stringResource(R.string.cd_eq_3band_curve)
  val curveStrokeColor = if (equalizer.isActive) colorScheme.primary else colorScheme.outline
  val fillTopColor = if (equalizer.isActive) {
    colorScheme.primary.copy(alpha = 0.26f)
  } else {
    colorScheme.outline.copy(alpha = 0.08f)
  }
  val fillBottomColor = if (equalizer.isActive) {
    colorScheme.tertiary.copy(alpha = 0.16f)
  } else {
    Color.Transparent
  }
  val gridColor = colorScheme.outlineVariant.copy(alpha = 0.55f)
  val dotColor = if (equalizer.isActive) colorScheme.primary else colorScheme.outline
  val dotCoreColor = colorScheme.surface

  val lowX = remember { GraphicEqualizerDsp.frequencyToCurveX(ThreeBandEqBand.LOW.centerHz) }
  val midX = remember { GraphicEqualizerDsp.frequencyToCurveX(ThreeBandEqBand.MID.centerHz) }
  val highX = remember { GraphicEqualizerDsp.frequencyToCurveX(ThreeBandEqBand.HIGH.centerHz) }
  val lowMidDividerX = remember { GraphicEqualizerDsp.frequencyToCurveX(440) }
  val midHighDividerX = remember { GraphicEqualizerDsp.frequencyToCurveX(4200) }

  Surface(
    shape = RoundedCornerShape(10.dp),
    color = colorScheme.surfaceContainerLowest.copy(alpha = 0.85f),
    border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.7f)),
    modifier = Modifier
      .fillMaxWidth()
      .height(74.dp),
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      Canvas(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 8.dp, vertical = 8.dp)
          .semantics { contentDescription = curveCd }
          .testTag("three_band_eq_curve_canvas"),
      ) {
        val w = size.width
        val h = size.height
        if (w <= 1f || h <= 1f || curvePoints.size < 2) return@Canvas

        val centerY = h * 0.5f
        val maxAmplitude = h * 0.42f

        // Subtle vertical Low / Mid / High zone dividers
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        drawLine(
          color = gridColor,
          start = Offset(w * lowMidDividerX, 0f),
          end = Offset(w * lowMidDividerX, h),
          strokeWidth = 1.dp.toPx(),
          pathEffect = dashEffect,
        )
        drawLine(
          color = gridColor,
          start = Offset(w * midHighDividerX, 0f),
          end = Offset(w * midHighDividerX, h),
          strokeWidth = 1.dp.toPx(),
          pathEffect = dashEffect,
        )

        // 0 dB reference horizontal line
        drawLine(
          color = gridColor.copy(alpha = 0.85f),
          start = Offset(0f, centerY),
          end = Offset(w, centerY),
          strokeWidth = 1.dp.toPx(),
          pathEffect = dashEffect,
        )

        val stepX = w / (curvePoints.size - 1).toFloat()
        val linePath = Path()
        val fillPath = Path()

        val firstY = centerY - curvePoints[0] * maxAmplitude
        linePath.moveTo(0f, firstY)
        fillPath.moveTo(0f, centerY)
        fillPath.lineTo(0f, firstY)

        for (i in 1 until curvePoints.size) {
          val prevX = (i - 1) * stepX
          val prevY = centerY - curvePoints[i - 1] * maxAmplitude
          val currX = i * stepX
          val currY = centerY - curvePoints[i] * maxAmplitude
          val midPtX = (prevX + currX) * 0.5f
          linePath.cubicTo(midPtX, prevY, midPtX, currY, currX, currY)
          fillPath.cubicTo(midPtX, prevY, midPtX, currY, currX, currY)
        }

        fillPath.lineTo(w, centerY)
        fillPath.close()

        drawPath(
          path = fillPath,
          brush = Brush.verticalGradient(
            colors = listOf(fillTopColor, Color.Transparent, fillBottomColor),
            startY = 0f,
            endY = h,
          ),
        )

        drawPath(
          path = linePath,
          color = curveStrokeColor,
          style = Stroke(
            width = 2.2.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
          ),
        )

        // Draw 3 anchor indicator nodes at Low / Mid / High centers
        val anchors = listOf(lowX, midX, highX)
        val lastIdx = curvePoints.lastIndex
        anchors.forEach { normX ->
          val sampleIdx = (normX * lastIdx).toInt().coerceIn(0, lastIdx)
          val cx = normX * w
          val cy = centerY - curvePoints[sampleIdx] * maxAmplitude
          drawCircle(
            color = dotColor.copy(alpha = 0.25f),
            radius = 7.dp.toPx(),
            center = Offset(cx, cy),
          )
          drawCircle(
            color = dotColor,
            radius = 4.dp.toPx(),
            center = Offset(cx, cy),
          )
          drawCircle(
            color = dotCoreColor,
            radius = 1.6.dp.toPx(),
            center = Offset(cx, cy),
          )
        }
      }

      // Micro zone labels along bottom edge
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .align(Alignment.BottomCenter)
          .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = stringResource(R.string.spectrum_zone_low),
          style = MaterialTheme.typography.labelSmall,
          color = colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
        )
        Text(
          text = stringResource(R.string.spectrum_zone_mid),
          style = MaterialTheme.typography.labelSmall,
          color = colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
        )
        Text(
          text = stringResource(R.string.spectrum_zone_high),
          style = MaterialTheme.typography.labelSmall,
          color = colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
        )
      }
    }
  }
}

@Composable
private fun ThreeBandFaderRow(
  band: ThreeBandEqBand,
  bandTitle: String,
  rangeLabel: String,
  gainDb: Float,
  isEqEnabled: Boolean,
  onGainChange: (Float) -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  val accentColor = when {
    !isEqEnabled -> colorScheme.outline
    gainDb > 0.1f -> colorScheme.primary
    gainDb < -0.1f -> colorScheme.tertiary
    else -> colorScheme.onSurfaceVariant
  }
  val resetBandCd = stringResource(R.string.cd_eq_3band_reset_band, bandTitle)

  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Column(
      modifier = Modifier.width(74.dp),
      verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
      Text(
        text = bandTitle,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = colorScheme.onSurface,
        maxLines = 1,
      )
      Text(
        text = rangeLabel,
        style = MaterialTheme.typography.labelSmall,
        color = colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    Slider(
      value = gainDb.coerceIn(GraphicEqualizerDsp.MIN_GAIN_DB, GraphicEqualizerDsp.MAX_GAIN_DB),
      onValueChange = onGainChange,
      valueRange = GraphicEqualizerDsp.MIN_GAIN_DB..GraphicEqualizerDsp.MAX_GAIN_DB,
      steps = 47, // 0.5 dB precision from -12 dB to +12 dB
      colors = SliderDefaults.colors(
        thumbColor = accentColor,
        activeTrackColor = accentColor,
        inactiveTrackColor = colorScheme.surfaceContainerHighest,
      ),
      modifier = Modifier
        .weight(1f)
        .testTag("three_band_eq_slider_${band.id}"),
    )

    Surface(
      shape = RoundedCornerShape(8.dp),
      color = if (isEqEnabled && abs(gainDb) >= 0.1f) {
        accentColor.copy(alpha = 0.14f)
      } else {
        colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
      },
      modifier = Modifier
        .minimumInteractiveComponentSize()
        .clip(RoundedCornerShape(8.dp))
        .clickable(enabled = abs(gainDb) >= 0.1f) { onGainChange(0f) }
        .semantics { contentDescription = resetBandCd }
        .testTag("three_band_eq_value_${band.id}"),
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .width(64.dp)
          .padding(horizontal = 6.dp, vertical = 6.dp),
      ) {
        Text(
          text = "${GraphicEqualizerDsp.formatGainDb(gainDb)} dB",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Medium,
          color = accentColor,
          textAlign = TextAlign.Center,
          maxLines = 1,
        )
      }
    }
  }
}
