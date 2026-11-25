package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.SeamlessLoopDsp
import com.example.audio.TrackLoopMode
import com.example.audio.TrackLoopSettings

/**
 * Dedicated Seamless Loop Control panel inside the Audio Player Studio.
 * Provides instant switching between Off, Loop Track (seamless full-track repeat), and
 * Loop Selection (seamless A–B region looping with precision RangeSlider, Set A / Set B punch
 * buttons, and quick section presets).
 */
@Composable
fun TrackLoopControlCard(
  loopSettings: TrackLoopSettings,
  durationMs: Long,
  enabled: Boolean,
  onSelectLoopMode: (TrackLoopMode) -> Unit,
  onSelectionRangeChange: (startFraction: Float, endFraction: Float) -> Unit,
  onSetLoopStartAtPlayhead: () -> Unit,
  onSetLoopEndAtPlayhead: () -> Unit,
  onResetLoopSelection: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val normalized = loopSettings.normalized()
  val isActive = enabled && normalized.isLooping

  val borderColor by animateColorAsState(
    targetValue = if (isActive) {
      colorScheme.secondary.copy(alpha = 0.45f)
    } else {
      colorScheme.outlineVariant
    },
    animationSpec = tween(durationMillis = 220),
    label = "loopCardBorder",
  )

  val statusText = when {
    !enabled -> stringResource(R.string.loop_status_off)
    normalized.isLoopTrack -> stringResource(R.string.loop_status_full_track)
    normalized.isLoopSelection -> {
      val span = SeamlessLoopDsp.formatSpanSeconds(normalized.selectionDurationMs(durationMs))
      stringResource(R.string.loop_status_selection_fmt, span)
    }
    else -> stringResource(R.string.loop_status_off)
  }

  Surface(
    shape = MaterialTheme.shapes.medium,
    color = if (isActive) {
      colorScheme.surfaceContainerHigh
    } else {
      colorScheme.surfaceContainerLow
    },
    border = BorderStroke(1.dp, borderColor),
    modifier = modifier
      .fillMaxWidth()
      .testTag("track_loop_control_card"),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .drawBehind {
          if (isActive) {
            drawRect(
              brush = Brush.linearGradient(
                colors = listOf(
                  colorScheme.secondary.copy(alpha = 0.09f),
                  Color.Transparent,
                  colorScheme.primary.copy(alpha = 0.06f),
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
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        // Header row: icon, title, seamless badge
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
              color = if (isActive) {
                colorScheme.secondary.copy(alpha = 0.16f)
              } else {
                colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
              },
              modifier = Modifier.size(32.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = when (normalized.mode) {
                    TrackLoopMode.LOOP_TRACK -> Icons.Rounded.RepeatOne
                    TrackLoopMode.LOOP_SELECTION -> Icons.Rounded.Loop
                    TrackLoopMode.OFF -> Icons.Rounded.Repeat
                  },
                  contentDescription = null,
                  tint = if (isActive) colorScheme.secondary else colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(18.dp),
                )
              }
            }

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
              Text(
                text = stringResource(R.string.loop_card_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) colorScheme.secondary else colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("track_loop_status_label"),
              )
            }
          }

          if (normalized.isLoopSelection && enabled) {
            TextButton(
              onClick = onResetLoopSelection,
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("loop_reset_selection_button"),
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
        }

        // 3-way Segmented Mode Selector: Off | Loop Track | Loop Selection
        val modes = listOf(
          TrackLoopMode.OFF to stringResource(R.string.loop_mode_off_label),
          TrackLoopMode.LOOP_TRACK to stringResource(R.string.loop_mode_track_label),
          TrackLoopMode.LOOP_SELECTION to stringResource(R.string.loop_mode_selection_label),
        )
        SingleChoiceSegmentedButtonRow(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("loop_mode_segmented_row"),
        ) {
          modes.forEachIndexed { index, (mode, label) ->
            val tag = when (mode) {
              TrackLoopMode.OFF -> "loop_mode_off"
              TrackLoopMode.LOOP_TRACK -> "loop_mode_track"
              TrackLoopMode.LOOP_SELECTION -> "loop_mode_selection"
            }
            SegmentedButton(
              selected = normalized.mode == mode,
              onClick = { onSelectLoopMode(mode) },
              enabled = enabled,
              shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
              colors = SegmentedButtonDefaults.colors(
                activeContainerColor = colorScheme.secondary.copy(alpha = 0.2f),
                activeContentColor = colorScheme.secondary,
              ),
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag(tag),
            ) {
              Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }

        // Expandable A–B Loop Selection controls when Loop Selection mode is active
        AnimatedVisibility(
          visible = normalized.isLoopSelection && enabled,
          enter = expandVertically(tween(200)) + fadeIn(tween(180)),
          exit = shrinkVertically(tween(180)) + fadeOut(tween(140)),
        ) {
          val startMs = normalized.selectionStartMs(durationMs)
          val endMs = normalized.selectionEndMs(durationMs)
          val spanMs = normalized.selectionDurationMs(durationMs)

          Column(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("loop_selection_controls"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            // A–B Timecode Summary Row
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = colorScheme.secondary.copy(alpha = 0.14f),
              ) {
                Text(
                  text = stringResource(
                    R.string.loop_point_a_fmt,
                    SeamlessLoopDsp.formatLoopTime(startMs),
                  ),
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.SemiBold,
                  color = colorScheme.secondary,
                  modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("loop_point_a_label"),
                )
              }

              Text(
                text = SeamlessLoopDsp.formatSpanSeconds(spanMs),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("loop_span_label"),
              )

              Surface(
                shape = RoundedCornerShape(8.dp),
                color = colorScheme.secondary.copy(alpha = 0.14f),
              ) {
                Text(
                  text = stringResource(
                    R.string.loop_point_b_fmt,
                    SeamlessLoopDsp.formatLoopTime(endMs),
                  ),
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.SemiBold,
                  color = colorScheme.secondary,
                  modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("loop_point_b_label"),
                )
              }
            }

            // Dual-thumb RangeSlider synced with the waveform A–B overlay
            RangeSlider(
              value = normalized.selectionStartFraction..normalized.selectionEndFraction,
              onValueChange = { range ->
                val rawStart = range.start
                val rawEnd = range.endInclusive
                if (rawEnd - rawStart >= SeamlessLoopDsp.MIN_SELECTION_FRACTION) {
                  onSelectionRangeChange(rawStart, rawEnd)
                }
              },
              valueRange = 0f..1f,
              colors = SliderDefaults.colors(
                thumbColor = colorScheme.secondary,
                activeTrackColor = colorScheme.secondary,
                inactiveTrackColor = colorScheme.surfaceContainerHighest,
              ),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("loop_selection_range_slider"),
            )

            // Studio Punch-In / Punch-Out + Quick Section Presets
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              FilledTonalButton(
                onClick = onSetLoopStartAtPlayhead,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                  .weight(1f)
                  .minimumInteractiveComponentSize()
                  .testTag("loop_set_a_button"),
              ) {
                Text(
                  text = stringResource(R.string.loop_set_a_button),
                  style = MaterialTheme.typography.labelSmall,
                  maxLines = 1,
                )
              }

              FilledTonalButton(
                onClick = onSetLoopEndAtPlayhead,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                  .weight(1f)
                  .minimumInteractiveComponentSize()
                  .testTag("loop_set_b_button"),
              ) {
                Text(
                  text = stringResource(R.string.loop_set_b_button),
                  style = MaterialTheme.typography.labelSmall,
                  maxLines = 1,
                )
              }
            }

            // Quick section span chips
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              val presets = listOf(
                Triple(stringResource(R.string.loop_preset_first_half), 0f to 0.5f, "first_half"),
                Triple(stringResource(R.string.loop_preset_middle), 0.25f to 0.75f, "middle"),
                Triple(stringResource(R.string.loop_preset_second_half), 0.5f to 1f, "second_half"),
                Triple(stringResource(R.string.loop_preset_full), 0f to 1f, "full"),
              )
              presets.forEach { (label, bounds, id) ->
                AssistChip(
                  onClick = { onSelectionRangeChange(bounds.first, bounds.second) },
                  label = {
                    Text(
                      text = label,
                      style = MaterialTheme.typography.labelSmall,
                      maxLines = 1,
                    )
                  },
                  colors = AssistChipDefaults.assistChipColors(
                    labelColor = colorScheme.onSurfaceVariant,
                  ),
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("loop_preset_$id"),
                )
              }
            }
          }
        }
      }
    }
  }
}
