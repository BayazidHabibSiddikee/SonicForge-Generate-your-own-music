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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.GuitarChordShape
import com.example.audio.GuitarProgressionPreset
import com.example.audio.GuitarStrumStyle
import com.example.audio.GuitarVoice
import com.example.audio.InstrumentSynthDsp
import com.example.ui.GuitarStudioState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuitarStudioPage(
  state: GuitarStudioState,
  onSelectVoice: (GuitarVoice) -> Unit,
  onSelectChord: (GuitarChordShape) -> Unit,
  onAddChordToSequence: (GuitarChordShape) -> Unit = {},
  onRemoveChordFromSequenceAt: (Int) -> Unit = {},
  onApplyProgressionPreset: (GuitarProgressionPreset) -> Unit = {},
  onSelectStrumStyle: (GuitarStrumStyle) -> Unit,
  onStepBpm: (Int) -> Unit,
  onPluckString: (stringIndex: Int, fret: Int) -> Unit,
  onStrumChord: () -> Unit,
  onToggleRecording: () -> Unit,
  onClearRecording: () -> Unit,
  onPreviewPattern: () -> Unit,
  onGenerateTrack: () -> Unit,
  onOpenStudio: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme

  Column(
    modifier = modifier
      .fillMaxWidth()
      .testTag("guitar_studio_page"),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // 1. Header & Guitar Voice Bank
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
            color = colorScheme.secondaryContainer,
          ) {
            Icon(
              imageVector = Icons.Rounded.MusicNote,
              contentDescription = null,
              tint = colorScheme.onSecondaryContainer,
              modifier = Modifier
                .padding(10.dp)
                .size(22.dp),
            )
          }
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = stringResource(R.string.guitar_page_title),
              style = MaterialTheme.typography.titleLarge,
              color = colorScheme.onSurface,
            )
            Text(
              text = stringResource(R.string.guitar_page_subtitle),
              style = MaterialTheme.typography.bodySmall,
              color = colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          GuitarVoice.entries.forEach { voice ->
            FilterChip(
              selected = state.selectedVoice == voice,
              onClick = { onSelectVoice(voice) },
              label = {
                Text(
                  text = voice.label,
                  style = MaterialTheme.typography.labelSmall,
                )
              },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = colorScheme.secondaryContainer,
                selectedLabelColor = colorScheme.onSecondaryContainer,
              ),
              modifier = Modifier.testTag("guitar_voice_${voice.id}"),
            )
          }
        }
        Text(
          text = state.selectedVoice.subtitle,
          style = MaterialTheme.typography.labelSmall,
          color = colorScheme.secondary,
        )
      }
    }

    // 2. Interactive 6-String Playable Fretboard (Open + Frets 1..5 with Muted X indicators)
    Surface(
      shape = MaterialTheme.shapes.large,
      color = colorScheme.surfaceContainerLow,
      border = BorderStroke(1.dp, colorScheme.outlineVariant),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "6-String Fretboard (${state.selectedChord.label})",
              style = MaterialTheme.typography.titleSmall,
              color = colorScheme.onSurface,
            )
            Text(
              text = stringResource(R.string.guitar_muted_hint),
              style = MaterialTheme.typography.labelSmall,
              color = colorScheme.onSurfaceVariant,
            )
          }
          Button(
            onClick = onStrumChord,
            colors = ButtonDefaults.buttonColors(
              containerColor = colorScheme.secondary,
              contentColor = colorScheme.onSecondary,
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("guitar_strum_now_button"),
          ) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(R.string.guitar_strum_now_button),
              style = MaterialTheme.typography.labelMedium,
            )
          }
        }

        // Fret column guide header (Open + Frets 1..5)
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Spacer(modifier = Modifier.width(34.dp))
          for (fret in 0..5) {
            val fretLabel = when (fret) {
              0 -> "Open"
              3, 5 -> "F$fret •"
              else -> "F$fret"
            }
            Text(
              text = fretLabel,
              style = MaterialTheme.typography.labelSmall,
              color = if (fret == 3 || fret == 5) colorScheme.primary else colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
              modifier = Modifier.weight(1f),
            )
          }
        }

        // Strings high E4 (index 5) down to low E2 (index 0)
        for (stringIdx in 5 downTo 0) {
          val stringName = InstrumentSynthDsp.GUITAR_STRING_NAMES[stringIdx]
          val chordFret = state.selectedChord.frets.getOrElse(stringIdx) { -1 }
          val isMutedInChord = chordFret < 0
          val isPluckedString = state.activePluckedCell?.first == stringIdx

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Column(
              modifier = Modifier.width(34.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                text = stringName,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = when {
                  isPluckedString -> colorScheme.primary
                  isMutedInChord -> colorScheme.error
                  else -> colorScheme.onSurfaceVariant
                },
              )
              if (isMutedInChord) {
                Text(
                  text = "X",
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                  color = colorScheme.error,
                  modifier = Modifier.testTag("guitar_muted_string_$stringIdx"),
                )
              }
            }

            for (fret in 0..5) {
              val isChordDot = chordFret == fret
              val isActiveCell = state.activePluckedCell == (stringIdx to fret)
              val noteName = InstrumentSynthDsp.midiToNoteName(
                InstrumentSynthDsp.GUITAR_OPEN_MIDI[stringIdx] + fret,
              )
              val targetBg = when {
                isActiveCell -> colorScheme.secondaryContainer
                isChordDot -> colorScheme.primaryContainer
                fret == 0 -> colorScheme.surfaceContainerHigh
                else -> colorScheme.surfaceContainer
              }
              val cellBg by animateColorAsState(
                targetValue = targetBg,
                animationSpec = spring(stiffness = 700f),
                label = "guitarFret_${stringIdx}_$fret",
              )

              Box(
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(cellBg)
                  .border(
                    width = when {
                      isActiveCell -> 2.dp
                      isChordDot -> 1.5.dp
                      fret == 0 -> 1.25.dp
                      else -> 1.dp
                    },
                    color = when {
                      isActiveCell -> colorScheme.secondary
                      isChordDot -> colorScheme.primary
                      fret == 0 -> colorScheme.outline
                      else -> colorScheme.outlineVariant
                    },
                    shape = RoundedCornerShape(8.dp),
                  )
                  .clickable { onPluckString(stringIdx, fret) }
                  .testTag("guitar_fret_${stringIdx}_$fret"),
                contentAlignment = Alignment.Center,
              ) {
                // Visual guitar string line (thicker on lower bass strings E2/A2/D3)
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(
                      when {
                        isPluckedString -> 2.5.dp
                        stringIdx < 2 -> 2.2.dp
                        stringIdx < 4 -> 1.6.dp
                        else -> 1.dp
                      },
                    )
                    .padding(horizontal = 4.dp)
                    .background(
                      if (isPluckedString) {
                        colorScheme.secondary.copy(alpha = 0.85f)
                      } else {
                        colorScheme.outlineVariant.copy(alpha = 0.65f)
                      },
                    ),
                )

                if (isActiveCell) {
                  Surface(
                    shape = CircleShape,
                    color = colorScheme.secondary,
                  ) {
                    Text(
                      text = noteName,
                      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                      color = colorScheme.onSecondary,
                      modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                  }
                } else if (isChordDot) {
                  Box(
                    modifier = Modifier
                      .size(24.dp)
                      .clip(CircleShape)
                      .background(colorScheme.primary),
                    contentAlignment = Alignment.Center,
                  ) {
                    Text(
                      text = if (fret == 0) "O" else "$fret",
                      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                      color = colorScheme.onPrimary,
                    )
                  }
                }
              }
            }
          }
        }

        // Take Recording Bar
        FlowRow(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = onToggleRecording,
            border = BorderStroke(
              1.dp,
              if (state.isRecording) colorScheme.error else colorScheme.outline,
            ),
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("guitar_record_button"),
          ) {
            Icon(
              imageVector = if (state.isRecording) Icons.Rounded.Stop else Icons.Rounded.FiberManualRecord,
              contentDescription = null,
              tint = if (state.isRecording) colorScheme.error else colorScheme.tertiary,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (state.isRecording) {
                stringResource(R.string.piano_record_stop, state.recordedPlucks.size)
              } else {
                stringResource(R.string.piano_record_start)
              },
              style = MaterialTheme.typography.labelMedium,
            )
          }

          if (state.recordedPlucks.isNotEmpty()) {
            TextButton(
              onClick = onClearRecording,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("guitar_clear_take_button"),
            ) {
              Icon(Icons.Rounded.ClearAll, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = stringResource(R.string.piano_clear_take, state.recordedPlucks.size),
                style = MaterialTheme.typography.labelSmall,
              )
            }
          }

          OutlinedButton(
            onClick = onPreviewPattern,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("guitar_preview_button"),
          ) {
            Icon(
              imageVector = if (state.isPreviewing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = stringResource(R.string.piano_preview_take),
              style = MaterialTheme.typography.labelMedium,
            )
          }
        }
      }
    }

    // 3. Chord Selector + Progression Builder + Strum Style + Track Generation
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
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(R.string.guitar_chords_label),
            style = MaterialTheme.typography.titleSmall,
            color = colorScheme.onSurface,
          )
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = { onStepBpm(-4) },
              enabled = state.bpm > 55,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("guitar_bpm_down"),
            ) {
              Icon(Icons.Rounded.Remove, contentDescription = "Decrease tempo")
            }
            Text(
              text = "${state.bpm} BPM",
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
              color = colorScheme.secondary,
              modifier = Modifier.testTag("guitar_bpm_value"),
            )
            IconButton(
              onClick = { onStepBpm(4) },
              enabled = state.bpm < 180,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("guitar_bpm_up"),
            ) {
              Icon(Icons.Rounded.Add, contentDescription = "Increase tempo")
            }
          }
        }

        // Chord audition chips: tapping selects the chord on the fretboard & strums it cleanly
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          GuitarChordShape.entries.forEach { chord ->
            val isSelected = state.selectedChord == chord
            FilterChip(
              selected = isSelected,
              onClick = { onSelectChord(chord) },
              label = {
                Text(
                  text = chord.label,
                  style = MaterialTheme.typography.labelSmall,
                )
              },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = colorScheme.primaryContainer,
                selectedLabelColor = colorScheme.onPrimaryContainer,
              ),
              modifier = Modifier.testTag("guitar_chord_${chord.id}"),
            )
          }
        }

        // Add selected chord to progression + Quick Progression presets
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = { onAddChordToSequence(state.selectedChord) },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("guitar_add_to_seq_button"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "${stringResource(R.string.guitar_add_to_seq_button)} (${state.selectedChord.label})",
              style = MaterialTheme.typography.labelSmall,
            )
          }
        }

        // Interactive Sequence Bar (tap step to audition, tap X to remove)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.testTag("guitar_chord_sequence_row"),
        ) {
          state.chordSequence.forEachIndexed { idx, seqChord ->
            InputChip(
              selected = true,
              onClick = { onSelectChord(seqChord) },
              label = {
                Text(
                  text = "${idx + 1}. ${seqChord.label}",
                  style = MaterialTheme.typography.labelSmall,
                )
              },
              trailingIcon = if (state.chordSequence.size > 1) {
                {
                  Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove ${seqChord.label} from sequence",
                    modifier = Modifier
                      .size(14.dp)
                      .clickable { onRemoveChordFromSequenceAt(idx) }
                      .testTag("guitar_seq_remove_$idx"),
                  )
                }
              } else {
                null
              },
              modifier = Modifier.testTag("guitar_seq_chip_$idx"),
            )
          }
        }

        // Quick Progression Presets
        Text(
          text = stringResource(R.string.guitar_progression_presets_label),
          style = MaterialTheme.typography.labelSmall,
          color = colorScheme.onSurfaceVariant,
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          GuitarProgressionPreset.entries.forEach { preset ->
            FilterChip(
              selected = state.chordSequence == preset.chords,
              onClick = { onApplyProgressionPreset(preset) },
              label = {
                Text(
                  text = preset.label,
                  style = MaterialTheme.typography.labelSmall,
                )
              },
              modifier = Modifier.testTag("guitar_preset_${preset.id}"),
            )
          }
        }

        Text(
          text = stringResource(R.string.guitar_strum_style_label),
          style = MaterialTheme.typography.labelMedium,
          color = colorScheme.onSurfaceVariant,
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          GuitarStrumStyle.entries.forEach { style ->
            FilterChip(
              selected = state.strumStyle == style,
              onClick = { onSelectStrumStyle(style) },
              label = {
                Text(
                  text = style.label,
                  style = MaterialTheme.typography.labelSmall,
                )
              },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = colorScheme.tertiaryContainer,
                selectedLabelColor = colorScheme.onTertiaryContainer,
              ),
              modifier = Modifier.testTag("guitar_style_${style.id}"),
            )
          }
        }

        state.lastGeneratedTrackTitle?.let { createdTitle ->
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
          onClick = onGenerateTrack,
          enabled = !state.isGenerating,
          colors = ButtonDefaults.buttonColors(
            containerColor = colorScheme.primary,
            contentColor = colorScheme.onPrimary,
          ),
          shape = MaterialTheme.shapes.medium,
          contentPadding = PaddingValues(vertical = 14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("generate_guitar_track_button"),
        ) {
          if (state.isGenerating) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              strokeWidth = 2.dp,
              color = colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.guitar_generating_label))
          } else {
            Icon(
              imageVector = Icons.Rounded.LibraryAdd,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.guitar_generate_button))
          }
        }
      }
    }
  }
}
