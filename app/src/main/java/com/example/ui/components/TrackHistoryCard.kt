package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.TrackHistoryItem
import com.example.data.local.TrackHistoryTypes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val historyFilterTypes = listOf(
  TrackHistoryTypes.ALL,
  TrackHistoryTypes.PROMPT,
  TrackHistoryTypes.PIANO,
  TrackHistoryTypes.GUITAR,
  TrackHistoryTypes.DRUMS,
  TrackHistoryTypes.MERGED,
  TrackHistoryTypes.SOUND_PACK,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrackHistoryCard(
  historyItems: List<TrackHistoryItem>,
  totalHistoryCount: Int,
  selectedTypeFilter: String,
  searchQuery: String,
  currentTrackId: String?,
  isPlaying: Boolean,
  onSelectTypeFilter: (String) -> Unit,
  onSearchQueryChanged: (String) -> Unit,
  onPlayHistoryItem: (TrackHistoryItem) -> Unit,
  onReusePrompt: (String) -> Unit,
  onDeleteHistoryItem: (String) -> Unit,
  onClearAllHistory: () -> Unit,
  onExportHistoryItem: ((TrackHistoryItem) -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  var showClearDialog by remember { mutableStateOf(false) }

  if (showClearDialog) {
    AlertDialog(
      onDismissRequest = { showClearDialog = false },
      title = { Text(stringResource(R.string.history_clear_dialog_title)) },
      text = { Text(stringResource(R.string.history_clear_dialog_body)) },
      confirmButton = {
        Button(
          onClick = {
            showClearDialog = false
            onClearAllHistory()
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = colorScheme.error,
            contentColor = colorScheme.onError,
          ),
          modifier = Modifier.testTag("history_confirm_clear_button"),
        ) {
          Text(stringResource(R.string.history_clear_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearDialog = false }) {
          Text(stringResource(R.string.edit_org_cancel_button))
        }
      },
    )
  }

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("track_history_card"),
    shape = MaterialTheme.shapes.large,
    color = colorScheme.surfaceContainerLow,
    border = BorderStroke(1.dp, colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .drawBehind {
          drawRect(
            brush = Brush.radialGradient(
              colors = listOf(
                colorScheme.primary.copy(alpha = 0.08f),
                colorScheme.surfaceContainerLow.copy(alpha = 0f),
              ),
              center = Offset(size.width * 0.9f, 0f),
              radius = size.width * 0.75f,
            ),
          )
        }
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Header Row
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
            color = colorScheme.primaryContainer,
          ) {
            Icon(
              imageVector = Icons.Rounded.History,
              contentDescription = null,
              tint = colorScheme.onPrimaryContainer,
              modifier = Modifier
                .padding(8.dp)
                .size(20.dp),
            )
          }
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = stringResource(R.string.history_card_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
              )
              Surface(
                shape = RoundedCornerShape(6.dp),
                color = colorScheme.secondaryContainer,
                modifier = Modifier.testTag("history_count_badge"),
              ) {
                Text(
                  text = stringResource(R.string.history_count_badge, totalHistoryCount),
                  style = MaterialTheme.typography.labelSmall,
                  color = colorScheme.onSecondaryContainer,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
              }
            }
            Text(
              text = stringResource(R.string.history_card_subtitle),
              style = MaterialTheme.typography.labelSmall,
              color = colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        if (totalHistoryCount > 0) {
          TextButton(
            onClick = { showClearDialog = true },
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("history_clear_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.DeleteSweep,
              contentDescription = null,
              tint = colorScheme.error,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = stringResource(R.string.history_clear_button),
              style = MaterialTheme.typography.labelSmall,
              color = colorScheme.error,
            )
          }
        }
      }

      if (totalHistoryCount > 0 || searchQuery.isNotBlank()) {
        // Search Input
        OutlinedTextField(
          value = searchQuery,
          onValueChange = onSearchQueryChanged,
          placeholder = {
            Text(
              text = stringResource(R.string.history_search_placeholder),
              style = MaterialTheme.typography.bodySmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
          leadingIcon = {
            Icon(
              imageVector = Icons.Rounded.Search,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
          },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(
                onClick = { onSearchQueryChanged("") },
                modifier = Modifier.minimumInteractiveComponentSize(),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Close,
                  contentDescription = "Clear history search",
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colorScheme.primary,
            unfocusedBorderColor = colorScheme.outlineVariant,
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("history_search_input"),
        )

        // Source Type Filter Chips
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          historyFilterTypes.forEach { type ->
            val selected = selectedTypeFilter.equals(type, ignoreCase = true)
            FilterChip(
              selected = selected,
              onClick = { onSelectTypeFilter(type) },
              label = {
                Text(
                  text = type,
                  style = MaterialTheme.typography.labelSmall,
                )
              },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = colorScheme.primaryContainer,
                selectedLabelColor = colorScheme.onPrimaryContainer,
              ),
              modifier = Modifier.testTag("history_filter_${type.lowercase().replace(" ", "_")}"),
            )
          }
        }
      }

      // History List or Empty State
      if (historyItems.isEmpty()) {
        Surface(
          shape = MaterialTheme.shapes.medium,
          color = colorScheme.surfaceContainer,
          border = BorderStroke(1.dp, colorScheme.outlineVariant),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("history_empty_state"),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.History,
              contentDescription = null,
              tint = colorScheme.onSurfaceVariant,
              modifier = Modifier.size(28.dp),
            )
            Text(
              text = stringResource(R.string.history_empty_title),
              style = MaterialTheme.typography.titleSmall,
              color = colorScheme.onSurface,
            )
            Text(
              text = stringResource(R.string.history_empty_subtitle),
              style = MaterialTheme.typography.bodySmall,
              color = colorScheme.onSurfaceVariant,
            )
          }
        }
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          historyItems.forEach { item ->
            val isCurrent = item.trackId == currentTrackId
            TrackHistoryRow(
              item = item,
              isCurrent = isCurrent,
              isPlayingThis = isCurrent && isPlaying,
              onPlay = { onPlayHistoryItem(item) },
              onReusePrompt = { onReusePrompt(item.prompt) },
              onExport = onExportHistoryItem?.let { handler -> { handler(item) } },
              onDelete = { onDeleteHistoryItem(item.id) },
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrackHistoryRow(
  item: TrackHistoryItem,
  isCurrent: Boolean,
  isPlayingThis: Boolean,
  onPlay: () -> Unit,
  onReusePrompt: () -> Unit,
  onExport: (() -> Unit)? = null,
  onDelete: () -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  val formatter = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
  val createdText = remember(item.createdAtEpochMs) { formatter.format(Date(item.createdAtEpochMs)) }

  val icon = when (item.instrumentType) {
    TrackHistoryTypes.PIANO -> Icons.Rounded.Piano
    TrackHistoryTypes.GUITAR -> Icons.Rounded.MusicNote
    TrackHistoryTypes.DRUMS -> Icons.Rounded.Album
    TrackHistoryTypes.PROMPT -> Icons.Rounded.AutoAwesome
    else -> Icons.Rounded.GraphicEq
  }

  Surface(
    shape = MaterialTheme.shapes.medium,
    color = if (isCurrent) colorScheme.primaryContainer.copy(alpha = 0.25f) else colorScheme.surfaceContainer,
    border = BorderStroke(
      width = if (isCurrent) 1.5.dp else 1.dp,
      color = if (isCurrent) colorScheme.primary else colorScheme.outlineVariant,
    ),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("history_entry_${item.id}"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = if (isCurrent) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh,
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isCurrent) colorScheme.onPrimaryContainer else colorScheme.secondary,
            modifier = Modifier
              .padding(8.dp)
              .size(18.dp),
          )
        }
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = item.title,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
              color = colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f, fill = false),
            )
            Surface(
              shape = RoundedCornerShape(5.dp),
              color = colorScheme.tertiaryContainer,
            ) {
              Text(
                text = item.instrumentType,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
              )
            }
          }

          Text(
            text = "${item.engineOrSource} · ${item.bpm} BPM · ${formatDurationMs(item.durationMs)} · $createdText",
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )

          if (item.prompt.isNotBlank()) {
            Text(
              text = item.prompt,
              style = MaterialTheme.typography.bodySmall,
              color = colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }

      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        FilledTonalButton(
          onClick = onPlay,
          enabled = item.fileExists,
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("history_play_${item.id}"),
        ) {
          Icon(
            imageVector = if (isPlayingThis) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = if (!item.fileExists) {
              stringResource(R.string.history_missing_file_badge)
            } else if (isPlayingThis) {
              stringResource(R.string.playback_control_pause)
            } else {
              stringResource(R.string.history_play_button)
            },
            style = MaterialTheme.typography.labelMedium,
          )
        }

        if (onExport != null && item.fileExists) {
          OutlinedButton(
            onClick = onExport,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("history_export_${item.id}"),
          ) {
            Icon(
              imageVector = Icons.Rounded.SaveAlt,
              contentDescription = null,
              modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = stringResource(R.string.history_export_button),
              style = MaterialTheme.typography.labelMedium,
            )
          }
        }

        if (item.prompt.isNotBlank()) {
          OutlinedButton(
            onClick = onReusePrompt,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("history_reuse_${item.id}"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Replay,
              contentDescription = null,
              modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = stringResource(R.string.history_reuse_prompt_button),
              style = MaterialTheme.typography.labelMedium,
            )
          }
        }

        if (item.playCount > 0) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = colorScheme.secondaryContainer.copy(alpha = 0.7f),
            modifier = Modifier
              .align(Alignment.CenterVertically)
              .testTag("history_plays_${item.id}"),
          ) {
            Text(
              text = stringResource(R.string.history_plays_badge, item.playCount),
              style = MaterialTheme.typography.labelSmall,
              color = colorScheme.onSecondaryContainer,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
        }

        IconButton(
          onClick = onDelete,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("history_delete_${item.id}"),
        ) {
          Icon(
            imageVector = Icons.Rounded.DeleteOutline,
            contentDescription = stringResource(R.string.cd_delete_history_entry),
            tint = colorScheme.error,
          )
        }
      }
    }
  }
}
