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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.audio.InstrumentSynthDsp
import com.example.audio.PianoProgressionPreset
import com.example.audio.PianoVoice
import com.example.ui.PianoStudioState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PianoStudioPage(
  state: PianoStudioState,
  onSelectVoice: (PianoVoice) -> Unit,
  onSelectProgression: (PianoProgressionPreset) -> Unit,
  onShiftOctave: (Int) -> Unit,
  onStepBpm: (Int) -> Unit,
  onPlayKey: (Int) -> Unit,
  onPlayChord: (IntArray) -> Unit = { notes -> notes.forEach(onPlayKey) },
  onToggleRecording: () -> Unit,
  onClearRecording: () -> Unit,
  onPreviewPattern: () -> Unit,
  onGenerateTrack: () -> Unit,
  onOpenStudio: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val baseC = (state.baseOctave + 1) * 12 // C4 = 60
  val chordLabels = remember(state.selectedProgression) {
    state.selectedProgression.label.split("·").map { it.trim() }.filter { it.isNotEmpty() }
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .testTag("piano_studio_page"),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // 1. Page Header & Voice Bank
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
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = colorScheme.primaryContainer,
          ) {
            Icon(
              imageVector = Icons.Rounded.Piano,
              contentDescription = null,
              tint = colorScheme.onPrimaryContainer,
              modifier = Modifier
                .padding(10.dp)
                .size(22.dp),
            )
          }
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = stringResource(R.string.piano_page_title),
              style = MaterialTheme.typography.titleLarge,
              color = colorScheme.onSurface,
            )
            Text(
              text = stringResource(R.string.piano_page_subtitle),
              style = MaterialTheme.typography.bodySmall,
              color = colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        // Voice Resource Bank
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          PianoVoice.entries.forEach { voice ->
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
                selectedContainerColor = colorScheme.primaryContainer,
                selectedLabelColor = colorScheme.onPrimaryContainer,
              ),
              modifier = Modifier.testTag("piano_voice_${voice.id}"),
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

    // 2. Interactive Polyphonic Playable Keyboard + Octave/BPM Controls
    Surface(
      shape = MaterialTheme.shapes.large,
      color = colorScheme.surfaceContainerLow,
      border = BorderStroke(1.dp, colorScheme.outlineVariant),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = stringResource(R.string.piano_octave_label),
              style = MaterialTheme.typography.labelMedium,
              color = colorScheme.onSurfaceVariant,
            )
            IconButton(
              onClick = { onShiftOctave(-1) },
              enabled = state.baseOctave > 2,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("piano_octave_down"),
            ) {
              Icon(Icons.Rounded.Remove, contentDescription = "Octave down")
            }
            Text(
              text = "C${state.baseOctave}",
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
              color = colorScheme.primary,
            )
            IconButton(
              onClick = { onShiftOctave(1) },
              enabled = state.baseOctave < 5,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("piano_octave_up"),
            ) {
              Icon(Icons.Rounded.Add, contentDescription = "Octave up")
            }
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            IconButton(
              onClick = { onStepBpm(-4) },
              enabled = state.bpm > 55,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("piano_bpm_down"),
            ) {
              Icon(Icons.Rounded.Remove, contentDescription = "Decrease tempo")
            }
            Text(
              text = "${state.bpm} BPM",
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
              color = colorScheme.secondary,
              modifier = Modifier.testTag("piano_bpm_value"),
            )
            IconButton(
              onClick = { onStepBpm(4) },
              enabled = state.bpm < 180,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("piano_bpm_up"),
            ) {
              Icon(Icons.Rounded.Add, contentDescription = "Increase tempo")
            }
          }
        }

        InteractivePianoKeyboard(
          baseMidiC = baseC,
          activeMidiNotes = state.activeMidiNotes,
          onPlayKey = onPlayKey,
        )

        // Quick Polyphonic Chord Trigger Pads for the active progression
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = stringResource(R.string.piano_quick_chords_label),
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            state.selectedProgression.chordsMidi.forEachIndexed { idx, chordNotes ->
              val label = chordLabels.getOrElse(idx) { "Chord ${idx + 1}" }
              FilledTonalButton(
                onClick = { onPlayChord(chordNotes) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                modifier = Modifier
                  .weight(1f)
                  .minimumInteractiveComponentSize()
                  .testTag("piano_quick_chord_$idx"),
              ) {
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }

        // Recorded Note Trail
        if (state.recordedNotes.isNotEmpty()) {
          val trail = remember(state.recordedNotes) {
            state.recordedNotes.takeLast(10).joinToString(" · ") {
              InstrumentSynthDsp.midiToNoteName(it.midiNote)
            }
          }
          Surface(
            shape = MaterialTheme.shapes.small,
            color = colorScheme.primaryContainer.copy(alpha = 0.35f),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("piano_recorded_trail"),
          ) {
            Text(
              text = stringResource(R.string.piano_recorded_trail_label, trail),
              style = MaterialTheme.typography.labelSmall,
              color = colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
          }
        }

        // Take Recording Bar
        FlowRow(
          modifier = Modifier.fillMaxWidth(),
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
              .testTag("piano_record_button"),
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
                stringResource(R.string.piano_record_stop, state.recordedNotes.size)
              } else {
                stringResource(R.string.piano_record_start)
              },
              style = MaterialTheme.typography.labelMedium,
            )
          }

          if (state.recordedNotes.isNotEmpty()) {
            TextButton(
              onClick = onClearRecording,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("piano_clear_take_button"),
            ) {
              Icon(Icons.Rounded.ClearAll, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = stringResource(R.string.piano_clear_take, state.recordedNotes.size),
                style = MaterialTheme.typography.labelSmall,
              )
            }
          }

          OutlinedButton(
            onClick = onPreviewPattern,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("piano_preview_button"),
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

    // 3. Chord Progression Presets & Track Generator CTA
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
        Text(
          text = stringResource(R.string.piano_progression_label),
          style = MaterialTheme.typography.titleSmall,
          color = colorScheme.onSurface,
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          PianoProgressionPreset.entries.forEach { prog ->
            FilterChip(
              selected = state.selectedProgression == prog,
              onClick = { onSelectProgression(prog) },
              label = {
                Text(
                  text = prog.label,
                  style = MaterialTheme.typography.labelSmall,
                )
              },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = colorScheme.secondaryContainer,
                selectedLabelColor = colorScheme.onSecondaryContainer,
              ),
              modifier = Modifier.testTag("piano_prog_${prog.id}"),
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
            .testTag("generate_piano_track_button"),
        ) {
          if (state.isGenerating) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              strokeWidth = 2.dp,
              color = colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.piano_generating_label))
          } else {
            Icon(
              imageVector = Icons.Rounded.LibraryAdd,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.piano_generate_button))
          }
        }
      }
    }
  }
}

@Composable
private fun InteractivePianoKeyboard(
  baseMidiC: Int,
  activeMidiNotes: Set<Int>,
  onPlayKey: (Int) -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  // 10 white keys spanning C..E across 1.3 octaves; fits nicely or scrolls cleanly on narrow screens
  val whiteSemitones = listOf(0, 2, 4, 5, 7, 9, 11, 12, 14, 16)
  // Black key offset in white-key slots (between C-D, D-E, F-G, G-A, A-B, C'-D', D'-E')
  val blackKeys = listOf(
    Triple(0, 1, "C#"),
    Triple(1, 3, "D#"),
    Triple(3, 6, "F#"),
    Triple(4, 8, "G#"),
    Triple(5, 10, "A#"),
    Triple(7, 13, "C#"),
    Triple(8, 15, "D#"),
  )
  val whiteGap = 3.dp
  val keyHeight = 160.dp

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(colorScheme.surfaceContainerHighest.copy(alpha = 0.55f))
      .padding(top = 6.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
  ) {
    val computedWhiteWidth = ((maxWidth - whiteGap * (whiteSemitones.size - 1)) / whiteSemitones.size)
      .coerceAtLeast(48.dp)
    val blackKeyWidth = (computedWhiteWidth * 0.68f).coerceIn(32.dp, 42.dp)
    val totalKeyboardWidth = computedWhiteWidth * whiteSemitones.size + whiteGap * (whiteSemitones.size - 1)

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
    ) {
      Box(
        modifier = Modifier
          .height(keyHeight)
          .width(totalKeyboardWidth),
      ) {
        // White keys layer
        Row(
          modifier = Modifier.fillMaxSize(),
          horizontalArrangement = Arrangement.spacedBy(whiteGap),
        ) {
          whiteSemitones.forEach { semi ->
            val midi = baseMidiC + semi
            val isActive = midi in activeMidiNotes
            val label = InstrumentSynthDsp.midiToNoteName(midi)
            val keyColor by animateColorAsState(
              targetValue = if (isActive) colorScheme.primaryContainer else colorScheme.surfaceBright,
              animationSpec = spring(stiffness = 700f),
              label = "whiteKeyColor_$midi",
            )
            Box(
              modifier = Modifier
                .width(computedWhiteWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp, topStart = 4.dp, topEnd = 4.dp))
                .background(keyColor)
                .border(
                  width = if (isActive) 2.dp else 1.dp,
                  color = if (isActive) colorScheme.primary else colorScheme.outlineVariant,
                  shape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp, topStart = 4.dp, topEnd = 4.dp),
                )
                .clickable { onPlayKey(midi) }
                .padding(bottom = 10.dp)
                .testTag("piano_key_$midi"),
              contentAlignment = Alignment.BottomCenter,
            ) {
              Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                  fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                ),
                color = if (isActive) colorScheme.onPrimaryContainer else colorScheme.onSurface,
              )
            }
          }
        }

        // Black keys layer (elevated zIndex so black key taps never fall through to white keys)
        blackKeys.forEach { (whiteSlot, semi, sharpLabel) ->
          val midi = baseMidiC + semi
          val isActive = midi in activeMidiNotes
          val boundaryX = (computedWhiteWidth + whiteGap) * (whiteSlot + 1) - whiteGap / 2
          val xOffset = boundaryX - blackKeyWidth / 2
          val blackKeyBg by animateColorAsState(
            targetValue = if (isActive) colorScheme.secondary else colorScheme.inverseSurface,
            animationSpec = spring(stiffness = 700f),
            label = "blackKeyColor_$midi",
          )
          Box(
            modifier = Modifier
              .zIndex(2f)
              .offset(x = xOffset)
              .width(blackKeyWidth)
              .height(96.dp)
              .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp, topStart = 2.dp, topEnd = 2.dp))
              .background(blackKeyBg)
              .border(
                width = if (isActive) 1.5.dp else 1.dp,
                color = if (isActive) colorScheme.primary else colorScheme.outline,
                shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp, topStart = 2.dp, topEnd = 2.dp),
              )
              .clickable { onPlayKey(midi) }
              .padding(bottom = 6.dp)
              .testTag("piano_key_$midi"),
            contentAlignment = Alignment.BottomCenter,
          ) {
            Text(
              text = sharpLabel,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
              color = if (isActive) colorScheme.onSecondary else colorScheme.inverseOnSurface,
            )
          }
        }
      }
    }
  }
}
