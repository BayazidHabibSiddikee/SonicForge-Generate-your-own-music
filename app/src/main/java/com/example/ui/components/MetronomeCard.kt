package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.MetronomeDsp
import com.example.audio.MetronomeState
import com.example.audio.MetronomeTimeSignature
import com.example.audio.MetronomeTone
import kotlin.math.roundToInt

/**
 * Interactive Studio Metronome card with real-time adjustable click volume, pitch (Hz), tone timber,
 * time signature beat indicators, tap tempo, and automatic synchronization to the app's current BPM.
 */
@Composable
fun MetronomeCard(
  state: MetronomeState,
  onTogglePlayStop: () -> Unit,
  onStepBpm: (Int) -> Unit,
  onToggleBpmSync: () -> Unit,
  onVolumeChange: (Float) -> Unit,
  onPitchHzChange: (Float) -> Unit,
  onSelectToneStyle: (MetronomeTone) -> Unit,
  onSelectTimeSignature: (MetronomeTimeSignature) -> Unit,
  onToggleAccentDownbeat: () -> Unit,
  onTapTempo: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("metronome_card"),
    shape = MaterialTheme.shapes.large,
    color = colorScheme.surfaceContainer,
    tonalElevation = 4.dp,
    border = BorderStroke(
      width = 1.dp,
      color = if (state.isPlaying) colorScheme.tertiary.copy(alpha = 0.65f) else colorScheme.outline,
    ),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // 1. Header, Tap Tempo & Play/Stop Transport
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
            imageVector = Icons.Rounded.Timer,
            contentDescription = null,
            tint = if (state.isPlaying) colorScheme.tertiary else colorScheme.secondary,
            modifier = Modifier.size(20.dp),
          )
          Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
              text = stringResource(R.string.metronome_title),
              style = MaterialTheme.typography.titleMedium,
              color = colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            val subtitleText = if (state.isPlaying && state.currentBeat >= 0) {
              stringResource(
                R.string.metronome_subtitle,
                state.timeSignature.label,
                state.currentBeat + 1,
                state.timeSignature.beatsPerBar,
              )
            } else {
              stringResource(R.string.metronome_subtitle_idle, state.timeSignature.label)
            }
            Text(
              text = subtitleText,
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
            onClick = onTapTempo,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("metronome_tap_tempo_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.TouchApp,
              contentDescription = stringResource(R.string.cd_metronome_tap_tempo),
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = stringResource(R.string.metronome_tap_tempo),
              style = MaterialTheme.typography.labelSmall,
            )
          }

          FilledIconButton(
            onClick = onTogglePlayStop,
            colors = IconButtonDefaults.filledIconButtonColors(
              containerColor = if (state.isPlaying) colorScheme.tertiary else colorScheme.primary,
              contentColor = if (state.isPlaying) colorScheme.onTertiary else colorScheme.onPrimary,
            ),
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("metronome_play_pause_button"),
          ) {
            Icon(
              imageVector = if (state.isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
              contentDescription = if (state.isPlaying) {
                stringResource(R.string.cd_metronome_stop)
              } else {
                stringResource(R.string.cd_metronome_play)
              },
            )
          }
        }
      }

      // 2. Visual Beat Indicator Strip
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        for (beat in 0 until state.timeSignature.beatsPerBar) {
          val isActiveBeat = state.isPlaying && state.currentBeat == beat
          val isDownbeat = beat == 0
          val indicatorScale by animateFloatAsState(
            targetValue = if (isActiveBeat) 1.08f + state.beatPulse * 0.12f else 1.0f,
            label = "beatScale",
          )
          val bgColor by animateColorAsState(
            targetValue = when {
              isActiveBeat && isDownbeat -> colorScheme.tertiary
              isActiveBeat -> colorScheme.primary
              isDownbeat && state.accentDownbeat -> colorScheme.surfaceContainerHighest
              else -> colorScheme.surfaceContainerLow
            },
            label = "beatBg",
          )
          Box(
            modifier = Modifier
              .weight(1f)
              .height(28.dp)
              .scale(indicatorScale)
              .clip(MaterialTheme.shapes.small)
              .background(bgColor)
              .border(
                width = if (isActiveBeat) 1.5.dp else 1.dp,
                color = when {
                  isActiveBeat -> colorScheme.onSurface
                  isDownbeat && state.accentDownbeat -> colorScheme.tertiary.copy(alpha = 0.55f)
                  else -> colorScheme.outlineVariant
                },
                shape = MaterialTheme.shapes.small,
              )
              .testTag("metronome_beat_indicator_$beat"),
            contentAlignment = Alignment.Center,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              if (isDownbeat && state.accentDownbeat) {
                Box(
                  modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isActiveBeat) colorScheme.onTertiary else colorScheme.tertiary),
                )
              }
              Text(
                text = "${beat + 1}",
                style = MaterialTheme.typography.labelSmall.copy(
                  fontWeight = if (isActiveBeat || isDownbeat) FontWeight.Bold else FontWeight.Medium,
                ),
                color = when {
                  isActiveBeat && isDownbeat -> colorScheme.onTertiary
                  isActiveBeat -> colorScheme.onPrimary
                  else -> colorScheme.onSurfaceVariant
                },
              )
            }
          }
        }
      }

      // 3. Tempo Stepper & BPM Sync Row
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
            enabled = state.bpm > MetronomeDsp.MIN_BPM,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("metronome_bpm_down"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Remove,
              contentDescription = stringResource(R.string.cd_metronome_bpm_down),
              tint = colorScheme.onSurfaceVariant,
            )
          }

          Text(
            text = stringResource(R.string.metronome_bpm_value, state.bpm),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (state.isBpmSynced) colorScheme.secondary else colorScheme.primary,
            modifier = Modifier
              .padding(horizontal = 2.dp)
              .testTag("metronome_bpm_value"),
          )

          IconButton(
            onClick = { onStepBpm(2) },
            enabled = state.bpm < MetronomeDsp.MAX_BPM,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("metronome_bpm_up"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Add,
              contentDescription = stringResource(R.string.cd_metronome_bpm_up),
              tint = colorScheme.onSurfaceVariant,
            )
          }

          val syncLabel = when {
            state.syncedAppBpm > 0 && state.isBpmSynced -> {
              val source = state.bpmSyncSourceLabel.ifBlank { "App" }
              stringResource(R.string.metronome_sync_active, state.syncedAppBpm, source)
            }
            state.syncedAppBpm > 0 ->
              stringResource(R.string.metronome_sync_available, state.syncedAppBpm)
            else -> stringResource(R.string.metronome_sync_waiting)
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
              .testTag("metronome_bpm_sync_chip"),
          )

          FilterChip(
            selected = state.accentDownbeat,
            onClick = onToggleAccentDownbeat,
            label = {
              Text(
                text = stringResource(R.string.metronome_accent_label),
                style = MaterialTheme.typography.labelSmall,
              )
            },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = colorScheme.tertiaryContainer,
              selectedLabelColor = colorScheme.onTertiaryContainer,
            ),
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("metronome_accent_chip"),
          )
        }
      }

      // 4. Adjustable Click Volume & Pitch Controls
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = null,
            tint = colorScheme.secondary,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = stringResource(R.string.metronome_volume_label),
            style = MaterialTheme.typography.labelMedium,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp),
          )
          Slider(
            value = state.volume,
            onValueChange = onVolumeChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
              thumbColor = colorScheme.secondary,
              activeTrackColor = colorScheme.secondary,
            ),
            modifier = Modifier
              .weight(1f)
              .testTag("metronome_volume_slider"),
          )
          Text(
            text = stringResource(R.string.metronome_volume_percent, (state.volume * 100f).roundToInt()),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colorScheme.onSurface,
            modifier = Modifier
              .width(44.dp)
              .testTag("metronome_volume_value"),
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = colorScheme.tertiary,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = stringResource(R.string.metronome_pitch_label),
            style = MaterialTheme.typography.labelMedium,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp),
          )
          Slider(
            value = state.pitchHz,
            onValueChange = onPitchHzChange,
            valueRange = MetronomeDsp.MIN_PITCH_HZ..MetronomeDsp.MAX_PITCH_HZ,
            colors = SliderDefaults.colors(
              thumbColor = colorScheme.tertiary,
              activeTrackColor = colorScheme.tertiary,
            ),
            modifier = Modifier
              .weight(1f)
              .testTag("metronome_pitch_slider"),
          )
          Text(
            text = stringResource(R.string.metronome_pitch_hz, state.pitchHz.roundToInt()),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colorScheme.onSurface,
            modifier = Modifier
              .width(54.dp)
              .testTag("metronome_pitch_value"),
          )
        }
      }

      // 5. Tone Style & Time Signature Selectors
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.GraphicEq,
          contentDescription = null,
          tint = colorScheme.onSurfaceVariant,
          modifier = Modifier.size(16.dp),
        )
        MetronomeTone.entries.forEach { tone ->
          FilterChip(
            selected = state.toneStyle == tone,
            onClick = { onSelectToneStyle(tone) },
            label = {
              Text(
                text = tone.label,
                style = MaterialTheme.typography.labelSmall,
              )
            },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = colorScheme.primaryContainer,
              selectedLabelColor = colorScheme.onPrimaryContainer,
            ),
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("metronome_tone_${tone.id}"),
          )
        }

        Spacer(modifier = Modifier.width(4.dp))

        MetronomeTimeSignature.entries.forEach { sig ->
          FilterChip(
            selected = state.timeSignature == sig,
            onClick = { onSelectTimeSignature(sig) },
            label = {
              Text(
                text = sig.label,
                style = MaterialTheme.typography.labelSmall,
              )
            },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = colorScheme.tertiaryContainer,
              selectedLabelColor = colorScheme.onTertiaryContainer,
            ),
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("metronome_sig_${sig.id}"),
          )
        }
      }
    }
  }
}
