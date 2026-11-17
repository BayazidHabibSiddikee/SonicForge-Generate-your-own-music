package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.SequencerLane
import com.example.audio.SequencerPreset
import com.example.audio.StepSequencerDsp
import com.example.audio.StepSequencerState

/**
 * Interactive 16-step grid-based drum & rhythmic sequence programmer with real-time loop playback,
 * preset grooves, groove swing, WAV library rendering, and live BPM sync to SonicForge's detected
 * track/stream tempo.
 */
@Composable
fun StepSequencerCard(
  state: StepSequencerState,
  onToggleStep: (SequencerLane, Int) -> Unit,
  onTogglePlayStop: () -> Unit,
  onClearPattern: () -> Unit,
  onApplyPreset: (SequencerPreset) -> Unit,
  onStepBpm: (Int) -> Unit,
  onToggleBpmSync: () -> Unit,
  onCycleSwing: () -> Unit,
  onRenderToLibrary: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val gridScrollState = rememberScrollState()

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("step_sequencer_card"),
    shape = MaterialTheme.shapes.large,
    color = colorScheme.surfaceContainer,
    tonalElevation = 4.dp,
    border = BorderStroke(
      width = 1.dp,
      color = if (state.isPlaying) colorScheme.primary.copy(alpha = 0.6f) else colorScheme.outline,
    ),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // 1. Header & Transport Actions
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.GridView,
            contentDescription = null,
            tint = if (state.isPlaying) colorScheme.primary else colorScheme.secondary,
            modifier = Modifier.size(20.dp),
          )
          Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
              text = stringResource(R.string.sequencer_title),
              style = MaterialTheme.typography.titleMedium,
              color = colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = stringResource(R.string.sequencer_subtitle, state.activeStepCount),
              style = MaterialTheme.typography.labelSmall,
              color = colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          OutlinedButton(
            onClick = onRenderToLibrary,
            enabled = state.activeStepCount > 0 && !state.isRenderingToLibrary,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("sequencer_render_track_button"),
          ) {
            if (state.isRenderingToLibrary) {
              CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = stringResource(R.string.sequencer_rendering),
                style = MaterialTheme.typography.labelSmall,
              )
            } else {
              Icon(
                imageVector = Icons.Rounded.Save,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = stringResource(R.string.sequencer_render_wav),
                style = MaterialTheme.typography.labelSmall,
              )
            }
          }

          FilledIconButton(
            onClick = onTogglePlayStop,
            colors = IconButtonDefaults.filledIconButtonColors(
              containerColor = if (state.isPlaying) colorScheme.tertiary else colorScheme.primary,
              contentColor = if (state.isPlaying) colorScheme.onTertiary else colorScheme.onPrimary,
            ),
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("sequencer_play_pause_button"),
          ) {
            Icon(
              imageVector = if (state.isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
              contentDescription = if (state.isPlaying) {
                stringResource(R.string.cd_sequencer_stop)
              } else {
                stringResource(R.string.cd_sequencer_play)
              },
            )
          }
        }
      }

      // 2. Tempo Control & Detected BPM Sync Bar
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          IconButton(
            onClick = { onStepBpm(-2) },
            enabled = state.bpm > StepSequencerDsp.MIN_BPM,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("sequencer_bpm_down"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Remove,
              contentDescription = stringResource(R.string.cd_sequencer_bpm_down),
              tint = colorScheme.onSurfaceVariant,
            )
          }

          Text(
            text = stringResource(R.string.sequencer_bpm_value, state.bpm),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (state.isBpmSynced) colorScheme.secondary else colorScheme.primary,
            modifier = Modifier
              .padding(horizontal = 2.dp)
              .testTag("sequencer_bpm_value"),
          )

          IconButton(
            onClick = { onStepBpm(2) },
            enabled = state.bpm < StepSequencerDsp.MAX_BPM,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("sequencer_bpm_up"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Add,
              contentDescription = stringResource(R.string.cd_sequencer_bpm_up),
              tint = colorScheme.onSurfaceVariant,
            )
          }

          val syncLabel = when {
            state.detectedAppBpm > 0 && state.isBpmSynced -> {
              val source = state.bpmSyncSourceLabel.ifBlank { "App" }
              stringResource(R.string.sequencer_sync_active, state.detectedAppBpm, source)
            }
            state.detectedAppBpm > 0 ->
              stringResource(R.string.sequencer_sync_available, state.detectedAppBpm)
            else -> stringResource(R.string.sequencer_sync_waiting)
          }

          FilterChip(
            selected = state.isBpmSynced,
            onClick = onToggleBpmSync,
            leadingIcon = {
              Icon(
                imageVector = Icons.Rounded.Sync,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
            },
            label = {
              Text(
                text = syncLabel,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
              )
            },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = colorScheme.secondaryContainer,
              selectedLabelColor = colorScheme.onSecondaryContainer,
              selectedLeadingIconColor = colorScheme.onSecondaryContainer,
            ),
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("sequencer_bpm_sync_chip"),
          )

          FilterChip(
            selected = state.swingPercent > 0,
            onClick = onCycleSwing,
            leadingIcon = {
              Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
              )
            },
            label = {
              Text(
                text = stringResource(R.string.sequencer_swing_label, state.swingPercent),
                style = MaterialTheme.typography.labelSmall,
              )
            },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = colorScheme.tertiaryContainer,
              selectedLabelColor = colorScheme.onTertiaryContainer,
            ),
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("sequencer_swing_chip"),
          )
        }
      }

      // 3. Preset Grooves & Clear Action
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        SequencerPreset.entries.forEach { preset ->
          FilterChip(
            selected = state.selectedPresetName == preset.label,
            onClick = { onApplyPreset(preset) },
            label = {
              Text(
                text = preset.label,
                style = MaterialTheme.typography.labelSmall,
              )
            },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = colorScheme.primaryContainer,
              selectedLabelColor = colorScheme.onPrimaryContainer,
            ),
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("sequencer_preset_${preset.id}"),
          )
        }

        TextButton(
          onClick = onClearPattern,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("sequencer_clear_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.ClearAll,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(3.dp))
          Text(
            text = stringResource(R.string.sequencer_clear),
            style = MaterialTheme.typography.labelSmall,
          )
        }
      }

      // 4. 6x16 Interactive Step Matrix (fixed lane labels + horizontally aligned 16-step grid)
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
      ) {
        // Fixed left column for instrument lane titles
        Column(
          modifier = Modifier.width(62.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(22.dp),
            contentAlignment = Alignment.CenterStart,
          ) {
            Text(
              text = "LANE",
              style = MaterialTheme.typography.labelSmall,
              color = colorScheme.onSurfaceVariant,
            )
          }

          SequencerLane.entries.forEach { lane ->
            val accent = laneAccentColor(lane)
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              Box(
                modifier = Modifier
                  .size(7.dp)
                  .clip(CircleShape)
                  .background(accent),
              )
              Text(
                text = lane.shortLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Clip,
              )
            }
          }
        }

        // Horizontally aligned 16-step matrix grouped into 4 quarter-note beats
        Column(
          modifier = Modifier
            .weight(1f)
            .horizontalScroll(gridScrollState),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          // Step ruler (1..16 + active playhead indicator)
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            for (step in 0 until StepSequencerDsp.STEP_COUNT) {
              if (step > 0 && step % 4 == 0) {
                Spacer(modifier = Modifier.width(6.dp))
              }
              val isPlayhead = state.isPlaying && state.currentStep == step
              val isBeatDownbeat = step % 4 == 0
              Box(
                modifier = Modifier
                  .width(36.dp)
                  .height(22.dp)
                  .clip(RoundedCornerShape(6.dp))
                  .background(
                    when {
                      isPlayhead -> colorScheme.primary
                      isBeatDownbeat -> colorScheme.surfaceContainerHighest
                      else -> Color.Transparent
                    }
                  ),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = "${step + 1}",
                  style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isPlayhead || isBeatDownbeat) FontWeight.Bold else FontWeight.Normal,
                  ),
                  color = when {
                    isPlayhead -> colorScheme.onPrimary
                    isBeatDownbeat -> colorScheme.onSurface
                    else -> colorScheme.onSurfaceVariant
                  },
                  textAlign = TextAlign.Center,
                )
              }
            }
          }

          // 6 instrument lanes x 16 step pads
          SequencerLane.entries.forEach { lane ->
            val laneAccent = laneAccentColor(lane)
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              for (step in 0 until StepSequencerDsp.STEP_COUNT) {
                if (step > 0 && step % 4 == 0) {
                  Spacer(modifier = Modifier.width(6.dp))
                }
                val isActive = state.isStepActive(lane, step)
                val isPlayhead = state.isPlaying && state.currentStep == step
                val isQuarterGroupAlt = (step / 4) % 2 == 1
                val cellCd = stringResource(
                  if (isActive) R.string.sequencer_cell_active_cd else R.string.sequencer_cell_inactive_cd,
                  lane.label,
                  step + 1,
                )

                val bgColor by animateColorAsState(
                  targetValue = when {
                    isActive && isPlayhead -> laneAccent
                    isActive -> laneAccent.copy(alpha = 0.84f)
                    isPlayhead -> colorScheme.surfaceContainerHighest
                    isQuarterGroupAlt -> colorScheme.surfaceContainerHigh
                    else -> colorScheme.surfaceContainerLow
                  },
                  label = "seqPadColor",
                )

                val borderColor = when {
                  isPlayhead -> colorScheme.onSurface
                  isActive -> laneAccent
                  else -> colorScheme.outlineVariant
                }

                Box(
                  modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .border(
                      width = if (isPlayhead) 1.5.dp else 1.dp,
                      color = borderColor,
                      shape = RoundedCornerShape(8.dp),
                    )
                    .clickable { onToggleStep(lane, step) }
                    .semantics { contentDescription = cellCd }
                    .testTag("seq_cell_${lane.id}_$step"),
                  contentAlignment = Alignment.Center,
                ) {
                  if (isActive) {
                    Box(
                      modifier = Modifier
                        .width(14.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colorScheme.onPrimary.copy(alpha = 0.9f)),
                    )
                  } else if (step % 4 == 0) {
                    Box(
                      modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun laneAccentColor(lane: SequencerLane): Color {
  val colorScheme = MaterialTheme.colorScheme
  return when (lane) {
    SequencerLane.KICK, SequencerLane.BASS -> colorScheme.primary
    SequencerLane.SNARE, SequencerLane.CH_HAT -> colorScheme.secondary
    SequencerLane.OH_HAT, SequencerLane.SYNTH -> colorScheme.tertiary
  }
}
