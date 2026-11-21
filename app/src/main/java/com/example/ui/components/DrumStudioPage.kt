package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.DrumKitPreset
import com.example.audio.DrumPadVoice
import com.example.audio.MetronomeState
import com.example.audio.MetronomeTimeSignature
import com.example.audio.MetronomeTone
import com.example.audio.SequencerLane
import com.example.audio.SequencerPreset
import com.example.audio.StepSequencerState
import com.example.ui.DrumStudioState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrumStudioPage(
  sequencerState: StepSequencerState,
  metronomeState: MetronomeState,
  drumState: DrumStudioState = DrumStudioState(),
  onSelectDrumKit: (DrumKitPreset) -> Unit,
  onTriggerDrumPad: (DrumPadVoice) -> Unit,
  onToggleDrumPadRecording: () -> Unit = {},
  onClearDrumPadRecording: () -> Unit = {},
  onPreviewDrumPadTake: () -> Unit = {},
  onToggleSequencerStep: (SequencerLane, Int) -> Unit,
  onToggleSequencerPlayStop: () -> Unit,
  onClearSequencerPattern: () -> Unit,
  onApplySequencerPreset: (SequencerPreset) -> Unit,
  onStepSequencerBpm: (Int) -> Unit,
  onToggleSequencerBpmSync: () -> Unit,
  onCycleSequencerSwing: () -> Unit,
  onGenerateDrumTrack: () -> Unit,
  onToggleMetronomePlayStop: () -> Unit,
  onStepMetronomeBpm: (Int) -> Unit,
  onToggleMetronomeBpmSync: () -> Unit,
  onMetronomeVolumeChange: (Float) -> Unit,
  onMetronomePitchHzChange: (Float) -> Unit,
  onSelectMetronomeToneStyle: (MetronomeTone) -> Unit,
  onSelectMetronomeTimeSignature: (MetronomeTimeSignature) -> Unit,
  onToggleMetronomeAccentDownbeat: () -> Unit,
  onTapMetronomeTempo: () -> Unit,
  onOpenStudio: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme

  Column(
    modifier = modifier
      .fillMaxWidth()
      .testTag("drum_studio_page"),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // 1. Drum Header & Kit Selector
    Surface(
      shape = MaterialTheme.shapes.large,
      color = colorScheme.surfaceContainer,
      border = BorderStroke(1.dp, colorScheme.outlineVariant),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = colorScheme.tertiaryContainer,
          ) {
            Icon(
              imageVector = Icons.Rounded.Album,
              contentDescription = null,
              tint = colorScheme.onTertiaryContainer,
              modifier = Modifier
                .padding(10.dp)
                .size(22.dp),
            )
          }
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = stringResource(R.string.drums_page_title),
              style = MaterialTheme.typography.titleLarge,
              color = colorScheme.onSurface,
            )
            Text(
              text = stringResource(R.string.drums_page_subtitle),
              style = MaterialTheme.typography.bodySmall,
              color = colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        Text(
          text = stringResource(R.string.drums_kit_label),
          style = MaterialTheme.typography.labelMedium,
          color = colorScheme.onSurfaceVariant,
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          DrumKitPreset.entries.forEach { kit ->
            FilterChip(
              selected = sequencerState.drumKit == kit,
              onClick = { onSelectDrumKit(kit) },
              label = {
                Text(
                  text = kit.label,
                  style = MaterialTheme.typography.labelSmall,
                )
              },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = colorScheme.tertiaryContainer,
                selectedLabelColor = colorScheme.onTertiaryContainer,
              ),
              modifier = Modifier.testTag("drum_kit_${kit.id}"),
            )
          }
        }
        Text(
          text = sequencerState.drumKit.subtitle,
          style = MaterialTheme.typography.labelSmall,
          color = colorScheme.secondary,
        )
      }
    }

    // 2. Polyphonic 2x4 Live Drum Trigger Pads + Live Pad Recording
    Surface(
      shape = MaterialTheme.shapes.large,
      color = colorScheme.surfaceContainerLow,
      border = BorderStroke(1.dp, colorScheme.outlineVariant),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = stringResource(R.string.drums_pads_label),
            style = MaterialTheme.typography.titleSmall,
            color = colorScheme.onSurface,
          )
          Text(
            text = stringResource(R.string.drums_pads_hint),
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
          )
        }

        val rows = DrumPadVoice.entries.chunked(4)
        rows.forEach { rowPads ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            rowPads.forEach { pad ->
              val isActive = pad in drumState.activePads
              val accentColor = when (pad) {
                DrumPadVoice.KICK, DrumPadVoice.LOW_TOM -> colorScheme.primary
                DrumPadVoice.SNARE, DrumPadVoice.CLAP, DrumPadVoice.RIMSHOT -> colorScheme.secondary
                DrumPadVoice.CLOSED_HAT, DrumPadVoice.OPEN_HAT, DrumPadVoice.CRASH -> colorScheme.tertiary
              }
              val activeContainer = when (pad) {
                DrumPadVoice.KICK, DrumPadVoice.LOW_TOM -> colorScheme.primaryContainer
                DrumPadVoice.SNARE, DrumPadVoice.CLAP, DrumPadVoice.RIMSHOT -> colorScheme.secondaryContainer
                DrumPadVoice.CLOSED_HAT, DrumPadVoice.OPEN_HAT, DrumPadVoice.CRASH -> colorScheme.tertiaryContainer
              }
              val bgColor by animateColorAsState(
                targetValue = if (isActive) activeContainer else colorScheme.surfaceContainerHigh,
                animationSpec = spring(stiffness = 800f),
                label = "drumPadBg_${pad.id}",
              )

              Box(
                modifier = Modifier
                  .weight(1f)
                  .height(68.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(bgColor)
                  .border(
                    width = if (isActive) 2.dp else 1.dp,
                    color = if (isActive) accentColor else colorScheme.outlineVariant,
                    shape = RoundedCornerShape(12.dp),
                  )
                  .clickable { onTriggerDrumPad(pad) }
                  .padding(6.dp)
                  .testTag("drum_pad_${pad.id}"),
                contentAlignment = Alignment.Center,
              ) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                  Text(
                    text = pad.shortTag,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = accentColor,
                  )
                  Text(
                    text = pad.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
        }

        // Live Pad Take Recording Controls
        FlowRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = onToggleDrumPadRecording,
            border = BorderStroke(
              1.dp,
              if (drumState.isRecordingPads) colorScheme.error else colorScheme.outline,
            ),
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("drum_record_pads_button"),
          ) {
            Icon(
              imageVector = if (drumState.isRecordingPads) Icons.Rounded.Stop else Icons.Rounded.FiberManualRecord,
              contentDescription = null,
              tint = if (drumState.isRecordingPads) colorScheme.error else colorScheme.tertiary,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (drumState.isRecordingPads) {
                stringResource(R.string.drums_record_pads_stop, drumState.recordedHits.size)
              } else {
                stringResource(R.string.drums_record_pads_start)
              },
              style = MaterialTheme.typography.labelMedium,
            )
          }

          if (drumState.recordedHits.isNotEmpty()) {
            OutlinedButton(
              onClick = onPreviewDrumPadTake,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("drum_preview_pads_button"),
            ) {
              Icon(
                imageVector = if (drumState.isPreviewingPads) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = stringResource(R.string.drums_preview_pads),
                style = MaterialTheme.typography.labelMedium,
              )
            }

            TextButton(
              onClick = onClearDrumPadRecording,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("drum_clear_pads_button"),
            ) {
              Icon(Icons.Rounded.ClearAll, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = stringResource(R.string.drums_clear_pads, drumState.recordedHits.size),
                style = MaterialTheme.typography.labelSmall,
              )
            }
          }
        }
      }
    }

    // 3. Generated Confirmation + Generate Drum Track Primary CTA
    drumState.lastGeneratedTrackTitle?.let { createdTitle ->
      Surface(
        shape = MaterialTheme.shapes.small,
        color = colorScheme.secondaryContainer.copy(alpha = 0.75f),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.GraphicEq,
              contentDescription = null,
              tint = colorScheme.onSecondaryContainer,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = "Saved to History & Merger: $createdTitle",
              style = MaterialTheme.typography.labelMedium,
              color = colorScheme.onSecondaryContainer,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          TextButton(
            onClick = onOpenStudio,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
          ) {
            Text(
              text = stringResource(R.string.instrument_open_studio_button),
              style = MaterialTheme.typography.labelSmall,
            )
          }
        }
      }
    }

    Button(
      onClick = onGenerateDrumTrack,
      enabled = !sequencerState.isRenderingToLibrary,
      colors = ButtonDefaults.buttonColors(
        containerColor = colorScheme.primary,
        contentColor = colorScheme.onPrimary,
      ),
      shape = MaterialTheme.shapes.medium,
      contentPadding = PaddingValues(vertical = 14.dp),
      modifier = Modifier
        .fillMaxWidth()
        .minimumInteractiveComponentSize()
        .testTag("generate_drum_track_button"),
    ) {
      if (sequencerState.isRenderingToLibrary) {
        CircularProgressIndicator(
          modifier = Modifier.size(18.dp),
          strokeWidth = 2.dp,
          color = colorScheme.onPrimary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.drums_generating_label))
      } else {
        Icon(
          imageVector = Icons.Rounded.LibraryAdd,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = if (drumState.recordedHits.isNotEmpty()) {
            "${stringResource(R.string.drums_generate_button)} (${drumState.recordedHits.size} Pad Hits)"
          } else {
            stringResource(R.string.drums_generate_button)
          },
        )
      }
    }

    // 4. Full 16-Step Groove Sequencer
    StepSequencerCard(
      state = sequencerState,
      onToggleStep = onToggleSequencerStep,
      onTogglePlayStop = onToggleSequencerPlayStop,
      onClearPattern = onClearSequencerPattern,
      onApplyPreset = onApplySequencerPreset,
      onStepBpm = onStepSequencerBpm,
      onToggleBpmSync = onToggleSequencerBpmSync,
      onCycleSwing = onCycleSequencerSwing,
      onRenderToLibrary = onGenerateDrumTrack,
    )

    // 5. Studio Metronome (conveniently co-located with rhythm & drums)
    MetronomeCard(
      state = metronomeState,
      onTogglePlayStop = onToggleMetronomePlayStop,
      onStepBpm = onStepMetronomeBpm,
      onToggleBpmSync = onToggleMetronomeBpmSync,
      onVolumeChange = onMetronomeVolumeChange,
      onPitchHzChange = onMetronomePitchHzChange,
      onSelectToneStyle = onSelectMetronomeToneStyle,
      onSelectTimeSignature = onSelectMetronomeTimeSignature,
      onToggleAccentDownbeat = onToggleMetronomeAccentDownbeat,
      onTapTempo = onTapMetronomeTempo,
    )
  }
}
