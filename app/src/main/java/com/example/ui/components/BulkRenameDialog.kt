package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.BulkRenameConfig
import com.example.model.BulkRenameMode
import com.example.model.GeneratedTrack
import com.example.model.TrackBulkRenameFormatter

@Composable
fun BulkSelectionBar(
  selectedCount: Int,
  allVisibleSelected: Boolean,
  onToggleSelectAll: () -> Unit,
  onOpenBulkRename: () -> Unit,
  onExitSelectionMode: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  Surface(
    shape = MaterialTheme.shapes.medium,
    color = colorScheme.primaryContainer.copy(alpha = 0.55f),
    border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.45f)),
    modifier = modifier
      .fillMaxWidth()
      .testTag("bulk_selection_bar"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      IconButton(
        onClick = onExitSelectionMode,
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("bulk_select_cancel_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Close,
          contentDescription = stringResource(R.string.cd_exit_bulk_select),
          tint = colorScheme.onPrimaryContainer,
        )
      }

      Text(
        text = stringResource(R.string.bulk_selected_count, selectedCount),
        style = MaterialTheme.typography.titleSmall,
        color = colorScheme.onPrimaryContainer,
        modifier = Modifier
          .weight(1f)
          .testTag("bulk_selection_count"),
      )

      TextButton(
        onClick = onToggleSelectAll,
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("bulk_select_all_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.SelectAll,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = stringResource(
            if (allVisibleSelected) R.string.bulk_deselect_all else R.string.bulk_select_all,
          ),
          style = MaterialTheme.typography.labelMedium,
          maxLines = 1,
        )
      }

      Button(
        onClick = onOpenBulkRename,
        enabled = selectedCount > 0,
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("bulk_rename_open_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.DriveFileRenameOutline,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = stringResource(R.string.bulk_rename_action),
          style = MaterialTheme.typography.labelMedium,
          maxLines = 1,
        )
      }
    }
  }
}

@Composable
fun BulkRenameDialog(
  selectedTracks: List<GeneratedTrack>,
  isRenaming: Boolean,
  onDismiss: () -> Unit,
  onConfirmRename: (BulkRenameConfig) -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  var mode by remember { mutableStateOf(BulkRenameMode.PREFIX_TITLE) }
  var prefix by remember { mutableStateOf("") }
  var timestampPattern by remember { mutableStateOf(BulkRenameConfig.DEFAULT_TIMESTAMP_PATTERN) }
  var useCurrentTime by remember { mutableStateOf(false) }

  val config = remember(mode, prefix, timestampPattern, useCurrentTime) {
    BulkRenameConfig(
      mode = mode,
      prefix = prefix,
      timestampPattern = timestampPattern,
      useCurrentTime = useCurrentTime,
    )
  }

  val isPatternValid = mode != BulkRenameMode.TIMESTAMP ||
    TrackBulkRenameFormatter.isValidTimestampPattern(timestampPattern)
  val canSubmit = !isRenaming && TrackBulkRenameFormatter.canApply(config, selectedTracks.size)
  val previews = remember(selectedTracks, config) {
    TrackBulkRenameFormatter.generatePreviews(
      tracks = selectedTracks,
      config = config,
    )
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = colorScheme.surfaceContainer,
    modifier = Modifier.testTag("bulk_rename_dialog"),
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = stringResource(R.string.bulk_rename_dialog_title),
          style = MaterialTheme.typography.titleLarge,
        )
        Text(
          text = stringResource(R.string.bulk_rename_dialog_subtitle, selectedTracks.size),
          style = MaterialTheme.typography.bodySmall,
          color = colorScheme.onSurfaceVariant,
        )
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 420.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = stringResource(R.string.bulk_rename_mode_label),
          style = MaterialTheme.typography.labelMedium,
          color = colorScheme.onSurfaceVariant,
        )

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          val modeOptions = listOf(
            BulkRenameMode.PREFIX_TITLE to R.string.rename_mode_prefix_title,
            BulkRenameMode.PREFIX_NUMBERED to R.string.rename_mode_prefix_numbered,
            BulkRenameMode.TIMESTAMP to R.string.rename_mode_timestamp,
          )
          modeOptions.forEach { (option, labelRes) ->
            FilterChip(
              selected = mode == option,
              enabled = !isRenaming,
              onClick = { mode = option },
              label = {
                Text(
                  text = stringResource(labelRes),
                  style = MaterialTheme.typography.labelSmall,
                )
              },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = colorScheme.primaryContainer,
                selectedLabelColor = colorScheme.onPrimaryContainer,
              ),
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("rename_mode_${option.testTagSuffix}"),
            )
          }
        }

        OutlinedTextField(
          value = prefix,
          onValueChange = { prefix = it },
          enabled = !isRenaming,
          label = {
            Text(
              stringResource(
                if (mode == BulkRenameMode.TIMESTAMP) {
                  R.string.bulk_rename_optional_prefix_label
                } else {
                  R.string.bulk_rename_prefix_label
                },
              ),
            )
          },
          placeholder = { Text(stringResource(R.string.bulk_rename_prefix_placeholder)) },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("bulk_rename_prefix_input"),
        )

        if (mode == BulkRenameMode.TIMESTAMP) {
          OutlinedTextField(
            value = timestampPattern,
            onValueChange = { timestampPattern = it },
            enabled = !isRenaming,
            isError = !isPatternValid,
            label = { Text(stringResource(R.string.bulk_rename_pattern_label)) },
            supportingText = if (!isPatternValid) {
              { Text(stringResource(R.string.bulk_rename_pattern_error)) }
            } else {
              null
            },
            leadingIcon = {
              Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
            },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("bulk_rename_pattern_input"),
          )

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            BulkRenameConfig.PRESET_PATTERNS.forEachIndexed { idx, preset ->
              FilterChip(
                selected = timestampPattern == preset,
                enabled = !isRenaming,
                onClick = { timestampPattern = preset },
                label = {
                  Text(
                    text = preset,
                    style = MaterialTheme.typography.labelSmall,
                  )
                },
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("pattern_preset_$idx"),
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(
              checked = useCurrentTime,
              enabled = !isRenaming,
              onCheckedChange = { useCurrentTime = it },
              modifier = Modifier.testTag("bulk_rename_use_current_time_checkbox"),
            )
            Text(
              text = stringResource(R.string.bulk_rename_use_current_time),
              style = MaterialTheme.typography.bodySmall,
              color = colorScheme.onSurfaceVariant,
            )
          }
        }

        if (previews.isNotEmpty()) {
          Text(
            text = stringResource(R.string.bulk_rename_preview_header),
            style = MaterialTheme.typography.labelMedium,
            color = colorScheme.secondary,
          )

          Surface(
            shape = RoundedCornerShape(10.dp),
            color = colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, colorScheme.outlineVariant),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("bulk_rename_preview_list"),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              previews.forEach { item ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bulk_rename_preview_${item.trackId}"),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Text(
                    text = item.oldTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.9f),
                  )
                  Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = colorScheme.secondary,
                    modifier = Modifier.size(12.dp),
                  )
                  Text(
                    text = item.newTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                      .weight(1.1f)
                      .testTag("bulk_rename_preview_new_${item.trackId}"),
                  )
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirmRename(config) },
        enabled = canSubmit,
        modifier = Modifier.testTag("confirm_bulk_rename_button"),
      ) {
        if (isRenaming) {
          CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = colorScheme.onPrimary,
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.bulk_renaming_label))
        } else {
          Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.bulk_rename_confirm_button, selectedTracks.size))
        }
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        enabled = !isRenaming,
        modifier = Modifier.testTag("cancel_bulk_rename_button"),
      ) {
        Text(stringResource(R.string.edit_org_cancel_button))
      }
    },
  )
}
