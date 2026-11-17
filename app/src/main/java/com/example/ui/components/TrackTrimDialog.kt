package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.GeneratedTrack
import kotlin.math.max
import kotlin.math.roundToLong

@Composable
fun TrackTrimDialog(
  track: GeneratedTrack,
  isTrimming: Boolean,
  onDismiss: () -> Unit,
  onConfirmTrim: (startMs: Long, endMs: Long, saveAsCopy: Boolean) -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  val totalMs = track.durationMs.coerceAtLeast(1_000L)
  val minCropMs = (totalMs / 10L).coerceIn(250L, 1_000L)

  var selectionRange by remember(track.id) {
    mutableStateOf(0f..totalMs.toFloat())
  }
  var saveAsCopy by remember(track.id) {
    mutableStateOf(true)
  }

  val startMs = selectionRange.start.roundToLong().coerceIn(0L, totalMs - minCropMs)
  val endMs = selectionRange.endInclusive.roundToLong().coerceIn(startMs + minCropMs, totalMs)
  val croppedDurationMs = (endMs - startMs).coerceAtLeast(minCropMs)

  val startFraction = (startMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
  val endFraction = (endMs.toFloat() / totalMs.toFloat()).coerceIn(startFraction, 1f)

  val activeColor = colorScheme.primary
  val dimColor = colorScheme.surfaceContainerHighest
  val markerColor = colorScheme.secondary
  val shadeColor = colorScheme.surface.copy(alpha = 0.55f)

  AlertDialog(
    onDismissRequest = { if (!isTrimming) onDismiss() },
    containerColor = colorScheme.surfaceContainer,
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.ContentCut,
          contentDescription = null,
          tint = colorScheme.primary,
          modifier = Modifier.size(20.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = stringResource(R.string.trim_dialog_title),
            style = MaterialTheme.typography.titleLarge,
          )
          Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("track_trim_dialog"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Surface(
          shape = MaterialTheme.shapes.medium,
          color = colorScheme.surfaceContainerLow,
          border = BorderStroke(1.dp, colorScheme.outlineVariant),
        ) {
          Canvas(
            modifier = Modifier
              .fillMaxWidth()
              .height(72.dp)
              .padding(horizontal = 10.dp, vertical = 10.dp)
              .testTag("trim_waveform_preview"),
          ) {
            val peaks = track.waveformPeaks.ifEmpty { List(48) { 0.4f } }
            val count = peaks.size
            val slotW = size.width / count.toFloat()
            val barW = (slotW * 0.62f).coerceAtLeast(2.dp.toPx())
            val radius = CornerRadius(barW / 2f, barW / 2f)
            val centerY = size.height / 2f

            peaks.forEachIndexed { idx, amp ->
              val barCenterFrac = (idx + 0.5f) / count.toFloat()
              val inSelection = barCenterFrac in startFraction..endFraction
              val barH = (size.height * amp.coerceIn(0.12f, 1f)).coerceAtLeast(4.dp.toPx())
              val x = idx * slotW + (slotW - barW) / 2f
              drawRoundRect(
                color = if (inSelection) activeColor else dimColor,
                topLeft = Offset(x, centerY - barH / 2f),
                size = Size(barW, barH),
                cornerRadius = radius,
              )
            }

            val startX = startFraction * size.width
            val endX = endFraction * size.width
            if (startX > 0f) {
              drawRect(color = shadeColor, topLeft = Offset.Zero, size = Size(startX, size.height))
            }
            if (endX < size.width) {
              drawRect(
                color = shadeColor,
                topLeft = Offset(endX, 0f),
                size = Size(size.width - endX, size.height),
              )
            }

            drawLine(
              color = markerColor,
              start = Offset(startX, 0f),
              end = Offset(startX, size.height),
              strokeWidth = 2.5.dp.toPx(),
            )
            drawLine(
              color = markerColor,
              start = Offset(endX, 0f),
              end = Offset(endX, size.height),
              strokeWidth = 2.5.dp.toPx(),
            )
          }
        }

        RangeSlider(
          value = selectionRange,
          onValueChange = { raw ->
            val clampedStart = raw.start.coerceIn(0f, max(0f, totalMs - minCropMs.toFloat()))
            val clampedEnd = raw.endInclusive.coerceIn(clampedStart + minCropMs.toFloat(), totalMs.toFloat())
            selectionRange = clampedStart..clampedEnd
          },
          valueRange = 0f..totalMs.toFloat(),
          enabled = !isTrimming,
          colors = SliderDefaults.colors(
            thumbColor = colorScheme.secondary,
            activeTrackColor = colorScheme.primary,
            inactiveTrackColor = colorScheme.surfaceContainerHigh,
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("trim_range_slider"),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = "Start: ${formatDurationMs(startMs)}",
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
          )
          Text(
            text = "Crop: ${formatDurationMs(croppedDurationMs)}",
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.primary,
            modifier = Modifier.testTag("trim_duration_label"),
          )
          Text(
            text = "End: ${formatDurationMs(endMs)}",
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = stringResource(R.string.trim_save_copy_label),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurface,
            modifier = Modifier.weight(1f),
          )
          Switch(
            checked = saveAsCopy,
            enabled = !isTrimming,
            onCheckedChange = { saveAsCopy = it },
            modifier = Modifier.testTag("trim_save_copy_switch"),
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirmTrim(startMs, endMs, saveAsCopy) },
        enabled = !isTrimming,
        modifier = Modifier.testTag("confirm_trim_button"),
      ) {
        if (isTrimming) {
          CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = colorScheme.onPrimary,
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.trim_processing_button))
        } else {
          Text(stringResource(R.string.trim_apply_button))
        }
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        enabled = !isTrimming,
      ) {
        Text(stringResource(R.string.edit_org_cancel_button))
      }
    },
  )
}
