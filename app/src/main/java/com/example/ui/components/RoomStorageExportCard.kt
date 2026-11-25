package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DriveFolderUpload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.AudioExportFormat
import com.example.audio.RoomTracksStorageExporter
import com.example.ui.RoomStorageExportState

@Composable
fun RoomStorageExportCard(
  state: RoomStorageExportState,
  roomTrackCount: Int,
  selectedTrackCount: Int = 0,
  onSelectFormat: (AudioExportFormat) -> Unit,
  onExportToDeviceMusic: () -> Unit,
  onChooseStorageDirectory: () -> Unit,
  onDismissStatus: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val targetCount = if (selectedTrackCount > 0) selectedTrackCount else roomTrackCount
  val hasTracks = targetCount > 0

  Surface(
    shape = RoundedCornerShape(18.dp),
    color = colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, colorScheme.secondary.copy(alpha = 0.30f)),
    modifier = modifier
      .fillMaxWidth()
      .testTag("room_storage_export_card"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = colorScheme.secondaryContainer,
          modifier = Modifier.size(36.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.DriveFolderUpload,
            contentDescription = null,
            tint = colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(8.dp),
          )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = stringResource(R.string.room_export_card_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface,
          )
          Text(
            text = if (selectedTrackCount > 0) {
              stringResource(R.string.room_export_selected_subtitle, selectedTrackCount)
            } else {
              stringResource(R.string.room_export_card_subtitle, roomTrackCount)
            },
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
          )
        }
      }

      // Format Selection Chips (MP3 / WAV / FLAC)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.room_export_format_label),
          style = MaterialTheme.typography.labelMedium,
          color = colorScheme.onSurfaceVariant,
        )
        RoomTracksStorageExporter.supportedBatchFormats.forEach { fmt ->
          FilterChip(
            selected = state.format == fmt,
            onClick = { onSelectFormat(fmt) },
            enabled = !state.isExporting,
            label = {
              Text(
                text = fmt.displayName,
                style = MaterialTheme.typography.labelSmall,
              )
            },
            modifier = Modifier.testTag("room_export_format_${fmt.extension}"),
          )
        }
      }

      // Export Actions: Save to Music/SonicForge + Choose Device Storage Folder (SAF)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
          onClick = onExportToDeviceMusic,
          enabled = hasTracks && !state.isExporting,
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
          modifier = Modifier
            .weight(1f)
            .minimumInteractiveComponentSize()
            .testTag("room_export_to_music_button"),
        ) {
          if (state.isExporting) {
            CircularProgressIndicator(
              modifier = Modifier.size(16.dp),
              strokeWidth = 2.dp,
              color = colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(R.string.room_export_in_progress_short),
              style = MaterialTheme.typography.labelMedium,
              maxLines = 1,
            )
          } else {
            Icon(
              imageVector = Icons.Rounded.SaveAlt,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (selectedTrackCount > 0) {
                stringResource(R.string.room_export_selected_music_button, selectedTrackCount)
              } else {
                stringResource(R.string.room_export_all_music_button)
              },
              style = MaterialTheme.typography.labelMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        OutlinedButton(
          onClick = onChooseStorageDirectory,
          enabled = hasTracks && !state.isExporting,
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("room_export_choose_folder_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = stringResource(R.string.room_export_choose_folder_button),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
          )
        }
      }

      // Live Export Progress Indicator
      AnimatedVisibility(visible = state.isExporting && state.totalCount > 0) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          val progress = if (state.totalCount > 0) {
            (state.exportedCount.toFloat() / state.totalCount.toFloat()).coerceIn(0f, 1f)
          } else {
            0f
          }
          LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("room_export_progress_bar"),
          )
          Text(
            text = stringResource(
              R.string.room_export_progress_label,
              (state.exportedCount + 1).coerceAtMost(state.totalCount),
              state.totalCount,
              state.currentTrackTitle.ifBlank { "audio track" },
            ),
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      // Error Banner
      state.errorMessage?.let { err ->
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = colorScheme.errorContainer,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("room_export_error_banner"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = err,
              style = MaterialTheme.typography.bodySmall,
              color = colorScheme.onErrorContainer,
              modifier = Modifier.weight(1f),
            )
            IconButton(
              onClick = onDismissStatus,
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.cd_dismiss_room_export_status),
                tint = colorScheme.onErrorContainer,
                modifier = Modifier.size(16.dp),
              )
            }
          }
        }
      }

      // Success Status Banner
      state.statusMessage?.let { status ->
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = colorScheme.secondaryContainer,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("room_export_status_banner"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Rounded.CheckCircle,
              contentDescription = null,
              tint = colorScheme.onSecondaryContainer,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = status,
              style = MaterialTheme.typography.labelMedium,
              color = colorScheme.onSecondaryContainer,
              modifier = Modifier.weight(1f),
            )
            IconButton(
              onClick = onDismissStatus,
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.cd_dismiss_room_export_status),
                tint = colorScheme.onSecondaryContainer,
                modifier = Modifier.size(16.dp),
              )
            }
          }
        }
      }
    }
  }
}
