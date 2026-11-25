package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.GeneratedTrack
import com.example.ui.MergeMixerState
import kotlin.math.roundToInt

@Composable
fun MultiTrackMergerCard(
  allTracks: List<GeneratedTrack>,
  state: MergeMixerState,
  onToggleTrackSelection: (String) -> Unit,
  onStemGainChange: (String, Float) -> Unit,
  onLoopShorterStemsChange: (Boolean) -> Unit,
  onTitleChanged: (String) -> Unit,
  onAutoPickBandStems: () -> Unit,
  onMergeTracks: () -> Unit,
  onMergeAndSaveMp3: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val selectedCount = state.selectedTrackIds.count { id -> allTracks.any { it.id == id } }
  val canMerge = selectedCount >= 2
  var expanded by remember { mutableStateOf(false) }
  val isExpanded = expanded || state.isMerging

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("multi_track_merger_card"),
    shape = MaterialTheme.shapes.large,
    color = colorScheme.surfaceContainer,
    tonalElevation = 2.dp,
    border = BorderStroke(1.dp, colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .minimumInteractiveComponentSize()
          .clip(MaterialTheme.shapes.medium)
          .clickable { expanded = !expanded }
          .testTag("merger_expand_toggle"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Layers,
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
          Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
              text = stringResource(R.string.merger_title),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
              color = colorScheme.onSurface,
            )
            Text(
              text = if (isExpanded) {
                stringResource(R.string.merger_subtitle)
              } else {
                stringResource(R.string.merger_collapsed_hint)
              },
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
          if (selectedCount > 0) {
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = colorScheme.primaryContainer,
            ) {
              Text(
                text = stringResource(R.string.merger_selected_badge, selectedCount),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
              )
            }
          }
          Icon(
            imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
          )
        }
      }

      AnimatedVisibility(visible = isExpanded) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(
          onClick = onAutoPickBandStems,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("merger_auto_pick_button"),
        ) {
          Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = stringResource(R.string.merger_select_band_button),
            style = MaterialTheme.typography.labelSmall,
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = stringResource(R.string.merger_loop_stems_label),
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
          )
          Switch(
            checked = state.loopShorterStems,
            onCheckedChange = onLoopShorterStemsChange,
            modifier = Modifier.testTag("merger_loop_stems_switch"),
          )
        }
      }

      OutlinedTextField(
        value = state.customTitle,
        onValueChange = onTitleChanged,
        label = { Text(stringResource(R.string.merger_title_label)) },
        placeholder = { Text(stringResource(R.string.merger_title_placeholder)) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = colorScheme.primary,
          unfocusedBorderColor = colorScheme.outlineVariant,
        ),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("merger_title_input"),
      )

      // Compact stem checklist with per-stem gain sliders
      if (allTracks.isEmpty()) {
        Text(
          text = stringResource(R.string.merger_need_two_hint),
          style = MaterialTheme.typography.bodySmall,
          color = colorScheme.onSurfaceVariant,
        )
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          allTracks.take(8).forEach { track ->
            val isChecked = track.id in state.selectedTrackIds
            val stemGain = state.stemGains[track.id] ?: 1.0f
            Surface(
              shape = MaterialTheme.shapes.medium,
              color = if (isChecked) colorScheme.primaryContainer.copy(alpha = 0.35f) else colorScheme.surfaceContainerLow,
              border = BorderStroke(
                width = 1.dp,
                color = if (isChecked) colorScheme.primary.copy(alpha = 0.6f) else colorScheme.outlineVariant,
              ),
              modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable { onToggleTrackSelection(track.id) }
                .testTag("merger_stem_row_${track.id}"),
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 8.dp, vertical = 4.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Checkbox(
                    checked = isChecked,
                    onCheckedChange = { onToggleTrackSelection(track.id) },
                    modifier = Modifier
                      .minimumInteractiveComponentSize()
                      .testTag("merger_stem_checkbox_${track.id}"),
                  )
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = track.title,
                      style = MaterialTheme.typography.labelLarge,
                      color = colorScheme.onSurface,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text = "${track.category} · ${track.bpm} BPM · ${formatDurationMs(track.durationMs)}",
                      style = MaterialTheme.typography.labelSmall,
                      color = colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                  if (isChecked) {
                    Surface(
                      shape = RoundedCornerShape(6.dp),
                      color = colorScheme.secondaryContainer,
                    ) {
                      Text(
                        text = "${(stemGain * 100f).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                      )
                    }
                  }
                }
                if (isChecked) {
                  Slider(
                    value = stemGain,
                    onValueChange = { onStemGainChange(track.id, it) },
                    valueRange = 0.1f..1.5f,
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 8.dp)
                      .testTag("merger_stem_gain_${track.id}"),
                  )
                }
              }
            }
          }
        }
      }

      if (!canMerge && allTracks.isNotEmpty()) {
        Text(
          text = stringResource(R.string.merger_need_two_hint),
          style = MaterialTheme.typography.labelSmall,
          color = colorScheme.onSurfaceVariant,
        )
      }

      state.lastStatusMessage?.let { status ->
        Surface(
          shape = MaterialTheme.shapes.small,
          color = colorScheme.secondaryContainer.copy(alpha = 0.7f),
        ) {
          Text(
            text = status,
            style = MaterialTheme.typography.labelMedium,
            color = colorScheme.onSecondaryContainer,
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 10.dp, vertical = 6.dp)
              .testTag("merger_status_banner"),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = onMergeTracks,
          enabled = canMerge && !state.isMerging,
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
          modifier = Modifier
            .weight(1f)
            .minimumInteractiveComponentSize()
            .testTag("merger_merge_button"),
        ) {
          if (state.isMerging) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.merger_merging_label), style = MaterialTheme.typography.labelSmall)
          } else {
            Icon(Icons.Rounded.LibraryMusic, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.merger_merge_button), style = MaterialTheme.typography.labelMedium)
          }
        }

        Button(
          onClick = onMergeAndSaveMp3,
          enabled = canMerge && !state.isMerging,
          colors = ButtonDefaults.buttonColors(
            containerColor = colorScheme.primary,
            contentColor = colorScheme.onPrimary,
          ),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
          modifier = Modifier
            .weight(1.15f)
            .minimumInteractiveComponentSize()
            .testTag("merger_merge_mp3_button"),
        ) {
          Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.merger_merge_mp3_button), style = MaterialTheme.typography.labelMedium)
        }
      }
      }
      }
    }
  }
}
